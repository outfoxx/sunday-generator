package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in interfaces`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val arrayOfStrings: kotlin.collections.List<kotlin.String>

          public val arrayOfNullableStrings: kotlin.collections.List<kotlin.String?>

          public val nullableArrayOfStrings: kotlin.collections.List<kotlin.String>?

          public val nullableArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>?
        
          public val declaredArrayOfStrings: kotlin.collections.List<kotlin.String>
        
          public val declaredArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated collection interface`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public interface Test {
          public val implicit: kotlin.collections.List<kotlin.String>
        
          public val unspecified: kotlin.collections.List<kotlin.String>
        
          public val nonUnique: kotlin.collections.List<kotlin.String>
        
          public val unique: kotlin.collections.Set<kotlin.String>
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated nullability of array types and elements in classes`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public class Test(
          public val arrayOfStrings: kotlin.collections.List<kotlin.String>,
          public val arrayOfNullableStrings: kotlin.collections.List<kotlin.String?>,
          public val nullableArrayOfStrings: kotlin.collections.List<kotlin.String>?,
          public val nullableArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>?,
          public val declaredArrayOfStrings: kotlin.collections.List<kotlin.String>,
          public val declaredArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>
        ) {
          public fun copy(
            arrayOfStrings: kotlin.collections.List<kotlin.String>?,
            arrayOfNullableStrings: kotlin.collections.List<kotlin.String?>?,
            nullableArrayOfStrings: kotlin.collections.List<kotlin.String>?,
            nullableArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>?,
            declaredArrayOfStrings: kotlin.collections.List<kotlin.String>?,
            declaredArrayOfNullableStrings: kotlin.collections.List<kotlin.String?>?
          ) = io.test.Test(arrayOfStrings ?: this.arrayOfStrings, arrayOfNullableStrings ?: this.arrayOfNullableStrings, nullableArrayOfStrings ?: this.nullableArrayOfStrings, nullableArrayOfNullableStrings ?: this.nullableArrayOfNullableStrings, declaredArrayOfStrings ?: this.declaredArrayOfStrings, declaredArrayOfNullableStrings ?: this.declaredArrayOfNullableStrings)

          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + arrayOfStrings.hashCode()
            result = 31 * result + arrayOfNullableStrings.hashCode()
            result = 31 * result + (nullableArrayOfStrings?.hashCode() ?: 0)
            result = 31 * result + (nullableArrayOfNullableStrings?.hashCode() ?: 0)
            result = 31 * result + declaredArrayOfStrings.hashCode()
            result = 31 * result + declaredArrayOfNullableStrings.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

            if (arrayOfStrings != other.arrayOfStrings) return false
            if (arrayOfNullableStrings != other.arrayOfNullableStrings) return false
            if (nullableArrayOfStrings != other.nullableArrayOfStrings) return false
            if (nullableArrayOfNullableStrings != other.nullableArrayOfNullableStrings) return false
            if (declaredArrayOfStrings != other.declaredArrayOfStrings) return false
            if (declaredArrayOfNullableStrings != other.declaredArrayOfNullableStrings) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test(arrayOfStrings='${'$'}arrayOfStrings',
          | arrayOfNullableStrings='${'$'}arrayOfNullableStrings',
          | nullableArrayOfStrings='${'$'}nullableArrayOfStrings',
          | nullableArrayOfNullableStrings='${'$'}nullableArrayOfNullableStrings',
          | declaredArrayOfStrings='${'$'}declaredArrayOfStrings',
          | declaredArrayOfNullableStrings='${'$'}declaredArrayOfNullableStrings')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated collection class`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public class Test(
          public val implicit: kotlin.collections.List<kotlin.String>,
          public val unspecified: kotlin.collections.List<kotlin.String>,
          public val nonUnique: kotlin.collections.List<kotlin.String>,
          public val unique: kotlin.collections.Set<kotlin.String>
        ) {
          public fun copy(
            implicit: kotlin.collections.List<kotlin.String>?,
            unspecified: kotlin.collections.List<kotlin.String>?,
            nonUnique: kotlin.collections.List<kotlin.String>?,
            unique: kotlin.collections.Set<kotlin.String>?
          ) = io.test.Test(implicit ?: this.implicit, unspecified ?: this.unspecified, nonUnique ?: this.nonUnique, unique ?: this.unique)

          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + implicit.hashCode()
            result = 31 * result + unspecified.hashCode()
            result = 31 * result + nonUnique.hashCode()
            result = 31 * result + unique.hashCode()
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

            if (implicit != other.implicit) return false
            if (unspecified != other.unspecified) return false
            if (nonUnique != other.nonUnique) return false
            if (unique != other.unique) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test(implicit='${'$'}implicit',
          | unspecified='${'$'}unspecified',
          | nonUnique='${'$'}nonUnique',
          | unique='${'$'}unique')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

  @Test
  fun `test generated primitive class`(
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        public class Test(
          public val binary: kotlin.ByteArray,
          public val nullableBinary: kotlin.ByteArray?
        ) {
          public fun copy(binary: kotlin.ByteArray?, nullableBinary: kotlin.ByteArray?) = io.test.Test(binary ?: this.binary, nullableBinary ?: this.nullableBinary)

          public override fun hashCode(): kotlin.Int {
            var result = 1
            result = 31 * result + binary.contentHashCode()
            result = 31 * result + (nullableBinary?.contentHashCode() ?: 0)
            return result
          }

          public override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as io.test.Test

            if (!binary.contentEquals(other.binary)) return false
            if (nullableBinary != null) {
              if (other.nullableBinary == null) return false
              if (!nullableBinary.contentEquals(other.nullableBinary)) return false
            }
            else if (other.nullableBinary != null) return false

            return true
          }

          public override fun toString() = ""${'"'}
          |Test(binary='${'$'}binary',
          | nullableBinary='${'$'}nullableBinary')
          ""${'"'}.trimMargin()
        }
        
      """.trimIndent(),
      typeSpec.toString()
    )
  }

}
