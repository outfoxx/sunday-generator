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
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.PRIVATE
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec

/** Validating raw-value structs and scalar enums with one shared construction/decoding path. */
internal class SwiftNominalTypes(
  private val nominal: GeneratedNominalTypes,
  private val properties: GeneratedModelProperties,
  private val modelName: (GeneratedModel) -> DeclaredTypeName,
  private val typeName: (GeneratedTypeRef) -> TypeName,
) {
  fun generate(model: GeneratedModel): TypeSpec.Builder? =
    when {
      model.nominal -> scalar(model)
      nominal.branches(model).isNotEmpty() -> union(model)
      else -> null
    }

  private fun scalar(model: GeneratedModel): TypeSpec.Builder {
    val scalar = nominal.scalar(model)
    val raw = typeName(scalar.type)
    val field = scalar.property.copy(name = "rawValue")
    val fields =
      listOf(field) +
        scalar.patterns.filterNot { it == field.validation["pattern"] }.map {
          field.copy(validation = mapOf("pattern" to it))
        }
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
        ),
      ).addProperty(PropertySpec.builder("rawValue", raw, PUBLIC).build())
      .addProperty(description())
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addModifiers(PUBLIC)
          .addDoc("Validates and stores the raw wire value.\n")
          .addParameter("_", "rawValue", raw)
          .throws(true)
          .addCode(
            SwiftModelConstraints.initializer(
              fields.map {
                GeneratedModelProperties.Field(it, it, false)
              },
              properties,
              false,
            ),
          ).addStatement("self.rawValue = rawValue")
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
      ).addFunction(decoder(raw))
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
        .addStatement("var matches: [%T] = []", name)
    cases.forEach { branch ->
      constructor
        .beginControlFlow("if", "let value = try? %T(rawValue)", modelName(branch))
        .addStatement("matches.append(.%N(value))", branch.name.swiftEnumCaseName)
        .endControlFlow("if")
    }
    constructor
      .beginControlFlow("guard", "!matches.isEmpty else")
      .addStatement(
        "throw %T.invalidValue(rawValue, .init(codingPath: [], debugDescription: %S))",
        ENCODING_ERROR,
        "No branch matched ${model.name}",
      ).endControlFlow("guard")
    if (model.unionMode == GeneratedModel.UnionMode.ONE_OF) {
      constructor
        .beginControlFlow("guard", "matches.count == 1 else")
        .addStatement(
          "throw %T.invalidValue(rawValue, .init(codingPath: [], debugDescription: %S))",
          ENCODING_ERROR,
          "Ambiguous value for ${model.name}: multiple branches matched",
        ).endControlFlow("guard")
    }
    constructor.addStatement("self = matches[0]")
    return TypeSpec
      .enumBuilder(name)
      .addModifiers(PUBLIC)
      .addDoc("Validated scalar union %L.\n", model.name)
      .addSuperTypes(listOf(CODABLE, HASHABLE, SENDABLE, CUSTOM_STRING_CONVERTIBLE))
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
      .addFunction(decoder(raw))
      .addFunction(encoder())
  }

  private fun description(): PropertySpec =
    PropertySpec
      .builder("description", STRING, PUBLIC)
      .getter(FunctionSpec.getterBuilder().addStatement("return String(describing: rawValue)").build())
      .build()

  private fun decoder(raw: TypeName): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addStatement("let container = try decoder.singleValueContainer()")
      .addStatement("let rawValue = try container.decode(%T.self)", raw)
      .beginControlFlow("do", "")
      .addStatement("try self.init(rawValue)")
      .nextControlFlow("catch", "")
      .addStatement(
        "throw %T.dataCorruptedError(in: container, debugDescription: String(describing: error))",
        DECODING_ERROR,
      ).endControlFlow("do")
      .build()

  private fun encoder(): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .addStatement("var container = encoder.singleValueContainer()")
      .addStatement("try container.encode(rawValue)")
      .build()
}
