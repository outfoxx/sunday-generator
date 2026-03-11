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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opentest4j.AssertionFailedError
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

  @Test
  fun `test external discriminator with multiple constrained properties`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-multi-props.raml") testUri: URI,
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

    assertTrue(output.contains("externalDiscriminatorSchema1"))
    assertTrue(output.contains("externalDiscriminatorSchema2"))
    assertTrue(output.contains("externallyConstrainedWireSchema2"))
    assertSnapshot("RamlRegressionTest/external-discriminator-multi-props.container.ts", output)
  }

  @Test
  fun `test external discriminator with no inheriting variants falls back to base wire schema`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-no-variants.raml") testUri: URI,
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

    assertTrue(output.contains("const externallyConstrainedWireSchema1 = wireSchema;"))
    assertSnapshot("RamlRegressionTest/external-discriminator-no-variants.container.ts", output)
  }

  @Test
  fun `test external discriminator duplicate values fall back to plain union schema`(
    @ResourceUri("raml/regression/external-discriminator-duplicate-values.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypesWithoutCompile(testUri, typeRegistry)

    val containerTypeSpec = findTypeMod("Container@!container", generatedTypes)
    val output =
      buildString {
        FileSpec
          .get(containerTypeSpec)
          .writeTo(this)
      }

    assertTrue(output.contains("const externallyConstrainedWireSchema1 = z.union(["))
    assertFalse(output.contains("const externallyConstrainedWireSchema1 = z.discriminatedUnion("))
    assertSnapshot("RamlRegressionTest/external-discriminator-duplicate-values.container.ts", output)
  }

  @Test
  fun `test multi external discriminator duplicate values use plain union constraint schemas`(
    @ResourceUri("raml/regression/external-discriminator-multi-props-duplicate-values.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes = generateTypesWithoutCompile(testUri, typeRegistry)

    val containerTypeSpec = findTypeMod("Container@!container", generatedTypes)
    val output =
      buildString {
        FileSpec
          .get(containerTypeSpec)
          .writeTo(this)
      }

    assertTrue(output.contains("const externalDiscriminatorSchema1 = z.union(["))
    assertTrue(output.contains("const externalDiscriminatorSchema2 = z.union(["))
    assertFalse(output.contains("const externalDiscriminatorSchema1 = z.discriminatedUnion("))
    assertFalse(output.contains("const externalDiscriminatorSchema2 = z.discriminatedUnion("))
    assertSnapshot("RamlRegressionTest/external-discriminator-multi-props-duplicate-values.container.ts", output)
  }

  @Test
  fun `test external discriminator rejects nullable union property range`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-nullable-property.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Externally discriminated types must be 'object'") ?: false)
  }

  @Test
  fun `test multi external discriminator rejects nullable union property range`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-multi-props-nullable.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Externally discriminated types must be 'object'") ?: false)
  }

  @Test
  fun `test invalid discriminator declaration is rejected during RAML processing`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/regression/external-discriminator-missing-property.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<AssertionFailedError> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Property 'kind' marked as discriminator is missing") ?: false)
  }

  private fun generateTypesWithoutCompile(
    uri: URI,
    typeRegistry: TypeScriptTypeRegistry,
  ): Map<TypeName.Standard, ModuleSpec> {
    val (document, shapeIndex) = TestAPIProcessing.process(uri)

    val apiTypeName = TypeName.namedImport("API", "!api")
    typeRegistry.addServiceType(apiTypeName, ClassSpec.builder(apiTypeName))

    TestAPIProcessing.generateTypes(document, shapeIndex, Client, typeRegistry::defineProblemType) { name, schema ->
      val context = TypeScriptResolutionContext(document, shapeIndex, apiTypeName.nested(name))
      typeRegistry.resolveTypeName(schema, context)
    }

    return typeRegistry.buildTypes()
  }
}
