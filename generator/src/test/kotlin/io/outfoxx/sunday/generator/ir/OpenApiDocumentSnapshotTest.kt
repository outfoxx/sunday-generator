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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class OpenApiDocumentSnapshotTest {
  @Test
  fun `relative paths require compatible Windows drives and UNC shares`() {
    Jimfs.newFileSystem(Configuration.windows()).use { fs ->
      val base = fs.getPath("C:\\project\\schemas")
      assertEquals(
        "shared/type.yaml",
        OpenApiDocumentSnapshot.relativePath(base, fs.getPath("C:\\project\\schemas\\shared\\type.yaml")),
      )
      assertEquals(
        "../type.yaml",
        OpenApiDocumentSnapshot.relativePath(base, fs.getPath("C:\\project\\schemas\\..\\type.yaml")),
      )
      assertNull(OpenApiDocumentSnapshot.relativePath(base, fs.getPath("D:\\shared\\type.yaml")))
      val share = fs.getPath("\\\\server\\one\\project")
      assertEquals(
        "../type.yaml",
        OpenApiDocumentSnapshot.relativePath(share, fs.getPath("\\\\server\\one\\type.yaml")),
      )
      assertNull(OpenApiDocumentSnapshot.relativePath(share, fs.getPath("\\\\server\\two\\type.yaml")))
      assertNull(OpenApiDocumentSnapshot.relativePath(share, fs.getPath("\\\\other\\one\\type.yaml")))
    }
  }

  @Test
  fun `incompatible filesystems retain absolute escaped URIs in version one snapshots`(
    @TempDir directory: Path,
  ) {
    Jimfs.newFileSystem(Configuration.windows()).use { fs ->
      val source = directory.resolve("schema # with %.yaml")
      val body = "type: object\nproperties: {value: {type: string}}"
      source.writeText(body)
      val uri = source.toUri()
      val alias = URI("https://example.test/schema?version=1")
      val snapshot = directory.resolve("snapshot")
      val documents =
        mapOf(
          uri to OpenApiLoadedDocument(uri, body.toByteArray()),
          alias to OpenApiLoadedDocument(uri, body.toByteArray()),
        )
      OpenApiDocumentSnapshot.write(snapshot, fs.getPath("C:\\project"), documents)
      val manifestFile = snapshot.resolve("manifest.yaml")
      val timestamp = Files.getLastModifiedTime(manifestFile)
      val manifest = ObjectMapper(YAMLFactory()).readTree(manifestFile.toFile())
      assertEquals(1, manifest.path("version").asInt())
      for (entry in manifest.path("documents")) {
        assertFalse(entry.path("source").has("file"))
        assertEquals(uri.toString(), entry.path("uri").path("uri").asText())
        assertTrue(
          entry
            .path("uri")
            .path("uri")
            .asText()
            .contains("%23"),
        )
      }
      OpenApiDocumentSnapshot.write(snapshot, fs.getPath("C:\\project"), documents)
      assertEquals(timestamp, Files.getLastModifiedTime(manifestFile))
      Files.delete(source)
      val loader = OpenApiDocumentSnapshot.loader(snapshot, fs.getPath("D:\\relocated"))
      for (key in documents.keys) {
        val loaded = loader.load(key)
        assertEquals(uri, loaded.uri)
        assertEquals(body, String(loaded.bytes))
      }
    }
  }
}
