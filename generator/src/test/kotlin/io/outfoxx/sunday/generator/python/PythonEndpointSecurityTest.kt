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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonEndpointSecurityTest : PythonTest() {

  @Test
  fun `public and protected routes enforce security before invoking application delegates`(compiler: PythonCompiler) {
    val modules = modules(listOf("openapi/ir/server-adapters.yaml"))
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          from litestar import Litestar
          from litestar.exceptions import NotAuthorizedException
          from litestar.middleware import DefineMiddleware
          from litestar.middleware.authentication import AbstractAuthenticationMiddleware, AuthenticationResult
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from security_api.users_server import create_users_router
          from security_api.health_server import create_health_router

          calls = []

          class Users:
              async def get_user(self, user_id: str) -> str:
                  calls.append(user_id)
                  return user_id

              async def create_user(self) -> str:
                  calls.append("created")
                  return "created"

              async def optional_user(self) -> str:
                  return "optional"

              async def mixed_user(self) -> str:
                  return "mixed"

          class Health:
              async def health(self) -> None:
                  return None

          class Authentication(AbstractAuthenticationMiddleware):
              async def authenticate_request(self, connection):
                  if connection.headers.get("Authorization") == "Bearer test-token":
                      return AuthenticationResult(user="alice", auth="test-token")
                  raise NotAuthorizedException()

          def app(middleware):
              return Litestar(
                  route_handlers=[create_users_router(Users()), create_health_router(Health())],
                  middleware=middleware,
                  plugins=[SundayPlugin()],
              )

          with TestClient(app([DefineMiddleware(Authentication)])) as client:
              response = client.post("/users")
              assert response.status_code == 201, response.text
              assert response.text == "created", response.text
              assert client.get("/users/optional").status_code == 200
              assert client.get("/users/mixed").status_code == 200
              assert client.get("/health").status_code == 204
              assert client.get("/users/private").status_code == 401
              assert calls == ["created"], calls
              response = client.get("/users/private", headers={"Authorization": "Bearer test-token"})
              assert response.status_code == 200, response.text
              assert response.text == "private", response.text
              assert calls == ["created", "private"], calls

          with TestClient(app([])) as client:
              assert client.get("/users/private").status_code == 401
              assert client.post("/users").status_code == 201
          """.trimIndent(),
      ),
    )
    assertPythonSnapshot(
      "PythonEndpointSecurityTest/users_server.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "security_api/users_server.py"),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `endpoint security supports every source frontend`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val paths =
      when (kind) {
        "raml" -> listOf("raml/ir/craft-project.raml")
        "openapi" -> listOf("openapi/ir/server-adapters.yaml")
        "asyncapi" -> listOf("asyncapi/ir/server-adapters.yaml")
        else -> listOf("openapi/ir/server-adapters.yaml", "asyncapi/ir/server-adapters.yaml")
      }
    assertTrue(compileModules(compiler, modules(paths)))
  }

  @Test
  fun `endpoint security is opt in`(compiler: PythonCompiler) {
    assertTrue(compileModules(compiler, modules(listOf("openapi/ir/server-adapters.yaml"), enforce = false)))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "security_api/users_server.py")
    assertFalse(source.contains("_require_authenticated"))
    assertFalse(source.contains("exclude_from_auth"))
  }

  private fun modules(
    paths: List<String>,
    enforce: Boolean = true,
  ): List<PythonModule> {
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(paths.map { javaClass.getResource("/$it")!!.toURI() })
    return PythonLitestarIrGenerator(
      api,
      PythonGeneratorOptions(packageName = "security_api", aggregateServices = true, enforceEndpointSecurity = enforce),
    ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
  }
}
