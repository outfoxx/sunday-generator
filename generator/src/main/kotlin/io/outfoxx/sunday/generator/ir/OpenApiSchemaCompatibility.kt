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

import java.util.Collections
import java.util.IdentityHashMap

/** Compares inherited field contracts without treating documentary annotations as refinements. */
internal class OpenApiSchemaCompatibility(
  private val composition: OpenApiSchemaComposition,
) {
  private val compared = IdentityHashMap<Map<*, *>, IdentityHashMap<Map<*, *>, Boolean>>()
  private val comparing = IdentityHashMap<Map<*, *>, MutableSet<Map<*, *>>>()

  fun equivalent(
    left: Any?,
    right: Any?,
  ): Boolean {
    if (left == right) return true
    if (left !is Map<*, *> || right !is Map<*, *>) return false
    compared[left]?.get(right)?.let { return it }
    val active = comparing.getOrPut(left) { Collections.newSetFromMap(IdentityHashMap()) }
    // Recursive shapes that cannot be established from canonical references remain refinements.
    if (!active.add(right)) return false
    try {
      // Matching wrappers can establish a recursive contract without resolving the enclosing declaration.
      if (structurallyEquivalent(left, right)) {
        compared.getOrPut(left) { IdentityHashMap() }[right] = true
        return true
      }
      val reference = OpenApiSchemaComposition.referenceName(left)
      if (reference != null &&
        reference == OpenApiSchemaComposition.referenceName(right) &&
        equivalentFields(left, right)
      ) {
        return true
      }
      val first = composition.resolveForComparison(left) ?: return false
      val second = composition.resolveForComparison(right) ?: return false
      val result = equivalentFields(first, second)
      compared.getOrPut(left) { IdentityHashMap() }[right] = result
      return result
    } finally {
      active.remove(right)
    }
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
  ): Any? {
    if (left == right || left !is Map<*, *> || right !is Map<*, *>) return right
    // Equivalent named aliases retain the inherited reference spelling instead of expanding their target.
    val referenceUses =
      OpenApiSchemaComposition.referenceName(left) != null &&
        OpenApiSchemaComposition.referenceName(right) != null &&
        equivalentFields(left.filterKeys { it != "\$ref" }, right.filterKeys { it != "\$ref" })
    // Keep references in matching branches; only different composition forms need effective schemas.
    val (first, second) =
      if (structurallyEquivalent(left, right) || referenceUses || equivalentFields(left, right)) {
        left to right
      } else {
        checkNotNull(composition.resolveForComparison(left)) to checkNotNull(composition.resolveForComparison(right))
      }
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

  private companion object {
    val documentaryAnnotations =
      setOf("description", "summary", "title", "example", "examples", "externalDocs", "\$comment")
    val schemaMaps = setOf("properties", "patternProperties", "\$defs", "definitions", "dependentSchemas")
    val schemaLists = setOf("allOf", "oneOf", "anyOf", "prefixItems")
    val numericAssertions =
      setOf(
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "minLength",
        "maxLength",
        "minItems",
        "maxItems",
        "minProperties",
        "maxProperties",
        "minContains",
        "maxContains",
        "multipleOf",
      )
    val schemaValues =
      setOf(
        "items",
        "additionalProperties",
        "not",
        "if",
        "then",
        "else",
        "contains",
        "propertyNames",
        "unevaluatedItems",
        "unevaluatedProperties",
      )
  }
}
