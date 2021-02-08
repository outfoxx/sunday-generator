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
class RequestUriParamsTest {

  @Test
  fun `test basic uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "def") def: kotlin.String,
            @javax.ws.rs.PathParam(value = "obj") obj: io.test.Test,
            @javax.ws.rs.PathParam(value = "str") str: kotlin.String,
            @javax.ws.rs.PathParam(value = "int") @javax.ws.rs.DefaultValue(value = "5") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test basic uri parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "def") def: kotlin.String,
            @javax.ws.rs.PathParam(value = "obj") @javax.validation.Valid obj: io.test.Test,
            @javax.ws.rs.PathParam(value = "str") str: kotlin.String,
            @javax.ws.rs.PathParam(value = "int") @javax.ws.rs.DefaultValue(value = "5") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test inherited uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "obj") obj: kotlin.collections.Map<kotlin.String, kotlin.Any>,
            @javax.ws.rs.PathParam(value = "str") str: kotlin.String,
            @javax.ws.rs.PathParam(value = "def") def: kotlin.String,
            @javax.ws.rs.PathParam(value = "int") int: kotlin.Int
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test optional uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @javax.ws.rs.PathParam(value = "def") def: kotlin.String,
            @javax.ws.rs.PathParam(value = "obj") obj: io.test.Test?,
            @javax.ws.rs.PathParam(value = "str") str: kotlin.String?,
            @javax.ws.rs.PathParam(value = "int") int: kotlin.Int?
          ): javax.ws.rs.core.Response
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test generation of multiple uri parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI
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
          @javax.ws.rs.Path(value = "/tests/{category}/{type}")
          public fun fetchTest(@javax.ws.rs.PathParam(value = "category") category: io.test.service.API.FetchTestCategoryUriParam, @javax.ws.rs.PathParam(value = "type") type: io.test.service.API.FetchTestTypeUriParam): javax.ws.rs.core.Response

          public enum class FetchTestCategoryUriParam {
            Politics,
            Science,
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
