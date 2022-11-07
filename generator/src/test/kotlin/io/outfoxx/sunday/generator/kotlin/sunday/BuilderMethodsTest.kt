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
@DisplayName("[Kotlin/Sunday] [RAML] Builder Methods Test")
class BuilderMethodsTest {

  @Test
  fun `test request builder method generation `(
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.collections.List
        import okhttp3.Request

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          public suspend fun fetchTest(): Request = this.requestFactory.request(
            method = Method.Get,
            pathTemplate = "/test/request",
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test response builder method generation `(
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import kotlin.collections.List
        import okhttp3.Response

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          public suspend fun fetchTest(): Response = this.requestFactory.response(
            method = Method.Get,
            pathTemplate = "/test/response",
            acceptTypes = this.defaultAcceptTypes
          )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
