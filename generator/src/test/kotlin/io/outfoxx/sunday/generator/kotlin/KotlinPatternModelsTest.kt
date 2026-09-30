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
import io.outfoxx.sunday.generator.kotlin.jaxrs.kotlinJAXRSTestOptions
import io.outfoxx.sunday.generator.kotlin.sunday.kotlinSundayTestOptions
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinPatternModelsTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    @TempDir directory: Path,
  ) {
    val api = patternModelsApi(directory, composed)
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
          KotlinJAXRSIrGenerator(api, registry, kotlinJAXRSTestOptions).generateServiceTypes()
        } else {
          KotlinSundayIrGenerator(api, registry, kotlinSundayTestOptions).generateServiceTypes()
        }
        val result = compileTypesResult(registry.buildTypes())
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val mapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val model = result.classLoader.loadClass("io.test.PatternRecord")
        patternModelValid.forEach { wire ->
          val decoded = mapper.readValue(wire, model)
          if (preserve) {
            val input = mapper.readTree(wire)
            val output = mapper.readTree(mapper.writeValueAsString(decoded))
            input.properties().forEach { (key, value) -> assertEquals(value, output[key], key) }
          }
        }
        patternModelInvalid.forEach { wire ->
          assertThrows(JsonMappingException::class.java, { mapper.readValue(wire, model) }, wire)
        }
        mapper.readValue("""{"x-fixed":"a"}""", result.classLoader.loadClass("io.test.ClosedFieldParent"))
        mapper.readValue("""{"x-fixed":"ok"}""", result.classLoader.loadClass("io.test.PatternFieldParent"))
        patternModelRegressions.forEach { (name, values) ->
          val regressionModel = result.classLoader.loadClass("io.test.$name")
          values.first.forEach { wire -> mapper.readValue(wire, regressionModel) }
          values.second.forEach { wire ->
            assertThrows(JsonMappingException::class.java, { mapper.readValue(wire, regressionModel) }, "$name: $wire")
          }
        }
        val inherited = result.classLoader.loadClass("io.test.PatternInherited")
        mapper.readValue("""{"x-valid":"ok"}""", inherited)
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"x-invalid":"a"}""", inherited) }
        assertThrows(JsonMappingException::class.java) { mapper.readValue("""{"extra":1}""", inherited) }
        mapper.readValue("""{"extra":1,"x-valid":"ok"}""", result.classLoader.loadClass("io.test.OpenPattern"))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"extra":"wrong"}""", result.classLoader.loadClass("io.test.OpenPattern"))
        }
        mapper.readValue("""{"x-valid":"ok"}""", result.classLoader.loadClass("io.test.PatternOnly"))
      }
    }
  }
}
