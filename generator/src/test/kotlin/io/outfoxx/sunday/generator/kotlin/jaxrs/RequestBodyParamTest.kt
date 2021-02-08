package io.outfoxx.sunday.generator.kotlin.jaxrs

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.findType
import io.outfoxx.sunday.generator.kotlin.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RequestBodyParamTest {

  @Test
  fun `test basic body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI
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
          public fun fetchTest(body: io.test.Test): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test basic body parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(ValidationConstraints))

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
          public fun fetchTest(@javax.validation.Valid body: io.test.Test): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test optional body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI
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
          public fun fetchTest(body: io.test.Test?): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type`(
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI
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
          @javax.ws.rs.Consumes(value = ["application/octet-stream"])
          public fun fetchTest(body: kotlin.ByteArray): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
