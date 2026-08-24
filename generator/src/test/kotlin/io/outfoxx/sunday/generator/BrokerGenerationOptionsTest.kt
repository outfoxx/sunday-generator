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

package io.outfoxx.sunday.generator

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.python.PythonGeneratorOptions
import io.outfoxx.sunday.generator.python.PythonLitestarIrGenerator
import io.outfoxx.sunday.generator.python.PythonSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayOptions
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BrokerGenerationOptionsTest {

  private val api =
    GeneratedApi(
      name = "Empty API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory://empty"),
    )

  @Test
  fun `unsupported generators reject broker service generation`() {
    assertUnsupported("Kotlin/JAX-RS") {
      KotlinJAXRSIrGenerator(api, kotlinTypeRegistry(), kotlinJaxRsOptions()).generateServiceTypes()
    }
    assertUnsupported("Swift/Sunday") {
      SwiftSundayIrGenerator(api, SwiftTypeRegistry(setOf()), swiftOptions()).generateServiceTypes()
    }
    assertUnsupported("TypeScript/Sunday") {
      TypeScriptSundayIrGenerator(api, TypeScriptTypeRegistry(setOf()), typeScriptOptions()).generateServiceTypes()
    }
    assertUnsupported("Python/Sunday") {
      PythonSundayIrGenerator(api, PythonGeneratorOptions(generateBrokerServices = true)).generateModules(setOf())
    }
    assertUnsupported("Python/Litestar") {
      PythonLitestarIrGenerator(api, PythonGeneratorOptions(generateBrokerServices = true)).generateModules(setOf())
    }
  }

  private fun assertUnsupported(
    target: String,
    generate: () -> Unit,
  ) {
    val error = assertThrows(GenerationException::class.java, generate)
    assertEquals("Broker service generation is not supported for $target", error.message)
  }

  private fun kotlinTypeRegistry(): KotlinTypeRegistry =
    KotlinTypeRegistry(
      null,
      null,
      GenerationMode.Server,
      setOf(),
      KotlinProblemLibrary.ZALANDO,
      KotlinProblemRfc.RFC9457,
    )

  private fun kotlinJaxRsOptions(): KotlinJAXRSOptions =
    KotlinJAXRSOptions(
      coroutineFlowMethods = false,
      coroutineServiceMethods = false,
      reactiveResponseType = null,
      explicitSecurityParameters = false,
      baseUriMode = null,
      alwaysUseResponseReturn = false,
      defaultServicePackageName = null,
      defaultProblemBaseUri = "http://example.com/",
      defaultMediaTypes = listOf("application/json"),
      serviceSuffix = "API",
      quarkus = false,
      generateBrokerServices = true,
    )

  private fun swiftOptions(): SwiftSundayOptions =
    SwiftSundayOptions(
      defaultProblemBaseUri = "http://example.com/",
      defaultMediaTypes = listOf("application/json"),
      serviceSuffix = "API",
      generateBrokerServices = true,
    )

  private fun typeScriptOptions(): TypeScriptSundayOptions =
    TypeScriptSundayOptions(
      defaultProblemBaseUri = "http://example.com/",
      defaultMediaTypes = listOf("application/json"),
      serviceSuffix = "API",
      generateBrokerServices = true,
    )
}
