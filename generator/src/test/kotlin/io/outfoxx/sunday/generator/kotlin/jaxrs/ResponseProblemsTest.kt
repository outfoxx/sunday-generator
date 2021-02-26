package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin/JAXRS] [RAML] Response Problems Test")
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://api.example.com/api/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://errors.example.com/docs/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://example.com/api/errors/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
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

    val typeSpec = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

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
          cause: ThrowableProblem? = null
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", typeSpec)
          .writeTo(this)
      }
    )
  }

}
