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

/** Structural identity excluding source provenance and documentation. */
internal fun GeneratedModel.compositionSignature(): GeneratedModel =
  copy(
    source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "urn:sunday:structural-signature"),
    declarationUri = null,
    placementScopes = emptyList(),
    placementDeclarations = emptyList(),
    ownerUris = emptySet(),
    properties = properties.map { property -> property.compositionSignature() },
    aliases = aliases.map { alias -> alias.compositionSignature() },
    patchOf = patchOf?.compositionSignature(),
    additionalProperties = additionalProperties?.compositionSignature(),
    patternProperties = patternProperties.map { patternProperty -> patternProperty.compositionSignature() },
    targets = targets.mapValues { (_, target) -> target.compositionSignature() },
    nested = nested?.compositionSignature(),
    inherits = inherits.map { inherited -> inherited.compositionSignature() },
    discriminatorMappings = discriminatorMappings.mapValues { (_, type) -> type.compositionSignature() },
    examples = listOf(),
    documentation = null,
  )

private fun GeneratedModelProperty.compositionSignature(): GeneratedModelProperty =
  copy(
    type = type.compositionSignature(),
    targets = targets.mapValues { (_, target) -> target.compositionSignature() },
    examples = listOf(),
    documentation = null,
  )

private fun GeneratedAdditionalProperties.compositionSignature(): GeneratedAdditionalProperties =
  copy(
    type = type?.compositionSignature(),
    documentation = null,
  )

private fun GeneratedPatternProperty.compositionSignature(): GeneratedPatternProperty =
  copy(
    type = type.compositionSignature(),
    documentation = null,
  )

private fun GeneratedNestedType.compositionSignature(): GeneratedNestedType =
  copy(enclosedIn = enclosedIn?.compositionSignature())

private fun GeneratedTarget.compositionSignature(): GeneratedTarget = this

private fun GeneratedTypeRef.compositionSignature(): GeneratedTypeRef =
  copy(
    // Collection kind and arguments define maps; readers use different descriptive names.
    name = if (kind == GeneratedTypeRef.Kind.MAP) "map" else name,
    arguments = arguments.map { argument -> argument.compositionSignature() },
    source = null,
  )

/** Operation behavior excludes provenance; bound identities are checked after placement resolution. */
internal fun GeneratedOperation.compositionSignature(): GeneratedOperation =
  GeneratedTypeReferenceRewriter(null, { reference, _ -> reference.compositionSignature() })
    .operation(copy(declarationUri = null, placementScopes = emptyList(), placementDeclarations = emptyList()))
