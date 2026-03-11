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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-freeform-object.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of property types in interfaces`(
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val types = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", types)

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types-in-interfaces.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Test2", types)

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types-in-interfaces.output2.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of property types in classes`(
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val types = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Test", types)

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types-in-classes.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Test2", types)

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-nullability-of-property-types-in-classes.output2.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec2)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.firstOrNull()?.name,
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val builtTypes =
      generateTypes(testUri, typeRegistry)
        .filter { it.key.simpleName != "API" }

    assertEquals(
      "io.test.API.FetchTestResponse0Payload",
      builtTypes.keys.first().canonicalName,
    )
  }

  @Test
  fun `test generated interfaces for object hierarchy`(
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val emptySpec = builtTypes[ClassName.bestGuess("io.test.Empty")]
    emptySpec ?: fail("No Empty class defined")

    val test3Spec = builtTypes[ClassName.bestGuess("io.test.Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-interfaces-for-object-hierarchy.output.kt",
      buildString {
        FileSpec
          .get("io.test", testSpec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-interfaces-for-object-hierarchy.output2.kt",
      buildString {
        FileSpec
          .get("io.test", test2Spec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-interfaces-for-object-hierarchy.output3.kt",
      buildString {
        FileSpec
          .get("io.test", emptySpec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-interfaces-for-object-hierarchy.output4.kt",
      buildString {
        FileSpec
          .get("io.test", test3Spec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI,
  ) {

    val typeRegistryOptions = setOf(ImplementModel)
    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val emptySpec = builtTypes[ClassName.bestGuess("io.test.Empty")]
    emptySpec ?: fail("No Empty class defined")

    val test3Spec = builtTypes[ClassName.bestGuess("io.test.Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output.kt",
      buildString {
        FileSpec
          .get("io.test", testSpec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output2.kt",
      buildString {
        FileSpec
          .get("io.test", test2Spec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output3.kt",
      buildString {
        FileSpec
          .get("io.test", emptySpec)
          .writeTo(this)
      },
    )

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-classes-for-object-hierarchy.output4.kt",
      buildString {
        FileSpec
          .get("io.test", test3Spec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated interface property with kebab case name`(
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-interface-property-with-kebab-case-name.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-class-property-with-kebab-or-snake-case-names.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursive property`(
    @ResourceUri("raml/type-gen/types/obj-recursive.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-class-with-recursive-property.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursion down to a complex leaf`(
    @ResourceUri("raml/type-gen/types/obj-recursive-complex-leaf.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpecs = generateTypes(testUri, typeRegistry)
    val typeSpec = findType("io.test.Node", typeSpecs)

    assertNotNull(typeSpec)
    assertNotNull(findType("io.test.NodeType", typeSpecs))
    assertNotNull(findType("io.test.NodeValue", typeSpecs))
    assertNotNull(findType("io.test.NodeList", typeSpecs))
    assertNotNull(findType("io.test.NodeMap", typeSpecs))

    assertKotlinSnapshot(
      "RamlObjectTypesTest/test-generated-class-with-recursion-down-to-a-complex-leaf.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
