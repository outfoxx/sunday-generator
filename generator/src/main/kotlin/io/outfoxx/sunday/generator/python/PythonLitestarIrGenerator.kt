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
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.endpointAuthentication
import io.outfoxx.sunday.generator.ir.emit.endpointSecurityPolicy
import io.outfoxx.sunday.generator.ir.emit.endpointSecuritySchemes
import io.outfoxx.sunday.generator.requireBrokerServicesSupported

/** Generates Python Litestar server modules from generated IR. */
class PythonLitestarIrGenerator(
  private val api: GeneratedApi,
  private val options: PythonGeneratorOptions = PythonGeneratorOptions(),
) {

  /** Generates the modules for the requested type categories. */
  fun generateModules(outputCategories: Set<GeneratedTypeCategory>): List<PythonModule> {
    options.requireBrokerServicesSupported("Python/Litestar")
    val packageName = api.pythonPackageName(options)
    val services = api.pythonHttpServices()
    val modules = mutableListOf(PythonModuleBuilder("$packageName/__init__.py").build())

    if (GeneratedTypeCategory.Model in outputCategories) {
      modules += PythonModelRenderer(packageName).renderModels(api.models)
      modules += PythonProblemRenderer(packageName).renderProblems(api.problems)
    }

    if (GeneratedTypeCategory.Service in outputCategories) {
      val litestarRenderer = PythonLitestarRenderer(packageName, api)
      if (options.enforceSecuritySchemes) {
        modules += renderSecurity(packageName, services)
      }
      modules +=
        services.map { service ->
          val authentication =
            if (options.enforceEndpointSecurity || options.enforceSecuritySchemes) {
              service.operations.associate { operation ->
                operation.id to api.endpointAuthentication(service, operation)
              }
            } else {
              emptyMap()
            }
          litestarRenderer.renderService(
            service,
            authentication,
            if (options.enforceSecuritySchemes) {
              service.operations.associate { it.id to api.endpointSecurityPolicy(service, it) }
            } else {
              null
            },
          )
        }
      if (options.aggregateServices && services.size > 1) {
        modules += renderAggregate(packageName, services)
      }
    }

    return modules
  }

  private fun renderSecurity(
    packageName: String,
    services: List<GeneratedService>,
  ): PythonModule {
    val policies =
      services
        .flatMap { service ->
          service.operations.mapNotNull { operation ->
            api.endpointSecurityPolicy(service, operation)?.let { (service.name + "." + operation.id) to it }
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
        ".$pythonServiceServerModuleName",
        "${pythonServiceBaseName.pythonTypeName}Service",
      ),
    )

  private fun GeneratedService.renderAggregateRouteHandler(parameterName: String): PythonCodeBlock =
    PythonCodeBlock.of(
      "            %T(%L%C),",
      PythonSymbol(
        ".$pythonServiceServerModuleName",
        pythonServiceRouterFactoryName,
      ),
      parameterName,
      if (options.enforceSecuritySchemes) PythonCodeBlock.of(", security=_sunday_security") else PythonCodeBlock.of(""),
    )
}
