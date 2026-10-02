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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.INTERNAL
import io.outfoxx.swiftpoet.Modifier.PRIVATE
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec

/** Validating raw-value structs and scalar enums with one shared construction/decoding path. */
internal class SwiftNominalTypes(
  private val nominal: GeneratedNominalTypes,
  private val views: () -> SwiftValidationViews,
  private val modelName: (GeneratedModel) -> DeclaredTypeName,
  private val typeName: (GeneratedTypeRef) -> TypeName,
) {
  fun generate(model: GeneratedModel): TypeSpec.Builder? =
    when {
      model.nominal -> scalar(model)
      nominal.branches(model).isNotEmpty() -> union(model)
      else -> null
    }

  fun validator(model: GeneratedModel): TypeSpec.Builder {
    val name = SwiftModelValidation.name(modelName(model))
    val reference = if (model.nominal) nominal.scalar(model).type else nominal.unionType(model)
    val raw = typeName(reference)
    val body =
      if (model.nominal) {
        val scalar = nominal.scalar(model)
        val field = scalar.property.copy(name = "rawValue")
        val assertions =
          listOf(field) +
            scalar.patterns.filterNot { it == field.validation["pattern"] }.map {
              field.copy(validation = mapOf("pattern" to it))
            }
        CodeBlock
          .builder()
          .add("%L valid = { () -> Bool in\n", if (assertions.size > 1) "var" else "let")
          .indent()
          .add(views().validation(assertions.first()))
          .unindent()
          .add("}()\n")
          .addStatement("if !valid && !context.collectsDiagnostics { return false }")
          .apply {
            assertions.drop(1).forEach { assertion ->
              beginControlFlow("if", "!(%L)", SwiftValueConstraints.check(assertion, CodeBlock.of("value")))
              addStatement("valid = false")
              addStatement("if !context.collectsDiagnostics { return false }")
              endControlFlow("if")
            }
          }.addStatement("return valid")
          .build()
      } else {
        CodeBlock
          .builder()
          .apply {
            addStatement("var matches: [Int] = []")
            nominal.branches(model).forEachIndexed { index, branch ->
              beginControlFlow(
                "if",
                "context.matches({ context in %T.isValid(normalized: value, mode, context: &context) })",
                SwiftModelValidation.name(modelName(branch)),
              )
              addStatement("matches.append(%L)", index)
              endControlFlow("if")
            }
            beginControlFlow("guard", "!matches.isEmpty else")
            addStatement("return context.reject(.allowedValue)")
            endControlFlow("guard")
            if (model.unionMode == GeneratedModel.UnionMode.ONE_OF) {
              beginControlFlow("guard", "matches.count == 1 else")
              addStatement("return context.reject(.allowedValue)")
              endControlFlow("guard")
            }
            addStatement("context.selectAlternative(matches[0])")
            addStatement("return true")
          }.build()
      }
    return SwiftModelValidation
      .type(
        name,
        modelName(model),
        CodeBlock.of("return isValid(normalized: view(value), mode, context: &context)\n"),
      ).addFunction(
        FunctionSpec
          .builder("view")
          .addModifiers(STATIC)
          .addDoc("Projects the raw scalar without constructing or encoding an application model.\n")
          .addParameter("_", "value", modelName(model))
          .returns(SwiftValueConstraints.valueType)
          .addStatement("return %L", views().project(reference, CodeBlock.of("value.rawValue")))
          .build(),
      ).addFunction(
        SwiftModelValidation
          .function(raw, "rawValue", "rawValue")
          .addModifiers(STATIC)
          .addStatement(
            "return isValid(normalized: %L, mode, context: &context)",
            views().project(reference, CodeBlock.of("rawValue")),
          ).build(),
      ).addFunction(
        SwiftModelValidation
          .function(SwiftValueConstraints.valueType, "value", "normalized")
          .addModifiers(STATIC)
          .addCode(body)
          .build(),
      )
  }

  private fun scalar(model: GeneratedModel): TypeSpec.Builder {
    val scalar = nominal.scalar(model)
    val raw = typeName(scalar.type)
    return TypeSpec
      .structBuilder(modelName(model))
      .addModifiers(PUBLIC)
      .addDoc("Validated scalar value for %L.\n", model.name)
      .addSuperTypes(
        listOf(
          DeclaredTypeName.typeName("Swift.RawRepresentable"),
          CODABLE,
          HASHABLE,
          SENDABLE,
          CUSTOM_STRING_CONVERTIBLE,
          SwiftModelValidation.validatable,
        ),
      ).addProperty(PropertySpec.builder("rawValue", raw, PUBLIC).build())
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addModifiers(INTERNAL)
          .addDoc("Stores a branch value after the containing union has validated and selected it.\n")
          .addParameter("validatedRawValue", raw)
          .addStatement("self.rawValue = validatedRawValue")
          .build(),
      ).addProperty(description())
      .addFunction(SwiftModelValidation.instance(SwiftModelValidation.name(modelName(model))))
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addModifiers(PUBLIC)
          .addDoc("Validates and stores the raw wire value.\n")
          .addParameter("_", "rawValue", raw)
          .throws(true)
          .addStatement("self.rawValue = rawValue")
          .addStatement("try %T.validate(self, .response)", SwiftModelValidation.name(modelName(model)))
          .build(),
      ).addFunction(
        FunctionSpec
          .constructorBuilder()
          .addModifiers(PUBLIC)
          .failable(true)
          .addDoc("Returns nil when the wire value violates the schema.\n")
          .addParameter("rawValue", raw)
          .addStatement("try? self.init(rawValue)")
          .build(),
      ).addFunction(decoder(model, raw))
      .addFunction(encoder())
      .addType(
        TypeSpec
          .enumBuilder("CodingKeys")
          .addModifiers(PRIVATE)
          .addSuperTypes(listOf(STRING, CODING_KEY))
          .addEnumCase("rawValue")
          .build(),
      )
  }

  private fun union(model: GeneratedModel): TypeSpec.Builder {
    val name = modelName(model)
    val raw = typeName(nominal.unionType(model))
    val cases = nominal.branches(model)
    val constructor =
      FunctionSpec
        .constructorBuilder()
        .addModifiers(PUBLIC)
        .throws(true)
        .addDoc("Selects a validated branch using the source union's matching rule.\n")
        .addParameter("_", "rawValue", raw)
        .addStatement("var context = %T(collectsDiagnostics: true)", SwiftModelValidation.context)
        .beginControlFlow(
          "guard",
          "%T.isValid(rawValue: rawValue, .response, context: &context) else",
          SwiftModelValidation.name(name),
        ).addStatement("throw context.validationError")
        .endControlFlow("guard")
        .addCode(alternativeSelection(model))
    return TypeSpec
      .enumBuilder(name)
      .addModifiers(PUBLIC)
      .addDoc("Validated scalar union %L.\n", model.name)
      .addSuperTypes(listOf(CODABLE, HASHABLE, SENDABLE, CUSTOM_STRING_CONVERTIBLE, SwiftModelValidation.validatable))
      .addFunction(SwiftModelValidation.instance(SwiftModelValidation.name(name)))
      .apply { cases.distinct().forEach { addEnumCase(it.name.swiftEnumCaseName, modelName(it)) } }
      .addProperty(
        PropertySpec
          .builder("rawValue", raw, PUBLIC)
          .getter(
            FunctionSpec
              .getterBuilder()
              .beginControlFlow("switch", "self")
              .apply {
                cases.distinct().forEach {
                  addStatement(
                    "case .%N(let value): return value.rawValue",
                    it.name.swiftEnumCaseName,
                  )
                }
              }.endControlFlow("switch")
              .build(),
          ).build(),
      ).addProperty(description())
      .addFunction(constructor.build())
      .addFunction(decoder(model, raw))
      .addFunction(encoder())
  }

  private fun description(): PropertySpec =
    PropertySpec
      .builder("description", STRING, PUBLIC)
      .getter(FunctionSpec.getterBuilder().addStatement("return String(describing: rawValue)").build())
      .build()

  private fun alternativeSelection(model: GeneratedModel): CodeBlock =
    CodeBlock
      .builder()
      .beginControlFlow("switch", "context.selectedAlternative")
      .apply {
        nominal.branches(model).forEachIndexed { index, branch ->
          addStatement(
            "case %L:%Wself = .%N(%T(validatedRawValue: rawValue))",
            index,
            branch.name.swiftEnumCaseName,
            modelName(branch),
          )
        }
      }.addStatement(
        "default:%WpreconditionFailure(%S)",
        "Canonical union validation did not select an alternative",
      ).endControlFlow("switch")
      .build()

  private fun decoder(
    model: GeneratedModel,
    raw: TypeName,
  ): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addStatement("let container = try decoder.singleValueContainer()")
      .addStatement("let rawValue = try container.decode(%T.self)", raw)
      .addStatement("var context = try %T.decodingValue(decoder)", SwiftModelValidation.context)
      .beginControlFlow(
        "guard",
        "%T.isValid(rawValue: rawValue, .response, context: &context) else",
        SwiftModelValidation.name(modelName(model)),
      ).addStatement("throw context.decodingError")
      .endControlFlow("guard")
      .apply {
        if (model.nominal) {
          addStatement(
            "self.init(validatedRawValue: rawValue)",
          )
        } else {
          addCode(alternativeSelection(model))
        }
      }.build()

  private fun encoder(): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .addStatement("try validate(.response)")
      .addStatement("var container = encoder.singleValueContainer()")
      .addStatement("try container.encode(rawValue)")
      .build()
}
