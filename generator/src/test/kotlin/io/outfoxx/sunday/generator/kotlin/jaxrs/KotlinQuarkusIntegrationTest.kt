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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.jaxrs.quarkus.ServerSecurityProvider
import io.smallrye.config.SmallRyeConfigBuilder
import io.smallrye.mutiny.Uni
import jakarta.enterprise.inject.Instance
import org.eclipse.microprofile.config.Config
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotEmpty
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import java.lang.reflect.Proxy
import java.nio.file.Path

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("security")
class KotlinQuarkusIntegrationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `shared provider factories compile for every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, serverQuarkus = "provider")
    val registry = registry(GenerationMode.Server)
    KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Server)).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    val factory = compiled.classLoader.loadClass("io.test.OpenAPISecurityFactory")
    expectThat(
      factory.declaredMethods
        .single {
          it.name == "security"
        }.returnType.name,
    ).isEqualTo("io.test.OpenAPISecurity")
    val producer = factory.getDeclaredConstructor().newInstance()
    val method = factory.getMethod("security", Instance::class.java, Instance::class.java)
    val provider =
      object : ServerSecurityProvider {
        override val name = "verifier"

        override fun binding() =
          ServerSecurityProvider.Binding(
            ServerSecurityProvider.Authenticator { _, _, _ ->
              Uni.createFrom().nullItem()
            },
            permissions = { emptySet() },
          )
      }

    fun resolve(providers: List<ServerSecurityProvider>) =
      try {
        method.invoke(producer, instance(providers), instance<Any>(emptyList()))
      } catch (failure: java.lang.reflect.InvocationTargetException) {
        throw failure.targetException
      }
    expectCatching { resolve(listOf(provider)) }.isSuccess()
    expectCatching { resolve(emptyList()) }.isFailure().isA<IllegalArgumentException>()
    expectCatching { resolve(listOf(provider, provider)) }.isFailure().isA<IllegalArgumentException>()
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native server gates compile without a generated authenticator`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, serverQuarkus = "oidc")
    val registry = registry(GenerationMode.Server)
    KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Server)).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    expectThat(types.keys.any { it.simpleName == "OpenAPISecurity" }).isFalse()
    expectThat(types.keys.any { it.simpleName.startsWith("OpenAPIOidcPolicy_") }).isTrue()
    val policy = types.keys.first { it.simpleName.startsWith("OpenAPIOidcPolicy_") }
    val constructor =
      compiled.classLoader
        .loadClass(policy.canonicalName)
        .constructors
        .single()
    val oidcType = constructor.parameterTypes[1]
    val oidc =
      Proxy.newProxyInstance(
        oidcType.classLoader,
        arrayOf(oidcType),
      ) { _, _, _ -> error("Unexpected authentication") }
    val valid =
      mapOf(
        "quarkus.oidc.auth-server-url" to "https://issuer.example",
        "quarkus.oidc.token.issuer" to "https://issuer.example",
        "quarkus.oidc.token.audience" to "api",
      )

    fun configure(settings: Map<String, String>) {
      val config: Config = SmallRyeConfigBuilder().withDefaultValues(settings).build()
      try {
        constructor.newInstance(config, oidc)
      } catch (failure: java.lang.reflect.InvocationTargetException) {
        throw failure.targetException
      }
    }
    expectCatching { configure(valid) }.isSuccess()
    valid.keys.forEach { missing ->
      expectCatching { configure(valid - missing) }.isFailure().isA<IllegalArgumentException>()
    }
    expectCatching {
      configure(
        valid + ("quarkus.oidc.token.issuer" to "any"),
      )
    }.isFailure().isA<IllegalArgumentException>()
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native acquisition compiles without Sunday retry wrappers`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, clientQuarkus = "acquire")
    val registry = registry(GenerationMode.Client)
    KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Client)).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
    val methods =
      types.keys
        .filter { it.enclosingClassName() == null }
        .map {
          compiled.classLoader.loadClass(it.canonicalName)
        }.filter { it.isInterface }
        .flatMap { it.declaredMethods.toList() }
    expectThat(
      methods.filter { method ->
        method.annotations.any { it.annotationClass.simpleName == "OidcClientFilter" }
      },
    ).isNotEmpty()
    expectThat(methods.any { it.name.endsWith("Transport") || it.name.endsWith("WithRetry") }).isFalse()
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native propagation and exchange compile for every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    listOf("propagate", "exchange").forEach { mode ->
      val api = scopedSecurityApi(frontend, directory, clientQuarkus = mode)
      val registry = registry(GenerationMode.Client)
      KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Client)).generateServiceTypes()
      val types = registry.buildTypes()
      val compiled = compileTypesResult(types)
      expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
      val annotations =
        types.keys
          .filter { it.enclosingClassName() == null }
          .map { compiled.classLoader.loadClass(it.canonicalName) }
          .filter { it.isInterface }
          .flatMap { it.declaredMethods.toList() }
          .flatMap { it.annotations.toList() }
          .filter { it.annotationClass.simpleName == "AccessToken" }
      expectThat(annotations).isNotEmpty()
      annotations.forEach { annotation ->
        val client =
          annotation.annotationClass.java
            .getMethod("exchangeTokenClient")
            .invoke(annotation) as String
        expectThat(client.isNotEmpty()).isEqualTo(mode == "exchange")
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `explicit web application bindings compile for every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi(frontend, directory, serverQuarkus = "webApp")
    val registry = registry(GenerationMode.Server)
    KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Server)).generateServiceTypes()
    val compiled = compileTypesResult(registry.buildTypes())
    expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> instance(values: List<T>): Instance<T> =
    Proxy.newProxyInstance(
      Instance::class.java.classLoader,
      arrayOf(Instance::class.java),
    ) { _, method, _ ->
      when (method.name) {
        "iterator" -> values.iterator()
        "isUnsatisfied" -> values.isEmpty()
        "isAmbiguous" -> values.size > 1
        "get" -> values.single()
        else -> error("Unexpected Instance method: " + method.name)
      }
    } as Instance<T>

  private fun registry(mode: GenerationMode) = KotlinTypeRegistry("io.test", null, mode, emptySet())

  private fun options(mode: GenerationMode) =
    KotlinJAXRSOptions(
      coroutineServiceMethods = false,
      coroutineFlowMethods = false,
      reactiveResponseType = null,
      explicitSecurityParameters = false,
      baseUriMode = null,
      alwaysUseResponseReturn = false,
      defaultServicePackageName = "io.test",
      defaultProblemBaseUri = "https://example.com/problems/",
      defaultMediaTypes = listOf("application/json"),
      serviceSuffix = "API",
      quarkus = true,
      resourceAdapters = mode == GenerationMode.Server,
      enforceSecuritySchemes = mode == GenerationMode.Server,
      profile = "internal",
    )
}
