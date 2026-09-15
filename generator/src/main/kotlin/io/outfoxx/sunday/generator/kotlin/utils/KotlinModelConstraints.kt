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

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/** Checks effective wire restrictions against stored values and supplied patch payloads. */
internal object KotlinModelConstraints {
  fun initializer(
    fields: List<GeneratedModelProperties.Field>,
    properties: GeneratedModelProperties,
    patchParameters: Set<String> = emptySet(),
    typeName: (GeneratedTypeRef) -> TypeName,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        fields.forEach { field ->
          val property = field.effective
          val name = field.storage.name.kotlinIdentifierName
          val patch = name in patchParameters
          val value = CodeBlock.of(if (patch) "%N.value" else "%N", name)
          val storageType = typeName(field.storage.type).copy(nullable = false)
          val untyped = storageType == ANY
          val wireValue = wireValue(field.storage.type, storageType, value, properties)
          val divisor = GeneratedNumericBounds.multipleOf(property.validation, "property '${field.wireName}'")
          val numericTarget =
            divisor?.let {
              properties.numericValidationTarget(field.storage.type, "property '${field.wireName}'")
            }
          val numericValue = if (numericTarget?.elements == true) CodeBlock.of("element") else value
          val numericChecks = mutableListOf<CodeBlock>()
          val checks = mutableListOf<CodeBlock>()
          property.allowedValues?.let { values ->
            checks += values
              .filterNotNull()
              .map { allowed ->
                when (allowed) {
                  is Number ->
                    CodeBlock
                      .builder()
                      .apply {
                        if (untyped) add("%L is %T && ", value, Number::class)
                        add(
                          "%L.toString().toBigDecimalOrNull()?.compareTo(%T(%S)) == 0",
                          value,
                          BigDecimal::class,
                          allowed.toString(),
                        )
                      }.build()
                  is Boolean ->
                    if (untyped) {
                      CodeBlock.of(
                        "%L is %T && %L == %L",
                        value,
                        Boolean::class,
                        value,
                        allowed,
                      )
                    } else {
                      CodeBlock.of("%L == %S", wireValue, allowed.toString())
                    }
                  is String ->
                    if (untyped) {
                      CodeBlock.of(
                        "%L is %T && %L == %S",
                        value,
                        String::class,
                        value,
                        allowed,
                      )
                    } else {
                      CodeBlock.of("%L == %S", wireValue, allowed)
                    }
                  else -> CodeBlock.of("%L == %S", wireValue, allowed.toString())
                }
              }.joinToCode(" || ")
              .takeUnless { it.isEmpty() } ?: CodeBlock.of("false")
          }
          if (patch ||
            field.inherited &&
            property.validation != field.declaration.validation ||
            numericTarget?.elements == true
          ) {
            GeneratedNumericBounds.parse(property.validation, "property '${field.wireName}'").forEach { bound ->
              numericChecks +=
                CodeBlock.of(
                  "%T(%L.toString()).compareTo(%T(%S)) %L 0",
                  BigDecimal::class,
                  numericValue,
                  BigDecimal::class,
                  bound.value.toString(),
                  bound.operator,
                )
            }
            property.validation.forEach { (key, bound) ->
              when (key) {
                "minLength" ->
                  checks +=
                    CodeBlock.of("%L.codePointCount(0, %L.length) >= %L", wireValue, wireValue, bound)
                "maxLength" ->
                  checks +=
                    CodeBlock.of("%L.codePointCount(0, %L.length) <= %L", wireValue, wireValue, bound)
                "pattern" -> checks += CodeBlock.of("%T(%S).containsMatchIn(%L)", Regex::class, bound, wireValue)
                "minItems" -> checks += CodeBlock.of("%L.size >= %L", value, bound)
                "maxItems" -> checks += CodeBlock.of("%L.size <= %L", value, bound)
                "uniqueItems" -> if (bound == "true") checks += CodeBlock.of("%L.toSet().size == %L.size", value, value)
              }
            }
          }
          divisor?.let {
            numericChecks +=
              CodeBlock.of(
                "%T(%L.toString()).remainder(%T(%S)).signum() == 0",
                BigDecimal::class,
                numericValue,
                BigDecimal::class,
                divisor.toPlainString(),
              )
          }
          if (numericTarget?.elements == true) {
            val predicate = numericChecks.joinToCode(" && ")
            checks +=
              if (numericTarget.nullable) {
                CodeBlock.of("%L.all { element -> element == null || (%L) }", value, predicate)
              } else {
                CodeBlock.of("%L.all { element -> %L }", value, predicate)
              }
          } else {
            checks += numericChecks
          }
          val optionalStorage = !patch && (!field.storage.required || field.storage.type.nullable)
          val requiresValue =
            property.required && (!property.type.nullable || property.allowedValues?.contains(null) == false)
          if (optionalStorage && requiresValue) {
            addStatement(
              "require(%N != null) { %S }",
              name,
              "Property '${field.wireName}' is required and cannot be null",
            )
          }
          if (checks.isNotEmpty()) {
            if (patch) {
              beginControlFlow("if (%N is %T)", name, PATCH_SET_OP)
            } else if (optionalStorage && !requiresValue) {
              beginControlFlow("if (%N != null)", name)
            }
            checks.forEach { condition ->
              addStatement("require(%L) { %S }", condition, "Invalid value for '${field.wireName}'")
            }
            if (patch || optionalStorage && !requiresValue) endControlFlow()
          }
        }
      }.build()

  private fun wireValue(
    type: GeneratedTypeRef,
    storageType: TypeName,
    value: CodeBlock,
    properties: GeneratedModelProperties,
  ): CodeBlock =
    when {
      properties.declarationModel(type)?.unknownValue != null -> CodeBlock.of("%L.wireValue", value)
      storageType == STRING -> value
      storageType == BYTE_ARRAY && properties.declarationType(type).format.equals("byte", ignoreCase = true) ->
        CodeBlock.of("%T.getEncoder().encodeToString(%L)", Base64::class, value)
      else ->
        temporalFormatters[storageType]?.let { formatter ->
          CodeBlock.of("%L.format(%T.%L)", value, DateTimeFormatter::class, formatter)
        } ?: CodeBlock.of("%L.toString()", value)
    }

  // ISO serializers retain zero seconds, unlike the temporal types' abbreviated toString output.
  private val temporalFormatters =
    mapOf(
      LocalDate::class.asTypeName() to "ISO_LOCAL_DATE",
      LocalTime::class.asTypeName() to "ISO_LOCAL_TIME",
      LocalDateTime::class.asTypeName() to "ISO_LOCAL_DATE_TIME",
      OffsetDateTime::class.asTypeName() to "ISO_OFFSET_DATE_TIME",
    )
}
