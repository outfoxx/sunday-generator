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

import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.inheritedAdditionalPropertiesModels
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.openModelWire
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@RequiresPythonRuntime(PythonRuntimeProfile.LITESTAR)
@Tag("models")
class PythonOpenModelFieldsTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("validation")
  fun `inherited additional constraints survive native revalidation`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = openModelFieldsApi(frontend, directory)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(source.models + inheritedAdditionalPropertiesModels()),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from test_api.models import DynamicChild, DynamicLeaf, DynamicHolder
          for model in (DynamicChild, DynamicLeaf):
              value = model.model_validate({"id": "one", "extra": [1, 2]})
              assert value.model_dump() == {"id": "one", "extra": [1, 2]}
              for invalid in ([1], [1, 2, 3, 4]):
                  try:
                      DynamicHolder.model_validate({"payload": {"id": "one", "extra": invalid}})
                  except ValidationError:
                      pass
                  else:
                      raise AssertionError("Inherited nested model treated as free-form")
                  for candidate in ({"id": "one", "extra": invalid}, value):
                      value.__pydantic_extra__["extra"] = invalid
                      try:
                          model.model_validate(candidate, strict=True)
                      except ValidationError:
                          pass
                      else:
                          raise AssertionError("Inherited additional constraint was lost")
          """.trimIndent(),
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("validation")
  fun `dynamic validation preserves native alias selection for existing models`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = openModelFieldsApi(frontend, directory)
    val model =
      GeneratedModel(
        "AliasedExtensions",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "projectId",
              GeneratedTypeRef.scalar("string"),
              required = true,
              serializationName = "wire-project-id",
              validation = mapOf("minLength" to "2"),
            ),
          ),
        additionalProperties =
          GeneratedAdditionalProperties(
            type = GeneratedTypeRef.scalar("integer"),
            validation = mapOf("minimum" to "1"),
          ),
      )
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(source.models + model),
          PythonModuleBuilder("test_api/__init__.py").build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from test_api.models import AliasedExtensions
          original = AliasedExtensions.model_validate({"wire-project-id": "valid", "count": 2})
          for aliases in (False, True):
              checked = AliasedExtensions.model_validate(original, strict=True, by_alias=aliases, by_name=not aliases)
              assert checked.project_id == "valid"
              assert checked.__pydantic_extra__ == {"count": 2}
              assert checked.model_fields_set == original.model_fields_set
              original.project_id = "x"
              try:
                  AliasedExtensions.model_validate(original, strict=True, by_alias=aliases, by_name=not aliases)
              except ValidationError as error:
                  assert error.errors()[0]["type"] == "string_too_short"
              else:
                  raise AssertionError("Existing aliased field escaped native validation")
              original.project_id = "valid"
              original.__pydantic_extra__["count"] = 0
              try:
                  AliasedExtensions.model_validate(original, strict=True, by_alias=aliases, by_name=not aliases)
              except ValidationError:
                  pass
              else:
                  raise AssertionError("Mutated extra field escaped native validation")
              original.__pydantic_extra__["count"] = 2
          """.trimIndent(),
      ),
    )
  }

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
          TypedRecord.model_validate({"id": "one", "future": 2})
          if ${if (frontend == "raml") "False" else "True"}:
              try:
                  TypedRecord.model_validate({"id": "one", "future": 3})
              except ValidationError:
                  pass
              else:
                  raise AssertionError("additional-property const was ignored")
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
