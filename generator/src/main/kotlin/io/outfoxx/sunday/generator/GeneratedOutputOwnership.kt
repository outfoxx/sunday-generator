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

  /** Enclosing declaration context while constructing companion output. */
  var current: GeneratedOutputOrigin? = null

  /** Associates a generated symbol with its source declaration. */
  fun bind(
    symbol: Any,
    origin: GeneratedOutputOrigin,
  ) {
    origins[symbol] = origin
  }

  /** Records a symbol created inside a declaration's emission context. */
  fun register(
    symbol: Any,
    category: GeneratedTypeCategory,
  ) {
    origins.putIfAbsent(
      symbol,
      current?.let {
        it.copy(category = if (it.category == GeneratedTypeCategory.Problem) it.category else category)
      } ?: GeneratedOutputOrigin(category, emptySet()),
    )
  }

  /** Selects a declaration without removing it from the registry. */
  fun includes(
    selection: GeneratedOutputSelection,
    symbol: Any,
  ): Boolean {
    val origin = requireNotNull(origins[symbol]) { "Missing output provenance for $symbol" }
    return selection.includes(origin.category, origin.ownerUris)
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
  scope?.service?.let { name -> api.services.firstOrNull { it.name == name }?.outputOrigin(api) }
    ?: GeneratedOutputOrigin(
      GeneratedTypeCategory.Model,
      ownerUris
        .ifEmpty {
          setOf(source?.location ?: declarationUri?.substringBefore('#') ?: api.source.location)
        }.map(::URI)
        .toSet(),
    )

/** Resolves the provenance of an explicitly declared problem. */
fun GeneratedProblem.outputOrigin(api: GeneratedApi): GeneratedOutputOrigin =
  GeneratedOutputOrigin(GeneratedTypeCategory.Problem, setOf(URI((source ?: api.source).location)))

/** A composed service belongs to every document contributing to it. */
fun GeneratedService.outputOrigin(api: GeneratedApi): GeneratedOutputOrigin =
  GeneratedOutputOrigin(
    GeneratedTypeCategory.Service,
    ownerUris.ifEmpty { setOf(api.source.location) }.map(::URI).toSet(),
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
