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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Base URI Test")
class BaseUriTest {

  @Test
  fun `test baseUrl generation in API (client mode)`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Client, setOf())

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
      "BaseUriTest/test-baseurl-generation-in-api-client-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )

    val envTypeSpec = builtTypes[ClassName.bestGuess("io.test.Environment")]
    assertNotNull(envTypeSpec)
  }

  @Test
  fun `test baseUrl generation in API (server mode)`(
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, Server, setOf())

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
      "BaseUriTest/test-baseurl-generation-in-api-server-mode.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )

    val envTypeSpec = builtTypes[ClassName.bestGuess("io.test.Environment")]
    assertNotNull(envTypeSpec)
  }
}
