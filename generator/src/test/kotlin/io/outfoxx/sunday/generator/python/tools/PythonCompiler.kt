/*
 * Copyright 2026 Outfox, Inc.
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

@file:Suppress("DEPRECATION")

package io.outfoxx.sunday.generator.python.tools

import io.outfoxx.sunday.generator.utils.ShellProcess
import org.junit.jupiter.api.extension.ExtensionContext
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonCompiler(
  private val command: String,
  val workDir: Path,
) : Closeable,
  ExtensionContext.Store.CloseableResource {

  companion object {

    fun create(workDir: Path): PythonCompiler {
      val (uvExists, uvPath) = ShellProcess.execute("command", "-v", "uv")
      require(uvExists) { "Python generated source verification requires uv" }
      return PythonCompiler(uvPath.trim(), workDir)
    }
  }

  private val env = ShellProcess.loadExtraEnvironment()

  val srcDir: Path = workDir.resolve("src")

  init {
    val pkgDir = Paths.get(PythonCompiler::class.java.getResource("/python/compile")!!.toURI())
    Files.walk(pkgDir).forEach { source ->
      val target = workDir.resolve(pkgDir.relativize(source).toString())
      if (Files.isRegularFile(source)) {
        Files.createDirectories(target.parent)
        Files.copy(source, target)
      } else if (!target.exists()) {
        Files.createDirectories(target)
      }
    }

    val (result, output) = execute("sync")
    check(result == 0) { "Python verification environment setup failed:\n$output" }
  }

  fun verify(
    importModules: List<String> = listOf(),
    smokeCode: String? = null,
  ): Pair<Int, String> {
    val commands =
      listOf(
        listOf("run", "ruff", "format", "--check", "src"),
        listOf("run", "ruff", "check", "src"),
        listOf("run", "mypy", "src"),
        listOf(
          "run",
          "python",
          "-c",
          importSmokeCommand(importModules, smokeCode),
        ),
      )

    val output = StringBuilder()
    commands.forEach { args ->
      val (result, commandOutput) = execute(*args.toTypedArray())
      output.append("$ $command ${args.joinToString(" ")}\n")
      output.append(commandOutput)
      if (!commandOutput.endsWith("\n")) {
        output.append("\n")
      }
      if (result != 0) {
        return result to output.toString()
      }
    }

    return 0 to output.toString()
  }

  private fun importSmokeCommand(
    importModules: List<String>,
    smokeCode: String?,
  ): String =
    buildString {
      append("import compileall, importlib, pathlib, sys; ")
      append("sys.path.insert(0, 'src'); ")
      append("assert compileall.compile_dir('src', quiet=1); ")
      importModules.forEach { module ->
        append("importlib.import_module(${module.pythonStringLiteral()}); ")
      }
      if (smokeCode != null) {
        append("\n")
        append(smokeCode)
      }
    }

  private fun execute(vararg args: String): Pair<Int, String> {
    val process =
      ProcessBuilder()
        .directory(workDir.toFile())
        .command(command, *args)
        .apply {
          environment().putAll(env)
        }.redirectErrorStream(true)
        .start()

    val result = process.waitFor()
    return result to process.inputStream.readAllBytes().decodeToString()
  }

  override fun close() {
    workDir.toFile().deleteRecursively()
  }
}

private fun String.pythonStringLiteral(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
