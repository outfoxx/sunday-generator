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
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in interfaces`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlArrayTypesTest/test-generated-nullability-of-array-types-and-elements-in-interfaces.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated collection interface`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlArrayTypesTest/test-generated-collection-interface.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of array types and elements in classes`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlArrayTypesTest/test-generated-nullability-of-array-types-and-elements-in-classes.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated collection class`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlArrayTypesTest/test-generated-collection-class.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated primitive class`(
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ImplementModel))

    val typeSpec = findType("io.test.Test", generateTypes(testUri, typeRegistry))

    assertKotlinSnapshot(
      "RamlArrayTypesTest/test-generated-primitive-class.output.kt",
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
