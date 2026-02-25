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

import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Regression Test")
class RamlRegressionTest {

  @Test
  fun `test external discriminator library uses qualified nested schema names`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-library.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val containerTypeSpec = findTypeMod("Container@!container", generatedTypes)
    val output =
      buildString {
        FileSpec
          .get(containerTypeSpec)
          .writeTo(this)
      }

    assertFalse(output.contains("Registry"))
    assertTrue(output.contains("runtime.resolveSchema(Service.ASchema)"))
    assertSnapshot("RamlRegressionTest/external-discriminator-library.container.ts", output)
  }

  @Test
  fun `test external discriminator base properties retain typed schema output`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-base-props.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val containerTypeSpec = findTypeMod("Container@!container", generatedTypes)
    val output =
      buildString {
        FileSpec
          .get(containerTypeSpec)
          .writeTo(this)
      }

    assertFalse(output.contains("Registry"))
    assertTrue(output.contains("discriminatedUnion"))
    assertSnapshot("RamlRegressionTest/external-discriminator-base-props.container.ts", output)
  }
}
