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

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.swiftpoet.BOOL
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.INOUT
import io.outfoxx.swiftpoet.Modifier.OVERRIDE
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.joinToCode

/** Emits canonical Swift validation and adapters without observing partially initialized objects. */
internal object SwiftModelValidation {
  val mode = DeclaredTypeName.typeName("Sunday.ModelMode")
  val context = DeclaredTypeName.typeName("Sunday.ModelValidationContext")
  val validatable = DeclaredTypeName.typeName("Sunday.ModelValidatable")
  private val validator = DeclaredTypeName.typeName("Sunday.ModelValidator")

  fun name(type: DeclaredTypeName): DeclaredTypeName =
    type.enclosingTypeName()?.nestedType("${type.simpleName}Validation")
      ?: DeclaredTypeName.typeName("${type.moduleName}.${type.simpleName}Validation")

  fun type(
    name: DeclaredTypeName,
    valueType: TypeName,
    body: CodeBlock,
  ): TypeSpec.Builder =
    TypeSpec
      .enumBuilder(name)
      .addModifiers(PUBLIC)
      .addDoc("Validates current values of the associated schema in request or response mode.\n")
      .addSuperType(validator)
      .addFunction(
        function(valueType)
          .addModifiers(STATIC)
          .addCode(body)
          .build(),
      )

  fun instance(
    validatorName: DeclaredTypeName,
    override: Boolean = false,
  ): FunctionSpec =
    function()
      .apply { if (override) addModifiers(OVERRIDE) }
      .addStatement("return %T.isValid(self, mode, context: &context)", validatorName)
      .build()

  fun function(
    valueType: TypeName? = null,
    valueName: String = "value",
    valueLabel: String = "_",
  ): FunctionSpec.Builder =
    FunctionSpec
      .builder("isValid")
      .addModifiers(PUBLIC)
      .addDoc("Checks the schema once using the caller's wire paths and traversal state.\n")
      .apply { valueType?.let { addParameter(valueLabel, valueName, it) } }
      .addParameter("_", "mode", mode)
      .addParameter(ParameterSpec.builder("context", context, INOUT).build())
      .returns(BOOL)

