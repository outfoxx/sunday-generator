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
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI
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
          public fun fetchTest(
            @javax.ws.rs.HeaderParam(value = "obj") obj: io.test.Test,
            @javax.ws.rs.HeaderParam(value = "str") str: kotlin.String,
            @javax.ws.rs.HeaderParam(value = "int") @javax.ws.rs.DefaultValue(value = "5") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test optional header parameter generation`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI
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
          public fun fetchTest(
            @javax.ws.rs.HeaderParam(value = "obj") obj: io.test.Test?,
            @javax.ws.rs.HeaderParam(value = "str") str: kotlin.String?,
            @javax.ws.rs.HeaderParam(value = "int") int: kotlin.Int?
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test optional header parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI
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
          public fun fetchTest(
            @javax.ws.rs.HeaderParam(value = "obj") @javax.validation.Valid obj: io.test.Test?,
            @javax.ws.rs.HeaderParam(value = "str") str: kotlin.String?,
            @javax.ws.rs.HeaderParam(value = "int") int: kotlin.Int?
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of multiple header parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI
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
          public fun fetchTest(@javax.ws.rs.HeaderParam(value = "category") category: io.test.service.API.FetchTestCategoryHeaderParam, @javax.ws.rs.HeaderParam(value = "type") type: io.test.service.API.FetchTestTypeHeaderParam): javax.ws.rs.core.Response

          public enum class FetchTestCategoryHeaderParam {
            Politics,
            Science,
          }

          public enum class FetchTestTypeHeaderParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
