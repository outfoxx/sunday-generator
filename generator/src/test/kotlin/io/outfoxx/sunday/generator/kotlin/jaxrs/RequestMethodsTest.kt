package io.outfoxx.sunday.generator.kotlin.jaxrs

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.findType
import io.outfoxx.sunday.generator.kotlin.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
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

    val type = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        @javax.ws.rs.Produces(value = ["application/json"])
        @javax.ws.rs.Consumes(value = ["application/json"])
        public interface API {
          @javax.ws.rs.GET
          @javax.ws.rs.Path(value = "/tests")
          public fun fetchTest(): javax.ws.rs.core.Response
        
          @javax.ws.rs.PUT
          @javax.ws.rs.Path(value = "/tests")
          public fun putTest(body: io.test.Test): javax.ws.rs.core.Response
        
          @javax.ws.rs.POST
          @javax.ws.rs.Path(value = "/tests")
          public fun postTest(body: io.test.Test, @javax.ws.rs.core.Context uriInfo: javax.ws.rs.core.UriInfo): javax.ws.rs.core.Response
        
          @javax.ws.rs.PATCH
          @javax.ws.rs.Path(value = "/tests")
          public fun patchTest(body: io.test.Test): javax.ws.rs.core.Response
        
          @javax.ws.rs.DELETE
          @javax.ws.rs.Path(value = "/tests")
          public fun deleteTest(): javax.ws.rs.core.Response
        
          @javax.ws.rs.HEAD
          @javax.ws.rs.Path(value = "/tests")
          public fun headTest(): javax.ws.rs.core.Response
        
          @javax.ws.rs.OPTIONS
          @javax.ws.rs.Path(value = "/tests")
          public fun optionsTest(): javax.ws.rs.core.Response
        
          @javax.ws.rs.PATCH
          @javax.ws.rs.Path(value = "/tests2")
          public fun patchableTest(body: com.fasterxml.jackson.databind.node.ObjectNode): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
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

    val type = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        @javax.ws.rs.Produces(value = ["application/json"])
        @javax.ws.rs.Consumes(value = ["application/json"])
        public interface API {
          @javax.ws.rs.GET
          @javax.ws.rs.Path(value = "/tests")
          public fun fetchTest(): io.test.Test
        
          @javax.ws.rs.PUT
          @javax.ws.rs.Path(value = "/tests")
          public fun putTest(body: io.test.Test): io.test.Test
        
          @javax.ws.rs.POST
          @javax.ws.rs.Path(value = "/tests")
          public fun postTest(body: io.test.Test): io.test.Test
        
          @javax.ws.rs.PATCH
          @javax.ws.rs.Path(value = "/tests")
          public fun patchTest(body: io.test.Test): io.test.Test
        
          @javax.ws.rs.DELETE
          @javax.ws.rs.Path(value = "/tests")
          public fun deleteTest(): kotlin.Unit

          @javax.ws.rs.HEAD
          @javax.ws.rs.Path(value = "/tests")
          public fun headTest(): kotlin.Unit
        
          @javax.ws.rs.OPTIONS
          @javax.ws.rs.Path(value = "/tests")
          public fun optionsTest(): kotlin.Unit
        
          @javax.ws.rs.PATCH
          @javax.ws.rs.Path(value = "/tests2")
          public fun patchableTest(body: com.fasterxml.jackson.databind.node.ObjectNode): io.test.Test
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
