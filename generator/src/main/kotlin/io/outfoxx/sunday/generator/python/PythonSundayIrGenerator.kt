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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.clientConfigurations
import io.outfoxx.sunday.generator.ir.emit.clientConstructorSecurity
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.ir.emit.requireCompatibleAggregate
import io.outfoxx.sunday.generator.ir.emit.requireNoUnsupportedPolicies
import io.outfoxx.sunday.generator.ir.orInvocation
import io.outfoxx.sunday.generator.outputOrigin
import io.outfoxx.sunday.generator.requireBrokerServicesSupported

/** Generates transport-neutral Python Sunday client modules from generated IR. */
class PythonSundayIrGenerator(
  sourceApi: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  private val api =
    io.outfoxx.sunday.generator.ir
      .GeneratedPlacementResolver(
        sourceApi.placementConfiguration.orInvocation(options.placement),
        generationLanguage = "python",
        generationRole = "client",
      ).resolve(sourceApi)

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(listOf("application/json"))

  /** Generates the modules for the requested type categories. */
  fun generateModules(selection: GeneratedOutputSelection = GeneratedOutputSelection()): List<PythonModule> {
    options.requireBrokerServicesSupported("Python/Sunday")
    val packageName = api.pythonPackageName()
    val services = api.pythonHttpServices()

    fun selected(service: GeneratedService): Boolean {
      val origin = service.outputOrigin(api)
      return selection.includes(origin)
    }
    val sharedOrigin = api.outputOrigin()
    val includeShared = selection.includes(sharedOrigin)
    val modules = mutableListOf(PythonModuleBuilder("$packageName/__init__.py").build())

    val selectedModels =
      api.models
        .filter { model ->
          val origin = model.outputOrigin(api)
          selection.includes(origin)
        }.toSet()
    if (selectedModels.isNotEmpty() ||
      (
        selection.ownerUris.isEmpty() &&
          (selection.categories.isEmpty() || GeneratedTypeCategory.Model in selection.categories)
      )
    ) {
      modules +=
        if (selectedModels.isEmpty()) {
          listOf(
            PythonModelRenderer(
              packageName,
              options.preserveUnknownFields,
              options.defaultTolerance,
            ).renderModels(api.models) {
              false
            },
          )
        } else {
          renderPlacedModels(api.models, selectedModels, packageName, options)
        }
    }
    if (selection.categories.isEmpty() || GeneratedTypeCategory.Problem in selection.categories) {
      modules +=
        PythonSchemaProblemRenderer(packageName, api.models).render(
          api.models
            .filter {
              selection.includes(it.outputOrigin(api).copy(category = GeneratedTypeCategory.Problem))
            }.toSet(),
        )
    }
    val problems =
      api.problems.filter { problem ->
        val origin = problem.outputOrigin(api)
        selection.includes(origin)
      }
    if (problems.isNotEmpty() ||
      (
        selection.ownerUris.isEmpty() &&
          (selection.categories.isEmpty() || GeneratedTypeCategory.Problem in selection.categories)
      )
    ) {
      modules += renderPlacedProblems(problems, packageName)
    }

    if (services.isNotEmpty() &&
      (selection.categories.isEmpty() || GeneratedTypeCategory.Service in selection.categories)
    ) {
      services.requireNoUnsupportedPolicies(options.generationContext(GenerationMode.Client), "Python/Sunday")
      val clientRenderer =
        PythonClientRenderer(
          packageName,
          registerProblems = api.problems.isNotEmpty() || api.models.any(PythonProblemModels(api.models)::isProblem),
          problems = api.problems,
          models = api.models,
          defaultMediaTypes = defaultMediaTypes,
          profile = options.profile,
          configurationFactories = options.generateClientConfig,
          security = {
            service,
            operation,
            ->
            api.clientConstructorSecurity(
              service,
              operation,
              options.generationContext(GenerationMode.Client),
              options.generateClientConfig,
            )
          },
        )
      val configurations = if (options.generateClientConfig) api.clientConfigurations(services) else emptyList()
      val factories = PythonClientConfigurations(api, packageName, options.profile, defaultMediaTypes)
      if (configurations.isNotEmpty() && includeShared) modules += factories.configurations(configurations)
      modules +=
        services.mapNotNull { service ->
          factories
            .factories(
              clientRenderer.renderService(service),
              service,
              configurations,
            ).takeIf { selected(service) }
        }
      if (options.aggregateServices && services.size > 1 && includeShared) {
        if (options.generateClientConfig) configurations.requireCompatibleAggregate(services)
        val className = options.aggregateServiceName?.pythonTypeName ?: api.aggregateTypeName
        modules +=
          factories.factories(
            renderAggregate(packageName, services, defaultMediaTypes),
            services.first(),
            configurations,
            services,
            className,
            className,
            services.aggregateMediaSelection(defaultMediaTypes),
          )
      }
    }

    return modules.withPackageInitializers()
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
        %C
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
        if (options.generateClientConfig) {
          PythonCodeBlock.of(
            "        client_settings: %T | None = None,",
            PythonSymbol("sunday", "ClientSettings"),
          )
        } else {
          PythonCodeBlock.of("")
        },
        PythonCodeBlock.join(
          services.map { service ->
            PythonCodeBlock.of(
              """
              |        self.%L = %T(
              |            transport,
              |            default_content_types=self.default_content_types,
              |            default_accept_types=self.default_accept_types,
              |${if (options.generateClientConfig) "            client_settings=client_settings," else ""}
              |        )
              """.trimMargin(),
              service.pythonServiceIdentifierName,
              PythonSymbol(
                service.pythonImportModule(packageName),
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
