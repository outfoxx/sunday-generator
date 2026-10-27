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
import io.outfoxx.sunday.generator.tools.narrowedProblemDetailApi
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX)
@Tag("models")
@Tag("responses")
class PythonProblemEncodingTest : PythonTest() {
  @ParameterizedTest
  @CsvSource(
    "openapi,sunday",
    "asyncapi,sunday",
    "composed,sunday",
    "openapi,litestar",
    "asyncapi,litestar",
    "composed,litestar",
  )
  fun `problem encoding omits absent detail and preserves empty strings`(
    frontend: String,
    target: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = narrowedProblemDetailApi(frontend, directory)
    val options =
      PythonGeneratorOptions(placement = pythonPlacement("test_api"), defaultProblemBaseUri = "https://example.com/")
    val modules =
      if (target ==
        "sunday"
      ) {
        PythonSundayIrGenerator(api, options).generateModules()
      } else {
        PythonLitestarIrGenerator(api, options).generateModules()
      }
    assertTrue(
      compileModules(
        compiler,
        modules,
        smokeCode =
          """
          import json
          from test_api.models import AccountFieldsInvalidProblem
          from test_api.models_problems import AccountFieldsInvalidProblem as ProblemError
          for detail in [None, '', 'Text']:
              raw = {"type": "https://example.com/problems/account-fields-invalid"}
              if detail is not None:
                  raw['detail'] = detail
              payload = AccountFieldsInvalidProblem.model_validate(raw)
              error = ProblemError() if detail is None else ProblemError(detail=detail)
              for value in [payload, error]:
                  encoded = value.model_dump(mode='json', by_alias=True)
                  assert ('detail' in encoded) == (detail is not None), encoded
                  if detail is not None:
                      assert encoded['detail'] == detail
                  assert AccountFieldsInvalidProblem.model_validate(encoded).model_dump(mode='json', by_alias=True) == encoded
              encoded_json = json.loads(payload.model_dump_json(by_alias=True))
              assert ('detail' in encoded_json) == (detail is not None), encoded_json
          """.trimIndent(),
      ),
    )
  }
}
