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

import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex
import io.outfoxx.sunday.generator.ir.emit.resolvedTypeUri
import java.net.URI

/** Resolves problem URI literals before constructors, validation, and registrations consume them. */
fun GeneratedApi.resolveProblemUris(defaultBaseUri: String): GeneratedApi =
  resolveSchemaProblemUris { URI(defaultBaseUri) }.copy(
    problems =
      problems.map { problem ->
        val uri = problem.resolvedTypeUri(defaultBaseUri)
        problem.copy(
          typeUri = uri,
          statusBindings =
            problem.statusBindings.map { binding ->
              binding.copy(typeUri = binding.typeUri?.let { URI(defaultBaseUri).resolve(it).toString() })
            },
        )
      },
  )

/** Resolves source problem literals using the base of each declaring document before composition. */
internal fun GeneratedApi.resolveSchemaProblemUris(baseUri: (GeneratedModel) -> URI): GeneratedApi {
  val index = GeneratedModelIndex(models)

  fun isProblem(model: GeneratedModel): Boolean {
    val parent = model.inherits.firstOrNull()?.let(index::modelOrNull)
    if (parent != null) return isProblem(parent)
    return model.kind == GeneratedModel.Kind.OBJECT &&
      model.name.endsWith("Problem") &&
      model.properties.map { it.serializationName ?: it.name }.containsAll(listOf("type", "title", "status"))
  }

  fun resolve(
    model: GeneratedModel,
    value: String,
  ): String {
    val uri = URI(value)
    return if (uri.isAbsolute) value else baseUri(model).resolve(uri).toString()
  }
  val typeDiscriminatorModels =
    models
      .filter { it.discriminator == "type" }
      .flatMap { owner ->
        listOf(owner) + owner.discriminatorMappings.values.mapNotNull(index::modelOrNull)
      }.toSet()

  return copy(
    models =
      models.map { model ->
        val problem = isProblem(model)
        model.copy(
          properties =
            if (!problem) {
              model.properties
            } else {
              model.properties.map { property ->
                if ((property.serializationName ?: property.name) != "type") {
                  property
                } else {
                  property.copy(
                    defaultValue = property.defaultValue?.let { resolve(model, it) },
                    allowedValues = property.allowedValues?.map { if (it is String) resolve(model, it) else it },
                  )
                }
              }
            },
          discriminatorValue =
            if (problem && model in typeDiscriminatorModels) {
              model.discriminatorValue?.let { resolve(model, it) }
            } else {
              model.discriminatorValue
            },
          discriminatorMappings =
            if (model.discriminator != "type") {
              model.discriminatorMappings
            } else {
              model.discriminatorMappings.entries.associate { (value, ref) ->
                val target = index.modelOrNull(ref)
                (if (target != null && isProblem(target)) resolve(target, value) else value) to ref
              }
            },
        )
      },
  )
}
