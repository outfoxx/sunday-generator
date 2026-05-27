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

/** A constrained Python source fragment that can register imports while rendering. */
class PythonCodeBlock private constructor(
  private val format: String,
  private val args: List<Any?>,
) {

  companion object {

    /** Creates a code block using `%T` for symbols, `%L` for literals, `%S` for strings, and `%C` for code blocks. */
    fun of(
      format: String,
      vararg args: Any?,
    ): PythonCodeBlock = PythonCodeBlock(format, args.toList())

    /** Joins code blocks with the given separator. */
    fun join(
      blocks: List<PythonCodeBlock>,
      separator: String = "\n",
    ): PythonCodeBlock = PythonCodeBlock(blocks.joinToString(separator) { "%C" }, blocks)
  }

  /** Renders this block using the given context. */
  fun render(context: PythonRenderContext): String {
    val output = StringBuilder()
    var argIndex = 0
    var index = 0

    while (index < format.length) {
      if (format[index] == '%' && index + 1 < format.length) {
        val arg = args.getOrNull(argIndex++) ?: error("Missing PythonCodeBlock argument for '$format'")
        when (format[index + 1]) {
          'T' -> output.append(context.reference(arg as PythonSymbol))
          'L' -> output.append(arg)
          'S' -> output.append((arg as String).pythonStringLiteral())
          'C' -> output.append((arg as PythonCodeBlock).render(context))
          '%' -> output.append('%')
          else -> error("Unsupported PythonCodeBlock placeholder '%${format[index + 1]}'")
        }
        index += 2
      } else {
        output.append(format[index])
        index++
      }
    }

    return output.toString()
  }
}

internal fun String.pythonStringLiteral(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
