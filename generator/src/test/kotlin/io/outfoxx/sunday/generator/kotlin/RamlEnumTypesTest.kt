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
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
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
@DisplayName("[Kotlin] [RAML] Enum Types Test")
class RamlEnumTypesTest {

  @Test
  fun `test names generated for enums types & values`(
    @ResourceUri("raml/type-gen/types/scalar/enums.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val typeSpec = findType("io.test.TestEnum", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test
        
        public enum class TestEnum {
          None,
          Some,
          All,
          SnakeCase,
          KebabCase,
          InvalidChar,
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test names generated for enums types & values with jackson`(
    @ResourceUri("raml/type-gen/types/scalar/enums.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(JacksonAnnotations))

    val typeSpec = findType("io.test.TestEnum", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        package io.test

        import com.fasterxml.jackson.`annotation`.JsonProperty
        
        public enum class TestEnum {
          @JsonProperty(value = "none")
          None,
          @JsonProperty(value = "some")
          Some,
          @JsonProperty(value = "all")
          All,
          @JsonProperty(value = "snake_case")
          SnakeCase,
          @JsonProperty(value = "kebab-case")
          KebabCase,
          @JsonProperty(value = "invalid:char")
          InvalidChar,
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
