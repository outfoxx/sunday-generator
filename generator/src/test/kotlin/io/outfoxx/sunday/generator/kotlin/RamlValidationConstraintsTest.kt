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
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Validation Constraints Test")
class RamlValidationConstraintsTest {

  @Test
  fun `test arrays generated with constraint annotations`(
    @ResourceUri("raml/type-gen/validation/constraints-array.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Size
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Set

        public interface Test {
          @get:Size(min = 5)
          public val minList: List<String>

          @get:Size(max = 10)
          public val maxList: List<String>

          @get:Size(min = 15)
          public val minSet: Set<String>

          @get:Size(max = 20)
          public val maxSet: Set<String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test strings generated with constraint annotations`(
    @ResourceUri("raml/type-gen/validation/constraints-string.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Pattern
        import javax.validation.constraints.Size
        import kotlin.String

        public interface Test {
          @get:Pattern(regexp = ${'"'}""^[a-zA-Z0-9]+$""${'"'})
          public val pattern: String

          @get:Size(min = 5)
          public val min: String

          @get:Size(max = 10)
          public val max: String
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test integer numbers generated with constraint annotations`(
    @ResourceUri("raml/type-gen/validation/constraints-integer.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Max
        import javax.validation.constraints.Min
        import kotlin.Byte
        import kotlin.Int
        import kotlin.Long
        import kotlin.Short

        public interface Test {
          @get:Min(value = 1)
          public val byteMin: Byte

          @get:Max(value = 2)
          public val byteMax: Byte

          public val byteMultiple: Byte

          @get:Min(value = 4)
          public val shortMin: Short

          @get:Max(value = 5)
          public val shortMax: Short

          public val shortMultiple: Short

          @get:Min(value = 7)
          public val intMin: Int

          @get:Max(value = 8)
          public val intMax: Int

          public val intMultiple: Int

          @get:Min(value = 10)
          public val longMin: Long

          @get:Max(value = 11)
          public val longMax: Long

          public val longMultiple: Long
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test real numbers generated with constraint annotations`(
    @ResourceUri("raml/type-gen/validation/constraints-number.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.DecimalMax
        import javax.validation.constraints.DecimalMin
        import kotlin.Double
        import kotlin.Float

        public interface Test {
          @get:DecimalMin(value = "1.0")
          public val floatMin: Float

          @get:DecimalMax(value = "2.0")
          public val floatMax: Float

          public val floatMultiple: Float

          @get:DecimalMin(value = "4.0")
          public val doubleMin: Double

          @get:DecimalMax(value = "5.0")
          public val doubleMax: Double

          public val doubleMultiple: Double
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test container element validation annotations`(
    @ResourceUri("raml/type-gen/validation/constraints-container-valid.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        Server,
        setOf(ValidationConstraints, KotlinTypeRegistry.Option.ContainerElementValid),
      )

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.Valid
        import kotlin.Any
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map

        public interface Test {
          @get:Valid
          public val child: Child

          public val children: List<@Valid Child>

          public val childMap: Map<String, Any>

          public val names: List<String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test container element validation annotations disabled`(
    @ResourceUri("raml/type-gen/validation/constraints-container-valid.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.Valid
        import kotlin.Any
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map

        public interface Test {
          @get:Valid
          public val child: Child

          public val children: List<Child>

          @get:Valid
          public val childMap: Map<String, Any>

          public val names: List<String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test array element scalar constraints with container element validation`(
    @ResourceUri("raml/type-gen/validation/constraints-array-elements.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        Server,
        setOf(ValidationConstraints, KotlinTypeRegistry.Option.ContainerElementValid),
      )

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Pattern
        import javax.validation.constraints.Size
        import kotlin.String
        import kotlin.collections.List

        public interface Test {
          public val codes: List<@Size(max = 5, min = 2) @Pattern(regexp = ${'"'}""^[A-Z]+$""${'"'}) String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test array element scalar constraints with container element validation disabled`(
    @ResourceUri("raml/type-gen/validation/constraints-array-elements.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf(ValidationConstraints))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Pattern
        import javax.validation.constraints.Size
        import kotlin.String
        import kotlin.collections.List

        public interface Test {
          @get:Size(
            max = 5,
            min = 2,
          )
          @get:Pattern(regexp = ${'"'}""^[A-Z]+$""${'"'})
          public val codes: List<String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test array element scalar constraints with nullable union`(
    @ResourceUri("raml/type-gen/validation/constraints-array-elements-union.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        Server,
        setOf(ValidationConstraints, KotlinTypeRegistry.Option.ContainerElementValid),
      )

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Pattern
        import javax.validation.constraints.Size
        import kotlin.String
        import kotlin.collections.List

        public interface Test {
          public val codes: List<@Size(max = 5, min = 2) @Pattern(regexp = ${'"'}""^[A-Z]+$""${'"'}) String?>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test map value scalar constraints with container element validation`(
    @ResourceUri("raml/type-gen/validation/constraints-map-values.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        Server,
        setOf(ValidationConstraints, KotlinTypeRegistry.Option.ContainerElementValid),
      )

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import javax.validation.constraints.Pattern
        import javax.validation.constraints.Size
        import kotlin.String
        import kotlin.collections.Map

        public interface Test {
          public val labels: Map<String, @Size(max = 5, min = 2) @Pattern(regexp = ${'"'}""^[a-z]+$""${'"'}) String>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
