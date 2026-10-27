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

/** Applies the RFC standard-member contract only to recognized Problem hierarchies. */
object GeneratedProblemModels {
  /** Keeps authored extension constraints and all ordinary models unchanged. */
  fun normalizeFields(
    models: List<GeneratedModel>,
    stringReferences: Boolean = false,
  ): List<GeneratedModel> {
    val index = GeneratedModelIndex(models)

    fun root(model: GeneratedModel): GeneratedModel =
      model.inherits
        .firstOrNull()
        ?.let(index::modelOrNull)
        ?.let(::root) ?: model
    return models.map { model ->
      val base = root(model)
      if (model.kind != GeneratedModel.Kind.OBJECT ||
        !base.name.endsWith("Problem") ||
        !base.properties.map { it.serializationName ?: it.name }.containsAll(listOf("type", "title", "status"))
      ) {
        model
      } else {
        model.copy(
          properties =
            model.properties.map { property ->
              when (property.serializationName ?: property.name) {
                "type" ->
                  property.copy(
                    required = false,
                    type =
                      property.type.copy(
                        nullable = false,
                        format = if (stringReferences) "problem-uri" else property.type.format,
                      ),
                    defaultValue = "about:blank",
                  )
                "title", "status", "detail", "instance" ->
                  property.copy(
                    required = false,
                    type =
                      property.type.copy(
                        nullable = false,
                        format =
                          if (stringReferences && (property.serializationName ?: property.name) == "instance") {
                            "problem-uri"
                          } else {
                            property.type.format
                          },
                      ),
                  )
                else -> property
              }
            },
        )
      }
    }
  }
}
