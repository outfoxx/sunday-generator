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

import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.joinToCode

/** Projects immutable schema metadata into the shared normalized-value assertion implementation. */
internal object SwiftValueConstraints {
  val valueType = DeclaredTypeName.typeName("Sunday.ModelValidationValue")
  private val constraintsType = DeclaredTypeName.typeName("Sunday.ModelValueConstraints")

  fun scalarProjection(
    declaration: GeneratedTypeRef,
    stringValue: String,
    enum: Boolean,
  ): CodeBlock? =
    when {
      enum || declaration.kind == GeneratedTypeRef.Kind.SCALAR && declaration.name == "string" ->
        CodeBlock.of("%T.string(%L)", valueType, stringValue)
      declaration.kind != GeneratedTypeRef.Kind.SCALAR -> null
      declaration.name in setOf("integer", "number") ->
        CodeBlock.of(
          "context.number.map { %T.number($0) } ?? %T.number(String(describing: scalarValue))",
          valueType,
          valueType,
        )
      declaration.name == "boolean" -> CodeBlock.of("%T.boolean(scalarValue)", valueType)
      declaration.name == "any" -> CodeBlock.of("%T(scalarValue)", valueType)
      else -> null
    }

  fun check(
    property: GeneratedModelProperty,
    value: CodeBlock,
    elementType: GeneratedTypeRef? = null,
  ): CodeBlock = CodeBlock.of("%L.isValid(%L, context: &context)", metadata(property, elementType), value)

  private fun metadata(
    property: GeneratedModelProperty,
    elementType: GeneratedTypeRef? = null,
  ): CodeBlock {
    val elementKeys =
      setOf(
        "minLength",
        "maxLength",
        "pattern",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "multipleOf",
      )
    if (elementType != null && property.validation.keys.any { it in elementKeys }) {
      val elementRules = property.validation.filterKeys { it in elementKeys }
      return CodeBlock.of(
        "%L.appending(.elements(%L, nullable: %L))",
        metadata(property.copy(validation = property.validation - elementKeys)),
        metadata(property.copy(type = elementType, validation = elementRules, allowedValues = null)),
        elementType.nullable,
      )
    }
    val rules = mutableListOf<CodeBlock>()
    property.allowedValues?.let { values ->
      rules +=
        CodeBlock.of(
          ".allowedValues([%L])",
          values
            .map { value ->
              when (value) {
                null -> CodeBlock.of(".null")
                is Boolean -> CodeBlock.of(".boolean(%L)", value)
                is Number -> CodeBlock.of(".number(%S)", value.toString())
                else -> CodeBlock.of(".string(%S)", value.toString())
              }
            }.joinToCode(", "),
        )
    }
    property.validation.forEach { (key, bound) ->
      when (key) {
        "minLength", "maxLength", "minItems", "maxItems" -> rules += CodeBlock.of(".%L(%L)", key, bound)
        "pattern" -> rules += CodeBlock.of(".pattern(%L)", SwiftModelConstraints.regexLiteral(bound))
        "uniqueItems" -> if (bound == "true") rules += CodeBlock.of(".uniqueItems")
      }
    }
    GeneratedNumericBounds.parse(property.validation, property.name).forEach { bound ->
      SwiftModelConstraints.decimalLiteral(bound.value, property.name)
      rules +=
        CodeBlock.of(
          ".%L(.init(%S), exclusive: %L)",
          if (bound.operator.startsWith(">")) "minimum" else "maximum",
          bound.value.toString(),
          bound.operator.length == 1,
        )
    }
    GeneratedNumericBounds.multipleOf(property.validation, property.name)?.let { divisor ->
      val normalized = divisor.stripTrailingZeros()
      rules +=
        CodeBlock.of(
          ".multipleOf(digits: [%L], exponent: %L)",
          normalized
            .unscaledValue()
            .toString()
            .map { it.digitToInt() }
            .joinToString(", "),
          -normalized.scale().toLong(),
        )
    }
    return CodeBlock.of("%T([%L])", constraintsType, rules.joinToCode(", "))
  }
}
