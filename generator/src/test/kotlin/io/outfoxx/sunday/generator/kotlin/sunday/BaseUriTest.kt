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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/Sunday] [RAML] Base URI Test")
class BaseUriTest {

  @Test
  fun `test baseUrl generation in API`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinSundayGenerator(
          document,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.URITemplate
        import io.outfoxx.sunday.http.Method
        import io.test.Environment
        import kotlin.String
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON)
        ) {
          public suspend fun fetchTest(): String = this.requestFactory.result(
            method = Method.Get,
            pathTemplate = "/tests",
            acceptTypes = this.defaultAcceptTypes
          )

          public companion object {
            public fun baseURL(
              server: String = "master",
              environment: Environment = Environment.Sbx,
              version: String = "1"
            ): URITemplate = URITemplate(
              "http://{server}.{environment}.example.com/api/{version}",
              mapOf("server" to server, "environment" to environment, "version" to version)
            )
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )

    val envTypeSpec = builtTypes[ClassName.bestGuess("io.test.Environment")]
    assertNotNull(envTypeSpec)
  }
}
