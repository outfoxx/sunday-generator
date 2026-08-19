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
import io.outfoxx.sunday.generator.ir.GeneratedService

/** Generates Python Litestar server modules from generated IR. */
class PythonLitestarIrGenerator(
  private val api: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  /** Generates the modules for the requested type categories. */
  fun generateModules(outputCategories: Set<GeneratedTypeCategory>): List<PythonModule> {
    options.requireHttpOnly()
    val packageName = api.pythonPackageName(options)
    val services = api.pythonHttpServices()
    val modules = mutableListOf(PythonModuleBuilder("$packageName/__init__.py").build())

    if (GeneratedTypeCategory.Model in outputCategories) {
      modules += PythonModelRenderer(packageName).renderModels(api.models)
      modules += PythonProblemRenderer(packageName).renderProblems(api.problems)
    }

    if (GeneratedTypeCategory.Service in outputCategories) {
      val litestarRenderer = PythonLitestarRenderer(packageName)
      modules += services.map(litestarRenderer::renderService)
      if (options.aggregateServices && services.size > 1) {
        modules += renderAggregate(packageName, services)
      }
    }

    return modules
  }

  private fun renderAggregate(
    packageName: String,
    services: List<GeneratedService>,
  ): PythonModule {
    val module = PythonModuleBuilder("$packageName/api_server.py")
    val aggregateName = options.aggregateServiceName?.pythonIdentifierName ?: api.aggregateIdentifierName
    val routerFactoryName = "create_${aggregateName}_router"

    module.addExport(routerFactoryName)
    module.addCode(
      PythonCodeBlock.of(
        """
        def %L(
        %C
        ) -> %T:
            ${"\"\"\"Create an aggregate Litestar router for all generated service routers.\n\n            Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.\n            \"\"\""}
            return %T(
                path="/",
                route_handlers=[
        %C
                ],
            )
        """.trimIndent(),
        routerFactoryName,
        PythonCodeBlock.join(services.map { service -> service.renderAggregateParameter() }, separator = "\n"),
        PythonSymbol("litestar", "Router"),
        PythonSymbol("litestar", "Router"),
        PythonCodeBlock.join(services.map { service -> service.renderAggregateRouteHandler() }, separator = "\n"),
      ),
    )

    return module.build()
  }

  private fun GeneratedService.renderAggregateParameter(): PythonCodeBlock =
    PythonCodeBlock.of(
      "    %L: %T,",
      pythonServiceIdentifierName,
      PythonSymbol(
        ".$pythonServiceServerModuleName",
        "${pythonServiceBaseName.pythonTypeName}Service",
      ),
    )

  private fun GeneratedService.renderAggregateRouteHandler(): PythonCodeBlock =
    PythonCodeBlock.of(
      "            %T(%L),",
      PythonSymbol(
        ".$pythonServiceServerModuleName",
        pythonServiceRouterFactoryName,
      ),
      pythonServiceIdentifierName,
    )
}
