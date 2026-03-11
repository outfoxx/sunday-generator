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
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Request Coroutine Methods Test")
class RequestCoroutineMethodsTest {

  private fun typeRegistry(mode: GenerationMode): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      mode,
      setOf(),
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  val coroutineOptions =
    KotlinJAXRSGenerator.Options(
      coroutineFlowMethods = false,
      coroutineServiceMethods = true,
      null,
      false,
      null,
      false,
      "io.test.service",
      "http://example.com/",
      listOf("application/json"),
      "API",
      quarkus = false,
    )

  val coroutineWithFlowOptions =
    KotlinJAXRSGenerator.Options(
      coroutineFlowMethods = true,
      coroutineServiceMethods = true,
      null,
      false,
      null,
      false,
      "io.test.service",
      "http://example.com/",
      listOf("application/json"),
      "API",
      quarkus = false,
    )

  val coroutineWithQuarkusOptions =
    KotlinJAXRSGenerator.Options(
      coroutineFlowMethods = true,
      coroutineServiceMethods = true,
      null,
      false,
      null,
      false,
      "io.test.service",
      "http://example.com/",
      listOf("application/json"),
      "API",
      quarkus = true,
    )

  @Test
  fun `test basic coroutines method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with nullify`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-client-mode-with-nullify.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with multiple default types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithFlowOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-server-mode-with-multiple-default-types.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithFlowOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with multiple default types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithFlowOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-client-mode-with-multiple-default-types.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in server mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithFlowOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-with-common-type-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in client mode`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithFlowOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-with-common-type-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-server-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-client-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic coroutines method generation in client mode with nullify and quarkus option enabled`(
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-basic-coroutines-method-generation-in-client-mode-with-nullify-and-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-server-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in server mode with quarkus option enabled and multiple defaults types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-server-mode-with-quarkus-option-enabled-and-multiple-defaults-types.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-client-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation in client mode with quarkus option enabled and multiple defaults types`(
    @ResourceUri("raml/resource-gen/res-event-stream-jaxrs-multi-default.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-in-client-mode-with-quarkus-option-enabled-and-multiple-defaults-types.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in server mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-with-common-type-in-server-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event coroutines method generation with common type in client mode with quarkus option enabled`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineWithQuarkusOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-event-coroutines-method-generation-with-common-type-in-client-mode-with-quarkus-option-enabled.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of coroutine with no response in client mode`(
    @ResourceUri("raml/resource-gen/res-none.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-generation-of-coroutine-with-no-response-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of coroutine with no response in server mode`(
    @ResourceUri("raml/resource-gen/res-none.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          coroutineOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinJaxrsSnapshot(
      "RequestCoroutineMethodsTest/test-generation-of-coroutine-with-no-response-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
