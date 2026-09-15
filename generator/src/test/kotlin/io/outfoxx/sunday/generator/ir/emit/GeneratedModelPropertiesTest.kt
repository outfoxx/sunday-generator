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
  fun `numeric validation follows scalar and array aliases with nullable elements`() {
    val integer = GeneratedTypeRef.scalar("integer")
    val item = GeneratedModel("Item", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(integer.copy(nullable = true)))
    val items = GeneratedModel("Items", GeneratedModel.Kind.ARRAY, aliases = listOf(GeneratedTypeRef.named("Item")))
    val alias =
      GeneratedModel("Alias", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(GeneratedTypeRef.named("Items")))
    val models = listOf(item, items, alias).associateBy { it.name }
    val properties = GeneratedModelProperties { models[it.name] }
    assertEquals(
      GeneratedModelProperties.NumericValidationTarget(false, false),
      properties.numericValidationTarget(integer, "Example.values"),
    )
    assertEquals(
      GeneratedModelProperties.NumericValidationTarget(false, true),
      properties.numericValidationTarget(GeneratedTypeRef.named("Item"), "Example.values"),
    )
    assertEquals(
      GeneratedModelProperties.NumericValidationTarget(true, true),
      properties.numericValidationTarget(GeneratedTypeRef.named("Alias"), "Example.values"),
    )
    val array =
      GeneratedTypeRef(
        GeneratedTypeRef.Kind.ARRAY,
        "array",
        nullable = true,
        arguments = listOf(integer),
        collection = io.outfoxx.sunday.generator.ir.GeneratedCollectionKind.SET,
      )
    assertEquals(
      GeneratedModelProperties.NumericValidationTarget(true, false),
      properties.numericValidationTarget(array, "Example.values"),
    )
    assertEquals(
      GeneratedModelProperties.NumericValidationTarget(false, false),
      properties.numericValidationTarget(GeneratedTypeRef.scalar("number"), "Example.values"),
    )
    val cycle =
      GeneratedModel("Cycle", GeneratedModel.Kind.SCALAR_ALIAS, aliases = listOf(GeneratedTypeRef.named("Cycle")))
    val cyclic = GeneratedModelProperties { cycle }
    for (type in listOf(
      GeneratedTypeRef.scalar("string"),
      GeneratedTypeRef.named("Missing"),
      array.copy(arguments = listOf(array)),
      array.copy(arguments = emptyList()),
    )) {
      val error =
        assertThrows(GenerationException::class.java) { properties.numericValidationTarget(type, "Example.values") }
      assertTrue(error.message.orEmpty().contains("Example.values"))
    }
    assertThrows(GenerationException::class.java) {
      cyclic.numericValidationTarget(GeneratedTypeRef.named("Cycle"), "Example.values")
    }
  }

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

  @Test
  fun `every compatible parent contributes effective constraints in either order`() {
    val property = GeneratedModelProperty("text", GeneratedTypeRef.scalar("string", nullable = true))
    val base = GeneratedModel("Base", GeneratedModel.Kind.OBJECT, properties = listOf(property))

    fun parent(
      name: String,
      validation: Map<String, String>,
      values: List<Any?>,
      required: Boolean,
    ) = GeneratedModel(
      name,
      GeneratedModel.Kind.OBJECT,
      inherits = listOf(GeneratedTypeRef.named("Base")),
      properties =
        listOf(
          property.copy(
            validation = validation,
            allowedValues = values,
            required = required,
            type = property.type.copy(nullable = !required),
          ),
        ),
    )
    val first = parent("First", mapOf("minLength" to "2"), listOf("ab", "abc", "abcd"), true)
    val second = parent("Second", mapOf("maxLength" to "3"), listOf("abc", "abcd"), false)
    for (parents in listOf(listOf(first, second), listOf(second, first))) {
      val child =
        GeneratedModel(
          "Child",
          GeneratedModel.Kind.OBJECT,
          inherits = parents.map { GeneratedTypeRef.named(it.name) },
        )
      val models = (parents + base + child).associateBy { it.name }
      val analysis = GeneratedModelProperties { models[it.name] }
      val field = analysis.fields(child).single()
      assertEquals(mapOf("minLength" to "2", "maxLength" to "3"), field.effective.validation)
      assertEquals(listOf("abc", "abcd"), field.effective.allowedValues)
      assertTrue(field.effective.required)
      assertEquals(false, field.effective.type.nullable)
      assertEquals(property, field.declaration)
      assertEquals(first.properties.single(), analysis.fields(first).single().effective)
      assertEquals(second.properties.single(), analysis.fields(second).single().effective)
      assertEquals(field, analysis.fields(child).single())
    }
  }

  @Test
  fun `inherited numeric and collection assertions intersect without weakening either parent`() {
    val property = GeneratedModelProperty("value", GeneratedTypeRef.scalar("number"))
    val left =
      property.copy(
        validation =
          mapOf(
            "minimum" to "1",
            "exclusiveMinimum" to "true",
            "maximum" to "10",
            "multipleOf" to "0.2",
            "minItems" to "1",
            "maxItems" to "5",
            "uniqueItems" to "false",
          ),
        allowedValues = listOf(0, false, 1, 2),
      )
    val right =
      property.copy(
        validation =
          mapOf(
            "minimum" to "0",
            "exclusiveMaximum" to "4",
            "multipleOf" to "0.3",
            "minItems" to "2",
            "maxItems" to "4",
            "uniqueItems" to "true",
          ),
        allowedValues = listOf(0.0, 1.0),
      )
    for ((a, b) in listOf(left to right, right to left)) {
      val result = GeneratedPropertyConstraints.intersect(a, b, "Child.value")
      assertEquals(
        mapOf(
          "minimum" to "1",
          "exclusiveMinimum" to "true",
          "maximum" to "4",
          "exclusiveMaximum" to "true",
          "multipleOf" to "0.6",
          "minItems" to "2",
          "maxItems" to "4",
          "uniqueItems" to "true",
        ),
        result.validation,
      )
      assertEquals(
        listOf("0", "1"),
        result.allowedValues!!.map {
          it
            .toString()
            .toBigDecimal()
            .stripTrailingZeros()
            .toPlainString()
        },
      )
    }
    val error =
      assertThrows(GenerationException::class.java) {
        GeneratedPropertyConstraints.intersect(
          property.copy(validation = mapOf("pattern" to "^a")),
          property.copy(validation = mapOf("pattern" to "z$")),
          "Child.value",
        )
      }
    assertTrue(error.message.orEmpty().contains("Child.value"))
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
