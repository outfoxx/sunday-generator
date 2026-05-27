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

package io.outfoxx.sunday.generator.swift.tools

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class LocalSwiftCompiler(
  private val command: String,
  workDir: Path,
) : SwiftCompiler(workDir) {

  private val swiftBuildDir: Path
  private val resolvedDependencyBuild: Boolean

  init {

    val localPkgFile =
      Paths.get(SwiftCompiler::class.java.getResource("/swift/compile/local/Package.swift")!!.toURI())
    val localPkgDir = localPkgFile.parent

    val buildDir = localPkgFile.resolve("../../../../../../../build").normalize()

    swiftBuildDir =
      if (Files.isDirectory(buildDir) && System.getenv("CI").isNullOrBlank()) {
        val cacheDir = buildDir.resolve("validation/swift").toAbsolutePath()
        Files.createDirectories(cacheDir)
        cacheDir
      } else {
        workDir
      }

    val packageFile = workDir.resolve("Package.swift")
    Files.copy(localPkgFile, packageFile)

    val localSundaySwift = localPkgDir.resolve("../../../../../../../../sunday-swift").normalize()
    resolvedDependencyBuild = !Files.isDirectory(localSundaySwift)
    if (resolvedDependencyBuild) {
      Files.copy(localPkgDir.resolve("Package.resolved"), workDir.resolve("Package.resolved"))
    } else {
      Files.writeString(
        packageFile,
        Files
          .readString(packageFile)
          .replace(
            ".package(url: \"https://github.com/outfoxx/sunday-swift.git\", exact: \"2.0.0-beta.1\")",
            ".package(path: \"${localSundaySwift.toAbsolutePath()}\")",
          ),
      )
    }
  }

  override fun compile(): Pair<Int, String> {

    val buildCommand =
      buildList {
        add(command)
        add("build")
        add("--manifest-cache")
        add("local")
        add("--disable-index-store")
        if (resolvedDependencyBuild) {
          add("--disable-automatic-resolution")
        }
        add("--build-path")
        add("${swiftBuildDir.resolve("build")}")
        add("--cache-path")
        add("${swiftBuildDir.resolve("cache")}")
      }

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(buildCommand)
        .redirectErrorStream(true)
        .start()

    val result = buildPkg.waitFor()

    return result to buildPkg.inputStream.readAllBytes().decodeToString()
  }

  override fun close() {
  }
}
