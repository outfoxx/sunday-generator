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
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.anyOfDeserializationApi
import io.outfoxx.sunday.generator.tools.anyOfInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfValidWires
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinAnyOfDeserializationTest {
  @ParameterizedTest
  @MethodSource("io.outfoxx.sunday.generator.kotlin.DirectionalToleranceArguments#targets")
  fun `anyOf tries complete alternatives and preserves open fields`(
    frontend: String,
    namespace: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val registry =
      KotlinTypeRegistry(
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
    val api = anyOfDeserializationApi(frontend, directory)
    if (target == "sunday") {
      KotlinSundayIrGenerator(
        api,
        registry,
        KotlinSundayOptions(
          kotlinPlacement("io.test", "io.test.service"),
          "http://example.com/",
          listOf("application/json"),
          "API",
          preserveUnknownFields = true,
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
          placement = kotlinPlacement("io.test", "io.test.service"),
          defaultProblemBaseUri = "http://example.com/",
          defaultMediaTypes = listOf("application/json"),
          serviceSuffix = "API",
          quarkus = false,
          preserveUnknownFields = true,
        ),
      ).generateServiceTypes()
    }
    val result = compileTypesResult(registry.buildTypes())
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    withNativeBeanValidation(namespace, result.classLoader) {
      val mapper =
        jacksonObjectMapper()
          .registerModule(JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      val union = result.classLoader.loadClass("io.test.UserResponse")
      mapper.readTree(anyOfValidWires).forEachIndexed { index, raw ->
        val value = mapper.readValue(raw.toString(), union)
        val expectedType = if (index == 1) "UserSelfResponse" else "UserSummaryResponse"
        assertEquals(expectedType, value.javaClass.simpleName, raw.toString())
        assertEquals(raw, mapper.readTree(mapper.writeValueAsString(value)), raw.toString())
      }
      for (invalid in mapper.readTree(anyOfInvalidWires)) {
        val failure = assertThrows(JsonMappingException::class.java) { mapper.readValue(invalid.toString(), union) }
        assertEquals(2, failure.suppressed.size)
      }
    }
  }
}
