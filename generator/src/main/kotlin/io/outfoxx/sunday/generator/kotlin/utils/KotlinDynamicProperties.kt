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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Attaches one native property-rule definition to codecs, constructor views, and mutable model boundaries. */
internal fun TypeSpec.Builder.addNativeDynamicProperties(
  owner: ClassName,
  model: GeneratedModel,
  properties: GeneratedModelProperties,
  types: BeanValidationTypes,
  extensionField: String?,
  typeName: (GeneratedTypeRef) -> TypeName,
  common: Boolean = false,
  isPatchField: (String) -> Boolean,
) {
  val patterns = properties.patternProperties(model)
  val additional = properties.additionalProperties(model)
  if (!common && patterns.isEmpty() && additional.isEmpty() && !properties.isClosed(model)) return
  val fields = properties.fields(model)
  val shapes = KotlinNativeShape(properties, types, typeName)

  fun schema(property: GeneratedModelProperty): CodeBlock {
    val effective =
      properties
        .fields(
          GeneratedModel("DynamicValue", GeneratedModel.Kind.OBJECT, properties = listOf(property)),
        ).single()
        .effective
    val annotation = KotlinNativeSchema.annotation(effective, properties, types)
    return CodeBlock.of("%T(%L)", types.schema, annotation?.members.orEmpty().joinToCode(", "))
  }
  val annotation =
    AnnotationSpec
      .builder(types.dynamicProperties)
      .addMember("declared = [%L]", fields.map { CodeBlock.of("%S", it.wireName) }.joinToCode(", "))
      .addMember(
        "patterns = [%L]",
        patterns
          .map { pattern ->
            val property =
              GeneratedModelProperty(
                "value",
                pattern.type,
                required = true,
                validation = pattern.validation,
                allowedValues = pattern.allowedValues,
              )
            CodeBlock.of(
              "%T(%S, %L, shape = %L)",
              types.dynamicProperties.nestedClass("Pattern"),
              pattern.pattern,
              schema(property),
              shapes.annotation(pattern.type),
            )
          }.joinToCode(", "),
      ).apply {
        if (common) {
          addMember(
            "properties = [%L]",
            properties
              .unionCommonProperties(model)
              .map { field ->
                CodeBlock.of(
                  "%T(%S, required = %L, schema = %L, shape = %L)",
                  types.dynamicProperties.nestedClass("Property"),
                  field.serializationName ?: field.name,
                  field.required,
                  schema(field),
                  shapes.annotation(
                    if (model.discriminator?.let { it == field.name || it == field.serializationName } == true) {
                      GeneratedTypeRef.scalar("any")
                    } else {
                      field.type
                    },
                  ),
                )
              }.joinToCode(", "),
          )
        }
        if (properties.isClosed(model)) addMember("closed = true")
        if (additional.isNotEmpty()) {
          addMember(
            "additional = [%L]",
            additional
              .map { declaration ->
                val reference = requireNotNull(declaration.type)
                CodeBlock.of(
                  "%T(%L, shape = %L)",
                  types.dynamicProperties.nestedClass("Value"),
                  schema(
                    GeneratedModelProperty(
                      "value",
                      reference,
                      required = true,
                      validation = declaration.validation,
                      allowedValues = declaration.allowedValues,
                    ),
                  ),
                  shapes.annotation(reference),
                )
              }.joinToCode(", "),
          )
        }
      }.build()
  val viewType = owner.nestedClass(if (common) "CommonPropertiesValidation" else "DynamicPropertiesValidation")
  val valuesType = MAP.parameterizedBy(STRING, ANY.copy(nullable = true))
  addType(
    TypeSpec
      .classBuilder(viewType)
      .addModifiers(if (common) KModifier.PUBLIC else KModifier.PRIVATE)
      .addKdoc("Native metadata used to validate wire property views without constructing an application model.\n")
      .apply {
        if (common) {
          addAnnotation(
            AnnotationSpec
              .builder(types.dynamicProperties.nestedClass("ObjectValue"))
              .addMember("fields = [], dynamic = %T::class", viewType)
              .build(),
          )
        }
      }.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNUSED_PARAMETER").build())
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter(
            ParameterSpec.builder("values", valuesType).addAnnotation(annotation).build(),
          ).build(),
      ).build(),
  )
  if (common) return
  addSuperinterface(types.dynamicModel)
  addFunction(
    FunSpec
      .builder("dynamicPropertySchema")
      .addModifiers(KModifier.OVERRIDE)
      .addKdoc("Supplies the concrete model's effective native property constraints.\n")
      .returns(types.dynamicProperties)
      .addStatement("return %T.from(%T::class.java)", types.dynamicProperties, viewType)
      .build(),
  )
  val constructorParameters =
    build()
      .primaryConstructor
      ?.parameters
      .orEmpty()
      .map { it.name }
      .toSet()

  fun values(constructor: Boolean): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        add("buildMap<String, Any?> {\n").indent()
        val parameters = if (constructor) constructorParameters else emptySet()
        fields.forEach { field ->
          val name = field.storage.name.kotlinIdentifierName
          val value =
            if (name in
              parameters
            ) {
              CodeBlock.of("%N", name)
            } else {
              CodeBlock.of("this@%L.%N", owner.simpleName, name)
            }
          if (isPatchField(name)) {
            beginControlFlow("if (%L !is %T<*>)", value, PATCH_OP.nestedClass("None"))
            addStatement("put(%S, (%L as? %T<*>)?.value)", field.wireName, value, PATCH_SET_OP)
            endControlFlow()
          } else if (!field.storage.required && !properties.acceptsNull(field.storage.type)) {
            beginControlFlow("if (%L != null)", value)
            addStatement("put(%S, %L)", field.wireName, value)
            endControlFlow()
          } else {
            addStatement("put(%S, %L)", field.wireName, value)
          }
        }
        extensionField?.let { addStatement("putAll(this@%L.%N)", owner.simpleName, it) }
        unindent().add("}")
      }.build()
  addFunction(
    FunSpec
      .builder("dynamicPropertyValues")
      .addModifiers(KModifier.OVERRIDE)
      .addKdoc("Supplies fresh participating wire values to native validation after every mutation.\n")
      .returns(valuesType)
      .addCode("return %L\n", values(false))
      .build(),
  )
  addInitializerBlock(
    CodeBlock
      .builder()
      .beginControlFlow("if (javaClass == %T::class.java)", owner)
      .add("%T.response(%L, %T::class.java)\n", types.modelValidation, values(true), viewType)
      .endControlFlow()
      .build(),
  )
}
