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

package io.outfoxx.sunday.generator.ir.emit

import io.outfoxx.sunday.generator.genError
import java.math.BigDecimal

/** Interprets numeric assertions without conflating boolean exclusivity modifiers with numeric bounds. */
internal object GeneratedNumericBounds {
  data class Bound(
    val keyword: String,
    val value: BigDecimal,
    val lower: Boolean,
    val exclusive: Boolean,
  ) {
    val operator: String get() = (if (lower) ">" else "<") + (if (exclusive) "" else "=")

    fun accepts(candidate: BigDecimal): Boolean {
      val comparison = candidate.compareTo(value) * if (lower) 1 else -1
      return if (exclusive) comparison > 0 else comparison >= 0
    }
  }

  fun parse(
    validation: Map<String, String>,
    context: String,
  ): List<Bound> =
    buildList {
      for ((bound, modifier) in listOf("minimum" to "exclusiveMinimum", "maximum" to "exclusiveMaximum")) {
        val exclusive = validation[modifier]
        if (exclusive == "true" && validation[bound] == null) {
          genError("Invalid $modifier for $context: true requires $bound")
        }
        validation[bound]?.let {
          add(
            Bound(bound, number(it, bound, context), bound == "minimum", exclusive == "true"),
          )
        }
        if (exclusive != null && exclusive != "true" && exclusive != "false") {
          add(Bound(modifier, number(exclusive, modifier, context), bound == "minimum", true))
        }
      }
    }

  fun multipleOf(
    validation: Map<String, String>,
    context: String,
  ): BigDecimal? =
    validation["multipleOf"]?.let { literal ->
      literal.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        ?: genError("Invalid multipleOf '$literal' for $context: expected a positive number")
    }

  private fun number(
    value: String,
    keyword: String,
    context: String,
  ): BigDecimal =
    value.toBigDecimalOrNull() ?: genError("Invalid $keyword '$value' for $context: expected a numeric bound")
}
