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

import io.outfoxx.sunday.generator.utils.ShellProcess
import java.io.Closeable
import java.nio.file.Path
import kotlin.text.RegexOption.IGNORE_CASE

abstract class SwiftCompiler(
  val workDir: Path,
) : Closeable {

  companion object {

    fun create(workDir: Path): SwiftCompiler {

      fun useDocker(message: String) =
        run {
          println("### $message, using Docker")
          DockerSwiftCompiler(workDir)
        }

      fun useLocal(command: String) =
        run {
          println("### Using Local 'swift' with command '$command'")
          LocalSwiftCompiler(command, workDir)
        }

      val forceDocker = System.getProperty("sunday.validation.force-docker", "false").toBoolean()
      if (forceDocker) {
        return useDocker("Forced Docker usage")
      }
      val isMac = System.getProperty("os.name")?.matches("""^mac ?os.*""".toRegex(IGNORE_CASE)) ?: false
      if (!isMac) {
        return useDocker("Not running on macOS")
      }

      val (swiftExists, swiftPath) = ShellProcess.execute("command", "-v", "swift")
      return if (swiftExists) {
        useLocal(swiftPath.trim())
      } else {
        useDocker("Local 'swift' not found")
      }
    }
  }

  val srcDir: Path = workDir.resolve("src")

  abstract fun compile(): Pair<Int, String>
}
