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
import io.outfoxx.sunday.generator.tools.closedModelsApi
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
@Tag("models")
@Tag("validation")
class PythonClosedModelsTest : PythonTest() {
  @ParameterizedTest
  @CsvSource(
    "raml,false",
    "raml,true",
    "openapi,false",
    "openapi,true",
    "asyncapi,false",
    "asyncapi,true",
    "composed,false",
    "composed,true",
    "openapi-unevaluated,false",
    "openapi-unevaluated,true",
    "asyncapi-unevaluated,false",
    "asyncapi-unevaluated,true",
    "composed-unevaluated,false",
    "composed-unevaluated,true",
  )
  fun `closed objects retain unknown field rejection`(
    frontend: String,
    preserve: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = closedModelsApi(frontend, directory)
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
          from pydantic import ValidationError
          from test_api.models import ClosedRecord, ClosedChild, EmptyClosed, OpenRecord
          ${if (frontend.startsWith("composed")) "from test_api.models import EventRecord" else ""}
          cases = [(ClosedRecord, {"display-name": "valid"}), (ClosedChild, {"name": "valid", "count": 1}), (EmptyClosed, {})]
          ${if (frontend.startsWith("composed")) "cases.append((EventRecord, {}))" else ""}
          for model, valid in cases:
              model.model_validate(valid)
              for extra in [1, None, {}, []]:
                  try:
                      model.model_validate({**valid, "extra": extra})
                  except ValidationError as error:
                      assert any(item["type"] == "extra_forbidden" for item in error.errors())
                  else:
                      raise AssertionError("unknown field accepted")
          open_value = OpenRecord.model_validate({"name": "valid", "extra": 1})
          assert ("extra" in open_value.model_dump()) == ${if (preserve) "True" else "False"}
          from test_api.models import ExplicitRecord, ExplicitChild, ExplicitLeaf
          for model in [ExplicitRecord, ExplicitChild, ExplicitLeaf]:
              assert model.model_validate({"extra": 1}).model_dump()["extra"] == 1
          """.trimIndent(),
      ),
    )
  }
}
