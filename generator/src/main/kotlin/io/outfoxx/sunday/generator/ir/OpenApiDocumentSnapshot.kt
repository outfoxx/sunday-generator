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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Persists captured documents as stable, relocatable inputs for a subsequent conversion. */
object OpenApiDocumentSnapshot {
  private val mapper = ObjectMapper(YAMLFactory())

  /** Writes content and a manifest without changing identical files or retaining unused bodies. */
  fun write(
    directory: Path,
    baseDirectory: Path,
    documents: Map<URI, OpenApiLoadedDocument>,
  ) {
    Files.createDirectories(directory)
    val bodies = mutableSetOf<String>()
    val entries =
      documents.entries.sortedBy { it.key.toString() }.map { (source, document) ->
        val bytes = document.bytes
        val body = bytes.sha256() + ".body"
        bodies.add(body)
        directory.resolve(body).writeOpenApiContent(bytes)
        mapOf(
          "source" to encodeUri(source, baseDirectory),
          "uri" to encodeUri(document.uri, baseDirectory),
          "body" to body,
        )
      }
    directory.resolve("manifest.yaml").writeOpenApiContent(
      mapper.writeValueAsBytes(
        mapOf(
          "version" to 1,
          "documents" to entries,
        ),
      ),
    )
    Files.list(directory).use { files ->
      files
        .filter {
          it.fileName.toString().endsWith(
            ".body",
          ) &&
            it.fileName.toString() !in bodies
        }.forEach(Files::delete)
    }
  }

  /** Creates a loader that reads only captured content; missing entries never fall back to the network or source files. */
  fun loader(
    directory: Path,
    baseDirectory: Path,
  ): OpenApiDocumentLoader {
    val manifest = mapper.readTree(directory.resolve("manifest.yaml").toFile())
    require(manifest.path("version").asInt() == 1) { "Unsupported OpenAPI document snapshot version" }
    val documents =
      manifest.path("documents").associate { entry ->
        val body = entry.path("body").asText()
        require(Regex("[0-9a-f]{64}\\.body").matches(body)) { "Invalid OpenAPI snapshot body '$body'" }
        val bytes = Files.readAllBytes(directory.resolve(body))
        require(bytes.sha256() == body.removeSuffix(".body")) { "Corrupt OpenAPI snapshot body '$body'" }
        decodeUri(entry.path("source"), baseDirectory).openApiDocumentUri() to
          OpenApiLoadedDocument(decodeUri(entry.path("uri"), baseDirectory), bytes)
      }
    return OpenApiDocumentLoader { uri ->
      documents[uri.openApiDocumentUri()]
        ?: throw IOException("OpenAPI document '$uri' is missing from the captured inputs")
    }
  }

  private fun encodeUri(
    uri: URI,
    baseDirectory: Path,
  ): Map<String, String> {
    if (uri.scheme.equals("file", ignoreCase = true)) {
      relativePath(baseDirectory, Path.of(uri))?.let { return mapOf("file" to it) }
    }
    return mapOf("uri" to uri.toString())
  }

  /** Cross-drive and cross-provider documents retain their absolute URI in the manifest. */
  internal fun relativePath(
    baseDirectory: Path,
    target: Path,
  ): String? {
    val base = baseDirectory.toAbsolutePath().normalize()
    val path = target.toAbsolutePath().normalize()
    if (base.fileSystem != path.fileSystem || base.root != path.root) return null
    return base.relativize(path).toString().replace('\\', '/')
  }

  private fun decodeUri(
    value: JsonNode,
    baseDirectory: Path,
  ): URI =
    value["file"]?.let { baseDirectory.resolve(it.asText()).normalize().toUri() } ?: URI(value.path("uri").asText())
}
