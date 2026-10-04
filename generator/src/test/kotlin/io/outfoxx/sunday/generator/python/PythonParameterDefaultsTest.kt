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
import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
@Tag("requests")
class PythonParameterDefaultsTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `client signatures preserve nullable and defaulted parameters`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = parameterDefaultsApi(frontend, directory, cookies = true)
    val modules =
      PythonSundayIrGenerator(
        api.withOptionalParameterTemplates(),
        PythonGeneratorOptions(packageName = "client_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)) +
        PythonLitestarIrGenerator(api, PythonGeneratorOptions(packageName = "server_api"))
          .generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          import inspect
          from typing import get_args, get_type_hints
          from client_api.parameters import ParametersClient
          from sunday.httpx import HttpxTransport
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from server_api.parameters_server import ParametersService, create_parameters_router

          type_parameters = {value.__name__: value for value in ParametersClient.__type_params__}
          hints = get_type_hints(ParametersClient.probe, localns=type_parameters)
          signature = inspect.signature(ParametersClient.probe)
          for name in ["path_value", "query_value", "nullable_value", "optional_value", "zero_value", "false_value", "header_value"]:
              assert type(None) in get_args(hints[name]), (name, hints[name])
              assert signature.parameters[name].default is not inspect.Parameter.empty, name
          if "cookie_value" in hints:
              assert type(None) in get_args(hints["cookie_value"])
          async def check_requests():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  api = ParametersClient(transport)
                  names = [name for name in hints if name != "return"]
                  omitted = await api.probe(**dict.fromkeys(names)).transport_request()
                  assert str(omitted.url) == "https://example.com/probe", omitted.url
                  assert "headerValue" not in omitted.headers
                  assert "cookie" not in omitted.headers
                  scalars = await api.scalar_defaults().transport_request()
                  assert scalars.url.path == "/scalar-defaults/active"
                  assert scalars.url.params["uriValue"] == "https://example.com/default"
                  defaults = await api.probe().transport_request()
                  assert defaults.url.path == "/probe/fallback"
                  assert defaults.url.params["queryValue"] == "5"
                  assert defaults.url.params["zeroValue"] == "0"
                  assert defaults.url.params["falseValue"] == "false"
                  assert defaults.headers["headerValue"] == "header"
                  explicit = await api.probe(path_value="explicit", query_value=7, header_value="custom").transport_request()
                  assert explicit.url.path == "/probe/explicit"
                  assert explicit.url.params["queryValue"] == "7"
                  assert explicit.headers["headerValue"] == "custom"
          asyncio.run(check_requests())

          captured = {}
          class Delegate:
              async def probe(self, *args):
                  captured.update(inspect.signature(ParametersService.probe).bind(self, *args).arguments)
              async def scalar_defaults(self, *args):
                  pass
              async def required(self, *args):
                  pass
          app = Litestar(route_handlers=[create_parameters_router(Delegate())], plugins=[SundayPlugin()])
          with TestClient(app) as client:
              response = client.get("/probe/explicit", params={"nullableValue": "present"})
              assert response.status_code == 204, response.text
          assert captured["query_value"] == 5
          assert captured["zero_value"] == 0
          assert captured["false_value"] is False
          assert captured["header_value"] == "header"
          if "cookie_value" in captured:
              assert captured["cookie_value"] == "cookie"
          required = inspect.signature(ParametersClient.required)
          required_hints = get_type_hints(ParametersClient.required, localns=type_parameters)
          for name in ["path_value", "query_value"]:
              assert type(None) not in get_args(required_hints[name])
              assert required.parameters[name].default is inspect.Parameter.empty
          """.trimIndent(),
      ),
    )
  }
}
