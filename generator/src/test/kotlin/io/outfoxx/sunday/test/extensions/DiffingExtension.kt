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

package io.outfoxx.sunday.test.extensions

import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextAlign.CENTER
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.ColumnWidth
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.opentest4j.AssertionFailedError
import kotlin.math.max


class DiffingExtension : TestExecutionExceptionHandler {

  private val differ: DiffRowGenerator =
    DiffRowGenerator.create()
      .lineNormalizer { it }
      .build()
  private val terminal = Terminal()

  override fun handleTestExecutionException(context: ExtensionContext, throwable: Throwable) {
    if (throwable !is AssertionFailedError || !throwable.isStringMismatch()) {
      throw throwable
    }
    val diff =
      differ
        .generateDiffRows(
          throwable.expected.value.toString().split("\n"),
          throwable.actual.value.toString().split("\n")
        )
    val maxWidth = diff.maxOf { max(it.oldLine.length, it.newLine.length) }
    terminal.println(
      table {
        style = TextStyle(color = white, bgColor = black, dim = true)
        whitespace = Whitespace.PRE
        borderStyle = TextStyle(color = white, bgColor = black, dim = true)

        header {
          row {
            cell("Expected") { align = CENTER }
            cell("Actual") { align = CENTER }
          }
        }
        body {
          for ((idx, row) in diff.withIndex()) {
            val (changeStyle, prefix) =
              when (row.tag) {
                DiffRow.Tag.INSERT -> TextStyle(color = green) to "+"
                DiffRow.Tag.DELETE -> TextStyle(color = red) to row.oldLine.ifBlank { "-" }
                DiffRow.Tag.CHANGE -> TextStyle(color = cyan) to "•"
                null, DiffRow.Tag.EQUAL -> TextStyle(color = white, dim = true) to ""
              }
            row {
              val borders = if (idx == diff.size - 1) Borders.LEFT_RIGHT_BOTTOM else Borders.LEFT_RIGHT
              cell(row.oldLine.padEnd(maxWidth)) {
                cellBorders = borders
              }
              cell(row.newLine.ifEmpty { prefix }.padEnd(maxWidth)) {
                style = changeStyle
                cellBorders = borders
              }
            }
          }
//          row { cells("", "") { cellBorders = Borders.LEFT_RIGHT_BOTTOM } }
        }
      }
    )

    throw throwable
  }

  private fun AssertionFailedError.isStringMismatch(): Boolean =
    isExpectedDefined && isActualDefined &&
      CharSequence::class.java.isAssignableFrom(expected.type) && CharSequence::class.java.isAssignableFrom(actual.type)

}
