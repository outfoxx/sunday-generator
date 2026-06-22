/*
 * Copyright 2020 Outfox, Inc.
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

/**
 * Composes source-produced IR fragments into one coherent API.
 */
class GeneratedApiComposer {

  /** Compose source fragments that share one Sunday API identity. */
  fun compose(fragments: List<GeneratedApiFragment>): GeneratedApi {
    if (fragments.isEmpty()) {
      throw GeneratedApiCompositionException("At least one GeneratedApiFragment is required")
    }

    val first = fragments.first()
    fragments.drop(1).forEach { fragment ->
      if (fragment.apiId.id != first.apiId.id) {
        throw GeneratedApiCompositionException(
          "Cannot compose API fragment '${fragment.api.name}' with api id '${fragment.apiId.id}' " +
            "into api id '${first.apiId.id}'. Add or align x-sunday-apiId or RAML (sunday.apiId).",
        )
      }
    }

    val services = linkedMapOf<String, ServiceState>()
    val models = linkedMapOf<String, ModelState>()
    val problems = linkedMapOf<String, ProblemState>()
    val tags = linkedMapOf<String, GeneratedTag>()
    val targets = linkedMapOf<String, GeneratedTarget>()
    val asyncEventStreamPaths =
      fragments
        .flatMap { fragment -> fragment.api.services.flatMap { service -> service.operations } }
        .filter { operation -> operation.isAsyncEventStreamOperation() }
        .map { operation -> operation.path }
        .toSet()

    fragments.forEach { fragment ->
      fragment.api.services.forEach { service ->
        addService(services, fragment, service, asyncEventStreamPaths)
      }
      fragment.api.models.forEach { model ->
        addModel(models, fragment, model)
      }
      fragment.api.problems.forEach { problem ->
        addProblem(problems, fragment, problem)
      }
      fragment.api.tags.forEach { tag ->
        tags.putIfAbsent(tag.name, tag)
      }
      fragment.api.targets.forEach { (targetId, target) ->
        targets.putIfAbsent(targetId, target)
      }
    }

    return first.api.copy(
      services = services.values.map { it.service }.filter { service -> service.operations.isNotEmpty() },
      models = models.values.map { it.model },
      problems = problems.values.map { it.problem },
      auth = fragments.firstNotNullOfOrNull { it.api.auth },
      jaxrs = fragments.firstNotNullOfOrNull { it.api.jaxrs },
      protocol = fragments.firstNotNullOfOrNull { it.api.protocol },
      media = fragments.firstNotNullOfOrNull { it.api.media },
      targets = targets,
      tags = tags.values.toList(),
      documentation = fragments.firstNotNullOfOrNull { it.api.documentation },
    )
  }

  private fun addService(
    services: MutableMap<String, ServiceState>,
    fragment: GeneratedApiFragment,
    service: GeneratedService,
    asyncEventStreamPaths: Set<String>,
  ) {
    val identity = fragment.serviceIdentity(service)
    val existing = services[identity.id]
    if (existing == null) {
      services[identity.id] =
        ServiceState(
          service = service.copy(operations = listOf()),
          identity = identity,
        ).also { state ->
          service.operations.forEach { operation ->
            addOperation(state, fragment, service, operation, asyncEventStreamPaths)
          }
        }
      return
    }

    val mergedService = existing.service.mergeMetadata(service)
    existing.service = mergedService
    service.operations.forEach { operation ->
      addOperation(existing, fragment, service, operation, asyncEventStreamPaths)
    }
  }

  private fun addOperation(
    serviceState: ServiceState,
    fragment: GeneratedApiFragment,
    service: GeneratedService,
    operation: GeneratedOperation,
    asyncEventStreamPaths: Set<String>,
  ) {
    if (fragment.api.source.kind == GeneratedSourceSpec.Kind.OPENAPI &&
      operation.isEventStreamFramingPlaceholder(asyncEventStreamPaths)
    ) {
      return
    }

    val identity = fragment.operationIdentity(service, operation)
    val existing = serviceState.operations[identity.id]
    if (existing == null) {
      serviceState.operations[identity.id] = OperationState(operation, identity)
      serviceState.service = serviceState.service.copy(operations = serviceState.service.operations + operation)
      return
    }

    if (existing.operation == operation) {
      return
    }

    throw GeneratedApiCompositionException(
      "Operation identity collision '${identity.id}' in service '${serviceState.service.name}'. " +
        "Add x-sunday-operationId to disambiguate default-derived operations.",
    )
  }

  private fun addModel(
    models: MutableMap<String, ModelState>,
    fragment: GeneratedApiFragment,
    model: GeneratedModel,
  ) {
    val identity = fragment.modelIdentity(model)
    val existing = models[identity.id]
    if (existing == null) {
      models[identity.id] = ModelState(model, identity, fragment.modelSource(model))
      return
    }

    if (existing.model.name == model.name && existing.model.compositionSignature() == model.compositionSignature()) {
      return
    }

    throw GeneratedApiCompositionException(
      "Model identity collision '${identity.id}' for model '${model.name}' between " +
        "${existing.source.description()} and ${fragment.modelSource(model).description()}. " +
        "Add x-sunday-modelName to disambiguate default-derived models.",
    )
  }

