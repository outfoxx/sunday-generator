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

package io.outfoxx.sunday.generator

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import java.net.URI

/** Tracks output provenance independently of the complete type registry. */
class GeneratedOutputOwnership {
  private val origins = mutableMapOf<Any, GeneratedOutputOrigin>()
  private val sharedOrigins = mutableMapOf<Any, List<GeneratedOutputOrigin>>()
  private val declarations = mutableMapOf<Any, MutableSet<String>>()

  /** Enclosing declaration context while constructing companion output. */
  var current: GeneratedOutputOrigin? = null

  /** Associates a generated symbol with its source declaration. */
  fun bind(
    symbol: Any,
    origin: GeneratedOutputOrigin,
  ) {
    declarations.getOrPut(symbol) { linkedSetOf() }.addAll(origin.declarationUris)
    origins[symbol] = origin
  }

  /** Retains shared support whenever any consuming declaration is selected, including its enclosing owners. */
  fun bindShared(
    symbol: Any,
    consumers: List<GeneratedOutputOrigin>,
  ) {
    require(consumers.isNotEmpty()) { "Shared output must have at least one consumer" }
    sharedOrigins[symbol] = consumers
    consumers.forEach { declarations.getOrPut(symbol) { linkedSetOf() }.addAll(it.declarationUris) }
  }

  /** Records a symbol created inside a declaration's emission context. */
  fun register(
    symbol: Any,
    category: GeneratedTypeCategory,
  ) {
    declarations.getOrPut(symbol) { linkedSetOf() }.addAll(current?.declarationUris.orEmpty())
    origins.putIfAbsent(
      symbol,
      current ?: GeneratedOutputOrigin(category, emptySet()),
    )
  }

  /** Describes every original declaration contributing to a colliding generated symbol. */
  fun declarationDescription(symbol: Any): String = declarations[symbol].orEmpty().joinToString()

  /** Selects a declaration without removing it from the registry. */
  fun includes(
    selection: GeneratedOutputSelection,
    symbol: Any,
  ): Boolean {
    sharedOrigins[symbol]?.let { consumers -> return consumers.any(selection::includes) }
    val origin = requireNotNull(origins[symbol]) { "Missing output provenance for $symbol" }
    return selection.includes(origin)
  }

  /** Runs generation with inherited provenance, restoring the enclosing context on failure. */
  inline fun <T> within(
    origin: GeneratedOutputOrigin,
    action: () -> T,
  ): T {
    val previous = current
    current = origin
    try {
      return action()
    } finally {
      current = previous
    }
  }
}

/** Resolves model provenance without changing naming or reference resolution. */
fun GeneratedModel.outputOrigin(api: GeneratedApi): GeneratedOutputOrigin =
  outputOrigin(api) { _, category -> category }

// Backends classify some model declarations as problems. Apply that classification to ancestors too.
internal fun GeneratedModel.outputOrigin(
  api: GeneratedApi,
  classify: (GeneratedModel, GeneratedTypeCategory) -> GeneratedTypeCategory,
): GeneratedOutputOrigin {
  val origin =
    owningService(api)?.outputOrigin(api)?.copy(declarationUris = setOfNotNull(declarationUri))
      ?: GeneratedOutputOrigin(
        GeneratedTypeCategory.Model,
        ownerUris.ifEmpty { setOf(source.location) }.map(::URI).toSet(),
        declarationUris = setOf(declarationUri ?: source.location),
      )
  val parent =
    nested?.enclosedIn?.let { reference ->
      requireNotNull(
        io.outfoxx.sunday.generator.ir.emit
          .GeneratedModelIndex(api.models)
          .modelOrNull(reference),
      ) {
        "Unknown enclosing declaration '${reference.name}' for '$name'"
      }
    }
  val enclosing = parent?.outputOrigin(api, classify)
  // A backend may hoist an operation model into model output; its nested declarations must follow it.
  val category =
    if (origin.category == GeneratedTypeCategory.Service && enclosing?.category == GeneratedTypeCategory.Model) {
      GeneratedTypeCategory.Model
    } else {
      origin.category
    }
  return origin.copy(category = classify(this, category), enclosing = enclosing)
}

/** Resolves the provenance of an explicitly declared problem. */
fun GeneratedProblem.outputOrigin(api: GeneratedApi): GeneratedOutputOrigin =
  GeneratedOutputOrigin(
    GeneratedTypeCategory.Problem,
    setOf(URI(source.location)),
    setOf(
      declarationUri ?: source.location,
    ),
  )

/** A composed service belongs to every document contributing to it. */
fun GeneratedService.outputOrigin(api: GeneratedApi): GeneratedOutputOrigin =
  GeneratedOutputOrigin(
    GeneratedTypeCategory.Service,
    ownerUris.ifEmpty { setOf(api.source.location) }.map(::URI).toSet(),
    declarationUris = declarationUris + operations.flatMap { it.declarationUris() },
  )

/** Invocation support declarations belong to the services they support. */
fun GeneratedApi.outputOrigin(): GeneratedOutputOrigin =
  GeneratedOutputOrigin(
    GeneratedTypeCategory.Service,
    services
      .flatMap { it.ownerUris }
      .toSet()
      .ifEmpty { setOf(source.location) }
      .map(::URI)
      .toSet(),
  )

/** Finds lexical ownership without following references to otherwise shared declarations. */
private fun GeneratedModel.owningService(api: GeneratedApi): GeneratedService? {
  scope?.service?.let { name -> return api.services.firstOrNull { it.name == name } }
  val declaration = declarationUri?.let(::URI) ?: return null

  fun owns(location: String?): Boolean {
    val owner = location?.let(::URI) ?: return false
    return owner.scheme == declaration.scheme &&
      owner.rawAuthority == declaration.rawAuthority &&
      owner.path == declaration.path &&
      owner.rawQuery == declaration.rawQuery &&
      owner.fragment != null &&
      declaration.fragment?.startsWith("${owner.fragment}/") == true
  }
  val enclosing =
    api.models
      .filter {
        it.scope?.service != null && owns(it.declarationUri)
      }.maxByOrNull { it.declarationUri!!.length }
  return api.services.firstOrNull { service ->
    service.name == enclosing?.scope?.service ||
      service.operations.any { operation -> operation.declarationUris().any(::owns) }
  }
}
