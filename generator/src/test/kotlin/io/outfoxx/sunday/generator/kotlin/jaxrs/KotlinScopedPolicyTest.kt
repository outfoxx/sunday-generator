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
import io.outfoxx.sunday.generator.ir.GeneratedExceptionRef
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedPolicySetting
import io.outfoxx.sunday.generator.ir.GeneratedPolicyValues
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.kotlinPlacement
import io.outfoxx.sunday.generator.tools.scopedPolicyApi
import io.smallrye.faulttolerance.api.RateLimit
import org.eclipse.microprofile.faulttolerance.CircuitBreaker
import org.eclipse.microprofile.faulttolerance.Retry
import org.eclipse.microprofile.faulttolerance.Timeout
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Path
import java.time.temporal.ChronoUnit

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
class KotlinScopedPolicyTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `status policies compile one guard per operation through every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val input = scopedPolicyApi(frontend, directory, statusPolicies = true)
    val api =
      input.copy(
        services =
          input.services.map { service ->
            service.copy(
              operations =
                service.operations.flatMap { operation ->
                  listOf(operation, operation.copy(id = operation.id + "Copy", path = operation.path + "/copy"))
                },
            )
          },
      )
    val registry = registry(GenerationMode.Client)
    KotlinJAXRSIrGenerator(
      api,
      registry,
      options(GenerationMode.Client, "internal", coroutines = true),
    ).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val classes =
      types.keys
        .filter {
          it.enclosingClassName() == null
        }.map { compiled.classLoader.loadClass(it.canonicalName) }
    val guarded =
      classes.flatMap { it.declaredMethods.toList() }.filter {
        it.getAnnotation(io.smallrye.faulttolerance.api.ApplyGuard::class.java) !=
          null
      }
    assertEquals(api.services.sumOf { it.operations.size }, guarded.size)
    val identifiers = guarded.map { it.getAnnotation(io.smallrye.faulttolerance.api.ApplyGuard::class.java).value }
    assertEquals(identifiers.size, identifiers.distinct().size)
    guarded.forEach {
      assertNull(it.getAnnotation(Retry::class.java))
      assertNull(it.getAnnotation(CircuitBreaker::class.java))
      assertNull(it.getAnnotation(Timeout::class.java))
      assertNull(it.getAnnotation(RateLimit::class.java))
    }
    val containers = classes.flatMap { it.declaredClasses.toList() }.filter { it.simpleName == "Guards" }
    assertEquals(input.services.size, containers.size)
    containers.forEach { container ->
      assertEquals(1, container.declaredMethods.count { it.name.startsWith("policy_") && !it.name.contains('$') })
    }
    val producers =
      containers.flatMap { it.declaredFields.toList() }.filter {
        it.getAnnotation(jakarta.enterprise.inject.Produces::class.java) != null
      }
    assertTrue(
      producers.all {
        java.lang.reflect.Modifier
          .isStatic(it.modifiers)
      },
    )
    assertEquals(
      identifiers.toSet(),
      producers
        .map {
          it.getAnnotation(io.smallrye.common.annotation.Identifier::class.java).value
        }.toSet(),
    )
    val output = directory.resolve("generated")
    registry.generateFiles(outputDirectory = output)
    assertTrue(
      java.nio.file.Files
        .exists(output.resolve("META-INF/beans.xml")),
    )
    val models = directory.resolve("models")
    registry.generateFiles(
      io.outfoxx.sunday.generator.GeneratedOutputSelection(
        setOf(io.outfoxx.sunday.generator.GeneratedTypeCategory.Model),
      ),
      models,
    )
    assertTrue(
      !java.nio.file.Files
        .exists(models.resolve("META-INF/beans.xml")),
    )
    val excluded = directory.resolve("excluded")
    registry.generateFiles(
      io.outfoxx.sunday.generator
        .GeneratedOutputSelection(ownerUris = setOf(directory.resolve("other.yaml").toUri())),
      excluded,
    )
    assertTrue(
      !java.nio.file.Files
        .exists(excluded.resolve("META-INF/beans.xml")),
    )
    val restored =
      io.outfoxx.sunday.generator.ir.GeneratedApiYaml.readString(
        io.outfoxx.sunday.generator.ir.GeneratedApiYaml
          .writeString(api),
      )
    assertEquals(api, restored)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `compiled annotations agree across source roles and profiles`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi(frontend, directory)
    for ((mode, profile) in listOf(
      GenerationMode.Client to "internal",
      GenerationMode.Client to null,
      GenerationMode.Server to "internal",
    )) {
      val registry = registry(mode)
      KotlinJAXRSIrGenerator(api, registry, options(mode, profile)).generateServiceTypes()
      val types = registry.buildTypes()
      val compiled = compileTypesResult(types)
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
      val methods =
        types.keys.filter { it.enclosingClassName() == null }.flatMap {
          compiled.classLoader
            .loadClass(it.canonicalName)
            .declaredMethods
            .toList()
        }
      val guarded = methods.filter { it.getAnnotation(Timeout::class.java) != null }
      assertEquals(api.services.sumOf { it.operations.size }, guarded.size)
      for (method in guarded) {
        val timeout = method.getAnnotation(Timeout::class.java)
        assertEquals(2, timeout.value)
        assertEquals(ChronoUnit.SECONDS, timeout.unit)
        val retry = method.getAnnotation(Retry::class.java)
        assertEquals(if (mode == GenerationMode.Client && profile == "internal") 1 else 3, retry.maxRetries)
        assertEquals(1, retry.delay)
        assertEquals(ChronoUnit.NANOS, retry.delayUnit)
        assertEquals(listOf(IOException::class), retry.retryOn.toList())
        assertEquals(emptyList<Any>(), retry.abortOn.toList())
        val rate = method.getAnnotation(RateLimit::class.java)
        assertEquals(if (mode == GenerationMode.Client) 3 else 7, rate.value)
        val breaker = method.getAnnotation(CircuitBreaker::class.java)
        if (mode == GenerationMode.Client && profile == "internal") {
          assertEquals(listOf(IllegalArgumentException::class), breaker.skipOn.toList())
        } else {
          assertNull(breaker)
        }
      }
    }
  }

  @Test
  @Tag("responses")
  fun `typed generated problem exceptions and explicit disables compile`(
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi("openapi", directory)
    val policy =
      GeneratedPolicy(
        all =
          GeneratedPolicyValues(
            timeout = GeneratedPolicySetting(enabled = false),
            rateLimit = GeneratedPolicySetting(enabled = false),
            retry =
              GeneratedPolicySetting(
                value =
                  GeneratedPolicyValues.Retry(
                    retryOn = listOf(GeneratedExceptionRef(problem = "unauthorized")),
                    abortOn = listOf(GeneratedExceptionRef(problem = "ForbiddenProblem")),
                  ),
              ),
            circuitBreaker =
              GeneratedPolicySetting(
                value =
                  GeneratedPolicyValues.CircuitBreaker(
                    failOn = listOf(GeneratedExceptionRef(className = "java.lang.Exception")),
                    skipOn = listOf(GeneratedExceptionRef(problem = "unauthorized")),
                  ),
              ),
          ),
      )
    val updated =
      api.copy(
        problems =
          listOf(
            GeneratedProblem(
              "UnauthorizedProblem",
              sourceName = "unauthorized",
              typeUri = "urn:unauthorized",
              status = 401,
              title = "Unauthorized",
              detail = "Authentication required",
              source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
            ),
            GeneratedProblem(
              "ForbiddenProblem",
              sourceName = "forbidden",
              typeUri = "urn:forbidden",
              status = 403,
              title = "Forbidden",
              detail = "Access denied",
              source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
            ),
          ),
        services =
          api.services.map { service ->
            service.copy(operations = service.operations.map { it.copy(policy = it.policy!!.inherit(policy)) })
          },
      )
    val registry = registry(GenerationMode.Client)
    KotlinJAXRSIrGenerator(updated, registry, options(GenerationMode.Client)).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val method =
      types.keys
        .flatMap {
          compiled.classLoader
            .loadClass(it.canonicalName)
            .declaredMethods
            .toList()
        }.single {
          it.getAnnotation(Retry::class.java) !=
            null
        }
    assertEquals(
      "UnauthorizedProblem",
      method
        .getAnnotation(Retry::class.java)
        .retryOn
        .single()
        .simpleName,
    )
    assertEquals(
      "ForbiddenProblem",
      method
        .getAnnotation(Retry::class.java)
        .abortOn
        .single()
        .simpleName,
    )
    assertEquals(
      "UnauthorizedProblem",
      method
        .getAnnotation(CircuitBreaker::class.java)
        .skipOn
        .single()
        .simpleName,
    )
    assertNull(method.getAnnotation(Timeout::class.java))
    assertNull(method.getAnnotation(RateLimit::class.java))
  }

  @Test
  fun `status predicates compile for every configured Problem library`(
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi("openapi", directory, statusPolicies = true)
    for (library in KotlinProblemLibrary.entries) {
      val registry = registry(GenerationMode.Client, library)
      KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Client)).generateServiceTypes()
      val compiled = compileTypesResult(registry.buildTypes())
      assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    }
  }

  @Test
  fun `status policies reject streaming responses`(
    @TempDir directory: Path,
  ) {
    val input = scopedPolicyApi("asyncapi", directory, statusPolicies = true)
    val api =
      input.copy(
        services =
          input.services.map { service ->
            service.copy(
              operations =
                service.operations.map {
                  it.copy(
                    streaming =
                      io.outfoxx.sunday.generator.ir.GeneratedStreaming(
                        io.outfoxx.sunday.generator.ir.GeneratedStreaming.Kind.EVENT_STREAM,
                      ),
                  )
                },
            )
          },
      )
    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(
          api,
          registry(GenerationMode.Client),
          options(GenerationMode.Client),
        ).generateServiceTypes()
      }
    assertTrue(error.message!!.contains("streaming or Future"), error.message)
  }

  @Test
  fun `status selectors reject server and portable generation`(
    @TempDir directory: Path,
  ) {
    val api = scopedPolicyApi("openapi", directory, statusPolicies = true)
    for (mode in GenerationMode.entries) {
      val selected =
        api.copy(
          services =
            api.services.map { service ->
              service.copy(
                operations =
                  service.operations.map { operation ->
                    operation.copy(
                      policy =
                        GeneratedPolicy(
                          all =
                            operation.policy!!.resolve(
                              io.outfoxx.sunday.generator
                                .GenerationContext(GenerationMode.Client),
                            ) { a, b -> a.merge(b) },
                        ),
                    )
                  },
              )
            },
        )
      val error =
        assertThrows(GenerationException::class.java) {
          KotlinJAXRSIrGenerator(
            selected,
            registry(mode),
            options(mode, quarkus = mode == GenerationMode.Server),
          ).generateServiceTypes()
        }
      assertTrue(
        error.message!!.contains(
          if (mode ==
            GenerationMode.Server
          ) {
            "Quarkus client"
          } else {
            "enable Quarkus"
          },
        ),
        error.message,
      )
    }
  }

  @Test
  fun `active policies reject non Quarkus generation`(
    @TempDir directory: Path,
  ) {
    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(
          scopedPolicyApi("openapi", directory),
          registry(GenerationMode.Client),
          options(GenerationMode.Client, quarkus = false),
        ).generateServiceTypes()
      }
    assertTrue(error.message!!.contains("enable Quarkus generation"))
  }

  private fun registry(
    mode: GenerationMode,
    library: KotlinProblemLibrary = KotlinProblemLibrary.QUARKUS,
  ) = KotlinTypeRegistry(
    null,
    mode,
    setOf(KotlinTypeRegistry.Option.UseJakartaPackages),
    problemLibrary = library,
  )

  private fun options(
    mode: GenerationMode,
    profile: String? = null,
    quarkus: Boolean = true,
    coroutines: Boolean = false,
  ) = KotlinJAXRSOptions(
    coroutineFlowMethods = false,
    coroutineServiceMethods = coroutines,
    reactiveResponseType = null,
    explicitSecurityParameters = false,
    baseUriMode = null,
    alwaysUseResponseReturn = false,
    placement = kotlinPlacement("io.test", "io.test"),
    defaultProblemBaseUri = "https://example.test/problems/",
    defaultMediaTypes = listOf("application/json"),
    serviceSuffix = "API",
    quarkus = quarkus,
    resourceAdapters = mode == GenerationMode.Server,
    profile = profile,
  )
}
