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
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
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
  fun `mapped groups validate nested unmapped containers on the wire`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Code",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("string")),
          validation = mapOf("pattern" to "^id:"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Code")),
        ),
        GeneratedModel(
          "Codes",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Code")),
          validation = mapOf("minItems" to "1", "maxItems" to "2"),
        ),
        GeneratedModel(
          "Groups",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Codes")),
          validation = mapOf("minItems" to "1"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Groups")),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("groups", GeneratedTypeRef.named("Groups"), required = true)),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Code:
                def __init__(self, value: str):
                    self.value = value

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.value),
                    )


            class Groups:
                def __init__(self, values: list[list[Code]]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(
                            lambda value: [[code.value for code in codes] for codes in value.values],
                        ),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Code, Groups
          groups = Groups([[Code("id:abc")]])
          record = Record(groups=groups)
          assert record.groups is groups
          assert record.model_dump() == {"groups": [["id:abc"]]}
          for values in ([], [[]], [[Code("id:a"), Code("id:b"), Code("id:c")]], [[Code("invalid")]]):
              try:
                  Record(groups=Groups(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError("nested wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `mapped arrays preserve unique collections and nested enum checks`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel("Status", GeneratedModel.Kind.ENUM, values = listOf("active", "inactive")),
        GeneratedModel(
          "Statuses",
          GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.named("Status")),
          collection = GeneratedCollectionKind.SET,
          validation = mapOf("minItems" to "1", "uniqueItems" to "true"),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Statuses")),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("statuses", GeneratedTypeRef.named("Statuses"), required = true)),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Statuses:
                def __init__(self, values: list[str]):
                    self.values = values

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.values),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Statuses
          statuses = Statuses(["active"])
          record = Record(statuses=statuses)
          assert record.statuses is statuses
          assert record.model_dump() == {"statuses": ["active"]}
          for values in ([], ["unknown"]):
              try:
                  Record(statuses=Statuses(values))
              except ValueError:
                  pass
              else:
                  raise AssertionError("array wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `mapped enum wire constraints do not rerun custom validators`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          "Code",
          GeneratedModel.Kind.ENUM,
          values = listOf("id:abc", "id:def"),
          targets =
            mapOf(
              "python" to GeneratedTarget(typeName = "test_api.domain.Code"),
            ),
        ),
        GeneratedModel(
          "Alias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.named("Code")),
          validation =
            mapOf(
              "pattern" to "^id:a",
            ),
          targets = mapOf("python" to GeneratedTarget(typeName = "test_api.domain.Code")),
        ),
        GeneratedModel(
          "Record",
          GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "code",
                GeneratedTypeRef.named("Code"),
                required = true,
                validation =
                  mapOf(
                    "pattern" to "^id:a",
                  ),
              ),
              GeneratedModelProperty("alias", GeneratedTypeRef.named("Alias"), required = true),
            ),
        ),
      )
    val domain =
      PythonModuleBuilder("test_api/domain.py")
        .addCode(
          PythonCodeBlock.of(
            "%L",
            """
            from pydantic import GetCoreSchemaHandler
            from pydantic_core import core_schema


            class Code:
                def __init__(self, value: str):
                    self.value = value

                @classmethod
                def __get_pydantic_core_schema__(cls, source: object, handler: GetCoreSchemaHandler) -> core_schema.CoreSchema:
                    return core_schema.is_instance_schema(
                        cls,
                        serialization=core_schema.plain_serializer_function_ser_schema(lambda value: value.value),
                    )
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileModules(
        compiler,
        listOf(
          domain,
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          from test_api.models import Record
          from test_api.domain import Code
          code = Code("id:abc")
          value = Record(code=code, alias=code)
          assert value.code is code and value.alias is code
          assert value.model_dump() == {"code": "id:abc", "alias": "id:abc"}
          for fields in ({"code": Code("id:def"), "alias": code}, {"code": code, "alias": Code("id:def")}):
              try:
                  Record.model_validate(fields)
              except ValueError:
                  pass
              else:
                  raise AssertionError("wire constraints were ignored")
          """.trimIndent(),
      ),
    )
  }

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
