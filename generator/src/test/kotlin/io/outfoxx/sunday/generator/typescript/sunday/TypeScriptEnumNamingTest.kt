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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.tools.enumNamingApi
import io.outfoxx.sunday.generator.tools.enumNamingUpperCamelNames
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TypeScriptTest
@Tag("models")
class TypeScriptEnumNamingTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `maps enum words and preserves strict and tolerant wire values`(
    source: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = enumNamingApi(source)
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    val checks =
      api.models
        .single { it.name == "JobKind" }
        .values
        .zip(
          enumNamingUpperCamelNames,
        ).joinToString("\n") { (wire, member) ->
          val tolerantMember = if (member == "UnknownValue") "$member('$wire')" else member
          """
          if (JobKind.$member !== '$wire') throw new Error('strict $member');
          if (TolerantKind.$tolerantMember.rawValue !== '$wire') throw new Error('tolerant $member');
          if (TolerantKind.fromValue('$wire') !== TolerantKind.$tolerantMember) throw new Error('lookup $member');
          """.trimIndent()
        }
    val consumer =
      ModuleSpec
        .builder("EnumNamingCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {JobKind} from './job-kind';
            import {TolerantKind} from './tolerant-kind';
            $checks
            if (TolerantKind.fromValue('future-value').rawValue !== 'future-value') throw new Error('fallback');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("EnumNamingCheck", "!enum-naming-check") to consumer),
        "enum-naming-check",
      ),
    )
  }

  @Test
  fun `rejects acronym names that normalize to the same member`() {
    val original = enumNamingApi("openapi")
    val model = original.models.single { it.name == "JobKind" }.copy(values = listOf("HTTPServer", "http_server"))
    val api = original.copy(models = listOf(model))
    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(
          api,
          TypeScriptTypeRegistry(setOf()),
          typeScriptSundayTestOptions,
        ).generateServiceTypes()
      }
    assertTrue(error.message!!.contains("'HttpServer' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }
}
