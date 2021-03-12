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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/Sunday] [RAML] Request Body Param Test")
class RequestBodyParamTest {

  @Test
  fun `test basic body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import io.test.Test
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(body: Test): Test = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests",
            body = body,
            contentTypes = this.defaultContentTypes,
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test optional body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service
        
        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import io.test.Test
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(body: Test?): Test = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests",
            body = body,
            contentTypes = this.defaultContentTypes,
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type`(
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service
        
        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.Any
        import kotlin.ByteArray
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(body: ByteArray): Map<String, Any> = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests",
            body = body,
            contentTypes = listOf(MediaType.OctetStream),
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }
}
