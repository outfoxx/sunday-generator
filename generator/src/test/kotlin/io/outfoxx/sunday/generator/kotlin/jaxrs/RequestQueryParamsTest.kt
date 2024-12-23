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
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Query Params Test")
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
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

        import io.test.Test
        import javax.ws.rs.Consumes
        import javax.ws.rs.DefaultValue
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @QueryParam(value = "obj") obj: Test,
            @QueryParam(value = "str-req") strReq: String,
            @QueryParam(value = "int") @DefaultValue(value = "5") int: Int,
          ): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic query parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ValidationConstraints))

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
        import javax.validation.Valid
        import javax.ws.rs.Consumes
        import javax.ws.rs.DefaultValue
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @QueryParam(value = "obj") @Valid obj: Test,
            @QueryParam(value = "str-req") strReq: String,
            @QueryParam(value = "int") @DefaultValue(value = "5") int: Int,
          ): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
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

        import io.test.Test
        import javax.ws.rs.Consumes
        import javax.ws.rs.DefaultValue
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @QueryParam(value = "obj") obj: Test?,
            @QueryParam(value = "str") str: String?,
            @QueryParam(value = "int") int: Int?,
            @QueryParam(value = "def1") @DefaultValue(value = "test") def1: String,
            @QueryParam(value = "def2") @DefaultValue(value = "10") def2: Int,
          ): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple query parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI,
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
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "category") category: FetchTestCategoryQueryParam,
              @QueryParam(value = "type") type: FetchTestTypeQueryParam): Response

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
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic query parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
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
        import jakarta.ws.rs.DefaultValue
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlin.Int
        import kotlin.String
        import org.jboss.resteasy.reactive.RestQuery
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @RestQuery obj: Test,
            @RestQuery strReq: String,
            @RestQuery @DefaultValue(value = "5") int: Int,
          ): RestResponse<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic query parameter generation with validation constraints and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(ValidationConstraints))

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
        import jakarta.ws.rs.DefaultValue
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import javax.validation.Valid
        import kotlin.Int
        import kotlin.String
        import org.jboss.resteasy.reactive.RestQuery
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @RestQuery @Valid obj: Test,
            @RestQuery strReq: String,
            @RestQuery @DefaultValue(value = "5") int: Int,
          ): RestResponse<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional query parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
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
        import jakarta.ws.rs.DefaultValue
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlin.Int
        import kotlin.String
        import org.jboss.resteasy.reactive.RestQuery
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(
            @RestQuery obj: Test?,
            @RestQuery str: String?,
            @RestQuery int: Int?,
            @RestQuery @DefaultValue(value = "test") def1: String,
            @RestQuery @DefaultValue(value = "10") def2: Int,
          ): RestResponse<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple query parameters with inline type definitions and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI,
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

        import jakarta.ws.rs.Consumes
        import jakarta.ws.rs.GET
        import jakarta.ws.rs.Path
        import jakarta.ws.rs.Produces
        import kotlin.Any
        import kotlin.String
        import kotlin.collections.Map
        import org.jboss.resteasy.reactive.RestQuery
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(@RestQuery category: FetchTestCategoryQueryParam, @RestQuery
              type: FetchTestTypeQueryParam): RestResponse<Map<String, Any>>

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
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
