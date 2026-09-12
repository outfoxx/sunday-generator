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
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenApiSchemaAnalysisTest {
  @Test
  fun `completed compositions remain idempotent without losing their bound dialect or provenance`() {
    val original =
      OpenApiSchema(
        mapOf("type" to "number", "minimum" to 1, "exclusiveMinimum" to true),
        OpenApiSchema.Source("file:/bounds.yaml", "type: number\nminimum: 1\nexclusiveMinimum: true".toByteArray()),
        "",
        true,
      )
    val composition = OpenApiSchemaComposition(emptyMap())
    val effective = composition.resolve(original)
    assertEquals(1, effective["exclusiveMinimum"])
    assertFalse((effective as OpenApiSchema).usesBooleanExclusiveBounds)
    assertSame(effective, composition.resolve(effective))
    assertSame(effective, composition.resolve(original))
    assertTrue(original.usesBooleanExclusiveBounds)
    assertEquals(true, original["exclusiveMinimum"])
    val error = assertThrows(GenerationException::class.java) { effective.error("test provenance") }
    assertEquals("file:/bounds.yaml", error.file)
    assertTrue(error.line > 0)
  }

  @Test
  fun `analysis retains canonical uses while sharing effective metadata and nullable projections`() {
    val item = mapOf("type" to "object", "properties" to mapOf("next" to ref("MaybeItem")))
    val maybeItem = mapOf("anyOf" to listOf(ref("Item"), mapOf("type" to "null")), "description" to "Optional item")
    val limit = mapOf("type" to "integer", "default" to 20, "minimum" to 1, "maximum" to 100)
    val schemas = mapOf("Item" to item, "MaybeItem" to maybeItem, "Limit" to limit)
    val analysis = OpenApiSchemaAnalysis(schemas)
    schemas.values.forEach(analysis::validate)
    val use = mapOf("allOf" to listOf(ref("MaybeItem")))
    val nullable = analysis.analyze(use)
    assertSame(use, nullable.source)
    assertSame(nullable, analysis.analyze(use))
    assertEquals("MaybeItem", nullable.canonicalReference)
    assertTrue(nullable.nullable)
    assertEquals(ref("Item") + ("description" to "Optional item"), nullable.projection?.schema)
    assertEquals("Optional item", nullable.effective["description"])
    assertEquals(item + ("description" to "Optional item"), nullable.metadata)
    val parameter = analysis.analyze(ref("Limit"))
    assertEquals("Limit", parameter.canonicalReference)
    assertEquals(limit, parameter.metadata)
    assertFalse(parameter.nullable)
    assertNotSame(nullable, OpenApiSchemaAnalysis(schemas).analyze(use))
  }

  private fun ref(name: String) = mapOf("\$ref" to "#/components/schemas/$name")
}
