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
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Response Body Content Test")
class ResponseBodyContentTest {

  @Test
  fun `test basic body parameter generation in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test basic body parameter generation in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of body parameter with inline type in client mode`(
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of body parameter with inline type in server mode`(
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of response body that is no content client mode`(
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of response body that is no content server mode`(
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of no response in client mode`(
    @ResourceUri("raml/resource-gen/res-none.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Client, setOf())

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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-no-response-in-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of no response in server mode`(
    @ResourceUri("raml/resource-gen/res-none.raml") testUri: URI,
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

    assertKotlinJaxrsSnapshot(
      "ResponseBodyContentTest/test-generation-of-no-response-in-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
