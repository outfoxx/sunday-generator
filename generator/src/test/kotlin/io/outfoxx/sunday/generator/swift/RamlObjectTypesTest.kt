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

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URI

@SwiftTest
@DisplayName("[Swift] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-freeform-object.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of property types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val testTypeSpec = findType("Test", builtTypes)

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types.output.swift",
      buildString {
        FileSpec
          .get("", testTypeSpec)
          .writeTo(this)
      },
    )

    val test2TypeSpec = findType("Test2", builtTypes)

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types.output2.swift",
      buildString {
        FileSpec
          .get("", test2TypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.lastOrNull()?.name,
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val api = findType("API", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "FetchTestResponse0Payload",
      api.typeSpecs.lastOrNull()?.name,
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val testSpec = builtTypes[DeclaredTypeName.typeName(".Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[DeclaredTypeName.typeName(".Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val emptySpec = builtTypes[DeclaredTypeName.typeName(".Empty")]
    emptySpec ?: fail("No Empty class defined")

    val test3Spec = builtTypes[DeclaredTypeName.typeName(".Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output.swift",
      buildString {
        FileSpec
          .get("", testSpec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output2.swift",
      buildString {
        FileSpec
          .get("", test2Spec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output3.swift",
      buildString {
        FileSpec
          .get("", emptySpec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output4.swift",
      buildString {
        FileSpec
          .get("", test3Spec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated classes for object hierarchy with empty root`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits-empty-root.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val rootSpec = builtTypes[DeclaredTypeName.typeName(".Root")]
    rootSpec ?: fail("No Root class defined")

    val branchSpec = builtTypes[DeclaredTypeName.typeName(".Branch")]
    branchSpec ?: fail("No Branch class defined")

    val leafSpec = builtTypes[DeclaredTypeName.typeName(".Leaf")]
    leafSpec ?: fail("No Leaf class defined")

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy-with-empty-root.output.swift",
      buildString {
        FileSpec
          .get("", rootSpec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy-with-empty-root.output2.swift",
      buildString {
        FileSpec
          .get("", branchSpec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy-with-empty-root.output3.swift",
      buildString {
        FileSpec
          .get("", leafSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-class-property-with-kebab-or-snake-case-names.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursive property`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-class-with-recursive-property.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursion down to a complex leaf`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive-complex-leaf.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpecs = generateTypes(testUri, typeRegistry, compiler)
    val typeSpec = findType("Node", typeSpecs)

    assertNotNull(typeSpec)
    assertNotNull(findType("NodeType", typeSpecs))
    assertNotNull(findType("NodeList", typeSpecs))
    assertNotNull(findType("NodeValue", typeSpecs))
    assertNotNull(findType("NodeMap", typeSpecs))

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-generated-class-with-recursion-down-to-a-complex-leaf.output.swift",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test default identifiable generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-with-id.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))

    val types = generateTypes(testUri, typeRegistry, compiler)
    val typeSpec = findType("Test", types)
    val typeSpec2 = findType("Test2", types)

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-default-identifiable-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )

    assertSwiftSnapshot(
      "RamlObjectTypesTest/test-default-identifiable-generation.output2.swift",
      buildString {
        FileSpec
          .get("", typeSpec2)
          .writeTo(this)
      },
    )
  }
}
