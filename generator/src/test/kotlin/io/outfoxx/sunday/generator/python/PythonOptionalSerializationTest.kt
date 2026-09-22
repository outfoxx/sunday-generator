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

import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.optionalSerializationApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonOptionalSerializationTest : PythonTest() {
  @Test
  fun `optional fields serialize according to presence and nullability`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(optionalSerializationApi(directory).models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from litestar import Litestar, get
          from litestar.testing import TestClient
          from sunday.litestar import SundayPlugin
          from sunday import JsonCodec
          from pydantic import ValidationError
          from test_api.models import Request, AliasRequest
          alias = AliasRequest(nullableAlias=None, anyValue=None)
          assert json.loads(alias.model_dump_json(by_alias=True)) == {"nullableAlias": None, "anyValue": None}
          expected = {"name": "test", "requiredNullable": None, "optionalNullable": None}
          value = Request(name="test", requiredNullable=None)
          assert value.model_dump(mode="json", by_alias=True) == expected, value.model_dump(mode="json", by_alias=True)
          assert json.loads(value.model_dump_json(by_alias=True)) == expected
          assert json.loads(JsonCodec().encode(value)) == {"name": "test", "requiredNullable": None}
          @get("/value")
          async def endpoint() -> Request:
              return value
          with TestClient(Litestar(route_handlers=[endpoint], plugins=[SundayPlugin()])) as client:
              assert client.get("/value").json() == expected
          for fields in ({"text": "", "number": 0, "flag": False, "items": []},
                         {"text": "main", "number": 1, "flag": True, "items": ["item"]}):
              wire = dict(expected, **fields)
              restored = Request.model_validate(wire)
              assert json.loads(restored.model_dump_json(by_alias=True)) == wire
          for invalid in ({}, {"name": "test"}, dict(expected, name=None), dict(expected, text=None)):
              try:
                  Request.model_validate(invalid)
                  raise AssertionError(f"invalid value accepted: {invalid}")
              except ValidationError:
                  pass
          """.trimIndent(),
      ),
    )
  }
}
