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

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedService

/** Finds concrete source-schema problems returned by a service, keyed by their fixed type URI. */
fun GeneratedService.referencedSchemaProblems(index: GeneratedApiIndex): Map<String, GeneratedModel> {
  val properties = GeneratedModelProperties(index::modelOrNull)

  fun root(model: GeneratedModel): GeneratedModel =
    model.inherits
      .firstOrNull()
      ?.let(index::modelOrNull)
      ?.let(::root) ?: model
  return operations
    .flatMap { operation -> operation.responses }
    .filter { it.status == null || it.status >= 400 }
    .flatMap { response -> response.payloads.map { it.type } + listOfNotNull(response.type) }
    .mapNotNull(index::modelOrNull)
    .filter { model ->
      val root = root(model)
      root.name.endsWith("Problem") &&
        root.properties
          .map { it.serializationName ?: it.name }
          .containsAll(listOf("type", "title", "status"))
    }.mapNotNull { model ->
      val type =
        properties
          .fields(model)
          .firstOrNull {
            it.wireName == "type"
          }?.effective
          ?.allowedValues
          ?.singleOrNull() as? String
      type?.let { it to model }
    }.toMap()
}
