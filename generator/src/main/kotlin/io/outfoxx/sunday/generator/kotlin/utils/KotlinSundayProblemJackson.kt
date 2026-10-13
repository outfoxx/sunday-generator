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
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec

/** Uses schema properties and ordinary bean decoding instead of Throwable's synthetic diagnostic fields. */
internal fun TypeSpec.Builder.addSundayProblemJackson(
  owner: ClassName,
  wireNames: Map<String, String>,
  nestedModelNames: Set<String>,
) {
  val autoDetect = ClassName("com.fasterxml.jackson.annotation", "JsonAutoDetect")
  val visibility = autoDetect.nestedClass("Visibility")
  addAnnotation(
    AnnotationSpec
      .builder(autoDetect)
      .addMember("getterVisibility = %T.NONE, isGetterVisibility = %T.NONE", visibility, visibility)
      .addMember("setterVisibility = %T.NONE, fieldVisibility = %T.NONE", visibility, visibility)
      .build(),
  )
  propertySpecs.replaceAll { property ->
    val wireName = wireNames[property.name]
    if (wireName == null || property.annotations.any { it.typeName == JACKSON_JSON_PROPERTY }) {
      property
    } else {
      property
        .toBuilder()
        .addAnnotation(
          AnnotationSpec
            .builder(JACKSON_JSON_PROPERTY)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
            .addMember("%S", wireName)
            .build(),
        ).build()
    }
  }
  val names = NameAllocator()
  (typeSpecs.mapNotNull { it.name } + nestedModelNames).distinct().forEach { names.newName(it) }
  val decoder = owner.nestedClass(names.newName("ProblemDeserializer"))
  val context = ClassName("com.fasterxml.jackson.databind", "DeserializationContext")
  val parser = ClassName("com.fasterxml.jackson.core", "JsonParser")
  val deserializer = ClassName("com.fasterxml.jackson.databind", "JsonDeserializer")
  val resolvable = ClassName("com.fasterxml.jackson.databind.deser", "ResolvableDeserializer")
  val contextual = ClassName("com.fasterxml.jackson.databind.deser", "ContextualDeserializer")
  val beanProperty = ClassName("com.fasterxml.jackson.databind", "BeanProperty")
  val factory = ClassName("com.fasterxml.jackson.databind.deser", "BeanDeserializerFactory")
  // Use the configured factory to retain Kotlin creators and mapper modules, while bypassing
  // ThrowableDeserializer's synthetic cause/stackTrace handling. Resolve and contextualize normally.
  annotations.removeIf { it.typeName == JACKSON_JSON_DESERIALIZE }
  addAnnotation(AnnotationSpec.builder(JACKSON_JSON_DESERIALIZE).addMember("using = %T::class", decoder).build())
  addType(
    TypeSpec
      .classBuilder(decoder)
      .addKdoc("Decodes schema fields without treating extension names as Java exception internals.\n")
      .superclass(deserializer.parameterizedBy(owner))
      .addSuperinterface(resolvable)
      .addSuperinterface(contextual)
      .addProperty(
        PropertySpec
          .builder("delegate", deserializer.parameterizedBy(ANY))
          .mutable()
          .addModifiers(KModifier.PRIVATE, KModifier.LATEINIT)
          .build(),
      ).addFunction(
        FunSpec
          .builder("resolve")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("context", context)
          .addStatement("val type = context.constructType(%T::class.java)", owner)
          .addStatement(
            "delegate = (context.factory as %T).buildBeanDeserializer(context, type, context.config.introspect(type))",
            factory,
          ).addStatement("(delegate as? %T)?.resolve(context)", resolvable)
          .build(),
      ).addFunction(
        FunSpec
          .builder("createContextual")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("context", context)
          .addParameter("property", beanProperty.copy(nullable = true))
          .returns(deserializer.parameterizedBy(STAR))
          .addStatement("return (delegate as? %T)?.createContextual(context, property) ?: delegate", contextual)
          .build(),
      ).addFunction(
        FunSpec
          .builder("deserialize")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("parser", parser)
          .addParameter("context", context)
          .returns(owner)
          .addStatement("return delegate.deserialize(parser, context) as %T", owner)
          .build(),
      ).build(),
  )
}
