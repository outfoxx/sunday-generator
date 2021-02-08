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
class ResponseBodyContentTest {

  @Test
  fun `test basic body parameter generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI
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
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI
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
          @javax.ws.rs.Produces(value = ["application/octet-stream"])
          public fun fetchTest(): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test basic body parameter generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI
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
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI
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
          @javax.ws.rs.Produces(value = ["application/octet-stream"])
          public fun fetchTest(): kotlin.ByteArray
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of body parameter with inline type in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI
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
          public fun fetchTest(): io.test.service.API.FetchTestResponseBody
        
          public interface FetchTestResponseBody {
            public val value: kotlin.String
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of body parameter with inline type in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI
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
        
          public interface FetchTestResponseBody {
            public val value: kotlin.String
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of response body that is no content client mode`(
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI
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
          public fun fetchTest(): kotlin.Unit
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of response body that is no content server mode`(
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI
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
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
