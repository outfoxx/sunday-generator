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
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.joinToCode
import java.math.BigDecimal
import java.math.BigInteger

/** Validates refined wire fields before decoding them into their inherited Swift storage types. */
internal object SwiftModelConstraints {
  fun fields(fields: List<GeneratedModelProperties.Field>): List<GeneratedModelProperties.Field> =
    fields.filter { field ->
      field.effective.allowedValues != null ||
        "multipleOf" in field.effective.validation ||
        field.inherited &&
        (
          field.effective.validation != field.declaration.validation ||
            field.effective.required != field.declaration.required ||
            field.effective.type.nullable != field.declaration.type.nullable
        )
    }

  fun decode(
    fields: List<GeneratedModelProperties.Field>,
    patchable: Boolean,
    properties: GeneratedModelProperties,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val needsNumericHelper = fields.any { "multipleOf" in it.effective.validation }
        if (needsNumericHelper) {
          // Limit the helper's name to validation, outside normal model type lookup and storage decoding.
          beginControlFlow("do", "")
          add(SwiftNumericValidation.helper)
        }
        fields.forEach { field ->
          val property = field.effective
          val key = field.storage.name.swiftIdentifierName
          val validation = property.validation
          if (property.required && !patchable) {
            beginControlFlow("if", "!container.contains(.%N)", key)
            addStatement(
              "throw %T.keyNotFound(CodingKeys.%N, .init(codingPath: decoder.codingPath, debugDescription: %S))",
              DECODING_ERROR,
              key,
              "Property '${field.wireName}' is required",
            )
            endControlFlow("if")
          }
          beginControlFlow("if", "container.contains(.%N)", key)
          beginControlFlow("if", "try container.decodeNil(forKey: .%N)", key)
          if (!patchable && (!property.type.nullable || property.allowedValues?.contains(null) == false)) {
            addStatement(
              "throw %T.dataCorruptedError(forKey: .%N, in: container, debugDescription: %S)",
              DECODING_ERROR,
              key,
              "Property '${field.wireName}' cannot be null",
            )
          }
          nextControlFlow("else")
          val values = property.allowedValues?.filterNotNull()
          val divisor = GeneratedNumericBounds.multipleOf(validation, "property '${field.wireName}'")
          val numericTarget =
            divisor?.let {
              properties.numericValidationTarget(field.storage.type, "property '${field.wireName}'")
            }
          val numericValue = if (numericTarget?.elements == true) "number" else "value"
          val numeric =
            values?.firstOrNull() is Number ||
              validation.keys.any {
                it in
                  setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf")
              }
          val collection = validation.keys.any { it in setOf("minItems", "maxItems", "uniqueItems") }
          val valueType =
            when {
              numericTarget?.elements == true ->
                CodeBlock.of(
                  if (numericTarget.nullable) "[_SundayValidationNumber?]" else "[_SundayValidationNumber]",
                )
              numericTarget != null -> CodeBlock.of("_SundayValidationNumber")
              numeric -> CodeBlock.of("%T", DECIMAL)
              values?.firstOrNull() is Boolean -> CodeBlock.of("Bool")
              collection -> CodeBlock.of("[%T]", ANY_VALUE)
              else -> CodeBlock.of("String")
            }
          val checks = mutableListOf<CodeBlock>()
          val numericChecks = mutableListOf<CodeBlock>()
          if (values != null) {
            val matches =
              values
                .map { value ->
                  when (value) {
                    is Number ->
                      CodeBlock.of(
                        "(try? container.decode(%T.self, forKey: .%N)) == " +
                          "%L",
                        DECIMAL,
                        key,
                        decimalLiteral(value.toString().toBigDecimal(), field.wireName),
                      )
                    is Boolean -> CodeBlock.of("(try? container.decode(Bool.self, forKey: .%N)) == %L", key, value)
                    else ->
                      CodeBlock.of(
                        "(try? container.decode(String.self, forKey: .%N)) == %S",
                        key,
                        value.toString(),
                      )
                  }
                }.joinToCode(" || ")
                .takeUnless { it.isEmpty() } ?: CodeBlock.of("false")
            beginControlFlow("if", "!(%L)", matches)
            addStatement(
              "throw %T.dataCorruptedError(forKey: .%N, in: container, debugDescription: %S)",
              DECODING_ERROR,
              key,
              "Invalid value for '${field.wireName}'",
            )
            endControlFlow("if")
          }
          if (field.inherited || numericTarget != null) {
            GeneratedNumericBounds.parse(validation, "property '${field.wireName}'").forEach { bound ->
              val literal = decimalLiteral(bound.value, field.wireName)
              numericChecks +=
                if (numericTarget != null) {
                  CodeBlock.of(
                    "%L %L _SundayValidationNumber(%S)",
                    numericValue,
                    bound.operator,
                    bound.value.toString(),
                  )
                } else {
                  CodeBlock.of("%L %L %L", numericValue, bound.operator, literal)
                }
            }
            validation.forEach { (constraint, bound) ->
              when (constraint) {
                "minLength" -> checks += CodeBlock.of("value.unicodeScalars.count >= %L", bound)
                "maxLength" -> checks += CodeBlock.of("value.unicodeScalars.count <= %L", bound)
                "pattern" -> checks += CodeBlock.of("value.range(of: %S, options: .regularExpression) != nil", bound)
                "minItems" -> checks += CodeBlock.of("value.count >= %L", bound)
                "maxItems" -> checks += CodeBlock.of("value.count <= %L", bound)
                "uniqueItems" -> if (bound == "true") checks += CodeBlock.of("Set(value).count == value.count")
              }
            }
          }
          divisor?.let {
            decimalLiteral(divisor, field.wireName)
            val normalized = divisor.stripTrailingZeros()
            numericChecks +=
              CodeBlock.of(
                "%L.isMultipleOf(digits: %L, exponent: %L)",
                numericValue,
                normalized.unscaledValue().toString().map { it.digitToInt() }.joinToString(
                  prefix = "[",
                  postfix = "]",
                ),
                -normalized.scale().toLong(),
              )
          }
          if (numericTarget?.elements == true) {
            val predicate = numericChecks.joinToCode(" && ")
            checks +=
              if (numericTarget.nullable) {
                CodeBlock.of("value.allSatisfy { element in element.map { number in %L } ?? true }", predicate)
              } else {
                CodeBlock.of("value.allSatisfy { number in %L }", predicate)
              }
          } else {
            checks += numericChecks
          }
          if (checks.isNotEmpty()) {
            addStatement("let value = try container.decode(%L.self, forKey: .%N)", valueType, key)
            checks.forEach { condition ->
              beginControlFlow("if", "!(%L)", condition)
              addStatement(
                "throw %T.dataCorruptedError(forKey: .%N, in: container, debugDescription: %S)",
                DECODING_ERROR,
                key,
                "Invalid value for '${field.wireName}'",
              )
              endControlFlow("if")
            }
          }
          endControlFlow("if")
          endControlFlow("if")
        }
        if (needsNumericHelper) endControlFlow("do")
      }.build()

  fun decimalLiteral(
    value: BigDecimal,
    context: String,
  ): CodeBlock {
    val normalized = value.stripTrailingZeros()
    val exponent = -normalized.scale().toLong()
    // Foundation Decimal has a signed eight-bit exponent and a 128-bit unsigned significand.
    val shift = (exponent - 127).coerceAtLeast(0)
    if (exponent < -128 ||
      shift > 38 ||
      normalized.unscaledValue().abs().multiply(BigInteger.TEN.pow(shift.toInt())) > maxSignificand
    ) {
      genError("Numeric literal '$value' for property '$context' cannot be represented by Swift Decimal")
    }
    return CodeBlock.of("%T(string: %S)!", DECIMAL, normalized.toPlainString())
  }

  private val maxSignificand = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
}
