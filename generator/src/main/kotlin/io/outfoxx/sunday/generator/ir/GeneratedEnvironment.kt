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

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.GenerationMode

/** Preserved shared, role-specific, and named-profile declarations for one metadata feature. */
data class GeneratedEnvironment<T>(
  val all: T? = null,
  val client: T? = null,
  val server: T? = null,
  val profiles: Map<String, Scope<T>> = emptyMap(),
  val inherited: List<GeneratedEnvironment<T>> = emptyList(),
) {
  /** Role overrides within a named profile; profiles cannot recursively contain other profiles. */
  data class Scope<T>(
    val all: T? = null,
    val client: T? = null,
    val server: T? = null,
  )

  /** Returns applicable declarations in increasing override precedence. */
  fun layers(context: GenerationContext): List<T> {
    val profile = context.profile?.let(profiles::get)
    return inherited.flatMap { it.layers(context) } +
      listOfNotNull(
        all,
        if (context.role == GenerationMode.Client) client else server,
        profile?.all,
        if (context.role == GenerationMode.Client) profile?.client else profile?.server,
      )
  }

  /** Resolves one environment with the feature's typed, field-aware merge operation. */
  fun resolve(
    context: GenerationContext,
    merge: (T, T) -> T,
  ): T? = layers(context).reduceOrNull(merge)

  /** Preserves declaration precedence: a local shared value overrides an inherited role or profile value. */
  fun inherit(overrides: GeneratedEnvironment<T>): GeneratedEnvironment<T> =
    overrides.copy(inherited = inherited + copy(inherited = emptyList()) + overrides.inherited)

  /** Projects one metadata member while preserving its scope and declaration precedence. */
  fun <R> mapNotNull(transform: (T) -> R?): GeneratedEnvironment<R>? {
    val result =
      GeneratedEnvironment(
        all = all?.let(transform),
        client = client?.let(transform),
        server = server?.let(transform),
        profiles =
          profiles
            .mapValues { (_, scope) ->
              Scope(scope.all?.let(transform), scope.client?.let(transform), scope.server?.let(transform))
            }.filterValues { it.all != null || it.client != null || it.server != null },
        inherited = inherited.mapNotNull { it.mapNotNull(transform) },
      )
    return result.takeIf {
      it.all != null || it.client != null || it.server != null || it.profiles.isNotEmpty() || it.inherited.isNotEmpty()
    }
  }

  /** Merges peer declarations independently in every scope, using the feature's conflict rules. */
  fun merge(
    overrides: GeneratedEnvironment<T>,
    merge: (T, T) -> T,
  ): GeneratedEnvironment<T> {
    fun combine(
      base: T?,
      override: T?,
    ): T? =
      when {
        base == null -> override
        override == null -> base
        else -> merge(base, override)
      }

    require(inherited.isEmpty() || overrides.inherited.isEmpty() || inherited == overrides.inherited) {
      "Conflicting inherited environment declarations"
    }
    return GeneratedEnvironment(
      inherited = inherited.ifEmpty { overrides.inherited },
      all = combine(all, overrides.all),
      client = combine(client, overrides.client),
      server = combine(server, overrides.server),
      profiles =
        (profiles.keys + overrides.profiles.keys).associateWith { name ->
          val base = profiles[name]
          val override = overrides.profiles[name]
          Scope(
            all = combine(base?.all, override?.all),
            client = combine(base?.client, override?.client),
            server = combine(base?.server, override?.server),
          )
        },
    )
  }
}
