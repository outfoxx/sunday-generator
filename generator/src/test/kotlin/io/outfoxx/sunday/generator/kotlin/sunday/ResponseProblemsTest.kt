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
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
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

    assertEquals(
      """
        package io.test.service

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import io.test.InvalidIdProblem
        import io.test.Test
        import io.test.TestNotFoundProblem
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          init {
            requestFactory.registerProblem("http://example.com/invalid_id", InvalidIdProblem::class)
            requestFactory.registerProblem("http://example.com/test_not_found", TestNotFoundProblem::class)
          }
          public suspend fun fetchTest(): Test = this.requestFactory
            .result(
              method = Method.Get,
              pathTemplate = "/tests",
              acceptTypes = this.defaultAcceptTypes
            )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import io.outfoxx.sunday.MediaType
        import io.outfoxx.sunday.RequestFactory
        import io.outfoxx.sunday.http.Method
        import io.test.Test
        import kotlin.collections.List

        public class API(
          public val requestFactory: RequestFactory,
          public val defaultContentTypes: List<MediaType> = listOf(),
          public val defaultAcceptTypes: List<MediaType> = listOf(MediaType.JSON),
        ) {
          public suspend fun fetchTest(): Test = this.requestFactory
            .result(
              method = Method.Get,
              pathTemplate = "/tests",
              acceptTypes = this.defaultAcceptTypes
            )
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          @JsonIgnore
          override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          @JsonIgnore
          override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://api.example.com/api/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          @JsonIgnore
          override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://errors.example.com/docs/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          @JsonIgnore
          override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/api/errors/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
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

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonIgnore
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          @JsonIgnore
          override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      },
    )
  }
}
