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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
      if (property.annotations.any { it.typeName == ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter") }) {
        body.addStatement("putAll(%N)", property.name)
      }
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
