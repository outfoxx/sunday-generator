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

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTolerance
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
@Tag("models")
@Tag("validation")
class PythonDirectionalToleranceTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `object oneOf rejects overlap while anyOf keeps overlapping branches`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = objectUnionValidationApi(frontend, directory)
    val choice = api.models.single { it.name == "Choice" }
    val models = api.models + choice.copy(name = "AnyChoice", unionMode = GeneratedModel.UnionMode.ANY_OF)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        smokeCode =
          """
          import json
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Choice, AnyChoice, Holder
          for mode in ("request", "response"):
              context = {"mode": mode}
              for native in (False, True):
                  def decode(adapter, value):
                      if native:
                          return adapter.validate_python(value, context=context)
                      return adapter.validate_json(json.dumps(value), context=context)
                  adapter = TypeAdapter(Choice)
                  for number in (1, 9):
                      value = decode(adapter, {"value": number})
                      assert value.value == number
                      assert json.loads(adapter.dump_json(value)) == {"value": number}
                  assert decode(TypeAdapter(AnyChoice), {"value": 2}).value == 2
                  for number in ${if (frontend == "raml") "(0, 10)" else "(0, 2, 10)"}:
                      for target, raw in ((adapter, {"value": number}), (TypeAdapter(Holder), {"choice": {"value": number}})):
                          try:
                              decode(target, raw)
                          except ValidationError:
                              pass
                          else:
                              raise AssertionError(f"Invalid union accepted: {raw}")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `named oneOf cases retain parent requirements without property declarations`(
    requiredInPayload: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("required.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Required union, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Payload:
            type: object
            ${if (requiredInPayload) "required: [id]" else ""}
            properties:
              id: {type: string}
          Choice:
            required: [id]
            oneOf: [{${'$'}ref: '#/components/schemas/Payload'}]
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val choice = api.models.single { it.name == "Choice" }
    assertEquals(if (requiredInPayload) "Payload" else "ChoiceCase1", choice.aliases.single().name)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Choice, Payload
          adapter = TypeAdapter(Choice)
          assert adapter.validate_python({"id": "present"}).id == "present"
          assert Payload.model_fields["id"].is_required() == ${if (requiredInPayload) "True" else "False"}
          for invalid in ({}, {"id": None}):
              try:
                  adapter.validate_python(invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("Parent required field was lost")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `union common constraints retain independent payload schemas`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = objectUnionValidationApi(frontend, directory, discriminated = true, commonMaximum = 5)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Choice, Large, Holder
          payload = Large(kind="large", value=9)
          assert Large.model_validate(payload).value == 9
          adapter = TypeAdapter(Choice)
          assert isinstance(adapter.validate_python({"kind": "large", "value": 2}), Large)
          for candidate in (payload, {"kind": "large", "value": 9}):
              try:
                  adapter.validate_python(candidate)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("Union common constraint was lost")
          try:
              Holder.model_validate({"choice": payload})
          except ValidationError:
              pass
          else:
              raise AssertionError("Nested union common constraint was lost")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("requests")
  fun `tolerant request unions preserve native constraints and nested modes`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = directionalToleranceApi(frontend, directory)
    val models = api.models.map { if (it.name == "Event") it.copy(tolerance = GeneratedTolerance.ALL) else it }
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Event, State
          adapter = TypeAdapter(Event)
          unknown = adapter.validate_python({"kind": "future", "note": "valid", "state": "future"})
          assert unknown.note == "valid"
          assert unknown.state == State("future")
          try:
              adapter.validate_python(unknown, context={"mode": "request"})
          except ValidationError as error:
              assert error.errors()[0]["loc"][-1] == "state"
              assert error.errors()[0]["type"] == "unknown_enum"
          else:
              raise AssertionError("nested response-only enum accepted in a tolerant request union")
          unknown.root["state"] = "active"
          adapter.validate_python(unknown, context={"mode": "request"})
          unknown.root["note"] = "x"
          try:
              adapter.validate_python(unknown)
          except ValidationError as error:
              assert error.errors()[0]["loc"][-1] == "note"
              assert error.errors()[0]["type"] == "string_too_short"
          else:
              raise AssertionError("mutated fallback base constraint was not checked")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `string discriminator fallbacks retain unknown payloads and reject malformed known branches`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = directionalToleranceApi(frontend, directory)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Event, EventUnknown, Item, State, OpenState
          state = TypeAdapter(State).validate_python("future")
          TypeAdapter(OpenState).validate_python("future", context={"mode": "request"})
          item = Item(state=State.active)
          assert Item.model_validate(item, context={"mode": "request"}) == item
          item.states = [State.active, state]
          try:
              Item.model_validate(item, context={"mode": "request"})
          except ValidationError as error:
              assert error.errors()[0]["loc"] == ("states", 1)
          else:
              raise AssertionError("nested unknown request value accepted")
          item.states = [State.active]
          Item.model_validate(item, context={"mode": "request"})
          adapter = TypeAdapter(Event)
          raw = {"kind": "future", "detail": {"attempt": 2}}
          unknown = adapter.validate_python(raw)
          assert isinstance(unknown, EventUnknown)
          assert unknown.kind == "future"
          assert unknown.raw_body == raw
          try:
              adapter.validate_python(unknown, context={"mode": "request"})
          except ValidationError as error:
              assert error.errors()[0]["type"] == "unknown_union"
          else:
              raise AssertionError("unknown request union accepted")
          disguised = EventUnknown.model_construct(root={"kind": "created", "count": 1})
          try:
              adapter.validate_python(disguised, context={"mode": "request"})
          except ValidationError as error:
              assert error.errors()[0]["type"] == "unknown_union"
          else:
              raise AssertionError("fallback escaped through known branch")
          assert adapter.dump_python(unknown, mode="json") == raw
          adapter.validate_python({"kind": "created", "count": 1})
          for invalid in [{"kind": "x"}, {"kind": "FUTURE"}, {"kind": "created"}, {"kind": None}, {}]:
              try:
                  adapter.validate_python(invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("invalid branch accepted")
          """.trimIndent(),
      ),
    )
  }
}
