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

package io.outfoxx.sunday.generator.python

/** Builds a Python module while keeping imports tied to rendered symbol references. */
class PythonModuleBuilder(
  private val path: String,
) {

  private val blocks = mutableListOf<PythonCodeBlock>()
  private val exports = linkedSetOf<String>()

  /** Adds a top-level code block. */
  fun addCode(block: PythonCodeBlock): PythonModuleBuilder =
    apply {
      blocks.add(block)
    }

  /** Adds a public export name to the module `__all__`. */
  fun addExport(name: String): PythonModuleBuilder =
    apply {
      exports.add(name)
    }

  /** Renders the module. */
  fun build(): PythonModule {
    val imports = PythonImportSet()
    val context = PythonRenderContext(imports)
    val body =
      buildString {
        if (exports.isNotEmpty()) {
          appendExports()
          append("\n\n\n")
        }
        blocks.forEachIndexed { index, block ->
          if (index > 0) {
            append("\n\n")
          }
          append(block.render(context).trimEnd())
          append("\n")
        }
      }.trimEnd()

    val source =
      buildString {
        append("from __future__ import annotations\n")
        if (!imports.isEmpty()) {
          append("\n")
          append(imports.render())
          append("\n")
        }
        if (body.isNotEmpty()) {
          append("\n")
          append(body)
          append("\n")
        }
      }

    return PythonModule(path, source)
  }

  private fun StringBuilder.appendExports() {
    val inline = "__all__ = [${exports.joinToString(", ") { it.pythonStringLiteral() }}]"
    if (inline.length <= 120) {
      append(inline)
      return
    }

    append("__all__ = [\n")
    exports.forEach { export ->
      append("    ")
      append(export.pythonStringLiteral())
      append(",\n")
    }
    append("]")
  }
}
