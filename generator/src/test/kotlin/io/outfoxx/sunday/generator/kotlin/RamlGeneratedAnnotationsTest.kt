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
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Generated Annotations Test")
class RamlGeneratedAnnotationsTest {
  companion object {
    private const val FIXED_GENERATION_TIMESTAMP = "2024-01-01T00:00:00"
  }

  @Test
  fun `test generated annotation is added to root classes`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(AddGeneratedAnnotation),
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertKotlinSnapshot(
      "RamlGeneratedAnnotationsTest/test-generated-annotation-is-added-to-root-classes.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", type)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test special generated annotation is added to root classes`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        "io.outfoxx.sunday.annotation.Generated",
        GenerationMode.Server,
        setOf(AddGeneratedAnnotation),
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertKotlinSnapshot(
      "RamlGeneratedAnnotationsTest/test-special-generated-annotation-is-added-to-root-classes.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", type)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test warning annotations are added to hide public api`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(SuppressPublicApiWarnings),
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val type = findType("io.test.Test", generateTypes(testUri, typeRegistry))
    assertKotlinSnapshot(
      "RamlGeneratedAnnotationsTest/test-warning-annotations-are-added-to-hide-public-api.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", type)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(AddGeneratedAnnotation),
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val type = findType("io.test.service.API", builtTypes)
    assertKotlinSnapshot(
      "RamlGeneratedAnnotationsTest/test-generated-annotation-is-added-to-service-class.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", type)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test warning annotation is added to service class`(
    @ResourceUri("raml/type-gen/general/generated-annotations.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(SuppressPublicApiWarnings),
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinJAXRSGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinJAXRSTestOptions,
        )
      }

    val type = findType("io.test.service.API", builtTypes)
    assertKotlinSnapshot(
      "RamlGeneratedAnnotationsTest/test-warning-annotation-is-added-to-service-class.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", type)
          .writeTo(this)
      },
    )
  }
}
