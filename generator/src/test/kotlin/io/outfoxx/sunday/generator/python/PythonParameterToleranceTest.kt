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
import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.parameterNameCollisionApi
import io.outfoxx.sunday.generator.tools.parameterToleranceApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
@Tag("requests")
@Tag("validation")
class PythonParameterToleranceTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `validation helper names do not shadow operation parameters`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val modules =
      PythonSundayIrGenerator(
        parameterNameCollisionApi(frontend, directory, listOf("validateParameters")),
        PythonGeneratorOptions(packageName = "client_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          from sunday import RequestEncodingError
          from sunday.httpx import HttpxTransport
          from client_api.parameters import ParametersClient
          from client_api.models import State

          async def verify():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  api = ParametersClient(transport)
                  await api.parameters().transport_request()
                  await api.parameters(validate_parameters=State("active")).transport_request()
                  try:
                      await api.parameters(validate_parameters=State("future")).transport_request()
                  except RequestEncodingError:
                      pass
                  else:
                      raise AssertionError("shadowed parameter bypassed validation")
          asyncio.run(verify())
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed", "openapi-all"])
  fun `typed parameters validate before client transmission and server invocation`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val tolerance = if (frontend.endsWith("-all")) Tolerance.All else Tolerance.Response
    val api = parameterToleranceApi(frontend.removeSuffix("-all"), directory, cookies = true)
    val categories = setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)
    val clients =
      PythonSundayIrGenerator(
        api,
        PythonGeneratorOptions(packageName = "client_api", defaultTolerance = tolerance),
      ).generateModules(categories)
    val servers =
      PythonLitestarIrGenerator(
        api,
        PythonGeneratorOptions(packageName = "server_api", defaultTolerance = tolerance),
      ).generateModules(categories)
    assertTrue(
      compileModules(
        compiler,
        clients + servers,
        smokeCode =
          """
          import asyncio
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday import RequestEncodingError
          from sunday.httpx import HttpxTransport
          from sunday.litestar import SundayPlugin
          from client_api.parameters import ParametersClient
          from client_api.models import State, OpenState, DefaultState
          from server_api.parameters_server import create_parameters_router
          from server_api.models import Item

          async def check_client():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  api = ParametersClient(transport)
                  values = [State("active")]
                  operation = api.parameters(State("active"), query_states=values, open_state=OpenState("future"))
                  await operation.transport_request()
                  values.append(State("future"))
                  try:
                      await operation.transport_request()
                  except RequestEncodingError:
                      pass
                  else:
                      raise AssertionError("mutation bypassed request validation")
                  for args in [
                      {"path_state": State("future")},
                      {"path_state": State("active"), "header_state": State("future")},
                      {"path_state": State("active"), "cookie_state": State("future")},

                  ]:
                      try:
                          await api.parameters(**args).transport_request()
                      except RequestEncodingError:
                          pass
                      else:
                          raise AssertionError("unknown parameter accepted")
                  default_operation = api.parameters(State("active"), default_state=DefaultState("future"))
                  try:
                      await default_operation.transport_request()
                  except RequestEncodingError:
                      assert ${if (tolerance == Tolerance.Response) "True" else "False"}, "default all must admit fallback"
                  else:
                      assert ${if (tolerance == Tolerance.All) "True" else "False"}, "default response must reject fallback"
          asyncio.run(check_client())

          class Delegate:
              calls = 0
              async def parameters(self, *args):
                  self.calls += 1
                  return Item.model_validate({"state": "active"})
          delegate = Delegate()
          app = Litestar(route_handlers=[create_parameters_router(delegate)], plugins=[SundayPlugin()])
          with TestClient(app) as client:
              for path, kwargs in [
                  ("future", {}),
                  ("active", {"params": {"queryStates": ["active", "future"]}}),
                  ("active", {"headers": {"headerState": "future"}}),
                  ("active", {"headers": {"Cookie": "cookieState=future"}}),
              ]:
                  response = client.get("/parameters/" + path, **kwargs)
                  assert response.status_code == 400, response.text
                  failure = response.json()
                  assert failure["detail"] == "Request parameter is invalid", failure
                  assert failure["extra"] and "loc" in failure["extra"][0], failure
                  assert all("input" not in error and "ctx" not in error for error in failure["extra"]), failure
                  assert delegate.calls == 0
              response = client.get("/parameters/active", params={"openState": "future"})
              assert response.status_code == 200, response.text
              assert delegate.calls == 1
              response = client.get("/parameters/active", params={"defaultState": "future"})
              assert response.status_code == ${if (tolerance == Tolerance.All) 200 else 400}, response.text
          """.trimIndent(),
      ),
    )
  }

  @Test
  @Tag("models")
  fun `whole-query models validate before client transmission and server invocation`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = parameterToleranceApi("openapi", directory)
    val api =
      source.copy(
        services =
          source.services.map { service ->
            service.copy(
              operations =
                service.operations.map { operation ->
                  operation.copy(
                    path = "/parameters",
                    parameters = emptyList(),
                    queryString = GeneratedTypeRef.named("Item"),
                  )
                },
            )
          },
      )
    val categories = setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)
    val clients =
      PythonSundayIrGenerator(
        api,
        PythonGeneratorOptions(packageName = "client_api"),
      ).generateModules(categories)
    val servers =
      PythonLitestarIrGenerator(
        api,
        PythonGeneratorOptions(packageName = "server_api"),
      ).generateModules(categories)
    assertTrue(
      compileModules(
        compiler,
        clients + servers,
        smokeCode =
          """
          import asyncio
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday import RequestEncodingError
          from sunday.httpx import HttpxTransport
          from sunday.litestar import SundayPlugin
          from client_api.parameters import ParametersClient
          from client_api.models import Item, State
          from server_api.parameters_server import create_parameters_router

          async def check_client():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  value = Item(state=State("active"))
                  operation = ParametersClient(transport).parameters(query_string=value)
                  await operation.transport_request()
                  value.state = State("future")
                  try:
                      await operation.transport_request()
                  except RequestEncodingError:
                      pass
                  else:
                      raise AssertionError("unknown query model accepted")
          asyncio.run(check_client())

          class Delegate:
              calls = 0
              async def parameters(self, query_string):
                  self.calls += 1
                  return query_string
          delegate = Delegate()
          with TestClient(Litestar(route_handlers=[create_parameters_router(delegate)], plugins=[SundayPlugin()])) as client:
              assert client.get("/parameters?state=future").status_code == 400
              assert delegate.calls == 0
              assert client.get("/parameters?state=active").status_code == 200
              assert delegate.calls == 1
          """.trimIndent(),
      ),
    )
  }
}
