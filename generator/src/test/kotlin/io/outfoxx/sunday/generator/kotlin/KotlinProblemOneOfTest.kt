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
import io.outfoxx.sunday.generator.tools.problemOneOfCases
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.quarkus.jackson.ObjectMapperCustomizer
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.zalando.problem.jackson.ProblemModule
import java.net.URI

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("responses")
@Tag("validation")
class KotlinProblemOneOfTest {
  companion object {
    @JvmStatic
    fun targets(): List<Arguments> =
      buildList {
        for (target in listOf("sunday", "client", "server", "quarkus-client", "quarkus")) {
          for (library in KotlinProblemLibrary.entries) {
            if (library == KotlinProblemLibrary.QUARKUS && !target.startsWith("quarkus")) continue
            for (namespace in if (target.startsWith("quarkus")) listOf("jakarta") else listOf("javax", "jakarta")) {
              for (validation in listOf(false, true)) add(Arguments.of(target, library, namespace, validation))
            }
          }
        }
      }
  }

  @ParameterizedTest
  @MethodSource("targets")
  fun `problem unions select constants before decoding and reject ambiguous matches`(
    target: String,
    library: KotlinProblemLibrary,
    namespace: String,
    validation: Boolean,
    @ResourceUri("openapi/ir/problem-oneof.yaml") source: URI,
  ) {
    val registry =
      KotlinTypeRegistry(
        null,
        if (target in setOf("server", "quarkus")) GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
          if (validation) add(KotlinTypeRegistry.Option.ValidationConstraints)
        },
        problemLibrary = library,
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
          quarkus = target.startsWith("quarkus"),
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper = jacksonObjectMapper()
      when (library) {
        KotlinProblemLibrary.SUNDAY -> Unit
        KotlinProblemLibrary.ZALANDO -> {
          mapper.registerModule(ProblemModule())
          for (name in listOf(
            "ItemMissingProblem",
            "CollectionMissingProblem",
            "DuplicateCollectionMissingProblem",
            "ShortDetailProblem",
            "LongDetailProblem",
          )) {
            mapper.registerSubtypes(
              com.fasterxml.jackson.databind.jsontype.NamedType(
                result.classLoader.loadClass("io.test.$name"),
                "https://example.org/problems/${if (name == "ItemMissingProblem") "item" else "collection"}-missing",
              ),
            )
          }
        }
        KotlinProblemLibrary.QUARKUS -> {
          val registrar =
            Class
              .forName("io.quarkiverse.resteasy.problem.jackson.JacksonProblemModuleRegistrar")
              .getDeclaredConstructor()
              .apply { isAccessible = true }
              .newInstance() as ObjectMapperCustomizer
          registrar.customize(mapper)
        }
      }
      val cases = mapper.readTree(problemOneOfCases).toList()
      val inlineCases =
        cases.filter { it["union"].asText() == "Problems" }.map {
          it.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("union", "APIGetItemResponseBody")
        }
      for (case in cases + inlineCases) {
        val union = result.classLoader.loadClass("io.test.${case["union"].asText()}")
        val raw = case["wire"]
        if (case["expected"].isNull) {
          val failure =
            assertThrows(JsonMappingException::class.java, { mapper.readValue(raw.toString(), union) }, case.toString())
          if (case["error"].asText() == "ambiguous") {
            assertTrue(failure.message.orEmpty().contains("multiple branches matched"))
          }
          if (case["error"].asText() == "constraints") assertEquals(2, failure.suppressed.size)
        } else {
          val decoded = mapper.readValue(raw.toString(), union)
          assertEquals(case["expected"].asText(), decoded.javaClass.simpleName, case.toString())
          val encoded = mapper.readTree(mapper.writeValueAsString(decoded))
          // JSON numbers compare by value, including the deliberately floating-point spelling of status.
          assertEquals(
            raw["status"].decimalValue().stripTrailingZeros(),
            encoded["status"].decimalValue().stripTrailingZeros(),
          )
          assertEquals(mapper.readTree(raw.toString().replace("404.0", "404")), encoded)
        }
      }
    }
  }
}
