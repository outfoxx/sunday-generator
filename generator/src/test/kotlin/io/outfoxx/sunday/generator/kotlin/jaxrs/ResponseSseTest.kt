package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.findType
import io.outfoxx.sunday.generator.kotlin.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/JAXRS] [RAML] Response SSE Test")
class ResponseSseTest {

  @Test
  fun `test basic sse method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
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
        import javax.ws.rs.sse.Sse
        import javax.ws.rs.sse.SseEventSink
        import kotlin.Unit

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchEvents(@Context sse: Sse, @Context sseEvents: SseEventSink): Unit
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test basic sse method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-sse.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
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
        import javax.ws.rs.sse.SseEventSource

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchEvents(): SseEventSource
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

}
