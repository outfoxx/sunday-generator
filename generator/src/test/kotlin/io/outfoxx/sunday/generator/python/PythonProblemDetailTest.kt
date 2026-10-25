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
import io.outfoxx.sunday.generator.tools.problemDetailApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@Tag("responses")
class PythonProblemDetailTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `problem details are optional defaults and can be overridden`(
    frontend: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val api = problemDetailApi(frontend, directory)
    assertTrue(
      compileModules(
        compiler,
        listOf(
          PythonModuleBuilder("test_api/__init__.py").build(),
          PythonProblemRenderer("test_api").renderProblems(api.problems),
        ),
        importModules = listOf("test_api.problems"),
        smokeCode =
          """
          from test_api.problems import WithDefaultProblem, WithoutDefaultProblem
          assert WithDefaultProblem().detail == "Default detail"
          assert WithoutDefaultProblem().detail is None
          assert WithDefaultProblem(detail="Override").detail == "Override"
          assert WithoutDefaultProblem(detail="Override").detail == "Override"
          assert WithDefaultProblem(detail="").detail == ""
          assert WithDefaultProblem(detail=None).detail is None
          """.trimIndent(),
      ),
    )
  }
}