  fun fields(
    fields: List<GeneratedModelProperties.Field>,
    properties: GeneratedModelProperties,
    patchable: Boolean,
    additional: CodeBlock? = null,
    nested: (GeneratedModelProperty) -> CodeBlock?,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val activeFields =
          fields
            .map { field ->
              GeneratedModelProperties.Field(
                field.declaration,
                properties.containingConstraints(field.effective),
                field.inherited,
              )
            }.filter { field ->
              (!patchable && field.effective.required) ||
                !properties.acceptsNull(field.effective.type) ||
                field.effective.allowedValues != null ||
                SwiftModelConstraints.hasValueConstraints(field.effective) ||
                nested(field.storage) != null
            }
        if (activeFields.isEmpty() && additional == null) {
          addStatement("return true")
          return@apply
        }
        if (activeFields.any { field ->
            GeneratedNumericBounds.parse(field.effective.validation, field.wireName).isNotEmpty() ||
              "multipleOf" in field.effective.validation ||
              field.effective.allowedValues?.any { it is Number } == true
          }
        ) {
          add(SwiftNumericValidation.helper)
        }
        addStatement("var valid = true")
        additional?.let { add(it) }
        activeFields.forEachIndexed { index, field ->
          val property = field.effective
          val optional = !field.storage.required || field.storage.type.nullable
          val identifier = field.storage.name.swiftIdentifierName
          val nestedValidation = nested(field.storage)
          val presenceName = "field${index}Presence"
          val asserts = property.allowedValues != null || SwiftModelConstraints.hasValueConstraints(property)
          val checksValue = asserts || nestedValidation != null
          val acceptsNull = properties.acceptsNull(property.type) && property.allowedValues?.contains(null) != false
          if ((!patchable && property.required) || !acceptsNull) {
            addStatement(
              "let %N = context.presence(of: .property(%S), inferred: %L)",
              presenceName,
              field.wireName,
              if (patchable && field.storage.type.nullable) {
                CodeBlock.of(
                  "value.%N.map { operation -> %T.Presence in if case .delete = operation { return .null }; return .value } ?? .omitted",
                  identifier,
                  context,
                )
              } else if (patchable) {
                CodeBlock.of("value.%N == nil ? .omitted : .value", identifier)
              } else if (optional) {
                CodeBlock.of("value.%N == nil ? .%L : .value", identifier, if (property.required) "null" else "omitted")
              } else {
                CodeBlock.of(".value")
              },
            )
          }
          add("if !context.at(.property(%S), { context in\n", field.wireName)
          indent()
          addStatement("var fieldValid = true")
          if (property.required && !patchable) {
            addFailure(CodeBlock.of("%N != .omitted", presenceName), "required", "fieldValid")
          }
          if (!acceptsNull) {
            addFailure(CodeBlock.of("%N != .null", presenceName), "nullValue", "fieldValid")
          }
          if (checksValue) {
            if (patchable) {
              beginControlFlow("if", "case .set(let fieldValue)? = value.%N", identifier)
            } else if (optional) {
              beginControlFlow("if", "let fieldValue = value.%N", identifier)
            } else {
              beginControlFlow("do", "")
              addStatement("let fieldValue = value.%N", identifier)
            }
            // A nominal type owns its own assertions; containing-property restrictions apply to its raw value.
            val nominal = properties.declarationModel(property.type)?.nominal == true
            if (asserts) {
              if (properties.declarationType(property.type).name == "any") {
                addStatement("let scalarValue = context.dynamicValue ?? fieldValue")
              } else {
                addStatement("let scalarValue = fieldValue%L", if (nominal) ".rawValue" else "")
              }
              addAssertions(property, properties)
            }
            if (nestedValidation != null) {
              beginControlFlow("if", "!(%L)", nestedValidation)
              addStatement("fieldValid = false")
              addStatement("if !context.collectsDiagnostics { return false }")
              endControlFlow("if")
            }
            endControlFlow(if (optional || patchable) "if" else "do")
          }
          addStatement("return fieldValid")
          // The control-flow helper emits the closure's closing brace and the enclosing if body separately.
          unindent()
          add("}) {\n")
          indent()
          addStatement("valid = false")
          addStatement("if !context.collectsDiagnostics { return false }")
          endControlFlow("if")
        }
        addStatement("return valid")
      }.build()

  fun scalar(
    assertions: List<GeneratedModelProperty>,
    properties: GeneratedModelProperties,
    value: CodeBlock = CodeBlock.of("value"),
    nested: CodeBlock? = null,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val active = assertions.filter { it.allowedValues != null || SwiftModelConstraints.hasValueConstraints(it) }
        if (active.isEmpty()) {
          add("return %L\n", nested ?: CodeBlock.of("true"))
          return@apply
        }
        if (active.any { property ->
            GeneratedNumericBounds.parse(property.validation, property.name).isNotEmpty() ||
              "multipleOf" in property.validation ||
              property.allowedValues?.any { it is Number } == true
          }
        ) {
          add(SwiftNumericValidation.helper)
        }
        addStatement("var fieldValid = true")
        addStatement("let scalarValue = %L", value)
        active.forEach { addAssertions(it, properties) }
        if (nested != null) {
          beginControlFlow("if", "!(%L)", nested)
          addStatement("fieldValid = false")
          endControlFlow("if")
        }
        addStatement("return fieldValid")
      }.build()

