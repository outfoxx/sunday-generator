package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.SchemaMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class OasValidationConstraintsTest {

  @Test
  fun `test arrays generated with constraint annotations`(
    @ResourceUri("openapi/type-gen/validation/constraints-array.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          @get:javax.validation.constraints.Size(min = 5)
          public val minList: kotlin.collections.List<kotlin.String>
        
          @get:javax.validation.constraints.Size(max = 10)
          public val maxList: kotlin.collections.List<kotlin.String>

          @get:javax.validation.constraints.Size(min = 15)
          public val minSet: kotlin.collections.Set<kotlin.String>
        
          @get:javax.validation.constraints.Size(max = 20)
          public val maxSet: kotlin.collections.Set<kotlin.String>
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test strings generated with constraint annotations`(
    @ResourceUri("openapi/type-gen/validation/constraints-string.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          @get:javax.validation.constraints.Pattern(regexp = ${'"'}""^[a-zA-Z0-9]+$""${'"'})
          public val pattern: kotlin.String
        
          @get:javax.validation.constraints.Size(min = 5)
          public val min: kotlin.String
        
          @get:javax.validation.constraints.Size(max = 10)
          public val max: kotlin.String
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test integer numbers generated with constraint annotations`(
    @ResourceUri("openapi/type-gen/validation/constraints-integer.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          @get:javax.validation.constraints.Min(value = 1)
          public val byteMin: kotlin.Byte
        
          @get:javax.validation.constraints.Max(value = 2)
          public val byteMax: kotlin.Byte
        
          public val byteMultiple: kotlin.Byte
        
          @get:javax.validation.constraints.Min(value = 4)
          public val shortMin: kotlin.Short
        
          @get:javax.validation.constraints.Max(value = 5)
          public val shortMax: kotlin.Short
        
          public val shortMultiple: kotlin.Short
        
          @get:javax.validation.constraints.Min(value = 7)
          public val intMin: kotlin.Int
        
          @get:javax.validation.constraints.Max(value = 8)
          public val intMax: kotlin.Int
        
          public val intMultiple: kotlin.Int
        
          @get:javax.validation.constraints.Min(value = 10)
          public val longMin: kotlin.Long
        
          @get:javax.validation.constraints.Max(value = 11)
          public val longMax: kotlin.Long
        
          public val longMultiple: kotlin.Long
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

  @Test
  fun `test real numbers generated with constraint annotations`(
    @ResourceUri("openapi/type-gen/validation/constraints-number.yaml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry, SchemaMode.OpenAPI3))

    assertEquals(
      """
        public interface Test {
          @get:javax.validation.constraints.DecimalMin(value = "1.0")
          public val floatMin: kotlin.Float
        
          @get:javax.validation.constraints.DecimalMax(value = "2.0")
          public val floatMax: kotlin.Float
        
          public val floatMultiple: kotlin.Float
        
          @get:javax.validation.constraints.DecimalMin(value = "4.0")
          public val doubleMin: kotlin.Double
        
          @get:javax.validation.constraints.DecimalMax(value = "5.0")
          public val doubleMax: kotlin.Double
        
          public val doubleMultiple: kotlin.Double
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )

  }

}
