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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OpenApiSchemaCompatibilityTest {
  @ParameterizedTest
  @ValueSource(strings = ["description", "summary", "title", "example", "examples", "externalDocs", "\$comment"])
  fun `documentary changes preserve inheritance and both original annotations`(annotation: String) {
    val parentProperty = mapOf("type" to "string", annotation to "Parent")
    val childProperty = mapOf("type" to "string", annotation to "Child")
    val parent = mapOf("type" to "object", "required" to listOf("id"), "properties" to mapOf("id" to parentProperty))
    val child = mapOf("allOf" to listOf(ref("Parent")), "properties" to mapOf("id" to childProperty))
    val composition = OpenApiSchemaComposition(mapOf("Parent" to parent))
    val model = composition.model(child)
    assertEquals(listOf("Parent"), model.parents)
    assertEquals(emptyMap<String, Any?>(), model.localSchema["properties"])
    assertEquals(listOf("id"), model.schema["required"])
    assertEquals(childProperty, (model.schema["properties"] as Map<*, *>)["id"])
    assertEquals(parentProperty, (composition.resolve(parent)["properties"] as Map<*, *>)["id"])
  }

  @Test
  fun `only schema locations ignore documentary keys`() {
    val comparison = OpenApiSchemaCompatibility(OpenApiSchemaComposition(emptyMap()))
    val before = mapOf("type" to "string", "description" to "Parent")
    val after = before + ("description" to "Child")
    for (key in listOf("properties", "patternProperties", "\$defs", "definitions", "dependentSchemas")) {
      assertTrue(
        comparison.equivalent(
          mapOf(key to mapOf("description" to before)),
          mapOf(
            key to mapOf("description" to after),
          ),
        ),
        key,
      )
      assertFalse(
        comparison.equivalent(
          mapOf(key to mapOf("description" to before)),
          mapOf(
            key to emptyMap<String, Any?>(),
          ),
        ),
        key,
      )
    }
    for (key in listOf("allOf", "oneOf", "anyOf", "prefixItems")) {
      assertTrue(comparison.equivalent(mapOf(key to listOf(before)), mapOf(key to listOf(after))), key)
    }
    for (key in listOf(
      "items",
      "additionalProperties",
      "not",
      "contains",
      "propertyNames",
      "unevaluatedItems",
      "unevaluatedProperties",
    )) {
      assertTrue(comparison.equivalent(mapOf(key to before), mapOf(key to after)), key)
    }
    for (key in listOf("if", "then", "else")) {
      val conditional = mapOf("if" to before, "then" to before, "else" to before)
      assertTrue(comparison.equivalent(conditional, conditional + (key to after)), key)
    }
    for (key in listOf("enum", "const", "default", "x-sunday-example", "unknown")) {
      val left = if (key == "enum") listOf(before) else before
      val right = if (key == "enum") listOf(after) else after
      assertFalse(comparison.equivalent(mapOf(key to left), mapOf(key to right)), key)
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = ["default", "deprecated", "readOnly", "writeOnly", "discriminator", "x-sunday-name", "unknown"],
  )
  fun `generation metadata remains significant`(key: String) {
    val comparison = OpenApiSchemaCompatibility(OpenApiSchemaComposition(emptyMap()))
    val before = mapOf("type" to "string", key to false)
    assertFalse(comparison.equivalent(before, before + (key to true)), key)
    assertFalse(comparison.equivalent(before, mapOf("type" to "string")), key)
  }

  @Test
  fun `aliases and composition compare effective constraints`() {
    val text = mapOf("type" to "string", "minLength" to 2)
    val comparison = OpenApiSchemaCompatibility(OpenApiSchemaComposition(mapOf("Text" to text, "Alias" to ref("Text"))))
    assertTrue(comparison.equivalent(ref("Alias"), text + ("description" to "Use")))
    assertTrue(comparison.equivalent(ref("Text"), mapOf("allOf" to listOf(text, mapOf("minLength" to 1)))))
    assertFalse(comparison.equivalent(ref("Text"), text + ("minLength" to 3)))
    assertFalse(comparison.equivalent(text, text + ("nullable" to true)))
  }

  @Test
  fun `recursive comparisons stay conservative and local to a composition`() {
    val left = mapOf("type" to "object", "properties" to mapOf("next" to ref("Left")))
    val right = mapOf("type" to "object", "properties" to mapOf("next" to ref("Right")))
    val comparison = OpenApiSchemaCompatibility(OpenApiSchemaComposition(mapOf("Left" to left, "Right" to right)))
    repeat(2) {
      assertTrue(comparison.equivalent(left, left + ("description" to "Recursive use")))
      assertFalse(comparison.equivalent(left, right))
    }
    val independent =
      OpenApiSchemaCompatibility(OpenApiSchemaComposition(mapOf("Left" to left, "Right" to ref("Left"))))
    assertTrue(independent.equivalent(left, right))
  }

  @Test
  fun `matching wrappers compare and merge without resolving their reference`() {
    // Resolving this target would fail, even when no enclosing composition is active.
    val comparison = OpenApiSchemaCompatibility(OpenApiSchemaComposition(mapOf("Node" to ref("Node"))))

    fun wrapped(description: String) = mapOf("allOf" to listOf(ref("Node") + ("description" to description)))
    val before =
      mapOf(
        "allOf" to listOf(wrapped("Parent reference")),
        "title" to "Inherited",
        "description" to "Parent wrapper",
      )
    val after =
      mapOf(
        "allOf" to listOf(wrapped("Child reference")),
        "description" to "Child wrapper",
      )
    repeat(2) {
      assertTrue(comparison.equivalent(before, after))
      val merged = (comparison.mergeValue("anyOf", listOf(before), listOf(after)) as List<*>).single()
      assertEquals(after + ("title" to "Inherited"), merged)
    }
    assertEquals("Parent wrapper", before["description"])
  }

  private fun ref(name: String) = mapOf("\$ref" to "#/components/schemas/$name")
}
