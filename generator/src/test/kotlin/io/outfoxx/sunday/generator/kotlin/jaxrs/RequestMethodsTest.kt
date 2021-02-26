package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
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
@DisplayName("[Kotlin/JAXRS] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation in server mode`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI
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
        package io.test

        import com.fasterxml.jackson.databind.node.ObjectNode
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
          public fun patchableTest(body: ObjectNode): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test request method generation in client mode`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI
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
        package io.test

        import com.fasterxml.jackson.databind.node.ObjectNode
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
        import kotlin.Unit

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
          public fun deleteTest(): Unit

          @HEAD
          @Path(value = "/tests")
          public fun headTest(): Unit
        
          @OPTIONS
          @Path(value = "/tests")
          public fun optionsTest(): Unit
        
          @PATCH
          @Path(value = "/tests2")
          public fun patchableTest(body: ObjectNode): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      }
    )
  }

}
