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
import io.outfoxx.sunday.generator.tools.anyOfObjectConstraintsApi
import io.outfoxx.sunday.generator.tools.anyOfObjectInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfObjectValidWires
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** Compile-backed runtime coverage for inclusive object constraints from issue #277. */
@Tag("models")
@Tag("validation")
class PythonAnyOfObjectConstraintsTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["shared", "inline", "named", "shared-composed", "nested"])
  fun `object anyOf preserves inclusive requirements and round trips`(
    shape: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {

    val api = anyOfObjectConstraintsApi(shape, directory)
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
          import json
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import CheckoutRequest
          adapter = TypeAdapter(CheckoutRequest)
          for raw in json.loads('''$anyOfObjectValidWires'''):
              value = adapter.validate_json(json.dumps(raw))
              assert json.loads(adapter.dump_json(value)) == raw
          for raw in json.loads('''$anyOfObjectInvalidWires'''):
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
