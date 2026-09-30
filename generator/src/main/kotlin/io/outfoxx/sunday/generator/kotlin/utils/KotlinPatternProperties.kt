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
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import java.util.Collections

/** Validates dynamic fields with the active mapper before optionally retaining their decoded values. */
internal fun TypeSpec.Builder.addPatternModelStorage(
  className: ClassName,
  model: GeneratedModel,
  knownNames: Set<String>,
  parent: OpenModelExtensionStorage?,
  closed: Boolean,
  preserve: Boolean,
  typeName: (GeneratedTypeRef) -> TypeName,
  properties: GeneratedModelProperties,
): OpenModelExtensionStorage {
  val names = NameAllocator()
  knownNames.forEach { names.newName(it) }
  val setterName = parent?.setterName ?: names.newName("setAdditionalProperty")
  val storageName = names.newName("extensionFields")
  val any = ANY.copy(nullable = true)
  val inheritable = build().modifiers.any { it in setOf(KModifier.OPEN, KModifier.ABSTRACT, KModifier.SEALED) }
  val decoder =
    patternDecoder(
      className,
      model.patternProperties,
      closed,
      typeName,
      properties,
      additional = model.additionalProperties,
    )
  val constructorStorage = preserve && parent?.preserves != true && KModifier.DATA in build().modifiers
  if (preserve && parent?.preserves != true) {
    addExtensionStorage(storageName, any)
    addProperty(
      PropertySpec
        .builder(names.newName("additionalProperties"), MAP.parameterizedBy(STRING, any))
        .addKdoc("Pattern-matched extension fields at their original JSON level.\n")
        .addAnnotation(
          AnnotationSpec
            .builder(
              ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter"),
            ).useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
            .build(),
        ).addAnnotation(
          AnnotationSpec
            .builder(JACKSON_JSON_INCLUDE)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
            .addMember(
              "value = %T.ALWAYS, content = %T.ALWAYS",
              JACKSON_JSON_INCLUDE_INCLUDE,
              JACKSON_JSON_INCLUDE_INCLUDE,
            ).build(),
        ).getter(
          FunSpec
            .getterBuilder()
            .addStatement(
              "return %T.unmodifiableMap(%N)",
              Collections::class,
              storageName,
            ).build(),
        ).build(),
    )
  }
  addFunction(
    FunSpec
      .builder(setterName)
      .addKdoc("Validates and optionally preserves pattern-matched extension fields.\n")
      .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter"))
      .addAnnotation(
        AnnotationSpec.builder(JACKSON_JSON_DESERIALIZE).addMember("contentUsing = %T::class", decoder).build(),
      ).addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNUSED_PARAMETER").build())
      .addParameter("name", STRING)
      .addParameter("value", parent?.valueType ?: any)
      .apply {
        if (parent != null) {
          addModifiers(KModifier.OVERRIDE)
        } else if (inheritable) {
          addModifiers(KModifier.OPEN)
        }
        if (preserve) {
          if (parent?.preserves != true) {
            if (constructorStorage) {
              addStatement("%N = %N + (name to value)", storageName, storageName)
            } else {
              addStatement("%N[name] = value", storageName)
            }
          } else {
            addStatement("super.%N(name, value)", parent.setterName)
          }
        }
      }.build(),
  )
  // Constructor annotations enforce child constraints even for storage declared by a superclass.
  val decoders =
    properties
      .fields(model)
      .mapNotNull { field ->
        val matching = model.patternProperties.filter { Regex(it.pattern).containsMatchIn(field.wireName) }
        if (matching.isEmpty()) return@mapNotNull null
        val name = field.storage.name.kotlinIdentifierName
        name to patternDecoder(className, matching, false, typeName, properties, field.storage.type, name)
      }.toMap()
  val localPropertyNames = propertySpecs.mapTo(mutableSetOf()) { it.name }
  build()
    .primaryConstructor
    ?.toBuilder()
    ?.apply {
      parameters.replaceAll { parameter ->
        if (parameter.name in localPropertyNames) return@replaceAll parameter
        val fieldDecoder = decoders[parameter.name] ?: return@replaceAll parameter
        parameter
          .toBuilder()
          .addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_DESERIALIZE)
              .addMember("using = %T::class", fieldDecoder)
              .build(),
          ).build()
      }
    }?.build()
    ?.let(::primaryConstructor)
  propertySpecs.replaceAll { property ->
    val fieldDecoder = decoders[property.name] ?: return@replaceAll property
    property
      .toBuilder()
      .addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_DESERIALIZE)
          .useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
          .addMember("using = %T::class", fieldDecoder)
          .build(),
      ).build()
  }
  return OpenModelExtensionStorage("", closed, setterName, parent?.valueType ?: any, preserves = preserve)
}

