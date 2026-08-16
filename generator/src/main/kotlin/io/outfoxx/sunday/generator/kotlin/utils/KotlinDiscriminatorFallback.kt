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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback

internal fun GeneratedDiscriminatorFallback.kotlinFallbackTypeSpec(
  fallbackTypeName: ClassName,
  hierarchyTypeName: ClassName,
  hierarchyIsClass: Boolean,
  hierarchyDeclaresProperties: Boolean,
  propertyTypeName: (GeneratedModelProperty) -> TypeName,
): TypeSpec.Builder {
  val exposedProperties =
    buildList {
      if (!externallyDiscriminated) {
        add(discriminatorProperty)
      }
      addAll(baseProperties)
    }
  val constructor =
    FunSpec
      .constructorBuilder()
      .apply {
        exposedProperties.forEach { property ->
          addParameter(property.kotlinFallbackParameter(propertyTypeName(property)))
        }
        addParameter("rawBody", OBJECT_NODE)
      }.build()
  return TypeSpec
    .classBuilder(fallbackTypeName)
    .addModifiers(KModifier.PUBLIC)
    .primaryConstructor(constructor)
    .addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_DESERIALIZE)
        .addMember("using = %T::class", fallbackTypeName.nestedClass("Deserializer"))
        .build(),
    ).addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_SERIALIZE)
        .addMember("using = %T::class", fallbackTypeName.nestedClass("Serializer"))
        .build(),
    ).apply {
      if (hierarchyIsClass) {
        superclass(hierarchyTypeName)
        exposedProperties.forEach { property ->
          addSuperclassConstructorParameter("%N", property.name.kotlinIdentifierName)
        }
      } else {
        addSuperinterface(hierarchyTypeName)
        exposedProperties.forEach { property ->
          val modifiers =
            if (hierarchyDeclaresProperties) {
              arrayOf(KModifier.PUBLIC, KModifier.OVERRIDE)
            } else {
              arrayOf(KModifier.PUBLIC)
            }
          addProperty(
            PropertySpec
              .builder(property.name.kotlinIdentifierName, propertyTypeName(property), *modifiers)
              .initializer(property.name.kotlinIdentifierName)
              .build(),
          )
        }
      }
      addProperty(
        PropertySpec
          .builder("rawBody", OBJECT_NODE, KModifier.PUBLIC)
          .initializer("rawBody")
          .build(),
      )
      addType(projectionType(exposedProperties, propertyTypeName))
      addType(deserializerType(fallbackTypeName, exposedProperties))
      addType(serializerType(fallbackTypeName))
    }
}

private fun GeneratedModelProperty.kotlinFallbackParameter(typeName: TypeName): ParameterSpec =
  ParameterSpec
    .builder(name.kotlinIdentifierName, typeName)
    .apply {
      if (serializationName != null || name.kotlinIdentifierName != name) {
        addAnnotation(
          AnnotationSpec
            .builder(JACKSON_JSON_PROPERTY)
            .addMember("value = %S", serializationName ?: name)
            .build(),
        )
      }
      if (!required || type.nullable) {
        defaultValue("null")
      }
    }.build()

private fun projectionType(
  properties: List<GeneratedModelProperty>,
  propertyTypeName: (GeneratedModelProperty) -> TypeName,
): TypeSpec {
  val constructor =
    FunSpec
      .constructorBuilder()
      .apply {
        properties.forEach { property ->
          addParameter(property.kotlinFallbackParameter(propertyTypeName(property)))
        }
      }.build()
  return TypeSpec
    .classBuilder("Projection")
    .addModifiers(KModifier.PRIVATE)
    .apply {
      if (properties.isNotEmpty()) {
        addModifiers(KModifier.DATA)
      }
    }.addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_IGNORE_PROPERTIES)
        .addMember("ignoreUnknown = true")
        .build(),
    ).primaryConstructor(constructor)
    .apply {
      properties.forEach { property ->
        addProperty(
          PropertySpec
            .builder(property.name.kotlinIdentifierName, propertyTypeName(property), KModifier.PUBLIC)
            .initializer(property.name.kotlinIdentifierName)
            .build(),
        )
      }
    }.build()
}

private fun deserializerType(
  fallbackTypeName: ClassName,
  properties: List<GeneratedModelProperty>,
): TypeSpec {
  val deserialize =
    FunSpec
      .builder("deserialize")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("parser", JACKSON_JSON_PARSER)
      .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
      .returns(fallbackTypeName)
      .addStatement("val tree = parser.codec.readTree<%T>(parser)", OBJECT_NODE)
      .addStatement("val projection = parser.codec.treeToValue(tree, Projection::class.java)")
      .addStatement(
        "return %T(%L)",
        fallbackTypeName,
        properties
          .map { property -> "projection.${property.name.kotlinIdentifierName}" }
          .plus("tree")
          .joinToString(", "),
      ).build()
  return TypeSpec
    .classBuilder("Deserializer")
    .addModifiers(KModifier.PUBLIC)
    .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(fallbackTypeName))
    .addFunction(deserialize)
    .build()
}

private fun serializerType(fallbackTypeName: ClassName): TypeSpec {
  val serialize =
    FunSpec
      .builder("serialize")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("value", fallbackTypeName)
      .addParameter("generator", JACKSON_JSON_GENERATOR)
      .addParameter("provider", JACKSON_SERIALIZER_PROVIDER)
      .addStatement("generator.writeTree(value.rawBody)")
      .build()
  val serializeWithType =
    FunSpec
      .builder("serializeWithType")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("value", fallbackTypeName)
      .addParameter("generator", JACKSON_JSON_GENERATOR)
      .addParameter("provider", JACKSON_SERIALIZER_PROVIDER)
      .addParameter("typeSerializer", JACKSON_TYPE_SERIALIZER)
      .addStatement("generator.writeTree(value.rawBody)")
      .build()
  return TypeSpec
    .classBuilder("Serializer")
    .addModifiers(KModifier.PUBLIC)
    .superclass(JACKSON_JSON_SERIALIZER.parameterizedBy(fallbackTypeName))
    .addFunction(serialize)
    .addFunction(serializeWithType)
    .build()
}
