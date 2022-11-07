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
@DisplayName("[Kotlin] [RAML] Scalar Types Test")
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Any
        import kotlin.Boolean
        import kotlin.ByteArray
        import kotlin.String
        import kotlin.Unit

        public interface Test {
          public val bool: Boolean

          public val string: String

          public val `file`: ByteArray

          public val any: Any

          public val nil: Unit
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for integer scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import kotlin.Byte
        import kotlin.Int
        import kotlin.Long
        import kotlin.Short

        public interface Test {
          public val int8: Byte

          public val int16: Short

          public val int32: Int

          public val int64: Long

          public val int: Int

          public val long: Long
        
          public val none: Int
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for float scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test
        
        import kotlin.Double
        import kotlin.Float

        public interface Test {
          public val float: Float

          public val double: Double
        
          public val none: Double
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for date & time scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import java.time.LocalDate
        import java.time.LocalDateTime
        import java.time.LocalTime
        import java.time.OffsetDateTime

        public interface Test {
          public val dateOnly: LocalDate
        
          public val timeOnly: LocalTime
        
          public val dateTimeOnly: LocalDateTime
        
          public val dateTime: OffsetDateTime
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
