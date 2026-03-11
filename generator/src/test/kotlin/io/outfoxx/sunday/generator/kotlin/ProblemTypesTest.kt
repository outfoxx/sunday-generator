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
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.assertKotlinSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Problem Types Test")
class ProblemTypesTest {
  companion object {
    private const val FIXED_GENERATION_TIMESTAMP = "2024-01-01T00:00:00"
  }

  private fun typeRegistry(options: Set<KotlinTypeRegistry.Option> = setOf()): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Server,
      options,
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @Test
  fun `generates problem types`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types.output2.kt",
      buildString {
        FileSpec
          .get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )

    val tenantResolver = findType("io.test.TestResolverProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types.output3.kt",
      buildString {
        FileSpec
          .get("io.test.service", tenantResolver)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates problem types with jackson annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types-with-jackson-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types-with-jackson-annotations.output2.kt",
      buildString {
        FileSpec
          .get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates problem types with generated annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(AddGeneratedAnnotation),
        problemLibrary = KotlinProblemLibrary.ZALANDO,
        problemRfc = KotlinProblemRfc.RFC7807,
        generationTimestamp = FIXED_GENERATION_TIMESTAMP,
      )

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types-with-generated-annotations.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types-with-generated-annotations.output2.kt",
      buildString {
        FileSpec
          .get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )

    val tenantResolver = findType("io.test.TestResolverProblem", builtTypes)
    assertKotlinSnapshot(
      "ProblemTypesTest/generates-problem-types-with-generated-annotations.output3.kt",
      buildString {
        FileSpec
          .get("io.test.service", tenantResolver)
          .writeTo(this)
      },
    )
  }
}
