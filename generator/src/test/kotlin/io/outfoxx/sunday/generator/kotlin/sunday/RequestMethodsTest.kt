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
@DisplayName("[Kotlin/Sunday] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
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
        import kotlin.Unit
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          public suspend fun fetchTest(): Test = this.requestFactory
            .result(
              method = Method.Get,
              pathTemplate = "/tests",
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun putTest(body: Test): Test = this.requestFactory
            .result(
              method = Method.Put,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun postTest(body: Test): Test = this.requestFactory
            .result(
              method = Method.Post,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun patchTest(body: Test): Test = this.requestFactory
            .result(
              method = Method.Patch,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun deleteTest(): Unit = this.requestFactory
            .result(
              method = Method.Delete,
              pathTemplate = "/tests"
            )

          public suspend fun headTest(): Unit = this.requestFactory
            .result(
              method = Method.Head,
              pathTemplate = "/tests"
            )

          public suspend fun optionsTest(): Unit = this.requestFactory
            .result(
              method = Method.Options,
              pathTemplate = "/tests"
            )

          public suspend fun patchableTest(body: PatchableTest): Test = this.requestFactory
            .result(
              method = Method.Patch,
              pathTemplate = "/tests2",
              body = body,
              contentTypes = this.defaultContentTypes,
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
  fun `test request method generation with result response`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinSundayGenerator.Options(
            true,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import io.outfoxx.sunday.http.ResultResponse
        import kotlin.Unit
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(MediaType.JSON),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          public suspend fun fetchTest(): ResultResponse<Test> = this.requestFactory
            .resultResponse(
              method = Method.Get,
              pathTemplate = "/tests",
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun putTest(body: Test): ResultResponse<Test> = this.requestFactory
            .resultResponse(
              method = Method.Put,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun postTest(body: Test): ResultResponse<Test> = this.requestFactory
            .resultResponse(
              method = Method.Post,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun patchTest(body: Test): ResultResponse<Test> = this.requestFactory
            .resultResponse(
              method = Method.Patch,
              pathTemplate = "/tests",
              body = body,
              contentTypes = this.defaultContentTypes,
              acceptTypes = this.defaultAcceptTypes
            )

          public suspend fun deleteTest(): ResultResponse<Unit> = this.requestFactory
            .resultResponse(
              method = Method.Delete,
              pathTemplate = "/tests"
            )

          public suspend fun headTest(): ResultResponse<Unit> = this.requestFactory
            .resultResponse(
              method = Method.Head,
              pathTemplate = "/tests"
            )

          public suspend fun optionsTest(): ResultResponse<Unit> = this.requestFactory
            .resultResponse(
              method = Method.Options,
              pathTemplate = "/tests"
            )

          public suspend fun patchableTest(body: PatchableTest): ResultResponse<Test> = this.requestFactory
            .resultResponse(
              method = Method.Patch,
              pathTemplate = "/tests2",
              body = body,
              contentTypes = this.defaultContentTypes,
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
  fun `test request method generation with nullify`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
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
        import kotlin.Int
        import kotlin.collections.List
        import org.zalando.problem.ThrowableProblem

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          init {
            requestFactory.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
            requestFactory.registerProblem("http://example.com/another_not_found",
                AnotherNotFoundProblem::class)
          }
          public suspend fun fetchTestOrNull(limit: Int): Test? = try {
            fetchTest(limit)
          } catch(x: ThrowableProblem) {
            when {
              x is TestNotFoundProblem -> null
              x is AnotherNotFoundProblem -> null
              x.status?.statusCode == 404 || x.status?.statusCode == 405 -> null
              else -> throw x
            }
          }

          public suspend fun fetchTest(limit: Int): Test = this.requestFactory
            .result(
              method = Method.Get,
              pathTemplate = "/tests",
              queryParameters = mapOf(
                "limit" to limit
              ),
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
