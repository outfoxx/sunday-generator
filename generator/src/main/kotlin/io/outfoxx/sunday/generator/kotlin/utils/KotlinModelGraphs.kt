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
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Supplies a schema-neutral field view for the native cycle constraint. */
internal fun addNativeModelGraphs(
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  properties: GeneratedModelProperties,
  types: BeanValidationTypes,
  typeName: (GeneratedTypeRef) -> TypeName,
) {
  val anyGetter = ClassName("com.fasterxml.jackson.annotation", "JsonAnyGetter")

  fun extensionField(name: ClassName): String? {
    val type = models[name]?.second?.build() ?: return null
    return type.propertySpecs.firstOrNull { field -> field.annotations.any { it.typeName == anyGetter } }?.name
      ?: (type.superclass as? ClassName)?.let(::extensionField)
  }

  fun isPatchField(
    name: ClassName,
    field: String,
  ): Boolean {
    val type = models[name]?.second?.build() ?: return false
    val storage =
      type.propertySpecs.firstOrNull { it.name == field }?.type
        ?: type.primaryConstructor
          ?.parameters
          ?.firstOrNull { it.name == field }
          ?.type
        ?: return (type.superclass as? ClassName)?.let { isPatchField(it, field) } ?: false
    return (storage as? ParameterizedTypeName)?.rawType in setOf(PATCH_OP, UPDATE_OP)
  }

  fun problemStatus(name: ClassName): CodeBlock? =
    when (val parent = models[name]?.second?.build()?.superclass) {
      QUARKUS_HTTP_PROBLEM -> CodeBlock.of("this.statusCode")
      ZALANDO_ABSTRACT_THROWABLE_PROBLEM -> CodeBlock.of("this.status?.statusCode")
      else -> (parent as? ClassName)?.let { problemStatus(it) }
    }
  models.forEach { (name, entry) ->
    val (model, builder) = entry
    if (properties.hasUnionCommonRules(model)) {
      builder.addNativeDynamicProperties(name, model, properties, types, null, typeName, common = true) { false }
    }
    if (model.kind != GeneratedModel.Kind.OBJECT) return@forEach
    builder.addSuperinterface(types.serializableModel)
    builder.propertySpecs.replaceAll { property ->
      val type = property.type as? ParameterizedTypeName
      if (type?.rawType != MAP || property.annotations.none { it.typeName == anyGetter }) {
        property
      } else {
        val value = type.typeArguments.last()
        if (value.copy(nullable = false, annotations = emptyList()) == ANY) {
          property
            .toBuilder()
            .apply {
              for (mode in listOf(types.requestMode, types.responseMode)) {
                addAnnotation(
                  AnnotationSpec
                    .builder(types.cascadedValues)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                    .addMember("mode = %T::class, groups = [%T::class]", mode, mode)
                    .build(),
                )
              }
            }.build()
        } else {
          property
            .toBuilder(
              type =
                MAP.parameterizedBy(
                  type.typeArguments.first(),
                  value.copy(annotations = value.annotations + AnnotationSpec.builder(types.valid).build()),
                ),
            ).build()
        }
      }
    }
    // Reusable discriminator interfaces do not own storage; their concrete payloads supply the field view.
    if (builder.build().kind == TypeSpec.Kind.INTERFACE) return@forEach
    builder.addNativeDynamicProperties(name, model, properties, types, extensionField(name), typeName) {
      isPatchField(name, it)
    }
    val shapes = KotlinNativeShape(properties, types, typeName)
    builder.addAnnotation(
      AnnotationSpec
        .builder(types.dynamicProperties.nestedClass("ObjectValue"))
        .addMember(
          "fields = [%L]",
          properties
            .fields(model)
            .map { field ->
              CodeBlock.of(
                "%T(%S, %S, required = %L, shape = %L)",
                types.dynamicProperties.nestedClass("Field"),
                field.storage.name.kotlinIdentifierName,
                field.wireName,
                field.effective.required && !model.patchable,
                shapes.annotation(field.effective.type),
              )
            }.joinToCode(", "),
        ).apply {
          if (builder.superinterfaces.containsKey(types.dynamicModel)) {
            addMember("dynamic = %T::class", name.nestedClass("DynamicPropertiesValidation"))
          }
        }.build(),
    )
    val fields =
      properties
        .fields(model)
        .map { field ->
          val value =
            field.storage.name
              .takeIf { it == "status" }
              ?.let { problemStatus(name) }
              ?: CodeBlock.of("this.%N", field.storage.name.kotlinIdentifierName)
          if (isPatchField(name, field.storage.name.kotlinIdentifierName)) {
            CodeBlock.of(
              "if (%L is %T<*>) null else %S to (%L as? %T<*>)?.value",
              value,
              PATCH_OP.nestedClass("None"),
              field.wireName,
              value,
              PATCH_SET_OP,
            )
          } else if (!field.storage.required && !properties.acceptsNull(field.storage.type)) {
            CodeBlock.of("%L?.let { %T(%S, it) }", value, Pair::class, field.wireName)
          } else {
            CodeBlock.of("%S to %L", field.wireName, value)
          }
        }
    builder.addFunction(
      FunSpec
        .builder("validationFields")
        .addModifiers(KModifier.OVERRIDE)
        .addKdoc("Exposes current wire fields to native serializability validation without copying models.\n")
        .returns(MAP.parameterizedBy(STRING, ANY.copy(nullable = true)))
        .addStatement(
          "return listOfNotNull<Pair<String, Any?>>(%L).toMap()%L",
          fields.joinToCode(", "),
          extensionField(name)?.let { CodeBlock.of(" + this.%N", it) } ?: CodeBlock.of(""),
        ).build(),
    )
  }
}
