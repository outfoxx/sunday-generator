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
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.joinToCode
import java.math.BigDecimal
import java.math.BigInteger

/** Validates declared wire restrictions while preserving inherited Swift storage types. */
internal object SwiftModelConstraints {
  fun fields(
    fields: List<GeneratedModelProperties.Field>,
    properties: GeneratedModelProperties,
    patchable: Boolean,
  ): List<GeneratedModelProperties.Field> =
    fields.filter { field ->
      !patchable &&
        !field.effective.required &&
        !properties.acceptsNull(field.effective.type) ||
        field.effective.allowedValues != null ||
        field.effective.validation.isNotEmpty() ||
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
    codingKey: (
      GeneratedModelProperties.Field,
    ) -> CodeBlock = { CodeBlock.of(".%N", it.storage.name.swiftIdentifierName) },
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val needsNumericHelper =
          fields.any {
            "multipleOf" in it.effective.validation ||
              GeneratedNumericBounds.parse(it.effective.validation, "property '${it.wireName}'").isNotEmpty() &&
              properties.declarationType(it.storage.type).kind == GeneratedTypeRef.Kind.ARRAY
          }
        if (needsNumericHelper) {
          // Limit the helper's name to validation, outside normal model type lookup and storage decoding.
          beginControlFlow("do", "")
          add(SwiftNumericValidation.helper)
        }
        fields.forEach { field ->
          val property = field.effective
          val key = codingKey(field)
          val validation = property.validation
          if (property.required && !patchable) {
            beginControlFlow("if", "!container.contains(%L)", key)
            addStatement(
              "throw %T.keyNotFound(%L, .init(codingPath: decoder.codingPath, debugDescription: %S))",
              DECODING_ERROR,
              CodeBlock.of("CodingKeys.%N", field.storage.name.swiftIdentifierName),
              "Property '${field.wireName}' is required",
            )
            endControlFlow("if")
          }
          beginControlFlow("if", "container.contains(%L)", key)
          beginControlFlow("if", "try container.decodeNil(forKey: %L)", key)
          if (!patchable &&
            (!properties.acceptsNull(property.type) || property.allowedValues?.contains(null) == false)
          ) {
            addStatement(
              "throw %T.dataCorruptedError(forKey: %L, in: container, debugDescription: %S)",
              DECODING_ERROR,
              key,
              "Property '${field.wireName}' cannot be null",
            )
          }
          nextControlFlow("else")
          val values = property.allowedValues?.filterNotNull()
          val divisor = GeneratedNumericBounds.multipleOf(validation, "property '${field.wireName}'")
          val numericTarget =
            if (divisor != null ||
              properties.declarationType(field.storage.type).kind == GeneratedTypeRef.Kind.ARRAY &&
              GeneratedNumericBounds.parse(validation, "property '${field.wireName}'").isNotEmpty()
            ) {
              properties
                .numericValidationTarget(field.storage.type, "property '${field.wireName}'")
            } else {
              null
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
                        "(try? container.decode(%T.self, forKey: %L)) == " +
                          "%L",
                        DECIMAL,
                        key,
                        decimalLiteral(value.toString().toBigDecimal(), field.wireName),
                      )
                    is Boolean -> CodeBlock.of("(try? container.decode(Bool.self, forKey: %L)) == %L", key, value)
                    else ->
                      CodeBlock.of(
                        "(try? container.decode(String.self, forKey: %L)) == %S",
                        key,
                        value.toString(),
                      )
                  }
                }.joinToCode(" || ")
                .takeUnless { it.isEmpty() } ?: CodeBlock.of("false")
            beginControlFlow("if", "!(%L)", matches)
            addStatement(
              "throw %T.dataCorruptedError(forKey: %L, in: container, debugDescription: %S)",
              DECODING_ERROR,
              key,
              "Invalid value for '${field.wireName}'",
            )
            endControlFlow("if")
          }
          if (validation.isNotEmpty()) {
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
                "pattern" ->
                  properties.patterns(property).forEach { pattern ->
                    checks +=
                      CodeBlock.of("value.range(of: %L, options: .regularExpression) != nil", regexLiteral(pattern))
                  }
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
            addStatement("let value = try container.decode(%L.self, forKey: %L)", valueType, key)
            checks.forEach { condition ->
              beginControlFlow("if", "!(%L)", condition)
              addStatement(
                "throw %T.dataCorruptedError(forKey: %L, in: container, debugDescription: %S)",
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

  /** Whether a field has an active assertion requiring a throwing initializer. */
  fun hasValueConstraints(property: GeneratedModelProperty): Boolean =
    property.validation.keys.any {
      it in
        setOf("minLength", "maxLength", "pattern", "minItems", "maxItems", "multipleOf")
    } ||
      property.validation["uniqueItems"] == "true" ||
      GeneratedNumericBounds.parse(property.validation, "property '${property.name}'").isNotEmpty()

  /** Validates supplied memberwise initializer values without serializing a partially initialized model. */
  fun initializer(
    fields: List<GeneratedModelProperties.Field>,
    properties: GeneratedModelProperties,
    patchable: Boolean,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val constrained = fields.filter { hasValueConstraints(it.effective) }
        val numeric = setOf("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf")
        if (constrained.any { field ->
            field.effective.validation.keys
              .any { it in numeric }
          }
        ) {
          add(SwiftNumericValidation.helper)
        }
        constrained.forEach { field ->
          val validation = field.effective.validation
          val optional = !field.storage.required || properties.acceptsNull(field.storage.type)
          if (patchable) {
            beginControlFlow("if", "case .set(let value)? = %N", field.storage.name.swiftIdentifierName)
          } else if (optional) {
            beginControlFlow("if", "let value = %N", field.storage.name.swiftIdentifierName)
          } else {
            beginControlFlow("do", "")
            addStatement("let value = %N", field.storage.name.swiftIdentifierName)
          }
          val checks = mutableListOf<CodeBlock>()
          val declaration = properties.declarationType(field.storage.type)
          val format = declaration.format ?: declaration.name
          if (swiftStringFormatTypeName(format) == DATE &&
            validation.keys.any { it in setOf("minLength", "maxLength", "pattern") }
          ) {
            val options =
              when (format.lowercase()) {
                "date", "full-date" -> ".withFullDate, .withDashSeparatorInDate"
                "time", "partial-time" -> ".withTime, .withColonSeparatorInTime"
                "datetime-only", "date-time-only" ->
                  ".withFullDate, .withTime, .withDashSeparatorInDate, .withColonSeparatorInTime"
                else -> ".withInternetDateTime"
              }
            addStatement(
              "let formatter = %T()",
              DeclaredTypeName.typeName("Foundation.ISO8601DateFormatter"),
            )
            addStatement("formatter.formatOptions = [%L]", options)
            if (format.lowercase() !in setOf("date", "full-date")) {
              beginControlFlow("if", "value.timeIntervalSince1970.truncatingRemainder(dividingBy: 1) != 0")
              addStatement("formatter.formatOptions.insert(.withFractionalSeconds)")
              endControlFlow("if")
            }
            addStatement("let wireValue = formatter.string(from: value)")
          }
          val stringValue =
            if (properties.declarationModel(field.storage.type)?.kind ==
              GeneratedModel.Kind.ENUM
            ) {
              "value.rawValue"
            } else {
              when (swiftStringFormatTypeName(format)) {
                DATA -> "value.base64EncodedString()"
                UUID -> "value.uuidString"
                URL -> "value.absoluteString"
                DATE -> "wireValue"
                else -> "value"
              }
            }
          validation.forEach { (key, bound) ->
            when (key) {
              "minLength" -> checks += CodeBlock.of("%L.unicodeScalars.count >= %L", stringValue, bound)
              "maxLength" -> checks += CodeBlock.of("%L.unicodeScalars.count <= %L", stringValue, bound)
              "pattern" ->
                properties.patterns(field.effective).forEach { pattern ->
                  checks +=
                    CodeBlock.of(
                      "%L.range(of: %L, options: .regularExpression) != nil",
                      stringValue,
                      regexLiteral(pattern),
                    )
                }
              "minItems" -> checks += CodeBlock.of("value.count >= %L", bound)
              "maxItems" -> checks += CodeBlock.of("value.count <= %L", bound)
              "uniqueItems" -> if (bound == "true") checks += CodeBlock.of("Set(value).count == value.count")
            }
          }
          if (GeneratedNumericBounds.parse(validation, "property '${field.wireName}'").isNotEmpty() ||
            "multipleOf" in validation
          ) {
            val target = properties.numericValidationTarget(field.storage.type, "property '${field.wireName}'")
            val numericChecks = mutableListOf<CodeBlock>()
            GeneratedNumericBounds.parse(validation, "property '${field.wireName}'").forEach { bound ->
              numericChecks +=
                CodeBlock.of("number %L _SundayValidationNumber(%S)", bound.operator, bound.value.toString())
            }
            GeneratedNumericBounds.multipleOf(validation, "property '${field.wireName}'")?.let { divisor ->
              val normalized = divisor.stripTrailingZeros()
              numericChecks +=
                CodeBlock.of(
                  "number.isMultipleOf(digits: %L, exponent: %L)",
                  normalized
                    .unscaledValue()
                    .toString()
                    .map { it.digitToInt() }
                    .joinToString(prefix = "[", postfix = "]"),
                  -normalized.scale().toLong(),
                )
            }
            val predicate = numericChecks.joinToCode(" && ")
            if (target.elements) {
              checks +=
                CodeBlock.of(
                  if (target.nullable) {
                    "value.allSatisfy { element in element.map { value in Double(value).isFinite && " +
                      "{ let number = _SundayValidationNumber(String(describing: value)); return %L }() } ?? true }"
                  } else {
                    "value.allSatisfy { value in Double(value).isFinite && " +
                      "{ let number = _SundayValidationNumber(String(describing: value)); return %L }() }"
                  },
                  predicate,
                )
            } else {
              // Double conversion checks finiteness for both integral and floating point storage.
              checks += CodeBlock.of("Double(value).isFinite")
              checks +=
                CodeBlock.of(
                  "{ let number = _SundayValidationNumber(String(describing: value)); return %L }()",
                  predicate,
                )
            }
          }
          checks.forEach { condition ->
            beginControlFlow("if", "!(%L)", condition)
            addStatement(
              "throw %T.invalidValue(value, .init(codingPath: [CodingKeys.%N], debugDescription: %S))",
              ENCODING_ERROR,
              field.storage.name.swiftIdentifierName,
              "Invalid value for '${field.wireName}'",
            )
            endControlFlow("if")
          }
          endControlFlow(if (optional || patchable) "if" else "do")
        }
      }.build()

  // SwiftPoet's quoted-string formatter escapes dollar signs using Kotlin interpolation syntax.
  fun regexLiteral(pattern: String): CodeBlock =
    CodeBlock.of("%L", CodeBlock.of("%S", pattern).toString().replace("\${'$'}", "$"))

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
