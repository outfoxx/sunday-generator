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

import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.anyOfDeserializationApi
import io.outfoxx.sunday.generator.tools.anyOfInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfValidWires
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Tag("models")
@Tag("validation")
class PythonAnyOfDeserializationTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `anyOf tries complete alternatives and preserves open fields`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {

    val api = anyOfDeserializationApi(frontend, directory)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api", preserveUnknownFields = UnknownFieldPreservation.On).renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import UserResponse, UserSelfResponse, UserSummaryResponse
          adapter = TypeAdapter(UserResponse)
          for index, raw in enumerate(json.loads('''$anyOfValidWires''')):
              value = adapter.validate_json(json.dumps(raw))
              assert isinstance(value, UserSelfResponse if index == 1 else UserSummaryResponse), raw
              assert json.loads(adapter.dump_json(value)) == raw
          for raw in json.loads('''$anyOfInvalidWires'''):
              try:
                  adapter.validate_json(json.dumps(raw))
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f"Invalid union accepted: {raw}")
          """.trimIndent(),
      ),
    )
  }
}
