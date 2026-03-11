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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Validation Constraints Test")
class RamlValidationConstraintsTest {

  @Test
  fun `test strings generated with constraint annotations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/validation/constraints-string.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }
    assertSnapshot("RamlValidationConstraintsTest/constraints-string.test.ts", output)
  }

  @Test
  fun `test numbers generated with constraint annotations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/validation/constraints-number.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }
    assertSnapshot("RamlValidationConstraintsTest/constraints-number.test.ts", output)
  }

  @Test
  fun `test arrays generated with constraint annotations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/validation/constraints-array.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }
    assertSnapshot("RamlValidationConstraintsTest/constraints-array.test.ts", output)
  }

  @Test
  fun `test object properties generated with constraint annotations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/validation/constraints-object.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }
    assertSnapshot("RamlValidationConstraintsTest/constraints-object.test.ts", output)
  }

  @Test
  fun `test raml exclusive number constraints are rejected during parsing`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/validation/constraints-number-exclusive-invalid.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val exception =
      assertThrows<AssertionFailedError> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("exclusiveMinimum") ?: false)
  }
}
