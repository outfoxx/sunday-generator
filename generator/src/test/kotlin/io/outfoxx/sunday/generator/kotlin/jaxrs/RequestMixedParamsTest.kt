package io.outfoxx.sunday.generator.kotlin.jaxrs

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.findType
import io.outfoxx.sunday.generator.kotlin.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{select}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "select") select: io.test.service.API.FetchTestSelectUriParam,
            @javax.ws.rs.QueryParam(value = "page") page: io.test.service.API.FetchTestPageQueryParam,
            @javax.ws.rs.HeaderParam(value = "x-type") xType: io.test.service.API.FetchTestXTypeHeaderParam
          ): javax.ws.rs.core.Response

          public enum class FetchTestPageQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestSelectUriParam {
            All,
            Limited,
          }
        
          public enum class FetchTestXTypeHeaderParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test @Disabled
  fun `test generation of multiple parameters of same name with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{type}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "type") type: io.test.service.API.FetchTestTypeUriParam,
            @javax.ws.rs.QueryParam(value = "type") type_: io.test.service.API.FetchTestTypeQueryParam,
            @javax.ws.rs.HeaderParam(value = "type") type__: io.test.service.API.FetchTestTypeHeaderParam
          ): javax.ws.rs.core.Response

          public enum class FetchTestTypeHeaderParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeUriParam {
            All,
            Limited,
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
