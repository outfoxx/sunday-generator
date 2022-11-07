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
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE3
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE3
import io.outfoxx.sunday.generator.kotlin.utils.UNI
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.util.concurrent.CompletionStage

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/JAXRS] [RAML] Request Reactive Methods Test")
class RequestReactiveMethodsTest {

  @Test
  fun `test basic reactive method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import java.util.concurrent.CompletionStage
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.core.Response

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): CompletionStage<Response>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.test.Test
        import java.util.concurrent.CompletionStage
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces

        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          @GET
          @Path(value = "/tests")
          public fun fetchTest(): CompletionStage<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (CompletionStage)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import java.util.concurrent.CompletionStage
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): CompletionStage<Test?> = fetchTest(limit)
            .exceptionally { x ->
              when {
                x is TestNotFoundProblem -> null
                x is AnotherNotFoundProblem -> null
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    null
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): CompletionStage<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Uni)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            UNI.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.smallrye.mutiny.Uni
        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): Uni<Test?> = fetchTest(limit)
            .onFailure().recoverWithItem { x ->
              when {
                x is TestNotFoundProblem -> null
                x is AnotherNotFoundProblem -> null
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    null
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): Uni<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Single)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            RXSINGLE3.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.reactivex.rxjava3.core.Single
        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import java.util.Optional
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): Single<Optional<Test>> = fetchTest(limit)
            .map { Optional.of(it) }
            .onErrorReturn { x ->
              when {
                x is TestNotFoundProblem -> Optional.empty()
                x is AnotherNotFoundProblem -> Optional.empty()
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    Optional.empty()
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): Single<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Observable)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            RXOBSERVABLE3.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.reactivex.rxjava3.core.Observable
        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import java.util.Optional
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): Observable<Optional<Test>> = fetchTest(limit)
            .map { Optional.of(it) }
            .onErrorReturn { x ->
              when {
                x is TestNotFoundProblem -> Optional.empty()
                x is AnotherNotFoundProblem -> Optional.empty()
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    Optional.empty()
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): Observable<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Single v2)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            RXSINGLE2.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.reactivex.Single
        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import java.util.Optional
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): Single<Optional<Test>> = fetchTest(limit)
            .map { Optional.of(it) }
            .onErrorReturn { x ->
              when {
                x is TestNotFoundProblem -> Optional.empty()
                x is AnotherNotFoundProblem -> Optional.empty()
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    Optional.empty()
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): Single<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Observable v2)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            false,
            RXOBSERVABLE2.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertEquals(
      """
        package io.test.service

        import io.reactivex.Observable
        import io.test.AnotherNotFoundProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import java.util.Optional
        import javax.ws.rs.Consumes
        import javax.ws.rs.GET
        import javax.ws.rs.Path
        import javax.ws.rs.Produces
        import javax.ws.rs.QueryParam
        import kotlin.Int
        import org.zalando.problem.ThrowableProblem
        
        @Produces(value = ["application/json"])
        @Consumes(value = ["application/json"])
        public interface API {
          public fun fetchTestOrNull(limit: Int): Observable<Optional<Test>> = fetchTest(limit)
            .map { Optional.of(it) }
            .onErrorReturn { x ->
              when {
                x is TestNotFoundProblem -> Optional.empty()
                x is AnotherNotFoundProblem -> Optional.empty()
                x is ThrowableProblem && (x.status?.statusCode == 404 || x.status?.statusCode == 405) ->
                    Optional.empty()
                else -> throw x
              }
            }

          @GET
          @Path(value = "/tests")
          public fun fetchTest(@QueryParam(value = "limit") limit: Int): Observable<Test>
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
