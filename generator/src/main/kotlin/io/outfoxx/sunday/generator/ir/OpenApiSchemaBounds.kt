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

package io.outfoxx.sunday.generator.ir

import io.outfoxx.sunday.generator.genError
import java.math.BigDecimal

/** Interprets each operand's dialect before bounds participate in schema composition. */
internal object OpenApiSchemaBounds {
  fun normalize(
    fields: Map<String, Any?>,
    origin: Map<*, *>,
  ): Map<String, Any?> {
    if ((origin as? OpenApiSchema)?.usesBooleanExclusiveBounds != true) return fields
    return fields.toMutableMap().apply {
      for ((inclusive, exclusive) in pairs) {
        if (!containsKey(exclusive)) continue
        val flag = this[exclusive] as? Boolean ?: fail(origin, "OpenAPI 3.0 '$exclusive' must be a boolean")
        if (flag) {
          this[exclusive] = this[inclusive] ?: fail(origin, "OpenAPI 3.0 '$exclusive' requires '$inclusive'")
        } else {
          remove(exclusive)
        }
      }
    }
  }

  fun validate(
    fields: MutableMap<String, Any?>,
    origin: Map<*, *>,
  ) {
    val lower = bound(fields, "minimum", "exclusiveMinimum", origin, true)
    val upper = bound(fields, "maximum", "exclusiveMaximum", origin, false)
    if (lower != null &&
      upper != null &&
      (lower.value > upper.value || (lower.value.compareTo(upper.value) == 0 && (lower.exclusive || upper.exclusive)))
    ) {
      fail(origin, "Incompatible OpenAPI schema bounds: lower bound exceeds or excludes upper bound")
    }
  }

  private fun bound(
    fields: MutableMap<String, Any?>,
    inclusive: String,
    exclusive: String,
    origin: Map<*, *>,
    lower: Boolean,
  ): Bound? {
    fun read(key: String): BigDecimal? =
      if (fields.containsKey(key)) {
        fields[key]?.toString()?.toBigDecimalOrNull()
          ?: fail(origin, "Invalid numeric OpenAPI schema constraint '${fields[key]}'")
      } else {
        null
      }
    val closed = read(inclusive)
    val open = read(exclusive)
    if (open == null) return closed?.let { Bound(it, false) }
    if (closed != null && (if (lower) closed > open else closed < open)) {
      fields.remove(exclusive)
      return Bound(closed, false)
    }
    // Retain an explicit inclusive bound for the existing IR validation projection.
    return Bound(open, true)
  }

  private fun fail(
    origin: Map<*, *>,
    message: String,
  ): Nothing = (origin as? OpenApiSchema)?.error(message) ?: genError(message)

  private data class Bound(
    val value: BigDecimal,
    val exclusive: Boolean,
  )

  private val pairs = listOf("minimum" to "exclusiveMinimum", "maximum" to "exclusiveMaximum")
}
