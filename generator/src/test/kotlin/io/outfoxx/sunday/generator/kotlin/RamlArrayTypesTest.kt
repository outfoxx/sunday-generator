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

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in interfaces`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String
        import kotlin.collections.List

        public interface Test {
          public val arrayOfStrings: List<String>

          public val arrayOfNullableStrings: List<String?>

          public val nullableArrayOfStrings: List<String>?

          public val nullableArrayOfNullableStrings: List<String?>?
        
          public val declaredArrayOfStrings: List<String>
        
          public val declaredArrayOfNullableStrings: List<String?>
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated collection interface`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Set

        public interface Test {
          public val implicit: List<String>
        
          public val unspecified: List<String>
        
          public val nonUnique: List<String>
        
          public val unique: Set<String>
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated nullability of array types and elements in classes`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
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
          public val arrayOfStrings: List<String>,
          public val arrayOfNullableStrings: List<String?>,
          public val nullableArrayOfStrings: List<String>?,
          public val nullableArrayOfNullableStrings: List<String?>?,
          public val declaredArrayOfStrings: List<String>,
          public val declaredArrayOfNullableStrings: List<String?>
        ) {
          public fun copy(
            arrayOfStrings: List<String>? = null,
            arrayOfNullableStrings: List<String?>? = null,
            nullableArrayOfStrings: List<String>? = null,
            nullableArrayOfNullableStrings: List<String?>? = null,
            declaredArrayOfStrings: List<String>? = null,
            declaredArrayOfNullableStrings: List<String?>? = null
          ) = Test(arrayOfStrings ?: this.arrayOfStrings, arrayOfNullableStrings ?:
              this.arrayOfNullableStrings, nullableArrayOfStrings ?: this.nullableArrayOfStrings,
              nullableArrayOfNullableStrings ?: this.nullableArrayOfNullableStrings, declaredArrayOfStrings
              ?: this.declaredArrayOfStrings, declaredArrayOfNullableStrings ?:
              this.declaredArrayOfNullableStrings)

          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + arrayOfStrings.hashCode()
            result = 31 * result + arrayOfNullableStrings.hashCode()
            result = 31 * result + (nullableArrayOfStrings?.hashCode() ?: 0)
            result = 31 * result + (nullableArrayOfNullableStrings?.hashCode() ?: 0)
            result = 31 * result + declaredArrayOfStrings.hashCode()
            result = 31 * result + declaredArrayOfNullableStrings.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

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
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated collection class`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
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
        import kotlin.collections.Set

        public class Test(
          public val implicit: List<String>,
          public val unspecified: List<String>,
          public val nonUnique: List<String>,
          public val unique: Set<String>
        ) {
          public fun copy(
            implicit: List<String>? = null,
            unspecified: List<String>? = null,
            nonUnique: List<String>? = null,
            unique: Set<String>? = null
          ) = Test(implicit ?: this.implicit, unspecified ?: this.unspecified, nonUnique ?: this.nonUnique,
              unique ?: this.unique)

          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + implicit.hashCode()
            result = 31 * result + unspecified.hashCode()
            result = 31 * result + nonUnique.hashCode()
            result = 31 * result + unique.hashCode()
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

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
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated primitive class`(
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.ByteArray
        import kotlin.Int

        public class Test(
          public val binary: ByteArray,
          public val nullableBinary: ByteArray?
        ) {
          public fun copy(binary: ByteArray? = null, nullableBinary: ByteArray? = null) = Test(binary ?:
              this.binary, nullableBinary ?: this.nullableBinary)

          public override fun hashCode(): Int {
            var result = 1
            result = 31 * result + binary.contentHashCode()
            result = 31 * result + (nullableBinary?.contentHashCode() ?: 0)
            return result
          }

          public override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Test

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
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }
}
