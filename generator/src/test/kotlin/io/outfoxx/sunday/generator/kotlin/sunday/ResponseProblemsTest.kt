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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinSundayGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.assertKotlinSundaySnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/Sunday] [RAML] Response Problems Test")
class ResponseProblemsTest {

  private fun typeRegistry(options: Set<KotlinTypeRegistry.Option> = setOf()): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Client,
      options,
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @Test
  fun `test API problem registration`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(setOf(JacksonAnnotations))

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
      "ResponseProblemsTest/test-api-problem-registration.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test API problem registration when no problems`(
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(setOf(JacksonAnnotations))

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
      "ResponseProblemsTest/test-api-problem-registration-when-no-problems.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-problem-type-generation.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation with sunday library`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)
    val generated =
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      }

    assertTrue(generated.contains("import io.outfoxx.sunday.problems.SundayHttpProblem"), generated)
    assertTrue(generated.contains("class InvalidIdProblem"), generated)
    assertTrue(generated.contains("SundayHttpProblem(TYPE_URI, \"Invalid Id\", 400,"), generated)
    assertFalse(generated.contains("org.zalando.problem.AbstractThrowableProblem"), generated)
  }

  @Test
  fun `test problem type generation using base uri`(
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-base-uri.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-absolute-problem-base-uri.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-relative-problem-base-uri.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry()

    val builtTypes =
      generate(testUri, typeRegistry) { document, shapeIndex ->
        KotlinSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          kotlinSundayTestOptions,
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertKotlinSundaySnapshot(
      "ResponseProblemsTest/test-problem-type-generation-locates-problems-in-libraries.output.kt",
      buildString {
        FileSpec
          .get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
