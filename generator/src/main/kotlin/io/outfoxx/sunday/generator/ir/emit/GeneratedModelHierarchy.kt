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

/** Follows nominal parents without expanding fields or counting a declaration as its own ancestor. */
internal fun GeneratedModel.ancestorModels(index: GeneratedApiIndex): Set<GeneratedModel> {
  val ancestors = linkedSetOf<GeneratedModel>()
  val pending = ArrayDeque(inherits.mapNotNull(index::modelOrNull))
  while (pending.isNotEmpty()) {
    val parent = pending.removeFirst()
    if (parent != this && ancestors.add(parent)) {
      pending.addAll(parent.inherits.mapNotNull(index::modelOrNull))
    }
  }
  return ancestors
}

/** Replaces intermediate dispatch branches with their cases, retaining unmapped sibling variants. */
internal fun GeneratedModel.discriminatorChildren(
  index: GeneratedApiIndex,
  children: (GeneratedModel) -> List<GeneratedModel>,
): List<GeneratedModel> {
  val mapped = discriminatorMappings.values.mapNotNull(index::modelOrNull).toSet()
  val intermediates = mapped.flatMap { it.ancestorModels(index) }.toSet() - mapped
  val selected = linkedSetOf<GeneratedModel>()
  val visited = mutableSetOf<GeneratedModel>()
  val pending = ArrayDeque(children(this))
  while (pending.isNotEmpty()) {
    val child = pending.removeFirst()
    if (!visited.add(child)) continue
    if (child in intermediates) {
      pending.addAll(children(child))
    } else {
      selected.add(child)
    }
  }
  return selected.toList()
}
