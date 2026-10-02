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

import io.outfoxx.sunday.generator.utils.CompilerProcess
import io.outfoxx.sunday.generator.utils.ShellProcess
import java.nio.file.Path
import kotlin.io.path.exists

class LocalTypeScriptCompiler(
  private val command: String,
  workDir: Path,
) : TypeScriptCompiler(workDir) {

  val env = ShellProcess.loadExtraEnvironment()

  init {

    println("### Installing NPM packages")
    val (result, output) = executeCommand(listOf(command, "ci", "--ignore-scripts", "--no-audit", "--no-fund"))
    println(output)
    check(result == 0) { "TypeScript compiler dependencies could not be installed: $output" }

    System.getenv("SUNDAY_TYPESCRIPT_PATH")?.let { source ->
      val runtime = Path.of(source).toAbsolutePath().normalize()
      require(runtime.resolve("build/index.d.ts").exists()) {
        "SUNDAY_TYPESCRIPT_PATH must reference a built sunday-js checkout: $runtime"
      }
      val (runtimeResult, runtimeOutput) =
        executeCommand(
          listOf(
            command,
            "install",
            "--install-links",
            "--ignore-scripts",
            "--no-audit",
            "--no-fund",
            "--no-save",
            "--package-lock=false",
            runtime.toString(),
          ),
        )
      check(runtimeResult == 0) { "Sunday TypeScript runtime could not be installed: $runtimeOutput" }
    }
  }

  override fun compile(): Pair<Int, String> = executeCommand(tscCommand())

  override fun execute(modulePath: String): Pair<Int, String> {
    outputDir.toFile().deleteRecursively()
    val (compileResult, compileOutput) = executeCommand(tscCommand(outputDir.toString()))
    if (compileResult != 0) {
      return compileResult to compileOutput
    }

    val (executionResult, executionOutput) =
      // Bundler-style fixtures emit CommonJS; select Sunday's ESM export under Node's require(ESM) support.
      executeCommand(listOf("node", "--conditions=import", outputDir.resolve("$modulePath.js").toString()))
    return executionResult to listOf(compileOutput, executionOutput).filter { it.isNotBlank() }.joinToString("\n")
  }

  private fun executeCommand(command: List<String>): Pair<Int, String> {
    val setup = command.first() == this.command
    return CompilerProcess.execute(
      command,
      workDir,
      env,
      if (setup) CompilerProcess.dependencyTimeout else CompilerProcess.compilerTimeout,
    )
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
