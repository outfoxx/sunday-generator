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
@DisplayName("[Kotlin/JAXRS] [RAML] Response SSE Test")
class ResponseSseTest {

  @Test
  fun `test basic sse method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

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
      package io.test.service

      import javax.ws.rs.Consumes
      import javax.ws.rs.GET
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.core.Context
      import javax.ws.rs.core.Response
      import javax.ws.rs.sse.Sse
      import javax.ws.rs.sse.SseEventSink

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        @Produces(value = ["text/event-stream"])
        public fun fetchEvents(@Context sse: Sse, @Context sseEvents: SseEventSink)

        @GET
        @Path(value = "/tests/server")
        @Produces(value = ["text/event-stream"])
        public fun fetchEventsServer(@Context sse: Sse, @Context sseEvents: SseEventSink)

        @GET
        @Path(value = "/tests/client")
        public fun fetchEventsClient(): Response
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic sse method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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
      package io.test.service

      import io.test.Test
      import javax.ws.rs.Consumes
      import javax.ws.rs.GET
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.sse.SseEventSource

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        @Produces(value = ["text/event-stream"])
        public fun fetchEvents(): SseEventSource

        @GET
        @Path(value = "/tests/server")
        public fun fetchEventsServer(): Test

        @GET
        @Path(value = "/tests/client")
        @Produces(value = ["text/event-stream"])
        public fun fetchEventsClient(): SseEventSource
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic sse method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
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
      package io.test.service

      import io.test.Test
      import jakarta.ws.rs.Consumes
      import jakarta.ws.rs.GET
      import jakarta.ws.rs.Path
      import jakarta.ws.rs.Produces
      import jakarta.ws.rs.core.Context
      import jakarta.ws.rs.sse.Sse
      import jakarta.ws.rs.sse.SseEventSink
      import org.jboss.resteasy.reactive.RestResponse
      import org.jboss.resteasy.reactive.RestStreamElementType

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        @Produces(value = ["text/event-stream"])
        @RestStreamElementType(value = "application/json")
        public fun fetchEvents(@Context sse: Sse, @Context sseEvents: SseEventSink)

        @GET
        @Path(value = "/tests/server")
        @Produces(value = ["text/event-stream"])
        @RestStreamElementType(value = "application/json")
        public fun fetchEventsServer(@Context sse: Sse, @Context sseEvents: SseEventSink)

        @GET
        @Path(value = "/tests/client")
        public fun fetchEventsClient(): RestResponse<Test>
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic sse method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI,
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
      package io.test.service

      import io.test.Test
      import jakarta.ws.rs.Consumes
      import jakarta.ws.rs.GET
      import jakarta.ws.rs.Path
      import jakarta.ws.rs.Produces
      import jakarta.ws.rs.sse.SseEventSource
      import org.jboss.resteasy.reactive.RestStreamElementType

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        @Produces(value = ["text/event-stream"])
        @RestStreamElementType(value = "application/json")
        public fun fetchEvents(): SseEventSource

        @GET
        @Path(value = "/tests/server")
        public fun fetchEventsServer(): Test

        @GET
        @Path(value = "/tests/client")
        @Produces(value = ["text/event-stream"])
        @RestStreamElementType(value = "application/json")
        public fun fetchEventsClient(): SseEventSource
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
