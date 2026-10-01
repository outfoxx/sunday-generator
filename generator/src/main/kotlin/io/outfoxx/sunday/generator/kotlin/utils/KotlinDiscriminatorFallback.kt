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
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback

internal fun GeneratedDiscriminatorFallback.kotlinFallbackTypeSpec(
  fallbackTypeName: ClassName,
  hierarchyTypeName: ClassName,
  hierarchyIsClass: Boolean,
  hierarchyDeclaresProperties: Boolean,
  validation: BeanValidationTypes? = null,
  annotations: (
    GeneratedModelProperty,
    AnnotationSpec.UseSiteTarget?,
  ) -> List<AnnotationSpec> = { _, _ -> emptyList() },
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
          addParameter(
            property
              .kotlinFallbackParameter(propertyTypeName(property), !hierarchyIsClass)
              .toBuilder()
              .addAnnotations(annotations(property, AnnotationSpec.UseSiteTarget.PARAM.takeUnless { hierarchyIsClass }))
              .build(),
          )
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
          addSuperclassConstructorParameter(
            "%N = %N",
            property.name.kotlinIdentifierName,
            property.name.kotlinIdentifierName,
          )
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
              .addAnnotations(annotations(property, AnnotationSpec.UseSiteTarget.GET))
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
      addType(deserializerType(fallbackTypeName, exposedProperties, validation))
      addType(serializerType(fallbackTypeName, exposedProperties, validation))
      validation?.let {
        addSuperinterface(it.serializableModel)
        addFunction(
          FunSpec
            .builder("validationFields")
            .addModifiers(KModifier.OVERRIDE)
            .addKdoc(
              "Exposes participating wire fields, including retained unknown payload members, to native validation.\n",
            ).returns(MAP.parameterizedBy(STRING, ANY.copy(nullable = true)))
            .addCode(
              CodeBlock
                .builder()
                .add("return buildMap {\n")
                .apply {
                  indent()
                  addStatement(
                    "putAll(rawBody.properties().filter { it.key !in setOf<String>(%L) }.associate { it.key to it.value })",
                    exposedProperties.map { CodeBlock.of("%S", it.serializationName ?: it.name) }.joinToCode(", "),
                  )
                  exposedProperties.forEach { property ->
                    val optional = !property.required && !property.type.nullable
                    if (optional) {
                      beginControlFlow(
                        "if (this@%L.%N != null)",
                        fallbackTypeName.simpleName,
                        property.name.kotlinIdentifierName,
                      )
                    }
                    addStatement(
                      "put(%S, this@%L.%N)",
                      property.serializationName ?: property.name,
                      fallbackTypeName.simpleName,
                      property.name.kotlinIdentifierName,
                    )
                    if (optional) endControlFlow()
                  }
                  unindent()
                }.add("}\n")
                .build(),
            ).build(),
        )
        addInitializerBlock(
          CodeBlock
            .builder()
            .add(KotlinNativeSchema.constructor(fallbackTypeName, constructor.parameters, it))
            .addStatement("%T.graph(this)", it.modelValidation)
            .build(),
        )
      }
    }
}

private fun GeneratedModelProperty.kotlinFallbackParameter(
  typeName: TypeName,
  declaresProperty: Boolean = true,
): ParameterSpec =
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
      if (!type.nullable || allowedValues?.contains(null) == false) {
        addAnnotation(
          AnnotationSpec
            .builder(ClassName("com.fasterxml.jackson.annotation", "JsonSetter"))
            .apply { if (declaresProperty) useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM) }
            .addMember("nulls = %T.FAIL", ClassName("com.fasterxml.jackson.annotation", "Nulls"))
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
  validation: BeanValidationTypes?,
): TypeSpec {
  val deserialize =
    FunSpec
      .builder("deserialize")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("parser", JACKSON_JSON_PARSER)
      .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
      .returns(fallbackTypeName)
      .addStatement("val tree = context.readTree(parser) as %T", OBJECT_NODE)
      .addStatement("val projection = parser.codec.treeToValue(tree, Projection::class.java)")
      .apply { if (validation != null) beginControlFlow("try") }
      .addStatement(
        "return %T(%L)",
        fallbackTypeName,
        properties
          .map { property -> "projection.${property.name.kotlinIdentifierName}" }
          .plus("tree")
          .joinToString(", "),
      ).apply {
        if (validation != null) {
          nextControlFlow("catch (error: %T)", validation.constraintViolationException)
          addStatement("throw %T.from(parser, error.message, error)", JACKSON_JSON_MAPPING_EXCEPTION)
          endControlFlow()
        }
      }.build()
  return TypeSpec
    .classBuilder("Deserializer")
    .addModifiers(KModifier.PUBLIC)
    .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(fallbackTypeName))
    .addFunction(deserialize)
    .build()
}

private fun serializerType(
  fallbackTypeName: ClassName,
  properties: List<GeneratedModelProperty>,
  validation: BeanValidationTypes?,
): TypeSpec {
  val serialize =
    FunSpec
      .builder("serialize")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("value", fallbackTypeName)
      .addParameter("generator", JACKSON_JSON_GENERATOR)
      .addParameter("provider", JACKSON_SERIALIZER_PROVIDER)
      .apply {
        validation?.let { addStatement("%T.response(value)", it.modelValidation) }
        addStatement("generator.writeStartObject()")
        val names = properties.map { CodeBlock.of("%S", it.serializationName ?: it.name) }.joinToCode(", ")
        addStatement("val declared = setOf<String>(%L)", names)
        beginControlFlow("for ((name, node) in value.rawBody.properties())")
        beginControlFlow("if (name !in declared)")
        addStatement("generator.writeFieldName(name)")
        addStatement("generator.writeTree(node)")
        endControlFlow()
        endControlFlow()
        properties.forEach { property ->
          val wireName = property.serializationName ?: property.name
          if (!property.required) {
            if (property.type.nullable && property.allowedValues?.contains(null) != false) {
              beginControlFlow(
                "if (value.%N != null || value.rawBody.has(%S))",
                property.name.kotlinIdentifierName,
                wireName,
              )
            } else {
              beginControlFlow("if (value.%N != null)", property.name.kotlinIdentifierName)
            }
          }
          addStatement(
            "provider.defaultSerializeField(%S, value.%N, generator)",
            wireName,
            property.name.kotlinIdentifierName,
          )
          if (!property.required) endControlFlow()
        }
        addStatement("generator.writeEndObject()")
      }.build()
  val serializeWithType =
    FunSpec
      .builder("serializeWithType")
      .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
      .addParameter("value", fallbackTypeName)
      .addParameter("generator", JACKSON_JSON_GENERATOR)
      .addParameter("provider", JACKSON_SERIALIZER_PROVIDER)
      .addParameter("typeSerializer", JACKSON_TYPE_SERIALIZER)
      .addStatement("serialize(value, generator, provider)")
      .build()
  return TypeSpec
    .classBuilder("Serializer")
    .addModifiers(KModifier.PUBLIC)
    .superclass(JACKSON_JSON_SERIALIZER.parameterizedBy(fallbackTypeName))
    .addFunction(serialize)
    .addFunction(serializeWithType)
    .build()
}