private fun TypeSpec.Builder.patternDecoder(
  owner: ClassName,
  patterns: List<GeneratedPatternProperty>,
  closed: Boolean,
  typeName: (GeneratedTypeRef) -> TypeName,
  properties: GeneratedModelProperties,
  declaredType: GeneratedTypeRef? = null,
  suffix: String = "AdditionalProperty",
  additional: GeneratedAdditionalProperties? = null,
): ClassName {
  val names = NameAllocator()
  build().typeSpecs.mapNotNull { it.name }.forEach { names.newName(it) }
  val decoderType = owner.nestedClass(names.newName(suffix.replaceFirstChar { it.uppercase() } + "PatternDeserializer"))
  val any = ANY.copy(nullable = true)
  val body = CodeBlock.builder()
  body.addStatement("val name = parser.currentName()")
  body.addStatement("val node = context.readTree(parser)")
  body.addStatement("var matched = false")
  body.addStatement("var decoded: Any? = null")
  patterns.forEach { pattern ->
    body.beginControlFlow("if (%T(%S).containsMatchIn(name))", Regex::class, pattern.pattern)
    body.addStatement("matched = true")
    body.add(patternNodeValidation(pattern.type, pattern.validation, "node", properties))
    pattern.allowedValues?.let { values ->
      val matches =
        values.map { value ->
          when (value) {
            null -> CodeBlock.of("node.isNull")
            is Number ->
              CodeBlock.of(
                "(node.isNumber && node.decimalValue().compareTo(%S.toBigDecimal()) == 0)",
                value.toString(),
              )
            is Boolean -> CodeBlock.of("(node.isBoolean && node.booleanValue() == %L)", value)
            else -> CodeBlock.of("(node.isTextual && node.textValue() == %S)", value.toString())
          }
        }
      val condition = CodeBlock.builder()
      matches.forEachIndexed { index, match ->
        if (index > 0) condition.add(" || ")
        condition.add(match)
      }
      body.addStatement(
        "require(%L) { %S + name }",
        if (matches.isEmpty()) CodeBlock.of("false") else condition.build(),
        "Invalid pattern property value: ",
      )
    }
    body.addStatement("node.traverse(parser.codec).use { input ->")
    body.indent().addStatement("input.nextToken()")
    body.addStatement(
      "decoded = context.readValue(input, context.typeFactory.constructType(object : %T() {}.type))",
      ClassName("com.fasterxml.jackson.core.type", "TypeReference").parameterizedBy(typeName(pattern.type)),
    )
    body.unindent().addStatement("}")
    body.endControlFlow()
  }
  additional?.type?.let { type ->
    body.beginControlFlow("if (!matched)")
    body.add(patternNodeValidation(type, additional.validation, "node", properties))
    body.addStatement("node.traverse(parser.codec).use { input ->")
    body.indent().addStatement("input.nextToken()")
    body.addStatement(
      "decoded = context.readValue(input, context.typeFactory.constructType(object : %T() {}.type))",
      ClassName("com.fasterxml.jackson.core.type", "TypeReference").parameterizedBy(typeName(type)),
    )
    body.unindent().addStatement("}")
    body.endControlFlow()
  }
  if (closed) body.addStatement("require(matched) { %S + name }", "Additional properties are not allowed: ")
  if (declaredType != null) {
    body.addStatement("return node.traverse(parser.codec).use { input ->")
    body.indent().addStatement("input.nextToken()")
    body.addStatement(
      "context.readValue(input, context.typeFactory.constructType(object : %T() {}.type))",
      ClassName("com.fasterxml.jackson.core.type", "TypeReference").parameterizedBy(typeName(declaredType)),
    )
    body.unindent().addStatement("}")
  } else {
    if (additional?.type != null) {
      body.addStatement("return decoded")
    } else {
      body.addStatement("return if (matched) decoded else context.readTreeAsValue(node, Any::class.java)")
    }
  }
  val nullBody = CodeBlock.builder()
  if (patterns.isNotEmpty() || closed || additional?.type?.let { !properties.acceptsNull(it) } == true) {
    nullBody.addStatement("val name = context.parser.currentName()")
  }
  if (closed) nullBody.addStatement("var matched = false")
  patterns.forEach { pattern ->
    nullBody.beginControlFlow("if (%T(%S).containsMatchIn(name))", Regex::class, pattern.pattern)
    if (!properties.acceptsNull(pattern.type) || pattern.allowedValues?.contains(null) == false) {
      nullBody.addStatement(
        "throw %T.from(context, %S + name)",
        ClassName("com.fasterxml.jackson.databind", "JsonMappingException"),
        "Null is not allowed: ",
      )
    } else if (closed) {
      nullBody.addStatement("matched = true")
    }
    nullBody.endControlFlow()
  }
  if (closed) nullBody.addStatement("require(matched) { %S + name }", "Additional properties are not allowed: ")
  if (additional?.type != null && !properties.acceptsNull(additional.type)) {
    val matches = patterns.map { CodeBlock.of("%T(%S).containsMatchIn(name)", Regex::class, it.pattern) }
    val condition = CodeBlock.builder()
    matches.forEachIndexed { index, match ->
      if (index > 0) condition.add(" || ")
      condition.add(match)
    }
    nullBody.addStatement(
      "require(%L) { %S + name }",
      if (matches.isEmpty()) CodeBlock.of("false") else condition.build(),
      "Null is not allowed: ",
    )
  }
  nullBody.addStatement("return null")
  addType(
    TypeSpec
      .classBuilder(decoderType)
      .addKdoc("Decodes values satisfying every matching property pattern.\n")
      .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(any))
      .addAnnotation(
        AnnotationSpec
          .builder(
            Suppress::class,
          ).addMember("%S, %S", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
          .build(),
      ).addFunction(
        FunSpec
          .builder("deserialize")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter(
            "parser",
            JACKSON_JSON_PARSER,
          ).addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
          .returns(any)
          .addCode(body.build())
          .build(),
      ).addFunction(
        FunSpec
          .builder("getNullValue")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
          .returns(any)
          .addCode(nullBody.build())
          .build(),
      ).build(),
  )
  return decoderType
}

