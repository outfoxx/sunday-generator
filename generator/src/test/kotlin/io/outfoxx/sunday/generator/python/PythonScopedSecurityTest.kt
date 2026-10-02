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
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("security")
class PythonScopedSecurityTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `compiled clients pass selected bindings to the shared runtime`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operations =
      source.services.flatMap { service ->
        service.operations.map { it.copy(auth = source.effectiveAuth(service, it)) }
      }
    val api = source.copy(services = listOf(GeneratedService("SecurityService", operations = operations)))
    val modules =
      PythonSundayIrGenerator(api, PythonGeneratorOptions(packageName = "security_api", profile = "external"))
        .generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    val calls =
      operations.joinToString("\n") {
        if (it.streaming != null) {
          "    async for ignored in client.${it.id.pythonIdentifierName}():\n        pass"
        } else {
          "    await client.${it.id.pythonIdentifierName}().execute()"
        }
      }
    assertTrue(
      compileModules(
        compiler,
        modules,
        listOf("security_api.security"),
        smokeCode =
          """
          import asyncio
          import httpx
          from sunday import TokenConfiguration, TokenManager, TokenSet, EventStreamOptions
          from sunday.httpx import HttpxTransport
          from security_api.security import SecurityClient

          acquired = []
          requests = []
          class Provider:
              identity = "application"
              def configure(self, binding):
                  return TokenConfiguration("public-client", "fresh-session")
              async def acquire(self, request):
                  acquired.append(request)
                  return TokenSet("external-token")
          def handler(request):
              requests.append(request)
              assert request.headers["authorization"].lower() == "bearer external-token"
              return httpx.Response(204)
          async def main():
              async with httpx.AsyncClient(base_url="https://api.example", transport=httpx.MockTransport(handler)) as native:
                  transport = HttpxTransport(native, token_manager=TokenManager({"application": Provider()}))
                  client = SecurityClient(transport)
          GENERATED_CALLS
              assert len(requests) == ${operations.size}
              assert len(acquired) == 1
              binding = acquired[0]
              assert binding.provider == "application"
              assert binding.profile == "external" and binding.flow == "authorizationCode"
              assert binding.token_url == "https://identity.example/token"
              assert binding.authorization_url == "https://identity.example/authorize"
              assert binding.scopes == ("items:read",)
          asyncio.run(main())
          """.trimIndent().replace("GENERATED_CALLS", calls.prependIndent("    ")),
      ),
    )
  }
}
