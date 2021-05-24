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

import java.nio.file.Path

class LocalTypeScriptCompiler(val command: String, workDir: Path) : TypeScriptCompiler(workDir) {

  init {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, "ci")
        .start()

    buildPkg.waitFor()

    println("### Installing NPM packages")

    println(buildPkg.inputStream.readAllBytes().decodeToString())
  }

  override fun compile(): Pair<Int, String> {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, "run", "build")
        .start()

    val result = buildPkg.waitFor()

    return result to buildPkg.inputStream.readAllBytes().decodeToString()
  }

  override fun close() {
  }
}
