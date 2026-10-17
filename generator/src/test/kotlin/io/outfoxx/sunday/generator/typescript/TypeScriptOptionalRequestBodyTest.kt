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

package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.tools.optionalRequestBodyApi
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
class TypeScriptOptionalRequestBodyTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi"])
  fun `optional request bodies omit the HTTP entity`(
    frontend: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      optionalRequestBodyApi(frontend),
      registry,
      TypeScriptSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("OptionalCheck", ModuleSpec.Kind.MODULE)
        .addCode(CodeBlock.of("%L", Files.readString(Path.of("src/test/resources/optional-bodies/check.ts"))))
        .build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("OptionalCheck", "!optional-check") to check),
        "optional-check",
        esm = true,
      ),
    )
  }
}
