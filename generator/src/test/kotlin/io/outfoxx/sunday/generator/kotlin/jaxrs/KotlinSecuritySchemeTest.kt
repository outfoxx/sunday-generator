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

import com.squareup.kotlinpoet.ClassName
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
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
        if (target == "quarkus") {
          assertTrue(resource.contains("@AuthorizationPolicy"), resource)
          assertTrue(!resource.contains("this.endpointSecurity.authorize("), resource)
          assertTrue(runtime.contains("SecurityIdentity"), runtime)
          assertTrue(!runtime.contains("ResourceInfo"), runtime)
        } else {
          assertTrue(resource.contains("this.endpointSecurity.authorize("), resource)
        }
        if (target != "quarkus") assertTrue(resource.contains("\"oauth\" to setOf(\"read\")"), resource)
        assertTrue(runtime.contains("https://issuer.example/token"), runtime)
        assertTrue(runtime.contains("https://issuer.example/.well-known/openid-configuration"), runtime)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `native specialization emits only necessary shared checks`(kind: String) {
    val paths =
      if (kind == "composed") {
        listOf("openapi/ir/security-enforcement.yaml", "asyncapi/ir/security-enforcement.yaml")
      } else {
        listOf("$kind/ir/security-enforcement." + if (kind == "raml") "raml" else "yaml")
      }
    val base =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(paths.map { javaClass.getResource("/$it")!!.toURI() })
    val service = base.services.first()
    val operation = service.operations.first()

    fun auth(vararg permissions: String) =
      GeneratedAuth(
        schemes = listOf("token"),
        requirements = listOf(GeneratedSecurityRequirement(listOf("token"), mapOf("token" to permissions.toList()))),
        securitySchemes = listOf(GeneratedSecurityScheme(name = "token", type = "http", scheme = "bearer")),
        securityOverride = true,
      )

    fun api(
      global: GeneratedAuth?,
      second: GeneratedAuth?,
    ) = base.copy(
      auth = global,
      services =
        listOf(
          service.copy(
            name = "Uniform",
            group = null,
            auth = null,
            operations =
              listOf(
                operation.copy(id = "first", path = "/uniform/first", auth = null),
                operation.copy(id = "second", path = "/uniform/second", auth = second),
              ),
          ),
        ),
    )

    fun checkShape(
      api: GeneratedApi,
      mechanisms: Int,
      policies: Int,
      hoisted: Boolean,
    ) {
      val generated = compileNative(api)
      assertEquals(mechanisms, generated.keys.count { it.simpleName.startsWith("OpenAPIAuthentication_") })
      assertEquals(policies, generated.keys.count { it.simpleName.startsWith("OpenAPIPolicy_") })
      generated.values.forEach { source ->
        assertTrue(
          !source.contains("ResourceInfo") &&
            !source.contains("normalizedPath") &&
            !source.contains("endpointSecurity.authorize"),
          source,
        )
      }
      val resource = generated.getValue(ClassName("io.test", "UniformAPIResource"))
      assertEquals(hoisted, resource.substringBefore("public class").contains("@HttpAuthenticationMechanism"), resource)
      if (mechanisms == 0) assertTrue(generated.keys.none { it.simpleName == "OpenAPISecurity" })
      if (mechanisms == 1 &&
        policies == 0
      ) {
        assertTrue(!generated.getValue(ClassName("io.test", "OpenAPISecurity")).contains("fun authorize("))
      }
    }
    // Inherited and explicitly repeated declarations must select the same constant strategy.
    checkShape(api(auth(), auth()), 1, 0, true)
    checkShape(api(auth("read"), auth("read")), 1, 1, true)
    checkShape(api(auth("read"), auth("write")), 1, 2, false)
    val tenant =
      GeneratedSecurityScheme(name = "tenant", type = "http", scheme = "basic")
    val composite =
      auth("read").copy(
        schemes = listOf("token", "tenant"),
        securitySchemes = auth().securitySchemes + tenant,
        requirements =
          listOf(
            GeneratedSecurityRequirement(listOf("token", "tenant"), mapOf("token" to listOf("read"))),
            GeneratedSecurityRequirement(listOf("tenant")),
          ),
      )
    checkShape(api(composite, composite), 1, 1, true)
    checkShape(api(composite, composite.copy(requirements = composite.requirements.reversed())), 1, 2, false)
    checkShape(
      api(auth(), composite.copy(requirements = listOf(GeneratedSecurityRequirement(listOf("tenant"))))),
      2,
      0,
      false,
    )
    checkShape(api(auth(), GeneratedAuth(securityOverride = true)), 1, 0, false)
    checkShape(api(GeneratedAuth(securityOverride = true), null), 0, 0, false)
    checkShape(api(null, null), 0, 0, false)
  }

  private fun compileNative(api: GeneratedApi): Map<ClassName, String> {
    val registry = registry("quarkus")
    KotlinJAXRSIrGenerator(api, registry, options("quarkus")).generateServiceTypes()
    val types = registry.buildTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(types))
    return types.keys.filter { it.topLevelClassName() == it }.associateWith {
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Kotlin,
        "${it.packageName.replace('.', '/')}/${it.simpleName}.kt",
      )
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
