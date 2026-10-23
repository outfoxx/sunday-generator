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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.discriminatedPatchApi
import io.outfoxx.sunday.generator.tools.pythonPlacement
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Tag("models")
@Tag("validation")
@Tag("requests")
class PythonDiscriminatedPatchTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "explicit", "raml", "asyncapi", "composed", "renamed", "tolerant", "three-level"])
  fun `discriminated patches retain required tags`(
    frontend: String,
    @TempDir directory: Path,
    compiler: PythonCompiler,
  ) {
    val api = discriminatedPatchApi(frontend, directory)
    val tag = if (frontend == "renamed") "animal-kind" else "kind"
    val modules =
      PythonSundayIrGenerator(api, PythonGeneratorOptions(placement = pythonPlacement("test_api")))
        .generateModules(
          GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Problem)),
        )
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from test_api.models import Cat, CatPatch, DogPatch, AnimalPatch
          from pydantic import TypeAdapter
          adapter = TypeAdapter(AnimalPatch)
          for data in [{"$tag": "cat"}, {"$tag": "cat", "nickname": None}, {"$tag": "cat", "name": "Misty"}, {"$tag": "cat", "custom": "extension"}, {"$tag": "cat", "indoor": True}, {"$tag": "cat", "indoor": None}]:
              patch = adapter.validate_python(data)
              assert isinstance(patch, CatPatch)
              assert patch.model_dump(mode="json", by_alias=True) == data
          for data in [{}, {"$tag": None}, {"$tag": "lizard"}, {"$tag": "cat", "name": None}, {"$tag": "cat", "detail": None}, {"$tag": "cat", "trained": True}, {"$tag": "cat", "trained": None}]:
              try:
                  adapter.validate_python(data)
              except ValueError:
                  pass
              else:
                  raise AssertionError(data)
          if "$frontend" == "three-level":
              from test_api.models import MammalPatch
              assert isinstance(MammalPatch.model_validate({"$tag": "Mammal", "custom": "extension"}), MammalPatch)
              for value in [True, None]:
                  try:
                      MammalPatch.model_validate({"$tag": "Mammal", "indoor": value})
                  except ValueError:
                      pass
                  else:
                      raise AssertionError("parent accepted child field")
          base = Cat.model_validate({"$tag": "cat", "name": "Misty", "detail": None, "nickname": "Kit", "indoor": True})
          assert base.patch(from_model=base).model_dump(mode="json", by_alias=True) == {"$tag": "cat"}
          assert base.merge(CatPatch.model_validate({"$tag": "cat", "nickname": None})).model_dump(mode="json", by_alias=True) == {"$tag": "cat", "name": "Misty", "detail": None, "indoor": True}
          complete = Cat.model_validate({"$tag": "cat", "name": "Misty", "detail": "info"})
          assert CatPatch.from_model(complete).model_dump(mode="json", by_alias=True) == complete.model_dump(mode="json", by_alias=True)
          try:
              base.patch()
          except ValueError:
              pass
          else:
              raise AssertionError("required null snapshot")
          try:
              base.merge(DogPatch.model_validate({"$tag": "dog"}))
          except ValueError:
              pass
          else:
              raise AssertionError("cross subtype merge")
          """.trimIndent(),
      ),
    )
  }
}
