/*
 * Copyright 2026 Outfox, Inc.
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

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
@Tag("validation")
class KotlinProblemOneOfTest {
  @ParameterizedTest
  @CsvSource("sunday,false", "sunday,true", "client,false", "client,true", "server,false", "server,true")
  fun `problem unions select constants before decoding and reject ambiguous matches`(
    target: String,
    validation: Boolean,
    @ResourceUri("openapi/ir/problem-oneof.yaml") source: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        if (target == "server") GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (validation) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = GeneratedApiIrExporter().export(source)
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test", "io.test"),
          "https://example.org/",
          listOf("application/json"),
          "API",
        ),
      ).generateServiceTypes()
    } else {
      KotlinJAXRSIrGenerator(
        api,
        registry,
        KotlinJAXRSOptions(
          coroutineFlowMethods = false,
          coroutineServiceMethods = false,
          reactiveResponseType = null,
          explicitSecurityParameters = false,
          baseUriMode = null,
          alwaysUseResponseReturn = false,
          placement = kotlinPlacement("io.test", "io.test"),
          defaultProblemBaseUri = "https://example.org/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation("jakarta", result.classLoader) {
      val mapper = jacksonObjectMapper()
      for (name in listOf("APIGetItemResponseBody", "ReversedProblems", "TaggedProblems")) {
        val union = result.classLoader.loadClass("io.test.$name")
        for ((kind, title) in listOf("item" to "Item", "collection" to "Collection")) {
          val raw =
            mapper
              .createObjectNode()
              .put("type", "https://example.org/problems/$kind-missing")
              .put("title", "$title missing")
              .put("status", 404)
              .put("detail", "No matching resource")
          val decoded = mapper.readValue(raw.toString(), union)
          assertEquals("${title}MissingProblem", decoded.javaClass.simpleName)
          assertEquals(
            decoded.javaClass,
            mapper.readValue(raw.deepCopy().put("status", 404.0).toString(), union).javaClass,
          )
          assertEquals(raw, mapper.readTree(mapper.writeValueAsString(decoded)))
          for ((field, value) in listOf(
            "type" to "https://example.org/problems/unknown",
            "title" to "Wrong",
            "detail" to "x",
          )) {
            val invalid = raw.deepCopy().put(field, value)
            assertThrows(
              JsonMappingException::class.java,
              { mapper.readValue(invalid.toString(), union) },
              "$name: $invalid",
            )
          }
          for (invalid in listOf(
            raw.deepCopy().put("status", 400),
            raw.deepCopy().put("status", "404"),
            raw.deepCopy().putNull("type"),
            raw.deepCopy().apply { remove("detail") },
          )) {
            assertThrows(
              JsonMappingException::class.java,
              { mapper.readValue(invalid.toString(), union) },
              "$name: $invalid",
            )
          }
          if (kind == "collection") {
            val ambiguous = result.classLoader.loadClass("io.test.AmbiguousProblems")
            val ambiguity =
              assertThrows(JsonMappingException::class.java) { mapper.readValue(raw.toString(), ambiguous) }
            assertTrue(ambiguity.message.orEmpty().contains("multiple branches matched"))
            val constrained = result.classLoader.loadClass("io.test.ConstrainedProblems")
            for ((detail, expectedType) in listOf(
              "short" to "ShortDetailProblem",
              "Long detail" to "LongDetailProblem",
            )) {
              val value = mapper.readValue(raw.deepCopy().put("detail", detail).toString(), constrained)
              assertEquals(expectedType, value.javaClass.simpleName)
            }
            val failure =
              assertThrows(JsonMappingException::class.java) {
                mapper.readValue(raw.deepCopy().put("detail", "x").toString(), constrained)
              }
            assertEquals(2, failure.suppressed.size)
          }
        }
        for (invalid in listOf("[]", "{}")) {
          assertThrows(JsonMappingException::class.java) { mapper.readValue(invalid, union) }
        }
      }
    }
  }
}
