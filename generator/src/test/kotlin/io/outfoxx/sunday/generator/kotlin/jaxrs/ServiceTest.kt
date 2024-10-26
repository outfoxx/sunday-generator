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
@DisplayName("[Kotlin/JAXRS] [RAML] Service Test")
class ServiceTest {

  @Test
  fun `test default media types are limited correctly (Client)`(
    @ResourceUri("raml/service-gen/svc-default-media-types.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val options = KotlinJAXRSGenerator.Options(
      false,
      null,
      false,
      null,
      false,
      "io.test.service",
      "http://example.com/",
      listOf("application/cbor", "application/yaml"),
      "API",
      false,
    )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          options,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.test.Test
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces

        @Produces(value = ["application/cbor","application/yaml","application/json"])
        @Consumes(value = ["application/cbor"])
        public interface API {
          @GET
          @Path(value = "/tests")
          @Consumes(value = ["application/yaml"])
          public fun fetchTest(body: Test): Test
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test default media types are limited correctly (Server)`(
    @ResourceUri("raml/service-gen/svc-default-media-types.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val options = KotlinJAXRSGenerator.Options(
      false,
      null,
      false,
      null,
      false,
      "io.test.service",
      "http://example.com/",
      listOf("application/cbor", "application/yaml"),
      "API",
      false,
    )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          options,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.test.Test
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response

        @Produces(value = ["application/cbor","application/yaml","application/json"])
        @Consumes(value = ["application/cbor","application/yaml","application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          @Consumes(value = ["application/yaml"])
          public fun fetchTest(body: Test): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
