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

class LocalSwiftCompiler(val command: String, workDir: Path) : SwiftCompiler(workDir) {

  val swiftBuildDir: Path

  init {

    val localPkgFile =
      Paths.get(SwiftCompiler::class.java.getResource("/swift/compile/local/Package.swift")!!.toURI())

    val buildDir = localPkgFile.resolve("../../../../../../../build").normalize()

    swiftBuildDir =
      if (Files.isDirectory(buildDir)) {
        val cacheDir = buildDir.resolve("validation/swift").toAbsolutePath()
        Files.createDirectories(cacheDir)
        cacheDir
      } else {
        workDir
      }

    Files.copy(localPkgFile, workDir.resolve("Package.swift"))
  }

  override fun compile(): Pair<Int, String> {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(
          command, "build",
          "--build-path", "${swiftBuildDir.resolve("build")}",
          "--cache-path", "${swiftBuildDir.resolve("cache")}",
        )
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .start()

    val result = buildPkg.waitFor()

    return result to buildPkg.errorStream.readAllBytes().decodeToString()
  }

  override fun close() {
  }
}
