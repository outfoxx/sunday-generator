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
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
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

class OpenApiReferenceOriginTest {
  @ParameterizedTest
  @ValueSource(strings = ["direct", "transitive", "mapping", "rebased"])
  fun `remote references cannot open local files through any supported reference path`(
    kind: String,
    @TempDir directory: Path,
  ) {
    val local = directory.resolve("secret.yaml").apply { writeText("type: string\ndefault: LOCAL_ONLY") }.toUri()
    val source = directory.resolve("api.yaml").toUri()
    val remote = URI("https://schemas.example.test/entry")
    val final = if (kind == "transitive") remote.resolve("next.yaml") else remote
    val body =
      when (kind) {
        "mapping" ->
          "type: object\nproperties: {kind: {type: string}}\ndiscriminator:\n" +
            "  propertyName: kind\n  mapping: {private: '$local'}"
        "rebased" -> "${'$'}id: '${local.resolve(
          "resource.yaml",
        )}'\ntype: object\nproperties:\n  private: {${'$'}ref: secret.yaml}"
        else -> "${'$'}ref: '$local'"
      }
    val documents =
      mutableMapOf(
        source to OpenApiReferenceDocuments.document("Root", "Remote: {${'$'}ref: '$remote'}"),
        final to body,
      )
    if (kind == "transitive") documents[remote] = "${'$'}ref: next.yaml"
    val requested = mutableListOf<URI>()
    val loader =
      OpenApiDocumentLoader { uri ->
        requested.add(uri)
        assertFalse(uri == local, "Remote dependency must not invoke the loader for a local file")
        OpenApiLoadedDocument(uri, documents.getValue(uri).toByteArray())
      }
    val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver(loader).resolve(source) }
    assertEquals(final.toString(), error.file)
    assertEquals(
      when (kind) {
        "mapping" -> 5
        "rebased" -> 4
        else -> 1
      },
      error.line,
    )
    assertTrue(error.message.orEmpty().contains("cannot access local file"), error.message)
    val blockedUri = Regex("local file '([^']+)'").find(error.message.orEmpty())!!.groupValues[1]
    assertEquals(local, URI(blockedUri), error.message)
    assertFalse(error.message.orEmpty().contains("LOCAL_ONLY"))
    assertFalse(requested.contains(local))
  }

  @Test
  fun `ordinary remote Reference Objects also cannot load local targets`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml").toUri()
    val remote = URI("https://schemas.example.test/parameter")
    val local = directory.resolve("parameter.yaml").toUri()
    val documents =
      mapOf(
        source to
          "openapi: 3.0.3\ninfo: {title: Root, version: 1}\npaths: {}\ncomponents:\n" +
          "  parameters:\n    Remote: {${'$'}ref: '$remote'}",
        remote to "${'$'}ref: '$local'",
      )
    val loader =
      OpenApiDocumentLoader { uri ->
        assertFalse(uri == local)
        OpenApiLoadedDocument(uri, documents.getValue(uri).toByteArray())
      }
    val failure = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver(loader).resolve(source) }
    assertEquals(remote.toString(), failure.file)
    assertEquals(1, failure.line)
    assertTrue(failure.message.orEmpty().contains("cannot access local file"), failure.message)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `loaded local documents cannot be borrowed through retrieval ids or anchors`(
    reverse: Boolean,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml").toUri()
    val remote = URI("https://schemas.example.test/remote")
    val identifier = "https://schemas.example.test/local"
    for (target in listOf("$source#/components/schemas/Local", "$identifier#local", "$identifier#/properties/value")) {
      val schemas =
        listOf(
          "Local: {${'$'}id: '$identifier', ${'$'}anchor: local, type: object, properties: {value: {type: string}}}",
          "Remote: {${'$'}ref: '$remote'}",
        ).let { if (reverse) it.reversed() else it }
      val documents =
        mapOf(
          source to OpenApiReferenceDocuments.document("Root", *schemas.toTypedArray()),
          remote to "${'$'}ref: '$target'",
        )
      val requested = mutableListOf<URI>()
      val loader =
        OpenApiDocumentLoader { uri ->
          requested.add(uri)
          OpenApiLoadedDocument(uri, documents.getValue(uri).toByteArray())
        }
      val error = assertThrows(GenerationException::class.java) { OpenApiReferenceResolver(loader).resolve(source) }
      assertEquals(remote.toString(), error.file)
      assertTrue(error.message.orEmpty().contains("cannot access local file"), error.message)
      assertEquals(listOf(source, remote), requested)
    }
  }

  @Test
  fun `remote embedded file identifiers remain usable without file access`(
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("api.yaml").toUri()
    val requestedRemote = URI("https://schemas.example.test/entry")
    val effectiveRemote = URI("https://schemas.example.test/final")
    val body =
      """
      ${'$'}id: file:///virtual/resource.yaml
      type: object
      ${'$'}defs:
        Value:
          ${'$'}id: child.yaml
          ${'$'}anchor: value
          type: object
          properties: {text: {type: string}}
      properties:
        value: {${'$'}ref: 'child.yaml#value'}
        again: {${'$'}ref: 'file:///virtual/resource.yaml#/${'$'}defs/Value'}
      example: {${'$'}ref: 'file:///secret.yaml'}
      """.trimIndent()
    val requests = mutableListOf<URI>()
    val loader =
      OpenApiDocumentLoader { uri ->
        requests.add(uri)
        when (uri) {
          source ->
            OpenApiLoadedDocument(
              source,
              OpenApiReferenceDocuments.document("Root", "Remote: {${'$'}ref: '$requestedRemote'}").toByteArray(),
            )
          requestedRemote -> OpenApiLoadedDocument(effectiveRemote, body.toByteArray())
          else -> error("Embedded file resource must not be retrieved: $uri")
        }
      }
    val api = OpenApiToGeneratedApi().convert(source, loader)
    val properties = api.models.single { it.name == "Remote" }.properties
    assertEquals(listOf("value", "again"), properties.map { it.name })
    assertEquals(properties.first().type, properties.last().type)
    assertEquals(listOf(source, requestedRemote), requests)
  }

  @Test
  fun `local schema identifiers do not turn local reference chains into remote ones`(
    @TempDir directory: Path,
  ) {
    val local = directory.resolve("local.yaml").apply { writeText("type: object\nproperties: {local: {type: string}}") }
    val source =
      directory.resolve("api.yaml").apply {
        writeText(
          OpenApiReferenceDocuments.document(
            "Root",
            "Local: {${'$'}id: 'https://schemas.example.test/local', ${'$'}ref: '${local.toUri()}'}",
          ),
        )
      }
    val api = OpenApiToGeneratedApi().convert(source.toUri())
    assertEquals(
      "local",
      api.models
        .single()
        .properties
        .single()
        .name,
    )
  }

  @Test
  fun `file boundary follows redirect origins in online offline and captured resolution`(
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { server ->
      val local = directory.resolve("secret.yaml").apply { writeText("type: string\ndefault: LOCAL_ONLY") }
      val source =
        directory.resolve("api.yaml").apply {
          writeText(OpenApiReferenceDocuments.document("Root", "Remote: {${'$'}ref: '${server.baseUri}entry'}"))
        }
      val body = "${'$'}ref: '${local.toUri()}'"
      server.respond("/entry", "", 302, mapOf("Location" to "/final"))
      server.respond("/final", body)
      val options = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true)
      val finalUri = server.baseUri.resolve("final")
      val snapshot = directory.resolve("snapshot")
      OpenApiDocumentSnapshot.write(
        snapshot,
        directory,
        mapOf(
          source.toUri() to OpenApiLoadedDocument(source.toUri(), Files.readAllBytes(source)),
          server.baseUri.resolve("entry") to OpenApiLoadedDocument(finalUri, body.toByteArray()),
          // Older captured inputs may already contain the local target. They must not bypass the boundary.
          local.toUri() to OpenApiLoadedDocument(local.toUri(), "type: string".toByteArray()),
        ),
      )
      val loaders =
        listOf(
          OpenApiDocumentLoader.create(options),
          OpenApiDocumentLoader.create(options.copy(offline = true, allowPrivateNetwork = false)),
          OpenApiDocumentSnapshot.loader(snapshot, directory),
        )
      for ((index, delegate) in loaders.withIndex()) {
        val requested = mutableListOf<URI>()
        val loader =
          OpenApiDocumentLoader { uri ->
            requested.add(uri)
            delegate.load(uri)
          }
        val failure =
          assertThrows(GenerationException::class.java) { OpenApiReferenceResolver(loader).resolve(source.toUri()) }
        assertEquals(finalUri.toString(), failure.file)
        assertEquals(1, failure.line)
        assertTrue(failure.message.orEmpty().contains("cannot access local file"), failure.message)
        assertFalse(requested.contains(local.toUri()))
        assertFalse(failure.message.orEmpty().contains("LOCAL_ONLY"))
        if (index == 0) {
          assertEquals(listOf("/entry", "/final"), server.requests.toList())
        } else {
          assertTrue(server.requests.isEmpty())
        }
        server.requests.clear()
      }
    }
  }
}
