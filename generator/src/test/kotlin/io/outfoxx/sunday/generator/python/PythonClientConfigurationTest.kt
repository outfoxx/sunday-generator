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
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
class PythonClientConfigurationTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(
    strings = ["raml", "openapi", "asyncapi", "composed", "security", "multi", "alternatives", "server-security"],
  )
  fun `configuration factory uses exactly one application transport and validates server variables`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi(frontend, directory)
    val authenticated = frontend in setOf("security", "alternatives", "server-security")
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
                  client = $factory(config, transport_factory${if (frontend == "alternatives") {
            ", credentials=${service.pythonServiceBaseName.pythonTypeName}Credentials(identity=BearerCredentials(\"secret\"), access_key=ApiKeyCredentials(\"key\")), security_selection={\"listItems\": ${service.pythonServiceBaseName.pythonTypeName}SecurityAlternative.ACCESS_KEY_AND_IDENTITY}"
          } else if (frontend in setOf("security", "server-security")) {
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
}
