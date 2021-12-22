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

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.SuppressPublicApiWarnings
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Generated Annotations Test")
class RamlGeneratedAnnotationsTest {

  @Test
  fun `test generated annotation is added to root classes`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertEquals(
      """
        package io.test.service
        
        import javax.`annotation`.processing.Generated
        import kotlin.String
        
        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}"
        )
        public interface Test {
          public val `value`: String
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", type)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test special generated annotation is added to root classes`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", "javax.annotation.Generated", GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertEquals(
      """
        package io.test.service
        
        import javax.`annotation`.Generated
        import kotlin.String
        
        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}"
        )
        public interface Test {
          public val `value`: String
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", type)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test warning annotations are added to hide public api`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(SuppressPublicApiWarnings))

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertEquals(
      """
        package io.test.service
        
        import kotlin.String
        import kotlin.Suppress
        
        @Suppress("RedundantVisibilityModifier", "RedundantUnitReturnType")
        public interface Test {
          public val `value`: String
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", type)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          kotlinJAXRSTestOptions
        )
      }

    val type = findType("io.test.service.API", builtTypes)
    assertEquals(
      """
        package io.test.service

        import javax.`annotation`.processing.Generated
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.String
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}"
        )
        public interface API {
          @GET
          @Path(value = "/tests/{id}")
          public fun fetchTest(@PathParam(value = "id") id: String): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", type)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test warning annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(SuppressPublicApiWarnings))

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val type = findType("io.test.service.API", builtTypes)
    assertEquals(
      """
        package io.test.service

        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.PathParam
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response
        import kotlin.String
        import kotlin.Suppress
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        @Suppress("RedundantVisibilityModifier", "RedundantUnitReturnType")
        public interface API {
          @GET
          @Path(value = "/tests/{id}")
          public fun fetchTest(@PathParam(value = "id") id: String): Response
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", type)
          .writeTo(this)
      }
    )
  }
}
