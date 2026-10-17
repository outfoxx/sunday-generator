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
import io.outfoxx.sunday.generator.tools.anyOfObjectConstraintsApi
import io.outfoxx.sunday.generator.tools.anyOfObjectInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfObjectValidWires
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

/** Compile-backed runtime coverage for inclusive object constraints from issue #277. */
@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("models")
@Tag("validation")
class KotlinAnyOfObjectConstraintsTest {
  @ParameterizedTest
  @CsvSource(
    "shared,server",
    "inline,server",
    "named,server",
    "shared-composed,server",
    "shared,client",
    "inline,client",
    "named,client",
    "shared,quarkus",
    "inline,quarkus",
    "named,quarkus",
    "shared,sunday",
    "inline,sunday",
    "named,sunday",
  )
  fun `object anyOf preserves inclusive requirements and round trips`(
    shape: String,
    target: String,
    @TempDir directory: Path,
  ) {
    val namespace = "jakarta"
    val registry =
      KotlinTypeRegistry(
        "io.test",
        null,
        if (target in setOf("server", "quarkus")) GenerationMode.Server else GenerationMode.Client,
        buildSet {
          add(KotlinTypeRegistry.Option.ImplementModel)
          add(KotlinTypeRegistry.Option.JacksonAnnotations)
          add(KotlinTypeRegistry.Option.ValidationConstraints)
          if (namespace == "jakarta") add(KotlinTypeRegistry.Option.UseJakartaPackages)
        },
        problemLibrary = KotlinProblemLibrary.SUNDAY,
      )
    val api = anyOfObjectConstraintsApi(shape, directory)
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
          quarkus = target == "quarkus",
          resourceAdapters = target == "quarkus",
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
      val union = result.classLoader.loadClass("io.test.CheckoutRequest")
      if (target == "quarkus") {
        for (name in listOf("API", "APIResource")) {
          val service = result.classLoader.loadClass("io.test.service.$name")
          assertEquals(
            union,
            service.methods
              .single { it.name == "checkout" }
              .parameterTypes
              .first(),
          )
        }
      }
      mapper.readTree(anyOfObjectValidWires).forEach { raw ->
        val value = mapper.readValue(raw.toString(), union)
        assertEquals(raw, mapper.readTree(mapper.writeValueAsString(value)), raw.toString())
      }
      for (invalid in mapper.readTree(anyOfObjectInvalidWires)) {
        assertThrows(JsonMappingException::class.java) { mapper.readValue(invalid.toString(), union) }
      }
    }
  }
}
