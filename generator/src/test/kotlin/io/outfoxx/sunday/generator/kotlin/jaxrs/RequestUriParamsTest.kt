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
@DisplayName("[Kotlin/JAXRS] [RAML] Request Uri Params Test")
class RequestUriParamsTest {

  @Test
  fun `test basic uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
          public fun fetchTest(
            @PathParam(value = "def") def: String,
            @PathParam(value = "obj") obj: Test,
            @PathParam(value = "str-req") strReq: String,
            @PathParam(value = "int") @DefaultValue(value = "5") int: Int,
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
  fun `test basic uri parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
          public fun fetchTest(
            @PathParam(value = "def") def: String,
            @PathParam(value = "obj") @Valid obj: Test,
            @PathParam(value = "str-req") strReq: String,
            @PathParam(value = "int") @DefaultValue(value = "5") int: Int,
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
  fun `test inherited uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.Any
        import kotlin.Int
        import kotlin.String
        import kotlin.collections.Map

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @PathParam(value = "obj") obj: Map<String, Any>,
            @PathParam(value = "str") str: String,
            @PathParam(value = "def") def: String,
            @PathParam(value = "int") int: Int,
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
  fun `test optional uri parameter generation`(
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.Int
        import kotlin.String

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}")
          public fun fetchTest(
            @PathParam(value = "def2") @DefaultValue(value = "10") def2: Int,
            @PathParam(value = "obj") obj: Test?,
            @PathParam(value = "str") str: String?,
            @PathParam(value = "def1") @DefaultValue(value = "test") def1: String,
            @PathParam(value = "int") int: Int?,
            @PathParam(value = "def") def: String,
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
  fun `test generation of multiple uri parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{category}/{type}")
          public fun fetchTest(@PathParam(value = "category") category: FetchTestCategoryUriParam,
              @PathParam(value = "type") type: FetchTestTypeUriParam): Response

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
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic uri parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
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
        import org.jboss.resteasy.reactive.RestPath
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
          public fun fetchTest(
            @RestPath def: String,
            @RestPath obj: Test,
            @RestPath strReq: String,
            @RestPath @DefaultValue(value = "5") int: Int,
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
  fun `test basic uri parameter generation with validation constraints and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
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
        import org.jboss.resteasy.reactive.RestPath
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str-req}/{int}/{def}")
          public fun fetchTest(
            @RestPath def: String,
            @RestPath @Valid obj: Test,
            @RestPath strReq: String,
            @RestPath @DefaultValue(value = "5") int: Int,
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
  fun `test inherited uri parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
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
        import kotlin.Int
        import kotlin.String
        import kotlin.collections.Map
        import org.jboss.resteasy.reactive.RestPath
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str}/{int}/{def}")
          public fun fetchTest(
            @RestPath obj: Map<String, Any>,
            @RestPath str: String,
            @RestPath def: String,
            @RestPath int: Int,
          ): RestResponse<Map<String, Any>>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional uri parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI,
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
        import org.jboss.resteasy.reactive.RestPath
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}")
          public fun fetchTest(
            @RestPath @DefaultValue(value = "10") def2: Int,
            @RestPath obj: Test?,
            @RestPath str: String?,
            @RestPath @DefaultValue(value = "test") def1: String,
            @RestPath int: Int?,
            @RestPath def: String,
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
  fun `test generation of multiple uri parameters with inline type definitions and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI,
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
        import org.jboss.resteasy.reactive.RestPath
        import org.jboss.resteasy.reactive.RestResponse

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{category}/{type}")
          public fun fetchTest(@RestPath category: FetchTestCategoryUriParam, @RestPath
              type: FetchTestTypeUriParam): RestResponse<Map<String, Any>>

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
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