private fun patternNodeValidation(
  reference: GeneratedTypeRef,
  validation: Map<String, String>,
  node: String,
  properties: GeneratedModelProperties,
): CodeBlock =
  CodeBlock
    .builder()
    .apply {
      val type = properties.declarationType(reference)
      if (type.nullable) beginControlFlow("if (!%L.isNull)", node)
      val shape =
        when (type.kind) {
          GeneratedTypeRef.Kind.ARRAY -> "isArray"
          GeneratedTypeRef.Kind.MAP -> "isObject"
          GeneratedTypeRef.Kind.SCALAR ->
            when (type.name) {
              "string" -> "isTextual"
              "integer" -> "isIntegralNumber"
              "number" -> "isNumber"
              "boolean" -> "isBoolean"
              "nil" -> "isNull"
              else -> null
            }
          else -> null
        }
      if (shape != null) addStatement("require(%L.%L) { %S + name }", node, shape, "Invalid pattern property type: ")
      validation.forEach { (constraint, bound) ->
        val check =
          when (constraint) {
            "minLength" ->
              CodeBlock.of(
                "%L.textValue().codePointCount(0, %L.textValue().length) >= %L",
                node,
                node,
                bound,
              )
            "maxLength" ->
              CodeBlock.of(
                "%L.textValue().codePointCount(0, %L.textValue().length) <= %L",
                node,
                node,
                bound,
              )
            "pattern" -> CodeBlock.of("%T(%S).containsMatchIn(%L.textValue())", Regex::class, bound, node)
            "minimum" -> CodeBlock.of("%L.decimalValue() >= %S.toBigDecimal()", node, bound)
            "maximum" -> CodeBlock.of("%L.decimalValue() <= %S.toBigDecimal()", node, bound)
            "minItems" -> CodeBlock.of("%L.size() >= %L", node, bound)
            "maxItems" -> CodeBlock.of("%L.size() <= %L", node, bound)
            "uniqueItems" -> CodeBlock.of("%L.toList().distinct().size == %L.size()", node, node)
            else -> null
          }
        if (check != null) addStatement("require(%L) { %S + name }", check, "Invalid pattern property value: ")
      }
      if (type.kind in setOf(GeneratedTypeRef.Kind.ARRAY, GeneratedTypeRef.Kind.MAP) && type.arguments.isNotEmpty()) {
        beginControlFlow("for (item in %L)", node)
        add(patternNodeValidation(type.arguments.last(), emptyMap(), "item", properties))
        endControlFlow()
      }
      if (type.nullable) endControlFlow()
    }.build()
