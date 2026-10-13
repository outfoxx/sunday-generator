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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.problemExtensionsApi
import io.outfoxx.sunday.problems.SundayHttpProblem
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinProblemExtensionsTest {
  @ParameterizedTest
  @MethodSource("io.outfoxx.sunday.generator.kotlin.DirectionalToleranceArguments#commonTargets")
  fun `source problems share runtime extension fields`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target == "server") GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = problemExtensionsApi(frontend, directory)
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions("io.test.service", "http://example.com/", listOf("application/json"), "API"),
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
          defaultServicePackageName = "io.test.service",
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper =
        jacksonObjectMapper()
          .registerModule(JavaTimeModule())
          .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      val raw =
        """{"type":"about:blank","title":"Failure","status":400,"scalar":"extra","object":{"nested":true},"future":null}"""
      for (name in listOf("Problem", "InvalidLimitProblem", "OtherProblem", "ProblemResponse")) {
        val type = result.classLoader.loadClass("io.test.$name")
        val value = mapper.readValue(raw, type)
        assertEquals(mapper.readTree(raw), mapper.readTree(mapper.writeValueAsString(value)), name)
        assertTrue(value is SundayHttpProblem, name)
        if (name == "ProblemResponse") assertEquals("InvalidLimitProblem", value.javaClass.simpleName)
        if (value is SundayHttpProblem) {
          assertEquals(
            mapOf("scalar" to "extra", "object" to mapOf("nested" to true), "future" to null),
            value.extensions,
          )
          value.extension("added", null)
          value.extensions["scalar"] = "updated"
          val fields = value.javaClass.getMethod("getAdditionalProperties").invoke(value)
          assertEquals(value.extensions, fields)
          val encoded = mapper.readTree(mapper.writeValueAsString(value))
          assertTrue(encoded.has("added"))
          assertTrue(encoded["added"].isNull)
          assertEquals("updated", encoded["scalar"].asText())
          assertThrows(IllegalArgumentException::class.java) { value.extension("title", "spoofed") }
          assertThrows(IllegalArgumentException::class.java) { value.extensions["status"] = 200 }
        }
        for (invalid in listOf(
          """{"type":"about:blank","status":400}""",
          """{"type":"about:blank","title":"x","status":400}""",
          """{"type":"about:blank","title":"Failure","status":999}""",
        )) {
          assertThrows(JsonMappingException::class.java, { mapper.readValue(invalid, type) }, "$name: $invalid")
        }
      }
      val base = """{"type":"about:blank","title":"Failure","status":400}"""
      val closed = result.classLoader.loadClass("io.test.ClosedProblem")
      val closedValue = mapper.readValue(base, closed) as SundayHttpProblem
      assertEquals(mapper.readTree(base), mapper.readTree(mapper.writeValueAsString(closedValue)))
      assertThrows(JsonMappingException::class.java) { mapper.readValue(raw, closed) }
      assertThrows(RuntimeException::class.java) { closedValue.extension("extra", true) }
      val declared = result.classLoader.loadClass("io.test.DeclaredProblem")
      val declaredValue = mapper.readValue(base.dropLast(1) + """, "code":"limit"}""", declared) as SundayHttpProblem
      assertThrows(IllegalArgumentException::class.java) { declaredValue.extension("code", "spoofed") }
      val typed = result.classLoader.loadClass("io.test.TypedProblem")
      val typedRaw = base.dropLast(1) + """, "count":2}"""
      val typedValue = mapper.readValue(typedRaw, typed) as SundayHttpProblem
      assertEquals(mapOf("count" to 2), typedValue.extensions)
      assertEquals(mapper.readTree(typedRaw), mapper.readTree(mapper.writeValueAsString(typedValue)))
      assertThrows(
        JsonMappingException::class.java,
      ) { mapper.readValue(base.dropLast(1) + """, "count":0}""", typed) }
      assertThrows(
        JsonMappingException::class.java,
      ) { mapper.readValue(base.dropLast(1) + """, "count":{}}""", typed) }
      val patterned = result.classLoader.loadClass("io.test.PatternProblem")
      val patternRaw = base.dropLast(1) + """, "x-count":2}"""
      val patternValue = mapper.readValue(patternRaw, patterned) as SundayHttpProblem
      assertEquals(mapper.readTree(patternRaw), mapper.readTree(mapper.writeValueAsString(patternValue)))
      assertEquals(mapOf("x-count" to 2), patternValue.extensions)
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue(base.dropLast(1) + """, "extra":2}""", patterned)
      }
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue(base.dropLast(1) + """, "x-count":0}""", patterned)
      }
    }
  }
}
