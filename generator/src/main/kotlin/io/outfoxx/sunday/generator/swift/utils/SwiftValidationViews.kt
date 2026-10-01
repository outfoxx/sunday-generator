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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.joinToCode

/** Adapts typed storage to the same lazy field view accepted by dynamic-property validators. */
internal class SwiftValidationViews(
  private val properties: GeneratedModelProperties,
  private val modelFor: (GeneratedTypeRef) -> GeneratedModel?,
  private val modelName: (GeneratedModel) -> DeclaredTypeName,
  private val typeName: (GeneratedTypeRef) -> TypeName,
  private val isFreeform: (GeneratedModel) -> Boolean,
  private val storedModelName: (GeneratedModel) -> DeclaredTypeName = modelName,
) {
  val valueType = SwiftValueConstraints.valueType
  private val objectSchema = DeclaredTypeName.typeName("Sunday.ModelObjectValidation")

  fun project(
    reference: GeneratedTypeRef,
    value: CodeBlock,
  ): CodeBlock {
    if (reference.nullable) {
      return CodeBlock.of(
        "%L.map { value in %L } ?? .null",
        value,
        project(reference.copy(nullable = false), CodeBlock.of("value")),
      )
    }
    if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
      val model = requireNotNull(modelFor(reference))
      return if (isFreeform(model)) {
        CodeBlock.of("%T.object { %L.mapValues { %T($0) } }", valueType, value, valueType)
      } else {
        CodeBlock.of("%T.view(%L)", SwiftModelValidation.name(storedModelName(model)), value)
      }
    }
    return when (reference.kind) {
      GeneratedTypeRef.Kind.ARRAY ->
        CodeBlock.of(
          "%T.array { %L.map { element in %L } }",
          valueType,
          value,
          project(reference.arguments.single(), CodeBlock.of("element")),
        )
      GeneratedTypeRef.Kind.MAP ->
        CodeBlock.of(
          "%T.object { %L.mapValues { element in %L } }",
          valueType,
          value,
          project(reference.arguments.single(), CodeBlock.of("element")),
        )
      GeneratedTypeRef.Kind.SCALAR ->
        when (reference.name) {
          "string", "date", "time", "datetime", "datetime-only", "file" -> stringProjection(reference, value)
          "integer", "number", "int32", "int64", "long", "float", "double" ->
            CodeBlock.of(
              "%T.number(String(describing: %L))",
              valueType,
              value,
            )
          "boolean" -> CodeBlock.of("%T.boolean(%L)", valueType, value)
          "nil" -> CodeBlock.of("%T.null", valueType)
          "object" -> CodeBlock.of("%T.object { %L.mapValues { %T($0) } }", valueType, value, valueType)
          else -> CodeBlock.of("%T(%L)", valueType, value)
        }
      GeneratedTypeRef.Kind.UNION -> CodeBlock.of("%T(%L)", valueType, value)
    }
  }

  fun objectView(
    model: GeneratedModel,
    extensions: String?,
    referenceType: Boolean,
    unknown: Boolean = false,
    validatorName: DeclaredTypeName = SwiftModelValidation.name(modelName(model)),
    patchField: (GeneratedModelProperty) -> Boolean = { model.patchable },
    storageProperty: (GeneratedModelProperty) -> GeneratedModelProperty = { it },
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        add(
          "return %T.object(%L%Lschemas: [%L], validate: { mode, context in self.isValid(mode, context: &context) }) {\n",
          valueType,
          if (referenceType) "identity: self, " else "",
          if (unknown) "isUnknown: true, " else "",
          validationSchemas(model, validatorName).map { CodeBlock.of("%T.self", it) }.joinToCode(", "),
        )
        indent()
        val fields =
          properties.fields(model).map { field ->
            val property = storageProperty(field.storage)
            val value = CodeBlock.of("self.%N", property.name.swiftIdentifierName)
            val projected =
              if (patchField(property)) {
                CodeBlock.of(
                  "%L.map { operation in if case .set(let value) = operation { return %L }; return .null } ?? .omitted",
                  value,
                  project(property.type.copy(nullable = false), CodeBlock.of("value")),
                )
              } else if (!property.required && !property.type.nullable) {
                CodeBlock.of("%L.map { value in %L } ?? .omitted", value, project(property.type, CodeBlock.of("value")))
              } else if (!property.required) {
                CodeBlock.of(
                  "%L.map { value in %L } ?? .omitted",
                  value,
                  project(property.type.copy(nullable = false), CodeBlock.of("value")),
                )
              } else {
                project(property.type, value)
              }
            CodeBlock.of("%S: %L", field.wireName, projected)
          }
        addStatement(
          "let fields: [String: %T] = %L",
          valueType,
          if (fields.isEmpty()) CodeBlock.of("[:]") else fields.joinToCode(",\n", "[", "]"),
        )
        if (extensions == null) {
          addStatement("return fields")
        } else {
          addStatement(
            "return self.%N.mapValues { %T($0) }.merging(fields) { _, declared in declared }",
            extensions,
            valueType,
          )
        }
        unindent().add("}\n")
      }.build()

  private fun validationSchemas(
    model: GeneratedModel,
    validatorName: DeclaredTypeName,
  ): Set<DeclaredTypeName> =
    buildSet {
      add(validatorName)
      val visited = mutableSetOf<GeneratedModel>()

      fun addParents(current: GeneratedModel) {
        if (!visited.add(current)) return
        current.inherits.mapNotNull(modelFor).forEach { parent ->
          add(SwiftModelValidation.name(modelName(parent)))
          addParents(parent)
        }
      }
      addParents(model)
    }

  fun objectValidation(
    model: GeneratedModel,
    fieldCheck: (GeneratedModelProperty) -> CodeBlock? = { null },
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        if (properties.fields(model).any { it.effective.externalDiscriminator != null }) {
          addStatement("let objectFields = value.fields ?? [:]")
        }
        add("return %T(fields: [\n", objectSchema).indent()
        properties.fields(model).forEach { field ->
          val property = properties.containingConstraints(field.effective)
          add(
            ".init(%S, required: %L) { value, mode, context in\n",
            field.wireName,
            property.required && !model.patchable,
          ).indent()
          add(validation(property, fieldCheck(property)))
          unindent().add("},\n")
        }
        unindent().add("], patterns: [\n").indent()
        properties.patternProperties(model).forEach { pattern ->
          add(".init(%L) { value, mode, context in\n", SwiftModelConstraints.regexLiteral(pattern.pattern)).indent()
          add(
            validation(
              GeneratedModelProperty(
                "value",
                pattern.type,
                validation = pattern.validation,
                allowedValues = pattern.allowedValues,
              ),
            ),
          )
          unindent().add("},\n")
        }
        unindent().add("], closed: %L", properties.isClosed(model))
        val additional = properties.additionalProperties(model)
        if (additional.isEmpty()) {
          add(")")
        } else {
          add(", additional: { value, mode, context in\n").indent()
          if (additional.size > 1) addStatement("var valid = true")
          additional.forEach { declaration ->
            if (additional.size > 1) add("if !({ () -> Bool in\n").indent()
            add(
              validation(
                GeneratedModelProperty(
                  "value",
                  requireNotNull(declaration.type),
                  validation = declaration.validation,
                  allowedValues = declaration.allowedValues,
                ),
              ),
            )
            if (additional.size > 1) {
              unindent().add("}()) {\n").indent()
              addStatement("valid = false")
              addStatement("if !context.collectsDiagnostics { return false }")
              unindent().add("}\n")
            }
          }
          if (additional.size > 1) addStatement("return valid")
          unindent().add("})")
        }
        add(".isValid(value, mode, context: &context)\n")
      }.build()

  fun validation(
    property: GeneratedModelProperty,
    nestedCheck: CodeBlock? = null,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        beginControlFlow("if", "value.kind == .null")
        if (properties.acceptsNull(property.type) && property.allowedValues?.contains(null) != false) {
          addStatement("return true")
        } else {
          addStatement("return context.reject(.nullValue)")
        }
        endControlFlow("if")
        val check = nestedCheck ?: typeCheck(property.type.copy(nullable = false))
        if (property.validation.isEmpty() && property.allowedValues == null) {
          add("return %L\n", check)
        } else {
          add("var valid = %L\n", check)
          addStatement("if !valid && !context.collectsDiagnostics { return false }")
          val declaration = properties.declarationType(property.type)
          val elementType =
            declaration.arguments
              .singleOrNull()
              ?.takeIf {
                declaration.kind ==
                  GeneratedTypeRef.Kind.ARRAY
              }?.let { it.copy(nullable = properties.acceptsNull(it)) }
          beginControlFlow("if", "!(%L)", SwiftValueConstraints.check(property, CodeBlock.of("value"), elementType))
          addStatement("valid = false")
          endControlFlow("if")
          addStatement("return valid")
        }
      }.build()

  fun unionCheck(
    references: List<GeneratedTypeRef>,
    exclusive: Boolean,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add("{ () -> Bool in\n")
      .indent()
      .addStatement("var matches = 0")
      .apply {
        references.forEach { reference ->
          addStatement("if context.matches({ context in %L }) { matches += 1 }", typeCheck(reference))
        }
      }.addStatement("return %L || context.reject(.allowedValue)", if (exclusive) "matches == 1" else "matches > 0")
      .unindent()
      .add("}()")
      .build()

  private fun typeCheck(reference: GeneratedTypeRef): CodeBlock {
    if (reference.nullable) {
      return CodeBlock.of("value.kind == .null || (%L)", typeCheck(reference.copy(nullable = false)))
    }
    if (reference.kind == GeneratedTypeRef.Kind.UNION) return unionCheck(reference.arguments, exclusive = false)
    if (reference.kind == GeneratedTypeRef.Kind.NAMED) {
      val model = requireNotNull(modelFor(reference))
      if (isFreeform(model)) return CodeBlock.of("true")
      return CodeBlock.of(
        "value.validateNested(mode, schema: %T.self, context: &context) { value, mode, context in %T.isValid(normalized: value, mode, context: &context) }",
        SwiftModelValidation.name(modelName(model)),
        SwiftModelValidation.name(modelName(model)),
      )
    }
    if (reference.kind in setOf(GeneratedTypeRef.Kind.ARRAY, GeneratedTypeRef.Kind.MAP)) {
      val array = reference.kind == GeneratedTypeRef.Kind.ARRAY
      return CodeBlock
        .builder()
        .apply {
          add("{ () -> Bool in\n").indent()
          addStatement(
            "guard let elements = value.%L else { return context.reject(.invalidValue) }",
            if (array) "elements" else "fields",
          )
          addStatement("var valid = true")
          beginControlFlow(
            "for",
            if (array) "(index, value) in elements.enumerated()" else "key in elements.keys.sorted()",
          )
          if (!array) addStatement("let value = elements[key]!")
          add(
            "if !context.at(.%L, { context in %L }) {\n",
            if (array) "index(index)" else "key(key)",
            typeCheck(reference.arguments.single()),
          ).indent()
          addStatement("valid = false")
          addStatement("if !context.collectsDiagnostics { return false }")
          unindent().add("}\n")
          endControlFlow("for")
          addStatement("return valid")
          unindent().add("}()")
        }.build()
    }
    return when (reference.name) {
      "string", "date", "time", "datetime", "datetime-only", "file" ->
        stringTypeCheck(reference)
      "integer", "int32", "int64", "long" ->
        CodeBlock.of(
          "value.number?.isInteger == true || context.reject(.invalidValue)",
        )
      "number", "float", "double" -> CodeBlock.of("value.kind == .number || context.reject(.invalidValue)")
      "boolean" -> CodeBlock.of("value.kind == .boolean || context.reject(.invalidValue)")
      "nil" -> CodeBlock.of("value.kind == .null || context.reject(.nullValue)")
      "object" -> CodeBlock.of("value.kind == .object || context.reject(.invalidValue)")
      else -> CodeBlock.of("value.kind != .invalid || context.reject(.invalidValue)")
    }
  }

  private fun stringTypeCheck(reference: GeneratedTypeRef): CodeBlock {
    val format =
      when (typeName(reference)) {
        DATA -> "base64"
        UUID -> "uuid"
        URL -> "url"
        DATE ->
          when ((reference.format ?: reference.name).lowercase()) {
            "date", "full-date" -> "date"
            "time", "partial-time" -> "time"
            "datetime-only", "date-time-only" -> "localDateTime"
            else -> "dateTime"
          }
        else -> null
      }
    return if (format == null) {
      CodeBlock.of("value.kind == .string || context.reject(.invalidValue)")
    } else {
      CodeBlock.of(
        "%T.%L.isValid(value, context: &context)",
        DeclaredTypeName.typeName("Sunday.ModelStringFormat"),
        format,
      )
    }
  }

  private fun stringProjection(
    reference: GeneratedTypeRef,
    value: CodeBlock,
  ): CodeBlock {
    val projected =
      when (typeName(reference)) {
        DATA -> CodeBlock.of("%L.base64EncodedString()", value)
        UUID -> CodeBlock.of("%L.uuidString", value)
        URL -> CodeBlock.of("%L.absoluteString", value)
        DATE -> return CodeBlock.of("%T.string(%L, format: %S)", valueType, value, reference.format ?: "date-time")
        else -> value
      }
    return CodeBlock.of("%T.string(%L)", valueType, projected)
  }
}
