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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonIrGeneratorTest : PythonTest() {

  @Test
  fun `generates compileable aggregate httpx modules from IR`(compiler: PythonCompiler) {
    val generator =
      PythonHttpxIrGenerator(
        apiFixture(),
        PythonGeneratorOptions(
          aggregateServices = true,
          aggregateServiceName = "CraftAPI",
        ),
      )
    val modules = generator.generateModules(GeneratedTypeCategory.entries.toSet())

    assertThat(
      modules.map { module -> module.path },
      contains(
        "craft_api/__init__.py",
        "craft_api/models.py",
        "craft_api/problems.py",
        "craft_api/runtime.py",
        "craft_api/projects.py",
        "craft_api/users.py",
        "craft_api/api.py",
      ),
    )
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("craft_api.api"),
        smokeCode =
          """
          from craft_api.api import CraftAPI

          assert hasattr(CraftAPI, "__init__")
          """.trimIndent(),
      ),
    )
  }

  @Test
  fun `generates compileable aggregate Litestar modules from IR`(compiler: PythonCompiler) {
    val generator =
      PythonLitestarIrGenerator(
        apiFixture(),
        PythonGeneratorOptions(
          aggregateServices = true,
          aggregateServiceName = "CraftAPI",
        ),
      )
    val modules = generator.generateModules(GeneratedTypeCategory.entries.toSet())

    assertThat(
      modules.map { module -> module.path },
      contains(
        "craft_api/__init__.py",
        "craft_api/models.py",
        "craft_api/problems.py",
        "craft_api/projects_server.py",
        "craft_api/users_server.py",
        "craft_api/api_server.py",
      ),
    )
    assertTrue(
      compileModules(
        compiler,
        modules,
        importModules = listOf("craft_api.api_server"),
      ),
    )
  }

  private fun apiFixture(): GeneratedApi =
    GeneratedApi(
      name = "Craft API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "craft.yaml"),
      models =
        listOf(
          GeneratedModel(
            name = "ProjectView",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
          GeneratedModel(
            name = "UserView",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
        ),
      services =
        listOf(
          GeneratedService(
            name = "Projects",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getProject",
                  method = "GET",
                  path = "/projects/{projectId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "projectId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(status = 200, type = GeneratedTypeRef.named("ProjectView")),
                    ),
                ),
              ),
          ),
          GeneratedService(
            name = "Users",
            operations =
              listOf(
                GeneratedOperation(
                  id = "getUser",
                  method = "GET",
                  path = "/users/{userId}",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(status = 200, type = GeneratedTypeRef.named("UserView")),
                    ),
                ),
              ),
          ),
        ),
    )
}
