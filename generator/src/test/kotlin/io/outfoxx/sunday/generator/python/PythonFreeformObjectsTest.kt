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
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@Tag("models")
class PythonFreeformObjectsTest : PythonTest() {
  @Test
  fun `inline and named freeform objects round trip`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/freeform-objects.yaml") uri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(uri)
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
          from typing import Any
          from pydantic import ValidationError
          from test_api.models import Document, Freeform
          assert Freeform.__value__ == dict[str, Any]
          value = {"name": "example", "enabled": True, "count": 3, "fraction": 1.5, "nullValue": None, "nested": {"value": None}, "items": [1, "two", None]}
          for field in ("properties", "named", "items"):
              original = {field: [value, {}] if field == "items" else value}
              decoded = Document.model_validate_json(json.dumps(original))
              field_value = getattr(decoded, field)
              assert isinstance(field_value[0] if field == "items" else field_value, dict)
              assert json.loads(decoded.model_dump_json(exclude_unset=True)) == original
              for invalid in ("{}", True, 3):
                  try:
                      Document.model_validate({field: [invalid] if field == "items" else invalid})
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError("Non-object accepted")
          for original in ({"child": {"id": "one", "enabled": True}}, {}, {"properties": {}, "named": {}, "items": []}):
              decoded = Document.model_validate(original)
              assert json.loads(decoded.model_dump_json(exclude_unset=True)) == original
          for field in ("properties", "named", "items"):
              try:
                  Document.model_validate({field: None})
              except ValidationError:
                  pass
              else:
                  raise AssertionError("Non-nullable field accepted null")
          """.trimIndent(),
      ),
    )
  }
}
