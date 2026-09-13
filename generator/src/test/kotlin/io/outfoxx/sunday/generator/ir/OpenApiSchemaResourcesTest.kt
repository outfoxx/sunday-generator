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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiSchemaResourcesTest {
  @Test
  fun `resource identifiers with different empty path segments stay distinct`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
        First:
          ${'$'}id: https://schemas.example.test/a//schema
          ${'$'}anchor: target
          type: string
        Second:
          ${'$'}id: https://schemas.example.test/a/schema
          ${'$'}anchor: target
          type: integer
        Container:
          type: object
          properties:
            first: {${'$'}ref: 'https://schemas.example.test/a//schema#target'}
            second: {${'$'}ref: 'https://schemas.example.test/a/schema#target'}
        """.trimIndent(),
      )
    val requests = mutableListOf<URI>()
    val result =
      OpenApiToGeneratedApi().convert(
        source.toUri(),
        OpenApiDocumentLoader { uri ->
          requests.add(uri)
          assertEquals(source.toUri(), uri)
          OpenApiLoadedDocument(uri, Files.readAllBytes(source))
        },
      )
    val properties =
      result.models
        .single { it.name == "Container" }
        .properties
        .associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("First"), properties.getValue("first").type)
    assertEquals(GeneratedTypeRef.named("Second"), properties.getValue("second").type)
    assertEquals(listOf(source.toUri()), requests)
  }

  @Test
  fun `embedded identifiers and forward anchors share model identity without fetching`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      User:
        ${'$'}id: https://schemas.example.test/users.json
        type: object
        properties:
          address: {${'$'}ref: 'addresses.json#home'}
          repeated: {${'$'}ref: 'addresses.json#/${'$'}defs/Address'}
          parent: {${'$'}ref: ''}
      Addresses:
        ${'$'}id: https://schemas.example.test/addresses.json
        type: object
        ${'$'}defs:
          Address:
            ${'$'}anchor: home
            type: object
            properties:
              street: {type: string}
      Other:
        ${'$'}id: https://schemas.example.test/other.json
        ${'$'}anchor: home
        type: object
    """,
      )
    val requests = mutableListOf<URI>()
    val loader =
      OpenApiDocumentLoader { uri ->
        requests.add(uri)
        assertEquals("file", uri.scheme)
        OpenApiLoadedDocument(uri, Files.readAllBytes(Path.of(uri)))
      }
    val result = OpenApiToGeneratedApi().convert(source.toUri(), loader)
    val properties =
      result.models
        .single { it.name == "User" }
        .properties
        .associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("Address"), properties.getValue("address").type)
    assertEquals(properties.getValue("address").type, properties.getValue("repeated").type)
    assertEquals(GeneratedTypeRef.named("User"), properties.getValue("parent").type)
    assertEquals(listOf(source.toUri()), requests)
  }

  @Test
  fun `nested relative ids rebase file loading and pointers to resource roots`(
    @TempDir directory: Path,
  ) {
    Files.createDirectories(directory.resolve("schemas/nested"))
    // A schema identifier's trailing-slash semantics must not depend on the local filesystem.
    Files.createDirectories(directory.resolve("schemas/user.yaml"))
    directory.resolve("schemas/nested/name.yaml").writeText("type: string\nminLength: 2")
    val source =
      api(
        directory,
        """
      User:
        ${'$'}id: schemas/user.yaml
        type: object
        properties:
          profile:
            ${'$'}id: nested/profile.yaml
            type: object
            ${'$'}defs:
              Name: {${'$'}ref: name.yaml}
            properties:
              name: {${'$'}ref: '#/${'$'}defs/Name'}
              repeated: {${'$'}ref: name.yaml}
    """,
      )
    val resolution = OpenApiReferenceResolver().resolve(source.toUri())
    assertEquals(
      setOf(source.toUri(), directory.resolve("schemas/nested/name.yaml").toUri()),
      resolution.documents.keys,
    )
    val serialized = resolution.document.toString()
    assertFalse(serialized.contains("\$id="), serialized)
    assertTrue(serialized.contains("minLength=2"), serialized)
  }

  @Test
  fun `component collections index sibling resource identifiers before resolving`(
    @TempDir directory: Path,
  ) {
    val shared = directory.resolve("shared.yaml")
    shared.writeText(
      """
      components:
        schemas:
          Container:
            ${'$'}id: https://schemas.example.test/container
            type: object
            properties:
              item: {${'$'}ref: 'item#item'}
          Item:
            ${'$'}id: https://schemas.example.test/item
            ${'$'}anchor: item
            type: object
            properties:
              id: {type: string}
      """.trimIndent(),
    )
    val source = api(directory, "User: {\$ref: 'shared.yaml#/components/schemas/Container'}")
    val requests = mutableListOf<URI>()
    val loader =
      OpenApiDocumentLoader { uri ->
        requests.add(uri)
        assertEquals("file", uri.scheme)
        OpenApiLoadedDocument(uri, Files.readAllBytes(Path.of(uri)))
      }
    val result = OpenApiToGeneratedApi().convert(source.toUri(), loader)
    assertEquals(setOf("User", "Item"), result.models.map { it.name }.toSet())
    assertEquals(listOf(source.toUri(), shared.toUri()), requests)
  }

  @Test
  fun `retrieval pointer and anchor aliases deduplicate external declarations`(
    @TempDir directory: Path,
  ) {
    directory.resolve("shared.yaml").writeText(
      """
      ${'$'}id: https://schemas.example.test/shared
      ${'$'}defs:
        Item:
          ${'$'}anchor: item
          type: object
          properties:
            value: {type: string}
      """.trimIndent(),
    )
    val source =
      api(
        directory,
        """
      Container:
        type: object
        properties:
          a: {${'$'}ref: 'shared.yaml#item'}
          b: {${'$'}ref: 'https://schemas.example.test/shared#/${'$'}defs/Item'}
          c: {${'$'}ref: 'shared.yaml#/${'$'}defs/Item'}
    """,
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(2, result.models.size)
    assertEquals(
      1,
      result.models
        .single { it.name == "Container" }
        .properties
        .map { it.type }
        .distinct()
        .size,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["urn:example:user", "file:embedded", "alias.yaml?type=user"])
  fun `opaque resource identifiers and static dynamic anchors resolve locally`(
    identifier: String,
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      User:
        ${'$'}id: '$identifier'
        ${'$'}dynamicAnchor: user
        type: object
        properties:
          parent: {${'$'}ref: '#user'}
    """,
      )
    val result = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(
      GeneratedTypeRef.named("User"),
      result.models
        .single()
        .properties
        .single()
        .type,
    )
  }

  @Test
  fun `examples and defaults do not establish resources or trigger dynamic evaluation`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      User:
        type: object
        example: {${'$'}id: 42, ${'$'}anchor: 42, ${'$'}ref: missing, ${'$'}dynamicRef: '#bad'}
        default: {${'$'}id: 'https://example.test/ignored'}
        x-custom: {${'$'}anchor: 'invalid anchor'}
        properties:
          name: {type: string}
    """,
      )
    assertEquals(
      "User",
      OpenApiToGeneratedApi()
        .convert(source.toUri())
        .models
        .single()
        .name,
    )
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "\$id: 42", "\$id: 'https://example.test/schema#bad'", "\$anchor: 'bad anchor'",
      "\$anchor: 42", "\$dynamicRef: '#self'", "\$schema: https://example.test/dialect",
      "properties: {bad: {\$ref: '#bad%20anchor'}}", "properties: {bad: {\$ref: '#missing'}}",
    ],
  )
  fun `invalid resource declarations retain source locations`(
    declaration: String,
    @TempDir directory: Path,
  ) {
    val source = api(directory, "User:\n  type: object\n  $declaration")
    val failure = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertEquals(source.toUri().toString(), failure.file)
    assertTrue(failure.line > 1, failure.message)
    assertTrue(failure.column > 0, failure.message)
  }

  @ParameterizedTest
  @ValueSource(strings = ["\$id: https://example.test/duplicate", "\$anchor: duplicate"])
  fun `conflicting resource identifiers fail deterministically`(
    declaration: String,
    @TempDir directory: Path,
  ) {
    val source = api(directory, "A: {type: object, $declaration}\nB: {type: object, $declaration}")
    val failure = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
    assertTrue(failure.message.orEmpty().contains("Conflicting"), failure.message)
  }

  @Test
  fun `OpenAPI 30 retains document-relative references`(
    @TempDir directory: Path,
  ) {
    val source =
      api(
        directory,
        """
      User:
        ${'$'}id: https://example.test/ignored
        type: object
        properties:
          parent: {${'$'}ref: '#/components/schemas/User'}
    """,
      )
    source.writeText(Files.readString(source).replace("3.1.0", "3.0.3"))
    assertEquals(
      GeneratedTypeRef.named("User"),
      OpenApiToGeneratedApi()
        .convert(source.toUri())
        .models
        .single()
        .properties
        .single()
        .type,
    )
  }

  private fun api(
    directory: Path,
    schemas: String,
  ): Path =
    directory.resolve("api.yaml").also {
      it.writeText(
        "openapi: 3.1.0\ninfo: {title: Resource Test, version: 1.0.0}\npaths: {}\ncomponents:\n  schemas:\n" +
          schemas.trimIndent().prependIndent("    "),
      )
    }
}
