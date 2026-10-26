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

package io.outfoxx.sunday.generator.kotlin.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import java.math.BigDecimal

/** Filters oneOf alternatives by inherited wire constraints, then requires exactly one successful decode. */
internal object KotlinOneOfSelection {
  /** Keeps constructor validation failures as diagnostics when no alternative can be decoded. */
  fun decode(
    name: String,
    unionType: ClassName,
    cases: List<GeneratedModel>,
    properties: GeneratedModelProperties,
    decodeCase: (GeneratedModel) -> CodeBlock,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        val processingException = ClassName("com.fasterxml.jackson.core", "JsonProcessingException")
        addStatement("val matches = mutableListOf<%T>()", unionType)
        addStatement("val failures = mutableListOf<%T>()", processingException)
        // Only Jackson decoding failures disqualify candidates; retain their causes when no branch matches.
        cases.forEach { model ->
          beginControlFlow("if (%L)", matches(model, properties))
          beginControlFlow("try")
          addStatement("matches.add(%L)", decodeCase(model))
          nextControlFlow("catch (cause: %T)", processingException)
          addStatement("failures.add(cause)")
          endControlFlow()
          endControlFlow()
        }
        beginControlFlow("if (matches.size == 1)")
        addStatement("return matches.single()")
        endControlFlow()
        addStatement(
          "val failure = %T.from(parser, if (matches.isEmpty()) %S else %S)",
          JACKSON_JSON_MAPPING_EXCEPTION,
          "unable to determine union case for \"$name\"",
          "ambiguous union case for \"$name\": multiple branches matched",
        )
        addStatement("failures.forEach(failure::addSuppressed)")
        addStatement("throw failure")
      }.build()

  /** Tests required fields and permitted JSON values without coercion or model construction. */
  fun matches(
    model: GeneratedModel,
    properties: GeneratedModelProperties,
  ): CodeBlock =
    buildList {
      add(CodeBlock.of("tree.isObject"))
      properties.fields(model).forEach { field ->
        val property = field.effective
        if (property.required) add(CodeBlock.of("tree.has(%S)", field.wireName))
        property.allowedValues?.let { values ->
          val value = CodeBlock.of("tree.path(%S)", field.wireName)
          val allowed = values.map { allowedValue(value, it) }.joinToCode(" || ")
          val restriction = if (values.isEmpty()) CodeBlock.of("false") else allowed
          add(
            if (property.required) {
              CodeBlock.of("(%L)", restriction)
            } else {
              CodeBlock.of("(!tree.has(%S) || (%L))", field.wireName, restriction)
            },
          )
        }
      }
    }.joinToCode(" && ")

  // Compare JSON values without Jackson coercion (for example, a string "404" is not the number 404).
  private fun allowedValue(
    value: CodeBlock,
    allowed: Any?,
  ): CodeBlock =
    when (allowed) {
      null -> CodeBlock.of("%L.isNull", value)
      is String -> CodeBlock.of("%L.textValue() == %S", value, allowed)
      is Boolean -> CodeBlock.of("(%L.isBoolean && %L.booleanValue() == %L)", value, value, allowed)
      is Number ->
        CodeBlock.of(
          "(%L.isNumber && %L.decimalValue().compareTo(%T(%S)) == 0)",
          value,
          value,
          BigDecimal::class,
          allowed.toString(),
        )
      else -> error("Unsupported oneOf property value: $allowed")
    }
}
