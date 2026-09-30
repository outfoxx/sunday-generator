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
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.openModelWire
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
class PythonOpenModelFieldsTest : PythonTest() {
  @ParameterizedTest
  @CsvSource(
    "raml,true",
    "openapi,true",
    "asyncapi,true",
    "composed,true",
    "raml,false",
    "openapi,false",
    "asyncapi,false",
    "composed,false",
  )
  fun `open model fields round trip by default and can be discarded`(
    frontend: String,
    preserve: Boolean,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val renderer = if (preserve) PythonModelRenderer("test_api") else PythonModelRenderer("test_api", false)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          renderer.renderModels(openModelFieldsApi(frontend, directory).models),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          import json
          from pydantic import ValidationError
          from test_api.models import Notice, ExtendedRecord, ClosedChild, TypedRecord
          preserve = ${if (preserve) "True" else "False"}
          data = json.loads('$openModelWire')
          for create in [Notice.model_validate, lambda value: Notice(**value)]:
              model = create(data)
              output = model.model_dump(mode="json", by_alias=True, exclude_unset=True)
              assert output["data"]["additionalProperties"] == "declared"
              if preserve:
                  assert output == data, output
                  assert model.model_copy().model_dump(mode="json", by_alias=True, exclude_unset=True) == data
              else:
                  assert "future" not in output and "future" not in output["data"] and "nested" not in output["data"]
          inherited = ExtendedRecord.model_validate({"id":"one","kind":"extended","future":None})
          assert ("future" in inherited.model_dump()) == preserve
          for cls, value in [(ClosedChild, {"id":"one","name":"name","future":1}), (TypedRecord, {"id":"one","future":"wrong"})]:
              try:
                  cls.model_validate(value)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("dynamic contract was not validated")
          """.trimIndent(),
      ),
    )
  }
}
