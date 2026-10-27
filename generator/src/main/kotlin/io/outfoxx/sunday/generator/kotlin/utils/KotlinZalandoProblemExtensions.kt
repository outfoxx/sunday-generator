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
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Exposes declared inherited fields through Zalando without competing Jackson any-getters. */
internal fun addZalandoProblemExtensions(
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  properties: GeneratedModelProperties,
) {
  fun isProblem(name: ClassName): Boolean =
    when (val parent = models[name]?.second?.build()?.superclass) {
      ZALANDO_ABSTRACT_THROWABLE_PROBLEM -> true
      is ClassName -> isProblem(parent)
      else -> false
    }
  models.filterKeys(::isProblem).forEach { (_, entry) ->
    val (model, builder) = entry
    if (builder.build().superclass == ZALANDO_ABSTRACT_THROWABLE_PROBLEM) {
      builder.build().tag(ProblemExtensionWriter::class)?.let { writer ->
        val type = builder.build()
        val constructor = requireNotNull(type.primaryConstructor)
        val names = NameAllocator()
        val memberNames = type.propertySpecs.map { it.name } + constructor.parameters.map { it.name }
        memberNames.distinct().forEach { names.newName(it) }
        val storage = names.newName("problemParameters")
        val map = MUTABLE_MAP.parameterizedBy(STRING, ANY.copy(nullable = true))
        // Zalando exposes an unmodifiable view; keep the same map passed to its constructor for validated writes.
        builder.primaryConstructor(
          constructor
            .toBuilder()
            .apply {
              modifiers.clear()
              addModifiers(KModifier.PRIVATE)
              annotations.clear()
              parameters.add(0, ParameterSpec.builder(storage, map).build())
            }.build(),
        )
        builder.addFunction(
          constructor
            .toBuilder()
            .apply {
              modifiers.clear()
              addModifiers(if (KModifier.SEALED in type.modifiers) KModifier.PROTECTED else KModifier.PUBLIC)
              addKdoc("Creates a problem with its declared fields.\n")
              parameters.replaceAll { parameter ->
                parameter
                  .toBuilder()
                  .apply {
                    annotations.replaceAll { it.toBuilder().useSiteTarget(null).build() }
                  }.build()
              }
              if (type.typeSpecs.flatMap { it.funSpecs }.none { function ->
                  function.annotations.any { it.typeName == JACKSON_JSON_CREATOR }
                }
              ) {
                addAnnotation(JACKSON_JSON_CREATOR)
              }
              callThisConstructor(
                listOf(CodeBlock.of("linkedMapOf()")) + constructor.parameters.map { CodeBlock.of("%N", it.name) },
              )
            }.build(),
        )
        builder.addSuperclassConstructorParameter("%N", storage)
        builder.addProperty(PropertySpec.builder(storage, map, KModifier.PRIVATE).initializer("%N", storage).build())
        builder.addFunction(
          FunSpec
            .builder(writer.name)
            .addModifiers(KModifier.PROTECTED)
            .addKdoc("Stores a validated extension in the runtime problem map.\n")
            .addParameter("name", STRING)
            .addParameter("value", ANY.copy(nullable = true))
            .addStatement("%N[name] = value", storage)
            .build(),
        )
        builder.addFunction(
          FunSpec
            .builder(names.newName("problemExtensionParameters"))
            .addModifiers(KModifier.PRIVATE)
            .addAnnotation(ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter"))
            .addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_INCLUDE)
                .addMember(
                  "value = %T.ALWAYS, content = %T.ALWAYS",
                  JACKSON_JSON_INCLUDE_INCLUDE,
                  JACKSON_JSON_INCLUDE_INCLUDE,
                ).build(),
            ).returns(MAP.parameterizedBy(STRING, ANY.copy(nullable = true)))
            .addStatement("return super.getParameters()")
            .build(),
        )
        builder.initializerIndex = builder.propertySpecs.size
      }
      // Zalando suppresses about:blank; a source schema can require the type member explicitly.
      builder.addFunction(
        FunSpec
          .builder("getType")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Retains the source schema's explicit problem type URI.\n")
          .addAnnotation(
            AnnotationSpec
              .builder(ClassName("com.fasterxml.jackson.databind.annotation", "JsonSerialize"))
              .addMember(
                "converter = %T::class",
                ClassName("com.fasterxml.jackson.databind.util", "Converter", "None"),
              ).build(),
          ).returns(ClassName("java.net", "URI"))
          .addStatement("return super.getType()")
          .build(),
      )
    }
    val fields = properties.fields(model).associateBy { it.storage.name.kotlinIdentifierName }
    fields.values.firstOrNull { it.wireName == "detail" }?.let { field ->
      builder.addFunction(
        FunSpec
          .builder("getDetail")
          .addModifiers(KModifier.OVERRIDE)
          .addKdoc("Preserves empty detail text while applying this schema's null inclusion rule.\n")
          .addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_INCLUDE)
              .addMember(
                "%T.%L",
                JACKSON_JSON_INCLUDE_INCLUDE,
                if (properties.acceptsNull(field.effective.type)) "ALWAYS" else "NON_NULL",
              ).build(),
          ).returns(STRING.copy(nullable = true))
          .addStatement("return super.getDetail()")
          .build(),
      )
    }
    builder.propertySpecs.replaceAll { property ->
      val field = fields[property.name]
      if (field != null && properties.acceptsNull(field.effective.type)) {
        property
          .toBuilder()
          .addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_INCLUDE)
              .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
              .addMember("%T.ALWAYS", JACKSON_JSON_INCLUDE_INCLUDE)
              .build(),
          ).build()
      } else {
        property
      }
    }
    val body =
      CodeBlock
        .builder()
        .beginControlFlow("return buildMap")
        .addStatement("putAll(super.getParameters())")
    builder.propertySpecs.forEach { property ->
      val field = fields[property.name]
      if (field != null && field.wireName !in setOf("type", "title", "status", "detail", "instance")) {
        val omitNull = !field.effective.required && !properties.acceptsNull(field.effective.type)
        if (omitNull) body.beginControlFlow("if (%N != null)", property.name)
        body.addStatement("put(%S, %N)", field.wireName, property.name)
        if (omitNull) body.endControlFlow()
      }
    }
    body.endControlFlow()
    builder.addFunction(
      FunSpec
        .builder("getParameters")
        .addModifiers(KModifier.OVERRIDE)
        .addKdoc("Returns inherited and declared problem extensions using their wire names.\n")
        .addAnnotation(
          AnnotationSpec
            .builder(
              ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter"),
            ).addMember("enabled = false")
            .build(),
        ).addAnnotation(JACKSON_JSON_IGNORE)
        .returns(MAP.parameterizedBy(STRING, ANY.copy(nullable = true)))
        .addCode(body.build())
        .build(),
    )
  }
}
