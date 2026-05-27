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
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PythonModuleBuilderTest : PythonTest() {

  @Test
  fun `renders imports from referenced symbols and verifies generated package`(compiler: PythonCompiler) {
    val initModule =
      PythonModuleBuilder("turnpost_api/__init__.py")
        .build()
    val modelModule =
      PythonModuleBuilder("turnpost_api/models.py")
        .addExport("Project")
        .addCode(
          PythonCodeBlock.of(
            """
            class Project(%T):
                model_config = %T(populate_by_name=True)

                project_id: str = %T(alias=%S)
                name: str
            """.trimIndent(),
            PythonSymbol("pydantic", "BaseModel"),
            PythonSymbol("pydantic", "ConfigDict"),
            PythonSymbol("pydantic", "Field"),
            "project-id",
          ),
        ).build()

    assertTrue(compileModules(compiler, listOf(initModule, modelModule), importModules = listOf("turnpost_api.models")))

    val compiledSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertEquals(modelModule.source, compiledSource)
    assertPythonSnapshot("PythonModuleBuilderTest/models.py", compiledSource)
  }
}
