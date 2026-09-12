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

import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.documentaryAnnotations
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.numericAssertions
import java.util.Collections
import java.util.IdentityHashMap
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.lists as schemaLists
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.maps as schemaMaps
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.values as schemaValues

/** Compares inherited field contracts without treating documentary annotations as refinements. */
internal class OpenApiSchemaCompatibility(
  private val composition: OpenApiSchemaComposition,
) {
  private class Comparison(
    val first: Map<*, *>,
    val second: Map<*, *>,
  )

  private val compared = IdentityHashMap<Map<*, *>, IdentityHashMap<Map<*, *>, Comparison>>()
  private val comparing = IdentityHashMap<Map<*, *>, MutableSet<Map<*, *>>>()

  fun equivalent(
    left: Any?,
    right: Any?,
  ): Boolean = left == right || (left is Map<*, *> && right is Map<*, *> && compare(left, right) != null)

  private fun compare(
    left: Map<*, *>,
    right: Map<*, *>,
  ): Comparison? {
    compared[left]?.get(right)?.let { return it }
    val active = comparing.getOrPut(left) { Collections.newSetFromMap(IdentityHashMap()) }
    // Recursive shapes that cannot be established from canonical references remain refinements.
    if (!active.add(right)) return null
    try {
      val result = compareOperands(left, right) ?: return null
      // A blocked recursive comparison can succeed later; only completed compatible results are reusable.
      compared.getOrPut(left) { IdentityHashMap() }[right] = result
      return result
    } finally {
      active.remove(right)
    }
  }

  private fun compareOperands(
    left: Map<*, *>,
    right: Map<*, *>,
  ): Comparison? {
    // Matching wrappers can establish a recursive contract without resolving the enclosing declaration.
    if (structurallyEquivalent(left, right)) {
      return Comparison(left, right)
    }
    val reference = OpenApiSchemaReferences.name(left)
    if (reference != null &&
      reference == OpenApiSchemaReferences.name(right) &&
      equivalentFields(left, right)
    ) {
      return Comparison(left, right)
    }
    val first = composition.resolveForComparison(left) ?: return null
    val second = composition.resolveForComparison(right) ?: return null
    if (!equivalentFields(first, second)) return null

    // Equivalent named aliases retain the inherited spelling when their use-site assertions also match.
    val referenceUses =
      OpenApiSchemaReferences.name(left) != null &&
        OpenApiSchemaReferences.name(right) != null &&
        equivalentFields(left.filterKeys { it != "\$ref" }, right.filterKeys { it != "\$ref" })
    return if (referenceUses || equivalentFields(left, right)) Comparison(left, right) else Comparison(first, second)
  }

  fun equivalentValue(
    key: String,
    left: Any?,
    right: Any?,
  ): Boolean = equivalentValue(key, left, right, ::equivalent)

  private fun equivalentValue(
    key: String,
    left: Any?,
    right: Any?,
    compareSchema: (Any?, Any?) -> Boolean,
  ): Boolean =
    when (key) {
      in schemaMaps ->
        left is Map<*, *> &&
          right is Map<*, *> &&
          left.keys == right.keys &&
          left.all { (name, schema) -> compareSchema(schema, right[name]) }
      in schemaLists ->
        left is List<*> &&
          right is List<*> &&
          left.size == right.size &&
          left.indices.all { compareSchema(left[it], right[it]) }
      in schemaValues -> compareSchema(left, right)
      in numericAssertions -> equivalentNumber(left, right)
      // Literal and extension data may itself contain keys that look like schema annotations.
      else -> left == right
    }

  /** Overlays documentation on already compatible contracts, preserving literal data and canonical references. */
  fun mergeValue(
    key: String,
    left: Any?,
    right: Any?,
  ): Any? =
    when {
      left == right -> right
      key == "\$ref" -> left
      key in schemaMaps && left is Map<*, *> && right is Map<*, *> ->
        right.entries.associate { (name, schema) -> name to mergeAnnotations(left[name], schema) }
      key in schemaLists && left is List<*> && right is List<*> ->
        left.indices.map { mergeAnnotations(left[it], right[it]) }
      key in schemaValues -> mergeAnnotations(left, right)
      else -> right
    }

  private fun mergeAnnotations(
    left: Any?,
    right: Any?,
  ): Any? =
    if (left == right || left !is Map<*, *> || right !is Map<*, *>) {
      right
    } else {
      checkNotNull(mergeIfCompatible(left, right))
    }

  /** Reuses the established comparison operands to overlay documentation without expanding matching references. */
  fun mergeIfCompatible(
    left: Map<*, *>,
    right: Map<*, *>,
  ): Map<String, Any?>? {
    val comparison = compare(left, right) ?: return null
    val first = comparison.first
    val second = comparison.second
    val fields = first.entries.associate { it.key.toString() to it.value }.toMutableMap()
    second.forEach { (name, value) ->
      val key = name.toString()
      fields[key] = mergeValue(key, fields[key], value)
    }
    return (second as? OpenApiSchema ?: first as? OpenApiSchema)?.withFields(fields) ?: fields
  }

  private fun equivalentFields(
    left: Map<*, *>,
    right: Map<*, *>,
    compareSchema: (Any?, Any?) -> Boolean = ::equivalent,
  ): Boolean {
    val first = left.filterKeys { it !in documentaryAnnotations }
    val second = right.filterKeys { it !in documentaryAnnotations }
    return first.keys == second.keys &&
      first.all { (key, value) -> equivalentValue(key.toString(), value, second[key], compareSchema) }
  }

  // References remain literal identifiers throughout this comparison, even inside nested applicators.
  private fun structurallyEquivalent(
    left: Any?,
    right: Any?,
  ): Boolean =
    left == right ||
      (left is Map<*, *> && right is Map<*, *> && equivalentFields(left, right, ::structurallyEquivalent))

  private fun equivalentNumber(
    left: Any?,
    right: Any?,
  ): Boolean {
    if (left == right) return true
    if (left !is Number || right !is Number) return false
    val first = left.toString().toBigDecimalOrNull() ?: return false
    val second = right.toString().toBigDecimalOrNull() ?: return false
    return first.compareTo(second) == 0
  }
}
