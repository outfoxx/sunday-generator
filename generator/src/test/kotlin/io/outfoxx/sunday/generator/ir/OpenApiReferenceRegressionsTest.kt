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

class OpenApiReferenceRegressionsTest {
  @Test
  fun `reference siblings retain inherited fields and every inline contribution`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      Base:
        type: object
        required: [id]
        properties:
          id: {type: string}
      Child:
        ${'$'}ref: '#/components/schemas/Base'
        required: [name]
        properties:
          name: {type: string}
        allOf:
          - properties: {first: {type: boolean}}
          - properties: {second: {type: integer}}
            required: [second]
      Container:
        type: object
        properties:
          child:
            ${'$'}ref: '#/components/schemas/Base'
            properties: {label: {type: string}}
            required: [label]
          alias: {allOf: [{${'$'}ref: '#/components/schemas/Base'}]}
    """,
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    val base = result.models.single { it.name == "Base" }
    assertEquals("id", base.properties.single().name)
    assertTrue(base.properties.single().required)
    val child = result.models.single { it.name == "Child" }
    assertEquals(listOf(GeneratedTypeRef.named("Base")), child.inherits)
    assertEquals(setOf("name", "first", "second"), child.properties.map { it.name }.toSet())
    assertEquals(
      setOf("name", "second"),
      child.properties
        .filter { it.required }
        .map { it.name }
        .toSet(),
    )
    val inline = result.models.single { it.name == "ContainerChild" }
    assertEquals(listOf(GeneratedTypeRef.named("Base")), inline.inherits)
    assertEquals("label", inline.properties.single().name)
    assertTrue(inline.properties.single().required)
    val container = result.models.single { it.name == "Container" }
    assertEquals(GeneratedTypeRef.named("Base"), container.properties.single { it.name == "alias" }.type)
  }

  @Test
  fun `overlapping property constraints and requiredness intersect without changing the parent`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      Base:
        type: object
        properties:
          id: {type: string, minLength: 2}
      Child:
        ${'$'}ref: '#/components/schemas/Base'
        required: [id]
        properties:
          id: {minLength: 4, maxLength: 8}
      Count: {type: number, minimum: 0, maximum: 10}
      SmallCount:
        ${'$'}ref: '#/components/schemas/Count'
        type: integer
        minimum: 3
        maximum: 8
    """,
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    val base =
      result.models
        .single { it.name == "Base" }
        .properties
        .single()
    assertFalse(base.required)
    assertEquals(mapOf("minLength" to "2"), base.validation)
    val child = result.models.single { it.name == "Child" }
    assertEquals(listOf(GeneratedTypeRef.named("Base")), child.inherits)
    assertTrue(child.properties.single().required)
    assertEquals(mapOf("minLength" to "4", "maxLength" to "8"), child.properties.single().validation)
    assertEquals(
      "string",
      child.properties
        .single()
        .type.name,
    )
    val count = result.models.single { it.name == "SmallCount" }
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, count.kind)
    assertEquals("integer", count.aliases.single().name)
    assertEquals(mapOf("minimum" to "3", "maximum" to "8"), count.validation)
  }

  @Test
  fun `sibling-bearing aliases retain their identity and annotations stay local`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      Base:
        type: object
        description: Original
        properties: {id: {type: string}}
      Child:
        ${'$'}ref: '#/components/schemas/Base'
        properties: {name: {type: string}}
      Alias: {${'$'}ref: '#/components/schemas/Child'}
      Container:
        type: object
        properties:
          child: {${'$'}ref: '#/components/schemas/Child'}
          annotated: {${'$'}ref: '#/components/schemas/Base', description: Local}
          plain: {${'$'}ref: '#/components/schemas/Base'}
    """,
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    val properties =
      result.models
        .single { it.name == "Container" }
        .properties
        .associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("Child"), properties.getValue("child").type)
    assertEquals("Local", properties.getValue("annotated").documentation?.description)
    assertEquals("Original", properties.getValue("plain").documentation?.description)
    assertEquals(
      "name",
      result.models
        .single { it.name == "Alias" }
        .properties
        .single()
        .name,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["type: string", "minimum: 11"])
  fun `incompatible intersections report the originating schema`(
    sibling: String,
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      Base: {type: integer, maximum: 10}
      Child:
        ${'$'}ref: '#/components/schemas/Base'
        $sibling
    """,
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.line > 5)
    assertTrue(error.message.orEmpty().contains("Incompatible"), error.message)
  }

  @Test
  fun `different patterns are diagnosed instead of silently replaced`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      Base: {type: string, pattern: '^a'}
      Child: {${'$'}ref: '#/components/schemas/Base', pattern: 'z$'}
    """,
      )
    val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.message.orEmpty().contains("pattern"))
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `inline anchored declarations and all aliases use one recursive model`(
    reverse: Boolean,
    @TempDir directory: Path,
  ) {
    val node =
      """
      node:
        ${'$'}anchor: node
        type: [object, 'null']
        properties:
          value: {type: string}
          child: {${'$'}ref: '#node'}
      """.trimIndent()
    val aliases =
      """
      copy: {${'$'}ref: '#node'}
      pointer: {${'$'}ref: '#/properties/node'}
      identifier: {${'$'}ref: 'https://schemas.example.test/container#node'}
      """.trimIndent()
    val properties = (if (reverse) listOf(aliases, node) else listOf(node, aliases)).joinToString("\n")
    val source =
      api(
        directory,
        """
        Node: {type: string}
        Container:
          ${'$'}id: https://schemas.example.test/container
          type: object
          properties:
        """.trimIndent() + "\n" + properties.prependIndent("    ") +
          """

          Choice:
            oneOf: [{${'$'}ref: 'https://schemas.example.test/container#node'}]
            discriminator:
              propertyName: kind
              mapping: {node: 'https://schemas.example.test/container#/properties/node'}
          """.trimIndent().let {
            "\n$it"
          },
      )
    val requests = mutableListOf<String>()
    val result =
      OpenApiToGeneratedApi().convert(
        source.toUri(),
        OpenApiDocumentLoader { uri ->
          requests.add(uri.toString())
          assertEquals("file", uri.scheme)
          OpenApiLoadedDocument(
            uri,
            java.nio.file.Files
              .readAllBytes(Path.of(uri)),
          )
        },
      )
    val container = result.models.single { it.name == "Container" }
    assertEquals(setOf(GeneratedTypeRef.named("Node2", nullable = true)), container.properties.map { it.type }.toSet())
    assertEquals(1, result.models.count { it.name == "Node2" })
    assertFalse(result.models.any { it.name == "ContainerNode" })
    val canonical = result.models.single { it.name == "Node2" }
    assertEquals(
      GeneratedTypeRef.named("Node2", nullable = true),
      canonical.properties.single { it.name == "child" }.type,
    )
    assertEquals(
      GeneratedTypeRef.named("Node2"),
      result.models
        .single {
          it.name == "Choice"
        }.discriminatorMappings["node"],
    )
    assertEquals(1, requests.size)
  }

  @ParameterizedTest
  @ValueSource(strings = ["schema", "content", "in", "paths", "openapi"])
  fun `arbitrary schema annotations are preserved and never traversed`(
    annotation: String,
    @TempDir directory: Path,
  ) {
    val schema =
      """
      type: object
      $annotation: {${'$'}ref: 'https://never.example.test/missing', ${'$'}id: 42, ${'$'}anchor: 'invalid anchor'}
      properties: {id: {type: string}}
      """.trimIndent()
    directory.resolve("shared.yaml").writeText(schema)
    val source =
      api(
        directory,
        "Base:\n" + schema.prependIndent("  ") + "\n" +
          """
          Container:
            type: object
            properties:
              local: {${'$'}ref: '#/components/schemas/Base'}
              remote: {${'$'}ref: './shared.yaml'}
          """.trimIndent(),
      )
    val resolution = OpenApiReferenceResolver().resolve(source.toUri())
    val components = resolution.document["components"] as Map<*, *>
    val schemas = components["schemas"] as Map<*, *>
    assertTrue((schemas["Base"] as Map<*, *>).containsKey(annotation))
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(
      "id",
      result.models
        .single { it.name == "Base" }
        .properties
        .single()
        .name,
    )
    assertEquals(
      "id",
      result.models
        .single { it.name == "Shared" }
        .properties
        .single()
        .name,
    )
    assertEquals(
      2,
      resolution.documents.values
        .map { it.uri }
        .distinct()
        .size,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["properties: []", "allOf: {}", "required: [42]", "type: invalid"])
  fun `malformed recognized keywords remain invalid reference targets`(
    invalid: String,
    @TempDir directory: Path,
  ) {
    val source = api(directory, "Base: {$invalid}\nChild: {${'$'}ref: '#/components/schemas/Base'}")
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.message.orEmpty().contains("expected a schema"))
  }

  @Test
  fun `the OpenAPI document itself is not a schema target`(
    @TempDir directory: Path,
  ) {
    val source = api(directory, "Invalid: {${'$'}ref: '#'}")
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver().resolve(source.toUri()) }
    assertEquals(source.toUri().toString(), error.file)
    assertTrue(error.message.orEmpty().contains("expected a schema"))
  }

  @Test
  fun `OpenAPI-shaped annotations on a standalone schema do not change its document kind`(
    @TempDir directory: Path,
  ) {
    directory.resolve("shared.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Annotation, version: 1.0.0}
      ${'$'}id: https://schemas.example.test/shared
      ${'$'}anchor: node
      type: object
      properties:
        next: {${'$'}ref: '#node'}
      """.trimIndent(),
    )
    val source = api(directory, "Node: {${'$'}ref: './shared.yaml#node'}")
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    val node = result.models.single()
    assertEquals("Node", node.name)
    assertEquals(
      "Node",
      node.properties
        .single()
        .type.name,
    )
  }

  @Test
  fun `empty object bases retain their named inheritance`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        Base: {type: object}
        Child:
          ${'$'}ref: '#/components/schemas/Base'
          properties: {name: {type: string}}
        """.trimIndent(),
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(listOf(GeneratedTypeRef.named("Base")), result.models.single { it.name == "Child" }.inherits)
  }

  private fun api(
    directory: Path,
    schemas: String,
  ): Path =
    directory.resolve("api.yaml").also {
      it.writeText(
        "openapi: 3.1.0\ninfo: {title: Regression, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.trimIndent().prependIndent("    ") +
          "\n",
      )
    }
}
