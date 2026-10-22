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

import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex

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
          api =
            if (changed) {
              GeneratedTypeReferenceRewriter(fragment.api.source, { reference, _ ->
                if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
                  reference.copy(name = renamed[reference.name] ?: reference.name)
                } else {
                  reference
                }
              }, { renamed[it] ?: it }).rewrite(fragment.api)
            } else {
              fragment.api
            },
          modelIdentities =
            fragment.modelIdentities.filterKeys { it !in renamed } +
              renamed.values.associateWith { name ->
                GeneratedIdentity.native(name.replaceFirstChar { it.lowercase() })
              },
        )
      }
    }
  }

  /** Binds ambiguous compact references in their lexical document before composition merges names. */
  fun qualifyDeclarations(fragments: List<GeneratedApiFragment>): List<GeneratedApiFragment> {
    val ambiguous =
      fragments
        .flatMap { fragment ->
          fragment.api.models.flatMap { it.placementDeclarations.ifEmpty { listOf(it) } }
        }.groupBy { it.name to it.scope }
        .filterValues { declarations -> declarations.map { it.source.documentIdentity() }.distinct().size > 1 }
        .keys
    if (ambiguous.isEmpty()) return fragments
    return fragments.map { fragment ->
      val declarations = fragment.api.models.flatMap { it.placementDeclarations.ifEmpty { listOf(it) } }
      val index = GeneratedModelIndex(declarations)
      fragment.copy(
        api =
          GeneratedTypeReferenceRewriter(fragment.api.source, { reference, owner ->
            if (reference.name to reference.scope in ambiguous) index.bind(reference, owner) else reference
          }).rewrite(fragment.api),
      )
    }
  }
}
