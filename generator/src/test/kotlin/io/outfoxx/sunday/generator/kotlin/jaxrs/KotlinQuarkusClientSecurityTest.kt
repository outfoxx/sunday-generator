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
import io.outfoxx.sunday.client.quarkus.ClientAuthentication
import io.outfoxx.sunday.client.quarkus.ClientInvocation
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedAuth
import io.outfoxx.sunday.generator.ir.GeneratedExceptionRef
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPolicy
import io.outfoxx.sunday.generator.ir.GeneratedPolicySetting
import io.outfoxx.sunday.generator.ir.GeneratedPolicyValues
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSIrGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSOptions
import io.outfoxx.sunday.generator.kotlin.KotlinTest
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.smallrye.config.ConfigSourceFactory
import io.smallrye.config.SmallRyeConfigBuilder
import org.eclipse.microprofile.config.spi.ConfigSource
import org.eclipse.microprofile.faulttolerance.CircuitBreaker
import org.eclipse.microprofile.faulttolerance.Retry
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
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

@KotlinTest
@OptIn(ExperimentalCompilerApi::class)
@Tag("security")
class KotlinQuarkusClientSecurityTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `native client bindings compile and use selected endpoints with application configuration`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val input = scopedSecurityApi(frontend, directory, endpointBindings = true)
    val first = input.services.first()
    val api =
      input.copy(
        services =
          input.services.map { service ->
            if (service != first) {
              service
            } else {
              service.copy(
                operations =
                  service.operations +
                    service.operations.first().copy(
                      id = "publicOperation",
                      path = "/public",
                      method = "GET",
                      streaming = null,
                      auth = GeneratedAuth(securityOverride = true),
                    ),
              )
            }
          },
      )
    val registry = registry()
    KotlinJAXRSIrGenerator(api, registry, options("internal")).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val classes =
      types.keys
        .filter {
          it.enclosingClassName() == null
        }.map { compiled.classLoader.loadClass(it.canonicalName) }
    val interfaces = classes.filter { it.isInterface }
    assertTrue(interfaces.all { it.getAnnotation(ClientAuthentication::class.java) == null })
    val operations = interfaces.flatMap { it.declaredMethods.toList() }
    assertNull(operations.single { it.name == "publicOperation" }.getAnnotation(ClientAuthentication::class.java))
    val names = operations.mapNotNull { it.getAnnotation(ClientAuthentication::class.java)?.value }.distinct()
    assertEquals(if (frontend == "composed") 2 else 1, names.size)
    val factory =
      compiled.classLoader
        .loadClass(
          "io.test.OpenAPIOidcConfiguration",
        ).getConstructor()
        .newInstance() as ConfigSourceFactory
    val provider =
      object : ConfigSource {
        override fun getName() = "application"

        override fun getProperties() =
          listOf("apiProvider", "eventsProvider")
            .flatMap { provider ->
              mapOf(
                "quarkus.oidc-client.$provider.client-id" to "app-client",
                "quarkus.oidc-client.$provider.credentials.secret" to "application-secret",
                "quarkus.oidc-client.$provider.token-path" to "https://deployment.example/token",
                "quarkus.oidc.token.issuer" to "https://trusted.example/issuer",
              ).entries.map { it.toPair() }
            }.toMap()

        override fun getPropertyNames() = properties.keys

        override fun getValue(name: String) = properties[name]

        override fun getOrdinal() = 250
      }
    val defaults = SmallRyeConfigBuilder().withSources(factory).build()
    val configured = SmallRyeConfigBuilder().withSources(provider).withSources(factory).build()
    names.forEach { name ->
      val prefix = "quarkus.oidc-client.\"$name\"."
      assertEquals("https://identity.internal/token", defaults.getValue(prefix + "token-path", String::class.java))
      assertEquals("client", configured.getValue(prefix + "grant.type", String::class.java))
      assertEquals("items:read", configured.getValue(prefix + "scopes", String::class.java))
      assertEquals("false", configured.getValue(prefix + "early-tokens-acquisition", String::class.java))
      assertEquals("app-client", configured.getValue(prefix + "client-id", String::class.java))
      assertEquals("application-secret", configured.getValue(prefix + "credentials.secret", String::class.java))
      assertEquals("https://deployment.example/token", configured.getValue(prefix + "token-path", String::class.java))
      assertEquals(
        "https://trusted.example/issuer",
        configured.getValue("quarkus.oidc.token.issuer", String::class.java),
      )
    }
    val output = directory.resolve("generated")
    registry.generateFiles(setOf(GeneratedTypeCategory.Service), output)
    assertEquals(
      "io.test.OpenAPIOidcConfiguration\n",
      Files.readString(output.resolve("META-INF/services/io.smallrye.config.ConfigSourceFactory")),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["sync", "suspend", "uni"])
  fun `authenticated wrappers preserve typed retry and concrete breaker classification`(
    style: String,
    @TempDir directory: Path,
  ) {
    val input = scopedSecurityApi("openapi", directory, endpointBindings = true)
    val policy =
      GeneratedPolicy(
        client =
          GeneratedPolicyValues(
            retry =
              GeneratedPolicySetting(
                value =
                  GeneratedPolicyValues.Retry(
                    maxRetries = 3,
                    retryOn = listOf(GeneratedExceptionRef(className = IOException::class.java.name)),
                    abortOn = listOf(GeneratedExceptionRef(className = IllegalArgumentException::class.java.name)),
                  ),
              ),
            circuitBreaker =
              GeneratedPolicySetting(
                value =
                  GeneratedPolicyValues.CircuitBreaker(
                    skipOn = listOf(GeneratedExceptionRef(className = IllegalArgumentException::class.java.name)),
                  ),
              ),
          ),
      )
    val api =
      input.copy(
        services =
          input.services.map { service ->
            service.copy(operations = service.operations.map { it.copy(policy = policy) })
          },
      )
    val registry = registry()
    KotlinJAXRSIrGenerator(
      api,
      registry,
      options("internal", style),
    ).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val methods =
      types.keys.filter { it.enclosingClassName() == null }.flatMap {
        compiled.classLoader
          .loadClass(it.canonicalName)
          .declaredMethods
          .filterNot { method -> Modifier.isStatic(method.modifiers) }
      }
    val retries = methods.filter { it.getAnnotation(Retry::class.java) != null }
    assertEquals(1, retries.size)
    val retry = retries.single().getAnnotation(Retry::class.java)
    assertEquals(3, retry.maxRetries)
    assertEquals(listOf(IOException::class), retry.retryOn.toList())
    assertEquals(
      listOf(IllegalArgumentException::class, ClientInvocation.Stopped::class, CancellationException::class),
      retry.abortOn.toList(),
    )
    assertNull(retries.single().getAnnotation(CircuitBreaker::class.java))
    val transport = methods.single { it.getAnnotation(ClientAuthentication::class.java) != null }
    assertNull(transport.getAnnotation(Retry::class.java))
    assertEquals(
      listOf(IllegalArgumentException::class),
      transport.getAnnotation(CircuitBreaker::class.java).skipOn.toList(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["sync", "suspend", "uni"])
  fun `method defaults preserve authentication provider registration`(
    style: String,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi("openapi", directory)
    val service = source.services.first()
    val operation =
      service.operations.first().copy(
        path = "/defaults/{segment}",
        parameters =
          listOf(
            GeneratedParameter(
              name = "segment",
              location = GeneratedParameter.Location.PATH,
              type = GeneratedTypeRef.scalar("string"),
              required = true,
              defaultValue = "fallback",
            ),
          ),
      )
    val api = source.copy(services = listOf(service.copy(operations = listOf(operation))))
    val registry = registry()
    KotlinJAXRSIrGenerator(api, registry, options("internal", style)).generateServiceTypes()
    val types = registry.buildTypes()
    val compiled = compileTypesResult(types)
    assertEquals(KotlinCompilation.ExitCode.OK, compiled.exitCode, compiled.messages)
    val apiType =
      types.keys
        .filter { it.enclosingClassName() == null }
        .map { compiled.classLoader.loadClass(it.canonicalName) }
        .single { it.isInterface && it.declaredMethods.any { method -> method.name == operation.id } }
    val providers =
      apiType.getAnnotationsByType(
        org.eclipse.microprofile.rest.client.annotation.RegisterProvider::class.java,
      )
    assertTrue(providers.any { it.value.java.name == "io.outfoxx.sunday.client.quarkus.ClientAuthenticationFilter" })
    val transport = apiType.methods.single { it.getAnnotation(ClientAuthentication::class.java) != null }
    assertEquals(operation.id + "Transport", transport.name)
    assertTrue(apiType.methods.single { it.name == operation.id }.isDefault)
  }

  @Test
  fun `unsupported native flows and transports are diagnosed`(
    @TempDir directory: Path,
  ) {
    val api = scopedSecurityApi("openapi", directory)
    val error =
      assertThrows(GenerationException::class.java) {
        KotlinJAXRSIrGenerator(api, registry(), options("external")).generateServiceTypes()
      }
    assertTrue(error.message!!.contains("supports clientCredentials"))
  }

  private fun registry() = KotlinTypeRegistry("io.test", null, GenerationMode.Client, emptySet())

  private fun options(
    profile: String,
    style: String = "sync",
  ) = KotlinJAXRSOptions(
    coroutineServiceMethods = style == "suspend",
    coroutineFlowMethods = false,
    reactiveResponseType = if (style == "uni") "io.smallrye.mutiny.Uni" else null,
    explicitSecurityParameters = false,
    baseUriMode = null,
    alwaysUseResponseReturn = false,
    defaultServicePackageName = "io.test",
    defaultProblemBaseUri = "https://example.com/problems/",
    defaultMediaTypes = listOf("application/json"),
    serviceSuffix = "API",
    quarkus = true,
    profile = profile,
  )
}