  private fun addProblem(
    problems: MutableMap<String, ProblemState>,
    fragment: GeneratedApiFragment,
    problem: GeneratedProblem,
  ) {
    val identity = fragment.problemIdentity(problem)
    val existing = problems[identity.id]
    if (existing == null) {
      problems[identity.id] = ProblemState(problem, identity)
      return
    }

    if (existing.problem == problem) {
      return
    }

    throw GeneratedApiCompositionException(
      "Problem identity collision '${identity.id}' for problem '${problem.name}'. " +
        "Add x-sunday-problemTypes or rename the source problem to disambiguate it.",
    )
  }

  private fun GeneratedApiFragment.serviceIdentity(service: GeneratedService): GeneratedIdentity =
    serviceIdentities[service.name] ?: GeneratedIdentity.native(service.group ?: service.name)

  private fun GeneratedApiFragment.operationIdentity(
    service: GeneratedService,
    operation: GeneratedOperation,
  ): GeneratedIdentity =
    operationIdentities[GeneratedOperationIdentityKey(service.name, operation.id)]
      ?: GeneratedIdentity.native(operation.id)

  private fun GeneratedApiFragment.modelIdentity(model: GeneratedModel): GeneratedIdentity =
    model.scope
      ?.let { scope -> GeneratedIdentity.native(model.scopedModelIdentity(scope)) }
      ?: modelIdentities[model.name]
      ?: GeneratedIdentity.native(model.name)

  private fun GeneratedApiFragment.modelSource(model: GeneratedModel): GeneratedSourceSpec = model.source ?: api.source

  private fun GeneratedSourceSpec.description(): String = "${kind.name.lowercase()} '$location'"

  private fun GeneratedModel.compositionSignature(): GeneratedModel =
    copy(
      source = null,
      properties = properties.map { property -> property.compositionSignature() },
      aliases = aliases.map { alias -> alias.compositionSignature() },
      additionalProperties = additionalProperties?.compositionSignature(),
      patternProperties = patternProperties.map { patternProperty -> patternProperty.compositionSignature() },
      targets = targets.mapValues { (_, target) -> target.compositionSignature() },
      nested = nested?.compositionSignature(),
      inherits = inherits.map { inherited -> inherited.compositionSignature() },
      discriminatorMappings = discriminatorMappings.mapValues { (_, type) -> type.compositionSignature() },
      examples = listOf(),
      documentation = null,
    )

  private fun GeneratedModelProperty.compositionSignature(): GeneratedModelProperty =
    copy(
      type = type.compositionSignature(),
      targets = targets.mapValues { (_, target) -> target.compositionSignature() },
      examples = listOf(),
      documentation = null,
    )

  private fun GeneratedAdditionalProperties.compositionSignature(): GeneratedAdditionalProperties =
    copy(
      type = type?.compositionSignature(),
      documentation = null,
    )

  private fun GeneratedPatternProperty.compositionSignature(): GeneratedPatternProperty =
    copy(
      type = type.compositionSignature(),
      documentation = null,
    )

  private fun GeneratedNestedType.compositionSignature(): GeneratedNestedType =
    copy(enclosedIn = enclosedIn?.compositionSignature())

  private fun GeneratedTarget.compositionSignature(): GeneratedTarget = this

  private fun GeneratedTypeRef.compositionSignature(): GeneratedTypeRef =
    copy(
      arguments = arguments.map { argument -> argument.compositionSignature() },
      source = null,
    )

  private fun GeneratedModel.scopedModelIdentity(scope: GeneratedModelScope): String =
    listOf(
      scope.service.orEmpty(),
      scope.operation.orEmpty(),
      scope.securityScheme.orEmpty(),
      scope.usage.name,
      scope.name.orEmpty(),
      scope.status?.toString().orEmpty(),
      name,
    ).joinToString(":")

  private fun GeneratedApiFragment.problemIdentity(problem: GeneratedProblem): GeneratedIdentity =
    problemIdentities[problem.name] ?: GeneratedIdentity.native(
      listOfNotNull(
        problem.source?.location,
        problem.sourceName ?: problem.name,
      ).joinToString(":"),
    )

  private fun GeneratedOperation.isAsyncEventStreamOperation(): Boolean =
    method == "SUBSCRIBE" &&
      streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM &&
      path.startsWith("/")

  private fun GeneratedOperation.isEventStreamFramingPlaceholder(asyncEventStreamPaths: Set<String>): Boolean =
    method == "GET" &&
      path in asyncEventStreamPaths &&
      responses.any { response ->
        EVENT_STREAM_MEDIA_TYPE in response.mediaTypes &&
          response.type == GeneratedTypeRef.scalar("string")
      }

  private fun GeneratedService.mergeMetadata(other: GeneratedService): GeneratedService =
    copy(
      baseUri = baseUri ?: other.baseUri,
      baseUriParameters = baseUriParameters.ifEmpty { other.baseUriParameters },
      auth = auth ?: other.auth,
      jaxrs = jaxrs ?: other.jaxrs,
      protocol = protocol ?: other.protocol,
      media = media ?: other.media,
      documentation = documentation ?: other.documentation,
    )

  private data class ServiceState(
    var service: GeneratedService,
    val identity: GeneratedIdentity,
    val operations: MutableMap<String, OperationState> = linkedMapOf(),
  )

  private data class OperationState(
    val operation: GeneratedOperation,
    val identity: GeneratedIdentity,
  )

  private data class ModelState(
    val model: GeneratedModel,
    val identity: GeneratedIdentity,
    val source: GeneratedSourceSpec,
  )

  private data class ProblemState(
    val problem: GeneratedProblem,
    val identity: GeneratedIdentity,
  )

  private companion object {
    const val EVENT_STREAM_MEDIA_TYPE = "text/event-stream"
  }
}
