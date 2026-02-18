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

import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.findType
import io.outfoxx.sunday.generator.kotlin.tools.generate
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.net.URI

@KotlinTest
@DisplayName("[Kotlin/JAXRS] [RAML] Quarkus Request Methods Test")
class QuarkusRequestMethodsTest {

  @ParameterizedTest
  @EnumSource(value = KotlinProblemRfc::class, names = ["RFC7807", "RFC9457"])
  fun `nullify catches httpproblem with status code`(
    rfc: KotlinProblemRfc,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry =
      KotlinTypeRegistry(
        "io.test",
        null,
        GenerationMode.Client,
        setOf(),
        problemLibrary = KotlinProblemLibrary.QUARKUS,
        problemRfc = rfc,
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

    val typeSpec = findType("io.test.service.API", builtTypes)
    val generated =
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      }

    assertTrue(generated.contains("import io.quarkiverse.resteasy.problem.HttpProblem"))
    assertTrue(generated.contains("catch(x: HttpProblem)"))
    assertTrue(generated.contains("x.statusCode"))
  }
}
