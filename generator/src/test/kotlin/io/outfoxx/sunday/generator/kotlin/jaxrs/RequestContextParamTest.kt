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
@DisplayName("[Kotlin/JAXRS] [RAML] Request Context Param Test")
class RequestContextParamTest {

  @Test
  fun `test jaxrsContext annotation adds context parameters`(
    @ResourceUri("raml/resource-gen/req-jaxrs-context.raml") testUri: URI,
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
      import javax.ws.rs.POST
      import javax.ws.rs.Path
      import javax.ws.rs.Produces
      import javax.ws.rs.container.ResourceContext
      import javax.ws.rs.core.Application
      import javax.ws.rs.core.Configuration
      import javax.ws.rs.core.Context
      import javax.ws.rs.core.HttpHeaders
      import javax.ws.rs.core.Request
      import javax.ws.rs.core.Response
      import javax.ws.rs.core.SecurityContext
      import javax.ws.rs.core.UriInfo
      import javax.ws.rs.ext.Providers
      import javax.ws.rs.sse.Sse
      import javax.ws.rs.sse.SseEventSink

      @Produces(value = ["application/json"])
      @Consumes(value = ["application/json"])
      public interface API {
        @GET
        @Path(value = "/tests/1")
        public fun withUriInfo(@Context uriInfo: UriInfo): Response

        @GET
        @Path(value = "/tests/2")
        public fun withRequest(@Context request: Request): Response

        @GET
        @Path(value = "/tests/3")
        public fun withHeaders(@Context headers: HttpHeaders): Response

        @GET
        @Path(value = "/tests/4")
        public fun withSecurityContext(@Context securityContext: SecurityContext): Response

        @GET
        @Path(value = "/tests/5")
        public fun withApplication(@Context application: Application): Response

        @GET
        @Path(value = "/tests/6")
        public fun withConfig(@Context configuration: Configuration): Response

        @GET
        @Path(value = "/tests/7")
        public fun withResourceContext(@Context resourceContext: ResourceContext): Response

        @GET
        @Path(value = "/tests/8")
        public fun withProviders(@Context providers: Providers): Response

        @GET
        @Path(value = "/tests/9")
        public fun withSseEventSink(@Context sseEventSink: SseEventSink): Response

        @GET
        @Path(value = "/tests/10")
        public fun withSse(@Context sse: Sse): Response

        @GET
        @Path(value = "/tests/11")
        public fun withUriInfoAndRequest(@Context uriInfo: UriInfo, @Context request: Request): Response

        @POST
        @Path(value = "/tests/12")
        public fun withRepeatOfUriInfoAddedBy201(@Context uriInfo: UriInfo, @Context request: Request):
            Response
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
