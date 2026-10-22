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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.ir.emit.GeneratedModelIndex
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

/** Complete placement identities shared by declaration deduplication and reference compatibility. */
internal class GeneratedDeclarationIdentities(
  models: List<GeneratedModel>,
  private val language: String?,
  private val role: String,
) {
  private val index = GeneratedModelIndex(models)

  fun declaration(model: GeneratedModel): List<Any?> =
    (
      language?.let(
        ::listOf,
      ) ?: listOf("kotlin", "swift", "typescript", "python")
    ).map { identity(model, it, emptySet()) }

  private fun identity(
    model: GeneratedModel,
    language: String,
    active: Set<GeneratedModel>,
  ): List<Any?> {
    require(model !in active) { "Cyclic nesting for '${model.declarationUri ?: model.name}'" }
    val target = model.targets[language + role.replaceFirstChar(Char::uppercase)] ?: model.targets[language]
    val parent =
      model.nested
        ?.enclosedIn
        ?.let(index::modelOrNull)
        .takeUnless { language == "python" }
    return listOf(
      language,
      target?.modelPackageName,
      target?.modelModuleName,
      target?.typeName,
      target?.moduleName.takeIf { target?.typeName != null },
      parent?.let { identity(it, language, active + model) },
      model.scope,
      model.nested?.name.takeIf { parent != null }?.let {
        if (language == "typescript") it.toUpperCamelCase() else it
      } ?: model.name.toUpperCamelCase(),
    )
  }

  fun structure(model: GeneratedModel): GeneratedModel =
    model.compositionSignature().copy(
      targets =
        model.targets
          .filterKeys {
            language == null ||
              it == language ||
              it == language + role.replaceFirstChar(Char::uppercase)
          }.mapValues { (_, target) ->
            target.copy(
              packageName = null,
              moduleName = target.moduleName.takeIf { target.typeName != null },
              modelPackageName = null,
              modelModuleName = null,
            )
          },
    )

  fun reference(reference: GeneratedTypeRef): List<Any?> =
    listOf(
      reference.kind,
      if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
        index.modelOrNull(reference)?.let(::declaration) ?: listOf(reference.name, reference.scope, reference.source)
      } else {
        if (reference.kind == GeneratedTypeRef.Kind.MAP) "map" else reference.name
      },
      reference.arguments.map(::reference),
    )

  fun references(model: GeneratedModel): List<Any?> =
    buildList {
      GeneratedTypeReferenceRewriter(model.source, { reference, _ ->
        add(reference(reference))
        reference
      }).model(model.copy(placementDeclarations = emptyList()))
    }

  fun references(operation: GeneratedOperation): List<Any?> =
    buildList {
      GeneratedTypeReferenceRewriter(null, { reference, _ ->
        add(reference(reference))
        reference
      }).operation(operation.copy(placementDeclarations = emptyList()))
    }
}
