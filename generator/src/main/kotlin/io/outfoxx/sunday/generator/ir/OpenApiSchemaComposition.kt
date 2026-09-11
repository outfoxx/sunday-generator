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

import io.outfoxx.sunday.generator.genError
import java.math.BigDecimal
import java.util.Collections
import java.util.IdentityHashMap

/** Projects schema conjunctions into the existing IR without treating assertions as overrides. */
internal class OpenApiSchemaComposition(
  private val schemas: Map<String, Map<*, *>>,
) {
  private val resolved = IdentityHashMap<Map<*, *>, Map<String, Any?>>()
  private val resolving = Collections.newSetFromMap(IdentityHashMap<Map<*, *>, Boolean>())
  private val compatibility = OpenApiSchemaCompatibility(this)

  class Model(
    val schema: Map<String, Any?>,
    val localSchema: Map<String, Any?>,
    val parents: List<String>,
  )

  fun model(schema: Map<*, *>): Model {
    val effective = resolve(schema).filterKeys { it !in declarationAnnotations || schema.containsKey(it) }
    val parents =
      parentNames(schema).distinct().filter { name ->
        val parent = resolve(schemas.getValue(name))
        val type = parent["type"]
        parent["properties"] is Map<*, *> ||
          ((type == "object" || type is List<*> && "object" in type) && parent["additionalProperties"] !is Map<*, *>)
      }
    val inherited = parents.map { resolve(schemas.getValue(it)) }
    val properties = effective["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
    val required = (effective["required"] as? List<*>).orEmpty()
    val parentProperties =
      inherited.flatMap { (it["properties"] as? Map<*, *>).orEmpty().entries }.associate {
        it.key to
          it.value
      }
    val parentRequired = inherited.flatMap { (it["required"] as? List<*>).orEmpty() }.toSet()
    // IR inheritance has no property-override contract. Refinements must become concrete local fields.
    val refinesParent =
      parentProperties.any { (name, property) ->
        !compatibility.equivalent(properties[name], property) || (name in required && name !in parentRequired)
      }
    return if (refinesParent) {
      Model(effective, effective, emptyList())
    } else {
      Model(effective, effective + ("properties" to properties.filterKeys { it !in parentProperties }), parents)
    }
  }

  fun resolve(schema: Map<*, *>): Map<String, Any?> {
    resolved[schema]?.let { return it }
    if (!resolving.add(schema)) fail(schema, "Cyclic OpenAPI schema composition cannot be represented")
    try {
      var result = linkedMapOf<String, Any?>()
      referenceName(schema)?.let { name ->
        val target = schemas[name] ?: fail(schema, "Unresolved OpenAPI schema '$name'")
        result = merge(result, resolve(target), schema)
      }
      val contributions = (schema["allOf"] as? List<*>).orEmpty()
      if (false in contributions) fail(schema, "OpenAPI false schema intersection cannot be represented")
      val parts = contributions.filterIsInstance<Map<*, *>>()
      parts.forEach { part -> result = merge(result, resolve(part), schema) }
      val own =
        schema.entries
          .filter { it.key is String && it.key !in setOf(REF, "allOf") }
          .associate { it.key as String to it.value }
      result = merge(result, normalizeAssertions(OpenApiSchemaBounds.normalize(own, schema)), schema)
      validate(result, schema)
      // Effective schemas use numeric exclusive bounds, including across dialect boundaries.
      return ((schema as? OpenApiSchema)?.withFields(result, false) ?: result).also { resolved[schema] = it }
    } finally {
      resolving.remove(schema)
    }
  }

  fun constrain(
    schema: Map<*, *>,
    constraints: Map<*, *>,
    origin: Map<*, *>,
  ): Map<String, Any?> = resolve(located(origin, mapOf("allOf" to listOf(schema, constraints))))

  /** Comparison cannot expand a reference or allOf chain back into an operand still being composed. */
  fun resolveForComparison(schema: Map<*, *>): Map<String, Any?>? {
    val visited = Collections.newSetFromMap(IdentityHashMap<Map<*, *>, Boolean>())

    fun available(value: Map<*, *>): Boolean {
      if (resolved.containsKey(value)) return true
      if (value in resolving || !visited.add(value)) return false
      try {
        val target = referenceName(value)?.let { schemas[it] }
        return (target == null || available(target)) &&
          (value["allOf"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().all { available(it) }
      } finally {
        visited.remove(value)
      }
    }
    return if (available(schema)) resolve(schema) else null
  }

  private fun parentNames(schema: Map<*, *>): List<String> =
    (schema["allOf"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().flatMap { part ->
      referenceName(part)?.let { listOf(it) } ?: parentNames(part)
    }

  private fun normalizeAssertions(schema: Map<String, Any?>): Map<String, Any?> {
    val result = LinkedHashMap(schema)
    val types =
      when (val type = result["type"]) {
        is String -> listOf(type)
        is List<*> -> type
        else -> null
      }
    if (types != null) {
      // Nullable modifies only this operand's type, never a type supplied by another operand.
      val allowed = (types + if (result["nullable"] == true) listOf("null") else emptyList()).distinct()
      result["type"] = allowed.singleOrNull() ?: allowed
      result.remove("nullable")
    }
    // Conditional keywords cannot pair with keywords from a different schema in allOf.
    if (!result.containsKey("if")) {
      result.remove("then")
      result.remove("else")
    } else if (!result.containsKey("then") && !result.containsKey("else")) {
      result.remove("if")
    }
    return result
  }

  private fun merge(
    left: Map<String, Any?>,
    right: Map<String, Any?>,
    origin: Map<*, *>,
  ): LinkedHashMap<String, Any?> {
    checkClosedProperties(left, right, origin)
    checkClosedProperties(right, left, origin)
    val leftUnion = left.keys.any { it == "oneOf" || it == "anyOf" }
    val rightUnion = right.keys.any { it == "oneOf" || it == "anyOf" }
    if ((leftUnion && hasDifferentAssertions(right, left)) || (rightUnion && hasDifferentAssertions(left, right))) {
      fail(origin, "Unsupported OpenAPI schema intersection involving oneOf or anyOf")
    }
    val result = LinkedHashMap(left)
    right.forEach { (key, value) ->
      val previous = result[key]
      result[key] =
        when {
          !result.containsKey(key) || previous == value -> value
          key in setOf("oneOf", "anyOf") && compatibility.equivalentValue(key, previous, value) ->
            compatibility.mergeValue(key, previous, value)
          key in setOf("properties", "patternProperties", "dependentSchemas") &&
            previous is Map<*, *> &&
            value is Map<*, *> -> {
            val combined = previous.entries.associate { it.key.toString() to it.value }.toMutableMap()
            value.forEach { (name, property) ->
              val existing = combined[name.toString()]
              combined[name.toString()] = if (existing == null) property else intersect(existing, property, origin)
            }
            combined
          }
          key == "required" -> ((previous as? List<*>).orEmpty() + (value as? List<*>).orEmpty()).distinct()
          key == "nullable" -> previous == true || value == true
          key == "type" -> intersectTypes(previous, value, origin)
          key == "enum" && previous is List<*> && value is List<*> ->
            previous.filter { it in value }.also {
              if (it.isEmpty()) {
                fail(
                  origin,
                  "Incompatible OpenAPI enum intersection",
                )
              }
            }
          key in lowerBounds -> maxOf(number(previous, origin), number(value, origin))
          key in upperBounds -> minOf(number(previous, origin), number(value, origin))
          key == "uniqueItems" -> previous == true || value == true
          key in
            setOf(
              "items",
              "additionalProperties",
              "unevaluatedProperties",
              "propertyNames",
            )
          -> intersect(previous, value, origin)
          key in setOf("const", "pattern", "format") || isAssertion(key) ->
            fail(origin, "Unsupported or incompatible OpenAPI schema intersection for '$key'")
          else -> value
        }
    }
    // Keep the legacy nullable-only fallback for unconstrained schemas without widening typed operands.
    if (result.containsKey("type")) result.remove("nullable")
    return result
  }

  private fun hasDifferentAssertions(
    left: Map<String, Any?>,
    right: Map<String, Any?>,
  ): Boolean = left.any { (key, value) -> isAssertion(key) && !compatibility.equivalentValue(key, value, right[key]) }

  private fun intersect(
    left: Any?,
    right: Any?,
    origin: Map<*, *>,
  ): Any? =
    when {
      left == right -> left
      left == true -> right
      right == true -> left
      left == false || right == false -> false
      left is Map<*, *> && right is Map<*, *> -> resolve(located(origin, mapOf("allOf" to listOf(left, right))))
      else -> fail(origin, "Unsupported OpenAPI schema intersection")
    }

  private fun intersectTypes(
    left: Any?,
    right: Any?,
    origin: Map<*, *>,
  ): Any {
    fun types(value: Any?): List<*> = if (value is List<*>) value else listOf(value)
    val combined =
      types(left)
        .flatMap { first ->
          types(right).mapNotNull { second ->
            when {
              first == second -> first
              setOf(first, second) == setOf("integer", "number") -> "integer"
              else -> null
            }
          }
        }.distinct()
    if (combined.isEmpty()) fail(origin, "Incompatible OpenAPI schema type intersection: $left and $right")
    if (combined == listOf("null")) {
      fail(origin, "OpenAPI schema type intersection has no representable non-null payload")
    }
    return combined.singleOrNull() ?: combined
  }

  private fun number(
    value: Any?,
    origin: Map<*, *>,
  ): BigDecimal =
    value?.toString()?.toBigDecimalOrNull() ?: fail(origin, "Invalid numeric OpenAPI schema constraint '$value'")

  private fun checkClosedProperties(
    closed: Map<String, Any?>,
    other: Map<String, Any?>,
    origin: Map<*, *>,
  ) {
    if (closed["additionalProperties"] == false) {
      val allowed = (closed["properties"] as? Map<*, *>).orEmpty().keys
      val added = (other["properties"] as? Map<*, *>).orEmpty().keys - allowed
      if (added.isNotEmpty()) {
        fail(
          origin,
          "Unsupported OpenAPI intersection adds properties to a closed schema: $added",
        )
      }
    }
  }

  private fun validate(
    schema: MutableMap<String, Any?>,
    origin: Map<*, *>,
  ) {
    OpenApiSchemaBounds.validate(schema, origin)
    listOf(
      "minLength" to "maxLength",
      "minItems" to "maxItems",
      "minProperties" to "maxProperties",
    ).forEach { (minimum, maximum) ->
      if (schema.containsKey(minimum) &&
        schema.containsKey(maximum) &&
        number(schema[minimum], origin) > number(schema[maximum], origin)
      ) {
        fail(origin, "Incompatible OpenAPI schema bounds: $minimum exceeds $maximum")
      }
    }
    if (schema.containsKey("const") && schema["enum"] is List<*> && schema["const"] !in schema["enum"] as List<*>) {
      fail(origin, "Incompatible OpenAPI schema const and enum intersection")
    }
  }

  private fun located(
    origin: Map<*, *>,
    fields: Map<String, Any?>,
  ): Map<String, Any?> = (origin as? OpenApiSchema)?.withFields(fields) ?: fields

  private fun fail(
    origin: Map<*, *>,
    message: String,
  ): Nothing = (origin as? OpenApiSchema)?.error(message) ?: genError(message)

  companion object {
    private const val REF = "\$ref"
    private val declarationAnnotations =
      setOf("discriminator", "description", "summary", "title", "example", "examples", "deprecated")
    private val lowerBounds =
      setOf("minimum", "exclusiveMinimum", "minLength", "minItems", "minProperties", "minContains")
    private val upperBounds =
      setOf("maximum", "exclusiveMaximum", "maxLength", "maxItems", "maxProperties", "maxContains")
    private val assertions =
      setOf(
        "type",
        "enum",
        "const",
        "required",
        "properties",
        "patternProperties",
        "additionalProperties",
        "items",
        "prefixItems",
        "allOf",
        "oneOf",
        "anyOf",
        "not",
        "if",
        "then",
        "else",
        "contains",
        "propertyNames",
        "dependentSchemas",
        "dependentRequired",
        "unevaluatedItems",
        "unevaluatedProperties",
        "multipleOf",
        "uniqueItems",
        "pattern",
        "format",
        "nullable",
      ) + lowerBounds + upperBounds

    fun isAssertion(name: String): Boolean = name in assertions

    fun referenceName(schema: Map<*, *>): String? =
      (schema[REF] as? String)?.takeIf { it.startsWith("#/components/schemas/") }?.substringAfterLast('/')

    fun isSingleReference(schema: Map<*, *>): Boolean = singleReferenceName(schema) != null

    /** Follows assertion-free allOf wrappers without expanding the named target. */
    fun singleReferenceName(schema: Map<*, *>): String? {
      if (schema.keys.any { it is String && it != "allOf" && isAssertion(it) }) return null
      val member = (schema["allOf"] as? List<*>)?.singleOrNull() as? Map<*, *> ?: return null
      return referenceName(member) ?: singleReferenceName(member)
    }
  }
}
