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

import io.outfoxx.sunday.generator.utils.CompilerProcess
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

    val localSundaySwift = System.getenv("SUNDAY_SWIFT_PATH")?.let(Path::of)
    resolvedDependencyBuild = localSundaySwift == null
    if (localSundaySwift == null) {
      Files.copy(localPkgDir.resolve("Package.resolved"), workDir.resolve("Package.resolved"))
      resolveDependencies()
    } else {
      require(Files.isRegularFile(localSundaySwift.resolve("Package.swift"))) {
        "SUNDAY_SWIFT_PATH must reference a sunday-swift checkout: $localSundaySwift"
      }
      Files.writeString(
        packageFile,
        Files
          .readString(packageFile)
          .replace(
            ".package(url: \"https://github.com/outfoxx/sunday-swift.git\", exact: \"2.0.0-beta.8\")",
            ".package(path: \"${localSundaySwift.toAbsolutePath()}\")",
          ),
      )
    }
  }

  private fun resolveDependencies() {
    val (result, output) =
      CompilerProcess.execute(
        listOf(
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
        ),
        workDir,
        timeout = CompilerProcess.dependencyTimeout,
      )
    check(result == 0) { "Swift package resolution failed:\n$output" }
  }

  override fun compile(): Pair<Int, String> = execute("build")

  override fun test(): Pair<Int, String> = execute("test")

  private fun execute(action: String): Pair<Int, String> {
    val buildCommand =
      buildList {
        add(command)
        add(action)
        add("--jobs")
        add(System.getProperty("sunday.validation.swift.jobs", "1"))
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

    return CompilerProcess.execute(buildCommand, workDir)
  }

  override fun close() {
  }
}
