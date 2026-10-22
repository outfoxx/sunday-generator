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
  private val source: io.outfoxx.sunday.generator.ir.GeneratedSourceSpec,
  private val properties: GeneratedModelProperties,
  private val types: BeanValidationTypes,
  private val modelFor: (GeneratedTypeRef) -> GeneratedModel?,
  private val annotatedType: (GeneratedTypeRef) -> TypeName,
  private val annotations: (GeneratedModelProperty) -> List<AnnotationSpec>,
  private val register: (ClassName, TypeSpec.Builder) -> Unit,
) {
  private val views = linkedMapOf<Pair<GeneratedTypeRef, String?>, ClassName>()

  fun register(
    reference: GeneratedTypeRef,
    name: ClassName,
    shapeType: (GeneratedTypeRef) -> TypeName = annotatedType,
    commonRules: Boolean = false,
    placement: String? = null,
  ) {
    val key = key(reference) to placement
    if (key in views) return
    views[key] = name
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
        ).apply {
          if (commonRules) {
            addNativeDynamicProperties(
              name,
              requireNotNull(modelFor(reference)),
              properties,
              types,
              null,
              shapeType,
              common = true,
            ) {
              false
            }
          }
        }.addProperty(
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

  private fun key(reference: GeneratedTypeRef): GeneratedTypeRef = bindSource(reference).copy(nullable = false)

  private fun bindSource(reference: GeneratedTypeRef): GeneratedTypeRef =
    reference.copy(
      source =
        if (reference.kind ==
          GeneratedTypeRef.Kind.NAMED
        ) {
          modelFor(reference)?.source ?: reference.source
        } else {
          reference.source
        },
      arguments = reference.arguments.map(::bindSource),
    )

  fun name(
    reference: GeneratedTypeRef,
    placement: String? = null,
  ): ClassName? = views[key(reference) to placement] ?: views[key(reference) to null]

  fun callback(
    reference: GeneratedTypeRef,
    request: Boolean,
    placement: String? = null,
  ): CodeBlock {
    val mode = if (request) "request" else "response"
    val view = name(reference, placement) ?: return CodeBlock.of("%T::%L", types.modelValidation, mode)
    return CodeBlock.of("{ %T.%L(it, %T::class.java) }", types.modelValidation, mode, view)
  }

  fun property(reference: GeneratedTypeRef): GeneratedModelProperty {
    val model = modelFor(reference)
    val property = GeneratedModelProperty("value", reference, required = true, validation = model?.validation.orEmpty())
    return properties
      .fields(
        GeneratedModel(
          "ValidationValue",
          GeneratedModel.Kind.OBJECT,
          source = model?.source ?: source,
          properties = listOf(property),
        ),
      ).single()
      .effective
  }
}
