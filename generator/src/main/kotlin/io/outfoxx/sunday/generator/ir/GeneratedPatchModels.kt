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

/** Expands schema patch capabilities into ordinary and presence-preserving model declarations. */
internal object GeneratedPatchModels {

  /** Projects patch-only IR declarations, including RAML models, into optional fields without defaults. */
  fun normalizeFields(models: List<GeneratedModel>): List<GeneratedModel> {
    val declarations = models.associateBy { it.name }
    val properties = GeneratedModelProperties { declarations[it.name] }
    return models.map { model ->
      if (!model.patchable) {
        model
      } else {
        model.copy(properties = patchFields(model, properties))
      }
    }
  }

  /** Reads the native annotation without silently accepting malformed declarations. */
  fun annotation(
    value: Any?,
    location: String,
  ): Boolean =
    when (value) {
      null -> false
      is Boolean -> value
      else -> genError("$location x-sunday-patchable must be a boolean")
    }

  /**
   * Adds companions for annotated schemas and merge-patch request bodies, independently of the HTTP method.
   * RAML's explicitly declared patch types are reused rather than expanded into another companion.
   */
  fun materialize(
    api: GeneratedApi,
    reusePatchTypes: Boolean = false,
    autoPatchable: Boolean = true,
  ): GeneratedApi {
    val mergePatchTypes =
      api.services
        .flatMap { it.operations }
        .mapNotNull { it.requestBody }
        .flatMap { it.options() }
        .filter { option -> option.mediaTypes.any { it.isMergePatch() } }
        .map { it.type }
        .takeIf { autoPatchable }
        .orEmpty()
    if (mergePatchTypes.isEmpty() && (reusePatchTypes || api.models.none { it.patchable })) return api
    val models = api.models.associateBy { it.name }
    val properties = GeneratedModelProperties { models[it.name] }
    val allocator = OpenApiNameAllocator(models.keys + api.problems.map { it.name })
    val patchNames = linkedMapOf<String, String>()

    fun parents(model: GeneratedModel): List<GeneratedModel> =
      (model.inherits + model.aliases.takeIf { model.kind == GeneratedModel.Kind.SCALAR_ALIAS }.orEmpty())
        .mapNotNull { models[it.name] }

    fun patchable(
      model: GeneratedModel,
      visited: Set<String> = emptySet(),
    ): Boolean =
      model.name !in visited &&
        (model.patchable || parents(model).any { patchable(it, visited + model.name) })

    fun reserve(model: GeneratedModel) {
      if (model.name in patchNames) return
      if (reusePatchTypes && model.patchable) return
      val objectAlias =
        model.kind == GeneratedModel.Kind.SCALAR_ALIAS &&
          !model.nominal &&
          properties.declarationModel(GeneratedTypeRef.named(model.name))?.kind == GeneratedModel.Kind.OBJECT
      if (model.kind != GeneratedModel.Kind.OBJECT && !objectAlias) {
        genError("Patchable model '${model.name}' must be an object schema")
      }
      if (model.discriminator != null || model.discriminatorMappings.isNotEmpty() || model.externallyDiscriminated) {
        genError(
          "Patchable model '${model.name}' must not be a discriminator hierarchy; patch a containing object instead",
        )
      }
      patchNames[model.name] = allocator.allocate("${model.name}Patch")
      parents(model).forEach(::reserve)
    }

    val marked = if (reusePatchTypes) emptyList() else api.models.filter { patchable(it) }
    marked.forEach(::reserve)
    mergePatchTypes.forEach { reference ->
      val model = models[reference.name]
      if (reference.kind != GeneratedTypeRef.Kind.NAMED || model == null) {
        genError("Merge-patch request body '${reference.name}' must reference an object schema")
      }
      reserve(model)
    }
    val patches =
      patchNames.map { (name, patchName) ->
        val model = models.getValue(name)
        model.copy(
          name = patchName,
          patchable = true,
          properties = patchFields(model, properties),
          inherits = model.inherits.map { it.copy(name = patchNames[it.name] ?: it.name) },
          aliases = model.aliases.map { it.copy(name = patchNames[it.name] ?: it.name) },
          targets = model.targets.mapValues { (_, target) -> target.copy(typeName = null, implementation = null) },
          nested = null,
        )
      }

    val selectedTypes = (marked.map { it.name } + mergePatchTypes.map { it.name }).toSet()
    val selectedPatchNames = patchNames.filterKeys { it in selectedTypes }
    return api.copy(
      models = (if (reusePatchTypes) api.models else api.models.map { it.copy(patchable = false) }) + patches,
      services =
        api.services.map { service ->
          service.copy(
            operations =
              service.operations.map { operation ->
                operation.copy(requestBody = operation.requestBody?.selectPatchTypes(selectedPatchNames))
              },
          )
        },
    )
  }

  private fun patchFields(
    model: GeneratedModel,
    properties: GeneratedModelProperties,
  ): List<GeneratedModelProperty> {
    val fields = properties.fields(model).associateBy { it.wireName }
    return model.properties.map { property ->
      val field = fields.getValue(property.serializationName ?: property.name)
      // Inherited required members remain protected even if a local declaration is optional.
      // Retain explicit false after projection has made every patch field omittable.
      val deletionAllowed =
        field.declaration.patchDeletionAllowed != false &&
          field.effective.patchDeletionAllowed != false &&
          !field.declaration.required &&
          !field.effective.required
      property.copy(
        required = false,
        defaultValue = null,
        patchDeletionAllowed = deletionAllowed,
        type = property.type.copy(nullable = deletionAllowed),
        allowedValues =
          field.effective.allowedValues
            ?.filterNotNull()
            ?.let { if (deletionAllowed) it + null else it },
      )
    }
  }

  private fun GeneratedPayload.selectPatchTypes(patchNames: Map<String, String>): GeneratedPayload {
    val options = options()
    val selected =
      options.flatMap { option ->
        if (option.mediaTypes.none { it.isMergePatch() }) {
          listOf(option)
        } else {
          option.mediaTypes.groupBy { it.isMergePatch() }.map { (mergePatch, mediaTypes) ->
            option.copy(
              type =
                if (mergePatch) {
                  option.type.copy(
                    name = patchNames[option.type.name] ?: option.type.name,
                  )
                } else {
                  option.type
                },
              mediaTypes = mediaTypes,
            )
          }
        }
      }
    if (selected == options) return this
    return copy(
      type = selected.first().type,
      mediaTypes = selected.first().mediaTypes,
      payloads = selected.takeIf { it.size > 1 }.orEmpty(),
    )
  }

  private fun GeneratedPayload.options(): List<GeneratedPayloadOption> =
    payloads.ifEmpty { listOf(GeneratedPayloadOption(type, mediaTypes, examples)) }

  private fun String.isMergePatch(): Boolean =
    substringBefore(';').trim().equals("application/merge-patch+json", ignoreCase = true)
}
