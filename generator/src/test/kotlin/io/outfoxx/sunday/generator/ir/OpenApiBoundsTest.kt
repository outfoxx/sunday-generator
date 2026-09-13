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
import kotlin.io.path.writeText

class OpenApiBoundsTest {
  @ParameterizedTest
  @ValueSource(strings = ["exclusiveMinimum", "exclusiveMaximum"])
  fun `disabled bounds do not require an associated limit`(
    exclusive: String,
    @TempDir directory: Path,
  ) {
    val inclusive = if (exclusive == "exclusiveMinimum") "minimum" else "maximum"
    for (bound in listOf("", ", $inclusive: 5")) {
      val source =
        api(
          directory,
          "3.0.3",
          """
          Count: {type: integer, $exclusive: false$bound}
          Alias: {${'$'}ref: '#/components/schemas/Count'}
          Composed: {allOf: [{${'$'}ref: '#/components/schemas/Count'}, {$exclusive: false}]}
          Container:
            type: object
            properties:
              count: {type: integer, $exclusive: false$bound}
          """.trimIndent(),
        )
      val result = OpenApiToGeneratedApi().convert(source.toUri())
      val expected = if (bound.isEmpty()) emptyMap() else mapOf(inclusive to "5")
      for (name in listOf("Count", "Alias", "Composed")) {
        val model = result.models.single { it.name == name }
        assertEquals(listOf(GeneratedTypeRef.scalar("integer")), model.aliases)
        assertEquals(expected, model.validation)
      }
      assertEquals(
        expected,
        result.models
          .single { it.name == "Container" }
          .properties
          .single()
          .validation,
      )
      val schemas = schemas(source)
      assertEquals(false, schemas.getValue("Count")[exclusive])
      val composition = OpenApiSchemaComposition(schemas)
      assertFalse(composition.resolve(schemas.getValue("Composed")).containsKey(exclusive))
    }
  }

  @Test
  fun `OpenAPI 3_0 boolean bounds compose without changing their raw dialect`(
    @TempDir directory: Path,
  ) {
    for (reverse in listOf(false, true)) {
      for (flag in listOf("", ", exclusiveMinimum: false, exclusiveMaximum: false")) {
        val base = "{type: number, minimum: 0, exclusiveMinimum: true, maximum: 10, exclusiveMaximum: true}"
        val child = "{type: number, minimum: 1, maximum: 9$flag}"
        val source =
          api(
            directory,
            "3.0.3",
            """
            Base: {type: object, properties: {count: $base}}
            Child:
              allOf:
            """.trimIndent() + "\n" +
              (
                if (reverse) {
                  listOf("{type: object, properties: {count: $child}}", "{${'$'}ref: '#/components/schemas/Base'}")
                } else {
                  listOf("{${'$'}ref: '#/components/schemas/Base'}", "{type: object, properties: {count: $child}}")
                }
              ).joinToString("\n") { "    - $it" },
          )
        val result = OpenApiToGeneratedApi().convert(source.toUri())
        assertEquals(
          mapOf("minimum" to "1", "maximum" to "9"),
          result.models
            .single {
              it.name == "Child"
            }.properties
            .single()
            .validation,
        )
        val schemas = schemas(source)
        assertEquals(
          true,
          ((schemas.getValue("Base")["properties"] as Map<*, *>)["count"] as Map<*, *>)["exclusiveMinimum"],
        )
        val effective = OpenApiSchemaComposition(schemas).resolve(schemas.getValue("Child"))
        val count = (effective["properties"] as Map<*, *>)["count"] as Map<*, *>
        assertFalse(count.containsKey("exclusiveMinimum"))
        assertFalse(count.containsKey("exclusiveMaximum"))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `equal bounds preserve exclusivity in either composition order`(
    version: String,
    @TempDir directory: Path,
  ) {
    for (reverse in listOf(false, true)) {
      val closed = "{type: number, minimum: 1, maximum: 9}"
      val open =
        if (version.startsWith("3.0")) {
          "{minimum: 1, maximum: 9, exclusiveMinimum: true, exclusiveMaximum: true}"
        } else {
          "{exclusiveMinimum: 1, exclusiveMaximum: 9}"
        }
      val parts = if (reverse) listOf(open, closed) else listOf(closed, open)
      val source = api(directory, version, "Count: {allOf: [${parts.joinToString()}]}")
      val schemas = schemas(source)
      val effective = OpenApiSchemaComposition(schemas).resolve(schemas.getValue("Count"))
      assertEquals("1", effective["exclusiveMinimum"].toString())
      assertEquals("9", effective["exclusiveMaximum"].toString())
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `mixed dialect operands and root aliases keep their source bound semantics`(
    version: String,
    @TempDir directory: Path,
  ) {
    val other = if (version.startsWith("3.0")) "3.1.0" else "3.0.3"
    directory.resolve("other.yaml").writeText(
      """
      openapi: $other
      info: {title: External bounds, version: 1.0.0}
      paths: {}
      components:
        schemas:
          Count: {type: number, minimum: 0, exclusiveMinimum: ${if (other.startsWith("3.0")) "true" else "0"}}
      """.trimIndent(),
    )
    val source =
      api(
        directory,
        version,
        """
        Alias: {${'$'}ref: 'other.yaml#/components/schemas/Count'}
        Count:
          allOf:
            - {${'$'}ref: '#/components/schemas/Alias'}
            - {minimum: 1${if (version.startsWith("3.0")) ", exclusiveMinimum: false" else ""}}
        """.trimIndent(),
      )
    val converter = OpenApiToGeneratedApi()
    repeat(2) {
      val result = converter.convert(source.toUri())
      assertEquals(mapOf("minimum" to "1"), result.models.single { it.name == "Count" }.validation)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `empty intervals and malformed exclusive bounds have source locations`(
    version: String,
    @TempDir directory: Path,
  ) {
    val invalid =
      if (version.startsWith("3.0")) {
        listOf(
          "minimum: 1.0, maximum: 1, exclusiveMinimum: true",
          "minimum: 1, maximum: 1, exclusiveMaximum: true",
          "minimum: 1, exclusiveMinimum: 1",
          "exclusiveMaximum: true",
        )
      } else {
        listOf("minimum: 1.0, exclusiveMaximum: 1", "exclusiveMinimum: 1, maximum: 1", "exclusiveMinimum: true")
      }
    for (fields in invalid) {
      val source = api(directory, version, "Count: {type: number, $fields}")
      val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
      assertEquals(source.toUri().toString(), error.file)
      assertTrue(error.line >= 6, error.toString())
    }
  }

  private fun schemas(source: Path): Map<String, Map<*, *>> {
    val document = OpenApiReferenceResolver().resolve(source.toUri()).document
    val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
    return schemas.entries.associate { it.key.toString() to it.value as Map<*, *> }
  }

  private fun api(
    directory: Path,
    version: String,
    schemas: String,
  ): Path =
    directory.resolve("api.yaml").apply {
      writeText(
        "openapi: $version\ninfo: {title: Bounds, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    "),
      )
    }
}
