package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.ClassName
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.findType
import io.outfoxx.sunday.generator.kotlin.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class ResponseProblemsTest {

  @Test
  fun `test problem type generation in server mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test problem type generation in client mode`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test problem type generation using base uri`(
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://api.example.com/api/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://errors.example.com/docs/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/api/errors/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

  @Test
  fun `test problem type generation uses package name from library`(
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        KotlinJAXRSGenerator(
          document,
          typeRegistry,
          null,
          "io.test.service",
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(ClassName.bestGuess("io.test.CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(ClassName.bestGuess("io.test.TestNotFoundProblem")))

    val type = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        public class InvalidIdProblem(
          public val offendingId: kotlin.String,
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Invalid Id", org.zalando.problem.Status.BAD_REQUEST, "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/invalid_id"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      type.toString()
    )
  }

}
