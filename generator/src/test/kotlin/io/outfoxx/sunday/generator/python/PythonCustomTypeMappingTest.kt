/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PythonCustomTypeMappingTest : PythonTest() {
  @Test
  fun `custom Python scalars retain their Pydantic representation`(compiler: PythonCompiler) {
    val api =
      OpenApiToGeneratedApi().convert(
        Path.of("src/test/resources/openapi/ir/custom-types/mapped-api.yaml").toUri(),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Identifier(str):
                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.no_info_after_validator_function(cls, handler(str))
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        PythonSundayIrGenerator(api, PythonGeneratorOptions(packageName = "test_api"))
          .generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)) + domain,
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Identifier
          value = Record.model_validate_json('{"id":"id:abc"}')
          assert isinstance(value.id, Identifier)
          assert Record.model_validate_json(value.model_dump_json()).id == value.id
          try:
              Record.model_validate_json('{"id":"invalid"}')
          except ValueError:
              pass
          else:
              raise AssertionError("wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `Kotlin mapping metadata leaves Python output unchanged`(compiler: PythonCompiler) {
    val api = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    val sources =
      listOf(api.models, api.models.map { it.copy(targets = emptyMap()) }).map { models ->
        val modules =
          listOf(
            PythonModelRenderer("test_api").renderModels(models),
            PythonModuleBuilder("test_api/__init__.py").build(),
          )
        assertTrue(compileModules(compiler, modules, importModules = listOf("test_api.models")))
        CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "test_api/models.py")
      }
    assertEquals(sources[0], sources[1])
  }
}
