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
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.writeText

class OpenApiDialectTest {
  @Test
  fun `OpenAPI 30 ignores every schema reference sibling and resolves actual target children`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      fixture.respond("/name.yaml", "type: string")
      val siblings =
        """
        ${'$'}ref: '#/components/schemas/Base'
        description: Ignored description
        summary: Ignored summary
        deprecated: true
        nullable: true
        default: ignored
        x-sunday-name: IgnoredName
        ${'$'}id: 42
        ${'$'}anchor: invalid anchor
        ${'$'}schema: unsupported
        ${'$'}dynamicRef: '#missing'
        properties:
          replacement:
            ${'$'}ref: 'BASEignored-property'
            ${'$'}id: https://ignored.example.test/resource
            ${'$'}anchor: same
          invalid: {${'$'}ref: 42}
        allOf: [{${'$'}ref: '#/missing'}]
        discriminator:
          propertyName: kind
          mapping: {ignored: 'BASEignored-mapping'}
        """.trimIndent().replace("BASE", fixture.baseUri.toString())
      val source = directory.resolve("api.yaml")
      source.writeText(
        document(
          "3.0.3",
          """
          Base:
            type: object
            description: Original
            required: [id]
            properties: {id: {${'$'}ref: 'BASEname.yaml'}}
          """.trimIndent().replace("BASE", fixture.baseUri.toString()) +
            "\nAlias:\n" + siblings.prependIndent("  ") +
            "\nContainer:\n  type: object\n  required: [inline]\n  properties:\n    inline:\n" +
            siblings.prependIndent("      "),
        ),
      )
      val loader =
        OpenApiDocumentLoader.create(
          OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
        )
      val resolution = OpenApiReferenceResolver(loader).resolve(source.toUri())
      val schemas = (resolution.document["components"] as Map<*, *>)["schemas"] as Map<*, *>
      val alias = schemas["Alias"] as Map<*, *>
      assertEquals("Original", alias["description"])
      assertFalse(alias.containsKey("summary"))
      assertFalse(alias.containsKey("default"))
      assertFalse(alias.containsKey("x-sunday-name"))
      assertFalse(alias.containsKey("discriminator"))
      val api = OpenApiToGeneratedApi().convert(source.toUri(), loader)
      assertEquals(setOf("Base", "Alias", "Container", "Name"), api.models.map { it.name }.toSet())
      for (name in listOf("Base", "Alias")) {
        val property =
          api.models
            .single { it.name == name }
            .properties
            .single()
        assertEquals("id", property.name)
        assertEquals(GeneratedTypeRef.named("Name"), property.type)
        assertTrue(property.required)
      }
      val inline =
        api.models
          .single { it.name == "Container" }
          .properties
          .single()
      assertEquals(GeneratedTypeRef.named("Base"), inline.type)
      assertEquals("Original", inline.documentation?.description)
      assertFalse(inline.deprecated)
      assertTrue(inline.required)
      assertEquals(listOf("/name.yaml"), fixture.requests.toList())
      assertEquals(setOf(source.toUri(), fixture.baseUri.resolve("name.yaml")), resolution.documents.keys)
    }
  }

  @ParameterizedTest
  @ValueSource(
    strings = ["", "https://spec.openapis.org/oas/3.1/dialect/base", "https://json-schema.org/draft/2020-12/schema"],
  )
  fun `OpenAPI 31 uses its own default dialect when referenced from 30`(dialect: String) {
    val documents = mixedDocuments("3.0.3", dialect)
    assertMixedConversion(OpenApiToGeneratedApi(), documents)
  }

  @Test
  fun `OpenAPI 30 keeps document relative references when referenced from 31`() {
    assertMixedConversion(OpenApiToGeneratedApi(), mixedDocuments("3.1.0"))
  }

  @Test
  fun `mixed document dialects remain isolated across repeated and parallel conversions`() {
    val converter = OpenApiToGeneratedApi()
    repeat(4) { assertMixedConversion(converter, mixedDocuments(if (it % 2 == 0) "3.0.3" else "3.1.0")) }
    Executors.newFixedThreadPool(4).use { executor ->
      val tasks =
        (0 until 16).map { index ->
          Callable { assertMixedConversion(converter, mixedDocuments(if (index % 2 == 0) "3.0.3" else "3.1.0")) }
        }
      executor.invokeAll(tasks).forEach { it.get(30, TimeUnit.SECONDS) }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `unsupported external document and schema dialects retain source locations`(schemaOverride: Boolean) {
    val documents =
      mixedDocuments(
        "3.0.3",
        if (schemaOverride) "" else "https://unsupported.example.test/dialect",
      ).toMutableMap()
    if (schemaOverride) {
      documents[shared] =
        documents.getValue(shared).replace(
          "${'$'}schema: https://json-schema.org/draft/2020-12/schema",
          "${'$'}schema: https://unsupported.example.test/dialect",
        )
    }
    val failure =
      assertThrows(GenerationException::class.java) {
        OpenApiToGeneratedApi().convert(root, loader(documents))
      }
    assertEquals(root.toString(), failure.file)
    assertTrue(failure.line > 4)
    assertTrue(failure.message.orEmpty().contains("Unsupported OpenAPI schema dialect"), failure.message)
    assertTrue(failure.message.orEmpty().contains(shared.toString()), failure.message)
  }

  private fun assertMixedConversion(
    converter: OpenApiToGeneratedApi,
    documents: Map<URI, String>,
  ) {
    val requested = mutableListOf<URI>()
    val api = converter.convert(root, loader(documents) { requested.add(it) })
    val node = api.models.single { it.name == "Node" }
    val properties = node.properties.associateBy { it.name }
    assertEquals(GeneratedTypeRef.named("Node", nullable = true), properties.getValue("child").type)
    assertTrue(properties.getValue("child").required)
    assertEquals(GeneratedTypeRef.named("Name"), properties.getValue("name").type)
    assertEquals(
      "string",
      api.models
        .single { it.name == "Name" }
        .aliases
        .single()
        .name,
    )
    assertEquals(documents.keys, requested.toSet())
    assertEquals(documents.size, requested.size)
  }

  private fun mixedDocuments(
    rootVersion: String,
    dialect: String = "",
  ): Map<URI, String> {
    val external31 = rootVersion.startsWith("3.0")
    val declarations =
      if (external31) {
        """
        ${'$'}id: ./models/node.yaml
        ${'$'}anchor: node
        type: [object, 'null']
        """.trimIndent()
      } else {
        """
        ${'$'}id: https://ignored.example.test/node
        ${'$'}anchor: invalid anchor
        type: object
        nullable: true
        """.trimIndent()
      }
    val recursive = if (external31) "#node" else "#/components/schemas/Node"
    val schemas =
      "Node:\n" + declarations.prependIndent("  ") + "\n" +
        """
        required: [child]
        properties:
          child: {${'$'}ref: '$recursive'}
          name:
            ${'$'}schema: https://json-schema.org/draft/2020-12/schema
            ${'$'}ref: name.yaml
        """.trimIndent().prependIndent("  ")
    return mapOf(
      root to document(rootVersion, "Node: {${'$'}ref: 'shared.yaml#/components/schemas/Node'}"),
      shared to document(if (external31) "3.1.0" else "3.0.3", schemas, dialect),
      root.resolve(if (external31) "models/name.yaml" else "name.yaml") to "type: string",
    )
  }

  private fun loader(
    documents: Map<URI, String>,
    requested: (URI) -> Unit = {},
  ): OpenApiDocumentLoader =
    OpenApiDocumentLoader { uri ->
      requested(uri)
      OpenApiLoadedDocument(uri, documents.getValue(uri).toByteArray())
    }

  private fun document(
    version: String,
    schemas: String,
    dialect: String = "",
  ): String =
    "openapi: $version\ninfo: {title: Dialects, version: 1.0.0}\npaths: {}\n" +
      (if (dialect.isEmpty()) "" else "jsonSchemaDialect: $dialect\n") +
      "components:\n  schemas:\n" + schemas.prependIndent("    ")

  private val root = URI("https://example.test/api.yaml")
  private val shared = URI("https://example.test/shared.yaml")
}
