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
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
@Tag("requests")
class PythonParameterDefaultsTest : PythonTest() {
  @Test
  fun `nested collection and model defaults are isolated without copying explicit values`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel("Labels", GeneratedModel.Kind.ARRAY, aliases = listOf(GeneratedTypeRef.scalar("string"))),
        GeneratedModel("Groups", GeneratedModel.Kind.MAP, aliases = listOf(GeneratedTypeRef.named("Labels"))),
        GeneratedModel(
          "Filter",
          GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("labels", GeneratedTypeRef.named("Labels"), required = true)),
        ),
      )
    val service =
      GeneratedService(
        "Defaults",
        operations =
          listOf(
            GeneratedOperation(
              "defaults",
              "GET",
              "/defaults",
              parameters =
                listOf(
                  GeneratedParameter(
                    "groups",
                    GeneratedParameter.Location.QUERY,
                    GeneratedTypeRef.named("Groups"),
                    defaultValue = mapOf("labels" to listOf("a")),
                  ),
                  GeneratedParameter(
                    "filter",
                    GeneratedParameter.Location.QUERY,
                    GeneratedTypeRef.named("Filter"),
                    defaultValue = mapOf("labels" to listOf("a")),
                  ),
                  GeneratedParameter(
                    "deepcopy",
                    GeneratedParameter.Location.QUERY,
                    GeneratedTypeRef.scalar("any"),
                    defaultValue = mapOf("labels" to listOf("a")),
                  ),
                ),
            ),
          ),
      )
    val modules =
      listOf(
        PythonModuleBuilder("client_api/__init__.py").build(),
        PythonModelRenderer("client_api").renderModels(models),
        PythonClientRenderer("client_api", models = models).renderService(service),
      )
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import asyncio
          from client_api.defaults import DefaultsClient
          from client_api.models import Filter
          from sunday.httpx import HttpxTransport

          def parameters(operation):
              return {parameter.name: parameter.value for parameter in operation.spec.request.parameters}

          async def verify():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  api = DefaultsClient(transport)
                  first = api.defaults()
                  pending = api.defaults()
                  first_values = parameters(first)
                  first_values["groups"]["labels"].append("mutated")
                  first_values["filter"].labels.append("mutated")
                  first_values["deepcopy"]["labels"].append("mutated")
                  for operation in [pending, api.defaults()]:
                      values = parameters(operation)
                      assert values["groups"] == {"labels": ["a"]}, values
                      assert values["filter"].labels == ["a"], values
                      assert values["deepcopy"] == {"labels": ["a"]}, values
                      operation.spec.request.parameter_validation()
                  supplied = {"groups": {"labels": ["a"]}, "filter": Filter(labels=["a"]), "deepcopy": {"labels": ["a"]}}
                  supplied_values = parameters(api.defaults(**supplied))
                  assert all(supplied_values[name] is supplied[name] for name in supplied)
                  omitted = api.defaults(groups=None, filter=None, deepcopy=None)
                  assert all(value is None for value in parameters(omitted).values())
                  assert not (await omitted.transport_request()).url.params
          asyncio.run(verify())
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `client signatures preserve nullable and defaulted parameters`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = parameterDefaultsApi(frontend, directory, cookies = true, collections = true)
    val modules =
      PythonSundayIrGenerator(
        api.withOptionalParameterTemplates(),
        PythonGeneratorOptions(packageName = "client_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)) +
        PythonLitestarIrGenerator(api, PythonGeneratorOptions(packageName = "server_api"))
          .generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service)) +
        PythonModuleBuilder("client_api/literals.py")
          .addCode(
            PythonCodeBlock.of(
              "defaults = %C",
              mapOf(
                "a" to listOf(null, false, 0, "text"),
                "b" to emptyMap<String, Any>(),
              ).pythonValueCode(),
            ),
          ).build()
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
          from client_api.literals import defaults as literal_defaults
          from sunday import RequestEncodingError
          from sunday.httpx import HttpxTransport
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from server_api.parameters_server import ParametersService, create_parameters_router

          assert literal_defaults == {"a": [None, False, 0, "text"], "b": {}}
          type_parameters = {value.__name__: value for value in ParametersClient.__type_params__}
          hints = get_type_hints(ParametersClient.probe, localns=type_parameters)
          signature = inspect.signature(ParametersClient.probe)
          for name in ["path_value", "query_value", "nullable_value", "optional_value", "zero_value", "false_value", "header_value"]:
              assert type(None) in get_args(hints[name]), (name, hints[name])
              assert signature.parameters[name].default is not inspect.Parameter.empty, name
          if "cookie_value" in hints:
              assert type(None) in get_args(hints["cookie_value"])
          collection_defaults = inspect.signature(ParametersClient.collections).parameters
          assert collection_defaults["tags"].default == ["a", "b"]
          assert collection_defaults["counts"].default == {"a": 0, "b": 2}
          assert collection_defaults["empty"].default == []
          async def check_requests():
              async with HttpxTransport(base_url="https://example.com") as transport:
                  api = ParametersClient(transport)
                  names = [name for name in hints if name != "return"]
                  omitted = await api.probe(**dict.fromkeys(names)).transport_request()
                  assert str(omitted.url) == "https://example.com/probe", omitted.url
                  assert "headerValue" not in omitted.headers
                  assert "cookie" not in omitted.headers
                  first = api.collections()
                  pending = api.collections()
                  first_values = {parameter.name: parameter.value for parameter in first.spec.request.parameters}
                  pending_values = {parameter.name: parameter.value for parameter in pending.spec.request.parameters}
                  first_values["tags"].append("mutated")
                  first_values["counts"]["a"] = 99
                  first_values["empty"].append("filled")
                  for operation in [pending, api.collections()]:
                      values = {parameter.name: parameter.value for parameter in operation.spec.request.parameters}
                      assert values == {"tags": ["a", "b"], "counts": {"a": 0, "b": 2}, "empty": []}, values
                      assert all(values[name] is not first_values[name] for name in values)
                  assert all(pending_values[name] is not collection_defaults[name].default for name in pending_values)
                  first_values["tags"].append(1)
                  try:
                      await first.transport_request()
                  except RequestEncodingError:
                      pass
                  else:
                      raise AssertionError("copied defaults bypassed request validation")
                  collections = await pending.transport_request()
                  assert collections.url.params.get_list("tags") == ["a", "b"]
                  supplied = {"tags": ["a", "b"], "counts": {"a": 0, "b": 2}, "empty": []}
                  supplied_operation = api.collections(**supplied)
                  supplied_values = {parameter.name: parameter.value for parameter in supplied_operation.spec.request.parameters}
                  assert all(supplied_values[name] is supplied[name] for name in supplied)
                  supplied["tags"].append("explicit")
                  supplied_request = await supplied_operation.transport_request()
                  assert supplied_request.url.params.get_list("tags") == ["a", "b", "explicit"]
                  no_collections = await api.collections(tags=None, counts=None, empty=None).transport_request()
                  assert not no_collections.url.params
                  formatted = await api.formatted().transport_request()
                  assert formatted.url.params["date"] == "2026-10-03"
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
              async def collections(self, *args):
                  values = inspect.signature(ParametersService.collections).bind(self, *args).arguments
                  assert values["tags"] == ["a", "b"]
                  assert values["counts"] == {"a": 0, "b": 2}
                  captured.update({key: value.copy() if isinstance(value, (list, dict)) else value for key, value in values.items()})
                  values["tags"].append("mutated")
                  values["counts"]["a"] = 99
              async def formatted(self, *args):
                  pass
              async def required(self, *args):
                  pass
          app = Litestar(route_handlers=[create_parameters_router(Delegate())], plugins=[SundayPlugin()])
          with TestClient(app) as client:
              response = client.get("/probe/explicit", params={"nullableValue": "present"})
              assert response.status_code == 204, response.text
              for _ in range(2):
                  response = client.get("/collections")
                  assert response.status_code == 204, response.text
          assert captured["tags"] == ["a", "b"]
          assert captured["counts"] == {"a": 0, "b": 2}
          assert captured["empty"] == []
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
