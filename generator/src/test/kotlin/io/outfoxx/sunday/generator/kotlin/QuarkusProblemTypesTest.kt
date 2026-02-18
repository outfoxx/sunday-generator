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
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generateTypes
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin] [RAML] Quarkus Problem Types Test")
class QuarkusProblemTypesTest {

  @ParameterizedTest
  @EnumSource(value = KotlinProblemRfc::class, names = ["RFC7807", "RFC9457"])
  fun `generates quarkus problem types`(
    rfc: KotlinProblemRfc,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Server,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = rfc,
        validateProblemRfc = true,
      )

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("io.test.InvalidIdProblem", builtTypes)
    val generated =
      buildString {
        FileSpec
          .get("io.test.service", invalidIdType)
          .writeTo(this)
      }

    assertTrue(generated.contains("import io.quarkiverse.resteasy.problem.HttpProblem"), generated)
    assertTrue(generated.contains("class InvalidIdProblem"), generated)
    assertTrue(generated.contains("HttpProblem.builder()"), generated)
    assertTrue(generated.contains("builder.withType(TYPE_URI)"), generated)
    assertTrue(generated.contains("builder.withTitle(\"Invalid Id\")"), generated)
    assertTrue(generated.contains("builder.withStatus(400)"), generated)
    assertTrue(generated.contains("builder.withDetail(\"The id contains one or more invalid characters.\")"), generated)
    assertTrue(generated.contains("initCause(cause)"), generated)
  }
}
