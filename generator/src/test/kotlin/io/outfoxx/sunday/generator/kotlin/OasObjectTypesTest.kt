package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI

@ExtendWith(ResourceExtension::class)
class OasObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    @ResourceUri("openapi/type-gen/types/obj-freeform.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val map: kotlin.collections.Map<kotlin.String, kotlin.Any>
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated nullability of property types in interfaces`(
    @ResourceUri("openapi/type-gen/types/obj-property-nullability.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val fromNilUnion: kotlin.String?
        
          public val notRequired: kotlin.String?
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated nullability of property types in classes`(
    @ResourceUri("openapi/type-gen/types/obj-property-nullability.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public class Test(
          public val fromNilUnion: kotlin.String?,
          public val notRequired: kotlin.String?
        ) {
          public fun copy(fromNilUnion: kotlin.String?, notRequired: kotlin.String?) = io.test.Test(fromNilUnion ?: this.fromNilUnion, notRequired ?: this.notRequired)

          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + (fromNilUnion?.hashCode() ?: 0)
            result = 31 * result + (notRequired?.hashCode() ?: 0)
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

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
      typeSpec.toString()
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    @ResourceUri("openapi/type-gen/types/obj-property-inline-type.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.firstOrNull()?.name
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    @ResourceUri("openapi/type-gen/types/obj-resource-inline-type.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3)

    assertEquals(
      "io.test.API.FetchTestResponsePayload",
      builtTypes.keys.first().canonicalName
    )
  }

  @Test
  fun `test generated interfaces for object hierarchy`(
    @ResourceUri("openapi/type-gen/types/obj-inherits.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf<Option>()
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val test3Spec = builtTypes[ClassName.bestGuess("io.test.Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertEquals(
      """
        public interface Test {
          public val value: kotlin.String
        }
        
      """.trimIndent(),
      testSpec.toString()
    )

    assertEquals(
      """
        public interface Test2 : io.test.Test {
          public val value2: kotlin.String
        }
        
      """.trimIndent(),
      test2Spec.toString()
    )

    assertEquals(
      """
        public interface Test3 : io.test.Test2 {
          public val value3: kotlin.String
        }
        
      """.trimIndent(),
      test3Spec.toString()
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    @ResourceUri("openapi/type-gen/types/obj-inherits.yaml") testUri: URI
  ) {

    val typeRegistryOptions = setOf(ImplementModel)
    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, typeRegistryOptions)

    val builtTypes = generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3)

    val testSpec = builtTypes[ClassName.bestGuess("io.test.Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[ClassName.bestGuess("io.test.Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val test3Spec = builtTypes[ClassName.bestGuess("io.test.Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertEquals(
      """
        public open class Test(
          public val value: kotlin.String
        ) {
          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + value.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

            if (value != other.value) return false

            return true
          }

          public override fun toString() = ${'"'}""Test(value='${'$'}value')""${'"'}
        }
        
      """.trimIndent(),
      testSpec.toString()
    )

    assertEquals(
      """
        public open class Test2(
          value: kotlin.String,
          public val value2: kotlin.String
        ) : io.test.Test(value) {
          public override fun hashCode(): kotlin.Int {
            var result = 31 * super.hashCode()
            result = 31 * result + value2.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test2

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
      test2Spec.toString()
    )

    assertEquals(
      """
        public class Test3(
          value: kotlin.String,
          value2: kotlin.String,
          public val value3: kotlin.String
        ) : io.test.Test2(value, value2) {
          public fun copy(
            value: kotlin.String?,
            value2: kotlin.String?,
            value3: kotlin.String?
          ) = io.test.Test3(value ?: this.value, value2 ?: this.value2, value3 ?: this.value3)

          public override fun hashCode(): kotlin.Int {
            var result = 31 * super.hashCode()
            result = 31 * result + value3.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test3

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
      test3Spec.toString()
    )
  }

  @Test
  fun `test generated interface property with kebab case name`(
    @ResourceUri("openapi/type-gen/types/obj-property-renamed.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          public val someValue: kotlin.String
        
          public val anotherValue: kotlin.String
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    @ResourceUri("openapi/type-gen/types/obj-property-renamed.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public class Test(
          @com.fasterxml.jackson.`annotation`.JsonProperty(value = "some-value")
          public val someValue: kotlin.String,
          @com.fasterxml.jackson.`annotation`.JsonProperty(value = "another_value")
          public val anotherValue: kotlin.String
        ) {
          public fun copy(someValue: kotlin.String?, anotherValue: kotlin.String?) = io.test.Test(someValue ?: this.someValue, anotherValue ?: this.anotherValue)
        
          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + someValue.hashCode()
            result = 31 * result + anotherValue.hashCode()
            return result
          }
        
          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as io.test.Test
        
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
      typeSpec.toString()
    )
  }

}
