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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry

/** Separates ordinary construction from the defaults applied to absent decoded fields. */
internal fun addModelDecodingDefaults(
  models: Map<ClassName, Pair<GeneratedModel, TypeSpec.Builder>>,
  options: Set<KotlinTypeRegistry.Option>,
  properties: GeneratedModelProperties,
) {
  val annotated = mutableSetOf<ClassName>()

  fun bindWireNames(name: ClassName) {
    if (!annotated.add(name)) return
    val (model, builder) = models[name] ?: return
    val fields = properties.fields(model).associateBy { it.storage.name.kotlinIdentifierName }
    // Factory parameters alone do not reliably bind inherited accessors with custom Jackson deserializers.
    builder.propertySpecs.replaceAll { property ->
      val field = fields[property.name]
      if (field == null || field.wireName == property.name) {
        property
      } else {
        property
          .toBuilder()
          .addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_PROPERTY)
              .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
              .addMember("value = %S", field.wireName)
              .build(),
          ).build()
      }
    }
    (builder.build().superclass as? ClassName)?.let(::bindWireNames)
  }

  models.forEach { (name, entry) ->
    val (model, builder) = entry
    if (model.kind != GeneratedModel.Kind.OBJECT || model.patchable) return@forEach
    val type = builder.build()
    val constructor = type.primaryConstructor ?: return@forEach
    val fields = properties.fields(model).associateBy { it.storage.name.kotlinIdentifierName }
    val defaulted =
      constructor.parameters
        .filter { parameter ->
          val field = fields[parameter.name] ?: return@filter false
          !field.effective.required &&
            field.effective.defaultValue != null &&
            parameter.defaultValue != null &&
            parameter.defaultValue != CodeBlock.of("null") &&
            (parameter.type as? ParameterizedTypeName)?.rawType !in setOf(PATCH_OP, UPDATE_OP)
        }.mapTo(mutableSetOf()) { it.name }
    val extensionParameters =
      constructor.parameters
        .filter { parameter ->
          parameter.annotations.any { it.typeName == ClassName("com.fasterxml.jackson.annotation", "JsonAnySetter") }
        }.mapTo(mutableSetOf()) { it.name }
    if (defaulted.isEmpty() && extensionParameters.isEmpty()) return@forEach
    // Keep extension storage in data-class copy(), but never bind its implementation name as a JSON field.
    val decodedParameters = constructor.parameters.filter { it.name !in extensionParameters }
    if (KotlinTypeRegistry.Option.JacksonAnnotations in options) bindWireNames(name)
    builder.primaryConstructor(
      constructor
        .toBuilder()
        .apply {
          parameters.replaceAll { parameter ->
            parameter
              .toBuilder()
              .apply {
                if (parameter.name in defaulted) defaultValue("null")
                if (parameter.name in extensionParameters) annotations.clear()
              }.build()
          }
          annotations.removeIf { it.typeName == JACKSON_JSON_CREATOR }
        }.build(),
    )
    if (type.modifiers.any { it in setOf(KModifier.ABSTRACT, KModifier.SEALED) }) return@forEach

    val companion = type.typeSpecs.firstOrNull { it.isCompanion }
    val names = NameAllocator()
    (type.funSpecs + companion?.funSpecs.orEmpty()).forEach { names.newName(it.name) }
    (type.propertySpecs + companion?.propertySpecs.orEmpty()).forEach { names.newName(it.name) }
    val factory =
      FunSpec
        .builder(names.newName("fromJson"))
        .addKdoc("Creates a decoded model, applying schema defaults only to absent optional fields.\n")
        .addAnnotation(JvmStatic::class)
        .apply {
          if (KotlinTypeRegistry.Option.JacksonAnnotations in options) {
            addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_CREATOR)
                .addMember("mode = %T.PROPERTIES", JACKSON_JSON_CREATOR.nestedClass("Mode"))
                .build(),
            )
          }
          decodedParameters.forEach { parameter ->
            addParameter(
              parameter
                .toBuilder(type = parameter.type.withoutValidationAnnotations())
                .apply {
                  // Static Jackson factories delegate to the validated constructor; Bean Validation ignores them.
                  annotations.removeAll { it.isValidationAnnotation() }
                  annotations.replaceAll { it.toBuilder().useSiteTarget(null).build() }
                }.build(),
            )
          }
        }.returns(name)
        .addStatement(
          "return %T(%L)",
          name,
          CodeBlock
            .builder()
            .apply {
              decodedParameters.forEachIndexed { index, parameter ->
                if (index > 0) add(", ")
                add("%N = %N", parameter.name, parameter.name)
              }
            }.build(),
        ).build()
    val updatedCompanion = (companion?.toBuilder() ?: TypeSpec.companionObjectBuilder()).addFunction(factory).build()
    if (companion == null) {
      builder.addType(updatedCompanion)
    } else {
      builder.typeSpecs[builder.typeSpecs.indexOf(companion)] = updatedCompanion
    }
  }
}

private fun AnnotationSpec.isValidationAnnotation(): Boolean =
  (typeName as? ClassName)?.packageName?.let { packageName ->
    packageName.startsWith("io.outfoxx.sunday.validation.") ||
      packageName.startsWith("javax.validation") ||
      packageName.startsWith("jakarta.validation")
  } == true

private fun TypeName.withoutValidationAnnotations(): TypeName {
  val unannotated =
    if (this is ParameterizedTypeName) {
      rawType.parameterizedBy(typeArguments.map { it.withoutValidationAnnotations() })
    } else {
      this
    }
  return unannotated.copy(nullable = isNullable, annotations = annotations.filterNot { it.isValidationAnnotation() })
}
