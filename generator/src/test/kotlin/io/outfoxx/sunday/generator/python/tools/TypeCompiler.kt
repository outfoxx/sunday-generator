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

package io.outfoxx.sunday.generator.python.tools

import io.outfoxx.sunday.generator.python.PythonModule
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.utils.Compilation
import java.nio.file.Files

fun compileModules(
  compiler: PythonCompiler,
  modules: List<PythonModule>,
  importModules: List<String> = listOf(),
  smokeCode: String? = null,
): Boolean {
  try {
    CompiledGeneratedSources.beginCompile()

    modules.forEach { module ->
      val path = compiler.srcDir.resolve(module.path).normalize()
      Files.createDirectories(path.parent)
      Files.writeString(path, module.source)
    }

    val (result, output) = compiler.verify(importModules, smokeCode)
    val files = modules.associate { it.path to it.source }

    if (result != 0) {
      Compilation.printFailure(files, output)
    } else {
      modules.forEach { module ->
        CompiledGeneratedSources.record(GeneratedCodeLanguage.Python, module.path, module.source)
      }
    }

    return result == 0
  } finally {
    compiler.srcDir.toFile().deleteRecursively()
  }
}
