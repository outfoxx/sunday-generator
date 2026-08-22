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

class LocalTypeScriptCompiler(
  private val command: String,
  workDir: Path,
) : TypeScriptCompiler(workDir) {

  val env = ShellProcess.loadExtraEnvironment()

  init {

    val buildPkg =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, "ci", "--ignore-scripts", "--no-audit", "--no-fund")
        .apply {
          environment().putAll(env)
        }.redirectErrorStream(true)
        .start()

    println("### Installing NPM packages")

    buildPkg.waitFor()

    println(buildPkg.inputStream.readAllBytes().decodeToString())
  }

  override fun compile(): Pair<Int, String> = executeCommand(tscCommand())

  override fun execute(modulePath: String): Pair<Int, String> {
    outputDir.toFile().deleteRecursively()
    val (compileResult, compileOutput) = executeCommand(tscCommand(outputDir.toString()))
    if (compileResult != 0) {
      return compileResult to compileOutput
    }

    val (executionResult, executionOutput) =
      executeCommand(listOf("node", outputDir.resolve("$modulePath.js").toString()))
    return executionResult to listOf(compileOutput, executionOutput).filter { it.isNotBlank() }.joinToString("\n")
  }

  private fun executeCommand(command: List<String>): Pair<Int, String> {
    val process =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command)
        .apply {
          environment().putAll(env)
        }.redirectErrorStream(true)
        .start()
    val result = process.waitFor()
    return result to process.inputStream.readAllBytes().decodeToString()
  }

  private fun tscCommand(outputDir: String? = null): List<String> =
    buildList {
      addAll(
        listOf(
          workDir.resolve("node_modules/.bin/tsc").toString(),
          "--project",
          "tsconfig.json",
          "--pretty",
          "false",
          "--incremental",
          "false",
          "--noErrorTruncation",
        ),
      )
      if (outputDir == null) {
        add("--noEmit")
      } else {
        addAll(listOf("--outDir", outputDir))
      }
    }

  override fun close() {
  }
}
