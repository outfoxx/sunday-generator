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

import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.sunday.generator.tools.optionalSerializationApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonOptionalSerializationTest : PythonTest() {
  @Test
  fun `optional scalar null validation follows pydantic input selection`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("input-names.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Input names",
        """
        ResourceScope:
          type: object
          properties:
            projectId: {type: string}
            nullableId: {type: [string, 'null']}
        ChildScope:
          allOf:
            - {${'$'}ref: '#/components/schemas/ResourceScope'}
            - type: object
              properties:
                displayName: {type: string}
        """.trimIndent(),
      ),
    )
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(OpenApiToGeneratedApi().convert(source.toUri()).models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import ConfigDict, ValidationError
          from test_api.models import ResourceScope, ChildScope
          class NameOnly(ResourceScope):
              model_config = ConfigDict(validate_by_alias=False, validate_by_name=True)
          class AliasOnly(ResourceScope):
              model_config = ConfigDict(validate_by_alias=True, validate_by_name=False)
          def rejects(create, data):
              try:
                  create(data)
              except ValidationError:
                  return
              raise AssertionError(f"accepted explicit null: {data}")
          for model in (ResourceScope, ChildScope):
              for create in (lambda data: model(**data), model.model_validate):
                  assert create({}).project_id is None
                  for key in ("projectId", "project_id"):
                      rejects(create, {key: None})
                      assert create({key: "ok"}).project_id == "ok"
                  for key in ("nullableId", "nullable_id"):
                      assert create({key: None}).nullable_id is None
                  assert create({"projectId": "alias", "project_id": None}).project_id == "alias"
                  rejects(create, {"projectId": None, "project_id": "name"})
          for model, selected, ignored in ((NameOnly, "project_id", "projectId"),
                                           (AliasOnly, "projectId", "project_id")):
              for create in (lambda data: model(**data), model.model_validate):
                  assert create({selected: "ok", ignored: None}).project_id == "ok"
                  rejects(create, {selected: None, ignored: "ok"})
          assert ResourceScope.model_validate(
              {"project_id": "name", "projectId": None}, by_alias=False, by_name=True
          ).project_id == "name"
          rejects(lambda data: ResourceScope.model_validate(data, by_alias=False, by_name=True),
                  {"project_id": None, "projectId": "alias"})
          """.trimIndent(),
      ),
    )
  }

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
          from test_api.models import Request, AliasRequest, CollectionRequest
          collection_nulls = {"nullableEntries": None, "nullableLookup": None}
          for fields in ({}, {"entries": [None], "lookup": {"key": None}, "aliasedEntries": [], "aliasedLookup": {}}):
              wire = dict(collection_nulls, **fields)
              collection = CollectionRequest.model_validate(wire)
              assert json.loads(collection.model_dump_json(by_alias=True)) == wire
          contents = {"nullableEntries": [None], "nullableLookup": {"key": None}}
          assert CollectionRequest.model_validate(contents).model_dump(mode="json", by_alias=True) == contents
          for field in ("entries", "lookup", "aliasedEntries", "aliasedLookup"):
              try:
                  CollectionRequest.model_validate({field: None})
                  raise AssertionError("non-nullable container accepted null")
              except ValidationError:
                  pass
          for create in (lambda fields: CollectionRequest(**fields), CollectionRequest.model_validate):
              assert create({}).aliased_entries is None
              for field in ("aliasedEntries", "aliased_entries"):
                  assert create({field: []}).aliased_entries == []
                  try:
                      create({field: None})
                      raise AssertionError(f"non-nullable field accepted null: {field}")
                  except ValidationError:
                      pass
              assert create({"aliasedEntries": [], "aliased_entries": None}).aliased_entries == []
              try:
                  create({"aliasedEntries": None, "aliased_entries": []})
                  raise AssertionError("null alias lost precedence over field name")
              except ValidationError:
                  pass
              for field in ("nullableEntries", "nullable_entries"):
                  assert create({field: None}).nullable_entries is None
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
