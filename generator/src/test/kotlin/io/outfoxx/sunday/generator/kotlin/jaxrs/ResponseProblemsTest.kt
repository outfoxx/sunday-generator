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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.generateJaxrs
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Response Problems Test")
class ResponseProblemsTest {

  private fun typeRegistry(
    mode: GenerationMode,
    options: Set<KotlinTypeRegistry.Option> = setOf(),
  ): KotlinTypeRegistry =
    KotlinTypeRegistry(
      "io.test",
      null,
      mode,
      options,
      problemLibrary = KotlinProblemLibrary.ZALANDO,
      problemRfc = KotlinProblemRfc.RFC7807,
    )

  @Test
  fun `test API problem registration in server mode when no problems referenced`(
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server, setOf(JacksonAnnotations))

    generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-api-problem-registration-in-server-mode-when-no-problems-referenced.output.kt",
      compiledServiceSource(),
    )
  }

  @Test
  fun `test API problem registration in client mode when no problems referenced`(
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client, setOf(JacksonAnnotations))

    generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-api-problem-registration-in-client-mode-when-no-problems-referenced.output.kt",
      compiledServiceSource(),
    )
  }

  @Test
  fun `test API problem registration in server mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server, setOf(JacksonAnnotations))

    generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-api-problem-registration-in-server-mode.output.kt",
      compiledServiceSource(),
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
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
        problemRfc = KotlinProblemRfc.RFC7807,
      )

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    val generated = compiledProblemSource()

    assertTrue(generated.contains("import io.outfoxx.sunday.problems.SundayHttpProblem"), generated)
    assertTrue(generated.contains("class InvalidIdProblem"), generated)
    assertTrue(generated.contains("SundayHttpProblem(TYPE_URI, \"Invalid Id\", 400,"), generated)
    assertFalse(generated.contains("org.zalando.problem.AbstractThrowableProblem"), generated)
  }

  @Test
  fun `test API problem registration in client mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client, setOf(JacksonAnnotations))

    generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-api-problem-registration-in-client-mode.output.kt",
      compiledServiceSource(),
    )
  }

  @Test
  fun `test problem type generation in server mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-in-server-mode.output.kt",
      compiledProblemSource(),
    )
  }

  @Test
  fun `test problem type generation in client mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Client)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-in-client-mode.output.kt",
      compiledProblemSource(),
    )
  }

  @Test
  fun `test problem type generation using base uri`(
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-base-uri.output.kt",
      compiledProblemSource(),
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-absolute-problem-base-uri.output.kt",
      compiledProblemSource(),
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-relative-problem-base-uri.output.kt",
      compiledProblemSource(),
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI,
  ) {

    val typeRegistry = typeRegistry(GenerationMode.Server)

    val builtTypes =
      generateJaxrs(testUri, typeRegistry, kotlinJAXRSTestOptions)

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    assertKotlinJaxrsSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-locates-problems-in-libraries.output.kt",
      compiledProblemSource(),
    )
  }

  private fun compiledServiceSource(): String =
    CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/service/API.kt")

  private fun compiledProblemSource(): String =
    CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/InvalidIdProblem.kt")
}
