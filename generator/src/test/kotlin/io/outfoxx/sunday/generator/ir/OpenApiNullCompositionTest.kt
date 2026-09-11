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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class OpenApiNullCompositionTest {
  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `nullable modifies only the type in the same composition operand`(
    version: String,
    @TempDir directory: Path,
  ) {
    val cases =
      listOf(
        "type: string, allOf: [{type: string, nullable: true}]" to false,
        "type: string, nullable: true, allOf: [{type: string}]" to false,
        "type: string, nullable: false, allOf: [{type: string, nullable: true}]" to false,
        "allOf: [{type: string, nullable: true}, {type: string}]" to false,
        "allOf: [{type: string}, {type: string, nullable: true}]" to false,
        "allOf: [{type: string, nullable: false}, {type: string, nullable: true}]" to false,
        "allOf: [{type: string, nullable: true}, {allOf: [{type: string}, {maxLength: 8}]}]" to false,
        "allOf: [{type: string, nullable: true}, {type: string, nullable: true}]" to true,
        "allOf: [{type: string, nullable: true}, {maxLength: 8}]" to true,
        "allOf: [{type: string, nullable: true}, {enum: [active, inactive]}]" to false,
        "allOf: [{nullable: true}, {type: string}]" to false,
        "type: string, allOf: [{nullable: true}]" to false,
      )
    for ((assertions, nullable) in cases) {
      val api = OpenApiToGeneratedApi().convert(source(directory, assertions, version).toUri())
      val uses = api.models.single { it.name == "Container" }.properties
      assertEquals(setOf(nullable), uses.map { it.type.nullable }.toSet(), assertions)
      assertEquals(setOf("direct", "inline"), uses.filter { it.required }.map { it.name }.toSet())
      for (name in listOf("Value", "Alias", "Chain")) {
        val model = api.models.single { it.name == name }
        if (assertions.contains("enum")) {
          assertEquals(GeneratedModel.Kind.ENUM, model.kind)
          assertEquals(listOf("active", "inactive"), model.values)
        } else {
          assertEquals(listOf(GeneratedTypeRef.scalar("string", nullable = nullable)), model.aliases, assertions)
        }
      }
      assertEquals(setOf("Value", "Alias", "Chain", "Container"), api.models.map { it.name }.toSet())
    }
  }

  @Test
  fun `type arrays and nullable declarations intersect without modifying a referenced target`(
    @TempDir directory: Path,
  ) {
    val file = source(directory, "type: ['null', string], nullable: false")
    file.writeText(
      file.readText() +
        "\n    Narrow: {type: string, allOf: [{\$ref: '#/components/schemas/Value'}]}",
    )
    val api = OpenApiToGeneratedApi().convert(file.toUri())
    assertEquals(
      listOf(GeneratedTypeRef.scalar("string", nullable = true)),
      api.models
        .single {
          it.name == "Value"
        }.aliases,
    )
    assertEquals(listOf(GeneratedTypeRef.scalar("string")), api.models.single { it.name == "Narrow" }.aliases)
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "then: {type: string}",
      "else: {type: string}",
      "if: {type: string}",
      "allOf: [{if: {type: string}}, {then: {type: string}}, {else: {type: string}}]",
      "then: {type: string}, allOf: [{if: {type: string}}]",
    ],
  )
  fun `inactive conditionals cannot remove null or pair across schema boundaries`(
    conditional: String,
    @TempDir directory: Path,
  ) {
    val file = source(directory, "type: [array, 'null'], items: {type: string}, $conditional")
    val api = OpenApiToGeneratedApi().convert(file.toUri())
    val uses = api.models.single { it.name == "Container" }.properties
    assertTrue(uses.all { it.type.nullable })
    assertEquals(GeneratedTypeRef.Kind.ARRAY, uses.single { it.name == "inline" }.type.kind)
    assertEquals(setOf("direct", "inline"), uses.filter { it.required }.map { it.name }.toSet())
  }

  @ParameterizedTest
  @ValueSource(strings = ["not: {type: string}", "if: {type: array}, then: {minItems: 1}"])
  fun `indeterminate active assertions report their source instead of emitting a non-null type`(
    conditional: String,
    @TempDir directory: Path,
  ) {
    val assertions = "type: [array, 'null'], items: {type: string}, $conditional"
    val file = source(directory, assertions)
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(file.toUri()) }
    assertTrue(error.message.orEmpty().contains("Unsupported OpenAPI schema nullability"), error.message)
    assertEquals(file.toUri().toString(), error.file)
    assertEquals(6, error.line)
    assertTrue(error.column > 0)
    file.writeText(file.readText().replaceFirst("Value: {$assertions}", "Value: {type: string}"))
    val inlineError = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(file.toUri()) }
    assertEquals(file.toUri().toString(), inlineError.file)
    assertTrue(inlineError.line > 12)
    assertTrue(inlineError.message.orEmpty().contains("Unsupported OpenAPI schema nullability"), inlineError.message)
  }

  @Test
  fun `indeterminate recursive union metadata fails without expanding models`(
    @TempDir directory: Path,
  ) {
    val file = source(directory, "type: [string, 'null'], anyOf: [{\$ref: '#/components/schemas/Value'}]")
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(file.toUri()) }
    assertEquals(file.toUri().toString(), error.file)
    assertTrue(error.message.orEmpty().contains("Unsupported OpenAPI schema nullability"), error.message)
  }

  @Test
  fun `known rejection wins over an unsupported assertion and unconstrained schemas keep their fallback`() {
    val nullability = OpenApiSchemaNullability(OpenApiSchemaComposition(emptyMap()))
    assertFalse(nullability.isNullable(mapOf("type" to "string", "not" to mapOf("type" to "number"))))
    assertFalse(nullability.isNullable(emptyMap<String, Any?>()))
    assertTrue(nullability.isNullable(mapOf("nullable" to true)))
    assertEquals(null, nullability.acceptsNull(mapOf("not" to mapOf("type" to "number"))))
  }

  @Test
  fun `null-only intersections fail rather than becoming arbitrary values`(
    @TempDir directory: Path,
  ) {
    val file = source(directory, "allOf: [{type: string, nullable: true}, {type: integer, nullable: true}]")
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(file.toUri()) }
    assertTrue(error.message.orEmpty().contains("no representable non-null payload"), error.message)
    assertEquals(file.toUri().toString(), error.file)
  }

  private fun source(
    directory: Path,
    assertions: String,
    version: String = "3.1.0",
  ): Path =
    directory.resolve("api.yaml").also {
      it.writeText(
        """
        openapi: $version
        info: {title: Null composition, version: 1.0.0}
        paths: {}
        components:
          schemas:
            Value: {$assertions}
            Alias: {${'$'}ref: '#/components/schemas/Value'}
            Chain: {allOf: [{${'$'}ref: '#/components/schemas/Alias'}]}
            Container:
              type: object
              required: [direct, inline]
              properties:
                direct: {${'$'}ref: '#/components/schemas/Value'}
                alias: {${'$'}ref: '#/components/schemas/Chain'}
                inline: {$assertions}
        """.trimIndent(),
      )
    }
}
