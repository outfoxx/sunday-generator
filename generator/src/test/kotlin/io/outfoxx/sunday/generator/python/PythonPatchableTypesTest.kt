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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
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
import kotlin.io.path.writeText

@Tag("models")
@Tag("validation")
@Tag("requests")
class PythonPatchableTypesTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `dynamic patch members retain deletions and validate supplied values`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val schemas =
      """
      Base:
        type: object
        required: [id]
        properties:
          id: {type: string}
        additionalProperties: {type: string, minLength: 2}
        patternProperties:
          '^x-': {type: string, minLength: 2}
          '^enum-': {type: string, enum: [first, second]}
      Item:
        x-sunday-patchable: true
        allOf:
          - {${'$'}ref: '#/components/schemas/Base'}
          - type: object
            properties:
              label: {type: string}
      """.trimIndent().prependIndent("    ")
    val openapi =
      """
      openapi: 3.1.0
      info: {title: Dynamic patch, version: 1.0.0}
      paths: {}
      components:
        schemas:
      """.trimIndent() + "\n" + schemas
    val asyncapi =
      """
      asyncapi: 3.0.0
      info: {title: Dynamic patch, version: 1.0.0}
      channels:
        item:
          address: /item
          messages:
            item: {payload: {${'$'}ref: '#/components/schemas/Item'}}
      operations:
        receiveItem:
          action: receive
          channel: {${'$'}ref: '#/channels/item'}
          messages: [{${'$'}ref: '#/channels/item/messages/item'}]
      components:
        schemas:
      """.trimIndent() + "\n" + schemas
    val raml =
      """
      #%RAML 1.0
      title: Dynamic patch
      annotationTypes:
        sunday.patchable: {type: boolean, allowedTargets: [TypeDeclaration]}
      types:
        EnumValue: {type: string, enum: [first, second]}
        Base:
          type: object
          properties:
            id: string
            /.+/: {type: string, minLength: 2}
            /^x-/: {type: string, minLength: 2}
            /^enum-/: EnumValue
        Item:
          type: Base
          (sunday.patchable): true
          properties:
            label?: string
      """.trimIndent()
    val source = directory.resolve(if (frontend == "raml") "patch.raml" else "patch.yaml")
    source.writeText(
      when (frontend) {
        "raml" -> raml
        "asyncapi" -> asyncapi
        else -> openapi
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      events.writeText(asyncapi.replace("/schemas/Item", "/schemas/Base"))
      sources += events.toUri()
    }
    val modules =
      PythonSundayIrGenerator(
        GeneratedApiIrExporter().export(sources),
        PythonGeneratorOptions(packageName = "test_api"),
      ).generateModules(setOf(GeneratedTypeCategory.Model))
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import ValidationError
          from sunday import JsonCodec
          from test_api.models import Item, ItemPatch
          original = Item.model_validate({"id": "base", "extra": "value", "x-known": "value", "enum-known": "first"})
          for key in ["extra", "x-known", "enum-known"]:
              fields = {key: None}
              patch = ItemPatch.model_validate(fields, context={"mode": "request"})
              assert patch.model_dump(mode="json", by_alias=True) == fields
              assert json.loads(JsonCodec().encode(patch)) == fields
              assert ItemPatch.model_validate_json(JsonCodec().encode(patch)) == patch
              merged = original.merge(patch)
              assert key not in merged.model_dump(mode="json", by_alias=True)
              assert key in original.model_dump(mode="json", by_alias=True)
              for invalid in [None, 1, "x"]:
                  try:
                      Item.model_validate({"id": "base", key: invalid})
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError("ordinary dynamic constraint lost")
          for fields in [{"extra": "valid"}, {"x-known": "valid"}, {"enum-known": "second"}]:
              patch = ItemPatch.model_validate(fields)
              assert patch.model_dump(mode="json", by_alias=True) == fields
          for fields in [{"extra": 1}, {"extra": "x"}, {"x-known": 1}, {"x-known": "x"}, {"enum-known": "other"}, {"id": None}]:
              try:
                  ItemPatch.model_validate(fields, context={"mode": "request"})
              except ValidationError:
                  pass
              else:
                  raise AssertionError("invalid patch accepted: " + repr(fields))
          """.trimIndent(),
      ),
    )
  }

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
  @ValueSource(
    strings = [
      "raml", "raml-auto", "openapi", "asyncapi", "composed", "composed-collisions", "reference", "collisions",
    ],
  )
  fun `PATCH fields expose typed UNSET value and deletion states`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val patchName = if (frontend == "composed-collisions") "SomeRequestPatch2" else "SomeRequestPatch"
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
          for fields in ({"required-nullable": None}, {"required-alias": None}, {"title": "x"}, {"count": 0}, {"count": None}, {"labels": {"bad": "x"}}):
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
          base = SomeRequest.model_validate({"count": 2, "required-nullable": None, "required-alias": None})
          try:
              base.patch${if (frontend == "collisions") "_" else ""}()
              raise AssertionError("required null snapshot accepted")
          except ValidationError as error:
              assert any(issue["loc"][0] == "required-nullable" for issue in error.errors())
          assert base.patch${if (frontend == "collisions") "_" else ""}(from_model=base).model_dump(mode="json", by_alias=True) == {}
          changed = SomeRequest.model_validate({"count": 3, "required-nullable": "valid", "required-alias": None, "title": "Changed"})
          changes = changed.patch${if (frontend == "collisions") "_" else ""}(from_model=base)
          assert changes.model_dump(mode="json", by_alias=True) == {"count": 3, "required-nullable": "valid", "title": "Changed"}
          restored = base.merge${if (frontend == "collisions") "_" else ""}(changes)
          assert restored.count == 3 and restored.required_nullable == "valid" and restored.required_alias is None
          assert base.count == 2
          assert restored.merge${if (frontend == "collisions") "_" else ""}(SomeRequestPatch(title=None)).title is None
          non_null = SomeRequest.model_validate({"count": 3, "required-nullable": "valid", "required-alias": "valid"})
          assert non_null.patch${if (frontend == "collisions") "_" else ""}() == SomeRequestPatch.from_model(non_null)
          try:
              base.patch${if (frontend == "collisions") "_" else ""}(from_model=non_null)
              raise AssertionError("required null assignment accepted")
          except ValidationError:
              pass

          nested_base = SomeRequest.model_validate(json.loads('{"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Original","note":"Remove","child":{"name":"Child","note":"Keep"}},"numbers":[1,null,2],"labels":{"keep":"yes","remove":"old"}}'))
          nested_updated = SomeRequest.model_validate(json.loads('{"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Changed","note":"Remove","child":{"name":"Updated child","note":"Keep"}},"numbers":[3,null],"labels":{"keep":"yes","remove":"old"}}'))
          nested_diff = nested_updated.patch${if (frontend == "collisions") "_" else ""}(from_model=nested_base)
          assert nested_diff.model_dump(mode="json", by_alias=True) == json.loads('{"details":{"name":"Changed","child":{"name":"Updated child"}},"numbers":[3,null]}')
          nested_merged = nested_base.merge${if (frontend == "collisions") "_" else ""}(nested_diff)
          assert nested_merged.details.child.name == "Updated child" and nested_base.details.child.name == "Child"
          assert nested_merged.details.name == "Changed" and nested_merged.numbers == [3, None]
          nested_deleted = nested_base.merge${if (frontend == "collisions") "_" else ""}(SomeRequestPatch.model_validate(json.loads('{"details":{"note":null},"labels":{"remove":null}}')))
          assert nested_deleted.details.note is None and "remove" not in nested_deleted.labels
          assert nested_base.details.note == "Remove"
          try:
              base.merge${if (frontend == "collisions") "_" else ""}(SomeRequestPatch.model_validate({"details": {"note": "new"}}))
              raise AssertionError("invalid merged child accepted")
          except ValidationError:
              pass
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
          """.trimIndent().replace("SomeRequestPatch", patchName),
      ),
    )
  }
}
