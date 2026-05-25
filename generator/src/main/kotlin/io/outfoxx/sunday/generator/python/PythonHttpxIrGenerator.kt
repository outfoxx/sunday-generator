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

/** Generates Python httpx client modules from generated IR. */
class PythonHttpxIrGenerator(
  private val api: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  /** Generates the modules for the requested type categories. */
  fun generateModules(outputCategories: Set<GeneratedTypeCategory>): List<PythonModule> {
    val packageName = api.pythonPackageName(options)
    val modules = mutableListOf(PythonModuleBuilder("$packageName/__init__.py").build())

    if (GeneratedTypeCategory.Model in outputCategories) {
      modules += PythonModelRenderer(packageName).renderModels(api.models)
      modules += PythonProblemRenderer(packageName).renderProblems(api.problems)
    }

    if (GeneratedTypeCategory.Service in outputCategories) {
      val clientRenderer = PythonClientRenderer(packageName)
      modules += clientRenderer.renderRuntime()
      modules += api.services.map(clientRenderer::renderService)
      if (options.aggregateServices && api.services.size > 1) {
        modules += renderAggregate(packageName)
      }
    }

    return modules
  }

  private fun renderAggregate(packageName: String): PythonModule {
    val module = PythonModuleBuilder("$packageName/api.py")
    val className = options.aggregateServiceName?.pythonTypeName ?: api.aggregateTypeName

    module.addExport(className)
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L:
            ${"\"\"\"Aggregate client for all generated service clients.\"\"\""}

            def __init__(self, transport: %T) -> None:
                self._transport = transport
        %C
        """.trimIndent(),
        className,
        PythonSymbol(".runtime", "Transport"),
        PythonCodeBlock.join(
          api.services.map { service ->
            PythonCodeBlock.of(
              "        self.%L = %T(transport)",
              service.pythonServiceIdentifierName,
              PythonSymbol(
                ".${service.pythonServiceModuleName}",
                "${service.pythonServiceBaseName.pythonTypeName}Client",
              ),
            )
          },
          separator = "\n",
        ),
      ),
    )

    return module.build()
  }
}
