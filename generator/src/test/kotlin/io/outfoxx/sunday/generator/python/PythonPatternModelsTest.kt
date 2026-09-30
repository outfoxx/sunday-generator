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
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonPatternModelsTest : PythonTest() {
  @ParameterizedTest
  @CsvSource("false,true", "true,true", "false,false", "true,false")
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    preserve: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = patternModelsApi(directory, composed)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api", preserve).renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import ValidationError
          from test_api.models import PatternClosedInherited, PatternFieldInherited, NestedAdditionalPattern, PatternObject
          from test_api.models import PatternInherited, PatternRecord, PatternOnly, OpenPattern
          for wire in [${patternModelValid.joinToString { "'$it'" }}]:
              decoded = PatternRecord.model_validate(json.loads(wire))
              output = decoded.model_dump(mode="json", by_alias=True, exclude_unset=True)
              if ${if (preserve) "True" else "False"}:
                  for key, value in json.loads(wire).items():
                      assert output[key] == value, (key, output)
              else:
                  assert "x-value" not in output and "maybe-value" not in output
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
          ${patternModelRegressions.entries.joinToString("\n          ") { (name, values) ->
            """
            for wire in [${values.first.joinToString { "'$it'" }}]:
                $name.model_validate(json.loads(wire))
                $name(**json.loads(wire))
            for wire in [${values.second.joinToString { "'$it'" }}]:
                for validate in [$name.model_validate, lambda data: $name(**data)]:
                    try:
                        validate(json.loads(wire))
                    except ValidationError:
                        pass
                    else:
                        raise AssertionError("$name accepted: " + wire)
            """.trimIndent().prependIndent("          ").trimStart()
          }}
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

  @Test
  fun `pattern validator enforces closedness inherited through IR`(
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val models =
      patternModelsApi(
        directory,
      ).models.filter { it.name in setOf("ClosedFieldParent", "PatternClosedInherited") }.map {
        if (it.name == "PatternClosedInherited") it.copy(closed = null, additionalProperties = null) else it
      }
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
          from pydantic import ValidationError
          from test_api.models import PatternClosedInherited
          PatternClosedInherited(x_fixed="okay")
          PatternClosedInherited.model_validate({"x-fixed": "okay"})
          for data in [{"extra": 1}, {"extra": None}, {"x-fixed": "a"}, {"x_fixed": "a"}]:
              for validate in [PatternClosedInherited.model_validate, lambda data: PatternClosedInherited(**data)]:
                  try:
                      validate(data)
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError("inherited closed contract ignored: " + repr(data))
          """.trimIndent(),
      ),
    )
  }
}
