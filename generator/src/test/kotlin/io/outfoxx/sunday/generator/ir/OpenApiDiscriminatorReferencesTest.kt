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

class OpenApiDiscriminatorReferencesTest {
  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `extensionless mappings discover otherwise unreachable recursive subtypes`(
    version: String,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    val cat = directory.resolve("cat")
    cat.writeText(
      """
      allOf:
        - ${'$'}ref: 'api.yaml#/components/schemas/Pet'
        - type: object
          properties:
            lives: {type: integer}
            child: {${'$'}ref: '#'}
      """.trimIndent(),
    )
    for (reference in listOf("cat", "./cat")) {
      source.writeText(document(version, "kitty: '$reference'"))
      val loader = OpenApiDocumentLoader.create(OpenApiReferenceOptions(directory.resolve("cache")))
      val resolution = OpenApiReferenceResolver(loader).resolve(source.toUri())
      assertEquals(setOf(source.toUri(), cat.toUri()), resolution.documents.keys)
      val api = OpenApiToGeneratedApi().convert(source.toUri(), loader)
      assertVariant(api, "Cat")
      assertEquals(
        mapOf("kitty" to "#/components/schemas/Cat"),
        mappings(resolution),
      )
      assertEquals(
        GeneratedTypeRef.named("Cat"),
        api.models
          .single { it.name == "Cat" }
          .properties
          .single { it.name == "child" }
          .type,
      )
      assertEquals(resolution.document, OpenApiReferenceResolver(loader).resolve(source.toUri()).document)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["3.0.3", "3.1.0"])
  fun `query mappings use the effective document or schema resource base`(
    version: String,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val scoped = version.startsWith("3.1")
      val targetPath = if (scoped) "/schemas/scoped/cat" else "/schemas/pet"
      val detailPath = if (scoped) "/schemas/scoped/detail" else "/schemas/detail"
      val name = if (scoped) "Cat" else "Pet2"
      val parentUri = server.baseUri.resolve("schemas/pet?base=original")
      val targetUri = server.baseUri.resolve("${targetPath.drop(1)}?schema=cat")
      val source = directory.resolve("api.yaml")
      source.writeText(
        """
        openapi: $version
        info: {title: Root, version: 1.0.0}
        paths: {}
        components:
          schemas:
            Pet: {${'$'}ref: '${server.baseUri}entry#/components/schemas/Pet'}
        """.trimIndent(),
      )
      server.respond("/entry", "", 302, mapOf("Location" to "/schemas/pet?base=original"))
      server.respond(detailPath, "type: object\nproperties: {id: {type: string}}")
      val variant =
        """
        allOf:
          - ${'$'}ref: '$parentUri#/components/schemas/Pet'
          - type: object
            properties:
              lives: {type: integer}
              detail: {${'$'}ref: 'detail'}
              child: {${'$'}ref: './${targetPath.substringAfterLast('/')}?schema=cat'}
        """.trimIndent()
      val relative = if (scoped) "./cat?schema=cat" else "./pet?schema=cat"
      for (reference in listOf("?schema=cat", relative)) {
        val parent =
          document(version, "kitty: '$reference'")
            .replace("    Pet:\n", "    Pet:\n      ${'$'}id: ./scoped/cat?base=resource\n")
        server.handlers["/schemas/pet"] = { exchange ->
          val body = if (exchange.requestURI.rawQuery == "schema=cat") variant else parent
          val bytes = body.toByteArray()
          exchange.sendResponseHeaders(200, bytes.size.toLong())
          exchange.responseBody.write(bytes)
        }
        if (scoped) server.respond(targetPath, variant)
        server.requests.clear()
        val options = OpenApiReferenceOptions(directory.resolve("cache"))
        val loader = OpenApiDocumentLoader.create(options)
        val resolution = OpenApiReferenceResolver(loader).resolve(source.toUri())
        assertTrue(targetUri in resolution.documents)
        assertTrue(server.baseUri.resolve(detailPath.drop(1)) in resolution.documents)
        assertEquals(setOf("#/components/schemas/$name"), mappings(resolution).values.toSet())
        val api = OpenApiToGeneratedApi().convert(source.toUri(), loader)
        assertVariant(api, name)
        assertEquals(
          listOf("/entry", "/schemas/pet?base=original", "$targetPath?schema=cat", detailPath),
          server.requests.toList(),
        )
        val offline = OpenApiDocumentLoader.create(options.copy(offline = true))
        assertEquals(api, OpenApiToGeneratedApi().convert(source.toUri(), offline))
        assertEquals(4, server.requests.size)
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["cat", "cat.v2"])
  fun `exact component names take precedence over relative documents`(
    name: String,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText(
      document("3.1.0", "kitty: '$name'") +
        "\n    $name: {allOf: [{${'$'}ref: '#/components/schemas/Pet'}, {properties: {lives: {type: integer}}}]}\n",
    )
    directory.resolve(name).writeText("invalid: [")
    val requests = mutableListOf<URI>()
    val loader =
      OpenApiDocumentLoader { uri ->
        requests.add(uri)
        OpenApiLoadedDocument(uri, Files.readAllBytes(Path.of(uri)))
      }
    val api = OpenApiToGeneratedApi().convert(source.toUri(), loader)
    assertVariant(api, name)
    assertEquals(listOf(source.toUri()), requests)
  }

  @Test
  fun `component names are case sensitive when selecting mapping targets`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    source.writeText(document("3.1.0", "kitty: Cat") + "\n    cat: {type: string}\n")
    directory.resolve("Cat").writeText(
      "allOf: [{${'$'}ref: 'api.yaml#/components/schemas/Pet'}, {properties: {lives: {type: integer}}}]",
    )
    assertVariant(OpenApiToGeneratedApi().convert(source.toUri()), "Cat2")
  }

  @Test
  fun `mapping failures retain the mapping source location`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml")
    for ((value, message) in listOf(
      "missing" to "Cannot read OpenAPI reference 'missing'",
      "'bad path'" to "Invalid OpenAPI reference 'bad path'",
      "42" to "discriminator mapping target must be a string",
      "false" to "discriminator mapping target must be a string",
      "null" to "discriminator mapping target must be a string",
      "{}" to "discriminator mapping target must be a string",
      "[]" to "discriminator mapping target must be a string",
      "'#/components/responses/Response'" to "expected a schema",
    )) {
      source.writeText(
        document("3.1.0", "kitty: $value") + "\n  responses:\n    Response: {description: Response}\n",
      )
      val error = assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi().convert(source.toUri()) }
      assertEquals(source.toUri().toString(), error.file)
      assertEquals(12, error.line)
      assertTrue(error.message.orEmpty().contains(message), error.message)
    }
    OpenApiHttpFixture().use { server ->
      source.writeText(
        document("3.1.0", "kitty: '?schema=missing'")
          .replace("    Pet:\n", "    Pet:\n      ${'$'}id: ${server.baseUri}cat\n"),
      )
      val options = GeneratedApiIrOptions(openApiReferences = OpenApiReferenceOptions(directory.resolve("cache")))
      val error =
        assertThrows(GenerationException::class.java) { OpenApiToGeneratedApi(options).convert(source.toUri()) }
      assertEquals(source.toUri().toString(), error.file)
      assertEquals(13, error.line)
      assertTrue(error.message.orEmpty().contains("?schema=missing"), error.message)
      assertTrue(error.message.orEmpty().contains("404"), error.message)
      assertEquals(listOf("/cat?schema=missing"), server.requests.toList())
    }
  }

  private fun document(
    version: String,
    mapping: String,
  ): String =
    """
    openapi: $version
    info: {title: Mapping references, version: 1.0.0}
    paths: {}
    components:
      schemas:
        Pet:
          type: object
          required: [kind]
          properties: {kind: {type: string}}
          discriminator:
            propertyName: kind
            mapping: {$mapping}
    """.trimIndent()

  private fun assertVariant(
    api: GeneratedApi,
    name: String,
  ) {
    val parent = api.models.single { it.name == "Pet" }
    assertEquals(GeneratedTypeRef.named(name), parent.discriminatorMappings.getValue("kitty"))
    val variant = api.models.single { it.name == name }
    assertEquals(listOf(GeneratedTypeRef.named("Pet")), variant.inherits)
    assertEquals("kitty", variant.discriminatorValue)
    assertEquals(GeneratedTypeRef.scalar("integer"), variant.properties.single { it.name == "lives" }.type)
  }

  private fun mappings(resolution: OpenApiReferenceResolution): Map<*, *> {
    val schemas = (resolution.document.getValue("components") as Map<*, *>)["schemas"] as Map<*, *>
    val discriminator = (schemas["Pet"] as Map<*, *>)["discriminator"] as Map<*, *>
    return discriminator["mapping"] as Map<*, *>
  }
}
