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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Source-aware declaration lookup shared by model transforms and emitters. */
internal class GeneratedModelIndex(
  models: List<GeneratedModel>,
) {
  private val candidates = models.groupBy { it.name to it.scope }

  /** Resolves a compact reference within its lexical document before placement can merge declarations. */
  fun bind(
    reference: GeneratedTypeRef,
    owner: GeneratedSourceSpec?,
  ): GeneratedTypeRef {
    if (reference.kind != GeneratedTypeRef.Kind.NAMED || reference.source != null) return reference
    val matches = candidates[reference.name to reference.scope].orEmpty()
    val document = owner?.documentIdentity()
    val declaration =
      matches.firstOrNull { it.source.documentIdentity() == document }
        ?: matches.firstOrNull().takeIf { matches.map { it.source.documentIdentity() }.distinct().size == 1 }
    require(declaration != null || matches.isEmpty()) {
      "Ambiguous reference '${reference.name}' in '${owner?.location}': " +
        matches.joinToString { it.declarationUri ?: it.source.location }
    }
    return reference.copy(source = declaration?.source)
  }

  fun modelOrNull(reference: GeneratedTypeRef): GeneratedModel? =
    candidates[reference.name to reference.scope].orEmpty().let { models ->
      models.firstOrNull { model ->
        reference.source?.documentIdentity()?.let { document ->
          model.source.documentIdentity() == document ||
            model.placementDeclarations.any { it.source.documentIdentity() == document }
        } == true
      } ?: models.firstOrNull().takeIf { reference.source == null }
    }
}
