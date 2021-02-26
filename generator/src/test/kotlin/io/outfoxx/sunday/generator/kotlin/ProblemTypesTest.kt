package io.outfoxx.sunday.generator.kotlin

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Kotlin] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    assertEquals(
      """
        public class InvalidIdProblem(
          @com.fasterxml.jackson.`annotation`.JsonProperty(value = "offending_id")
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
      invalidIdType.toString(),
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        public class AccountNotFoundProblem(
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Account Not Found", org.zalando.problem.Status.NOT_FOUND, "The requested account does not exist or you do not have permission to access it.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/account_not_found"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      accountNotFound.toString(),
    )
  }

  @Test
  fun `generates problem types with jackson annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", GenerationMode.Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    assertEquals(
      """
        @com.fasterxml.jackson.`annotation`.JsonTypeName(io.test.InvalidIdProblem.TYPE)
        public class InvalidIdProblem @com.fasterxml.jackson.`annotation`.JsonCreator constructor(
          @com.fasterxml.jackson.`annotation`.JsonProperty(value = "offending_id")
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
      invalidIdType.toString(),
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        @com.fasterxml.jackson.`annotation`.JsonTypeName(io.test.AccountNotFoundProblem.TYPE)
        public class AccountNotFoundProblem @com.fasterxml.jackson.`annotation`.JsonCreator constructor(
          instance: java.net.URI? = null,
          cause: org.zalando.problem.ThrowableProblem? = null
        ) : org.zalando.problem.AbstractThrowableProblem(TYPE_URI, "Account Not Found", org.zalando.problem.Status.NOT_FOUND, "The requested account does not exist or you do not have permission to access it.", instance, cause) {
          public override fun getCause(): org.zalando.problem.Exceptional? = super.cause
        
          public companion object {
            public const val TYPE: kotlin.String = "http://example.com/account_not_found"

            public val TYPE_URI: java.net.URI = java.net.URI(TYPE)
          }
        }

      """.trimIndent(),
      accountNotFound.toString(),
    )
  }

}
