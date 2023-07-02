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
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
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
          cause: ThrowableProblem? = null,
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
        FileSpec.get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class AccountNotFoundProblem(
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Account Not Found", Status.NOT_FOUND,
            "The requested account does not exist or you do not have permission to access it.", instance,
            cause) {
          public override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/account_not_found"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )

    val tenantResolver = findType("io.test.TestResolverProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import java.net.URI
        import kotlin.String
        import kotlin.collections.List
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        public class TestResolverProblem(
          public val optionalString: String? = null,
          public val arrayOfStrings: List<String>,
          public val optionalArrayOfStrings: List<String>? = null,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Test Resolve Type Reference", Status.INTERNAL_SERVER_ERROR,
            "Tests the resolveTypeReference function implementation.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/test_resolver"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", tenantResolver)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates problem types with jackson annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(JacksonAnnotations))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonCreator
        import com.fasterxml.jackson.`annotation`.JsonProperty
        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import io.test.InvalidIdProblem
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        @JsonTypeName(InvalidIdProblem.TYPE)
        public class InvalidIdProblem @JsonCreator constructor(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
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
        FileSpec.get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonCreator
        import com.fasterxml.jackson.`annotation`.JsonTypeName
        import io.test.AccountNotFoundProblem
        import java.net.URI
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        @JsonTypeName(AccountNotFoundProblem.TYPE)
        public class AccountNotFoundProblem @JsonCreator constructor(
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Account Not Found", Status.NOT_FOUND,
            "The requested account does not exist or you do not have permission to access it.", instance,
            cause) {
          public override fun getCause(): Exceptional? = super.cause

          public companion object {
            public const val TYPE: String = "http://example.com/account_not_found"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates problem types with generated annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = KotlinTypeRegistry("io.test", null, GenerationMode.Server, setOf(AddGeneratedAnnotation))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)

    assertEquals(
      """
        package io.test.service

        import com.fasterxml.jackson.`annotation`.JsonProperty
        import java.net.URI
        import javax.`annotation`.processing.Generated
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}",
        )
        public class InvalidIdProblem(
          @JsonProperty(value = "offending_id")
          public val offendingId: String,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Invalid Id", Status.BAD_REQUEST,
            "The id contains one or more invalid characters.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause

          @Generated
          public companion object {
            public const val TYPE: String = "http://example.com/invalid_id"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFound = findType("io.test.AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import java.net.URI
        import javax.`annotation`.processing.Generated
        import kotlin.String
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}",
        )
        public class AccountNotFoundProblem(
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Account Not Found", Status.NOT_FOUND,
            "The requested account does not exist or you do not have permission to access it.", instance,
            cause) {
          public override fun getCause(): Exceptional? = super.cause

          @Generated
          public companion object {
            public const val TYPE: String = "http://example.com/account_not_found"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", accountNotFound)
          .writeTo(this)
      },
    )

    val tenantResolver = findType("io.test.TestResolverProblem", builtTypes)
    assertEquals(
      """
        package io.test.service

        import java.net.URI
        import javax.`annotation`.processing.Generated
        import kotlin.String
        import kotlin.collections.List
        import org.zalando.problem.AbstractThrowableProblem
        import org.zalando.problem.Exceptional
        import org.zalando.problem.Status
        import org.zalando.problem.ThrowableProblem

        @Generated(
          value = ["io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry"],
          date = "${typeRegistry.generationTimestamp}",
        )
        public class TestResolverProblem(
          public val optionalString: String? = null,
          public val arrayOfStrings: List<String>,
          public val optionalArrayOfStrings: List<String>? = null,
          instance: URI? = null,
          cause: ThrowableProblem? = null,
        ) : AbstractThrowableProblem(TYPE_URI, "Test Resolve Type Reference", Status.INTERNAL_SERVER_ERROR,
            "Tests the resolveTypeReference function implementation.", instance, cause) {
          public override fun getCause(): Exceptional? = super.cause

          @Generated
          public companion object {
            public const val TYPE: String = "http://example.com/test_resolver"

            public val TYPE_URI: URI = URI(TYPE)
          }
        }

      """.trimIndent(),
      buildString {
        FileSpec.get("io.test.service", tenantResolver)
          .writeTo(this)
      },
    )
  }
}
