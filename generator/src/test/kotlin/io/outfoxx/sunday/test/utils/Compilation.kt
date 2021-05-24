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

package io.outfoxx.sunday.test.utils

import com.diogonunes.jcolor.Ansi
import com.diogonunes.jcolor.AnsiFormat
import com.diogonunes.jcolor.Attribute.BLUE_TEXT
import com.diogonunes.jcolor.Attribute.BRIGHT_BLACK_TEXT
import com.diogonunes.jcolor.Attribute.CYAN_TEXT
import com.diogonunes.jcolor.Attribute.ITALIC
import com.diogonunes.jcolor.Attribute.SATURATED
import com.diogonunes.jcolor.Attribute.UNDERLINE
import com.diogonunes.jcolor.Attribute.WHITE_TEXT

object Compilation {

  val out = System.out
  val err = System.err

  val isCI = !System.getenv("CI").isNullOrBlank()

  fun printFailure(files: Map<String, String>, compilerOutput: String) {

    err.println("Compilation failed:\n")

    out.println(
      """
        
        
        ${colorize("COMPILER OUTPUT", headerNameFmt)}
        ${colorize(" ".repeat(60), headerMarkerFmt)}
        
      """.trimIndent()
    )

    out.println(colorize(compilerOutput, outputFmt))

    out.println(
      """
        
        
        ${colorize("CODE", headerNameFmt)}
        ${colorize(" ".repeat(60), headerMarkerFmt)}
        
      """.trimIndent()
    )

    files.forEach { (name, content) ->

      out.println("${colorize(name, fileNameFmt)}:")
      out.println(colorize(" ".repeat(60), fileNameMarkerFmt))

      out.println(numberLines(content))

      out.println(colorize(" ".repeat(60), fileNameMarkerFmt))
      out.println("\n")
    }
  }

  private val outputFmt = AnsiFormat(WHITE_TEXT(), SATURATED())

  private val headerNameFmt = AnsiFormat(BLUE_TEXT(), ITALIC())
  private val headerMarkerFmt = AnsiFormat(BLUE_TEXT(), UNDERLINE())

  private val fileNameFmt = AnsiFormat(CYAN_TEXT())
  private val fileNameMarkerFmt = AnsiFormat(CYAN_TEXT(), UNDERLINE())

  private val lineNumberFmt = AnsiFormat(BRIGHT_BLACK_TEXT(), SATURATED())

  private fun colorize(string: String, format: AnsiFormat): String {
    if (isCI) {
      return string
    }

    return Ansi.colorize(string, format)
  }

  private fun numberLines(string: String): String =
    string.split("\n").mapIndexed { index, line ->
      colorize("${(index + 1).toString().padStart(4)}|", lineNumberFmt) + " $line"
    }.joinToString("\n")
}
