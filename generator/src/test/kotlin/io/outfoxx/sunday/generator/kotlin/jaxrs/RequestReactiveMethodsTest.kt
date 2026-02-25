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
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE3
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE3
import io.outfoxx.sunday.generator.kotlin.utils.UNI
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.util.concurrent.CompletionStage

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Reactive Methods Test")
class RequestReactiveMethodsTest {

  private fun typeRegistry(mode: GenerationMode): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      mode,
      setOf(),
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @Test
  fun `test basic reactive method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation in server mode (Quarkus)`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-server-mode-quarkus.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation in client mode (Quarkus)`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-client-mode-quarkus.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation in client mode (Quarkus + Response)`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            true,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-in-client-mode-quarkus-response.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (CompletionStage)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-completionstage.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Quarkus)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-quarkus.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Quarkus + Response)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            CompletionStage::class.qualifiedName,
            false,
            null,
            true,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            true,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-quarkus-response.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Uni)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            UNI.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-uni.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Single)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            RXSINGLE3.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-single.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Observable)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            RXOBSERVABLE3.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-observable.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Single v2)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            RXSINGLE2.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-single-v2.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic reactive method generation with nullify in client mode (Observable v2)`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          KotlinJAXRSGenerator.Options(
            coroutineFlowMethods = false,
            coroutineServiceMethods = false,
            RXOBSERVABLE2.canonicalName,
            false,
            null,
            false,
            "io.test.service",
            "http://example.com/",
            listOf("application/json"),
            "API",
            false,
          ),
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestReactiveMethodsTest/test-basic-reactive-method-generation-with-nullify-in-client-mode-observable-v2.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
