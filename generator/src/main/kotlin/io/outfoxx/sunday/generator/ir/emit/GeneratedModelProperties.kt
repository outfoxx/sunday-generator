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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.util.Collections
import java.util.IdentityHashMap

/** Separates inherited storage declarations from the wire contracts imposed by each descendant. */
internal class GeneratedModelProperties(
  private val modelFor: (GeneratedTypeRef) -> GeneratedModel?,
) {
  class Field(
    val declaration: GeneratedModelProperty,
    val effective: GeneratedModelProperty,
    val inherited: Boolean,
  ) {
    val wireName: String get() = effective.serializationName ?: effective.name

    // A stronger wire requirement must not invalidate the superclass initializer or protocol conformance.
    val storage: GeneratedModelProperty
      get() =
        if (declaration.type.copy(nullable = false) != effective.type.copy(nullable = false)) {
          effective
        } else {
          effective.copy(
            name = declaration.name,
            serializationName = declaration.serializationName,
            type = declaration.type,
            required = declaration.required,
          )
        }
  }

  private val completed = IdentityHashMap<GeneratedModel, List<Field>>()
  private val visiting = Collections.newSetFromMap(IdentityHashMap<GeneratedModel, Boolean>())

  /** Whether this model or an inherited schema forbids undeclared wire properties. */
  fun isClosed(model: GeneratedModel): Boolean {
    val visited = Collections.newSetFromMap(IdentityHashMap<GeneratedModel, Boolean>())

    fun closed(candidate: GeneratedModel): Boolean =
      visited.add(candidate) &&
        (
          candidate.closed == true ||
            candidate.additionalProperties?.allowed == false ||
            candidate.inherits.mapNotNull(modelFor).any(::closed)
        )
    return closed(model)
  }

  /** All pattern assertions applying to a model, including inherited constraints. */
  fun patternProperties(model: GeneratedModel): List<GeneratedPatternProperty> {
    val visited = Collections.newSetFromMap(IdentityHashMap<GeneratedModel, Boolean>())

    fun collect(candidate: GeneratedModel): List<GeneratedPatternProperty> =
      if (visited.add(candidate)) {
        candidate.inherits.mapNotNull(modelFor).flatMap(::collect) + candidate.patternProperties
      } else {
        emptyList()
      }
    return collect(model).distinct()
  }

  fun declarationModel(type: GeneratedTypeRef): GeneratedModel? {
    var model = modelFor(type) ?: return null
    val aliases = Collections.newSetFromMap(IdentityHashMap<GeneratedModel, Boolean>())
    while (model.kind == GeneratedModel.Kind.SCALAR_ALIAS && aliases.add(model)) {
      model = model.aliases.singleOrNull()?.let(modelFor) ?: return model
    }
    return model
  }

  fun scalarDefault(property: GeneratedModelProperty): Any? {
    val value = property.defaultValue ?: return null
    val type = declarationType(property.type)
    if (type.kind == GeneratedTypeRef.Kind.NAMED &&
      declarationModel(type)?.kind == GeneratedModel.Kind.ENUM
    ) {
      return value
    }
    if (type.kind != GeneratedTypeRef.Kind.SCALAR) return null
    return when (type.name) {
      "integer", "number" -> value.toBigDecimalOrNull()
      "boolean" -> value.toBooleanStrictOrNull()
      "string" -> value
      else -> null
    }
  }

  /** Whether a property may carry an explicit null, including aliases and union alternatives. */
  fun acceptsNull(
    reference: GeneratedTypeRef,
    visited: Set<String> = emptySet(),
  ): Boolean =
    reference.nullable ||
      (reference.kind == GeneratedTypeRef.Kind.SCALAR && reference.name.lowercase() in setOf("any", "object", "nil")) ||
      (reference.kind == GeneratedTypeRef.Kind.UNION && reference.arguments.any { acceptsNull(it, visited) }) ||
      (
        reference.kind == GeneratedTypeRef.Kind.NAMED &&
          reference.name !in visited &&
          modelFor(reference)
            ?.takeIf { it.kind == GeneratedModel.Kind.SCALAR_ALIAS || it.kind == GeneratedModel.Kind.UNION }
            ?.aliases
            ?.any { acceptsNull(it, visited + reference.name) } == true
      )

  fun declarationType(reference: GeneratedTypeRef): GeneratedTypeRef {
    var type = reference
    val aliases = mutableSetOf<GeneratedTypeRef>()
    while (type.kind == GeneratedTypeRef.Kind.NAMED && aliases.add(type)) {
      val model = modelFor(type) ?: return type
      if (model.kind == GeneratedModel.Kind.ARRAY) {
        return GeneratedTypeRef(
          GeneratedTypeRef.Kind.ARRAY,
          "array",
          arguments = model.aliases,
          collection = model.collection,
          nullable = type.nullable,
        )
      }
      if (model.kind != GeneratedModel.Kind.SCALAR_ALIAS) return type
      val target = model.aliases.singleOrNull() ?: return type
      type = target.copy(nullable = type.nullable || target.nullable)
    }
    return type
  }

  fun numericValidationTarget(
    reference: GeneratedTypeRef,
    context: String,
  ): NumericValidationTarget {
    val declaration = declarationType(reference)
    val elements = declaration.kind == GeneratedTypeRef.Kind.ARRAY
    val numeric = if (elements) declaration.arguments.singleOrNull()?.let(::declarationType) else declaration
    if (numeric?.kind != GeneratedTypeRef.Kind.SCALAR || numeric.name !in setOf("integer", "number")) {
      genError(
        "Unsupported numeric validation target for $context: expected a numeric scalar or numeric collection items",
      )
    }
    return NumericValidationTarget(elements, numeric.nullable)
  }

  /** Locates the scalar payload of numeric assertions, including legacy RAML array-item metadata. */
  data class NumericValidationTarget(
    val elements: Boolean,
    val nullable: Boolean,
  )

  /** Retains restrictions carried by named scalar aliases at every property use. */
  private fun withScalarConstraints(property: GeneratedModelProperty): GeneratedModelProperty {
    var effective = property
    var reference = property.type
    val visited = mutableSetOf<String>()
    while (reference.kind == GeneratedTypeRef.Kind.NAMED && visited.add(reference.name)) {
      val alias = modelFor(reference)?.takeIf { it.kind == GeneratedModel.Kind.SCALAR_ALIAS } ?: break
      effective =
        GeneratedPropertyConstraints.intersect(
          property.copy(
            // Patterns remain on their aliases and are checked independently by patterns().
            validation = if ("pattern" in effective.validation) alias.validation - "pattern" else alias.validation,
          ),
          effective,
          "property '${property.serializationName ?: property.name}'",
        )
      reference = alias.aliases.singleOrNull() ?: break
    }
    return effective
  }

  /** All regular-expression assertions contributed by a property and its scalar alias chain. */
  fun patterns(property: GeneratedModelProperty): List<String> {
    val patterns = linkedSetOf<String>()
    property.validation["pattern"]?.let(patterns::add)
    var reference = property.type
    val visited = mutableSetOf<String>()
    while (reference.kind == GeneratedTypeRef.Kind.NAMED && visited.add(reference.name)) {
      val alias = modelFor(reference)?.takeIf { it.kind == GeneratedModel.Kind.SCALAR_ALIAS } ?: break
      alias.validation["pattern"]?.let(patterns::add)
      reference = alias.aliases.singleOrNull() ?: break
    }
    return patterns.toList()
  }

  fun fields(model: GeneratedModel): List<Field> {
    completed[model]?.let { return it }
    if (!visiting.add(model)) genError("Cyclic model inheritance for '${model.name}'")
    try {
      if (model.kind == GeneratedModel.Kind.SCALAR_ALIAS) {
        model.aliases.singleOrNull()?.let(::declarationModel)?.takeUnless { it === model }?.let {
          return fields(it).also { fields -> completed[model] = fields }
        }
      }
      val fields = linkedMapOf<String, Field>()
      model.inherits.mapNotNull(modelFor).flatMap(::fields).forEach { field ->
        val previous = fields[field.wireName]
        if (previous != null &&
          (
            previous.declaration.required != field.declaration.required ||
              storageType(previous.declaration.type) != storageType(field.declaration.type)
          )
        ) {
          genError("Conflicting inherited declarations for property '${model.name}.${field.wireName}'")
        }
        val declaration = previous?.declaration ?: field.declaration
        val effective =
          if (storageType(field.effective.type) == storageType(field.declaration.type)) {
            field.effective.copy(type = declaration.type.copy(nullable = field.effective.type.nullable))
          } else {
            field.effective
          }
        fields[field.wireName] =
          Field(
            declaration,
            if (previous == null) {
              effective
            } else {
              GeneratedPropertyConstraints.intersect(
                previous.effective,
                effective,
                "property '${model.name}.${field.wireName}'",
              )
            },
            true,
          )
      }
      model.properties.forEach { declaredProperty ->
        val property = withScalarConstraints(declaredProperty)
        val wireName = property.serializationName ?: property.name
        val parent = fields[wireName]
        fields[wireName] =
          if (parent == null) {
            Field(property, property, false)
          } else {
            Field(
              parent.declaration,
              property.copy(allowedValues = property.allowedValues ?: parent.effective.allowedValues),
              true,
            )
          }
      }
      return fields.values.toList().also { completed[model] = it }
    } finally {
      visiting.remove(model)
    }
  }

  private fun storageType(
    reference: GeneratedTypeRef,
    visiting: MutableSet<GeneratedTypeRef> = mutableSetOf(),
  ): GeneratedTypeRef {
    val resolved = declarationType(reference)
    if (!visiting.add(resolved)) return reference
    try {
      return resolved.copy(
        nullable = reference.nullable || resolved.nullable,
        arguments = resolved.arguments.map { storageType(it, visiting) },
      )
    } finally {
      visiting.remove(resolved)
    }
  }
}
