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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedModel

internal fun GeneratedModel.tolerantEnumTypeSpec(
  className: ClassName,
  entries: List<KotlinEnumEntry>,
  jacksonAnnotations: Boolean,
): TypeSpec.Builder {
  val fallbackValue = unknownValue ?: genError("Kotlin tolerant enum '$name' is missing its unknown value")
  val fallbackEntry =
    entries.singleOrNull { entry -> entry.value == fallbackValue }
      ?: genError("Kotlin tolerant enum '$name' unknown value '$fallbackValue' does not match any enum value")

  return TypeSpec
    .classBuilder(className)
    .addModifiers(KModifier.PUBLIC, KModifier.SEALED)
    .primaryConstructor(
      FunSpec
        .constructorBuilder()
        .addModifiers(KModifier.PROTECTED)
        .addParameter("wireValue", STRING)
        .build(),
    ).addProperty(
      PropertySpec
        .builder("wireValue", STRING)
        .initializer("wireValue")
        .apply {
          if (jacksonAnnotations) {
            addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_VALUE)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                .build(),
            )
          }
        }.build(),
    ).addFunction(
      FunSpec
        .builder("toString")
        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
        .returns(STRING)
        .addStatement("return wireValue")
        .build(),
    ).apply {
      entries
        .filterNot { entry -> entry == fallbackEntry }
        .forEach { entry ->
          addType(
            TypeSpec
              .objectBuilder(entry.name)
              .addModifiers(KModifier.PUBLIC, KModifier.DATA)
              .superclass(className)
              .addSuperclassConstructorParameter("%S", entry.value)
              .build(),
          )
        }

      addType(
        TypeSpec
          .classBuilder(fallbackEntry.name)
          .addModifiers(KModifier.PUBLIC, KModifier.DATA)
          .primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addParameter("rawValue", STRING)
              .build(),
          ).addProperty(
            PropertySpec
              .builder("rawValue", STRING)
              .initializer("rawValue")
              .build(),
          ).superclass(className)
          .addSuperclassConstructorParameter("rawValue")
          .build(),
      )

      addType(tolerantEnumCompanionType(entries, fallbackEntry, className, jacksonAnnotations))
    }
}

private fun tolerantEnumCompanionType(
  entries: List<KotlinEnumEntry>,
  fallbackEntry: KotlinEnumEntry,
  className: ClassName,
  jacksonAnnotations: Boolean,
): TypeSpec =
  TypeSpec
    .companionObjectBuilder()
    .addFunction(
      FunSpec
        .builder("fromValue")
        .apply {
          if (jacksonAnnotations) {
            addAnnotation(JACKSON_JSON_CREATOR)
          }
        }.addAnnotation(ClassName("kotlin.jvm", "JvmStatic"))
        .addParameter("rawValue", STRING)
        .returns(className)
        .beginControlFlow("return when (rawValue)")
        .apply {
          entries
            .filterNot { entry -> entry == fallbackEntry }
            .forEach { entry -> addStatement("%S -> %L", entry.value, entry.name) }
        }.addStatement("else -> %L(rawValue)", fallbackEntry.name)
        .endControlFlow()
        .build(),
    ).build()
