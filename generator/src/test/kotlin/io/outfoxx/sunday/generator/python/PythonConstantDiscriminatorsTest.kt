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
import io.outfoxx.sunday.generator.tools.constantDiscriminatorsApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Tag("models")
class PythonConstantDiscriminatorsTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `constant tags are constructed implicitly and decoded strictly`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = constantDiscriminatorsApi(directory, frontend, receiverField = true)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModelRenderer("test_api").renderModels(api.models),
          PythonModuleBuilder("test_api/__init__.py").build(),
          PythonModuleBuilder("test_api/consumer.py")
            .addCode(
              PythonCodeBlock.of(
                "from .models import BeatExtent\n\nvalue: BeatExtent = BeatExtent(self=\"mine\")\n" +
                  "explicit_tag = BeatExtent(kind=\"beat\")  # type: ignore[call-arg]\n",
              ),
            ).build(),
        ),
        importModules = listOf("test_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from test_api.models import BeatExtent, OnlyConstants
          assert OnlyConstants().model_dump(by_alias=True, exclude_unset=True) == {'tag-value': 'fixed', 'zero': 0, 'flag': False, 'optional': 'fixed'}
          assert BeatExtent().model_dump(by_alias=True, exclude_none=True) == {'kind': 'beat'}
          assert BeatExtent.model_validate({'kind': 'beat'}).kind == 'beat'
          assert BeatExtent().model_dump(exclude_unset=True) == {'kind': 'beat'}
          try:
              BeatExtent().kind = 'time'
              raise AssertionError('constant was mutable')
          except ValidationError:
              pass
          try:
              BeatExtent.model_validate({'kind': 'x'})
              raise AssertionError('invalid constant accepted')
          except ValidationError:
              pass
          """.trimIndent(),
      ),
    )
  }
}
