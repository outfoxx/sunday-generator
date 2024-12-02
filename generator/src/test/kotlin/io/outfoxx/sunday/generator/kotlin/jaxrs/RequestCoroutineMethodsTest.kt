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
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Coroutine Methods Test")
class RequestCoroutineMethodsTest {

  @Test
  fun `test basic coroutines method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public suspend fun fetchTest(): Response

          @GET
          @Path(value = "/tests/derived")
          public suspend fun fetchDerivedTest(): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Base
        import io.test.Test
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public suspend fun fetchTest(): Test

          @GET
          @Path(value = "/tests/derived")
          public suspend fun fetchDerivedTest(): Base
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with nullify`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
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
          public suspend fun fetchTest1OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest1(@QueryParam(value = "limit") limit: Int): Test

          public suspend fun fetchTest2OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest2(@QueryParam(value = "limit") limit: Int): Test

          public suspend fun fetchTest3OrNull(limit: Int): Test? = try {
            fetchTest3(limit)
          } catch(x: TestNotFoundProblem) {
            null
          } catch(x: AnotherNotFoundProblem) {
            null
          }

          @GET
          @Path(value = "/test3")
          public suspend fun fetchTest3(@QueryParam(value = "limit") limit: Int): Test

          public suspend fun fetchTest4OrNull(limit: Int): Test? = try {
            fetchTest4(limit)
          } catch(x: ThrowableProblem) {
            when (x.status?.statusCode) {
              404, 405 -> null
              else -> throw x
            }
          }

          @GET
          @Path(value = "/test4")
          public suspend fun fetchTest4(@QueryParam(value = "limit") limit: Int): Test

          public suspend fun fetchTest5OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest5(@QueryParam(value = "limit") limit: Int): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Test1
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.sse.OutboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with multiple default types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Test1
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.sse.OutboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json", "application/yaml"])
        @Consumes(value = ["application/json", "application/yaml"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Test1
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.sse.InboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<InboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with multiple default types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Test1
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.sse.InboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json", "application/yaml"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<InboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in server mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Base
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Base>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Base>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in client mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
            null,
            false,
            null,
            false,
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
        package io.test.service

        import io.test.Base
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Base>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Base>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Base
        import io.test.Test
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public suspend fun fetchTest(): RestResponse<Test>

          @GET
          @Path(value = "/tests/derived")
          public suspend fun fetchDerivedTest(): RestResponse<Base>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Base
        import io.test.Test
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public suspend fun fetchTest(): Test

          @GET
          @Path(value = "/tests/derived")
          public suspend fun fetchDerivedTest(): Base
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with nullify and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlin.Int
        import org.jboss.resteasy.reactive.RestQuery
        import org.zalando.problem.ThrowableProblem

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public suspend fun fetchTest1OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest1(@RestQuery limit: Int): Test

          public suspend fun fetchTest2OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest2(@RestQuery limit: Int): Test

          public suspend fun fetchTest3OrNull(limit: Int): Test? = try {
            fetchTest3(limit)
          } catch(x: TestNotFoundProblem) {
            null
          } catch(x: AnotherNotFoundProblem) {
            null
          }

          @GET
          @Path(value = "/test3")
          public suspend fun fetchTest3(@RestQuery limit: Int): Test

          public suspend fun fetchTest4OrNull(limit: Int): Test? = try {
            fetchTest4(limit)
          } catch(x: ThrowableProblem) {
            when (x.status?.statusCode) {
              404, 405 -> null
              else -> throw x
            }
          }

          @GET
          @Path(value = "/test4")
          public suspend fun fetchTest4(@RestQuery limit: Int): Test

          public suspend fun fetchTest5OrNull(limit: Int): Test? = try {
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
          public suspend fun fetchTest5(@RestQuery limit: Int): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Test1
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import jakarta.ws.rs.sse.OutboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with quarkus option enabled and multiple defaults types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Test1
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import jakarta.ws.rs.sse.OutboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json", "application/yaml"])
        @Consumes(value = ["application/json", "application/yaml"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<OutboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Test1
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import jakarta.ws.rs.sse.InboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<InboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with quarkus option enabled and multiple defaults types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Test1
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import jakarta.ws.rs.sse.InboundSseEvent
        import kotlin.Any
        import kotlinx.coroutines.flow.Flow

        @Produces(value = ["application/json", "application/yaml"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimple(): Flow<Test1>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsDiscriminated(): Flow<Any>

          @GET
          @Path(value = "/test3")
          @Produces(value = ["text/event-stream"])
          public suspend fun fetchEventsSimpleSse(): Flow<InboundSseEvent>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Base
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlinx.coroutines.flow.Flow
        import org.jboss.resteasy.reactive.RestStreamElementType

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          @RestStreamElementType(value = "application/json")
          public suspend fun fetchEventsSimple(): Flow<Base>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          @RestStreamElementType(value = "application/json")
          public suspend fun fetchEventsDiscriminated(): Flow<Base>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = true,
            coroutineServiceMethods = true,
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
        package io.test.service

        import io.test.Base
        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlinx.coroutines.flow.Flow
        import org.jboss.resteasy.reactive.RestStreamElementType

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/test1")
          @Produces(value = ["text/event-stream"])
          @RestStreamElementType(value = "application/json")
          public suspend fun fetchEventsSimple(): Flow<Base>

          @GET
          @Path(value = "/test2")
          @Produces(value = ["text/event-stream"])
          @RestStreamElementType(value = "application/json")
          public suspend fun fetchEventsDiscriminated(): Flow<Base>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
