/*
 * Copyright 2020 Outfox, Inc.
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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Scalar Types Test")
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlScalarTypesTest/scalar-misc.test.ts", output)
  }

  @Test
  fun `test type names generated for integer scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlScalarTypesTest/scalar-ints.test.ts", output)
  }

  @Test
  fun `test type names generated for float scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlScalarTypesTest/scalar-floats.test.ts", output)
  }

  @Test
  fun `test type names generated for date & time scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlScalarTypesTest/scalar-dates.test.ts", output)
  }
}
