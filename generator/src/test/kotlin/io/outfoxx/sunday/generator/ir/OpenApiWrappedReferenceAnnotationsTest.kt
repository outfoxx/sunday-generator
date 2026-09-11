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

class OpenApiWrappedReferenceAnnotationsTest {
  @ParameterizedTest
  @ValueSource(strings = ["description", "summary", "title", "example", "examples", "externalDocs", "\$comment"])
  fun `wrapped recursive unions retain contracts and overlay documentation`(
    annotation: String,
    @TempDir directory: Path,
  ) {
    for (version in listOf("3.0.3", "3.1.0")) {
      for (keyword in listOf("anyOf", "oneOf")) {
        for (depth in 1..2) {
          val inherited = if (annotation == "title") "description" else "title"

          fun reference(
            target: String,
            label: String,
            level: Int = depth,
          ): String {
            val contract =
              if (level == 0) {
                "${'$'}ref: '#/components/schemas/$target'"
              } else {
                "allOf: [${reference(target, label, level - 1)}]"
              }
            val retained = if (label == "Parent") ", $inherited: 'Inherited $level'" else ""
            return "{$contract, $annotation: ${documentaryValue(annotation, "$label $level")}$retained}"
          }
          val parent = reference("Node", "Parent")
          val child = reference("NodeAlias", "Child")
          val recursiveChild = reference("Node", "Child")
          val nullBranch =
            if (version == "3.0.3") "{type: object, nullable: true, enum: [null]}" else "{type: 'null'}"
          val declarations =
            listOf(
              """
              Node:
                allOf:
                  - type: object
                    required: [next]
                    properties:
                      next: {$keyword: [$parent, $nullBranch]}
                  - properties:
                      next: {$keyword: [$recursiveChild, $nullBranch]}
              """.trimIndent(),
              "NodeAlias: {${'$'}ref: '#/components/schemas/NodeAlias2'}",
              "NodeAlias2: {${'$'}ref: '#/components/schemas/Node'}",
              "Pet: {type: object, required: [value], properties: {value: {$keyword: [$parent, $nullBranch]}}}",
              """
              Cat:
                allOf:
                  - {${'$'}ref: '#/components/schemas/Pet'}
                  - properties:
                      value: {$keyword: [$child, $nullBranch]}
                      lives: {type: integer}
              """.trimIndent(),
            )
          for (order in listOf(declarations, declarations.reversed())) {
            val source = api(directory, order.joinToString("\n"), version)
            val converter = OpenApiToGeneratedApi()
            val result = converter.convert(source.toUri())
            val models = result.models.associateBy { it.name }
            assertEquals(setOf("Node", "NodeAlias", "NodeAlias2", "Pet", "Cat"), models.keys)
            for ((model, name) in listOf("Node" to "next", "Pet" to "value")) {
              val property = models.getValue(model).properties.single()
              assertEquals(name, property.name)
              assertTrue(property.required)
              assertEquals(GeneratedTypeRef.named("Node", nullable = true), property.type)
            }
            assertEquals(listOf(GeneratedTypeRef.named("Pet")), models.getValue("Cat").inherits)
            assertEquals(listOf("lives"), models.getValue("Cat").properties.map { it.name })
            assertFalse(
              models
                .getValue("Cat")
                .properties
                .single()
                .required,
            )

            val document = OpenApiReferenceResolver().resolve(source.toUri()).document
            val schemas =
              ((document["components"] as Map<*, *>)["schemas"] as Map<*, *>).entries.associate {
                it.key.toString() to it.value as Map<*, *>
              }
            val composition = OpenApiSchemaComposition(schemas)
            val original = branch(schemas.getValue("Pet"), "value", keyword)
            for ((model, name) in listOf("Node" to "next", "Cat" to "value")) {
              val operand = (schemas.getValue(model)["allOf"] as List<*>).last() as Map<*, *>
              var expected = branch(operand, name, keyword)
              var merged = branch(composition.resolve(schemas.getValue(model)), name, keyword)
              for (level in depth downTo 0) {
                if (level > 0 || version == "3.1.0") {
                  assertEquals(documentaryObject(annotation, "Child $level"), merged[annotation])
                  assertEquals("Inherited $level", merged[inherited])
                }
                val location = assertThrows(GenerationException::class.java) { (merged as OpenApiSchema).error("use") }
                val origin =
                  assertThrows(GenerationException::class.java) { (expected as OpenApiSchema).error("origin") }
                assertEquals(source.toUri().toString(), location.file)
                assertEquals(origin.line, location.line)
                if (level > 0) {
                  merged = (merged["allOf"] as List<*>).single() as Map<*, *>
                  expected = (expected["allOf"] as List<*>).single() as Map<*, *>
                }
              }
              assertEquals("#/components/schemas/Node", merged["\$ref"])
            }
            assertEquals(documentaryObject(annotation, "Parent $depth"), original[annotation])
            assertEquals(original, branch(composition.resolve(schemas.getValue("Pet")), "value", keyword))
            assertEquals(result, converter.convert(source.toUri()))
            if (annotation == "description") {
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
  }

  @Test
  fun `wrapped recursive contract differences retain source located diagnostics`(
    @TempDir directory: Path,
  ) {
    for (keyword in listOf("anyOf", "oneOf")) {
      val reference = "{${'$'}ref: '#/components/schemas/Node'}"
      val parent = "{allOf: [$reference], description: Parent}"
      val otherKeyword = if (keyword == "anyOf") "oneOf" else "anyOf"
      val differences =
        listOf(
          "$keyword: [{allOf: [{${'$'}ref: '#/components/schemas/Other'}]}, {type: 'null'}]",
          "$keyword: [$parent]",
          "$keyword: [{type: 'null'}, $parent]",
          "$keyword: [$parent, {type: 'null'}, {type: integer}]",
          "$otherKeyword: [$parent, {type: 'null'}]",
          "$keyword: [{allOf: [$reference], maxProperties: 1}, {type: 'null'}]",
          "$keyword: [{allOf: [{allOf: [$reference], maxProperties: 1}]}, {type: 'null'}]",
          "$keyword: [{allOf: [$reference], default: {next: null}}, {type: 'null'}]",
          "$keyword: [{allOf: [$reference], deprecated: true}, {type: 'null'}]",
          "$keyword: [{allOf: [$reference], x-sunday-name: Other}, {type: 'null'}]",
        )
      for (difference in differences) {
        assertIntersection(directory, "$keyword: [$parent, {type: 'null'}]", difference)
      }
      for (key in listOf("enum", "const", "default", "x-data", "unknown")) {
        fun literal(label: String): String {
          val value = "{description: $label, ${'$'}ref: missing.yaml, ${'$'}id: missing-resource.yaml}"
          return if (key == "enum") "[$value]" else value
        }
        assertIntersection(
          directory,
          "$keyword: [{allOf: [$reference], $key: ${literal("Parent")}}, {type: 'null'}]",
          "$keyword: [{allOf: [$reference], $key: ${literal("Child")}}, {type: 'null'}]",
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
        Node:
          allOf:
            - type: object
              properties: {next: {$parent}}
            - properties: {next: {$child}}
        Other: {allOf: [{${'$'}ref: '#/components/schemas/Node'}], description: Other}
        """.trimIndent(),
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.line >= 6, error.toString())
    assertTrue(error.message.orEmpty().contains("Unsupported OpenAPI schema intersection"), error.message)
  }

  private fun documentaryValue(
    key: String,
    value: String,
  ): String =
    when (key) {
      "examples" -> "['$value']"
      "externalDocs" -> "{url: 'https://example.test', description: '$value'}"
      else -> "'$value'"
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

  private fun branch(
    schema: Map<*, *>,
    name: String,
    keyword: String,
  ): Map<*, *> = (((schema["properties"] as Map<*, *>)[name] as Map<*, *>)[keyword] as List<*>).first() as Map<*, *>

  private fun api(
    directory: Path,
    schemas: String,
    version: String = "3.1.0",
  ): Path =
    directory.resolve("api.yaml").apply {
      writeText(
        "openapi: $version\ninfo: {title: Wrapped references, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.prependIndent("    "),
      )
    }
}
