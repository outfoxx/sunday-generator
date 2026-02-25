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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

@SwiftTest
@DisplayName("[Swift] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  @Test
  fun `test type annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val type = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "Foundation.URL",
      type.propertySpecs
        .firstOrNull()
        ?.type
        ?.toString(),
    )
  }

  @Test
  fun `test module annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-module.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val types = generateTypes(testUri, typeRegistry, compiler)

    val apiTypes = types.filter { it.key.simpleName == "API" }.toList()
    val modelTypes = types.filterNot { it.key.simpleName == "API" }.toList()

    assertEquals("Explicit", apiTypes.first().first.moduleName)
    assertEquals("", modelTypes.first().first.moduleName)
  }

  @Test
  fun `test model module annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-model-module.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val types = generateTypes(testUri, typeRegistry, compiler)

    val apiTypes = types.filter { it.key.simpleName == "API" }.toList()
    val modelTypes = types.filterNot { it.key.simpleName == "API" }.toList()

    assertEquals("", apiTypes.first().first.moduleName)
    assertEquals("Explicit", modelTypes.first().first.moduleName)
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Group", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation (dashed scheme)`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-dashed.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Group", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-dashed-scheme.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Root", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-using-library-types.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using only library types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib2.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Root", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-nested-annotation-using-only-library-types.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class generated swift implementations`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-impl.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-generated-swift-implementations.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val parenTypeSpec =
      builtTypes[typeName(".Parent")]
        ?: error("Parent type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output.swift",
      buildString {
        FileSpec
          .get("", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec =
      builtTypes[typeName(".Child1")]
        ?: error("Child1 type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output2.swift",
      buildString {
        FileSpec
          .get("", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec =
      builtTypes[typeName(".Child2")]
        ?: error("Child2 type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output3.swift",
      buildString {
        FileSpec
          .get("", child2TypeSpec)
          .writeTo(this)
      },
    )

    val testTypeSpec =
      builtTypes[typeName(".Test")]
        ?: error("Test type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-types.output4.swift",
      buildString {
        FileSpec
          .get("", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated enum types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-enum.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val parenTypeSpec =
      builtTypes[typeName(".Parent")]
        ?: error("Parent type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output.swift",
      buildString {
        FileSpec
          .get("", parenTypeSpec)
          .writeTo(this)
      },
    )

    val child1TypeSpec =
      builtTypes[typeName(".Child1")]
        ?: error("Child1 type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output2.swift",
      buildString {
        FileSpec
          .get("", child1TypeSpec)
          .writeTo(this)
      },
    )

    val child2TypeSpec =
      builtTypes[typeName(".Child2")]
        ?: error("Child2 type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output3.swift",
      buildString {
        FileSpec
          .get("", child2TypeSpec)
          .writeTo(this)
      },
    )

    val testTypeSpec =
      builtTypes[typeName(".Test")]
        ?: error("Test type is not defined")

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-class-hierarchy-generated-for-externally-discriminated-enum-types.output4.swift",
      buildString {
        FileSpec
          .get("", testTypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test external discriminator must exist`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("externalDiscriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-patchable-class-generation.output.swift",
      buildString {
        FileSpec
          .builder("", typeSpec.name)
          .addType(typeSpec)
          .apply {
            typeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test discriminated patchable class generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable-disc.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val generatedTypes = generateTypes(testUri, typeRegistry, compiler)
    val testTypeTypeSpec = findType("TestType", generatedTypes)
    val testTypeSpec = findType("Test", generatedTypes)
    val childTypeSpec = findType("Child", generatedTypes)

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-discriminated-patchable-class-generation.output.swift",
      buildString {
        FileSpec
          .builder("", testTypeTypeSpec.name)
          .addType(testTypeTypeSpec)
          .apply {
            testTypeTypeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-discriminated-patchable-class-generation.output2.swift",
      buildString {
        FileSpec
          .builder("", testTypeSpec.name)
          .addType(testTypeSpec)
          .apply {
            testTypeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlTypeAnnotationsTest/test-discriminated-patchable-class-generation.output3.swift",
      buildString {
        FileSpec
          .builder("", childTypeSpec.name)
          .addType(childTypeSpec)
          .apply {
            childTypeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      },
    )
  }
}
