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

@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.outfoxx.sunday.generator.kotlin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.tschuchort.compiletesting.KotlinCompilation
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.kotlin.tools.compileTypesResult
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.tools.aggregateClientConfigurationApi
import io.outfoxx.sunday.generator.tools.aggregateFrontendConfigurationApi
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@KotlinTest
class KotlinClientConfigurationTest {
  @Test
  fun `credential field collisions fail before emitting source`(
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi("credential-collision", directory)
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
        KotlinSundayIrGenerator(
          api,
          registry,
          KotlinSundayOptions("example", "https://example.com/", listOf("application/json"), "API"),
        ).generateServiceTypes()
      }
    assertTrue(error.message.orEmpty().contains("credential field name collision"))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "raml", "openapi", "asyncapi", "composed", "security", "multi",
      "alternatives", "server-security", "server-profile",
    ],
  )
  fun `configuration factory compiles with generic transport`(
    frontend: String,
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi(frontend, directory)
    val authenticated = frontend in setOf("security", "alternatives", "server-security", "server-profile")
    val configType =
      if (frontend in
        setOf("multi", "server-security")
      ) {
        "ExampleAPIProductionConfig"
      } else {
        "ExampleAPIConfig"
      }
    val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions("example", "https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val consumer =
      TypeSpec
        .objectBuilder("ConstructionCheck")
        .addFunction(
          FunSpec
            .builder("verify")
            .addAnnotation(JvmStatic::class)
            .addCode(
              """
              var calls = 0
              val config = $configType(tenant = "secondary")
              val transport = io.outfoxx.sunday.jdk.JdkTransport(io.outfoxx.sunday.URITemplate(config.baseURL().toString()), io.outfoxx.sunday.problems.SundayHttpProblem.Factory)
              try {
                val client: API<io.outfoxx.sunday.jdk.JdkRequest> = createAPI(config, { settings ->
                  calls++
                  check((settings.tokenManager != null) == ${authenticated
              })
                  check(settings.baseURL.toString() == "https://secondary.example/v1")
                  transport
                }${if (frontend in setOf("alternatives", "server-profile")) {
                ", credentials = APICredentials(identity = io.outfoxx.sunday.security.BearerCredentials(\"secret\"), accessKey = io.outfoxx.sunday.security.ApiKeyCredentials(\"key\")), securitySelection = mapOf(\"listItems\" to APISecurityAlternative.AccessKeyAndIdentity)"
              } else if (frontend in setOf("security", "server-security", "server-profile")) {
                ", credentials = APICredentials(identity = io.outfoxx.sunday.security.BearerCredentials(\"secret\"))"
              } else {
                ""
              }})
                check(calls == 1)
                check(client.transport === transport)
                val direct = API(transport)
                check(direct.transport === transport)
              } finally {
                transport.close()
              }
              """.trimIndent(),
            ).build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (ClassName("example", "ConstructionCheck") to consumer))
    assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK)
    result.classLoader
      .loadClass("example.ConstructionCheck")
      .getMethod("verify")
      .invoke(null)
  }

  @Test
  fun `aggregate factory resolves all children before sharing one transport`(
    @TempDir directory: Path,
  ) {
    val api = aggregateClientConfigurationApi(directory)
    val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      api,
      registry,
      KotlinSundayOptions(
        "example",
        "https://example.com/",
        listOf("application/json"),
        "API",
        profile = "external",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    val consumer =
      TypeSpec
        .objectBuilder("AggregateCheck")
        .addFunction(
          FunSpec
            .builder("verify")
            .addAnnotation(JvmStatic::class)
            .addCode(
              """
              val acquired = mutableListOf<io.outfoxx.sunday.security.TokenRequest>()
              val provider = object : io.outfoxx.sunday.security.TokenProvider {
                override val identity = "application"
                override fun configure(binding: io.outfoxx.sunday.security.SecurityBinding) = io.outfoxx.sunday.security.TokenConfiguration("client", "session")
                override suspend fun acquire(request: io.outfoxx.sunday.security.TokenRequest): io.outfoxx.sunday.security.TokenSet {
                  acquired += request
                  return io.outfoxx.sunday.security.TokenSet("token")
                }
              }
              val credentials = ExampleAPICredentials(identity = io.outfoxx.sunday.security.ProviderCredentials(provider))
              for (override in listOf(false, true)) {
                acquired.clear()
                var calls = 0
                val expected = if (override) "external" else "external-development"
                val factory: (io.outfoxx.sunday.security.ClientSettings) -> io.outfoxx.sunday.jdk.JdkTransport = { settings ->
                  calls++
                  check(settings.baseURL.toString() == "https://api.dev.example")
                  check(settings.bindings.keys == setOf("listUsers", "listProjects", "register"))
                  check(settings.bindings.getValue("register").isEmpty())
                  for (operation in listOf("listUsers", "listProjects")) {
                    val binding = settings.bindings.getValue(operation).single()
                    check(binding.profile == expected)
                    check(binding.scopes == setOf("items:read"))
                  }
                  io.outfoxx.sunday.jdk.JdkTransport(io.outfoxx.sunday.URITemplate(settings.baseURL.toString()), io.outfoxx.sunday.problems.SundayHttpProblem.Factory, tokenManager = settings.tokenManager)
                }
                val client = if (override) createExampleAPI(ExampleAPIDevelopmentConfig(), factory, credentials, securityProfile = "external")
                             else createExampleAPI(ExampleAPIDevelopmentConfig(), factory, credentials,
                               securitySelection = mapOf("listProjects" to ExampleAPISecurityAlternative.Identity))
                client.transport.use { transport ->
                  check(calls == 1 && acquired.isEmpty())
                  check(client.users.transport === transport && client.projects.transport === transport)
                  kotlinx.coroutines.runBlocking {
                    val publicRequest = client.users.register().transportRequest()
                    check(publicRequest.headers.none { it.first.equals("Authorization", true) })
                    check(acquired.isEmpty())
                    client.users.listUsers().transportRequest()
                    client.projects.listProjects().transportRequest()
                  }
                  check(acquired.size == 1)
                  check(acquired.single().binding.profile == expected)
                }
              }
              for (invalidCredentials in listOf(
                ExampleAPICredentials(),
                ExampleAPICredentials(identity = credentials.identity, backupToken = io.outfoxx.sunday.security.BearerCredentials("key")),
              )) {
              var invalidCalls = 0
              try {
                createExampleAPI<io.outfoxx.sunday.jdk.JdkRequest>(ExampleAPIDevelopmentConfig(), { _ ->
                  invalidCalls++
                  error("UnexpectedTransport")
                }, invalidCredentials)
                error("Missing credentials accepted")
              } catch (_: IllegalArgumentException) { check(invalidCalls == 0) }
              }
              """.trimIndent(),
            ).build(),
        ).build()
    val result = compileTypesResult(registry.buildTypes() + (ClassName("example", "AggregateCheck") to consumer))
    assertTrue(result.exitCode == KotlinCompilation.ExitCode.OK)
    result.classLoader
      .loadClass("example.AggregateCheck")
      .getMethod("verify")
      .invoke(null)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `aggregate factories compile for every frontend`(
    frontend: String,
    @TempDir directory: Path,
  ) {

    val registry = KotlinTypeRegistry("example", null, GenerationMode.Client, setOf(), KotlinProblemLibrary.SUNDAY)
    KotlinSundayIrGenerator(
      aggregateFrontendConfigurationApi(frontend, directory),
      registry,
      KotlinSundayOptions(
        "example",
        "https://example.com/",
        listOf("application/json"),
        "API",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    assertTrue(compileTypesResult(registry.buildTypes()).exitCode == KotlinCompilation.ExitCode.OK)
  }
}
