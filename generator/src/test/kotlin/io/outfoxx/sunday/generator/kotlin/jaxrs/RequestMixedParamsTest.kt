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
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Mixed Params Test")
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{select}")
          public fun fetchTest(
            @PathParam(value = "select") select: FetchTestSelectUriParam,
            @QueryParam(value = "page") page: FetchTestPageQueryParam,
            @HeaderParam(value = "x-type") xType: FetchTestXTypeHeaderParam,
          ): Response

          public enum class FetchTestSelectUriParam {
            All,
            Limited,
          }

          public enum class FetchTestPageQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestXTypeHeaderParam {
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
  fun `test generation of multiple parameters of same name with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
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
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests/{type}")
          public fun fetchTest(
            @PathParam(value = "type") type: FetchTestTypeUriParam,
            @QueryParam(value = "type") type_: FetchTestTypeQueryParam,
            @HeaderParam(value = "type") type__: FetchTestTypeHeaderParam,
          ): Response

          public enum class FetchTestTypeUriParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeQueryParam {
            All,
            Limited,
          }

          public enum class FetchTestTypeHeaderParam {
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
