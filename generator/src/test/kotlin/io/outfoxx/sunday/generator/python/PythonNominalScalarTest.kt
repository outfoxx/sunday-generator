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
import io.outfoxx.sunday.generator.tools.nominalScalarApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
@Tag("models")
@Tag("validation")
class PythonNominalScalarTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `nominal scalars validate and preserve union identity`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = nominalScalarApi(frontend, directory)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
          PythonModuleBuilder("test_api/type_checks.py")
            .addCode(
              PythonCodeBlock.of(
                """
                from test_api.models import BaseFactSid, BaseLossSid

                plain: BaseFactSid = "sid:f:abc"  # type: ignore[assignment]
                other: BaseFactSid = BaseLossSid("sid:l:abc")  # type: ignore[assignment]
                """.trimIndent(),
              ),
            ).build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import TypeAdapter
          from test_api.models import BaseFactSid, BaseLossSid, AnySid, AmbiguousSid, PositiveCount, Ratio, Enabled, Defaults, Record
          adapter = TypeAdapter(AnySid)
          for value, branch in [("sid:f:abc", BaseFactSid), ("sid:l:abc", BaseLossSid)]:
              for decoded in [adapter.validate_python(value), adapter.validate_json(json.dumps(value))]:
                  assert type(decoded) is branch
                  assert json.loads(adapter.dump_json(decoded)) == value
          def rejects(create):
              try:
                  create()
              except (TypeError, ValueError):
                  return
              raise AssertionError("invalid value accepted")
          rejects(lambda: BaseFactSid("invalid"))
          rejects(lambda: adapter.validate_json('"invalid"'))
          rejects(lambda: PositiveCount(0))
          rejects(lambda: Ratio(2))
          defaulted = Defaults.model_validate({})
          ${if (frontend == "raml") "assert defaulted.fact is None" else "assert type(defaulted.fact) is BaseFactSid and defaulted.fact == 'sid:f:default'"}
          assert TypeAdapter(Enabled).validate_python(Enabled(True)) == Enabled(True)
          ${if (frontend == "raml") "TypeAdapter(AmbiguousSid).validate_python('sid:f:abc')" else "rejects(lambda: TypeAdapter(AmbiguousSid).validate_python('sid:f:abc'))"}
          wire = {"fact": "sid:f:abc", "identifiers": ["sid:l:abc"], "count": 2, "ratio": 0.5, "enabled": True}
          record = Record.model_validate(wire)
          assert type(record.fact) is BaseFactSid
          assert type(record.identifiers[0]) is BaseLossSid
          assert record.model_dump(mode="json") == wire
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("requests")
  fun `nominal parameters work in generated clients and servers`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api =
      nominalScalarApi(frontend, directory).let {
        it.copy(services = it.services.map { service -> service.copy(name = "Ids", group = null) })
      }
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
          if (frontend == "asyncapi") {
            null
          } else {
            """
            from litestar import Litestar
            from litestar.testing import TestClient
            from sunday.litestar import SundayPlugin
            from server_api.ids_server import create_ids_router
            from server_api.models import BaseFactSid, BaseLossSid, Record
            class Delegate:
                received = None
                def __getattr__(self, name):
                    async def call(*args):
                        self.received = args
                        return Record.model_validate({"fact": "sid:f:abc", "identifiers": ["sid:l:abc"], "count": 2, "ratio": 0.5, "enabled": True})
                    return call
            delegate = Delegate()
            with TestClient(Litestar(route_handlers=[create_ids_router(delegate)], plugins=[SundayPlugin()], debug=True)) as client:
                for id, branch in [("sid:f:abc", BaseFactSid), ("sid:l:abc", BaseLossSid)]:
                    response = client.get("/records/" + id, params={"fact": "sid:f:query"})
                    assert response.status_code == 200, response.text
                    assert type(delegate.received[0]) is branch, delegate.received
                    assert type(delegate.received[1]) is BaseFactSid, delegate.received
                response = client.get("/records/sid:f:abc")
                assert response.status_code == 200, response.text
                ${if (frontend == "raml") "assert delegate.received[1] is None" else "assert type(delegate.received[1]) is BaseFactSid and delegate.received[1] == 'sid:f:default'"}
                assert client.get("/records/invalid", params={"fact": "sid:f:query"}).status_code == 400
                assert client.get("/records/sid:f:abc", params={"fact": "invalid"}).status_code == 400
            """.trimIndent()
          },
      ),
    )
  }
}
