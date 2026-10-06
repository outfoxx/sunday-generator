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
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class LocalSwiftCompiler(
  private val command: String,
  workDir: Path,
) : SwiftCompiler(workDir) {

  private val swiftBuildRoot: Path
  private val swiftCacheDir: Path
  private val resolvedDependencyBuild: Boolean
  private val prepared = System.getProperty("sunday.validation.swift.workspace") != null
  private val workspaceChannel =
    if (prepared) {
      FileChannel.open(workDir.resolve("workspace.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    } else {
      null
    }
  private val workspaceLock = workspaceChannel?.tryLock()

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
    if (prepared) {
      require(workspaceLock != null) { "Prepared Swift workspace is already in use: $workDir" }
      require(
        Files.isRegularFile(workDir.resolve("prepared.json")),
      ) { "Swift workspace must be prepared before testing" }
      listOf("Package.swift", "Package.resolved").forEach { name ->
        require(Files.readString(localPkgDir.resolve(name)) == Files.readString(workDir.resolve(name))) {
          "Prepared Swift workspace has incompatible $name"
        }
      }
    } else {
      Files.copy(localPkgFile, packageFile)
    }

    val localSundaySwift = System.getenv("SUNDAY_SWIFT_PATH")?.let(Path::of)
    require(!prepared || localSundaySwift == null) { "Prepared Swift workspaces cannot use SUNDAY_SWIFT_PATH" }
    resolvedDependencyBuild = localSundaySwift == null
    if (localSundaySwift == null) {
      if (!prepared) {
        Files.copy(localPkgDir.resolve("Package.resolved"), workDir.resolve("Package.resolved"))
        resolveDependencies()
      }
    } else {
      require(Files.isRegularFile(localSundaySwift.resolve("Package.swift"))) {
        "SUNDAY_SWIFT_PATH must reference a sunday-swift checkout: $localSundaySwift"
      }
      Files.writeString(
        packageFile,
        Files
          .readString(packageFile)
          .replace(
            ".package(url: \"https://github.com/outfoxx/sunday-swift.git\", exact: \"2.0.0-beta.13\")",
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
          "--scratch-path",
          "$swiftBuildRoot",
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
        if (prepared) {
          // SwiftPM has no strict offline flag; deny networking to the compiler and its children.
          addAll(listOf("/usr/bin/sandbox-exec", "-p", "(version 1)(allow default)(deny network*)"))
        }
        add(command)
        add(action)
        add("--jobs")
        add(System.getProperty("sunday.validation.swift.jobs", "1"))
        add("--package-path")
        add("$workDir")
        add("--manifest-cache")
        add("local")
        add("--disable-index-store")
        if (prepared) {
          // The enclosing network-denying sandbox replaces SwiftPM's incompatible nested sandbox.
          add("--disable-sandbox")
        }
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
    workspaceLock?.release()
    workspaceChannel?.close()
  }
}
