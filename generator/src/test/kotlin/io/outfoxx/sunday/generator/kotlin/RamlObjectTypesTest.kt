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

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.String
      import kotlin.collections.List
      import kotlin.collections.Map

      public interface Test {
        public val map: Map<String, Any>

        public val array: List<Any>
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.String

      public interface Test {
        public val fromNilUnion: String?

        public val notRequired: String?
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Test2", types)

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.String
      import kotlin.collections.Map

      public interface Test2 {
        public val optionalObject: Map<String, Any>?

        public val nillableObject: Map<String, Any>?

        public val optionalHierarchy: Parent?

        public val nillableHierarchy: Parent?
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public class Test(
        public val fromNilUnion: String?,
        public val notRequired: String? = null,
      ) {
        public fun copy(fromNilUnion: String? = null, notRequired: String? = null): Test =
            Test(fromNilUnion ?: this.fromNilUnion, notRequired ?: this.notRequired)

        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + (fromNilUnion?.hashCode() ?: 0)
          result = 31 * result + (notRequired?.hashCode() ?: 0)
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test

          if (fromNilUnion != other.fromNilUnion) return false
          if (notRequired != other.notRequired) return false

          return true
        }

        override fun toString(): String = ""${'"'}
        |Test(fromNilUnion='${'$'}fromNilUnion',
        | notRequired='${'$'}notRequired')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )

    val typeSpec2 = findType("io.test.Test2", types)

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String
      import kotlin.collections.Map

      public class Test2(
        public val optionalObject: Map<String, Any>? = null,
        public val nillableObject: Map<String, Any>?,
        public val optionalHierarchy: Parent? = null,
        public val nillableHierarchy: Parent?,
      ) {
        public fun copy(
          optionalObject: Map<String, Any>? = null,
          nillableObject: Map<String, Any>? = null,
          optionalHierarchy: Parent? = null,
          nillableHierarchy: Parent? = null,
        ): Test2 = Test2(optionalObject ?: this.optionalObject, nillableObject ?: this.nillableObject,
            optionalHierarchy ?: this.optionalHierarchy, nillableHierarchy ?: this.nillableHierarchy)

        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + (optionalObject?.hashCode() ?: 0)
          result = 31 * result + (nillableObject?.hashCode() ?: 0)
          result = 31 * result + (optionalHierarchy?.hashCode() ?: 0)
          result = 31 * result + (nillableHierarchy?.hashCode() ?: 0)
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test2

          if (optionalObject != other.optionalObject) return false
          if (nillableObject != other.nillableObject) return false
          if (optionalHierarchy != other.optionalHierarchy) return false
          if (nillableHierarchy != other.nillableHierarchy) return false

          return true
        }

        override fun toString(): String = ""${'"'}
        |Test2(optionalObject='${"$"}optionalObject',
        | nillableObject='${"$"}nillableObject',
        | optionalHierarchy='${"$"}optionalHierarchy',
        | nillableHierarchy='${"$"}nillableHierarchy')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.String

      public interface Test {
        public val `value`: String
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", testSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      import kotlin.String

      public interface Test2 : Test {
        public val value2: String
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", test2Spec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      public interface Empty : Test2

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", emptySpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      import kotlin.String

      public interface Test3 : Empty {
        public val value3: String
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public open class Test(
        public val `value`: String,
      ) {
        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + value.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test

          if (value != other.value) return false

          return true
        }

        override fun toString(): String = ${'"'}""Test(value='${'$'}value')""${'"'}
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", testSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public open class Test2(
        `value`: String,
        public val value2: String,
      ) : Test(value) {
        override fun hashCode(): Int {
          var result = 31 * super.hashCode()
          result = 31 * result + value2.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test2

          if (value != other.value) return false
          if (value2 != other.value2) return false

          return true
        }

        override fun toString(): String = ${'"'}""
        |Test2(value='${'$'}value',
        | value2='${'$'}value2')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", test2Spec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.String

      public open class Empty(
        `value`: String,
        value2: String,
      ) : Test2(value, value2) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Empty

          if (value != other.value) return false
          if (value2 != other.value2) return false
          return true
        }

        override fun toString(): String = ${'"'}""
        |Empty(value='${'$'}value',
        | value2='${'$'}value2')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", emptySpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public class Test3(
        `value`: String,
        value2: String,
        public val value3: String,
      ) : Empty(value, value2) {
        public fun copy(
          `value`: String? = null,
          value2: String? = null,
          value3: String? = null,
        ): Test3 = Test3(value ?: this.value, value2 ?: this.value2, value3 ?: this.value3)

        override fun hashCode(): Int {
          var result = 31 * super.hashCode()
          result = 31 * result + value3.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test3

          if (value != other.value) return false
          if (value2 != other.value2) return false
          if (value3 != other.value3) return false

          return true
        }

        override fun toString(): String = ""${'"'}
        |Test3(value='${'$'}value',
        | value2='${'$'}value2',
        | value3='${'$'}value3')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.String

      public interface Test {
        public val someValue: String

        public val anotherValue: String
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import com.fasterxml.jackson.`annotation`.JsonProperty
      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public class Test(
        @param:JsonProperty(value = "some-value")
        public val someValue: String,
        @param:JsonProperty(value = "another_value")
        public val anotherValue: String,
      ) {
        public fun copy(someValue: String? = null, anotherValue: String? = null): Test = Test(someValue ?:
            this.someValue, anotherValue ?: this.anotherValue)

        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + someValue.hashCode()
          result = 31 * result + anotherValue.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test

          if (someValue != other.someValue) return false
          if (anotherValue != other.anotherValue) return false

          return true
        }

        override fun toString(): String = ""${'"'}
        |Test(someValue='${'$'}someValue',
        | anotherValue='${'$'}anotherValue')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String
      import kotlin.collections.List

      public class Test(
        public val parent: Test?,
        public val other: Test? = null,
        public val children: List<Test>,
      ) {
        public fun copy(
          parent: Test? = null,
          other: Test? = null,
          children: List<Test>? = null,
        ): Test = Test(parent ?: this.parent, other ?: this.other, children ?: this.children)

        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + (parent?.hashCode() ?: 0)
          result = 31 * result + (other?.hashCode() ?: 0)
          result = 31 * result + children.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Test

          if (parent != other.parent) return false
          if (other != other.other) return false
          if (children != other.children) return false

          return true
        }

        override fun toString(): String = ""${'"'}
        |Test(parent='${'$'}parent',
        | other='${'$'}other',
        | children='${'$'}children')
        ""${'"'}.trimMargin()
      }

      """.trimIndent(),
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

    assertEquals(
      """
      package io.test

      import kotlin.Any
      import kotlin.Boolean
      import kotlin.Int
      import kotlin.String

      public open class Node(
        public val type: NodeType,
      ) {
        override fun hashCode(): Int {
          var result = 1
          result = 31 * result + type.hashCode()
          return result
        }

        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false

          other as Node

          if (type != other.type) return false

          return true
        }

        override fun toString(): String = ""${'"'}Node(type='${'$'}type')""${'"'}
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
