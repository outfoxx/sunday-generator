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

class OpenApiUnionAnnotationsTest {
  @ParameterizedTest
  @ValueSource(strings = ["description", "summary", "title", "example", "examples", "externalDocs", "\$comment"])
  fun `documentary union branches preserve nullable inherited fields`(
    annotation: String,
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      for (keyword in listOf("anyOf", "oneOf")) {
        val nullBranch = if (version == "3.0.3") "type: string, nullable: true, enum: [null]" else "type: 'null'"
        val inheritedAnnotation = if (annotation == "title") "description" else "title"
        val declarations =
          listOf(
            """
            Pet:
              type: object
              required: [value]
              properties:
                value:
                  $keyword:
                    - type: string
                      minLength: 1
                      $inheritedAnnotation: Inherited
                      $annotation: ${documentaryValue(annotation, "Parent")}
                    - {$nullBranch, description: Parent null}
            """.trimIndent(),
            """
            Cat:
              allOf:
                - ${'$'}ref: '#/components/schemas/Pet'
                - type: object
                  properties:
                    value:
                      $keyword:
                        - {type: string, minLength: 1, $annotation: ${documentaryValue(annotation, "Child")}}
                        - {$nullBranch}
                    lives: {type: integer}
            """.trimIndent(),
          )
        for (order in listOf(declarations, declarations.reversed())) {
          val source = api(directory, order.joinToString("\n"), version)
          val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
          val pet = models.getValue("Pet")
          val cat = models.getValue("Cat")
          assertEquals(setOf("Pet", "Cat"), models.keys)
          assertEquals(listOf(GeneratedTypeRef.named("Pet")), cat.inherits)
          assertEquals(listOf("lives"), cat.properties.map { it.name })
          assertFalse(cat.properties.single().required)
          assertEquals(GeneratedTypeRef.scalar("string", nullable = true), pet.properties.single().type)
          assertTrue(pet.properties.single().required)

          val schemas = schemas(source)
          val composition = OpenApiSchemaComposition(schemas)
          val original = branch(property(schemas.getValue("Pet"), "value"), keyword)
          val child = composition.resolve(schemas.getValue("Cat"))
          val merged = branch(property(child, "value"), keyword)
          assertEquals(documentaryObject(annotation, "Child"), merged[annotation])
          assertEquals("Inherited", merged[inheritedAnnotation])
          assertEquals("Parent null", branch(property(child, "value"), keyword, 1)["description"])
          assertEquals(listOf("value"), child["required"])
          assertEquals(documentaryObject(annotation, "Parent"), original[annotation])
          assertEquals(original, branch(property(composition.resolve(schemas.getValue("Pet")), "value"), keyword))
          val location = assertThrows(GenerationException::class.java) { (merged as OpenApiSchema).error("child") }
          val childLocation =
            assertThrows(GenerationException::class.java) { (schemas.getValue("Cat") as OpenApiSchema).error("cat") }
          assertEquals(source.toUri().toString(), location.file)
          assertTrue(location.line > childLocation.line)
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["anyOf", "oneOf"])
  fun `aliases recursive references and nested branch documentation retain identity`(
    keyword: String,
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        Cat:
          allOf:
            - ${'$'}ref: '#/components/schemas/PetAlias'
            - ${'$'}ref: '#/components/schemas/Other'
            - properties:
                value:
                  $keyword: [{${'$'}ref: '#/components/schemas/NodeAlias', description: Child node}, {type: 'null'}]
                labels:
                  $keyword:
                    - type: array
                      items:
                        type: object
                        additionalProperties: {type: string, description: Child label}
                    - {type: 'null'}
                lives: {type: integer}
        PetAlias: {${'$'}ref: '#/components/schemas/Pet'}
        Other: {type: object, properties: {enabled: {type: boolean}}}
        Pet:
          type: object
          required: [value]
          properties:
            value:
              $keyword: [{${'$'}ref: '#/components/schemas/Node', title: Shared, description: Parent node}, {type: 'null'}]
            labels:
              $keyword:
                - type: array
                  items:
                    type: object
                    additionalProperties: {type: string, title: Shared, description: Parent label}
                - {type: 'null'}
        NodeAlias: {${'$'}ref: '#/components/schemas/Node'}
        Node:
          allOf:
            - type: object
              properties:
                next:
                  $keyword: [{${'$'}ref: '#/components/schemas/Node', title: Shared, description: Original next}, {type: 'null'}]
            - properties:
                next:
                  $keyword: [{${'$'}ref: '#/components/schemas/Node', description: Updated next}, {type: 'null'}]
        """.trimIndent(),
      )
    val converter = OpenApiToGeneratedApi()
    val result = converter.convert(source.toUri())
    val models = result.models.associateBy { it.name }
    val cat = models.getValue("Cat")
    assertEquals(listOf(GeneratedTypeRef.named("PetAlias"), GeneratedTypeRef.named("Other")), cat.inherits)
    assertEquals(listOf("lives"), cat.properties.map { it.name })
    assertEquals(
      GeneratedTypeRef.named("Node", nullable = true),
      models
        .getValue("Pet")
        .properties
        .first()
        .type,
    )
    assertEquals(
      GeneratedTypeRef.named("Node", nullable = true),
      models
        .getValue("Node")
        .properties
        .single()
        .type,
    )
    assertFalse(
      models
        .getValue("Node")
        .properties
        .single()
        .required,
    )
    assertEquals(setOf("Cat", "Pet", "PetAlias", "Other", "Node", "NodeAlias"), models.keys)
    val schemas = schemas(source)
    val composition = OpenApiSchemaComposition(schemas)
    val child = composition.resolve(schemas.getValue("Cat"))
    val reference = branch(property(child, "value"), keyword)
    assertEquals("#/components/schemas/Node", reference["\$ref"])
    assertEquals("Child node", reference["description"])
    assertEquals("Shared", reference["title"])
    val label = (
      (
        branch(
          property(child, "labels"),
          keyword,
        )["items"] as Map<*, *>
      )["additionalProperties"] as Map<*, *>
    )
    assertEquals("Child label", label["description"])
    assertEquals("Shared", label["title"])
    val next = branch(property(composition.resolve(schemas.getValue("Node")), "next"), keyword)
    assertEquals("#/components/schemas/Node", next["\$ref"])
    assertEquals("Updated next", next["description"])
    assertEquals("Shared", next["title"])
    assertEquals("Parent node", branch(property(schemas.getValue("Pet"), "value"), keyword)["description"])
    Executors.newFixedThreadPool(2).use { executor ->
      executor
        .invokeAll(
          List(4) {
            Callable { converter.convert(source.toUri()) }
          },
        ).forEach { assertEquals(result, it.get()) }
    }
  }

  @Test
  fun `compatible branch composition retains inherited documentation and literal data`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        Pet:
          type: object
          properties:
            value:
              anyOf:
                - allOf: [{type: string, minLength: 1, title: Shared}]
                  description: Parent
                  x-data: {description: Literal, ${'$'}ref: missing.yaml}
                - {type: 'null'}
        Cat:
          allOf:
            - ${'$'}ref: '#/components/schemas/Pet'
            - properties:
                value:
                  anyOf:
                    - type: string
                      minLength: 1
                      description: Child
                      x-data: {description: Literal, ${'$'}ref: missing.yaml}
                    - {type: 'null'}
        """.trimIndent(),
      )
    val models = OpenApiToGeneratedApi().convert(source.toUri()).models.associateBy { it.name }
    assertEquals(listOf(GeneratedTypeRef.named("Pet")), models.getValue("Cat").inherits)
    assertTrue(models.getValue("Cat").properties.isEmpty())
    val schemas = schemas(source)
    val merged = OpenApiSchemaComposition(schemas).resolve(schemas.getValue("Cat"))
    val value = branch(property(merged, "value"), "anyOf")
    assertEquals("Child", value["description"])
    assertEquals("Shared", value["title"])
    assertEquals(mapOf("description" to "Literal", "\$ref" to "missing.yaml"), value["x-data"])
  }

  @Test
  fun `unresolved recursive contracts report an intersection instead of reentering composition`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        Node:
          allOf:
            - type: object
              properties:
                next: {anyOf: [{${'$'}ref: '#/components/schemas/Node'}, {type: 'null'}]}
            - properties:
                next: {anyOf: [{${'$'}ref: '#/components/schemas/Other', description: Child}, {type: 'null'}]}
        Other: {allOf: [{${'$'}ref: '#/components/schemas/Node'}], description: Other}
        """.trimIndent(),
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.message.orEmpty().contains("Unsupported OpenAPI schema intersection"), error.message)
  }

  @Test
  fun `actual union differences retain source located intersection errors`(
    @TempDir directory: Path,
  ) {
    for (keyword in listOf("anyOf", "oneOf")) {
      val other = if (keyword == "anyOf") "oneOf" else "anyOf"
      val unchanged = "{type: string, minLength: 1, default: before}"
      val parent = "$keyword: [$unchanged, {type: 'null'}]"
      val refinements =
        listOf(
          "$keyword: [$unchanged]",
          "$keyword: [$unchanged, {type: 'null'}, {type: integer}]",
          "$keyword: [{type: 'null'}, $unchanged]",
          "$other: [$unchanged, {type: 'null'}]",
          "$keyword: [{type: string, minLength: 2, default: before}, {type: 'null'}]",
          "$keyword: [{type: string, nullable: true, minLength: 1, default: before}, {type: 'null'}]",
          "$keyword: [{type: string, minLength: 1, default: after}, {type: 'null'}]",
          "$keyword: [{type: string, minLength: 1, default: before, deprecated: true}, {type: 'null'}]",
          "$keyword: [{type: string, minLength: 1, default: before, readOnly: true}, {type: 'null'}]",
          "$keyword: [{type: string, minLength: 1, default: before, x-sunday-name: different}, {type: 'null'}]",
        )
      for (child in refinements) assertIntersection(directory, parent, child)
      for (key in listOf("enum", "const", "default", "x-data", "unknown")) {
        fun literal(description: String): String {
          val value = "{description: $description, ${'$'}ref: missing.yaml, ${'$'}id: also-missing.yaml}"
          return if (key == "enum") "[$value]" else value
        }
        assertIntersection(
          directory,
          "$keyword: [{type: object, $key: ${literal("Parent")}}, {type: 'null'}]",
          "$keyword: [{type: object, $key: ${literal("Child")}}, {type: 'null'}]",
        )
      }
    }
  }

  private fun assertIntersection(
    directory: Path,
    parent: String,
    child: String,
  ) {
    val source =
      api(
        directory,
        """
        Pet: {type: object, properties: {value: {$parent}}}
        Cat:
          allOf:
            - ${'$'}ref: '#/components/schemas/Pet'
            - properties: {value: {$child}}
        """.trimIndent(),
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.line >= 7, error.toString())
    assertTrue(error.message.orEmpty().contains("Unsupported OpenAPI schema intersection"), error.message)
  }

  private fun documentaryValue(
    key: String,
    value: String,
  ): String =
    when (key) {
      "examples" -> "[$value]"
      "externalDocs" -> "{url: 'https://example.test', description: $value}"
      else -> value
    }

  private fun documentaryObject(
    key: String,
    value: String,
  ): Any =
    when (key) {
      "examples" -> listOf(value)
      "externalDocs" -> mapOf("url" to "https://example.test", "description" to value)
      else -> value
    }

  private fun property(
    schema: Map<*, *>,
    name: String,
  ): Map<*, *> = (schema["properties"] as Map<*, *>)[name] as Map<*, *>

  private fun branch(
    schema: Map<*, *>,
    keyword: String,
    index: Int = 0,
  ): Map<*, *> = (schema[keyword] as List<*>)[index] as Map<*, *>

  private fun schemas(source: Path): Map<String, Map<*, *>> {
    val document = OpenApiReferenceResolver().resolve(source.toUri()).document
    return ((document["components"] as Map<*, *>)["schemas"] as Map<*, *>).entries.associate {
      it.key.toString() to
        it.value as Map<*, *>
    }
  }

  private fun api(
    directory: Path,
    schemas: String,
    version: String = "3.1.0",
  ): Path =
    directory.resolve("api.yaml").apply {
      writeText(
        "openapi: $version\ninfo: {title: Unions, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    "),
      )
    }
}
