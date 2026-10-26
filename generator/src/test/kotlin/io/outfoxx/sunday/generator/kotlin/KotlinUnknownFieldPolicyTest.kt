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

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.patchableApi
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinUnknownFieldPolicyTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `unconstrained inherited patches respect mapper unknown-field policy`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = patchableApi(frontend, directory)
    for (mode in listOf(UnknownFieldPreservation.Off, UnknownFieldPreservation.Schema)) {
      for (jaxrs in listOf(false, true)) {
        val loader = compile(api, mode, jaxrs)
        val type = loader.loadClass("io.test.SomeRequestPatch")
        assertFalse(type.methods.any { it.getAnnotation(JsonAnySetter::class.java)?.enabled == true })
        for (strict in listOf(false, true)) {
          val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
          mapper.readValue("""{"count":2,"display-name":"valid"}""", type)
          for (extra in listOf("1", "null", "{}", "[]")) {
            val wire = """{"count":2,"misspelled":$extra}"""
            if (strict) {
              val error = assertThrows(UnrecognizedPropertyException::class.java) { mapper.readValue(wire, type) }
              assertEquals("misspelled", error.propertyName)
            } else {
              val value = mapper.readValue(wire, type)
              assertFalse(mapper.readTree(mapper.writeValueAsBytes(value)).has("misspelled"))
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `disabled storage retains typed dynamic validation`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val original = openModelFieldsApi(frontend, directory)
    // Plain value types still require a decoder even without extra constraints or stored values.
    val api =
      original.copy(
        models =
          original.models.map { model ->
            if (model.name == "TypedRecord") {
              model.copy(
                additionalProperties = model.additionalProperties?.copy(validation = emptyMap(), allowedValues = null),
              )
            } else {
              model
            }
          },
      )
    for (jaxrs in listOf(false, true)) {
      val type = compile(api, UnknownFieldPreservation.Off, jaxrs).loadClass("io.test.TypedRecord")
      for (strict in listOf(false, true)) {
        val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, strict)
        val value = mapper.readValue("""{"id":"one","future":2}""", type)
        // RAML represents the declared field as a pattern, whose storage is independent of catch-all policy.
        assertEquals(frontend == "raml", mapper.readTree(mapper.writeValueAsBytes(value)).has("future"))
        assertThrows(JsonMappingException::class.java) {
          mapper.readValue("""{"id":"one","future":"wrong"}""", type)
        }
      }
    }
  }

  private fun compile(
    api: GeneratedApi,
    mode: UnknownFieldPreservation,
    jaxrs: Boolean,
  ): ClassLoader {
    val registry =
      KotlinTypeRegistry(
        null,
        if (jaxrs) GenerationMode.Server else GenerationMode.Client,
        setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.JacksonAnnotations),
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    if (jaxrs) {
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
          placement = kotlinPlacement("io.test", "io.test.service"),
          defaultProblemBaseUri = "https://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
          preserveUnknownFields = mode,
        ),
      ).generateServiceTypes()
    } else {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test", "io.test.service"),
          "https://example.com/",
          listOf("application/json"),
          "API",
          preserveUnknownFields = mode,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    return result.classLoader
  }
}
