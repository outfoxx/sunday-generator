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
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.declarationAnnotations
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.isAssertion
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.lowerBounds
import io.outfoxx.sunday.generator.ir.OpenApiSchemaKeywords.upperBounds
import java.math.BigDecimal
import java.util.Collections
import java.util.IdentityHashMap
import io.outfoxx.sunday.generator.ir.OpenApiSchemaReferences.name as referenceName

/** Projects schema conjunctions into the existing IR without treating assertions as overrides. */
internal class OpenApiSchemaComposition(
  private val schemas: Map<String, Map<*, *>>,
) {
  private val resolved = IdentityHashMap<Map<*, *>, Map<String, Any?>>()
  private val resolving = Collections.newSetFromMap(IdentityHashMap<Map<*, *>, Boolean>())
  private val compatibility = OpenApiSchemaCompatibility(this)
  private val nullability = OpenApiSchemaNullability(this)
  private val models = IdentityHashMap<Map<*, *>, Model>()
  private val inheritanceNames = mutableMapOf<String, String>()
  private val discriminatorTargets = Collections.newSetFromMap(IdentityHashMap<Map<*, *>, Boolean>())

  init {
    retainDiscriminatorTargets(schemas.values)
  }

  // Inspect only schema locations; example and extension data cannot select a declaration identity.
  fun retainDiscriminatorTargets(locations: Collection<Map<*, *>>) {
    val visited = Collections.newSetFromMap(IdentityHashMap<Map<*, *>, Boolean>())

    fun visit(schema: Map<*, *>) {
      if (!visited.add(schema)) return
      val discriminator = schema["discriminator"] as? Map<*, *>
      if (discriminator != null) {
        val targets = (discriminator["mapping"] as? Map<*, *>)?.values.orEmpty().filterIsInstance<String>()
        val alternatives =
          listOf("oneOf", "anyOf").flatMap { keyword ->
            (schema[keyword] as? List<*>)
              .orEmpty()
              .filterIsInstance<Map<*, *>>()
              .mapNotNull { it["\$ref"] as? String }
          }
        (targets + alternatives).forEach { reference ->
          OpenApiSchemaReferences.name(mapOf("\$ref" to reference))?.let(schemas::get)?.let(discriminatorTargets::add)
        }
      }
      schema.forEach { (key, value) ->
        val children =
          when (OpenApiSchemaKeywords.shape(key.toString())) {
            OpenApiSchemaKeywords.Shape.VALUE -> listOf(value)
            OpenApiSchemaKeywords.Shape.MAP -> (value as? Map<*, *>)?.values.orEmpty()
            OpenApiSchemaKeywords.Shape.LIST -> (value as? List<*>).orEmpty()
            null -> emptyList()
          }
        children.filterIsInstance<Map<*, *>>().forEach(::visit)
      }
    }
    locations.forEach(::visit)
  }

  class PropertyDeclaration(
    val modelName: String,
    val wireName: String,
    val schema: Map<*, *>,
  )

  class Model(
    val schema: Map<String, Any?>,
    val localSchema: Map<String, Any?>,
    val parents: List<String>,
    val inheritedProperties: Map<String, PropertyDeclaration> = emptyMap(),
  )

  fun canonicalReference(schema: Map<*, *>): String? =
    OpenApiSchemaReferences.canonicalName(schema) ?: OpenApiSchemaReferences.wrappedName(schema)?.takeIf { name ->
      schemas[name]?.let { compatibility.equivalent(resolve(schema), resolve(it)) } == true
    }

  /** Only wrappers emitted as aliases may disappear from the nominal inheritance graph. */
  fun collapsedAlias(schema: Map<*, *>): String? =
    canonicalReference(schema).takeIf {
      schema !in discriminatorTargets && OpenApiSchemaReferences.canonicalName(schema) == null
    }

  private fun inheritanceName(
    name: String,
    origin: Map<*, *>,
  ): String {
    inheritanceNames[name]?.let { return it }
    val visited = linkedSetOf<String>()
    var current = name
    while (true) {
      if (!visited.add(current)) fail(origin, "Cyclic OpenAPI schema inheritance cannot be represented")
      val target = schemas[current] ?: fail(origin, "Unresolved OpenAPI schema '$current'")
      current = collapsedAlias(target) ?: break
    }
    visited.forEach { inheritanceNames[it] = current }
    return current
  }

  fun model(schema: Map<*, *>): Model {
    models[schema]?.let { return it }
    val effective = resolve(schema).filterKeys { it !in declarationAnnotations || schema.containsKey(it) }
    val parents =
      parentNames(schema).map { inheritanceName(it, schema) }.distinct().filter { name ->
        val parent = resolve(schemas.getValue(name))
        val type = parent["type"]
        parent["properties"] is Map<*, *> ||
          ((type == "object" || type is List<*> && "object" in type) && parent["additionalProperties"] !is Map<*, *>)
      }
    val inherited = parents.map { resolve(schemas.getValue(it)) }
    val properties = effective["properties"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
    // Root aliases are expanded during normalization but still share their target's inline declarations.
    val aliasDeclarations =
      (schema as? OpenApiSchema)
        ?.declarationReference
        ?.takeIf { schemas[it] !== schema }
        ?.let { target ->
          val declared = (resolve(schemas.getValue(target))["properties"] as? Map<*, *>).orEmpty()
          properties
            .filter { (key, property) ->
              key in declared && canRefine(property, declared[key])
            }.keys
            .associate {
              it.toString() to
                declaration(declaringModelName(target), it.toString())
            }
        }.orEmpty()
    val required = (effective["required"] as? List<*>).orEmpty()
    val parentProperties = inherited.flatMap { (it["properties"] as? Map<*, *>).orEmpty().entries }.groupBy { it.key }
    val declarations =
      parentProperties.keys.associateWith { key ->
        parents
          .filter { (resolve(schemas.getValue(it))["properties"] as? Map<*, *>)?.containsKey(key) == true }
          .map { declaration(it, key.toString()) }
      }
    val refinements =
      parentProperties.filter { (name, contributions) ->
        contributions.any { !compatibility.equivalent(properties[name], it.value) } ||
          (
            name in required &&
              inherited.any {
                (it["properties"] as? Map<*, *>)?.containsKey(name) == true &&
                  name !in (it["required"] as? List<*>).orEmpty()
              }
          )
      }
    // Retained refinements must fit one superclass constructor without losing secondary-parent fields.
    val differentParentFields =
      refinements.isNotEmpty() &&
        inherited.map { (it["properties"] as? Map<*, *>).orEmpty().keys }.distinct().size > 1
    val result =
      if (differentParentFields ||
        parentProperties.any { (name, contributions) ->
          contributions.any { !canRefine(properties[name], it.value) }
        } ||
        declarations.values.any { sources -> sources.drop(1).any { !sameStorage(sources.first(), it) } }
      ) {
        Model(effective, effective, emptyList())
      } else {
        Model(
          effective,
          effective + ("properties" to properties.filterKeys { it !in parentProperties || it in refinements }),
          parents,
          aliasDeclarations +
            refinements.keys.associate { key ->
              key.toString() to declarations.getValue(key).first()
            },
        )
      }
    return result.also { models[schema] = it }
  }

  private fun declaration(
    modelName: String,
    wireName: String,
  ): PropertyDeclaration {
    val schema = schemas.getValue(modelName)
    declaringModelName(modelName).takeUnless { it == modelName }?.let { return declaration(it, wireName) }
    val projected = model(schema)
    projected.inheritedProperties[wireName]?.let { return it }
    val local = (projected.localSchema["properties"] as? Map<*, *>)?.get(wireName) as? Map<*, *>
    if (local != null) return PropertyDeclaration(modelName, wireName, local)
    val parent =
      projected.parents.first {
        (resolve(schemas.getValue(it))["properties"] as? Map<*, *>)?.containsKey(wireName) ==
          true
      }
    return declaration(parent, wireName)
  }

  private fun sameStorage(
    left: PropertyDeclaration,
    right: PropertyDeclaration,
  ): Boolean {
    if (left.modelName == right.modelName && left.wireName == right.wireName) return true

    fun required(declaration: PropertyDeclaration): Boolean =
      declaration.wireName in (resolve(schemas.getValue(declaration.modelName))["required"] as? List<*>).orEmpty()
    return required(left) == required(right) &&
      storageIdentity(left.schema, left.modelName to left.wireName) ==
      storageIdentity(right.schema, right.modelName to right.wireName)
  }

  // Compare the types a declaration will allocate without allocating models or expanding recursive fields.
  private fun storageIdentity(
    schema: Map<*, *>,
    inlineIdentity: Pair<String, String>? = null,
    references: Set<String> = emptySet(),
  ): List<Any?> {
    val reference = canonicalReference(schema)
    if (reference != null && reference in references) return listOf("reference", reference)
    val nestedReferences = if (reference == null) references else references + reference
    val effective = resolve(schema)
    val type = effective.schemaType()
    val union = (effective["oneOf"] ?: effective["anyOf"]) as? List<*>
    val additional = effective["additionalProperties"]
    val properties = effective["properties"] as? Map<*, *>
    val nominal =
      reference != null &&
        effective["enum"] is List<*> ||
        (reference != null || inlineIdentity != null) &&
        (
          union != null ||
            (type == "object" || type == null) &&
            (!properties.isNullOrEmpty() || additional !is Map<*, *> && additional != true)
        )
    val representation =
      when {
        effective.isUnconstrainedSchema() -> listOf("any")
        nominal -> listOf("nominal", reference ?: inlineIdentity)
        type == "array" ->
          listOf(
            "array",
            effective["uniqueItems"] == true,
            storageIdentity((effective["items"] as? Map<*, *>).orEmpty(), references = nestedReferences),
          )
        (type == "object" || type == null) && additional is Map<*, *> && properties.isNullOrEmpty() ->
          listOf("map", storageIdentity(additional, references = nestedReferences))
        union != null ->
          listOf(
            "union",
            union.filterIsInstance<Map<*, *>>().map { storageIdentity(it, references = nestedReferences) },
          )
        else -> listOf(type, effective["format"])
      }
    return listOf(nullability.isNullable(effective), representation)
  }

  private fun declaringModelName(name: String): String {
    val schema = schemas.getValue(name)
    val target =
      ((schema as? OpenApiSchema)?.declarationReference ?: canonicalReference(schema))
        ?.takeUnless { it == name } ?: return name
    return declaringModelName(target)
  }

  // A child's wire restrictions need not replace the inherited storage type or nominal relationship.
  private fun canRefine(
    child: Any?,
    parent: Any?,
  ): Boolean {
    if (compatibility.equivalent(child, parent)) return true
    if (child !is Map<*, *> || parent !is Map<*, *>) return false

    fun representation(schema: Map<*, *>): Map<String, Any?> =
      resolve(schema).filterKeys { it !in refinementKeywords }.mapValues { (key, value) ->
        if (key == "type" && value is List<*>) value.filterNot { it == "null" }.singleOrNull() ?: value else value
      }
    return compatibility.equivalent(representation(child), representation(parent))
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
          .filter { it.key is String && it.key !in setOf("\$ref", "allOf") }
          .associate { it.key as String to it.value }
      result = merge(result, normalizeAssertions(OpenApiSchemaBounds.normalize(own, schema)), schema)
      validate(result, schema)
      // Effective schemas use numeric exclusive bounds, including across dialect boundaries.
      return ((schema as? OpenApiSchema)?.withFields(result, false) ?: result).also {
        resolved[schema] = it
        resolved[it] = it
      }
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
      left is Map<*, *> && right is Map<*, *> ->
        compatibility.mergeIfCompatible(left, right) ?: resolve(located(origin, mapOf("allOf" to listOf(left, right))))
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

  private companion object {
    val refinementKeywords =
      setOf(
        "enum",
        "const",
        "nullable",
        "default",
        "minLength",
        "maxLength",
        "pattern",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "multipleOf",
        "minItems",
        "maxItems",
        "uniqueItems",
      )
  }
}
