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
import io.outfoxx.sunday.generator.tools.patchableApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Tag("models")
@Tag("validation")
@Tag("requests")
class PythonPatchableTypesTest : PythonTest() {
  @Test
  @RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
  fun `Litestar validates participating PATCH fields before invoking the delegate`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = patchableApi("openapi", directory)
    val service =
      api.services.first().copy(
        name = "Patch",
        group = null,
        operations =
          api.services.flatMap {
            it.operations
          },
      )
    val modules =
      PythonLitestarIrGenerator(
        api.copy(services = listOf(service)),
        PythonGeneratorOptions(packageName = "test_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service))
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("test_api.patch_server"),
        smokeCode =
          """
          from litestar import Litestar
          from litestar.testing import TestClient
          from sunday import UNSET
          from sunday.litestar import SundayPlugin
          from test_api.models import SomeRequest, SomeRequestPatch
          from test_api.patch_server import create_patch_router
          class Delegate:
              received = None
              async def update_request(self, body):
                  assert isinstance(body, SomeRequestPatch)
                  self.received = body
              async def create_request(self, body):
                  assert isinstance(body, SomeRequest)
                  self.received = body
                  return body
              async def plain_request(self, body):
                  assert isinstance(body, SomeRequest)
                  self.received = body
          delegate = Delegate()
          with TestClient(Litestar(route_handlers=[create_patch_router(delegate)], plugins=[SundayPlugin()])) as client:
              for fields in ({}, {"description": None}, {"title": None, "display-name": None, "optional-alias": None}, {"title": "New", "count": 2, "state": "active"}):
                  delegate.received = None
                  response = client.put("/request", json=fields, headers={"Content-Type": "application/merge-patch+json"})
                  assert response.status_code == 204, response.text
                  assert delegate.received.model_dump(mode="json", by_alias=True) == fields
                  if "title" not in fields:
                      assert delegate.received.title is UNSET
              for fields in ({"required-nullable": None}, {"required-alias": None}, {"title": "x"}, {"count": 0}, {"state": "future"}):
                  delegate.received = None
                  response = client.put("/request", json=fields, headers={"Content-Type": "application/merge-patch+json"})
                  assert response.status_code == 400, response.text
                  assert delegate.received is None
              delegate.received = None
              response = client.post("/request", json={})
              assert response.status_code == 400, response.text
              assert delegate.received is None
              response = client.post("/request", json={"count": 2, "required-nullable": None, "required-alias": None})
              assert response.status_code == 200, response.text
              assert delegate.received.title == "initial"
              delegate.received = None
              response = client.patch("/request", json={})
              assert response.status_code == 400, response.text
              assert delegate.received is None
              response = client.patch("/request", json={"count": 2, "required-nullable": None, "required-alias": None})
              assert response.status_code == 204, response.text
              assert isinstance(delegate.received, SomeRequest)
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference"])
  fun `PATCH fields expose typed UNSET value and deletion states`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val modules =
      PythonSundayIrGenerator(patchableApi(frontend, directory), PythonGeneratorOptions(packageName = "test_api"))
        .generateModules(GeneratedTypeCategory.entries.toSet())
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import ValidationError
          from sunday import UNSET, JsonCodec
          from test_api.models import SomeRequest, SomeRequestPatch
          for fields in ({}, {"title": "new title"}, {"description": None},
                         {"title": None, "display-name": None, "optional-alias": None},
                         {"required-nullable": "new", "required-alias": "new"},
                         {"description": "text", "count": 2, "state": "active", "display-name": "Name"}):
              patch = SomeRequestPatch.model_validate(fields)
              assert patch.model_dump(mode="json", by_alias=True) == fields
              assert json.loads(JsonCodec().encode(patch)) == fields
              assert SomeRequestPatch.model_validate(patch, context={"mode": "request"}).model_dump(mode="json") == fields
          assert SomeRequestPatch().title is UNSET
          assert SomeRequestPatch(title=UNSET).model_dump(mode="json") == {}
          for fields in ({"required-nullable": None}, {"required-alias": None}, {"title": "x"}, {"count": 0}, {"count": None}):
              try:
                  SomeRequestPatch.model_validate(fields)
                  raise AssertionError(f"invalid patch accepted: {fields}")
              except ValidationError:
                  pass
          try:
              SomeRequest.model_validate({})
              raise AssertionError("ordinary required field lost")
          except ValidationError:
              pass
          assert SomeRequest.model_validate({"count": 2, "required-nullable": None, "required-alias": None}).title == ${if (frontend
              .startsWith(
                "raml",
              )
          ) {
            "None"
          } else {
            "\"initial\""
          }}
          patch = SomeRequestPatch(title="valid")
          patch.title = None
          SomeRequestPatch.model_validate(patch, context={"mode": "request"})
          patch.title = UNSET
          assert patch.model_dump(mode="json") == {}
          for name in ("required_nullable", "required_alias"):
              setattr(patch, name, None)
              try:
                  SomeRequestPatch.model_validate(patch, context={"mode": "request"})
                  raise AssertionError("required member deleted after mutation")
              except ValidationError:
                  pass
              setattr(patch, name, UNSET)
          unknown = SomeRequestPatch.model_validate({"state": "future"})
          try:
              SomeRequestPatch.model_validate(unknown, context={"mode": "request"})
              raise AssertionError("unknown enum accepted in request")
          except ValidationError:
              pass
          unknown.state = UNSET
          SomeRequestPatch.model_validate(unknown, context={"mode": "request"})
          """.trimIndent(),
      ),
    )
  }
}
