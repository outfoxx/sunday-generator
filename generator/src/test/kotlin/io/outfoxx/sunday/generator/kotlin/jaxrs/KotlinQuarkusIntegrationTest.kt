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
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedSecurityRequirement
import io.outfoxx.sunday.generator.kotlin.KotlinApplicationMetadataOptions
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.jaxrs.quarkus.ServerSecurityProvider
import io.outfoxx.sunday.jaxrs.quarkus.ServerSecuritySubjectSelector
import io.smallrye.config.ConfigSourceFactory
import io.smallrye.config.SmallRyeConfigBuilder
import io.smallrye.mutiny.Uni
import jakarta.enterprise.inject.Instance
import org.eclipse.microprofile.config.Config
import org.eclipse.microprofile.config.spi.ConfigSource
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
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
import java.nio.file.Files
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
  fun `shared provider factories validate composite subject selectors for every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory, serverQuarkus = "provider")
    val auth =
      (
        listOfNotNull(source.auth) +
          source.services.flatMap { service ->
            listOfNotNull(service.auth) +
              service.operations.flatMap { operation ->
                listOfNotNull(operation.auth) + operation.serverAuth.values
              }
          }
      ).first { it.securitySchemes.isNotEmpty() }.let {
        it.copy(
          securitySchemes = it.securitySchemes + it.securitySchemes.single().copy(name = "second"),
          requirements = listOf(GeneratedSecurityRequirement(listOf("token", "second"))),
        )
      }
    val api =
      source.copy(
        auth = auth,
        services =
          source.services.map { service ->
            service.copy(
              auth = auth,
              operations = service.operations.map { it.copy(auth = auth, serverAuth = emptyMap()) },
            )
          },
      )
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

    fun resolve(selectors: List<ServerSecuritySubjectSelector>) =
      try {
        method.invoke(producer, instance(listOf(provider)), instance(selectors))
      } catch (failure: java.lang.reflect.InvocationTargetException) {
        throw failure.targetException
      }
    val selected = mutableListOf<Pair<String, Set<String>>>()
    val selector =
      ServerSecuritySubjectSelector { service, schemes ->
        selected.add(service to schemes)
        "token"
      }
    expectCatching { resolve(listOf(selector)) }.isSuccess()
    expectThat(selected.toList()).isEqualTo(listOf("io.test.OpenAPISecurity" to setOf("token", "second")))
    expectCatching { resolve(emptyList()) }.isFailure().isA<IllegalArgumentException>()
    expectCatching { resolve(listOf(selector, selector)) }.isFailure().isA<IllegalArgumentException>()
    expectCatching { resolve(listOf(ServerSecuritySubjectSelector { _, _ -> "unknown" })) }
      .isFailure()
      .isA<IllegalArgumentException>()
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
  fun `native server policies compile with public API warning suppression`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    for (mode in listOf("oidc", "webApp")) {
      for (tenant in listOf(null, "application")) {
        val api = scopedSecurityApi(frontend, directory, serverQuarkus = mode, serverTenant = tenant)
        val registry =
          KotlinTypeRegistry(
            "io.test",
            null,
            GenerationMode.Server,
            setOf(KotlinTypeRegistry.Option.SuppressPublicApiWarnings, KotlinTypeRegistry.Option.UseJakartaPackages),
          )
        KotlinJAXRSIrGenerator(
          api,
          registry,
          options(GenerationMode.Server, coroutines = true),
        ).generateServiceTypes()
        val types = registry.buildTypes()
        val compiled = compileTypesResult(types)
        expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val policies = types.filterKeys { it.simpleName.startsWith("OpenAPIOidcPolicy_") }.values
        expectThat(policies).isNotEmpty()
        policies.forEach { policy ->
          val suppression = ClassName("kotlin", "Suppress")
          expectThat(policy.annotations.count { it.typeName == suppression }).isEqualTo(1)
          val parameter = requireNotNull(policy.primaryConstructor).parameters.single { it.name == "oidc" }
          expectThat(parameter.annotations.count { it.typeName == suppression }).isEqualTo(1)
        }
      }
    }
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

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native startup validates effective client settings including contract endpoints`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    listOf(false, true).forEach { discovery ->
      val api = scopedSecurityApi(frontend, directory, clientQuarkus = "acquire", clientDiscovery = discovery)
      val registry = registry(GenerationMode.Client)
      KotlinJAXRSIrGenerator(api, registry, options(GenerationMode.Client)).generateServiceTypes()
      val compiled = compileTypesResult(registry.buildTypes())
      expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
      val factory =
        compiled.classLoader
          .loadClass("io.test.OpenAPIOidcConfiguration")
          .getConstructor()
          .newInstance() as ConfigSourceFactory
      val requirements =
        compiled.classLoader
          .loadClass("io.test.OpenAPIClientRequirements")
          .getConstructor(Config::class.java)
      val deployment =
        mapOf(
          "quarkus.oidc-client.service.client-id" to "application",
          "quarkus.oidc-client.service.credentials.secret" to "application-secret",
        )

      fun configuration(settings: Map<String, String>): Config =
        SmallRyeConfigBuilder()
          .withSources(
            object : ConfigSource {
              override fun getName() = "application"

              override fun getProperties() = settings

              override fun getPropertyNames() = settings.keys

              override fun getValue(name: String) = settings[name]

              override fun getOrdinal() = 250
            },
          ).withSources(factory)
          .build()

      fun validate(config: Config) {
        try {
          requirements.newInstance(config)
        } catch (failure: java.lang.reflect.InvocationTargetException) {
          throw failure.targetException
        }
      }
      val config = configuration(deployment)
      expectCatching { validate(config) }.isSuccess()
      val prefixes =
        config.propertyNames
          .filter {
            it.startsWith("quarkus.oidc-client.\"sunday-") &&
              it.endsWith("grant.type")
          }.map { it.removeSuffix("grant.type") }
      expectThat(prefixes).isNotEmpty()
      prefixes.forEach { prefix ->
        expectThat(config.getValue(prefix + "client-id", String::class.java)).isEqualTo("application")
        expectThat(config.getValue(prefix + "credentials.secret", String::class.java)).isEqualTo("application-secret")
        val endpoint = if (discovery) "auth-server-url" else "token-path"
        val expected = if (discovery) "https://identity.internal" else "https://identity.internal/token"
        expectThat(config.getValue(prefix + endpoint, String::class.java)).isEqualTo(expected)
        val overridden = configuration(deployment + (prefix + endpoint to "https://deployment.example/endpoint"))
        expectCatching { validate(overridden) }.isSuccess()
        expectThat(
          overridden.getValue(prefix + endpoint, String::class.java),
        ).isEqualTo("https://deployment.example/endpoint")
        expectCatching {
          validate(configuration(deployment + mapOf(prefix + "token-path" to "", prefix + "auth-server-url" to "")))
        }.isFailure().isA<IllegalArgumentException>()
        expectCatching {
          validate(configuration(deployment + (prefix + "client-enabled" to "false")))
        }.isFailure().isA<IllegalArgumentException>()
      }
      expectCatching { validate(configuration(emptyMap())) }.isFailure().isA<IllegalArgumentException>()
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `application metadata names and switches preserve compiled security bindings`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    for (mode in listOf(GenerationMode.Server, GenerationMode.Client)) {
      val api =
        scopedSecurityApi(
          frontend,
          directory,
          serverQuarkus = if (mode == GenerationMode.Server) "oidc" else null,
          clientQuarkus = if (mode == GenerationMode.Client) "acquire" else null,
        )
      for (selection in listOf("renamed", "disabled", "all-disabled")) {
        val metadata =
          KotlinApplicationMetadataOptions(
            enabled = selection != "all-disabled",
            serverConfiguration = selection != "disabled" || mode != GenerationMode.Server,
            clientConfiguration = selection != "disabled" || mode != GenerationMode.Client,
            serverConfigurationFileName = "ServerDefaults.kt",
            clientConfigurationFileName = "ClientDefaults.kt",
          )
        val registry = KotlinTypeRegistry("io.test", null, mode, emptySet(), applicationMetadata = metadata)
        KotlinJAXRSIrGenerator(api, registry, options(mode)).generateServiceTypes()
        val types = registry.buildTypes()
        val compiled = compileTypesResult(types)
        expectThat(compiled.exitCode).isEqualTo(KotlinCompilation.ExitCode.OK)
        val output = directory.resolve("$mode-$selection")
        registry.generateFiles(GeneratedTypeCategory.entries.toSet(), output)
        val name = if (mode == GenerationMode.Server) "ServerDefaults" else "ClientDefaults"
        val service =
          if (mode == GenerationMode.Server) {
            "org.eclipse.microprofile.config.spi.ConfigSource"
          } else {
            "io.smallrye.config.ConfigSourceFactory"
          }
        expectThat(Files.exists(output.resolve("io/test/$name.kt"))).isEqualTo(selection == "renamed")
        val descriptor = output.resolve("META-INF/services/$service")
        expectThat(Files.exists(descriptor)).isEqualTo(selection == "renamed")
        if (selection == "renamed") {
          expectThat(Files.readString(descriptor)).isEqualTo("io.test.$name\n")
          expectThat(compiled.classLoader.loadClass("io.test.$name").simpleName).isEqualTo(name)
        }
        expectThat(Files.exists(output.resolve("META-INF/beans.xml"))).isFalse()
        expectThat(
          types.keys.any {
            if (mode == GenerationMode.Server) {
              it.simpleName.startsWith("OpenAPIOidcPolicy_")
            } else {
              it.simpleName == "OpenAPIClientRequirements"
            }
          },
        ).isTrue()
      }
    }
  }

  @Test
  fun `application metadata filenames reject directories and non Kotlin basenames`() {
    for (name in listOf("../Defaults.kt", "/Defaults.kt", "defaults.json", "")) {
      expectCatching { KotlinApplicationMetadataOptions(serverConfigurationFileName = name) }
        .isFailure()
        .isA<IllegalArgumentException>()
    }
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

  private fun options(
    mode: GenerationMode,
    coroutines: Boolean = false,
  ) = KotlinJAXRSOptions(
    coroutineServiceMethods = coroutines,
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
