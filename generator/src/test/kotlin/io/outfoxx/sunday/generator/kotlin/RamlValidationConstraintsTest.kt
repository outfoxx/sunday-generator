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
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-arrays-generated-with-constraint-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-strings-generated-with-constraint-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-integer-numbers-generated-with-constraint-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-real-numbers-generated-with-constraint-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-container-element-validation-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-container-element-validation-annotations-disabled.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-array-element-scalar-constraints-with-container-element-validation.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-array-element-scalar-constraints-with-container-element-validation-disabled.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-array-element-scalar-constraints-with-nullable-union.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
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

    assertKotlinSnapshot(
      "RamlValidationConstraintsTest/test-map-value-scalar-constraints-with-container-element-validation.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
