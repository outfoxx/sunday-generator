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

import io.outfoxx.sunday.generator.ir.GeneratedTolerance
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
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
class PythonDirectionalToleranceTest : PythonTest() {
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
          item = Item(state=State.ACTIVE)
          assert Item.model_validate(item, context={"mode": "request"}) == item
          item.states = [State.ACTIVE, state]
          try:
              Item.model_validate(item, context={"mode": "request"})
          except ValidationError as error:
              assert error.errors()[0]["loc"] == ("states", 1)
          else:
              raise AssertionError("nested unknown request value accepted")
          item.states = [State.ACTIVE]
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
