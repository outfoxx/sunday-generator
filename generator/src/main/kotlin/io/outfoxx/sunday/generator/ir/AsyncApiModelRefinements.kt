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
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedPropertyConstraints

/** Preserves enum declarations when AsyncAPI allOf descendants restrict their wire values. */
internal class AsyncApiModelRefinements(
  models: List<GeneratedModel>,
) {
  private val models = models.associateBy { it.name }
  private val refined = mutableMapOf<String, GeneratedModel>()
  private val visiting = mutableSetOf<String>()
  private val properties = GeneratedModelProperties { refined[it.name] ?: this.models[it.name] }

  /** Refines complete models because a parent property may reference a child still being materialized. */
  fun refine(): List<GeneratedModel> = models.values.map(::refine)

  private fun refine(model: GeneratedModel): GeneratedModel {
    refined[model.name]?.let { return it }
    if (!visiting.add(model.name)) genError("Cyclic model inheritance for '${model.name}'")
    try {
      model.inherits.mapNotNull { models[it.name] }.forEach(::refine)
      val inherited = properties.fields(model.copy(properties = emptyList())).associateBy { it.wireName }
      val result =
        model.copy(
          properties =
            model.properties.map { property ->
              val wireName = property.serializationName ?: property.name
              val parent = inherited[wireName] ?: return@map property
              val declaration = parent.declaration.type
              val enum = properties.declarationModel(declaration)
              if (enum?.kind != GeneratedModel.Kind.ENUM ||
                property.type.copy(nullable = false) !in
                listOf(declaration.copy(nullable = false), GeneratedTypeRef.scalar("string"))
              ) {
                return@map property
              }
              GeneratedPropertyConstraints.intersect(
                parent.effective,
                property.copy(type = declaration.copy(nullable = property.type.nullable)),
                "property '${model.name}.$wireName'",
              )
            },
        )
      return result.also { refined[model.name] = it }
    } finally {
      visiting.remove(model.name)
    }
  }
}
