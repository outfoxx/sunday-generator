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

package io.outfoxx.sunday.generator.ir

/** Allocates companion names against the complete composition without renaming source declarations. */
internal object GeneratedPatchModelNames {

  /** Makes independently allocated companions share one name per ordinary model identity. */
  fun canonicalize(fragments: List<GeneratedApiFragment>): List<GeneratedApiFragment> {
    if (fragments.size < 2) return fragments
    val allocator =
      OpenApiNameAllocator(
        fragments.flatMap { fragment ->
          fragment.api.models
            .filter { it.patchOf == null }
            .map { it.name } + fragment.api.problems.map { it.name }
        },
      )
    val companions =
      fragments.flatMap { fragment ->
        fragment.api.models.mapNotNull { model ->
          val original = model.patchOf ?: return@mapNotNull null
          val declaration = fragment.api.models.single { it.name == original.name && it.scope == original.scope }
          fragment.modelIdentity(declaration).id to original.name
        }
      }
    // Input order and fragment-local suffixes must not change the generated public type names.
    val names =
      companions.groupBy({ it.first }, { it.second }).toSortedMap().mapValues { (_, originals) ->
        allocator.allocate("${originals.min()}Patch")
      }
    return fragments.map { fragment ->
      val renamed =
        fragment.api.models.filter { it.patchOf != null }.associate { model ->
          val original = requireNotNull(model.patchOf)
          val declaration = fragment.api.models.single { it.name == original.name && it.scope == original.scope }
          model.name to names.getValue(fragment.modelIdentity(declaration).id)
        }
      val changed = renamed.any { (before, after) -> before != after }
      if (renamed.isEmpty()) {
        fragment
      } else {
        fragment.copy(
          api = if (changed) References(renamed).rewrite(fragment.api) else fragment.api,
          modelIdentities =
            fragment.modelIdentities.filterKeys { it !in renamed } +
              renamed.values.associateWith { name ->
                GeneratedIdentity.native(name.replaceFirstChar { it.lowercase() })
              },
        )
      }
    }
  }

  private class References(
    private val names: Map<String, String>,
  ) {
    fun rewrite(api: GeneratedApi): GeneratedApi =
      api.copy(
        models = api.models.map(::model),
        services = api.services.map(::service),
        problems =
          api.problems.map { problem ->
            problem.copy(
              model = problem.model?.let(::type),
              fields = problem.fields.map(::field),
              payload = problem.payload?.let { it.copy(type = type(it.type), fields = it.fields.map(::field)) },
            )
          },
        auth = api.auth?.let(::auth),
      )

    private fun type(reference: GeneratedTypeRef): GeneratedTypeRef =
      reference.copy(
        name =
          if (reference.kind ==
            GeneratedTypeRef.Kind.NAMED
          ) {
            names[reference.name] ?: reference.name
          } else {
            reference.name
          },
        arguments = reference.arguments.map(::type),
      )

    private fun field(property: GeneratedModelProperty) = property.copy(type = type(property.type))

    private fun parameter(parameter: GeneratedParameter) = parameter.copy(type = type(parameter.type))

    private fun option(option: GeneratedPayloadOption) = option.copy(type = type(option.type))

    private fun model(model: GeneratedModel): GeneratedModel =
      model.copy(
        name = names[model.name] ?: model.name,
        properties = model.properties.map(::field),
        inherits = model.inherits.map(::type),
        aliases = model.aliases.map(::type),
        patchOf = model.patchOf?.let(::type),
        additionalProperties = model.additionalProperties?.let { it.copy(type = it.type?.let(::type)) },
        patternProperties = model.patternProperties.map { it.copy(type = type(it.type)) },
        discriminatorMappings = model.discriminatorMappings.mapValues { type(it.value) },
        nested = model.nested?.let { it.copy(enclosedIn = it.enclosedIn?.let(::type)) },
      )

    private fun service(service: GeneratedService): GeneratedService =
      service.copy(
        baseUriParameters = service.baseUriParameters.map(::parameter),
        operations = service.operations.map(::operation),
        auth = service.auth?.let(::auth),
      )

    private fun operation(operation: GeneratedOperation): GeneratedOperation =
      operation.copy(
        parameters = operation.parameters.map(::parameter),
        queryString = operation.queryString?.let(::type),
        requestBody =
          operation.requestBody?.let {
            it.copy(
              type = type(it.type),
              payloads = it.payloads.map(::option),
            )
          },
        responses =
          operation.responses.map { response ->
            response.copy(
              type = response.type?.let(::type),
              payloads = response.payloads.map(::option),
              headers = response.headers.map(::parameter),
            )
          },
        problems = operation.problems.map(::type),
        nullify = operation.nullify?.let { it.copy(problems = it.problems.map(::type)) },
        auth = operation.auth?.let(::auth),
      )

    private fun auth(auth: GeneratedAuth): GeneratedAuth =
      auth.copy(
        securitySchemes =
          auth.securitySchemes.map { scheme ->
            scheme.copy(
              headers = scheme.headers.map(::parameter),
              queryParameters = scheme.queryParameters.map(::parameter),
              cookieParameters = scheme.cookieParameters.map(::parameter),
              queryString = scheme.queryString?.let(::type),
            )
          },
      )
  }
}
