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

package io.outfoxx.sunday.generator.kotlin.sunday

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.assertKotlinSundaySnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/Sunday] [RAML] Request Query Params Test")
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-basic-query-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-optional-query-parameter-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple query parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    val typeSpec = findType("io.test.service.API", builtTypes)

    assertKotlinSundaySnapshot(
      "RequestQueryParamsTest/test-generation-of-multiple-query-parameters-with-inline-type-definitions.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
