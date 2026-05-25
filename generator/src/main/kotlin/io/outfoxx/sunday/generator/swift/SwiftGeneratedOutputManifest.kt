/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.swift

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal object SwiftGeneratedOutputManifest {

  private const val MANIFEST_FILE_NAME = ".sunday-swift-generated-files"
  private const val GENERATED_HEADER_MARKER = "Generator: Sunday"

  fun clean(
    outputDirectory: Path,
    generatedPaths: Collection<Path>,
  ) {
    Files.createDirectories(outputDirectory)

    val generatedPathSet = generatedPaths.map { it.normalize() }.toSet()
    val generatedFileNames = generatedPathSet.map { it.fileName.name }.toSet()

    read(outputDirectory).forEach { relativePath ->
      outputDirectory.resolve(relativePath).deleteIfExists()
    }

    // Older generator versions did not write a manifest. Remove only generated Swift files
    // that would collide with the new layout by basename so mixed source directories stay intact.
    Files
      .walk(outputDirectory)
      .use { paths ->
        paths
          .filter { path -> path.isRegularFile() }
          .filter { path -> path.fileName.name.endsWith(".swift") }
          .filter { path -> path.fileName.name in generatedFileNames }
          .filter { path -> path.relativeTo(outputDirectory).normalize() !in generatedPathSet }
          .filter { path -> Files.readString(path).contains(GENERATED_HEADER_MARKER) }
          .forEach { path -> path.deleteIfExists() }
      }
  }

  fun write(
    outputDirectory: Path,
    generatedPaths: Collection<Path>,
  ) {
    Files.createDirectories(outputDirectory)

    val manifestContent =
      generatedPaths
        .map { it.normalize().joinToString("/") }
        .sorted()
        .joinToString(separator = "\n", postfix = "\n")

    Files.writeString(outputDirectory.resolve(MANIFEST_FILE_NAME), manifestContent)
  }

  private fun read(outputDirectory: Path): List<Path> {
    val manifestPath = outputDirectory.resolve(MANIFEST_FILE_NAME)
    if (!manifestPath.exists()) {
      return emptyList()
    }

    return Files
      .readAllLines(manifestPath)
      .filter { it.isNotBlank() }
      .map { outputDirectory.fileSystem.getPath(it) }
  }
}
