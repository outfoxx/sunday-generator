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

package io.outfoxx.sunday.generator.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest

/** Only permits replacement of empty directories or unchanged output recorded by this generation. */
internal object GeneratedOutputOwnership {
  private const val MANIFEST_NAME = ".sunday-generated-output.json"
  private val mapper = ObjectMapper()

  /** Checks existing content before either task execution or Gradle's destructive cache restoration. */
  fun validate(
    directory: File,
    owner: String,
  ) {
    if (!Files.exists(directory.toPath(), NOFOLLOW_LINKS)) return
    requireSafe(directory, !Files.isSymbolicLink(directory.toPath()) && directory.isDirectory, "not a real directory")
    val files = files(directory)
    if (files.isEmpty()) return
    val manifest = files[MANIFEST_NAME]
    requireSafe(directory, manifest != null, "existing files have no Sunday ownership manifest")
    val document =
      try {
        mapper.readTree(requireNotNull(manifest))
      } catch (failure: java.io.IOException) {
        throw IllegalArgumentException(
          "Unsafe generated output directory '$directory': unreadable ownership manifest",
          failure,
        )
      }
    requireSafe(
      directory,
      document?.path("version")?.asInt() == 1 &&
        document.path("owner").asText() == owner &&
        document.path("files").isObject,
      "ownership manifest does not belong to this generation",
    )
    val recorded = document.path("files")
    for ((path, file) in files.filterKeys { it != MANIFEST_NAME }) {
      requireSafe(directory, recorded.path(path).asText() == checksum(file), "unowned or modified file '$path'")
    }
  }

  /** Records only successfully generated staging files; the manifest travels with cached output. */
  fun record(
    directory: File,
    owner: String,
  ) {
    val document = mapper.createObjectNode().put("version", 1).put("owner", owner)
    val recorded = document.putObject("files")
    for ((path, file) in files(directory).toSortedMap()) {
      requireSafe(directory, path != MANIFEST_NAME, "generated output collides with ownership manifest")
      recorded.put(path, checksum(file))
    }
    mapper.writeValue(directory.resolve(MANIFEST_NAME), document)
  }

  private fun files(directory: File): Map<String, File> =
    Files.walk(directory.toPath()).use { paths ->
      buildMap {
        paths.forEach { path ->
          requireSafe(directory, !Files.isSymbolicLink(path), "symbolic links are not owned output")
          if (!Files.isDirectory(path, NOFOLLOW_LINKS)) {
            requireSafe(directory, Files.isRegularFile(path, NOFOLLOW_LINKS), "non-regular file in output")
            put(
              directory
                .toPath()
                .relativize(path)
                .toString()
                .replace(File.separatorChar, '/'),
              path.toFile(),
            )
          }
        }
      }
    }

  private fun checksum(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream ->
      val buffer = ByteArray(8192)
      while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        digest.update(buffer, 0, count)
      }
    }
    return java.util.HexFormat
      .of()
      .formatHex(digest.digest())
  }

  private fun requireSafe(
    directory: File,
    safe: Boolean,
    reason: String,
  ) {
    require(safe) {
      "Unsafe generated output directory '$directory': $reason. " +
        "Choose an empty generation-only directory, or inspect and remove only previously generated output."
    }
  }
}
