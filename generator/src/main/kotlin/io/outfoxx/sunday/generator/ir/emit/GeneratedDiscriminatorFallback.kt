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
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

/**
 * Resolved catch-all contract for a discriminator backed by a tolerant enum.
 */
data class GeneratedDiscriminatorFallback(
  val hierarchy: GeneratedModel,
  val discriminatorProperty: GeneratedModelProperty,
  val enumModel: GeneratedModel,
  val fallbackValue: String,
  val fallbackName: String,
  val mappedValues: Set<String>,
  val baseProperties: List<GeneratedModelProperty>,
  val externallyDiscriminated: Boolean = false,
) {

  /** Generated model name used for the catch-all payload. */
  val modelName: String = hierarchy.name + fallbackName

  /** Serialized discriminator property name. */
  val discriminatorWireName: String = discriminatorProperty.serializationName ?: discriminatorProperty.name
}

/**
 * Resolves a catch-all contract when this model dispatches on a tolerant enum.
 */
fun GeneratedModel.discriminatorFallbackOrNull(index: GeneratedApiIndex): GeneratedDiscriminatorFallback? {
  val discriminatorName = discriminator ?: return null
  val lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel? = { type -> type.modelOrNull(index) }
  val discriminatorProperty = discriminatorPropertyOrNull(discriminatorName, lookup) ?: return null
  return discriminatorFallbackOrNull(discriminatorProperty, index.api.models, lookup)
}

/**
 * Resolves a catch-all contract from a name-indexed model collection.
 */
fun GeneratedModel.discriminatorFallbackOrNull(models: Map<String, GeneratedModel>): GeneratedDiscriminatorFallback? {
  val discriminatorName = discriminator ?: return null
  val lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel? = { type -> models[type.name] }
  val discriminatorProperty = discriminatorPropertyOrNull(discriminatorName, lookup) ?: return null
  return discriminatorFallbackOrNull(discriminatorProperty, models.values, lookup)
}

/**
 * Resolves a catch-all contract for an externally discriminated model property.
 */
fun GeneratedModelProperty.externalDiscriminatorFallbackOrNull(
  owner: GeneratedModel,
  index: GeneratedApiIndex,
): GeneratedDiscriminatorFallback? {
  val discriminatorName = externalDiscriminator ?: return null
  val lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel? = { type -> type.modelOrNull(index) }
  val discriminatorProperty = owner.discriminatorPropertyOrNull(discriminatorName, lookup) ?: return null
  val hierarchy = type.modelOrNull(index) ?: return null
  return hierarchy
    .discriminatorFallbackOrNull(discriminatorProperty, index.api.models, lookup)
    ?.copy(externallyDiscriminated = true)
}

/**
 * Resolves an externally discriminated catch-all from a name-indexed model collection.
 */
fun GeneratedModelProperty.externalDiscriminatorFallbackOrNull(
  owner: GeneratedModel,
  models: Map<String, GeneratedModel>,
): GeneratedDiscriminatorFallback? {
  val discriminatorName = externalDiscriminator ?: return null
  val lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel? = { type -> models[type.name] }
  val discriminatorProperty = owner.discriminatorPropertyOrNull(discriminatorName, lookup) ?: return null
  val hierarchy = models[type.name] ?: return null
  return hierarchy
    .discriminatorFallbackOrNull(discriminatorProperty, models.values, lookup)
    ?.copy(externallyDiscriminated = true)
}

private fun GeneratedModel.discriminatorFallbackOrNull(
  discriminatorProperty: GeneratedModelProperty,
  models: Collection<GeneratedModel>,
  lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel?,
): GeneratedDiscriminatorFallback? {
  val enumModel = lookup(discriminatorProperty.type) ?: return null
  if (enumModel.kind != GeneratedModel.Kind.ENUM) {
    return null
  }
  val fallbackValue = enumModel.unknownValue ?: return null
  val fallbackIndex = enumModel.values.indexOf(fallbackValue)
  if (fallbackIndex < 0) {
    genError("Tolerant enum '${enumModel.name}' unknown value '$fallbackValue' does not match any enum value")
  }
  val mappedValues =
    buildSet {
      addAll(discriminatorMappings.keys)
      aliases.mapNotNullTo(this) { alias -> lookup(alias)?.discriminatorValue }
      models
        .filter { candidate ->
          candidate.inherits.any { inherited ->
            lookup(inherited) == this@discriminatorFallbackOrNull
          }
        }.mapNotNullTo(this) { candidate -> candidate.discriminatorValue }
    }
  if (fallbackValue in mappedValues) {
    genError(
      "Discriminator hierarchy '$name' maps tolerant enum '${enumModel.name}' fallback value " +
        "'$fallbackValue' to a concrete subtype. Remove that mapping because x-unknown-value is reserved " +
        "for the generated catch-all variant.",
    )
  }
  val invalidMappedValues = mappedValues - enumModel.values.toSet()
  if (invalidMappedValues.isNotEmpty()) {
    genError(
      "Discriminator hierarchy '$name' maps values not declared by tolerant enum '${enumModel.name}': " +
        invalidMappedValues.sorted().joinToString(", ") { value -> "'$value'" },
    )
  }

  val fallbackName =
    enumModel.enumValueNames
      .getOrNull(fallbackIndex)
      ?.toUpperCamelCase()
      ?.takeIf(String::isNotBlank)
      ?: fallbackValue.toUpperCamelCase()
  val fallbackModelName = name + fallbackName
  if (mappedValues.any { mappedValue -> mappedValue.toUpperCamelCase() == fallbackName }) {
    genError(
      "Discriminator hierarchy '$name' generated fallback case '$fallbackName' collides with a mapped tag. " +
        "Rename the tolerant enum fallback member with x-enum-varnames.",
    )
  }
  if (models.any { candidate -> candidate.name == fallbackModelName }) {
    genError(
      "Discriminator hierarchy '$name' generated fallback type '$fallbackModelName' collides with an existing model. " +
        "Rename the tolerant enum fallback member with x-enum-varnames.",
    )
  }

  return GeneratedDiscriminatorFallback(
    hierarchy = this,
    discriminatorProperty = discriminatorProperty,
    enumModel = enumModel,
    fallbackValue = fallbackValue,
    fallbackName = fallbackName,
    mappedValues = mappedValues,
    baseProperties =
      allDeclaredProperties(lookup)
        .filterNot { property -> property.name == discriminatorProperty.name },
  )
}

private fun GeneratedModel.discriminatorPropertyOrNull(
  discriminatorName: String,
  lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel?,
): GeneratedModelProperty? =
  properties.firstOrNull { property ->
    property.name == discriminatorName || property.serializationName == discriminatorName
  }
    ?: inherits.firstNotNullOfOrNull { inherited ->
      lookup(inherited)?.discriminatorPropertyOrNull(discriminatorName, lookup)
    }
    ?: discriminatorMappings.values.firstNotNullOfOrNull { type ->
      lookup(type)?.discriminatorPropertyOrNull(discriminatorName, lookup)
    }

private fun GeneratedModel.allDeclaredProperties(
  lookup: (io.outfoxx.sunday.generator.ir.GeneratedTypeRef) -> GeneratedModel?,
): List<GeneratedModelProperty> {
  val inheritedProperties =
    inherits
      .mapNotNull(lookup)
      .flatMap { inherited -> inherited.allDeclaredProperties(lookup) }
  val localWireNames = properties.map { property -> property.serializationName ?: property.name }.toSet()
  return inheritedProperties.filterNot { property ->
    (property.serializationName ?: property.name) in localWireNames
  } + properties
}