  private fun CodeBlock.Builder.addAssertions(
    property: GeneratedModelProperty,
    properties: GeneratedModelProperties,
  ) {
    val validation = property.validation
    val declaration = properties.declarationType(property.type)
    val format = declaration.format ?: declaration.name
    val stringType = swiftStringFormatTypeName(format)
    val isEnum = properties.declarationModel(property.type)?.kind == GeneratedModel.Kind.ENUM
    val dynamic =
      declaration.kind == io.outfoxx.sunday.generator.ir.GeneratedTypeRef.Kind.SCALAR && declaration.name == "any"
    val stringValue =
      when {
        isEnum -> "scalarValue.rawValue"
        stringType == DATA -> "scalarValue.base64EncodedString()"
        stringType == UUID -> "scalarValue.uuidString"
        stringType == URL -> "scalarValue.absoluteString"
        stringType == DATE -> "wireValue"
        else -> "scalarValue"
      }
    if (stringType == DATE &&
      (property.allowedValues != null || validation.keys.any { it in setOf("minLength", "maxLength", "pattern") })
    ) {
      addStatement("let formatter = %T()", DeclaredTypeName.typeName("Foundation.ISO8601DateFormatter"))
      val options =
        when (format.lowercase()) {
          "date", "full-date" -> ".withFullDate, .withDashSeparatorInDate"
          "time", "partial-time" -> ".withTime, .withColonSeparatorInTime"
          "datetime-only", "date-time-only" ->
            ".withFullDate, .withTime, .withDashSeparatorInDate, .withColonSeparatorInTime"
          else -> ".withInternetDateTime"
        }
      addStatement("formatter.formatOptions = [%L]", options)
      if (format.lowercase() !in setOf("date", "full-date")) {
        beginControlFlow("if", "scalarValue.timeIntervalSince1970.truncatingRemainder(dividingBy: 1) != 0")
        addStatement("formatter.formatOptions.insert(.withFractionalSeconds)")
        endControlFlow("if")
      }
      addStatement("let wireValue = formatter.string(from: scalarValue)")
    }
    SwiftValueConstraints.scalarProjection(declaration, stringValue, isEnum)?.let { value ->
      beginControlFlow("if", "!(%L)", SwiftValueConstraints.check(property, value))
      addStatement("fieldValid = false")
      addStatement("if !context.collectsDiagnostics { return false }")
      endControlFlow("if")
      return
    }
    if (!dynamic && property.allowedValues?.any { it is Number } == true) {
      beginControlFlow("guard", "Double(scalarValue).isFinite else")
      addStatement("return context.reject(.nonFiniteNumber)")
      endControlFlow("guard")
    }
    property.allowedValues?.let { values ->
      val checks =
        values
          .map { allowed ->
            when (allowed) {
              is Number ->
                if (dynamic) {
                  CodeBlock.of(
                    "(context.number ?? _SundayValidationNumber(value: scalarValue)) == _SundayValidationNumber(%S)",
                    allowed.toString(),
                  )
                } else {
                  CodeBlock.of(
                    "(context.number ?? _SundayValidationNumber(String(describing: scalarValue))) == _SundayValidationNumber(%S)",
                    allowed.toString(),
                  )
                }
              is Boolean -> CodeBlock.of("scalarValue%L == %L", if (dynamic) ".boolValue" else "", allowed)
              null -> CodeBlock.of(if (dynamic) "scalarValue.isNull" else "false")
              else ->
                CodeBlock.of(
                  "%L == %S",
                  if (dynamic) "scalarValue.stringValue" else stringValue,
                  allowed.toString(),
                )
            }
          }.joinToCode(" || ")
          .takeUnless { it.isEmpty() } ?: CodeBlock.of("false")
      addFailure(CodeBlock.of("(%L)", checks), "allowedValue", "fieldValid")
    }
    validation.forEach { (key, bound) ->
      when (key) {
        "minLength" -> addFailure(CodeBlock.of("%L.unicodeScalars.count >= %L", stringValue, bound), key, "fieldValid")
        "maxLength" -> addFailure(CodeBlock.of("%L.unicodeScalars.count <= %L", stringValue, bound), key, "fieldValid")
        "pattern" ->
          listOfNotNull(property.validation["pattern"]).forEach { pattern ->
            addFailure(
              CodeBlock.of(
                "%L.range(of: %L, options: .regularExpression) != nil",
                stringValue,
                SwiftModelConstraints.regexLiteral(pattern),
              ),
              key,
              "fieldValid",
            )
          }
        "minItems" -> addFailure(CodeBlock.of("scalarValue.count >= %L", bound), key, "fieldValid")
        "maxItems" -> addFailure(CodeBlock.of("scalarValue.count <= %L", bound), key, "fieldValid")
        "uniqueItems" ->
          if (bound ==
            "true"
          ) {
            addFailure(CodeBlock.of("Set(scalarValue).count == scalarValue.count"), key, "fieldValid")
          }
      }
    }
    val bounds = GeneratedNumericBounds.parse(validation, property.name)
    bounds.forEach { SwiftModelConstraints.decimalLiteral(it.value, property.name) }
    val divisor = GeneratedNumericBounds.multipleOf(validation, property.name)
    if (bounds.isEmpty() && divisor == null) return
    val target = properties.numericValidationTarget(property.type, property.name)
    if (target.elements) {
      addStatement("let numbers: [_SundayValidationNumber?]")
      beginControlFlow("if", "let captured = context.numberElements")
      addStatement("numbers = captured")
      nextControlFlow("else")
      addStatement("var normalized: [_SundayValidationNumber?] = []")
      beginControlFlow("for", "(index, element) in scalarValue.enumerated()")
      if (target.nullable) {
        beginControlFlow("guard", "let numberValue = element else")
        addStatement("normalized.append(nil)")
        addStatement("continue")
        endControlFlow("guard")
      } else {
        addStatement("let numberValue = element")
      }
      beginControlFlow("guard", "Double(numberValue).isFinite else")
      addStatement("fieldValid = context.at(.index(index)) { $0.reject(.nonFiniteNumber) }")
      addStatement("if !context.collectsDiagnostics { return false }")
      addStatement("normalized.append(nil)")
      addStatement("continue")
      endControlFlow("guard")
      addStatement("normalized.append(_SundayValidationNumber(String(describing: numberValue)))")
      endControlFlow("for")
      addStatement("numbers = normalized")
      endControlFlow("if")
      beginControlFlow("for", "(index, element) in numbers.enumerated()")
      beginControlFlow("if", "let number = element")
      add("if !context.at(.index(index), { context in\n")
      indent()
      addStatement("var numberValid = true")
    } else {
      beginControlFlow("guard", "Double(scalarValue).isFinite else")
      addStatement("return context.reject(.nonFiniteNumber)")
      endControlFlow("guard")
      addStatement("let number = context.number ?? _SundayValidationNumber(String(describing: scalarValue))")
    }
    val result = if (target.elements) "numberValid" else "fieldValid"
    bounds.forEach { bound ->
      addFailure(
        CodeBlock.of("number %L _SundayValidationNumber(%S)", bound.operator, bound.value.toString()),
        if (bound.operator.startsWith(">")) "minimum" else "maximum",
        result,
      )
    }
    divisor?.let {
      val normalized = it.stripTrailingZeros()
      addFailure(
        CodeBlock.of(
          "number.isMultipleOf(digits: %L, exponent: %L)",
          normalized
            .unscaledValue()
            .toString()
            .map { digit ->
              digit.digitToInt()
            }.joinToString(prefix = "[", postfix = "]"),
          -normalized.scale().toLong(),
        ),
        "multipleOf",
        result,
      )
    }
    if (target.elements) {
      addStatement("return numberValid")
      unindent()
      add("}) {\n")
      indent()
      addStatement("fieldValid = false")
      addStatement("if !context.collectsDiagnostics { return false }")
      endControlFlow("if")
      endControlFlow("if")
      endControlFlow("for")
    }
  }

  private fun CodeBlock.Builder.addFailure(
    condition: CodeBlock,
    reason: String,
    result: String,
  ) {
    beginControlFlow("if", "!(%L)", condition)
    addStatement("%L = context.reject(.%L)", result, reason)
    addStatement("if !context.collectsDiagnostics { return false }")
    endControlFlow("if")
  }
}
