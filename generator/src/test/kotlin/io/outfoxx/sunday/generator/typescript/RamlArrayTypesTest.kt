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
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in classes`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlArrayTypesTest/arrays-nullability.test.ts", output)
  }

  @Test
  fun `test generated collection class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlArrayTypesTest/arrays-collection.test.ts", output)
  }

  @Test
  fun `test generated primitive class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlArrayTypesTest/arrays-primitive.test.ts", output)
  }

  @Test
  fun `test generated set nullability class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-set-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)
    val typeModSpec =
      builtTypes.values.firstOrNull { module ->
        module.members.any { member -> member is ClassSpec && member.name == "Test" }
      } ?: fail("Type 'Test' not defined. Available types: ${builtTypes.keys.joinToString()}")
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlArrayTypesTest/arrays-set-nullability.test.ts", output)
  }
}
