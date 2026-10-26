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
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.endpointAuthentication
import io.outfoxx.sunday.generator.ir.emit.endpointSecurityPolicy
import io.outfoxx.sunday.generator.ir.emit.endpointSecuritySchemes
import io.outfoxx.sunday.generator.ir.emit.requireNoUnsupportedPolicies
import io.outfoxx.sunday.generator.ir.emit.validateSchemaProblemResponses
import io.outfoxx.sunday.generator.ir.orInvocation
import io.outfoxx.sunday.generator.ir.resolveProblemUris
import io.outfoxx.sunday.generator.outputOrigin
import io.outfoxx.sunday.generator.requireBrokerServicesSupported

/** Generates Python Litestar server modules from generated IR. */
class PythonLitestarIrGenerator(
  sourceApi: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  private val api =
    io.outfoxx.sunday.generator.ir
      .GeneratedPlacementResolver(
        sourceApi.placementConfiguration.orInvocation(options.placement),
        generationLanguage = "python",
        generationRole = "server",
      ).resolve(sourceApi)
      .resolveProblemUris(options.defaultProblemBaseUri)

  /** Generates the modules for the requested type categories. */
  fun generateModules(selection: GeneratedOutputSelection = GeneratedOutputSelection()): List<PythonModule> {
    GeneratedApiIndex(api).validateSchemaProblemResponses()
    options.requireBrokerServicesSupported("Python/Litestar")
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
      services.requireNoUnsupportedPolicies(options.generationContext(GenerationMode.Server), "Python/Litestar")
      val litestarRenderer = PythonLitestarRenderer(packageName, api)
      if (options.enforceSecuritySchemes && includeShared) {
        modules += renderSecurity(packageName, services)
      }
      modules +=
        services.mapNotNull { service ->
          val authentication =
            if (options.enforceEndpointSecurity || options.enforceSecuritySchemes) {
              service.operations.associate { operation ->
                operation.id to api.endpointAuthentication(service, operation)
              }
            } else {
              emptyMap()
            }
          litestarRenderer
            .renderService(
              service,
              authentication,
              if (options.enforceSecuritySchemes) {
                service.operations.associate {
                  it.id to
                    api.endpointSecurityPolicy(service, it, options.generationContext(GenerationMode.Server))
                }
              } else {
                null
              },
            ).takeIf { selected(service) }
        }
      if (options.aggregateServices && services.size > 1 && includeShared) {
        modules += renderAggregate(packageName, services)
      }
    }

    return modules.withPackageInitializers()
  }

  private fun renderSecurity(
    packageName: String,
    services: List<GeneratedService>,
  ): PythonModule {
    val policies =
      services
        .flatMap { service ->
          service.operations.mapNotNull { operation ->
            api.endpointSecurityPolicy(service, operation, options.generationContext(GenerationMode.Server))?.let {
              (
                service.name +
                  "." +
                  operation.id
              ) to
                it
            }
          }
        }.groupBy({ it.first }, { it.second })
        .mapValues { (name, policies) ->
          policies.singleOrNull() ?: genError("Duplicate security policy key '$name'")
        }
    return PythonSecurityRenderer(packageName).render(policies.values.endpointSecuritySchemes(), policies)
  }

  private fun renderAggregate(
    packageName: String,
    services: List<GeneratedService>,
  ): PythonModule {
    val module = PythonModuleBuilder("$packageName/api_server.py")
    val aggregateName = options.aggregateServiceName?.pythonIdentifierName ?: api.aggregateIdentifierName
    val routerFactoryName = "create_${aggregateName}_router"

    val names = if (options.enforceSecuritySchemes) mutableSetOf("security") else mutableSetOf()
    val parameters =
      services.associateWith { service ->
        var name = service.pythonServiceIdentifierName
        while (!names.add(name)) name += "_"
        name
      }
    module.addExport(routerFactoryName)
    module.addCode(
      PythonCodeBlock.of(
        """
        def %L(
        %C
        ) -> %T:
            ${"\"\"\"Create an aggregate Litestar router for all generated service routers.\n\n            Configure Litestar with SundayPlugin() for alias-aware models and RFC problem responses.\n            \"\"\""}
        %C    return %T(
                path="/",
                route_handlers=[
        %C
                ],
            )
        """.trimIndent(),
        routerFactoryName,
        PythonCodeBlock.join(
          services.map { service -> service.renderAggregateParameter(parameters.getValue(service)) } +
            if (options.enforceSecuritySchemes) {
              listOf(
                PythonCodeBlock.of("    *,\n    security: %T,", PythonSecurityRenderer(packageName).securityType),
              )
            } else {
              emptyList()
            },
          separator = "\n",
        ),
        PythonSymbol("litestar", "Router"),
        if (options.enforceSecuritySchemes) {
          PythonCodeBlock.of(
            "    _sunday_security = security\n",
          )
        } else {
          PythonCodeBlock.of("")
        },
        PythonSymbol("litestar", "Router"),
        PythonCodeBlock.join(
          services.map { service -> service.renderAggregateRouteHandler(parameters.getValue(service)) },
          separator = "\n",
        ),
      ),
    )

    return module.build()
  }

  private fun GeneratedService.renderAggregateParameter(parameterName: String): PythonCodeBlock =
    PythonCodeBlock.of(
      "    %L: %T,",
      parameterName,
      PythonSymbol(
        pythonImportModule(api.pythonPackageName(), server = true),
        "${pythonServiceBaseName.pythonTypeName}Service",
      ),
    )

  private fun GeneratedService.renderAggregateRouteHandler(parameterName: String): PythonCodeBlock =
    PythonCodeBlock.of(
      "            %T(%L%C),",
      PythonSymbol(
        pythonImportModule(api.pythonPackageName(), server = true),
        pythonServiceRouterFactoryName,
      ),
      parameterName,
      if (options.enforceSecuritySchemes) PythonCodeBlock.of(", security=_sunday_security") else PythonCodeBlock.of(""),
    )
}
