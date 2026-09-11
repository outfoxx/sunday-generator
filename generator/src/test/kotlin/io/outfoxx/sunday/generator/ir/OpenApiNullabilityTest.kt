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
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiNullabilityTest {
  @ParameterizedTest
  @ValueSource(strings = ["#node", "#/properties/node", "https://schemas.example.test/container#node"])
  fun `promotion preserves nullability and requiredness in either declaration order`(
    reference: String,
    @TempDir directory: Path,
  ) {
    for ((declaration, nullable) in listOf(
      "type: [object, 'null']" to true,
      "type: object\nnullable: true" to true,
      "type: object\nnullable: false" to false,
      "type: object\nallOf: [{type: object, nullable: true}]" to false,
      "allOf: [{type: object, nullable: true}]" to true,
      "type: [object, 'null']\nthen: {type: string}" to true,
      "type: ['null', object]\nif: {type: string}" to true,
    )) {
      val node =
        """
        node:
          ${'$'}anchor: node
        """.trimIndent() + "\n" + declaration.prependIndent("  ") +
          "\n  properties: {value: {type: string}}"
      val baseline = convert(directory, node)
      val original =
        baseline.models
          .single { it.name == "Container" }
          .properties
          .single()
      assertEquals(nullable, original.type.nullable)
      assertTrue(original.required)
      assertEquals("ContainerNode", original.type.name)

      for (reverse in listOf(false, true)) {
        val aliases =
          """
          copy: {${'$'}ref: '$reference'}
          requiredCopy: {${'$'}ref: '$reference'}
          composed: {allOf: [{${'$'}ref: '$reference'}]}
          """.trimIndent()
        val properties = (if (reverse) listOf(aliases, node) else listOf(node, aliases)).joinToString("\n")
        val promoted = convert(directory, properties)
        val uses =
          promoted.models
            .single { it.name == "Container" }
            .properties
            .associateBy { it.name }
        assertEquals(original.required, uses.getValue("node").required)
        assertTrue(uses.getValue("requiredCopy").required)
        assertFalse(uses.getValue("copy").required)
        assertFalse(uses.getValue("composed").required)
        assertEquals(setOf(GeneratedTypeRef.named("Node", nullable = nullable)), uses.values.map { it.type }.toSet())
        assertEquals(setOf("Container", "Node"), promoted.models.map { it.name }.toSet())
      }
    }
  }

  @Test
  fun `named alias chains and single allOf references retain nullable recursive metadata`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("aliases.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Nullable aliases, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Node:
            allOf: [{type: object, nullable: true}]
            then: {type: string}
            required: [child]
            properties:
              child: {${'$'}ref: '#/components/schemas/Chain'}
          Alias: {${'$'}ref: '#/components/schemas/Node'}
          Chain: {${'$'}ref: '#/components/schemas/Alias'}
          Composed: {allOf: [{${'$'}ref: '#/components/schemas/Chain'}]}
          Container:
            type: object
            required: [direct, composed]
            properties:
              direct: {${'$'}ref: '#/components/schemas/Node'}
              alias: {${'$'}ref: '#/components/schemas/Alias'}
              chain: {${'$'}ref: '#/components/schemas/Chain'}
              composed: {allOf: [{${'$'}ref: '#/components/schemas/Composed'}]}
      """.trimIndent(),
    )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    val uses =
      result.models
        .single { it.name == "Container" }
        .properties
        .associateBy { it.name }
    for ((property, name) in mapOf(
      "direct" to "Node",
      "alias" to "Alias",
      "chain" to "Chain",
      "composed" to "Composed",
    )) {
      assertEquals(GeneratedTypeRef.named(name, nullable = true), uses.getValue(property).type)
      assertEquals(property in setOf("direct", "composed"), uses.getValue(property).required)
    }
    val child =
      result.models
        .single { it.name == "Node" }
        .properties
        .single()
    assertEquals(GeneratedTypeRef.named("Chain", nullable = true), child.type)
    assertTrue(child.required)
    assertEquals(setOf("Node", "Alias", "Chain", "Composed", "Container"), result.models.map { it.name }.toSet())
  }

  private fun convert(
    directory: Path,
    properties: String,
  ): GeneratedApi {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Nullable inline schema, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Container:
            ${'$'}id: https://schemas.example.test/container
            type: object
            required: [node, requiredCopy]
            properties:
      """.trimIndent() + "\n" + properties.prependIndent("        "),
    )
    return OpenApiToGeneratedApi().convert(source.toUri())
  }
}
