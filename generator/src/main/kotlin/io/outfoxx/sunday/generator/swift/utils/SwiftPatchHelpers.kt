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
import io.outfoxx.swiftpoet.CODABLE
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.INTERNAL
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.NameAllocator
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.TypeVariableName.Companion.bound
import io.outfoxx.swiftpoet.TypeVariableName.Companion.typeVariable

/** Generates thin model conveniences over one lossless wire-tree merge implementation. */
internal class SwiftPatchHelpers(
  private val models: List<GeneratedModel>,
  private val typeName: (GeneratedModel) -> DeclaredTypeName,
) {
  val enabled = models.any { it.patchOf != null }
  private val names =
    NameAllocator().apply {
      models
        .flatMap { it.properties }
        .map { it.name.swiftIdentifierName }
        .distinct()
        .forEach { newName(it) }
    }
  private val patchName = names.newName("patch")
  private val mergeName = names.newName("merge")
  private val factoryName = names.newName("fromModel")
  val supportName by lazy {
    val allocator = NameAllocator()
    models.map { typeName(it).simpleName }.distinct().forEach { allocator.newName(it) }
    DeclaredTypeName.typeName(
      "${typeName(
        models.first(),
      ).moduleName}.${allocator.newName("MergePatchSupport")}",
    )
  }

  fun add(
    model: GeneratedModel,
    builder: TypeSpec.Builder,
    valueType: Boolean,
  ) {
    val patch = models.singleOrNull { it.patchOf?.name == model.name }
    if (patch != null) {
      builder.addFunction(
        FunctionSpec
          .builder(patchName)
          .addModifiers(PUBLIC)
          .throws(true)
          .addDoc("Creates a snapshot or the changes from an earlier model. Required null assignments throw.\n")
          .addParameter(
            ParameterSpec.builder("from", "original", typeName(model).makeOptional()).defaultValue("nil").build(),
          ).returns(typeName(patch))
          .addStatement("try validate(.response)")
          .addStatement("return try %T.patch(self, from: original)", supportName)
          .build(),
      )
      builder.addFunction(
        FunctionSpec
          .builder(mergeName)
          .addModifiers(PUBLIC)
          .throws(true)
          .addDoc("Recursively merges a patch into a new validated value, preserving this instance.\n")
          .addParameter("_", "patch", typeName(patch))
          .returns(typeName(model))
          .addStatement("try validate(.response)")
          .addStatement("try patch.validate(.response)")
          .addStatement("return try %T.apply(self, patch: patch)", supportName)
          .build(),
      )
    }
    val original = models.singleOrNull { it.name == model.patchOf?.name }
    if (original != null) {
      builder.addFunction(
        FunctionSpec
          .builder(factoryName)
          .addModifiers(PUBLIC, STATIC)
          .throws(true)
          .addDoc("Creates a patch snapshot using the ordinary model's conversion.\n")
          .addParameter("_", "model", typeName(original))
          .returns(typeName(model))
          .addStatement("return try model.%N()", patchName)
          .build(),
      )
      if (valueType) {
        builder.addFunction(
          FunctionSpec
            .constructorBuilder()
            .addModifiers(PUBLIC)
            .throws(true)
            .addDoc("Creates a patch snapshot through the shared conversion factory.\n")
            .addParameter("_", "model", typeName(original))
            .addStatement("self = try Self.%N(model)", factoryName)
            .build(),
        )
      }
    }
  }

  fun support(): TypeSpec.Builder {
    val value = typeVariable("Value", bound(CODABLE))
    val patch = typeVariable("Patch", bound(CODABLE))
    val encoder = DeclaredTypeName.typeName("PotentCodables.AnyValueEncoder")
    val decoder = DeclaredTypeName.typeName("PotentCodables.AnyValueDecoder")
    val node = DeclaredTypeName.typeName("PotentCodables.AnyValue")
    return TypeSpec
      .enumBuilder(supportName)
      .addModifiers(INTERNAL)
      .addDoc("Shared RFC 7396 operations; tree coding preserves scalar types and numeric precision.\n")
      .addFunction(
        FunctionSpec
          .builder("patch")
          .addModifiers(STATIC)
          .addTypeVariable(value)
          .addTypeVariable(patch)
          .throws(true)
          .addParameter("_", "value", value)
          .addParameter("from", "original", value.makeOptional())
          .returns(patch)
          .addCode(
            """
            let encoder = %T()
            let tree = try difference(encoder.encodeTree(value), original: original.map { try encoder.encodeTree($0) })
            let result = try %T().decodeTree(Patch.self, from: tree)
            guard try equal(encoder.encodeTree(result), tree) else {
              throw %T.invalidValue(value, .init(codingPath: [], debugDescription: "Patch conversion changed the wire value"))
            }
            return result
            """.trimIndent() + "\n",
            encoder,
            decoder,
            DeclaredTypeName.typeName("Swift.EncodingError"),
          ).build(),
      ).addFunction(
        FunctionSpec
          .builder("apply")
          .addModifiers(STATIC)
          .addTypeVariable(value)
          .addTypeVariable(patch)
          .throws(true)
          .addParameter("_", "value", value)
          .addParameter("patch", "patch", patch)
          .returns(value)
          .addCode(
            """
            let encoder = %T()
            let tree = try merge(encoder.encodeTree(value), patch: encoder.encodeTree(patch))
            let decoder = %T()
            decoder.userInfo[.init(rawValue: "io.outfoxx.sunday.mergePatch")!] = true
            return try decoder.decodeTree(Value.self, from: tree)
            """.trimIndent() + "\n",
            encoder,
            decoder,
          ).build(),
      ).addFunction(
        FunctionSpec
          .builder("equal")
          .addModifiers(STATIC)
          .addParameter("_", "left", node.makeOptional())
          .addParameter("_", "right", node.makeOptional())
          .returns(io.outfoxx.swiftpoet.BOOL)
          .addCode(
            """
            guard let left, let right else { return left == right }
            if let a = left.dictionaryValue, let b = right.dictionaryValue {
              return a.count == b.count && a.allSatisfy { equal($0.value, b[$0.key]) }
            }
            if case .array(let a) = left, case .array(let b) = right {
              return a.count == b.count && zip(a, b).allSatisfy { equal($0.0, $0.1) }
            }
            return left == right
            """.trimIndent() + "\n",
          ).build(),
      ).addFunction(
        FunctionSpec
          .builder("difference")
          .addModifiers(STATIC)
          .addParameter("_", "value", node)
          .addParameter("original", "original", node.makeOptional())
          .returns(node)
          .addCode(
            """
            guard case .dictionary(let fields) = value else { return value }
            var result: %T.AnyDictionary = [:]
            let previous = original?.dictionaryValue ?? [:]
            for (key, item) in fields where !equal(previous[key], item) {
              result[key] = difference(item, original: previous[key])
            }
            for key in previous.keys where fields[key] == nil { result[key] = .nil }
            return .dictionary(result)
            """.trimIndent() + "\n",
            node,
          ).build(),
      ).addFunction(
        FunctionSpec
          .builder("merge")
          .addModifiers(STATIC)
          .addParameter("_", "value", node.makeOptional())
          .addParameter("patch", "patch", node)
          .returns(node)
          .addCode(
            """
            guard case .dictionary(let fields) = patch else { return patch }
            var result = value?.dictionaryValue ?? [:]
            for (key, item) in fields {
              if item == .nil { result.removeValue(forKey: key) }
              else { result[key] = merge(result[key], patch: item) }
            }
            return .dictionary(result)
            """.trimIndent() + "\n",
          ).build(),
      )
  }

  /** Merge decoding bypasses schema defaults so deleted members remain absent. */
  fun decodingDefault(default: CodeBlock): CodeBlock =
    if (enabled) {
      CodeBlock.of(
        "(decoder.userInfo[.init(rawValue: %S)!] as? Bool == true ? nil : %L)",
        "io.outfoxx.sunday.mergePatch",
        default,
      )
    } else {
      default
    }
}
