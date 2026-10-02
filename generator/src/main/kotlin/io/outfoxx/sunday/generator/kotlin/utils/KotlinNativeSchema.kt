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
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds

/** Projects effective schema metadata into one reusable native Bean Validation constraint. */
internal object KotlinNativeSchema {
  fun constructor(
    owner: ClassName,
    parameters: List<ParameterSpec>,
    types: BeanValidationTypes,
  ): CodeBlock =
    CodeBlock
      .builder()
      .beginControlFlow("if (javaClass == %T::class.java)", owner)
      .addStatement(
        "%T.constructor(%T::class.java, arrayOf(%L)%L)",
        types.modelValidation,
        owner,
        parameters.map { it.type.jvmClass() }.joinToCode(),
        parameters.map { CodeBlock.of(", %N", it.name) }.joinToCode(""),
      ).endControlFlow()
      .build()

  private fun TypeName.jvmClass(): CodeBlock =
    when {
      this is ParameterizedTypeName && rawType == ARRAY ->
        CodeBlock.of(
          "java.lang.reflect.Array.newInstance(%L, 0).javaClass",
          typeArguments.single().copy(nullable = true).jvmClass(),
        )
      this is ParameterizedTypeName -> rawType.jvmClass()
      this is TypeVariableName -> (bounds.firstOrNull() ?: ANY).jvmClass()
      else ->
        CodeBlock.of(
          "%T::class.%L",
          copy(nullable = false).copy(annotations = emptyList()),
          if (isNullable) "javaObjectType" else "java",
        )
    }

  fun commonSchema(
    reference: GeneratedTypeRef,
    properties: GeneratedModelProperties,
    typeName: (GeneratedTypeRef) -> TypeName,
  ): ClassName? =
    properties.declarationModel(reference)?.takeIf(properties::hasUnionCommonRules)?.let {
      (typeName(reference).copy(nullable = false) as ClassName).nestedClass("CommonPropertiesValidation")
    }

  fun annotation(
    property: GeneratedModelProperty,
    properties: GeneratedModelProperties,
    types: BeanValidationTypes,
    useSite: AnnotationSpec.UseSiteTarget? = null,
    patterns: List<String> = properties.patterns(property),
    objectSchema: ClassName? = null,
  ): AnnotationSpec? {
    val validation = property.validation
    val required =
      property.required && (!properties.acceptsNull(property.type) || property.allowedValues?.contains(null) == false)
    if (!required && validation.isEmpty() && property.allowedValues == null && objectSchema == null) return null
    return AnnotationSpec
      .builder(types.schema)
      .apply {
        useSite?.let(::useSiteTarget)
        objectSchema?.let { addMember("objectSchema = %T::class", it) }
        if (required) addMember("requiredValue = true")
        validation.forEach { (key, value) ->
          if (key in setOf("minLength", "maxLength", "minItems", "maxItems", "uniqueItems")) {
            addMember("%L = %L", key, value)
          }
        }
        if (patterns.isNotEmpty()) {
          addMember(
            "patterns = %L",
            patterns
              .map {
                CodeBlock.of("%S", it)
              }.joinToCode(", ", "[", "]"),
          )
        }
        val bounds = GeneratedNumericBounds.parse(validation, property.name)
        bounds.forEach { bound ->
          val name = if (bound.lower) "minimum" else "maximum"
          addMember("%L = %S", name, bound.value.toString())
          if (bound.exclusive) addMember("%L = true", if (bound.lower) "exclusiveMinimum" else "exclusiveMaximum")
        }
        GeneratedNumericBounds
          .multipleOf(
            validation,
            property.name,
          )?.let { addMember("multipleOf = %S", it.toPlainString()) }
        val declaration = properties.declarationType(property.type)
        if ((bounds.isNotEmpty() || "multipleOf" in validation) && declaration.kind == GeneratedTypeRef.Kind.ARRAY) {
          addMember("numericElements = true")
        }
        if ((patterns.isNotEmpty() || "minLength" in validation || "maxLength" in validation) &&
          declaration.kind == GeneratedTypeRef.Kind.ARRAY
        ) {
          addMember("stringElements = true")
        }
        declaration.format?.let { addMember("format = %S", it) }
        property.allowedValues?.let { values ->
          addMember("restrictValues = true")
          val strings = values.filterIsInstance<String>()
          val numbers = values.filterIsInstance<Number>()
          val booleans = values.filterIsInstance<Boolean>()
          if (strings.isNotEmpty()) {
            addMember(
              "allowedStrings = %L",
              strings
                .map {
                  CodeBlock.of("%S", it)
                }.joinToCode(", ", "[", "]"),
            )
          }
          if (numbers.isNotEmpty()) {
            addMember(
              "allowedNumbers = %L",
              numbers
                .map {
                  CodeBlock.of("%S", it.toString())
                }.joinToCode(", ", "[", "]"),
            )
          }
          if (booleans.isNotEmpty()) {
            addMember(
              "allowedBooleans = %L",
              booleans
                .map {
                  CodeBlock.of("%L", it)
                }.joinToCode(", ", "[", "]"),
            )
          }
        }
      }.build()
  }
}
