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
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonSecuritySchemeTest : PythonTest() {

  @ParameterizedTest
  @ValueSource(strings = ["security-enforcement-3", "security-api-keys-2", "composed-security"])
  fun `AsyncAPI security compiles with and without enforcement`(
    fixture: String,
    compiler: PythonCompiler,
  ) {
    val paths =
      if (fixture == "composed-security") {
        listOf(
          "openapi/ir/security-enforcement.yaml",
          "asyncapi/ir/security-enforcement-3.yaml",
          "asyncapi/ir/security-api-keys-2.yaml",
        )
      } else {
        listOf("asyncapi/ir/$fixture.yaml")
      }
    listOf(false, true).forEach { enforce -> assertTrue(compileModules(compiler, modules(paths, enforce))) }
  }

  @Test
  fun `AsyncAPI generated endpoints enforce transports and server plus operation scopes`(compiler: PythonCompiler) {
    assertTrue(
      compileModules(
        compiler,
        modules(listOf("asyncapi/ir/security-enforcement-3.yaml", "asyncapi/ir/security-api-keys-2.yaml")),
        smokeCode =
          """
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from security_api._sunday_security import ApiSecurity, Identity
          from security_api.api_server import create_secure_router

          calls = []
          async def authenticate(connection, scheme, credential):
              if scheme.name.endswith("Key") and credential == "valid-key":
                  return Identity("alice")
              if scheme.name == "eventToken":
                  if credential == "reader":
                      return Identity("alice", frozenset({"read"}))
                  if credential == "unscoped":
                      return Identity("alice")
              if scheme.name.startswith("inline_") and credential == "valid-token":
                  return Identity("alice")
              return None

          class Publisher:
              def __getattr__(self, name):
                  async def operation(*args, **kwargs):
                      calls.append(name)
                      yield "event"
                  return operation

          security = ApiSecurity({name: authenticate for name in ApiSecurity.schemes})
          app = Litestar(route_handlers=[create_secure_router(Publisher(), Publisher(), security=security)], plugins=[SundayPlugin()])
          with TestClient(app) as client:
              def send(path, expected, headers=None):
                  response = client.get(path, headers=headers)
                  assert response.status_code == expected, (path, response.status_code, response.text)
              for route in ("header", "query", "cookie", "scoped", "bearer", "combined"):
                  send("/async3/" + route, 401)
              assert calls == []
              key = {"X-API-Key": "valid-key"}
              reader = {"Authorization": "Bearer reader"}
              send("/async3/header", 200, key)
              send("/async3/header", 401, {"X-API-Key": "invalid"})
              send("/async3/query?api_key=valid-key", 200)
              send("/async3/query?wrong_name=valid-key", 401)
              send("/async3/cookie", 200, {"Cookie": "session_key=valid-key"})
              send("/async3/bearer", 200, {"Authorization": "Bearer valid-token"})
              send("/async3/scoped", 200, reader)
              before = len(calls)
              send("/async3/scoped", 403, {"Authorization": "Bearer unscoped"})
              send("/async3/combined", 401, reader)
              send("/async3/combined", 401, key)
              assert len(calls) == before
              send("/async3/combined", 200, key | reader)
              send("/async2/keys?api_key=valid-key", 401, key)
              send("/async2/keys?api_key=valid-key", 200, key | {"Cookie": "session_key=valid-key"})
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `scheme aware rendering rejects incomplete policy maps`() {
    val api =
      GeneratedApiIrExporter(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .export(javaClass.getResource("/openapi/ir/security-enforcement.yaml")!!.toURI())
    assertThrows(GenerationException::class.java) {
      PythonLitestarRenderer("security_api").renderService(api.services.first(), securityPolicies = emptyMap())
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `scheme enforcement compiles for every frontend`(
    kind: String,
    compiler: PythonCompiler,
  ) {
    val paths =
      if (kind == "composed") {
        listOf("openapi/ir/security-enforcement.yaml", "asyncapi/ir/security-enforcement.yaml")
      } else {
        listOf("$kind/ir/security-enforcement." + if (kind == "raml") "raml" else "yaml")
      }
    assertTrue(compileModules(compiler, modules(paths)))
  }

  @Test
  fun `real requests enforce credential transports permissions and alternatives before delegation`(
    compiler: PythonCompiler,
  ) {
    assertTrue(
      compileModules(
        compiler,
        modules(listOf("openapi/ir/security-enforcement.yaml")),
        smokeCode =
          """
          from collections import Counter
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from security_api._sunday_security import ApiSecurity, Identity
          from security_api.api_server import create_secure_router

          validations = Counter()
          calls = []

          async def authenticate(connection, scheme, credential):
              validations[scheme.name] += 1
              if scheme.name == "basicAuth" and credential == "YWxpY2U6cGFzc3dvcmQ=":
                  return Identity("alice")
              if scheme.name == "bearerAuth" and credential == "valid-token":
                  return Identity("alice")
              if scheme.name in {"headerKey", "queryKey", "cookieKey"}:
                  if credential == "valid-key":
                      return Identity("alice")
                  if credential == "admin-key":
                      return Identity("alice", frozenset({"admin"}))
              if scheme.name in {"oauth", "oidc"}:
                  if credential == "reader-token":
                      return Identity("alice", frozenset({"read"}))
                  if credential == "super-token":
                      return Identity("alice", frozenset({"read", "admin"}))
                  if credential == "admin-token":
                      return Identity("alice", frozenset({"admin"}))
                  if credential == "unprivileged-token":
                      return Identity("alice")
              return None

          try:
              ApiSecurity({})
              raise AssertionError("Missing authenticators must fail at setup")
          except ValueError as error:
              assert "headerKey" in str(error), error

          security = ApiSecurity({name: authenticate for name in ApiSecurity.schemes})
          assert security.schemes["oauth"].oauth_flows["clientCredentials"].token_url == "https://issuer.example/token"
          assert security.schemes["oidc"].open_id_connect_url == "https://issuer.example/.well-known/openid-configuration"

          class Policy:
              def __getattr__(self, name):
                  async def operation():
                      calls.append(name)
                      return name
                  return operation

          class Health:
              async def health(self):
                  return None

          app = Litestar(
              route_handlers=[create_secure_router(Policy(), Health(), security=security)],
              plugins=[SundayPlugin()],
          )
          with TestClient(app) as client:
              for operation in ("basic", "bearer", "header", "query", "cookie", "scoped", "oidc", "combined", "tls"):
                  assert client.get("/strict/" + operation).status_code == 401, operation
              assert calls == [], calls
              assert validations == {}, validations

              for operation in ("anonymous", "optional"):
                  assert client.get("/strict/" + operation).status_code == 200
              assert client.get("/strict/health").status_code == 204

              basic = {"Authorization": "Basic YWxpY2U6cGFzc3dvcmQ="}
              bearer = {"Authorization": "Bearer valid-token"}
              reader = {"Authorization": "Bearer reader-token"}
              key = {"X-API-Key": "valid-key"}
              assert client.get("/strict/basic", headers=basic).status_code == 200
              assert client.get("/strict/bearer", headers=bearer).status_code == 200
              assert client.get("/strict/inherited", headers=bearer).status_code == 200
              assert client.get("/strict/bearer", headers=basic).status_code == 401
              assert client.get("/strict/basic", headers=bearer).status_code == 401
              assert client.get("/strict/header", headers=bearer).status_code == 401
              assert client.get("/strict/header", headers=key).status_code == 200
              assert client.get("/strict/query?api_key=valid-key").status_code == 200
              assert client.get("/strict/query?wrong_name=valid-key").status_code == 401
              assert client.get("/strict/cookie", headers={"Cookie": "session_key=valid-key"}).status_code == 200
              assert client.get("/strict/cookie", headers=key).status_code == 401
              assert client.get("/strict/scoped", headers=reader).status_code == 200
              assert client.get("/strict/allScopes", headers=reader).status_code == 403
              assert client.get("/strict/allScopes", headers={"Authorization": "Bearer super-token"}).status_code == 200
              assert client.get("/strict/oidc", headers=reader).status_code == 200
              denied = client.get("/strict/scoped", headers={"Authorization": "Bearer unprivileged-token"})
              assert denied.status_code == 403, denied.text
              assert "insufficient_scope" in denied.headers["WWW-Authenticate"]
              assert client.get("/strict/scoped", headers=bearer).status_code == 401
              assert client.get("/strict/combined", headers=reader).status_code == 401
              assert client.get("/strict/combined", headers=key).status_code == 401
              assert client.get("/strict/combined", headers=reader | key).status_code == 200
              assert client.get("/strict/alternative", headers=reader).status_code == 403
              assert client.get("/strict/alternative", headers=reader | key).status_code == 200
              assert client.get("/strict/alternative", headers=key).status_code == 200
              before = validations["oauth"]
              assert client.get("/strict/scopeAlternative", headers=reader).status_code == 200
              assert validations["oauth"] == before + 1
              assert client.get("/strict/role", headers=key).status_code == 403
              assert client.get("/strict/role", headers={"X-API-Key": "admin-key"}).status_code == 200
              assert client.get("/strict/query?api_key=valid-key&api_key=invalid").status_code == 401
              assert client.get("/strict/bearer", headers=[("Authorization", "Bearer valid-token"),
                                                        ("Authorization", "Bearer invalid")]).status_code == 401
              assert "Bearer" in client.get("/strict/bearer").headers["WWW-Authenticate"]
              assert "Basic" in client.get("/strict/basic").headers["WWW-Authenticate"]
              before = len(calls)
              assert client.get("/strict/scoped", headers={"Authorization": "Bearer unprivileged-token"}).status_code == 403
              assert len(calls) == before

          # HTTPS alone is not proof of a client certificate: the trusted validator must still accept the peer.
          with TestClient(app, base_url="https://testserver.local") as client:
              assert client.get("/strict/tls").status_code == 401
              assert validations["clientCertificate"] == 1
          """.trimIndent(),
      ),
    )
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
      PythonGeneratorOptions(
        packageName = "security_api",
        aggregateServices = true,
        aggregateServiceName = "Secure",
        enforceSecuritySchemes = enforce,
      ),
    ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
  }
}
