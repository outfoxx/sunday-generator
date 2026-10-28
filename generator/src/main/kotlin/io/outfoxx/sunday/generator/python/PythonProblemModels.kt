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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Identifies source-schema problem hierarchies without changing their payload identities. */
internal class PythonProblemModels(
  val models: List<GeneratedModel>,
) {
  private val index = GeneratedModelIndex(models)
  val properties = GeneratedModelProperties(index::modelOrNull)

  fun model(reference: GeneratedTypeRef): GeneratedModel? = index.modelOrNull(reference)

  fun parent(model: GeneratedModel): GeneratedModel? = model.inherits.firstOrNull()?.let(index::modelOrNull)

  fun isProblem(model: GeneratedModel): Boolean {
    val parent = parent(model)
    if (parent != null) return isProblem(parent)
    return model.kind == GeneratedModel.Kind.OBJECT &&
      model.name.endsWith("Problem") &&
      model.properties.map { it.serializationName ?: it.name }.containsAll(listOf("type", "title", "status"))
  }

  fun typeUri(model: GeneratedModel): String? =
    properties
      .fields(model)
      .firstOrNull { it.wireName == "type" }
      ?.effective
      ?.allowedValues
      ?.singleOrNull() as? String

  // The runtime exception contract exposes URI strings, while Pydantic still validates URI syntax.
  fun normalizedModels(): List<GeneratedModel> =
    io.outfoxx.sunday.generator.ir.GeneratedProblemModels.normalizeFields(models).map { model ->
      if (!isProblem(model)) {
        model
      } else {
        model.copy(
          properties =
            model.properties.map { property ->
              if (property.name in setOf("type", "instance") && property.type.format in setOf("uri", "url", "iri")) {
                property.copy(type = property.type.copy(format = "problem-uri"))
              } else {
                property
              }
            },
        )
      }
    }
}

/** Keeps exception companions separate from the source schemas used by Pydantic adapters. */
internal fun GeneratedModel.pythonProblemModule(packageName: String): String = pythonModule(packageName) + "_problems"
