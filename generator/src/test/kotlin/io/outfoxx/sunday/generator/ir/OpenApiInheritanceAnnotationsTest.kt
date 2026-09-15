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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.writeText

class OpenApiInheritanceAnnotationsTest {
  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `documented Pet fields preserve Cat inheritance in either declaration order`(
    version: String,
    @TempDir directory: Path,
  ) {
    val declarations =
      listOf(
        """
        Pet:
          type: object
          required: [kind]
          properties: {kind: {type: string, description: Parent kind}}
          discriminator: {propertyName: kind}
        """.trimIndent(),
        """
        Cat:
          allOf:
            - ${'$'}ref: '#/components/schemas/Pet'
            - type: object
              required: [kind]
              properties:
                kind: {type: string, description: The animal kind}
                lives: {type: integer}
        """.trimIndent(),
      )
    for (order in listOf(declarations, declarations.reversed())) {
      val source = api(directory, order.joinToString("\n"), version)
      val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
      val pet = models.getValue("Pet")
      val cat = models.getValue("Cat")
      assertEquals(listOf(GeneratedTypeRef.named("Pet")), cat.inherits)
      assertEquals(listOf("lives"), cat.properties.map { it.name })
      assertFalse(cat.properties.single().required)
      assertEquals(GeneratedTypeRef.scalar("string"), pet.properties.single().type)
      assertTrue(pet.properties.single().required)
      assertEquals(
        "Parent kind",
        pet.properties
          .single()
          .documentation
          ?.description,
      )
      val schemas = schemas(source)
      val child = OpenApiSchemaComposition(schemas).resolve(schemas.getValue("Cat"))
      assertEquals("The animal kind", ((child["properties"] as Map<*, *>)["kind"] as Map<*, *>)["description"])
      assertEquals(listOf("kind"), child["required"])
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `aliases nested fields recursion and multiple parents keep canonical contracts`(
    version: String,
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        Child:
          allOf:
            - ${'$'}ref: '#/components/schemas/Alias'
            - ${'$'}ref: '#/components/schemas/Other'
            - properties:
                node: {allOf: [{${'$'}ref: '#/components/schemas/Node'}], description: Child node}
                labels: {type: array, items: {type: string, description: Child label}}
                values: {type: object, additionalProperties: {type: string, title: Child value}}
                enabled: {type: boolean, summary: Child flag}
                own: {type: string}
        Alias: {${'$'}ref: '#/components/schemas/Parent'}
        Other:
          type: object
          properties: {enabled: {type: boolean}}
        Parent:
          type: object
          required: [node, labels]
          properties:
            node: {${'$'}ref: '#/components/schemas/Node'}
            labels: {type: array, items: {type: string, description: Parent label}}
            values: {type: object, additionalProperties: {type: string, title: Parent value}}
        Node:
          type: object
          properties:
            next: {${'$'}ref: '#/components/schemas/Node'}
        """.trimIndent(),
        version,
      )
    val converter = OpenApiToGeneratedApi()
    val result = converter.convert(source.toUri())
    val models = result.models.associateBy { it.name }
    val child = models.getValue("Child")
    assertEquals(listOf(GeneratedTypeRef.named("Alias"), GeneratedTypeRef.named("Other")), child.inherits)
    assertEquals(listOf("own"), child.properties.map { it.name })
    val parent = models.getValue("Parent")
    assertEquals(
      setOf("node", "labels"),
      parent.properties
        .filter { it.required }
        .map { it.name }
        .toSet(),
    )
    assertEquals(GeneratedTypeRef.named("Node"), parent.properties.single { it.name == "node" }.type)
    assertEquals(
      GeneratedTypeRef.named("Node"),
      models
        .getValue("Node")
        .properties
        .single()
        .type,
    )
    assertEquals(setOf("Child", "Alias", "Other", "Parent", "Node"), models.keys)
    Executors.newFixedThreadPool(2).use { executor ->
      val calls = List(4) { Callable { converter.convert(source.toUri()) } }
      executor.invokeAll(calls).forEach { assertEquals(result, it.get()) }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `recursive property intersections retain references annotations and inheritance`(
    version: String,
    @TempDir directory: Path,
  ) {
    val nodeReference = if (version == "3.1.0") "https://schemas.example.test/node" else "#/components/schemas/Node"
    val recursiveReference = if (version == "3.1.0") "#node" else nodeReference
    for (depth in 0..2) {
      fun reference(
        target: String,
        label: String,
        level: Int = depth,
      ): String {
        val contract =
          if (level == 0) {
            "${'$'}ref: '$target'"
          } else {
            "allOf: [${reference(target, label, level - 1)}]"
          }
        val inherited = if (label == "Parent") ", title: 'Inherited $level'" else ""
        return "{$contract, description: '$label $level'$inherited}"
      }
      for (nullable in listOf(false, true)) {
        val parent = reference(nodeReference, "Parent")
        val declarations =
          listOf(
            """
            Node:
              ${'$'}id: https://schemas.example.test/node
              ${'$'}anchor: node
              allOf:
                - type: object
                  nullable: $nullable
                  required: [next]
                  properties:
                    next: $parent
                    optional: $parent
                - properties:
                    next: ${reference(recursiveReference, "Updated")}
            """.trimIndent(),
            "NodeAlias: {${'$'}ref: '#/components/schemas/Node'}",
            "Pet: {type: object, required: [value], properties: {value: $parent}}",
            """
            Cat:
              allOf:
                - ${'$'}ref: '#/components/schemas/Pet'
                - properties:
                    value: ${reference("#/components/schemas/NodeAlias", "Child")}
                    own: {type: string}
            """.trimIndent(),
          )
        for (order in listOf(declarations, declarations.reversed())) {
          val source = api(directory, order.joinToString("\n"), version)
          val converter = OpenApiToGeneratedApi()
          val result = converter.convert(source.toUri())
          val models = result.models.associateBy { it.name }
          val expectedType = GeneratedTypeRef.named("Node", nullable = nullable)
          assertEquals(setOf("Node", "NodeAlias", "Pet", "Cat"), models.keys)
          for (name in listOf("Node", "NodeAlias")) {
            val properties = models.getValue(name).properties.associateBy { it.name }
            assertEquals(setOf("next", "optional"), properties.keys)
            assertEquals(expectedType, properties.getValue("next").type)
            assertEquals(expectedType, properties.getValue("optional").type)
            assertTrue(properties.getValue("next").required)
            assertFalse(properties.getValue("optional").required)
          }
          assertEquals(
            expectedType,
            models
              .getValue("Pet")
              .properties
              .single()
              .type,
          )
          assertTrue(
            models
              .getValue("Pet")
              .properties
              .single()
              .required,
          )
          assertEquals(listOf(GeneratedTypeRef.named("Pet")), models.getValue("Cat").inherits)
          assertEquals(listOf("own"), models.getValue("Cat").properties.map { it.name })
          val schemas = schemas(source)
          val composition = OpenApiSchemaComposition(schemas)
          val parts = schemas.getValue("Node")["allOf"] as List<*>
          val original = ((parts.first() as Map<*, *>)["properties"] as Map<*, *>)["next"]
          var origin = ((parts.last() as Map<*, *>)["properties"] as Map<*, *>)["next"] as Map<*, *>
          var merged = (composition.resolve(schemas.getValue("Node"))["properties"] as Map<*, *>)["next"] as Map<*, *>
          for (level in depth downTo 0) {
            if (level > 0 || version == "3.1.0") {
              assertEquals("Updated $level", merged["description"])
              assertEquals("Inherited $level", merged["title"])
            }
            if (depth > 0 || version == "3.1.0") {
              val location = assertThrows(GenerationException::class.java) { (merged as OpenApiSchema).error("merged") }
              val expected = assertThrows(GenerationException::class.java) { (origin as OpenApiSchema).error("origin") }
              assertEquals(expected.file, location.file)
              assertEquals(expected.line, location.line)
            }
            if (level > 0) {
              merged = (merged["allOf"] as List<*>).single() as Map<*, *>
              origin = (origin["allOf"] as List<*>).single() as Map<*, *>
            }
          }
          assertEquals("#/components/schemas/Node", merged["\$ref"])
          assertEquals(original, (composition.resolve(schemas.getValue("Pet"))["properties"] as Map<*, *>)["value"])
          assertEquals(result, converter.convert(source.toUri()))
          if (depth == 2) {
            Executors.newFixedThreadPool(2).use { executor ->
              executor.invokeAll(List(4) { Callable { converter.convert(source.toUri()) } }).forEach {
                assertEquals(result, it.get())
              }
            }
          }
        }
      }
    }
  }

  @Test
  fun `unproven recursive property intersections retain source located failures`(
    @TempDir directory: Path,
  ) {
    val reference = "${'$'}ref: '#/components/schemas/Node'"
    for (changed in listOf(
      "${'$'}ref: '#/components/schemas/Other'",
      "$reference, maxProperties: 1",
      "$reference, default: {description: Literal, ${'$'}ref: missing.yaml}",
      "$reference, deprecated: true",
      "$reference, x-sunday-name: Different",
    )) {
      val source =
        api(
          directory,
          """
          Node:
            allOf:
              - type: object
                properties: {next: {$reference, description: Parent}}
              - properties: {next: {$changed, description: Child}}
          Other: {type: string}
          """.trimIndent(),
        )
      val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
      assertEquals(source.toUri().toString(), error.file)
      assertTrue(error.line >= 6, error.toString())
      assertTrue(error.message.orEmpty().contains("Cyclic OpenAPI schema composition"), error.message)
    }
  }

  @Test
  fun `representable field refinements preserve inheritance and incompatible intersections retain their source`(
    @TempDir directory: Path,
  ) {
    for ((parent, child) in listOf(
      "type: string, minLength: 1" to "type: string, minLength: 2",
      "type: string, nullable: true" to "type: string",
      "type: string, default: before" to "type: string, default: after",
      "type: string" to "type: string, deprecated: true",
      "type: string" to "type: string, readOnly: true",
      "type: string" to "type: string, writeOnly: true",
      "type: string" to "type: string, x-sunday-name: different",
    )) {
      val source =
        api(
          directory,
          """
          Parent: {type: object, properties: {value: {$parent}}}
          Child:
            allOf:
              - ${'$'}ref: '#/components/schemas/Parent'
              - properties: {value: {$child, description: Child value}}
          """.trimIndent(),
        )
      val result = OpenApiToGeneratedApi().convert(source.toUri()).models.single { it.name == "Child" }
      val changesGenerationMetadata =
        child.contains("deprecated") ||
          child.contains("readOnly") ||
          child.contains("writeOnly") ||
          child.contains("x-sunday")
      assertEquals(changesGenerationMetadata, result.inherits.isEmpty(), child)
      assertEquals(listOf("value"), result.properties.map { it.name })
    }
    val source =
      api(
        directory,
        """
        Parent: {type: object, properties: {value: {type: string}}}
        Child:
          allOf:
            - ${'$'}ref: '#/components/schemas/Parent'
            - required: [value]
              properties: {value: {type: string, description: Required child value}}
        """.trimIndent(),
      )
    val child = OpenApiToGeneratedApi().convert(source.toUri()).models.single { it.name == "Child" }
    assertEquals(listOf(GeneratedTypeRef.named("Parent")), child.inherits)
    assertTrue(child.properties.single().required)
    val invalid =
      api(
        directory,
        """
        Parent: {type: object, properties: {value: {type: string}}}
        Child:
          allOf:
            - ${'$'}ref: '#/components/schemas/Parent'
            - properties: {value: {type: integer, description: Incompatible}}
        """.trimIndent(),
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(invalid.toUri()) }
    assertEquals(invalid.toUri().toString(), error.file)
    assertTrue(error.line >= 7, error.toString())
    assertTrue(error.message.orEmpty().contains("intersection"), error.message)
  }

  private fun schemas(source: Path): Map<String, Map<*, *>> {
    val document = OpenApiReferenceResolver().resolve(source.toUri()).document
    val schemas = (document["components"] as Map<*, *>)["schemas"] as Map<*, *>
    return schemas.entries.associate { it.key.toString() to it.value as Map<*, *> }
  }

  private fun api(
    directory: Path,
    schemas: String,
    version: String = "3.1.0",
  ): Path =
    directory.resolve("api.yaml").apply {
      writeText(
        "openapi: $version\ninfo: {title: Inheritance, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    "),
      )
    }
}
