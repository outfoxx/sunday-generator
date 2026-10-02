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
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.tools.nativeConstraintPaths
import io.outfoxx.sunday.generator.kotlin.tools.withNativeBeanValidation
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
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
class KotlinUnionCommonConstraintsTest {
  @ParameterizedTest
  @MethodSource("io.outfoxx.sunday.generator.kotlin.DirectionalToleranceArguments#commonTargets")
  fun `union common constraints retain independent payload schemas`(
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
    val api = objectUnionValidationApi(frontend, directory, discriminated = true, commonMaximum = 5)
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
      val mapper = jacksonObjectMapper()
      val payload = mapper.readValue("""{"kind":"large","value":9}""", result.classLoader.loadClass("io.test.Large"))
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, payload, "Request"))
      val view = result.classLoader.loadClass("io.test.ChoiceValidation")
      val wrapped = view.constructors.single { !it.isSynthetic }.newInstance(payload)
      assertEquals(setOf("value.value"), nativeConstraintPaths(namespace, wrapped, "Request"))
      val holder = result.classLoader.loadClass("io.test.Holder")
      val valid = mapper.readValue("""{"choice":{"kind":"large","value":2}}""", holder)
      assertEquals(emptySet<String>(), nativeConstraintPaths(namespace, valid, "Request"))
      val field = holder.getDeclaredField("choice").apply { isAccessible = true }
      field.set(valid, payload)
      assertEquals(setOf("choice.value"), nativeConstraintPaths(namespace, valid, "Request"))
      val choice = result.classLoader.loadClass("io.test.Choice")
      mapper.readValue("""{"kind":"large","value":2}""", choice)
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue("""{"kind":"large","value":9}""", choice)
      }
      assertThrows(JsonMappingException::class.java) {
        mapper.readValue("""{"kind":"large","value":5.00000000000000000001}""", choice)
      }
    }
  }
}
