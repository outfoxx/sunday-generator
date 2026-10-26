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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.enumNamingApi
import io.outfoxx.sunday.generator.tools.enumNamingSnakeNames
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Tag("models")
class PythonEnumNamingTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `maps enum words and preserves strict and tolerant wire values`(
    source: String,
    compiler: PythonCompiler,
  ) {
    val api = enumNamingApi(source)
    val members = enumNamingSnakeNames.joinToString(", ") { "'$it'" }
    val values =
      api.models
        .single { it.name == "JobKind" }
        .values
        .joinToString(", ") { "'$it'" }
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModuleBuilder("naming/__init__.py").build(),
          PythonModelRenderer("naming").renderModels(api.models),
        ),
        importModules = listOf("naming.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter
          from naming.models import JobKind, TolerantKind
          for enum in (JobKind, TolerantKind):
              adapter = TypeAdapter(enum)
              assert list(enum.__members__) == [$members]
              for name, wire in zip([$members], [$values]):
                  member = getattr(enum, name)
                  assert adapter.validate_python(wire) is member
                  assert adapter.dump_python(member, mode='json') == wire
          unknown = TypeAdapter(TolerantKind).validate_python('future-value')
          assert unknown.name == 'unknown_value'
          assert TypeAdapter(TolerantKind).dump_python(unknown, mode='json') == 'future-value'
          try:
              JobKind('future-value')
              raise AssertionError('strict enum accepted an unknown value')
          except ValueError:
              pass
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `rejects acronym names that normalize to the same member`() {
    val original = enumNamingApi("openapi")
    val model = original.models.single { it.name == "JobKind" }.copy(values = listOf("HTTPServer", "http_server"))
    val api = original.copy(models = listOf(model))
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("naming").renderModels(api.models)
      }
    assertTrue(error.message!!.contains("'http_server' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `escapes Python keywords and digit leading members without changing wire values`(compiler: PythonCompiler) {
    val model =
      enumNamingApi("openapi")
        .models
        .single { it.name == "JobKind" }
        .copy(values = listOf("class", "from", "123ABC", "MRO", "NAME", "VALUE", "UNKNOWN"))
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModuleBuilder("naming/__init__.py").build(),
          PythonModelRenderer(
            "naming",
          ).renderModels(listOf(model, model.copy(name = "TolerantKind", unknownValue = "UNKNOWN"))),
        ),
        smokeCode =
          """
          from naming.models import JobKind
          assert JobKind.class_.value == 'class'
          assert JobKind.from_.value == 'from'
          assert JobKind._123_abc.value == '123ABC'
          assert JobKind.mro_.value == 'MRO'
          assert JobKind.name.value == 'NAME'
          assert JobKind.value.value == 'VALUE'
          from naming.models import TolerantKind
          for enum in (JobKind, TolerantKind):
              for member in enum:
                  assert enum(member.value) is member
          assert TolerantKind('future').name == 'unknown'
          """.trimIndent(),
      ),
    )
  }
}
