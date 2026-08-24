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
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.requireBrokerServicesSupported

/** Generates transport-neutral Python Sunday client modules from generated IR. */
class PythonSundayIrGenerator(
  private val api: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(listOf("application/json"))

  /** Generates the modules for the requested type categories. */
  fun generateModules(outputCategories: Set<GeneratedTypeCategory>): List<PythonModule> {
    options.requireBrokerServicesSupported("Python/Sunday")
    val packageName = api.pythonPackageName(options)
    val services = api.pythonHttpServices()
    val modules = mutableListOf(PythonModuleBuilder("$packageName/__init__.py").build())

    if (GeneratedTypeCategory.Model in outputCategories) {
      modules += PythonModelRenderer(packageName).renderModels(api.models)
      modules += PythonProblemRenderer(packageName).renderProblems(api.problems)
    }

    if (GeneratedTypeCategory.Service in outputCategories) {
      val clientRenderer =
        PythonClientRenderer(
          packageName,
          registerProblems = api.problems.isNotEmpty(),
          models = api.models,
          defaultMediaTypes = defaultMediaTypes,
        )
      modules += services.map(clientRenderer::renderService)
      if (options.aggregateServices && services.size > 1) {
        modules += renderAggregate(packageName, services, defaultMediaTypes)
      }
    }

    return modules
  }

  private fun renderAggregate(
    packageName: String,
    services: List<GeneratedService>,
    defaultMediaTypes: List<String>,
  ): PythonModule {
    val module = PythonModuleBuilder("$packageName/api.py")
    val className = options.aggregateServiceName?.pythonTypeName ?: api.aggregateTypeName
    val mediaSelection = services.aggregateMediaSelection(defaultMediaTypes)

    module.addExport(className)
    module.addCode(
      PythonCodeBlock.of(
        """
        class %L[TransportRequestT, TransportResponseT]:
            ${"\"\"\"Aggregate client for all generated service clients.\"\"\""}

            def __init__(
                self,
                transport: %T[TransportRequestT, TransportResponseT],
                *,
                default_content_types: %T[%T] = %C,
                default_accept_types: %T[%T] = %C,
            ) -> None:
                self.transport = transport
                self.default_content_types = tuple(default_content_types)
                self.default_accept_types = tuple(default_accept_types)
        %C
        """.trimIndent(),
        className,
        PythonSymbol("sunday", "Transport"),
        PythonSymbol("collections.abc", "Sequence"),
        PythonSymbol("sunday", "MediaType"),
        renderMediaTypes(mediaSelection.contentTypes),
        PythonSymbol("collections.abc", "Sequence"),
        PythonSymbol("sunday", "MediaType"),
        renderMediaTypes(mediaSelection.acceptTypes),
        PythonCodeBlock.join(
          services.map { service ->
            PythonCodeBlock.of(
              """
              |        self.%L = %T(
              |            transport,
              |            default_content_types=self.default_content_types,
              |            default_accept_types=self.default_accept_types,
              |        )
              """.trimMargin(),
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

  private fun List<GeneratedService>.aggregateMediaSelection(defaultMediaTypes: List<String>): GeneratedMediaSelection {
    val selections = map { service -> service.defaultMediaSelection(defaultMediaTypes) }
    val contentTypes = selections.flatMap { selection -> selection.contentTypes }.toSet()
    val acceptTypes = selections.flatMap { selection -> selection.acceptTypes }.toSet()
    return GeneratedMediaSelection(
      defaultMediaTypes.filter(contentTypes::contains),
      defaultMediaTypes.filter(acceptTypes::contains),
    )
  }

  private fun renderMediaTypes(mediaTypes: List<String>): PythonCodeBlock =
    if (mediaTypes.isEmpty()) {
      PythonCodeBlock.of("()")
    } else {
      PythonCodeBlock.of(
        "(%C,)",
        PythonCodeBlock.join(
          mediaTypes.map { mediaType ->
            PythonCodeBlock.of("%T(%S)", PythonSymbol("sunday", "MediaType"), mediaType)
          },
          separator = ", ",
        ),
      )
    }
}
