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

class OpenApiParameterMetadataTest {
  @Test
  fun `promoted parameter schemas retain metadata and independent parameter annotations`(
    @TempDir directory: Path,
  ) {
    for ((schema, default, constant) in listOf(
      Triple("type: integer, default: 20, minimum: 1, maximum: 100", 20, null),
      Triple("type: integer, default: 0, const: 0, minimum: 0, maximum: 100", 0, 0),
      Triple("type: boolean, default: false, const: false", false, false),
    )) {
      for (reference in listOf(
        "#/paths/~1items/get/parameters/0/schema",
        "https://schemas.example.test/limit#limit",
        "https://schemas.example.test/limit",
      )) {
        val baseline = convert(directory, schema.replace(", ", "\n"), null)
        val promoted = convert(directory, schema.replace(", ", "\n"), reference)
        val inlineParameter =
          baseline.services
            .single()
            .operations
            .single()
            .parameters
            .single()
        val promotedParameter =
          promoted.services
            .single()
            .operations
            .single()
            .parameters
            .single()
        assertEquals(inlineParameter, promotedParameter.copy(type = inlineParameter.type))
        assertEquals(GeneratedTypeRef.named("Limit"), promotedParameter.type)
        assertEquals(default, promotedParameter.defaultValue)
        assertEquals(constant, promotedParameter.constantValue)
        assertFalse(promotedParameter.required)
        assertTrue(promotedParameter.deprecated)
        assertEquals("Parameter documentation", promotedParameter.documentation?.description)
        assertEquals(setOf("Limit"), promoted.models.map { it.name }.toSet())
      }
    }
  }

  @Test
  fun `schema metadata follows aliases and constrained projections`(
    @TempDir directory: Path,
  ) {
    val api =
      convert(
        directory,
        """
        type: integer
        minimum: 1
        maximum: 100
        default: 20
        anyOf: [{type: [integer, 'null'], minimum: 5}, {type: 'null'}]
        """.trimIndent(),
        "https://schemas.example.test/limit#limit",
      )
    val parameter =
      api.services
        .single()
        .operations
        .single()
        .parameters
        .single()
    assertEquals(20, parameter.defaultValue)
    assertEquals(mapOf("minimum" to "5", "maximum" to "100"), parameter.validation)
    assertEquals(GeneratedTypeRef.named("Limit"), parameter.type)
  }

  @Test
  fun `alias metadata survives in every parameter location and additional properties`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("metadata.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Metadata aliases, version: 1.0.0}
      paths:
        /items/{limit}:
          get:
            operationId: getItem
            parameters:
              - {name: limit, in: path, required: true, schema: {${'$'}ref: '#/components/schemas/Chain'}}
              - {name: limit, in: query, schema: {${'$'}ref: '#/components/schemas/Chain'}}
              - {name: limit, in: header, required: true, schema: {${'$'}ref: '#/components/schemas/Chain'}}
              - {name: limit, in: cookie, schema: {${'$'}ref: '#/components/schemas/Chain'}}
            responses: {'204': {description: Done}}
      components:
        schemas:
          Chain: {allOf: [{${'$'}ref: '#/components/schemas/Alias'}]}
          Alias: {${'$'}ref: '#/components/schemas/Limit'}
          Limit: {type: integer, default: 20, const: 20, minimum: 1, maximum: 100, description: Schema documentation}
          Values:
            type: object
            additionalProperties: {${'$'}ref: '#/components/schemas/Chain'}
      """.trimIndent(),
    )
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    val parameters =
      api.services
        .single()
        .operations
        .single()
        .parameters
    assertEquals(
      setOf(
        GeneratedParameter.Location.PATH,
        GeneratedParameter.Location.QUERY,
        GeneratedParameter.Location.HEADER,
        GeneratedParameter.Location.COOKIE,
      ),
      parameters
        .map {
          it.location
        }.toSet(),
    )
    for (parameter in parameters) {
      assertEquals(20, parameter.defaultValue)
      assertEquals(20, parameter.constantValue)
      assertEquals(mapOf("minimum" to "1", "maximum" to "100"), parameter.validation)
      assertEquals(
        parameter.location in setOf(GeneratedParameter.Location.PATH, GeneratedParameter.Location.HEADER),
        parameter.required,
      )
    }
    val values = api.models.single { it.name == "Values" }.additionalProperties!!
    assertEquals(mapOf("minimum" to "1", "maximum" to "100"), values.validation)
    assertEquals("Schema documentation", values.documentation?.description)
  }

  private fun convert(
    directory: Path,
    schema: String,
    reference: String?,
  ): GeneratedApi {
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.1.0
      info: {title: Parameters, version: 1.0.0}
      paths:
        /items:
          get:
            operationId: getItems
            parameters:
              - name: limit
                in: query
                required: false
                description: Parameter documentation
                deprecated: true
                style: form
                explode: false
                schema:
                  ${'$'}id: https://schemas.example.test/limit
                  ${'$'}anchor: limit
      """.trimIndent() + "\n" + schema.prependIndent("            ") + "\n" +
        """
        responses: {'204': {description: Done}}
        """.trimIndent().prependIndent("      ") +
        if (reference == null) {
          ""
        } else {
          "\n" +
            """
            components:
              schemas:
                Limit: {${'$'}ref: '$reference'}
            """.trimIndent()
        },
    )
    return OpenApiToGeneratedApi().convert(source.toUri())
  }
}
