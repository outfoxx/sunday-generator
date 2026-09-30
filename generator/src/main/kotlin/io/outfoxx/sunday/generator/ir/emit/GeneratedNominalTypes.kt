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

/** Shared interpretation of nominal scalars and unions composed exclusively of those scalars. */
internal class GeneratedNominalTypes(
  private val modelFor: (GeneratedTypeRef) -> GeneratedModel?,
) {
  private val properties = GeneratedModelProperties { modelFor(it)?.copy(nominal = false) }

  fun scalar(model: GeneratedModel): Scalar {
    val reference = GeneratedTypeRef.named(model.name, scope = model.scope, source = model.source)
    val raw = properties.declarationType(reference)
    if (model.kind != GeneratedModel.Kind.SCALAR_ALIAS ||
      raw.kind != GeneratedTypeRef.Kind.SCALAR ||
      raw.name !in setOf("string", "integer", "number", "boolean")
    ) {
      genError("Nominal model '${model.name}' must wrap a string, integer, number, or boolean scalar")
    }
    val field =
      properties
        .fields(
          GeneratedModel(
            name = "${model.name}RawValue",
            kind = GeneratedModel.Kind.OBJECT,
            properties = listOf(GeneratedModelProperty("value", reference, required = true)),
          ),
        ).single()
        .effective
    // Nominal strings retain their wire representation, including formatted strings.
    val type = raw.copy(nullable = false, format = raw.format.takeUnless { raw.name == "string" })
    return Scalar(type, field.copy(type = type), properties.patterns(field))
  }

  fun branches(model: GeneratedModel): List<GeneratedModel> {
    if (model.kind != GeneratedModel.Kind.UNION || model.aliases.isEmpty()) return emptyList()
    val branches = model.aliases.map { nominalModel(it) ?: return emptyList() }
    return branches.takeIf { it.all { branch -> branch.nominal } }.orEmpty()
  }

  private fun nominalModel(reference: GeneratedTypeRef): GeneratedModel? {
    var model = modelFor(reference) ?: return null
    val visited = mutableSetOf<GeneratedModel>()
    while (!model.nominal && model.kind == GeneratedModel.Kind.SCALAR_ALIAS && visited.add(model)) {
      model = model.aliases.singleOrNull()?.let(modelFor) ?: return null
    }
    return model.takeIf { it.nominal }
  }

  fun unionType(model: GeneratedModel): GeneratedTypeRef {
    val types = branches(model).map { scalar(it).type }.distinct()
    if (types.size != 1) {
      genError("Nominal scalar union '${model.name}' requires branches with the same scalar representation")
    }
    return types.single()
  }

  /** Effective scalar restrictions, including every pattern inherited through aliases. */
  data class Scalar(
    val type: GeneratedTypeRef,
    val property: GeneratedModelProperty,
    val patterns: List<String>,
  )
}
