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
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiNullAssertionsTest {
  @Test
  fun `null must satisfy type enum const and every composition assertion`(
    @TempDir directory: Path,
  ) {
    val cases =
      listOf(
        "type: [string, 'null'], enum: [active, inactive]" to false,
        "type: [string, 'null'], enum: [active, null]" to true,
        "type: string, nullable: true, enum: [active, inactive]" to false,
        "type: string, nullable: true, enum: [active, null]" to true,
        "type: [string, 'null'], const: active" to false,
        "type: [string, 'null'], const: null" to true,
        "type: string, const: null" to false,
        "nullable: true, enum: [active, inactive]" to false,
        "nullable: true, const: active" to false,
        "type: object, anyOf: [{type: object}, {type: 'null'}]" to false,
        "nullable: true, oneOf: [{type: [string, 'null']}, {type: 'null'}]" to false,
        "allOf: [{type: [string, 'null']}, {enum: [active, inactive]}]" to false,
        "allOf: [{type: [string, 'null']}, {enum: [active, null]}]" to true,
      )
    for ((assertions, nullable) in cases) {
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: 3.1.0
        info: {title: Null assertions, version: 1.0.0}
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
      val uses =
        OpenApiToGeneratedApi()
          .convert(source.toUri())
          .models
          .single { it.name == "Container" }
          .properties
      assertEquals(setOf(nullable), uses.map { it.type.nullable }.toSet(), assertions)
      assertEquals(setOf("direct", "inline"), uses.filter { it.required }.map { it.name }.toSet())
    }
  }

  @Test
  fun `inline projections keep normalized bounds from the payload dialect`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("bounds.yaml")
    source.writeText(
      """
      openapi: 3.0.3
      info: {title: Projected bounds, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Value:
            anyOf:
              - {type: integer, minimum: 0, exclusiveMinimum: true}
              - {enum: [null]}
          Container:
            type: object
            required: [value]
            properties: {value: {${'$'}ref: '#/components/schemas/Value'}}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val value = api.models.single { it.name == "Value" }
    assertEquals(listOf(GeneratedTypeRef.scalar("integer", nullable = true)), value.aliases)
    assertEquals(mapOf("minimum" to "0"), value.validation)
    assertEquals(
      GeneratedTypeRef.named("Value", nullable = true),
      api.models
        .single {
          it.name == "Container"
        }.properties
        .single()
        .type,
    )
  }

  @Test
  fun `unknown branch acceptance cannot authorize null projection`() {
    val nullability = OpenApiSchemaNullability(OpenApiSchemaComposition(emptyMap()))
    assertEquals(
      null,
      nullability.acceptsNull(
        mapOf(
          "oneOf" to listOf(emptyMap<String, Any?>(), mapOf("type" to "null")),
        ),
      ),
    )
    assertFalse(nullability.isNullable(mapOf("type" to "string", "nullable" to true, "const" to "active")))
    assertTrue(nullability.isNullable(mapOf("anyOf" to listOf(emptyMap<String, Any?>(), mapOf("type" to "null")))))
    assertFalse(
      nullability.isNullable(
        mapOf(
          "oneOf" to listOf(mapOf("type" to "null"), mapOf("const" to null), emptyMap<String, Any?>()),
        ),
      ),
    )
    assertFalse(
      nullability.isNullable(
        mapOf(
          "anyOf" to listOf(mapOf("type" to "null")),
          "oneOf" to listOf(mapOf("type" to "null"), mapOf("const" to null)),
        ),
      ),
    )
  }
}
