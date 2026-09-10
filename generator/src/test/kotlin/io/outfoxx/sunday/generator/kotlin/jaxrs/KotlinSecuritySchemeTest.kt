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

package io.outfoxx.sunday.generator.kotlin.jaxrs

import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypes
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinSecuritySchemeTest {

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `scheme enforcement compiles for every frontend and JAX-RS implementation`(kind: String) {
    val paths =
      if (kind == "composed") {
        listOf("openapi/ir/security-enforcement.yaml", "asyncapi/ir/security-enforcement.yaml")
      } else {
        listOf("$kind/ir/security-enforcement." + if (kind == "raml") "raml" else "yaml")
      }
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(paths.map { javaClass.getResource("/$it")!!.toURI() })
    listOf("javax", "jakarta", "quarkus").forEach { target ->
      val registry = registry(target)
      KotlinJAXRSIrGenerator(api, registry, options(target)).generateServiceTypes()
      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(registry.buildTypes()))
      val runtime = source("OpenAPISecurity")
      assertTrue(runtime.contains("Missing security authenticators:"), runtime)
      if (kind == "openapi" || kind == "composed") {
        val resource = source("PolicyAPIResource")
        assertTrue(resource.contains("this.endpointSecurity.authorize("), resource)
        assertTrue(resource.contains("\"oauth\" to setOf(\"read\")"), resource)
        assertTrue(runtime.contains("https://issuer.example/token"), runtime)
        assertTrue(runtime.contains("https://issuer.example/.well-known/openid-configuration"), runtime)
      }
    }
  }

  @Test
  fun `scheme enforcement requires server resource adapters`() {
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(javaClass.getResource("/openapi/ir/security-enforcement.yaml")!!.toURI())
    assertThrows(GenerationException::class.java) {
      KotlinJAXRSIrGenerator(api, registry("quarkus"), options("quarkus", adapters = false))
        .generateServiceTypes()
    }
    assertThrows(GenerationException::class.java) {
      KotlinJAXRSIrGenerator(api, registry("quarkus"), options("quarkus", explicitCredentials = true))
        .generateServiceTypes()
    }
  }

  private fun registry(target: String) =
    KotlinTypeRegistry(
      "io.test",
      null,
      GenerationMode.Server,
      if (target == "javax") emptySet() else setOf(KotlinTypeRegistry.Option.UseJakartaPackages),
    )

  private fun options(
    target: String,
    adapters: Boolean = true,
    explicitCredentials: Boolean = false,
  ) = KotlinJAXRSOptions(
    coroutineFlowMethods = target == "quarkus",
    coroutineServiceMethods = target == "quarkus",
    reactiveResponseType = null,
    explicitSecurityParameters = explicitCredentials,
    baseUriMode = null,
    alwaysUseResponseReturn = false,
    defaultServicePackageName = "io.test",
    defaultProblemBaseUri = "http://example.com/",
    defaultMediaTypes = listOf("application/json"),
    serviceSuffix = "API",
    quarkus = target == "quarkus",
    aggregateServices = true,
    aggregateServiceName = "SecureAPI",
    resourceAdapters = adapters,
    enforceSecuritySchemes = true,
  )

  private fun source(name: String): String =
    CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/$name.kt")
}
