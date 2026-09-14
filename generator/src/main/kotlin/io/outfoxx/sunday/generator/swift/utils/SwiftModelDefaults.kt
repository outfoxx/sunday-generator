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

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import java.math.BigDecimal
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalQueries
import java.util.Base64

/** Uses the same validated literal for a constructor default and an omitted-field decoder fallback. */
internal object SwiftModelDefaults {
  fun render(
    modelName: String,
    property: GeneratedModelProperty,
    properties: GeneratedModelProperties,
    scalar: (Any) -> CodeBlock,
  ): CodeBlock? {
    val value = property.defaultValue ?: return null
    val type = properties.declarationType(property.type)
    val validatedValue = validateDefault(modelName, property, properties, type)
    val format = type.format ?: type.name.takeIf { it in setOf("date", "time", "datetime", "datetime-only") }
    if (type.kind != GeneratedTypeRef.Kind.SCALAR) return validatedValue?.let(scalar)
    try {
      return when (swiftStringFormatTypeName(format)) {
        UUID -> {
          require(
            java.util.UUID
              .fromString(value)
              .toString()
              .equals(value, ignoreCase = true),
          )
          CodeBlock.of("%T(uuidString: %S)!", UUID, value)
        }
        URL -> {
          URI(value)
          CodeBlock.of("%T(string: %S)!", URL, value)
        }
        DATE -> {
          val instant =
            when (format?.lowercase()) {
              "date", "full-date" -> LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)
              "time", "partial-time" -> {
                val parsed = DateTimeFormatter.ISO_TIME.parse(value)
                LocalTime
                  .from(parsed)
                  .atDate(LocalDate.of(1970, 1, 1))
                  .toInstant(parsed.query(TemporalQueries.offset()) ?: ZoneOffset.UTC)
              }
              else -> {
                val parsed = DateTimeFormatter.ISO_DATE_TIME.parse(value)
                LocalDateTime.from(parsed).toInstant(parsed.query(TemporalQueries.offset()) ?: ZoneOffset.UTC)
              }
            }
          val seconds = BigDecimal.valueOf(instant.epochSecond).add(BigDecimal.valueOf(instant.nano.toLong(), 9))
          CodeBlock.of("%T(timeIntervalSince1970: %L)", DATE, seconds.stripTrailingZeros().toPlainString())
        }
        DATA -> {
          require(format.equals("byte", ignoreCase = true)) { "Only base64 byte defaults are supported for Data" }
          val encoded = Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value))
          CodeBlock.of("%T(base64Encoded: %S)!", DATA, encoded)
        }
        else -> validatedValue?.let(scalar)
      }
    } catch (error: IllegalArgumentException) {
      genError(
        "Invalid Swift default for property '$modelName.${property.serializationName ?: property.name}' ($format): ${error.message}",
      )
    } catch (error: java.time.DateTimeException) {
      genError(
        "Invalid Swift default for property '$modelName.${property.serializationName ?: property.name}' ($format): ${error.message}",
      )
    } catch (error: java.net.URISyntaxException) {
      genError(
        "Invalid Swift default for property '$modelName.${property.serializationName ?: property.name}' ($format): ${error.reason}",
      )
    }
  }

  private fun validateDefault(
    modelName: String,
    property: GeneratedModelProperty,
    properties: GeneratedModelProperties,
    type: GeneratedTypeRef,
  ): Any? {
    val literal = property.defaultValue ?: return null
    val context = "$modelName.${property.serializationName ?: property.name}"

    fun invalid(constraint: String): Nothing =
      genError("Invalid Swift default '$literal' for property '$context': violates $constraint")

    val value =
      properties.scalarDefault(property)
        ?: literal.takeIf {
          type.kind == GeneratedTypeRef.Kind.SCALAR && swiftStringFormatTypeName(type.format ?: type.name) != null
        }
    if (value == null) {
      if (type.kind == GeneratedTypeRef.Kind.SCALAR &&
        type.name in setOf("integer", "number", "boolean")
      ) {
        invalid(type.name)
      }
      return null
    }
    property.allowedValues?.let { values ->
      if (values.none { allowed ->
          if (allowed is Number && value is Number) {
            allowed.toString().toBigDecimal().compareTo(value.toString().toBigDecimal()) == 0
          } else {
            allowed == value
          }
        }
      ) {
        invalid("allowedValues")
      }
    }
    if (value is BigDecimal) {
      if (type.name == "integer" && value.stripTrailingZeros().scale() > 0) invalid("integer")
      GeneratedNumericBounds.parse(property.validation, "property '$context'").forEach { bound ->
        SwiftModelConstraints.decimalLiteral(bound.value, context)
        if (!bound.accepts(value)) invalid(bound.keyword)
      }
      property.validation["multipleOf"]?.let { raw ->
        val divisor = raw.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: invalid("multipleOf '$raw'")
        if (value.remainder(divisor).signum() != 0) invalid("multipleOf")
      }
    }
    if (value is String) {
      val length = value.codePointCount(0, value.length)
      property.validation["minLength"]?.let { if (length < it.toInt()) invalid("minLength") }
      property.validation["maxLength"]?.let { if (length > it.toInt()) invalid("maxLength") }
      property.validation["pattern"]?.let { pattern ->
        val regex =
          try {
            Regex(pattern)
          } catch (_: IllegalArgumentException) {
            invalid("pattern '$pattern'")
          }
        if (!regex.containsMatchIn(value)) invalid("pattern")
      }
    }
    return if (value is BigDecimal && type.name == "integer") {
      try {
        value.longValueExact()
      } catch (_: ArithmeticException) {
        invalid("signed 64-bit integer")
      }
    } else {
      value
    }
  }
}
