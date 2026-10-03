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

import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.ir.emit.normalizedSecurityScheme

/**
 * Composes source-produced IR fragments into one coherent API.
 */
class GeneratedApiComposer {

  /** Compose source fragments that share one Sunday API identity. */
  fun compose(fragments: List<GeneratedApiFragment>): GeneratedApi {
    if (fragments.isEmpty()) {
      throw GeneratedApiCompositionException("At least one GeneratedApiFragment is required")
    }

    val sources = captureAuthentication(canonicalizeSecuritySchemes(GeneratedPatchModelNames.canonicalize(fragments)))
    val first = sources.first()
    sources.drop(1).forEach { fragment ->
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
    val asyncEventStreams =
      sources
        .flatMap { fragment ->
          fragment.api.services.flatMap { service ->
            service.operations
              .filter { operation -> operation.isAsyncEventStreamOperation() }
              .map { operation -> EventStreamKey(fragment.serviceIdentity(service).id, operation.path) }
          }
        }.toSet()
    val eventStreamFramingOperations =
      sources
        .filter { fragment -> fragment.api.source.kind == GeneratedSourceSpec.Kind.OPENAPI }
        .flatMap { fragment ->
          fragment.api.services.flatMap { service ->
            service.operations
              .filter { operation -> operation.isEventStreamFramingPlaceholder() }
              .map { operation ->
                EventStreamKey(fragment.serviceIdentity(service).id, operation.path) to
                  operation.copy(auth = fragment.api.effectiveAuth(service, operation))
              }
          }
        }.filter { (key, _) -> key in asyncEventStreams }
        .toMap()

    sources.forEach { fragment ->
      fragment.api.services.forEach { service ->
        addService(services, fragment, service, asyncEventStreams, eventStreamFramingOperations)
      }
      fragment.api.models.forEach { model ->
        addModel(models, fragment, model)
      }
      fragment.api.problems.forEach { problem ->
        addProblem(problems, fragment, problem)
      }
      fragment.api.tags.forEach { tag ->
        val existing = tags[tag.name]
        if (existing?.policy != null && tag.policy != null && existing.policy != tag.policy) {
          throw GeneratedApiCompositionException("Conflicting policy definitions for tag '${tag.name}'")
        }
        tags[tag.name] = existing?.copy(policy = existing.policy ?: tag.policy) ?: tag
      }
      fragment.api.targets.forEach { (targetId, target) ->
        targets.putIfAbsent(targetId, target)
      }
    }

    return first.api.copy(
      services = services.values.map { it.service }.filter { service -> service.operations.isNotEmpty() },
      models = models.values.map { it.model },
      problems = problems.values.map { it.problem },
      auth = sources.firstNotNullOfOrNull { it.api.auth },
      jaxrs = sources.firstNotNullOfOrNull { it.api.jaxrs },
      protocol = sources.firstNotNullOfOrNull { it.api.protocol },
      media = sources.firstNotNullOfOrNull { it.api.media },
      targets = targets,
      tags = tags.values.toList(),
      documentation = sources.firstNotNullOfOrNull { it.api.documentation },
    )
  }

  private fun canonicalizeSecuritySchemes(fragments: List<GeneratedApiFragment>): List<GeneratedApiFragment> {
    val schemes =
      fragments
        .flatMap { fragment ->
          listOfNotNull(fragment.api.auth) +
            fragment.api.services.flatMap { service ->
              listOfNotNull(service.auth) + service.operations.mapNotNull { it.auth }
            }
        }.flatMap { it.securitySchemes }
        .groupBy { it.name }
        .mapValues { (name, definitions) ->
          val contracts =
            definitions
              .map {
                it.normalizedSecurityScheme(requireSupported = false).copy(documentation = null, bearerFormat = null)
              }.distinct()
          val formats = definitions.mapNotNull { it.bearerFormat }.distinct()
          if (contracts.size != 1 || formats.size > 1) {
            throw GeneratedApiCompositionException("Conflicting security scheme definitions for '$name'")
          }
          definitions.first().copy(
            bearerFormat = formats.singleOrNull(),
            documentation = definitions.firstNotNullOfOrNull { it.documentation },
          )
        }

    fun GeneratedAuth.canonical() = copy(securitySchemes = securitySchemes.map { schemes.getValue(it.name) })
    return fragments.map { fragment ->
      fragment.copy(
        api =
          fragment.api.copy(
            auth = fragment.api.auth?.canonical(),
            services =
              fragment.api.services.map { service ->
                service.copy(
                  auth = service.auth?.canonical(),
                  operations =
                    service.operations.map { operation ->
                      operation.copy(auth = operation.auth?.canonical())
                    },
                )
              },
          ),
      )
    }
  }

  private fun captureAuthentication(fragments: List<GeneratedApiFragment>): List<GeneratedApiFragment> {
    if (fragments.size == 1 ||
      fragments.none { fragment ->
        fragment.api.auth != null || fragment.api.services.any { it.auth != null }
      }
    ) {
      return fragments
    }
    val framingAuth =
      fragments
        .filter { it.api.source.kind == GeneratedSourceSpec.Kind.OPENAPI }
        .flatMap { fragment ->
          fragment.api.services.flatMap { service ->
            service.operations.filter { it.isEventStreamFramingPlaceholder() }.map { operation ->
              EventStreamKey(fragment.serviceIdentity(service).id, operation.path) to
                fragment.api.effectiveAuth(service, operation)
            }
          }
        }.groupBy({ it.first }, { it.second })
        .mapValues { (key, values) ->
          val definitions = values.filterNotNull().distinct()
          if (definitions.size >
            1
          ) {
            throw GeneratedApiCompositionException("Conflicting security definitions for event stream '${key.path}'")
          }
          definitions.singleOrNull()
        }
    return fragments.map { fragment ->
      fragment.copy(
        api =
          fragment.api.copy(
            services =
              fragment.api.services.map { service ->
                service.copy(
                  operations =
                    service.operations.map { operation ->
                      val inherited =
                        if (operation.isAsyncEventStreamOperation()) {
                          framingAuth[EventStreamKey(fragment.serviceIdentity(service).id, operation.path)]
                        } else {
                          null
                        }
                      operation.copy(
                        auth =
                          fragment.api.effectiveAuth(service, operation) ?: inherited
                            ?: GeneratedAuth(securityOverride = true),
                      )
                    },
                )
              },
          ),
      )
    }
  }

  private fun addService(
    services: MutableMap<String, ServiceState>,
    fragment: GeneratedApiFragment,
    service: GeneratedService,
    asyncEventStreams: Set<EventStreamKey>,
    eventStreamFramingOperations: Map<EventStreamKey, GeneratedOperation>,
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
            addOperation(state, fragment, service, operation, asyncEventStreams, eventStreamFramingOperations)
          }
        }
      return
    }

    val mergedService = existing.service.mergeMetadata(service)
    existing.service = mergedService
    service.operations.forEach { operation ->
      addOperation(existing, fragment, service, operation, asyncEventStreams, eventStreamFramingOperations)
    }
  }

  private fun addOperation(
    serviceState: ServiceState,
    fragment: GeneratedApiFragment,
    service: GeneratedService,
    operation: GeneratedOperation,
    asyncEventStreams: Set<EventStreamKey>,
    eventStreamFramingOperations: Map<EventStreamKey, GeneratedOperation>,
  ) {
    val eventStreamKey = EventStreamKey(fragment.serviceIdentity(service).id, operation.path)
    if (fragment.api.source.kind == GeneratedSourceSpec.Kind.OPENAPI &&
      operation.isEventStreamFramingPlaceholder() &&
      eventStreamKey in asyncEventStreams
    ) {
      return
    }

    val composedOperation =
      if (operation.isAsyncEventStreamOperation()) {
        val framing = eventStreamFramingOperations[eventStreamKey]
        if (framing?.policy != null && operation.policy != null && framing.policy != operation.policy) {
          throw GeneratedApiCompositionException("Conflicting policy definitions for event stream '${operation.path}'")
        }
        val auth = fragment.api.effectiveAuth(service, operation)
        if (framing?.auth != null && auth != null && framing.auth != auth) {
          throw GeneratedApiCompositionException(
            "Conflicting security definitions for event stream '${operation.path}'",
          )
        }
        operation.copy(
          auth = auth ?: framing?.auth,
          policy = operation.policy ?: framing?.policy,
          parameters =
            operation.parameters
              .mergeFramingParameters(framing?.parameters.orEmpty()),
        )
      } else {
        operation
      }

    val identity = fragment.operationIdentity(service, operation)
    val existing = serviceState.operations[identity.id]
    if (existing == null) {
      serviceState.operations[identity.id] = OperationState(composedOperation, identity)
      serviceState.service = serviceState.service.copy(operations = serviceState.service.operations + composedOperation)
      return
    }

    if (existing.operation == composedOperation) {
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

  private fun GeneratedApiFragment.modelSource(model: GeneratedModel): GeneratedSourceSpec = model.source ?: api.source

  private fun GeneratedSourceSpec.description(): String = "${kind.name.lowercase()} '$location'"

  private fun GeneratedModel.compositionSignature(): GeneratedModel =
    copy(
      source = null,
      properties = properties.map { property -> property.compositionSignature() },
      aliases = aliases.map { alias -> alias.compositionSignature() },
      patchOf = patchOf?.compositionSignature(),
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
      // Collection kind and arguments define maps; readers use different descriptive names.
      name = if (kind == GeneratedTypeRef.Kind.MAP) "map" else name,
      arguments = arguments.map { argument -> argument.compositionSignature() },
      source = null,
    )

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

  private fun GeneratedOperation.isEventStreamFramingPlaceholder(): Boolean =
    method == "GET" &&
      responses.any { response ->
        EVENT_STREAM_MEDIA_TYPE in response.mediaTypes &&
          response.type == GeneratedTypeRef.scalar("string")
      }

  private fun List<GeneratedParameter>.mergeFramingParameters(
    framingParameters: List<GeneratedParameter>,
  ): List<GeneratedParameter> =
    this +
      framingParameters.filterNot { framingParameter ->
        any { parameter -> parameter.hasSameWireIdentity(framingParameter) }
      }

  private fun GeneratedParameter.hasSameWireIdentity(other: GeneratedParameter): Boolean {
    if (location != other.location) {
      return false
    }

    val wireName = serializationName ?: name
    val otherWireName = other.serializationName ?: other.name
    return if (location == GeneratedParameter.Location.HEADER) {
      wireName.equals(otherWireName, ignoreCase = true)
    } else {
      wireName == otherWireName
    }
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

  private data class EventStreamKey(
    val serviceIdentity: String,
    val path: String,
  )

  private companion object {
    const val EVENT_STREAM_MEDIA_TYPE = "text/event-stream"
  }
}
