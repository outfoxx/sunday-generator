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
@DisplayName("[Kotlin/JAXRS] [RAML] Request Header Params Test")
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI,
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
      import javax.ws.rs.DELETE
      import javax.ws.rs.DefaultValue
      import javax.ws.rs.GET
      import javax.ws.rs.HeaderParam
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.core.Response
      import kotlin.Int
      import kotlin.String

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @HeaderParam(value = "obj") obj: Test,
          @HeaderParam(value = "str-req") strReq: String,
          @HeaderParam(value = "int") @DefaultValue(value = "5") int: Int,
        ): Response

        @DELETE
        @Path(value = "/tests")
        public fun deleteTest(): Response
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
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
      import javax.ws.rs.HeaderParam
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.core.Response
      import kotlin.Int
      import kotlin.String

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @HeaderParam(value = "obj") obj: Test?,
          @HeaderParam(value = "str") str: String?,
          @HeaderParam(value = "int") int: Int?,
          @HeaderParam(value = "def1") @DefaultValue(value = "test") def1: String,
          @HeaderParam(value = "def2") @DefaultValue(value = "10") def2: Int,
        ): Response
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation with validation constraints`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
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
      import javax.ws.rs.HeaderParam
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.core.Response
      import kotlin.Int
      import kotlin.String

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @HeaderParam(value = "obj") @Valid obj: Test?,
          @HeaderParam(value = "str") str: String?,
          @HeaderParam(value = "int") int: Int?,
          @HeaderParam(value = "def1") @DefaultValue(value = "test") def1: String,
          @HeaderParam(value = "def2") @DefaultValue(value = "10") def2: Int,
        ): Response
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple header parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI,
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
      import javax.ws.rs.HeaderParam
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.core.Response

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(@HeaderParam(value = "category") category: FetchTestCategoryHeaderParam,
            @HeaderParam(value = "type") type: FetchTestTypeHeaderParam): Response

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
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic header parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI,
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
      import jakarta.ws.rs.DELETE
      import jakarta.ws.rs.DefaultValue
      import jakarta.ws.rs.GET
      import jakarta.ws.rs.Path
      import jakarta.ws.rs.Produces
      import kotlin.Int
      import kotlin.String
      import kotlin.Unit
      import org.jboss.resteasy.reactive.RestHeader
      import org.jboss.resteasy.reactive.RestResponse

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @RestHeader(value = "obj") obj: Test,
          @RestHeader(value = "str-req") strReq: String,
          @RestHeader(value = "int") @DefaultValue(value = "5") int: Int,
        ): RestResponse<Test>

        @DELETE
        @Path(value = "/tests")
        public fun deleteTest(): RestResponse<Unit>
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test constant header parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
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
      import jakarta.ws.rs.PUT
      import jakarta.ws.rs.Path
      import jakarta.ws.rs.Produces
      import kotlin.String
      import org.jboss.resteasy.reactive.RestHeader
      import org.jboss.resteasy.reactive.RestResponse

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @PUT
        @Path(value = "/tests")
        public fun putTest(@RestHeader(value = "x-custom") xCustom: String): RestResponse<Test>
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test constant header parameter generation with quarkus option enabled in client mode`(
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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
      import jakarta.ws.rs.PUT
      import jakarta.ws.rs.Path
      import jakarta.ws.rs.Produces
      import kotlin.String
      import org.eclipse.microprofile.rest.client.`annotation`.ClientHeaderParam
      import org.jboss.resteasy.reactive.RestHeader

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @PUT
        @Path(value = "/tests")
        @ClientHeaderParam(
          name = "Expect",
          value = "100-continue",
        )
        public fun putTest(@RestHeader(value = "x-custom") xCustom: String): Test
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
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
      import org.jboss.resteasy.reactive.RestHeader
      import org.jboss.resteasy.reactive.RestResponse

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @RestHeader(value = "obj") obj: Test?,
          @RestHeader(value = "str") str: String?,
          @RestHeader(value = "int") int: Int?,
          @RestHeader(value = "def1") @DefaultValue(value = "test") def1: String,
          @RestHeader(value = "def2") @DefaultValue(value = "10") def2: Int,
        ): RestResponse<Test>
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation with validation constraints and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
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
      import org.jboss.resteasy.reactive.RestHeader
      import org.jboss.resteasy.reactive.RestResponse

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(
          @RestHeader(value = "obj") @Valid obj: Test?,
          @RestHeader(value = "str") str: String?,
          @RestHeader(value = "int") int: Int?,
          @RestHeader(value = "def1") @DefaultValue(value = "test") def1: String,
          @RestHeader(value = "def2") @DefaultValue(value = "10") def2: Int,
        ): RestResponse<Test>
      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple header parameters with inline type definitions and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI,
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
      import org.jboss.resteasy.reactive.RestHeader
      import org.jboss.resteasy.reactive.RestResponse

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests")
        public fun fetchTest(@RestHeader(value = "category") category: FetchTestCategoryHeaderParam,
            @RestHeader(value = "type") type: FetchTestTypeHeaderParam): RestResponse<Map<String, Any>>

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
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
