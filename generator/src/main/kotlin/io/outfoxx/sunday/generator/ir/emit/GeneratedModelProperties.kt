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
      model.properties.forEach { property ->
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
