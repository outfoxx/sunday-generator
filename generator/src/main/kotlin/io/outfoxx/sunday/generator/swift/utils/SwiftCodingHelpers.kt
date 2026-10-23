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
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.INT
import io.outfoxx.swiftpoet.Modifier.PRIVATE
import io.outfoxx.swiftpoet.NameAllocator
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeAliasSpec
import io.outfoxx.swiftpoet.TypeSpec

/** Shares dynamic-property wire coding within each module while retaining consumer ownership. */
internal class SwiftCodingHelpers(
  models: List<GeneratedModel>,
  private val typeName: (GeneratedModel) -> DeclaredTypeName,
) {
  private val consumers = linkedMapOf<DeclaredTypeName, MutableSet<GeneratedModel>>()
  private val encoders = mutableSetOf<DeclaredTypeName>()
  private val names by lazy {
    models.groupBy { typeName(it).moduleName }.mapValues { (module, declarations) ->
      val names = NameAllocator()
      declarations.map { typeName(it).topLevelTypeName().simpleName }.distinct().forEach { names.newName(it) }
      DeclaredTypeName.typeName("$module.${names.newName("ModelCodingSupport")}")
    }
  }

  fun add(
    builder: TypeSpec.Builder,
    model: GeneratedModel,
    encoder: Boolean,
  ) {
    val name = names.getValue(typeName(model).moduleName)
    consumers.getOrPut(name) { linkedSetOf() }.add(model)
    builder.addType(
      TypeAliasSpec
        .builder(
          "UnknownPropertyCodingKey",
          name.nestedType("UnknownPropertyCodingKey"),
        ).addModifiers(PRIVATE)
        .build(),
    )
    if (encoder) {
      encoders.add(name)
      builder.addType(
        TypeAliasSpec
          .builder(
            "AdditionalPropertyValue",
            name.nestedType("AdditionalPropertyValue"),
          ).addModifiers(PRIVATE)
          .build(),
      )
    }
  }

  fun emit(register: (DeclaredTypeName, TypeSpec.Builder, List<GeneratedModel>) -> Unit) {
    consumers.forEach { (name, models) ->
      val helper =
        TypeSpec
          .enumBuilder(name)
          .addDoc("Shared coding for schema-permitted dynamic properties.\n")
          .addType(unknownPropertyCodingKeyType())
      if (name in encoders) helper.addType(extensionValueEncoderType())
      register(name, helper, models.toList())
    }
  }

  // AnyValue's ordered dictionary uses an unkeyed representation with Foundation's JSONEncoder.
  // Encode containers recursively so preserved JSON objects remain objects with any supported encoder.
  private fun extensionValueEncoderType(): TypeSpec =
    TypeSpec
      .structBuilder("AdditionalPropertyValue")
      .addSuperType(DeclaredTypeName.typeName("Swift.Encodable"))
      .addProperty(PropertySpec.builder("value", ANY_VALUE).build())
      .addFunction(
        FunctionSpec
          .builder("encode")
          .addParameter("to", "encoder", ENCODER)
          .throws(true)
          .addCode(
            CodeBlock.of(
              """
              switch value {
              case .dictionary(let values):
                var container = encoder.container(keyedBy: UnknownPropertyCodingKey.self)
                for (key, item) in values {
                  guard case .string(let name) = key else {
                    throw %T.invalidValue(key, .init(codingPath: encoder.codingPath, debugDescription: "JSON object keys must be strings"))
                  }
                  try container.encode(AdditionalPropertyValue(value: item), forKey: UnknownPropertyCodingKey(stringValue: name))
                }
              case .array(let values):
                var container = encoder.unkeyedContainer()
                for item in values { try container.encode(AdditionalPropertyValue(value: item)) }
              default:
                try value.encode(to: encoder)
              }

              """.trimIndent(),
              DeclaredTypeName.typeName("Swift.EncodingError"),
            ),
          ).build(),
      ).build()

  // Unlike the model's finite CodingKeys enum, this key accepts every input property name.
  private fun unknownPropertyCodingKeyType(): TypeSpec =
    TypeSpec
      .structBuilder("UnknownPropertyCodingKey")
      .addSuperType(CODING_KEY)
      .addProperty(PropertySpec.builder("stringValue", STRING).build())
      .addProperty(PropertySpec.builder("intValue", INT.makeOptional()).initializer("nil").build())
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addParameter("stringValue", STRING)
          .addStatement("self.stringValue = stringValue")
          .build(),
      ).addFunction(
        FunctionSpec
          .constructorBuilder()
          .addParameter("intValue", INT)
          .addStatement("self.stringValue = String(intValue)")
          .build(),
      ).build()
}
