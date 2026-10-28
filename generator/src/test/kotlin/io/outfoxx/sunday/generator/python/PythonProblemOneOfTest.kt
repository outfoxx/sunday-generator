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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.generator.tools.rfc9457ProblemOneOfCases
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
@Tag("models")
@Tag("responses")
@Tag("validation")
class PythonProblemOneOfTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["sunday", "litestar"])
  fun `problem oneOf accepts exactly one valid alternative`(
    target: String,
    @ResourceUri("openapi/ir/problem-oneof.yaml") source: URI,
    compiler: PythonCompiler,
  ) {
    val api = GeneratedApiIrExporter().export(source)
    val options =
      PythonGeneratorOptions(placement = pythonPlacement("test_api"), defaultProblemBaseUri = "https://example.org/")
    val modules =
      if (target == "litestar") {
        PythonLitestarIrGenerator(api, options).generateModules()
      } else {
        PythonSundayIrGenerator(api, options).generateModules()
      }
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import json
          from pydantic import TypeAdapter, ValidationError
          from test_api import models
          failures = []
          for case in json.loads('''$rfc9457ProblemOneOfCases'''):
              adapter = TypeAdapter(getattr(models, case['union']))
              try:
                  decoded = adapter.validate_json(json.dumps(case['wire']))
              except ValidationError:
                  if case['expected'] is not None:
                      failures.append(f"Rejected valid case: {case}")
              else:
                  if case['expected'] is None:
                      failures.append(f"Accepted invalid case: {case}")
                  else:
                      assert type(decoded).__name__ == case['expected'], case
                      for member in ['type', 'title', 'status', 'detail', 'instance']:
                          missing = {k: v for k, v in case['wire'].items() if k != member}
                          def outcome(raw):
                              try:
                                  value = adapter.validate_json(json.dumps(raw))
                                  return (type(value).__name__, json.loads(adapter.dump_json(value, by_alias=True)))
                              except ValidationError:
                                  return None
                          for wrong in ([None, True, '404', [], {}] if member == 'status' else [None, True, 42, [], {}]):
                              assert outcome(dict(missing, **{member: wrong})) == outcome(missing), (member, wrong, case)

                      assert json.loads(adapter.dump_json(decoded, by_alias=True)) == case['wire'], case
          assert not failures, '\n'.join(failures)
          """.trimIndent(),
      ),
    )
  }
}
