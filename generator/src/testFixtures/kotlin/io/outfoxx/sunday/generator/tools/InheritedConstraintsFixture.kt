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
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef

/** Compatible IR parent declarations without a frontend-synthesized child override. */
fun inheritedConstraintsFixture(): GeneratedApi {
  val text = GeneratedModelProperty("text", GeneratedTypeRef.scalar("string"))
  val count = GeneratedModelProperty("count", GeneratedTypeRef.named("CountAlias"), defaultValue = "2")
  val amount = GeneratedModelProperty("amount", GeneratedTypeRef.scalar("number"))
  val first =
    GeneratedModel(
      "First",
      GeneratedModel.Kind.OBJECT,
      properties =
        listOf(
          text.copy(validation = mapOf("minLength" to "2")),
          count,
          amount,
        ),
    )
  val second =
    GeneratedModel(
      "Second",
      GeneratedModel.Kind.OBJECT,
      properties =
        listOf(
          text.copy(validation = mapOf("maxLength" to "3")),
          count.copy(validation = mapOf("multipleOf" to "2")),
          amount.copy(validation = mapOf("multipleOf" to "0.1")),
        ),
    )
  val children =
    listOf("Child" to listOf(first, second), "Reversed" to listOf(second, first)).map { (name, parents) ->
      GeneratedModel(name, GeneratedModel.Kind.OBJECT, inherits = parents.map { GeneratedTypeRef.named(it.name) })
    }
  val patch =
    GeneratedModel(
      "MultiplePatch",
      GeneratedModel.Kind.OBJECT,
      patchable = true,
      properties = listOf(count.copy(type = count.type.copy(nullable = true), validation = mapOf("multipleOf" to "2"))),
    )
  return GeneratedApi(
    name = "Inherited constraints",
    source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
    models =
      listOf(first, second, patch) + children +
        GeneratedModel(
          "CountAlias",
          GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("integer")),
        ),
  )
}
