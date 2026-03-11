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

import io.outfoxx.sunday.generator.GenerationException
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
import org.junit.jupiter.api.assertThrows
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parentTypeSpec = findTypeMod("Parent@!parent", generatedTypes)
    val parentOutput =
      buildString {
        FileSpec
          .get(parentTypeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.parent.ts", parentOutput)
    assertFalse(parentOutput.contains("export const ParentSchema"))

    val parentSchemaTypeSpec = findTypeMod("ParentSchema@!parent-schema", generatedTypes)
    val parentSchemaOutput =
      buildString {
        FileSpec
          .get(parentSchemaTypeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.parent-schema.ts", parentSchemaOutput)

    val testTypeSpec = findTypeMod("Test@!test", generatedTypes)
    val testOutput =
      buildString {
        FileSpec
          .get(testTypeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.test.ts", testOutput)
  }

  @Test
  fun `test external discriminator must exist`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/patchable.test.ts", output)
  }

  @Test
  fun `test known sunday type annotation uses matching schema`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-duration.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/type-ts-duration.test.ts", output)
  }

  @Test
  fun `test custom typescript type annotation resolves custom type`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-custom.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlTypeAnnotationsTest/type-ts-custom.test.ts", output)
  }
}
