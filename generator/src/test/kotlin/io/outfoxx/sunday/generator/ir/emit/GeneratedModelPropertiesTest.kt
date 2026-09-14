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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeneratedModelPropertiesTest {
  @Test
  fun `retained nominal conflicts have a property diagnostic`() {
    val statusA = GeneratedModel("StatusA", GeneratedModel.Kind.ENUM, values = listOf("a", "b"))
    val statusB = GeneratedModel("StatusB", GeneratedModel.Kind.ENUM, values = listOf("b", "c"))
    val first = model("First", GeneratedTypeRef.named("StatusA"))
    val second = model("Second", GeneratedTypeRef.named("StatusB"))
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        inherits =
          listOf(
            GeneratedTypeRef.named("First"),
            GeneratedTypeRef.named("Second"),
          ),
      )
    val models = listOf(statusA, statusB, first, second, child).associateBy { it.name }
    val error =
      assertThrows(GenerationException::class.java) {
        GeneratedModelProperties { models[it.name] }.fields(child)
      }
    assertTrue(error.message.orEmpty().contains("Child.value"), error.message)
  }

  @Test
  fun `recursive collection declarations share storage without expanding their elements`() {
    val nodes =
      GeneratedModel("Nodes", GeneratedModel.Kind.ARRAY, aliases = listOf(GeneratedTypeRef.named("Nodes")))
    val first = model("First", GeneratedTypeRef.named("Nodes"))
    val second = model("Second", GeneratedTypeRef.named("Nodes"))
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named("First"), GeneratedTypeRef.named("Second")),
      )
    val models = listOf(nodes, first, second, child).associateBy { it.name }
    val properties = GeneratedModelProperties { models[it.name] }
    assertEquals(
      GeneratedTypeRef.named("Nodes"),
      properties
        .fields(child)
        .single()
        .storage.type,
    )
  }

  @Test
  fun `aliases share storage while single parent structural overrides remain effective`() {
    val alias =
      GeneratedModel("Text", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(GeneratedTypeRef.scalar("string")))
    val first = model("First", GeneratedTypeRef.named("Text"))
    val second = model("Second", GeneratedTypeRef.scalar("string"))
    val child =
      GeneratedModel(
        "Child",
        GeneratedModel.Kind.OBJECT,
        inherits =
          listOf(
            GeneratedTypeRef.named("First"),
            GeneratedTypeRef.named("Second"),
          ),
      )
    val refined =
      model(
        "Refined",
        GeneratedTypeRef.scalar("integer"),
      ).copy(inherits = listOf(GeneratedTypeRef.named("First")))
    val models = listOf(alias, first, second, child, refined).associateBy { it.name }
    val properties = GeneratedModelProperties { models[it.name] }
    assertEquals(
      GeneratedTypeRef.named("Text"),
      properties
        .fields(child)
        .single()
        .storage.type,
    )
    assertEquals(
      GeneratedTypeRef.scalar("integer"),
      properties
        .fields(refined)
        .single()
        .storage.type,
    )
    assertEquals(
      GeneratedTypeRef.named("Text"),
      properties
        .fields(first)
        .single()
        .storage.type,
    )
  }

  private fun model(
    name: String,
    type: GeneratedTypeRef,
  ) = GeneratedModel(
    name,
    GeneratedModel.Kind.OBJECT,
    properties = listOf(GeneratedModelProperty("value", type)),
  )
}
