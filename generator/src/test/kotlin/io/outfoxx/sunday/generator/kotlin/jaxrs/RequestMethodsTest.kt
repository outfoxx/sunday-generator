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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Methods Test")
class RequestMethodsTest {

  private fun typeRegistry(): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Server,
      setOf(),
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @Test
  fun `test request method generation in server mode`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import javax.ws.rs.Consumes
        import javax.ws.rs.DELETE
        import javax.ws.rs.GET
        import javax.ws.rs.HEAD
        import javax.ws.rs.OPTIONS
        import javax.ws.rs.PATCH
        import javax.ws.rs.POST
        import javax.ws.rs.PUT
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Context
        import javax.ws.rs.core.Response
        import javax.ws.rs.core.UriInfo

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): Response

          @PUT
          @Path(value = "/tests")
          public fun putTest(body: Test): Response

          @POST
          @Path(value = "/tests")
          public fun postTest(body: Test, @Context uriInfo: UriInfo): Response

          @PATCH
          @Path(value = "/tests")
          public fun patchTest(body: Test): Response

          @DELETE
          @Path(value = "/tests")
          public fun deleteTest(): Response

          @HEAD
          @Path(value = "/tests")
          public fun headTest(): Response

          @OPTIONS
          @Path(value = "/tests")
          public fun optionsTest(): Response

          @PATCH
          @Path(value = "/tests2")
          public fun patchableTest(body: PatchableTest): Response

          @GET
          @Path(value = "/request")
          public fun requestTest(): Response

          @GET
          @Path(value = "/response")
          public fun responseTest(): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation in client mode`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf(), problemLibrary = KotlinProblemLibrary.ZALANDO, problemRfc = KotlinProblemRfc.RFC7807)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import javax.ws.rs.Consumes
        import javax.ws.rs.DELETE
        import javax.ws.rs.GET
        import javax.ws.rs.HEAD
        import javax.ws.rs.OPTIONS
        import javax.ws.rs.PATCH
        import javax.ws.rs.POST
        import javax.ws.rs.PUT
        import javax.ws.rs.Path
        import javax.ws.rs.Produces

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): Test

          @PUT
          @Path(value = "/tests")
          public fun putTest(body: Test): Test

          @POST
          @Path(value = "/tests")
          public fun postTest(body: Test): Test

          @PATCH
          @Path(value = "/tests")
          public fun patchTest(body: Test): Test

          @DELETE
          @Path(value = "/tests")
          public fun deleteTest()

          @HEAD
          @Path(value = "/tests")
          public fun headTest()

          @OPTIONS
          @Path(value = "/tests")
          public fun optionsTest()

          @PATCH
          @Path(value = "/tests2")
          public fun patchableTest(body: PatchableTest): Test

          @GET
          @Path(value = "/request")
          public fun requestTest(): Test

          @GET
          @Path(value = "/response")
          public fun responseTest(): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation in client mode with nullify`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf(), problemLibrary = KotlinProblemLibrary.ZALANDO, problemRfc = KotlinProblemRfc.RFC7807)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTest1OrNull(limit: Int): Test? = try {
            fetchTest1(limit)
          } catch(x: TestNotFoundProblem) {
            null
          } catch(x: AnotherNotFoundProblem) {
            null
          } catch(x: ThrowableProblem) {
            when (x.status?.statusCode) {
              404, 405 -> null
              else -> throw x
            }
          }

          @GET
          @Path(value = "/test1")
          public fun fetchTest1(@QueryParam(value = "limit") limit: Int): Test

          public fun fetchTest2OrNull(limit: Int): Test? = try {
            fetchTest2(limit)
          } catch(x: TestNotFoundProblem) {
            null
          } catch(x: AnotherNotFoundProblem) {
            null
          } catch(x: ThrowableProblem) {
            if (x.status?.statusCode == 404) {
              null
            } else {
              throw x
            }
          }

          @GET
          @Path(value = "/test2")
          public fun fetchTest2(@QueryParam(value = "limit") limit: Int): Test

          public fun fetchTest3OrNull(limit: Int): Test? = try {
            fetchTest3(limit)
          } catch(x: TestNotFoundProblem) {
            null
          } catch(x: AnotherNotFoundProblem) {
            null
          }

          @GET
          @Path(value = "/test3")
          public fun fetchTest3(@QueryParam(value = "limit") limit: Int): Test

          public fun fetchTest4OrNull(limit: Int): Test? = try {
            fetchTest4(limit)
          } catch(x: ThrowableProblem) {
            when (x.status?.statusCode) {
              404, 405 -> null
              else -> throw x
            }
          }

          @GET
          @Path(value = "/test4")
          public fun fetchTest4(@QueryParam(value = "limit") limit: Int): Test

          public fun fetchTest5OrNull(limit: Int): Test? = try {
            fetchTest5(limit)
          } catch(x: ThrowableProblem) {
            if (x.status?.statusCode == 404) {
              null
            } else {
              throw x
            }
          }

          @GET
          @Path(value = "/test5")
          public fun fetchTest5(@QueryParam(value = "limit") limit: Int): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation in client mode with nullify and response`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf(), problemLibrary = KotlinProblemLibrary.ZALANDO, problemRfc = KotlinProblemRfc.RFC7807)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            null,
            false,
            null,
            true,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response
        import kotlin.Int

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          public fun fetchTest1(@QueryParam(value = "limit") limit: Int): Response

          @GET
          @Path(value = "/test2")
          public fun fetchTest2(@QueryParam(value = "limit") limit: Int): Response

          @GET
          @Path(value = "/test3")
          public fun fetchTest3(@QueryParam(value = "limit") limit: Int): Response

          @GET
          @Path(value = "/test4")
          public fun fetchTest4(@QueryParam(value = "limit") limit: Int): Response

          @GET
          @Path(value = "/test5")
          public fun fetchTest5(@QueryParam(value = "limit") limit: Int): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            null,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            quarkus = true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.DELETE
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.HEAD
        import jakarta.ws.rs.OPTIONS
        import jakarta.ws.rs.PATCH
        import jakarta.ws.rs.POST
        import jakarta.ws.rs.PUT
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import jakarta.ws.rs.core.Context
        import jakarta.ws.rs.core.UriInfo
        import kotlin.Unit
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): RestResponse<Test>

          @PUT
          @Path(value = "/tests")
          public fun putTest(body: Test): RestResponse<Test>

          @POST
          @Path(value = "/tests")
          public fun postTest(body: Test, @Context uriInfo: UriInfo): RestResponse<Test>

          @PATCH
          @Path(value = "/tests")
          public fun patchTest(body: Test): RestResponse<Test>

          @DELETE
          @Path(value = "/tests")
          public fun deleteTest(): RestResponse<Unit>

          @HEAD
          @Path(value = "/tests")
          public fun headTest(): RestResponse<Unit>

          @OPTIONS
          @Path(value = "/tests")
          public fun optionsTest(): RestResponse<Unit>

          @PATCH
          @Path(value = "/tests2")
          public fun patchableTest(body: PatchableTest): RestResponse<Test>

          @GET
          @Path(value = "/request")
          public fun requestTest(): RestResponse<Test>

          @GET
          @Path(value = "/response")
          public fun responseTest(): RestResponse<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf(), problemLibrary = KotlinProblemLibrary.ZALANDO, problemRfc = KotlinProblemRfc.RFC7807)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            null,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            quarkus = true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test

        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.DELETE
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.HEAD
        import jakarta.ws.rs.OPTIONS
        import jakarta.ws.rs.PATCH
        import jakarta.ws.rs.POST
        import jakarta.ws.rs.PUT
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): Test

          @PUT
          @Path(value = "/tests")
          public fun putTest(body: Test): Test

          @POST
          @Path(value = "/tests")
          public fun postTest(body: Test): Test

          @PATCH
          @Path(value = "/tests")
          public fun patchTest(body: Test): Test

          @DELETE
          @Path(value = "/tests")
          public fun deleteTest()

          @HEAD
          @Path(value = "/tests")
          public fun headTest()

          @OPTIONS
          @Path(value = "/tests")
          public fun optionsTest()

          @PATCH
          @Path(value = "/tests2")
          public fun patchableTest(body: PatchableTest): Test

          @GET
          @Path(value = "/request")
          public fun requestTest(): Test

          @GET
          @Path(value = "/response")
          public fun responseTest(): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
