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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.closedModelsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinClosedModelsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `closed objects reject unknown fields even with a lenient mapper`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = closedModelsApi(frontend, directory)
    for (jaxrs in listOf(false, true)) {
      for (preserve in listOf(false, true)) {
        val registry =
          KotlinTypeRegistry(
            "io.test",
            null,
            GenerationMode.Client,
            buildSet {
              add(KotlinTypeRegistry.Option.ImplementModel)
              add(KotlinTypeRegistry.Option.JacksonAnnotations)
              if (preserve) add(KotlinTypeRegistry.Option.PreserveUnknownFields)
            },
            problemLibrary = KotlinProblemLibrary.SUNDAY,
          )
        if (jaxrs) {
          KotlinJAXRSIrGenerator(
            api,
            registry,
            KotlinJAXRSOptions(
              false,
              false,
              null,
              false,
              null,
              false,
              "io.test.service",
              "http://example.com/",
              listOf("application/json"),
              "API",
              false,
              preserveUnknownFields = preserve,
            ),
          ).generateServiceTypes()
        } else {
          KotlinSundayIrGenerator(
            api,
            registry,
            KotlinSundayOptions(
              "io.test.service",
              "http://example.com/",
              listOf("application/json"),
              "API",
              preserveUnknownFields = preserve,
            ),
          ).generateServiceTypes()
        }
        val result = compileTypesResult(registry.buildTypes())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        for ((name, valid) in listOf(
          "ClosedRecord" to "\"display-name\":\"valid\"",
          "ClosedChild" to "\"name\":\"valid\",\"count\":1",
          "EmptyClosed" to "",
        )) {
          val model = result.classLoader.loadClass("io.test.$name")
          mapper.readValue("{$valid}", model)
          for (extra in listOf("1", "null", "{}", "[]")) {
            val fields = listOf(valid, "\"extra\":$extra").filter { it.isNotEmpty() }.joinToString(",")
            assertThrows(JsonMappingException::class.java) { mapper.readValue("{$fields}", model) }
          }
        }
        if (frontend == "composed") {
          val event = result.classLoader.loadClass("io.test.EventRecord")
          mapper.readValue("{}", event)
          assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"extra":1}""", event) }
        }
        val closed = result.classLoader.loadClass("io.test.ClosedRecord")
        mapper.readValue("""{"display-name":"valid","choice":{"kind":"cat","name":"cat"}}""", closed)
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"display-name":"valid","choice":{"kind":"cat","name":"cat","extra":1}}""", closed)
        }
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"display-name":"valid","nested":{"display-name":"nested","extra":1}}""", closed)
        }
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"display-name":"valid","empty":{"extra":1}}""", closed)
        }
        for (strict in listOf(false, true)) {
          val configured = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
          val open =
            configured.readValue(
              """{"name":"valid","extra":1}""",
              result.classLoader.loadClass("io.test.OpenRecord"),
            )
          assertEquals(preserve, configured.readTree(configured.writeValueAsBytes(open)).has("extra"))
        }
      }
    }
  }
}
