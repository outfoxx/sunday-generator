/*
 * Copyright 2020 Outfox, Inc.
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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/**
 * Indexed view of a generated API for shared IR emitter lookup operations.
 */
class GeneratedApiIndex(
  val api: GeneratedApi,
) {

  private val models = GeneratedModelIndex(api.models)

  private val problems = GeneratedProblemIndex(api.problems)

  /**
   * Returns the model matching a generated type reference's name, scope, and optional source identity.
   */
  fun modelOrNull(type: GeneratedTypeRef): GeneratedModel? = models.modelOrNull(type)

  /**
   * Returns the problem matching a generated type reference's generated or source name and optional source identity.
   */
  fun problemOrNull(type: GeneratedTypeRef): GeneratedProblem? = problems.problemOrNull(type)

  /**
   * Returns operation-local models referenced by a service and owned by that service.
   */
  fun referencedScopedModels(service: GeneratedService): List<GeneratedModel> =
    buildList {
      val added = linkedSetOf<GeneratedModel>()

      fun add(type: GeneratedTypeRef) {
        val model = modelOrNull(type)
        if (model != null && model.scope?.service == service.name && !model.isFreeformObject) {
          if (added.add(model)) {
            add(model)
          }
        }
        type.arguments.forEach(::add)
      }

      service.operations.forEach { operation ->
        operation.parameters.forEach { parameter -> add(parameter.type) }
        operation.queryString?.let(::add)
        operation.requestBody?.type?.let(::add)
        operation.requestBody
          ?.payloads
          .orEmpty()
          .forEach { payload -> add(payload.type) }
        operation.responses.forEach { response ->
          response.type?.let(::add)
          response.payloads.forEach { payload -> add(payload.type) }
        }
      }
    }
}

/**
 * Returns the model matching a generated type reference from an API index.
 */
fun GeneratedTypeRef.modelOrNull(index: GeneratedApiIndex): GeneratedModel? = index.modelOrNull(this)

/**
 * Returns all non-union type references inside a possibly nested union reference in declaration order.
 */
fun GeneratedTypeRef.flattenedUnionTypes(): List<GeneratedTypeRef> =
  if (kind == GeneratedTypeRef.Kind.UNION) {
    arguments.flatMap { argument -> argument.flattenedUnionTypes() }
  } else {
    listOf(this)
  }

private val GeneratedModel.isFreeformObject: Boolean
  get() =
    kind == GeneratedModel.Kind.OBJECT &&
      closed != true &&
      additionalProperties?.allowed != false &&
      properties.isEmpty() &&
      patternProperties.isEmpty()
