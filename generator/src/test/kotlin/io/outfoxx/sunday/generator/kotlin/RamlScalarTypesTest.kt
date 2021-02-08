package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val bool: kotlin.Boolean

          public val string: kotlin.String

          public val `file`: kotlin.ByteArray

          public val any: kotlin.Any

          public val nil: kotlin.Unit
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test type names generated for integer scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val int8: kotlin.Byte

          public val int16: kotlin.Short

          public val int32: kotlin.Int

          public val int64: kotlin.Long

          public val int: kotlin.Int

          public val long: kotlin.Long
        
          public val none: kotlin.Int
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test type names generated for float scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val float: kotlin.Float

          public val double: kotlin.Double
        
          public val none: kotlin.Double
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test type names generated for date & time scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val dateOnly: java.time.LocalDate
        
          public val timeOnly: java.time.LocalTime
        
          public val dateTimeOnly: java.time.LocalDateTime
        
          public val dateTime: java.time.OffsetDateTime
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

}
