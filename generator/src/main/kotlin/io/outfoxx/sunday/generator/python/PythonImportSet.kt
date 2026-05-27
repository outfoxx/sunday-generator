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

/** Collects Python imports while generated definitions render. */
class PythonImportSet {

  private val symbols = linkedSetOf<PythonSymbol>()

  /** Registers a symbol import and returns the expression used to reference it. */
  fun reference(symbol: PythonSymbol): String {
    symbols.add(symbol)
    return symbol.reference
  }

  /** Renders imports using deterministic stdlib, third-party, and local grouping. */
  fun render(): String =
    symbols
      .groupBy { it.module }
      .toSortedMap()
      .map { (module, symbols) ->
        val imports = symbols.sortedWith(compareBy<PythonSymbol> { it.name }.thenBy { it.alias ?: "" })
        val inlineImports = imports.joinToString(", ") { symbol -> symbol.renderImportName() }
        val inline = "from $module import $inlineImports"
        if (inline.length <= 120) {
          inline
        } else {
          buildString {
            append("from $module import (\n")
            imports.forEach { symbol ->
              append("    ")
              append(symbol.renderImportName())
              append(",\n")
            }
            append(")")
          }
        }
      }.joinToString("\n")

  /** Returns true when no imports were registered. */
  fun isEmpty(): Boolean = symbols.isEmpty()

  private fun PythonSymbol.renderImportName(): String =
    if (alias == null) {
      name
    } else {
      "$name as $alias"
    }
}
