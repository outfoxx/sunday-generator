/*
 * Copyright 2020 Outfox, Inc.
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
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedJaxrs
import io.outfoxx.sunday.generator.ir.GeneratedModeFlag
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypes
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertKotlinJaxrsSnapshot
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinJAXRSResourceAdapterTest {

  @ParameterizedTest
  @ValueSource(strings = ["javax", "jakarta", "quarkus"])
  fun `RAML anonymous alternatives compile to public endpoints without relaxing protected overrides`(target: String) {
    val raml = "raml/ir/security-overrides.raml"
    val openApi = "openapi/ir/server-adapters.yaml"
    listOf(listOf(raml), listOf(raml, openApi), listOf(openApi, raml)).forEach { paths ->
      val registry = registry(target)
      KotlinJAXRSIrGenerator(export(*paths.toTypedArray()), registry, options(target)).generateServiceTypes()
      val result = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
      val resource = result.classLoader.loadClass("io.test.RamlAccessAPIResource")
      val publicOperations = setOf("publicAccess", "mixedAccess", "resourcePublic", "traitPublic")
      val protectedOperations = setOf("inheritedAccess", "methodProtected", "nestedInherited", "replacementAccess")
      (publicOperations + protectedOperations).forEach { name ->
        val annotations =
          resource.declaredMethods
            .single { it.name == name }
            .annotations
            .map { it.annotationClass.simpleName }
        assertEquals(name in publicOperations, "PermitAll" in annotations, name)
        if (target == "quarkus") {
          assertEquals(name in protectedOperations, "Authenticated" in annotations, name)
        }
      }
      if (target != "quarkus") {
        assertEquals(4, Regex("userPrincipal == null").findAll(source("RamlAccessAPIResource")).count())
      }
    }
  }

  @ParameterizedTest
  @CsvSource("javax,false", "jakarta,false", "quarkus,false", "quarkus,true")
  fun `compiles every operation shape with a transport-free delegate`(
    target: String,
    coroutines: Boolean,
  ) {
    val registry = registry(target)
    KotlinJAXRSIrGenerator(shapes(), registry, options(target, coroutines = coroutines)).generateServiceTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(registry.buildTypes()))

    val handler = source("API")
    val resource = source("APIResource")
    assertFalse(handler.contains("@Path"), handler)
    assertFalse(handler.contains("@Context"), handler)
    assertFalse(handler.contains("@Valid"), handler)
    assertFalse(handler.contains("@GET"), handler)
    assertFalse(handler.contains("@Timeout"), handler)
    assertFalse(handler.contains("@FGA"), handler)
    assertTrue(resource.contains("this.delegate.created(uriInfo)"), resource)
    assertTrue(resource.contains("this.delegate.asynchronous(asyncResponse)"), resource)
    assertTrue(resource.contains("this.delegate.`when`(delegate_, securityContext)"), resource)
    assertTrue(resource.contains("@Suspended"), resource)
    assertTrue(resource.contains("@Context"), resource)
    if (target == "quarkus") {
      assertTrue(resource.contains("@Authenticated"), resource)
      assertTrue(resource.contains("@Timeout"), resource)
      assertTrue(resource.contains("@FGARelation"), resource)
      assertTrue(resource.contains("@Inject"), resource)
    } else {
      assertTrue(resource.contains("userPrincipal == null"), resource)
    }
    assertKotlinJaxrsSnapshot("KotlinJAXRSResourceAdapterTest/$target-$coroutines-handler.kt", handler)
    assertKotlinJaxrsSnapshot("KotlinJAXRSResourceAdapterTest/$target-$coroutines-resource.kt", resource)
  }

  @ParameterizedTest
  @CsvSource("javax,false", "jakarta,true", "quarkus,false", "quarkus,true")
  fun `source security is enforced on concrete endpoints including aggregate subresources`(
    target: String,
    aggregate: Boolean,
  ) {
    val api = export("openapi/ir/server-adapters.yaml")
    val registry = registry(target)
    KotlinJAXRSIrGenerator(api, registry, options(target, aggregate = aggregate)).generateServiceTypes()
    val types = registry.buildTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(types))
    val handler = source("UsersAPI")
    val resource = source("UsersAPIResource")
    assertFalse(handler.contains("PermitAll"), handler)
    assertFalse(handler.contains("Authenticated"), handler)
    assertEquals(3, Regex("@PermitAll").findAll(resource).count(), resource)
    assertTrue(Regex("""this\.delegate\.getUser\(userId,\s+securityContext\)""").containsMatchIn(resource), resource)
    if (aggregate) {
      val root = source("TestAPIResource")
      assertTrue(root.contains("this.users"), root)
      assertTrue(root.contains("UsersAPIResource"), root)
      assertTrue(root.contains("accountId: String): AccountsAPIResource"), root)
      val accounts = source("AccountsAPIResource")
      assertTrue(accounts.contains("this.delegate.getAccount(accountId)"), accounts)
      assertFalse(resource.substringBefore("public class").contains("@Path"), resource)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `all source frontends compile resource adapters`(kind: String) {
    val paths =
      when (kind) {
        "raml" -> listOf("raml/resource-gen/req-mixed-params-inline-types.raml")
        "openapi" -> listOf("openapi/ir/server-adapters.yaml")
        "asyncapi" -> listOf("asyncapi/ir/server-adapters.yaml")
        else -> listOf("openapi/ir/server-adapters.yaml", "asyncapi/ir/server-adapters.yaml")
      }
    listOf("javax", "jakarta", "quarkus").forEach { target ->
      val registry = registry(target)
      KotlinJAXRSIrGenerator(export(*paths.toTypedArray()), registry, options(target)).generateServiceTypes()
      val types = registry.buildTypes()
      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(types))
      assertTrue(types.keys.any { it.simpleName.endsWith("Resource") })
    }
  }

  @Test
  fun `interface output remains the default and client adapters are rejected`() {
    val registry = registry("quarkus")
    KotlinJAXRSIrGenerator(shapes(), registry, options("quarkus", adapters = false)).generateServiceTypes()
    assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(registry.buildTypes()))
    assertTrue(source("API").contains("@GET"))
    assertFalse(source("API").contains("@Authenticated"))
    assertThrows(GenerationException::class.java) {
      KotlinJAXRSIrGenerator(shapes(), registry("quarkus", GenerationMode.Client), options("quarkus"))
        .generateServiceTypes()
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "req-body-param.raml",
      "req-body-param-container-valid.raml",
      "req-body-param-json-override.raml",
      "req-body-param-optional.raml",
      "req-body-param-explicit-content-type.raml",
      "res-body-param-inline-type.raml",
      "req-jaxrs-context.raml",
    ],
  )
  fun `body validation media types and scoped models compile through delegates`(fixture: String) {
    val api = export("raml/resource-gen/$fixture")
    listOf("javax", "jakarta", "quarkus").forEach { target ->
      val registry = registry(target)
      KotlinJAXRSIrGenerator(api, registry, options(target)).generateServiceTypes()
      assertEquals(KotlinCompilation.ExitCode.OK, compileTypes(registry.buildTypes()))
    }
  }

  private fun export(vararg paths: String): GeneratedApi =
    GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
      .export(paths.map { javaClass.getResource("/$it")!!.toURI() })

  private fun registry(
    target: String,
    mode: GenerationMode = GenerationMode.Server,
  ) = KotlinTypeRegistry(
    "io.test",
    null,
    mode,
    setOf(KotlinTypeRegistry.Option.ImplementModel, KotlinTypeRegistry.Option.ValidationConstraints) +
      if (target == "javax") emptySet() else setOf(KotlinTypeRegistry.Option.UseJakartaPackages),
  )

  private fun options(
    target: String,
    coroutines: Boolean = false,
    aggregate: Boolean = false,
    adapters: Boolean = true,
  ) = KotlinJAXRSOptions(
    coroutineFlowMethods = coroutines,
    coroutineServiceMethods = coroutines,
    reactiveResponseType = "io.reactivex.rxjava3.core.Single",
    explicitSecurityParameters = false,
    baseUriMode = null,
    alwaysUseResponseReturn = false,
    defaultServicePackageName = "io.test",
    defaultProblemBaseUri = "http://example.com/",
    defaultMediaTypes = listOf("application/json"),
    serviceSuffix = "API",
    quarkus = target == "quarkus",
    aggregateServices = aggregate,
    aggregateServiceName = "TestAPI",
    resourceAdapters = adapters,
  )

  private fun source(name: String): String =
    CompiledGeneratedSources.source(GeneratedCodeLanguage.Kotlin, "io/test/$name.kt")

  private fun shapes(): GeneratedApi {
    val operation =
      GeneratedOperation(
        id = "sync",
        method = "GET",
        path = "/sync",
        jaxrs = GeneratedJaxrs(reactive = false),
        responses = listOf(GeneratedResponse(status = 200, type = GeneratedTypeRef.scalar("string"))),
      )
    return GeneratedApi(
      name = "Adapter API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory://adapters"),
      auth = GeneratedAuth(schemes = listOf("bearer")),
      services =
        listOf(
          GeneratedService(
            name = "AdapterService",
            operations =
              listOf(
                operation,
                operation.copy(
                  id = "created",
                  method = "POST",
                  path = "/created",
                  responses = listOf(GeneratedResponse(status = 201)),
                ),
                operation.copy(
                  id = "asynchronous",
                  path = "/async",
                  jaxrs = GeneratedJaxrs(asynchronous = true, reactive = false),
                ),
                operation.copy(id = "reactive", path = "/reactive", jaxrs = GeneratedJaxrs(reactive = true)),
                operation.copy(
                  id = "sse",
                  path = "/sse",
                  jaxrs = GeneratedJaxrs(sse = GeneratedModeFlag(server = true)),
                ),
                operation.copy(
                  id = "events",
                  path = "/events",
                  streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                ),
                operation.copy(
                  id = "when",
                  path = "/keywords",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        "delegate",
                        GeneratedParameter.Location.QUERY,
                        GeneratedTypeRef.scalar("string"),
                      ),
                      GeneratedParameter(
                        "securityContext",
                        GeneratedParameter.Location.QUERY,
                        GeneratedTypeRef.scalar("string"),
                      ),
                    ),
                ),
                operation.copy(
                  id = "policy",
                  path = "/policy",
                  policy = GeneratedPolicy(timeout = "PT1S"),
                  auth =
                    GeneratedAuth(
                      schemes = listOf("bearer"),
                      zanzibar =
                        mapOf(
                          "objectType" to "document",
                          "objectId" to "one",
                          "relation" to "reader",
                        ),
                    ),
                ),
              ),
          ),
        ),
    )
  }
}
