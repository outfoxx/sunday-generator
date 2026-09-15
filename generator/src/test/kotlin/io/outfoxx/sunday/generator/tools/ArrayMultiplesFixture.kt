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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.utils.TestAPIProcessing

/** RAML item facets plus IR controls for aliases, nullable elements, collection facets, and patches. */
fun arrayMultiplesFixture(): GeneratedApi {
  val uri = checkNotNull(object {}.javaClass.getResource("/raml/regression/array-item-multiples.raml")).toURI()
  val api = RamlToGeneratedApi().convert(TestAPIProcessing.process(uri))
  val integer = GeneratedTypeRef.scalar("integer")
  val items = GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(integer))

  fun model(
    name: String,
    type: GeneratedTypeRef,
    validation: Map<String, String> = mapOf("multipleOf" to "2"),
  ) = GeneratedModel(
    name,
    GeneratedModel.Kind.OBJECT,
    properties = listOf(GeneratedModelProperty("values", type, validation = validation)),
  )
  val parent = model("ArrayParent", items, emptyMap())
  val child =
    model("ArrayChild", items, mapOf("multipleOf" to "2", "minimum" to "-2", "maximum" to "4"))
      .copy(inherits = listOf(GeneratedTypeRef.named(parent.name)))
  val aliases =
    listOf(
      GeneratedModel("ItemAlias", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(integer)),
      GeneratedModel(
        "SetItemsAlias",
        GeneratedModel.Kind.ARRAY,
        aliases = listOf(integer),
        collection = GeneratedCollectionKind.SET,
      ),
      GeneratedModel(
        "ItemsAlias",
        GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.named("ItemAlias")),
      ),
      GeneratedModel(
        "ItemsAliasChain",
        GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.named("ItemsAlias")),
      ),
    )
  return api.copy(
    models =
      api.models + aliases +
        listOf(
          parent,
          child,
          model("AliasedMultiples", GeneratedTypeRef.named("ItemsAliasChain")),
          model(
            "NullableMultiples",
            items.copy(arguments = listOf(GeneratedTypeRef.named("ItemAlias", nullable = true))),
          ),
          model(
            "FractionMultiples",
            items.copy(arguments = listOf(GeneratedTypeRef.scalar("number"))),
            mapOf(
              "multipleOf" to "0.25",
            ),
          ),
          model("SetMultiples", GeneratedTypeRef.named("SetItemsAlias")),
          model(
            "SizedMultiples",
            items,
            mapOf(
              "multipleOf" to "2",
              "minItems" to "1",
              "maxItems" to "2",
              "uniqueItems" to "true",
            ),
          ),
          model("ArrayPatch", items.copy(nullable = true)).copy(patchable = true),
        ),
  )
}
