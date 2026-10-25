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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/**
 * Finds unambiguous custom problem registrations for a client transport.
 * Standard about:blank problems use the runtime fallback because their meaning depends on status.
 */
fun GeneratedService.referencedSchemaProblems(index: GeneratedApiIndex): Map<String, GeneratedModel> =
  schemaProblemResponses(index)
    .filterNot { (uri, _) -> uri == "about:blank" }
    .groupBy({ it.first }, { it.second })
    .mapValues { (uri, models) ->
      val distinct = models.distinct()
      require(distinct.size == 1) {
        "HTTP Problem responses in service '$name' map type URI '$uri' to multiple problems: " +
          distinct.map { it.name }.sorted().joinToString() + "; declare distinct fixed type URIs"
      }
      distinct.single()
    }

// Response shape validation also applies to servers, which do not need a URI-keyed client registry.
private fun GeneratedService.schemaProblemResponses(index: GeneratedApiIndex): List<Pair<String, GeneratedModel>> {
  val properties = GeneratedModelProperties(index::modelOrNull)

  fun root(model: GeneratedModel): GeneratedModel =
    model.inherits
      .firstOrNull()
      ?.let(index::modelOrNull)
      ?.let(::root) ?: model

  fun isProblem(model: GeneratedModel): Boolean =
    model.kind == GeneratedModel.Kind.OBJECT &&
      root(model).let { root ->
        root.name.endsWith("Problem") &&
          root.properties.map { it.serializationName ?: it.name }.containsAll(listOf("type", "title", "status"))
      }

  fun typeUri(model: GeneratedModel): String? =
    properties
      .fields(model)
      .firstOrNull { it.wireName == "type" }
      ?.effective
      ?.allowedValues
      ?.singleOrNull() as? String

  fun containsProblem(
    model: GeneratedModel,
    visited: Set<GeneratedModel> = emptySet(),
  ): Boolean =
    model !in visited &&
      (
        isProblem(model) ||
          model.kind == GeneratedModel.Kind.UNION &&
          model.aliases.mapNotNull(index::modelOrNull).any { containsProblem(it, visited + model) }
      )

  fun problems(model: GeneratedModel): List<GeneratedModel> {
    if (isProblem(model)) return listOf(model)
    if (model.kind != GeneratedModel.Kind.UNION || !containsProblem(model)) return emptyList()
    val alternatives = model.aliases.mapNotNull(index::modelOrNull)
    require(
      model.aliases.all { !it.nullable && it.kind == GeneratedTypeRef.Kind.NAMED } &&
        alternatives.size == model.aliases.size &&
        alternatives.all(::isProblem),
    ) {
      "HTTP Problem response union '${model.name}' must contain only direct references to concrete problem objects"
    }
    require(
      model.properties.isEmpty() &&
        model.validation.isEmpty() &&
        model.closed != true &&
        model.additionalProperties == null &&
        model.patternProperties.isEmpty(),
    ) {
      "HTTP Problem response union '${model.name}' cannot add constraints; declare them on its problem alternatives"
    }
    alternatives.forEach { alternative ->
      require(index.api.models.none { child -> child.inherits.any { index.modelOrNull(it) == alternative } }) {
        "HTTP Problem response union '${model.name}' references non-leaf problem '${alternative.name}'; reference concrete leaves"
      }
      require(typeUri(alternative) != null) {
        "HTTP Problem response union '${model.name}' requires a fixed type URI on '${alternative.name}'"
      }
    }
    require(alternatives.map(::typeUri).distinct().size == alternatives.size) {
      "HTTP Problem response union '${model.name}' requires distinct type URIs for its alternatives"
    }
    require(
      model.discriminator == null ||
        model.discriminator == "type" &&
        model.discriminatorMappings.size == alternatives.size &&
        model.discriminatorMappings.all { (uri, reference) ->
          index.modelOrNull(reference)?.let { it in alternatives && typeUri(it) == uri } == true
        },
    ) {
      "HTTP Problem response union '${model.name}' discriminator must map each fixed type URI to its problem alternative"
    }
    return alternatives
  }

  return operations
    .flatMap { it.responses }
    .filter { it.status == null || it.status >= 400 }
    .flatMap { response -> response.payloads.map { it.type } + listOfNotNull(response.type) }
    .mapNotNull(index::modelOrNull)
    .flatMap(::problems)
    .mapNotNull { model -> typeUri(model)?.let { it to model } }
}

/** Validates the supported HTTP Problem response union shape consistently for every emitter. */
fun GeneratedApiIndex.validateSchemaProblemResponses() {
  api.services.forEach { it.schemaProblemResponses(this) }
}
