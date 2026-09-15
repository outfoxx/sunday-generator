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
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import java.math.BigDecimal

/** Intersects inherited wire assertions without changing the selected storage declaration. */
internal object GeneratedPropertyConstraints {
  fun intersect(
    left: GeneratedModelProperty,
    right: GeneratedModelProperty,
    context: String,
  ): GeneratedModelProperty =
    right.copy(
      required = left.required || right.required,
      type = right.type.copy(nullable = left.type.nullable && right.type.nullable),
      defaultValue = right.defaultValue ?: left.defaultValue,
      validation = intersectValidation(left.validation, right.validation, context),
      allowedValues =
        when {
          left.allowedValues == null -> right.allowedValues
          right.allowedValues == null -> left.allowedValues
          else -> left.allowedValues.filter { candidate -> right.allowedValues.any { equivalent(candidate, it) } }
        },
    )

  private fun intersectValidation(
    left: Map<String, String>,
    right: Map<String, String>,
    context: String,
  ): Map<String, String> {
    val result = linkedMapOf<String, String>()
    val bounds = GeneratedNumericBounds.parse(left, context) + GeneratedNumericBounds.parse(right, context)
    for (lower in listOf(true, false)) {
      val strongest =
        bounds.filter { it.lower == lower }.reduceOrNull { a, b ->
          val comparison = a.value.compareTo(b.value) * if (lower) 1 else -1
          if (comparison > 0 || comparison == 0 && a.exclusive) a else b
        } ?: continue
      result[if (lower) "minimum" else "maximum"] = strongest.value.toPlainString()
      if (strongest.exclusive) result[if (lower) "exclusiveMinimum" else "exclusiveMaximum"] = "true"
    }
    for ((keyword, value) in left.entries + right.entries) {
      if (keyword in numericBounds) continue
      val previous = result[keyword]
      result[keyword] =
        when {
          previous == null || previous == value -> value
          keyword in lowerBounds -> maxOf(number(previous, context), number(value, context)).toPlainString()
          keyword in upperBounds -> minOf(number(previous, context), number(value, context)).toPlainString()
          keyword == "uniqueItems" -> (previous.toBooleanStrict() || value.toBooleanStrict()).toString()
          keyword == "multipleOf" -> {
            val first = GeneratedNumericBounds.multipleOf(mapOf(keyword to previous), context)!!
            val second = GeneratedNumericBounds.multipleOf(mapOf(keyword to value), context)!!
            val scale = maxOf(first.scale(), second.scale())
            val a = first.setScale(scale).unscaledValue()
            val b = second.setScale(scale).unscaledValue()
            BigDecimal(a.divide(a.gcd(b)).multiply(b), scale).toPlainString()
          }
          else -> genError("Unsupported inherited constraint intersection for $context: '$keyword'")
        }
    }
    return result
  }

  private fun equivalent(
    left: Any?,
    right: Any?,
  ): Boolean =
    if (left is Number && right is Number) {
      left.toString().toBigDecimal().compareTo(right.toString().toBigDecimal()) == 0
    } else {
      left == right
    }

  private fun number(
    value: String,
    context: String,
  ): BigDecimal = value.toBigDecimalOrNull() ?: genError("Invalid inherited constraint '$value' for $context")

  private val numericBounds = setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum")
  private val lowerBounds = setOf("minLength", "minItems", "minProperties")
  private val upperBounds = setOf("maxLength", "maxItems", "maxProperties")
}
