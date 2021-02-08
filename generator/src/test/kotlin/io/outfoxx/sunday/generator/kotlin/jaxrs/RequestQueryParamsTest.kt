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
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI
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
            @javax.ws.rs.QueryParam(value = "obj") obj: io.test.Test,
            @javax.ws.rs.QueryParam(value = "str") str: kotlin.String,
            @javax.ws.rs.QueryParam(value = "int") @javax.ws.rs.DefaultValue(value = "5") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test basic query parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI
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
            @javax.ws.rs.QueryParam(value = "obj") @javax.validation.Valid obj: io.test.Test,
            @javax.ws.rs.QueryParam(value = "str") str: kotlin.String,
            @javax.ws.rs.QueryParam(value = "int") @javax.ws.rs.DefaultValue(value = "5") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test optional query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI
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
            @javax.ws.rs.QueryParam(value = "obj") obj: io.test.Test?,
            @javax.ws.rs.QueryParam(value = "str") str: kotlin.String?,
            @javax.ws.rs.QueryParam(value = "int") int: kotlin.Int?
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of multiple query parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI
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
          public fun fetchTest(@javax.ws.rs.QueryParam(value = "category") category: io.test.service.API.FetchTestCategoryQueryParam, @javax.ws.rs.QueryParam(value = "type") type: io.test.service.API.FetchTestTypeQueryParam): javax.ws.rs.core.Response

          public enum class FetchTestCategoryQueryParam {
            Politics,
            Science,
          }

          public enum class FetchTestTypeQueryParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
