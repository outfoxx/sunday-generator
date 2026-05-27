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

  private val swiftBuildRoot: Path
  private val swiftCacheDir: Path
  private val resolvedDependencyBuild: Boolean

  init {

    val localPkgFile =
      Paths.get(SwiftCompiler::class.java.getResource("/swift/compile/local/Package.swift")!!.toURI())
    val localPkgDir = localPkgFile.parent

    val buildDir = localPkgFile.resolve("../../../../../../../build").normalize()

    val validationDir =
      if (Files.isDirectory(buildDir)) {
        val cacheDir = buildDir.resolve("validation/swift").toAbsolutePath()
        Files.createDirectories(cacheDir)
        cacheDir
      } else {
        workDir.resolve("validation").toAbsolutePath()
      }
    Files.createDirectories(validationDir)

    swiftBuildRoot = workDir.resolve("build").toAbsolutePath()
    swiftCacheDir = validationDir.resolve("package-cache")
    Files.createDirectories(swiftBuildRoot)
    Files.createDirectories(swiftCacheDir)

    val packageFile = workDir.resolve("Package.swift")
    Files.copy(localPkgFile, packageFile)

    val localSundaySwift = localPkgDir.resolve("../../../../../../../../sunday-swift").normalize()
    resolvedDependencyBuild = !Files.isDirectory(localSundaySwift)
    if (resolvedDependencyBuild) {
      Files.copy(localPkgDir.resolve("Package.resolved"), workDir.resolve("Package.resolved"))
      resolveDependencies()
    } else {
      Files.writeString(
        packageFile,
        Files
          .readString(packageFile)
          .replace(
            ".package(url: \"https://github.com/outfoxx/sunday-swift.git\", exact: \"2.0.0-beta.2\")",
            ".package(path: \"${localSundaySwift.toAbsolutePath()}\")",
          ),
      )
    }
  }

  private fun resolveDependencies() {
    val resolvePkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(
          command,
          "package",
          "--package-path",
          "$workDir",
          "--manifest-cache",
          "local",
          "--cache-path",
          "$swiftCacheDir",
          "--only-use-versions-from-resolved-file",
          "resolve",
        ).redirectErrorStream(true)
        .start()

    val result = resolvePkg.waitFor()
    if (result != 0) {
      error("Swift package resolution failed:\n${resolvePkg.inputStream.readAllBytes().decodeToString()}")
    }
  }

  override fun compile(): Pair<Int, String> {

    val buildCommand =
      buildList {
        add(command)
        add("build")
        add("--package-path")
        add("$workDir")
        add("--manifest-cache")
        add("local")
        add("--disable-index-store")
        if (resolvedDependencyBuild) {
          add("--only-use-versions-from-resolved-file")
        }
        add("--scratch-path")
        add("$swiftBuildRoot")
        add("--cache-path")
        add("$swiftCacheDir")
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
