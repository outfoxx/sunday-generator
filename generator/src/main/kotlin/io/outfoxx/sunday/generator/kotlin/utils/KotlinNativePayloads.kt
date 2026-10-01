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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties

/** Supplies native metadata for values whose JVM classes cannot carry their source-schema constraints. */
internal class KotlinNativePayloads(
  private val properties: GeneratedModelProperties,
  private val types: BeanValidationTypes,
  private val modelFor: (GeneratedTypeRef) -> GeneratedModel?,
  private val annotatedType: (GeneratedTypeRef) -> TypeName,
  private val annotations: (GeneratedModelProperty) -> List<AnnotationSpec>,
  private val register: (ClassName, TypeSpec.Builder) -> Unit,
) {
  private val views = linkedMapOf<GeneratedTypeRef, ClassName>()

  fun register(
    reference: GeneratedTypeRef,
    name: ClassName,
  ) {
    if (reference in views) return
    views[reference] = name
    val field = property(reference)
    val valueType = annotatedType(reference)
    register(
      name,
      TypeSpec
        .classBuilder(name)
        .addModifiers(KModifier.PUBLIC)
        .addKdoc(
          "Native Bean Validation view of this schema. Retains the original value without copying or converting it.\n",
        ).primaryConstructor(
          FunSpec
            .constructorBuilder()
            .addParameter(
              ParameterSpec
                .builder("value", valueType)
                .addAnnotations(
                  (annotations(field) + AnnotationSpec.builder(types.acyclic).build()).map {
                    it.toBuilder().useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM).build()
                  },
                ).build(),
            ).build(),
        ).addProperty(
          PropertySpec
            .builder("value", valueType)
            .addKdoc("The application value to validate using request or response groups.\n")
            .initializer("value")
            .addAnnotations(annotations(field))
            .addAnnotation(
              AnnotationSpec.builder(types.acyclic).useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build(),
            ).build(),
        ),
    )
  }

  fun name(reference: GeneratedTypeRef): ClassName? = views[reference.copy(nullable = false)] ?: views[reference]

  fun callback(
    reference: GeneratedTypeRef,
    request: Boolean,
  ): CodeBlock {
    val mode = if (request) "request" else "response"
    val view = name(reference) ?: return CodeBlock.of("%T::%L", types.modelValidation, mode)
    return CodeBlock.of("{ %T.%L(it, %T::class.java) }", types.modelValidation, mode, view)
  }

  fun property(reference: GeneratedTypeRef): GeneratedModelProperty {
    val model = modelFor(reference)
    val property = GeneratedModelProperty("value", reference, required = true, validation = model?.validation.orEmpty())
    return properties
      .fields(
        GeneratedModel("ValidationValue", GeneratedModel.Kind.OBJECT, properties = listOf(property)),
      ).single()
      .effective
  }
}
