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
import java.nio.file.Path

class LocalTypeScriptCompiler(private val command: String, workDir: Path) : TypeScriptCompiler(workDir) {

  val env = ShellProcess.loadExtraEnvironment()

  init {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, "ci")
        .apply {
          environment().putAll(env)
        }
        .redirectErrorStream(true)
        .start()

    println("### Installing NPM packages")

    buildPkg.waitFor()

    println(buildPkg.inputStream.readAllBytes().decodeToString())
  }

  override fun compile(): Pair<Int, String> {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, "run", "build")
        .apply {
          environment().putAll(env)
        }
        .redirectErrorStream(true)
        .start()

    val result = buildPkg.waitFor()

    return result to buildPkg.inputStream.readAllBytes().decodeToString()
  }

  override fun close() {
  }
}
