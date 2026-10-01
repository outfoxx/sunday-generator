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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Independent ancestor and descendant assertions, including a leaf that adds no local assertion. */
internal fun inheritedAdditionalPropertiesModels(): List<GeneratedModel> {
  val array =
    GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(GeneratedTypeRef.scalar("integer")))
  val base =
    GeneratedModel(
      "DynamicBase",
      GeneratedModel.Kind.OBJECT,
      properties = listOf(GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"))),
      additionalProperties = GeneratedAdditionalProperties(type = array, validation = mapOf("minItems" to "2")),
    )
  val child =
    base.copy(
      name = "DynamicChild",
      properties = emptyList(),
      inherits = listOf(GeneratedTypeRef.named(base.name)),
      additionalProperties = base.additionalProperties?.copy(validation = mapOf("maxItems" to "3")),
    )
  val leaf =
    child.copy(
      name = "DynamicLeaf",
      inherits = listOf(GeneratedTypeRef.named(child.name)),
      additionalProperties = null,
    )
  val holder =
    GeneratedModel(
      "DynamicHolder",
      GeneratedModel.Kind.OBJECT,
      properties = listOf(GeneratedModelProperty("payload", GeneratedTypeRef.named(leaf.name), required = true)),
    )
  return listOf(base, child, leaf, holder)
}
