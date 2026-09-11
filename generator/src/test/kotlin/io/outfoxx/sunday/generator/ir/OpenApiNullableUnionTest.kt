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

import io.outfoxx.sunday.generator.GenerationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiNullableUnionTest {
  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `nullable unions retain their payload type when promoted`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    for (reverse in listOf(false, true)) {
      for (reference in listOf("#node", "#/properties/node", "https://schemas.example.test/container#node")) {
        val original =
          """
          node:
            ${'$'}anchor: node
            $keyword:
              - {${'$'}ref: 'https://schemas.example.test/item'}
              - {type: 'null'}
          """.trimIndent()
        val aliases =
          """
          copy: {${'$'}ref: '$reference'}
          requiredCopy: {${'$'}ref: '$reference'}
          composed: {allOf: [{${'$'}ref: '$reference'}]}
          """.trimIndent()
        val baseline = convert(directory, original)
        val inline =
          baseline.models
            .single { it.name == "Container" }
            .properties
            .single()
        assertEquals(GeneratedTypeRef.named("Item", nullable = true), inline.type)
        val properties = (if (reverse) listOf(aliases, original) else listOf(original, aliases)).joinToString("\n")
        val result = convert(directory, properties)
        val uses = result.models.single { it.name == "Container" }.properties
        assertEquals(setOf(GeneratedTypeRef.named("Node", nullable = true)), uses.map { it.type }.toSet())
        assertEquals(setOf("node", "requiredCopy"), uses.filter { it.required }.map { it.name }.toSet())
        val node = result.models.single { it.name == "Node" }
        assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, node.kind)
        assertEquals(listOf(inline.type), node.aliases)
        assertEquals(setOf("Container", "Node", "Item"), result.models.map { it.name }.toSet())
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `inline union payloads retain one recursive canonical object`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val result =
      convert(
        directory,
        """
        node:
          ${'$'}anchor: node
          $keyword:
            - type: object
              required: [child]
              properties:
                child: {${'$'}ref: '#node'}
                value: {type: string}
            - {type: 'null'}
        copy: {${'$'}ref: '#node'}
        """.trimIndent(),
      )
    val node = result.models.single { it.name == "Node" }
    assertEquals(GeneratedModel.Kind.OBJECT, node.kind)
    assertEquals(GeneratedTypeRef.named("Node", nullable = true), node.properties.single { it.name == "child" }.type)
    assertTrue(node.properties.single { it.name == "child" }.required)
    assertFalse(
      node.properties
        .single { it.name == "value" }
        .type.nullable,
    )
    assertEquals(setOf("Container", "Node", "Item"), result.models.map { it.name }.toSet())
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `root aliases and single allOf retain a nullable payload without broadening it`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val path = directory.resolve("aliases.yaml")
    path.writeText(
      """
      openapi: 3.1.0
      info: {title: Union aliases, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item:
            type: object
            properties: {child: {${'$'}ref: '#/components/schemas/Chain'}}
          Maybe:
            $keyword: [{${'$'}ref: '#/components/schemas/Item'}, {type: 'null'}]
          Alias: {${'$'}ref: '#/components/schemas/Maybe'}
          Chain: {allOf: [{${'$'}ref: '#/components/schemas/Alias'}]}
          Container:
            type: object
            properties:
              direct: {${'$'}ref: '#/components/schemas/Maybe'}
              composed: {allOf: [{${'$'}ref: '#/components/schemas/Chain'}]}
      """.trimIndent(),
    )
    val result = OpenApiToGeneratedApi().convert(path.toUri())
    for (name in listOf("Maybe", "Alias", "Chain")) {
      assertEquals(
        listOf(GeneratedTypeRef.named("Item", nullable = true)),
        result.models.single { it.name == name }.aliases,
      )
    }
    assertTrue(
      result.models
        .single { it.name == "Container" }
        .properties
        .all { it.type.nullable },
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `overlapping nullable branches retain null only for anyOf`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val result =
      convert(
        directory,
        """
        node:
          ${'$'}anchor: node
          $keyword: [{type: [string, 'null']}, {type: 'null'}]
        copy: {${'$'}ref: '#node'}
        """.trimIndent(),
      )
    val node = result.models.single { it.name == "Node" }
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, node.kind)
    assertEquals(listOf(GeneratedTypeRef.scalar("string", nullable = keyword == "anyOf")), node.aliases)
    assertEquals(
      keyword == "anyOf",
      result.models
        .single { it.name == "Container" }
        .properties
        .first()
        .type.nullable,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["oneOf", "anyOf"])
  fun `object wrapper assertions reduce nullable unions without broadening them`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val result =
      convert(
        directory,
        """
        node:
          ${'$'}anchor: node
          type: object
          $keyword: [{${'$'}ref: 'https://schemas.example.test/item'}, {type: 'null'}]
        copy: {${'$'}ref: '#node'}
        """.trimIndent(),
      )
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, result.models.single { it.name == "Node" }.kind)
    assertEquals(listOf(GeneratedTypeRef.named("Item")), result.models.single { it.name == "Node" }.aliases)
    assertFalse(
      result.models
        .single { it.name == "Container" }
        .properties
        .first()
        .type.nullable,
    )
  }

  @Test
  fun `nullable target declarations stay nullable when a wrapper excludes null`(
    @TempDir directory: Path,
  ) {
    for (reverse in listOf(false, true)) {
      val target =
        """
        text:
          ${'$'}anchor: text
          type: [string, 'null']
        node:
          ${'$'}anchor: node
          oneOf: [{${'$'}ref: '#text'}, {type: 'null'}]
        """.trimIndent()
      val aliases =
        """
        copy: {${'$'}ref: '#node'}
        textCopy: {${'$'}ref: '#text'}
        """.trimIndent()
      val result =
        convert(directory, (if (reverse) listOf(aliases, target) else listOf(target, aliases)).joinToString("\n"))
      assertEquals(
        listOf(GeneratedTypeRef.scalar("string", nullable = true)),
        result.models
          .single {
            it.name == "Text"
          }.aliases,
      )
      assertEquals(listOf(GeneratedTypeRef.scalar("string")), result.models.single { it.name == "Node" }.aliases)
      val uses =
        result.models
          .single { it.name == "Container" }
          .properties
          .associateBy { it.name }
      assertEquals(GeneratedTypeRef.named("Text", nullable = true), uses.getValue("text").type)
      assertEquals(GeneratedTypeRef.named("Node"), uses.getValue("node").type)
      assertTrue(uses.getValue("node").required)
      assertFalse(uses.getValue("copy").required)
    }
  }

  @Test
  fun `wrapper intersections preserve recursive fields and validation`(
    @TempDir directory: Path,
  ) {
    val result =
      convert(
        directory,
        """
        node:
          ${'$'}anchor: node
          type: object
          required: [id]
          properties: {id: {type: string, minLength: 2}}
          anyOf:
            - type: object
              required: [child]
              properties:
                id: {type: string, maxLength: 8}
                child: {${'$'}ref: '#node'}
            - {type: 'null'}
        copy: {${'$'}ref: '#node'}
        """.trimIndent(),
      )
    val node = result.models.single { it.name == "Node" }
    assertEquals(GeneratedModel.Kind.OBJECT, node.kind)
    assertTrue(node.properties.all { it.required })
    assertEquals(mapOf("minLength" to "2", "maxLength" to "8"), node.properties.single { it.name == "id" }.validation)
    assertEquals(GeneratedTypeRef.named("Node"), node.properties.single { it.name == "child" }.type)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "anyOf: [{type: string}, {type: integer}, {type: 'null'}]",
      "oneOf: [{}, {type: 'null'}]",
      "anyOf: [{type: object, not: {required: [id]}}, {type: 'null'}]",
      "anyOf: [{${'$'}ref: '#node'}, {type: 'null'}]",
      "type: object\nanyOf: [{type: string}, {type: 'null'}]",
      "allOf: [false]\nanyOf: [{type: string}, {type: 'null'}]",
    ],
  )
  fun `unrepresentable null unions fail at their source`(
    assertions: String,
    @TempDir directory: Path,
  ) {
    val error =
      assertThrows<GenerationException> {
        convert(
          directory,
          "node:\n  ${'$'}anchor: node\n" + assertions.prependIndent("  ") + "\ncopy: {${'$'}ref: '#node'}",
        )
      }
    assertTrue(error.message!!.contains("OpenAPI"), error.message)
    assertTrue(error.file.endsWith("api.yaml"), error.message)
    assertTrue(error.line > 0, error.message)
  }

  private fun convert(
    directory: Path,
    properties: String,
  ): GeneratedApi {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Nullable unions, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Item: {${'$'}id: 'https://schemas.example.test/item', type: object, properties: {value: {type: string}}}
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
