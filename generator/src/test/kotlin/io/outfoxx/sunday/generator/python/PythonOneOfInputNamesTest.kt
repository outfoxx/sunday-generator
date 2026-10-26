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
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.sunday.generator.tools.pythonPlacement
import io.outfoxx.sunday.test.extensions.PythonRuntimeProfile
import io.outfoxx.sunday.test.extensions.RequiresPythonRuntime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresPythonRuntime(PythonRuntimeProfile.HTTPX_LITESTAR)
@Tag("models")
@Tag("validation")
class PythonOneOfInputNamesTest : PythonTest() {
  @ParameterizedTest
  @ValueSource(strings = ["sunday", "litestar"])
  fun `oneOf required input checks honor python names and alias precedence`(
    target: String,
    compiler: PythonCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("oneof-inputs.yaml")
    source.writeText(
      OpenApiReferenceDocuments.document(
        "Union inputs",
        """
        Parent:
          type: object
          required: [projectId]
          properties:
            projectId: {type: string, minLength: 2}
        Project:
          allOf:
            - {${'$'}ref: '#/components/schemas/Parent'}
            - type: object
              required: [problemCode]
              properties:
                problemCode: {type: string, const: project}
        Tenant:
          type: object
          required: [tenantId]
          properties:
            tenantId: {type: integer, minimum: 1}
        Choice:
          oneOf: [{${'$'}ref: '#/components/schemas/Project'}, {${'$'}ref: '#/components/schemas/Tenant'}]
        Holder:
          type: object
          required: [projectChoice]
          properties:
            projectChoice: {${'$'}ref: '#/components/schemas/Choice'}
        """.trimIndent(),
      ),
    )
    val api = GeneratedApiIrExporter().export(source.toUri())
    val options = PythonGeneratorOptions(placement = pythonPlacement("test_api"))
    val modules =
      if (target == "sunday") {
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
          from pydantic import TypeAdapter, ValidationError
          from test_api.models import Choice, Holder, Project, Tenant
          adapter = TypeAdapter(Choice)
          def rejects(create, value):
              try:
                  create(value)
              except ValidationError:
                  return
              raise AssertionError(f"accepted invalid union input: {value}")
          accepted = [
              {"projectId": "ok", "problemCode": "project"},
              {"project_id": "ok", "problem_code": "project"},
              {"projectId": "ok", "problem_code": "project"},
              {"project_id": "ok", "problemCode": "project"},
              {"projectId": "ok", "project_id": None, "problemCode": "project"},
              {"projectId": "ok", "problemCode": "project", "problem_code": "wrong"},
          ]
          for wire in accepted:
              expected = Project.model_validate(wire)
              for decode in (adapter.validate_python, lambda value: adapter.validate_json(json.dumps(value))):
                  value = decode(wire)
                  assert type(value) is Project
                  assert value.project_id == expected.project_id == "ok"
                  assert value.problem_code == expected.problem_code == "project"
              for holder in (Holder(project_choice=wire), Holder.model_validate({"projectChoice": wire}),
                             Holder.model_validate_json(json.dumps({"projectChoice": wire}))):
                  assert type(holder.project_choice) is Project
                  assert holder.project_choice.project_id == "ok"
          for wire in [
              {}, {"projectId": "ok"}, {"project_id": "ok"}, {"problem_code": "project"},
              {"projectId": None, "project_id": "ok", "problemCode": "project"},
              {"projectId": "x", "project_id": "ok", "problemCode": "project"},
              {"projectId": "ok", "problemCode": "wrong", "problem_code": "project"},
              {"project_id": "ok", "problem_code": "project", "tenant_id": 1},
          ]:
              rejects(adapter.validate_python, wire)
              rejects(lambda value: adapter.validate_json(json.dumps(value)), wire)
              rejects(lambda value: Holder(project_choice=value), wire)
          for wire in ({"tenantId": 1}, {"tenant_id": 1}):
              assert type(adapter.validate_python(wire)) is Tenant
          constructed = Project(project_id="ok")
          assert adapter.validate_python(constructed).project_id == "ok"
          """.trimIndent(),
      ),
    )
  }
}
