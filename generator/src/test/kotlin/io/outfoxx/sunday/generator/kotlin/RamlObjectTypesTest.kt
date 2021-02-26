package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.String
        import kotlin.collections.Map

        public interface Test {
          public val map: Map<String, Any>
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated nullability of property types in interfaces`(
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

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
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated nullability of property types in classes`(
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Test(
          public val fromNilUnion: String?,
          public val notRequired: String?
        ) {
          public fun copy(fromNilUnion: String? = null, notRequired: String? = null) = Test(fromNilUnion ?:
              this.fromNilUnion, notRequired ?: this.notRequired)

          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + (fromNilUnion?.hashCode() ?: 0)
            result = 31 * result + (notRequired?.hashCode() ?: 0)
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (fromNilUnion != other.fromNilUnion) return false
            if (notRequired != other.notRequired) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test(fromNilUnion='${'$'}fromNilUnion',
          | notRequired='${'$'}notRequired')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.firstOrNull()?.name
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry)

    assertEquals(
      "io.test.API.FetchTestResponsePayload",
      builtTypes.keys.first().canonicalName
    )
  }

  @Test
  fun `test generated interfaces for object hierarchy`(
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val test3Spec = builtTypes[ClassName.bestGuess("io.test.Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Test {
          public val value: String
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", testSpec)
          .writeTo(this)
      }
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
        FileSpec.get("io.test", test2Spec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        package io.test

        import kotlin.String

        public interface Test3 : Test2 {
          public val value3: String
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", test3Spec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI
  ) {

    val typeRegistryOptions = setOf(ImplementModel)
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

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
          public val value: String
        ) {
          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + value.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

            if (value != other.value) return false

            return true
          }

          public override fun toString() = ${'"'}""Test(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", testSpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public open class Test2(
          value: String,
          public val value2: String
        ) : Test(value) {
          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + value2.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test2

            if (!super.equals(other)) return false
            if (value2 != other.value2) return false

            return true
          }

          public override fun toString() = ${'"'}""
          |Test2(value='${'$'}value',
          | value2='${'$'}value2')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", test2Spec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.Int
        import kotlin.String

        public class Test3(
          value: String,
          value2: String,
          public val value3: String
        ) : Test2(value, value2) {
          public fun copy(
            value: String? = null,
            value2: String? = null,
            value3: String? = null
          ) = Test3(value ?: this.value, value2 ?: this.value2, value3 ?: this.value3)

          public override fun hashCode(): Int {
            var result = 31 * super.hashCode()
            result = 31 * result + value3.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test3

            if (!super.equals(other)) return false
            if (value3 != other.value3) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test3(value='${'$'}value',
          | value2='${'$'}value2',
          | value3='${'$'}value3')
          ""${'"'}.trimMargin()
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", test3Spec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated interface property with kebab case name`(
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

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
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

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
          @JsonProperty(value = "some-value")
          public val someValue: String,
          @JsonProperty(value = "another_value")
          public val anotherValue: String
        ) {
          public fun copy(someValue: String? = null, anotherValue: String? = null) = Test(someValue ?:
              this.someValue, anotherValue ?: this.anotherValue)
        
          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + someValue.hashCode()
            result = 31 * result + anotherValue.hashCode()
            return result
          }
        
          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as Test
        
            if (someValue != other.someValue) return false
            if (anotherValue != other.anotherValue) return false
        
            return true
          }
        
          public override fun toString() = ""${'"'}
          |Test(someValue='${'$'}someValue',
          | anotherValue='${'$'}anotherValue')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

}
