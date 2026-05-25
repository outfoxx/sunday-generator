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

import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonProblemRendererTest : PythonTest() {

  @Test
  fun `generates pydantic payloads and catchable problem exceptions from IR`(compiler: PythonCompiler) {
    val problemsModule =
      PythonProblemRenderer("turnpost_api")
        .renderProblems(
          listOf(
            GeneratedProblem(
              name = "ProjectNotFoundProblem",
              typeUri = "https://turnpost.example/problems/project-not-found",
              status = 404,
              title = "Project not found",
              fields =
                listOf(
                  GeneratedModelProperty(
                    "projectId",
                    GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "retryAfter",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    serializationName = "retry-after",
                  ),
                ),
            ),
          ),
        )
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, problemsModule),
        importModules = listOf("turnpost_api.problems"),
        smokeCode =
          """
          from datetime import datetime
          from turnpost_api.problems import Problem, ProjectNotFoundProblem, ProjectNotFoundProblemPayload

          payload = ProjectNotFoundProblemPayload.model_validate(
              {
                  "projectId": "4f76662f-dc50-41b8-bb15-0a097ace8515",
                  "detail": "Project was deleted",
                  "retry-after": "2026-05-24T13:45:00Z",
              },
          )
          problem = ProjectNotFoundProblem(payload)
          validated_problem = ProjectNotFoundProblem.model_validate({"projectId": "4f76662f-dc50-41b8-bb15-0a097ace8515"})

          assert isinstance(problem, Exception)
          assert isinstance(problem, Problem)
          assert isinstance(validated_problem, ProjectNotFoundProblem)
          assert problem.status == 404
          assert str(problem.project_id) == "4f76662f-dc50-41b8-bb15-0a097ace8515"
          assert isinstance(problem.retry_after, datetime)
          assert problem.model_dump(by_alias=True)["retry-after"].year == 2026

          try:
              raise problem
          except ProjectNotFoundProblem as caught:
              assert caught.detail == "Project was deleted"
              assert caught.type == "https://turnpost.example/problems/project-not-found"
          """.trimIndent(),
      ),
    )

    assertPythonSnapshot(
      "PythonProblemRendererTest/problems.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/problems.py"),
    )
  }
}
