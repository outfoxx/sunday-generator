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
import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonPatternModelsTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = patternModelsApi(directory, composed)
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
          from pydantic import ValidationError
          from test_api.models import PatternInherited, PatternRecord, PatternOnly, OpenPattern
          for wire in [${patternModelValid.joinToString { "'$it'" }}]:
              PatternRecord.model_validate(json.loads(wire))
          for wire in [${patternModelInvalid.joinToString { "'$it'" }}]:
              try:
                  PatternRecord.model_validate(json.loads(wire))
              except ValidationError:
                  pass
              else:
                  raise AssertionError("invalid pattern value accepted: " + wire)
          try:
              PatternRecord(x_fixed="a")
          except ValidationError:
              pass
          else:
              raise AssertionError("named pattern constraint bypassed through Python field name")
          PatternInherited.model_validate({"x-valid": "ok"})
          for invalid in [{"x-invalid": "a"}, {"extra": 1}]:
              try:
                  PatternInherited.model_validate(invalid)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("inherited constraint ignored")
          OpenPattern.model_validate({"extra": 1, "x-valid": "ok"})
          try:
              OpenPattern.model_validate({"extra": "wrong"})
          except ValidationError:
              pass
          else:
              raise AssertionError("invalid fallback accepted")
          PatternOnly.model_validate({"x-valid": "ok"})
          """.trimIndent(),
      ),
    )
  }
}
