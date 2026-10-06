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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.aggregateClientConfigurationApi
import io.outfoxx.sunday.generator.tools.aggregateFrontendConfigurationApi
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
class PythonClientConfigurationTest : PythonTest() {
  @Test
  fun `generated factories preserve application token persistence`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val modules =
      PythonSundayIrGenerator(
        aggregateClientConfigurationApi(directory),
        PythonGeneratorOptions(
          packageName = "example_api",
          aggregateServices = true,
          aggregateServiceName = "ExampleAPI",
        ),
      ).generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
    val source =
      java.nio.file.Files
        .readString(Path.of("src/test/resources/client-config-persistence/python.py"))
    assertTrue(compileModules(compiler, modules, smokeCode = source))
  }

  @Test
  fun `credential field collisions fail before emitting source`(
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi("credential-collision", directory)
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        PythonSundayIrGenerator(api, PythonGeneratorOptions(packageName = "example_api"))
          .generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
      }
    assertTrue(error.message.orEmpty().contains("credential field name collision"))
  }

  @Test
  fun `unavailable security alternatives never become public`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("profiles.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.2.0
      info: {title: Example API, version: '1'}
      servers:
        - url: https://api.example
          x-sunday-security-profile: production
      paths:
        /production:
          get:
            operationId: production
            security: [{production: []}]
            responses: {'204': {description: Success}}
        /development:
          get:
            operationId: development
            security: [{development: []}]
            responses: {'204': {description: Success}}
      components:
        securitySchemes:
          production:
            type: http
            scheme: bearer
            x-sunday-security:
              profiles:
                production:
                  client: {provider: production, flow: static}
          development:
            type: http
            scheme: bearer
            x-sunday-security:
              profiles:
                development:
                  client: {provider: development, flow: static}
      """.trimIndent(),
    )
    val original = OpenApiToGeneratedApi().convert(source.toUri())
    val api = original.copy(services = original.services.map { it.copy(name = "Service", group = null) })
    val modules =
      PythonSundayIrGenerator(api, PythonGeneratorOptions(packageName = "example_api"))
        .generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
    val service = api.services.single()
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from example_api.config import ExampleAPIConfig
          from example_api.${service.pythonServiceModuleName} import create_${service.pythonServiceBaseName.pythonIdentifierName}, ${service.pythonServiceBaseName.pythonTypeName}Credentials
          from sunday import BearerCredentials

          def unexpected_transport(settings):
              raise AssertionError("Transport must not be constructed for an unavailable security profile")

          for profile in ("production", "development"):
              try:
                  create_${service.pythonServiceBaseName.pythonIdentifierName}(ExampleAPIConfig(), unexpected_transport,
                      credentials=${service.pythonServiceBaseName.pythonTypeName}Credentials(
                          production=BearerCredentials("prod"), development=BearerCredentials("dev")),
                      security_profile=profile)
              except ValueError:
                  pass
              else:
                  raise AssertionError("Unavailable protected operation was accepted as public")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "raml", "openapi", "asyncapi", "composed", "security", "multi",
      "alternatives", "server-security", "server-profile",
    ],
  )
  fun `configuration factory uses exactly one application transport and validates server variables`(
    frontend: String,
    compiler: PythonCompiler,
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
    val service = api.services.single()
    val factory = "create_${service.pythonServiceBaseName.pythonIdentifierName}"
    val modules =
      PythonSundayIrGenerator(api, PythonGeneratorOptions(packageName = "example_api"))
        .generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
    assertTrue(
      compileModules(
        compiler,
        modules,
        listOf("example_api.config", "example_api.${service.pythonServiceModuleName}"),
        smokeCode =
          """
          import asyncio
          import httpx
          from example_api.config import $configType
          from example_api.${service.pythonServiceModuleName} import $factory, ${service.pythonServiceBaseName.pythonTypeName}Credentials, ${service.pythonServiceBaseName.pythonTypeName}SecurityAlternative
          from sunday.httpx import HttpxTransport
          from sunday import BearerCredentials, ApiKeyCredentials

          async def run():
              config = $configType(tenant="secondary")
              assert config.base_url() == "https://secondary.example/v1"
              calls = []
              async with httpx.AsyncClient(base_url=config.base_url()) as native:
                  def transport_factory(settings):
                      calls.append(settings)
                      assert (settings.token_manager is not None) == ${if (authenticated
          ) {
            "True"
          } else {
            "False"
          }}
                      return HttpxTransport.from_settings(settings, native)
                  client = $factory(config, transport_factory${if (frontend in
            setOf(
              "alternatives",
              "server-profile",
            )
          ) {
            ", credentials=${service.pythonServiceBaseName.pythonTypeName}Credentials(identity=BearerCredentials(\"secret\"), access_key=ApiKeyCredentials(\"key\")), security_selection={\"listItems\": ${service.pythonServiceBaseName.pythonTypeName}SecurityAlternative.ACCESS_KEY_AND_IDENTITY}"
          } else if (frontend in setOf("security", "server-security", "server-profile")) {
            ", credentials=${service.pythonServiceBaseName.pythonTypeName}Credentials(identity=BearerCredentials(\"secret\"))"
          } else {
            ""
          }})
                  assert len(calls) == 1
                  assert client.transport.client is native
              try:
                  $configType(tenant="invalid")
                  raise AssertionError("Invalid variable accepted")
              except ValueError:
                  pass
          asyncio.run(run())
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `aggregate factory shares settings and lazy token cache`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val modules =
      PythonSundayIrGenerator(
        aggregateClientConfigurationApi(directory),
        PythonGeneratorOptions(
          packageName = "example_api",
          profile = "external",
          aggregateServices = true,
          aggregateServiceName = "ExampleAPI",
        ),
      ).generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import httpx
          from sunday import BearerCredentials, ProviderCredentials, TokenConfiguration, TokenSet
          from sunday.httpx import HttpxTransport
          from example_api.config import ExampleAPIDevelopmentConfig
          from example_api.api import create_example_api, ExampleAPICredentials, ExampleAPISecurityAlternative
          acquired = []
          requests = []
          class Provider:
              identity = "application"
              def configure(self, binding):
                  return TokenConfiguration("client", "session")
              async def acquire(self, request):
                  acquired.append(request)
                  return TokenSet("token")
          def handler(request):
              requests.append(request)
              return httpx.Response(204)
          async def run():
              for override in (False, True):
                  acquired.clear()
                  requests.clear()
                  calls = []
                  expected = "external" if override else "external-development"
                  async with httpx.AsyncClient(base_url="https://api.dev.example", transport=httpx.MockTransport(handler)) as native:
                      def factory(settings):
                          calls.append(settings)
                          assert settings.base_url == "https://api.dev.example"
                          assert set(settings.bindings) == {"listUsers", "listProjects", "register"}
                          assert not settings.bindings["register"]
                          for operation in ("listUsers", "listProjects"):
                              binding = settings.bindings[operation][0]
                              assert binding.profile == expected and binding.scopes == ("items:read",)
                          return HttpxTransport.from_settings(settings, native)
                      client = create_example_api(ExampleAPIDevelopmentConfig(), factory,
                          credentials=ExampleAPICredentials(identity=ProviderCredentials(Provider())),
                          security_selection={"listProjects": ExampleAPISecurityAlternative.IDENTITY},
                          **({"security_profile": "external"} if override else {}))
                      assert len(calls) == 1 and not acquired
                      assert client.users.transport is client.transport and client.projects.transport is client.transport
                      await client.users.register().execute()
                      assert not acquired and "authorization" not in requests[0].headers
                      await client.users.list_users().execute()
                      await client.projects.list_projects().execute()
                      assert len(acquired) == 1 and acquired[0].profile == expected
                      assert all(r.headers["authorization"].lower() == "bearer token" for r in requests[1:])
              def unexpected(settings):
                  raise AssertionError("Transport constructed before validation")
              for credentials in (
                  ExampleAPICredentials(),
                  ExampleAPICredentials(identity=ProviderCredentials(Provider()), backup_token=BearerCredentials("key")),
              ):
                  try:
                      create_example_api(ExampleAPIDevelopmentConfig(), unexpected, credentials=credentials)
                  except ValueError:
                      pass
                  else:
                      raise AssertionError("Invalid credentials accepted")
          asyncio.run(run())
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `aggregate factories compile for every frontend`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {

    val modules =
      PythonSundayIrGenerator(
        aggregateFrontendConfigurationApi(frontend, directory),
        PythonGeneratorOptions(
          packageName = "example_api",
          aggregateServices = true,
          aggregateServiceName = "ExampleAPI",
        ),
      ).generateModules(setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model))
    assertTrue(compileModules(compiler, modules, listOf("example_api.api")))
  }
}
