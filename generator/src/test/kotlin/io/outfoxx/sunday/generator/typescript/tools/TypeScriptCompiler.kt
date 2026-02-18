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

package io.outfoxx.sunday.generator.typescript.tools

import io.outfoxx.sunday.generator.utils.ShellProcess
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class TypeScriptCompiler(
  val workDir: Path,
) : Closeable {

  companion object {

    fun create(workDir: Path): TypeScriptCompiler {

      fun useDocker(message: String) =
        run {
          println("### $message, using Docker")
          DockerTypeScriptCompiler(workDir)
        }

      fun useLocal(path: String) =
        run {
          println("### Local '$path' found at $path")
          LocalTypeScriptCompiler(path, workDir)
        }

      val forceDocker = System.getProperty("sunday.validation.force-docker", "false").toBoolean()
      if (forceDocker) {
        return useDocker("Forced Docker usage")
      }

      val (npmExists, npmPath) = ShellProcess.execute("command", "-v", "npm")
      return if (npmExists) {
        useLocal(npmPath.trim())
      } else {
        useDocker("Local 'npm' not found")
      }
    }
  }

  init {
    val pkgDir = Paths.get(DockerTypeScriptCompiler::class.java.getResource("/typescript/compile")!!.toURI())
    Files.walk(pkgDir).forEach { source ->
      val target = workDir.resolve(pkgDir.relativize(source))
      if (Files.isRegularFile(source)) {
        Files.copy(source, target)
      } else if (!Files.exists(target)) {
        Files.createDirectory(target)
      }
    }
  }

  val srcDir: Path = workDir.resolve("src")

  abstract fun compile(): Pair<Int, String>
}
