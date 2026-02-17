/*
 * Copyright 2020 Outfox, Inc.
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

@file:Suppress("DuplicatedCode")

package io.outfoxx.sunday.generator.kotlin

import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.ArrayNode
import amf.core.client.platform.model.domain.CustomizableElement
import amf.core.client.platform.model.domain.DomainElement
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import amf.shapes.client.platform.model.domain.AnyShape
import amf.shapes.client.platform.model.domain.ArrayShape
import amf.shapes.client.platform.model.domain.FileShape
import amf.shapes.client.platform.model.domain.NilShape
import amf.shapes.client.platform.model.domain.NodeShape
import amf.shapes.client.platform.model.domain.ScalarShape
import amf.shapes.client.platform.model.domain.UnionShape
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.tag
import io.outfoxx.sunday.generator.APIAnnotationName.ExternalDiscriminator
import io.outfoxx.sunday.generator.APIAnnotationName.ExternallyDiscriminated
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinImpl
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinModelPkg
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinType
import io.outfoxx.sunday.generator.APIAnnotationName.Nested
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.TypeRegistry
import io.outfoxx.sunday.generator.common.DefinitionLocation
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ContainerElementValid
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.SuppressPublicApiWarnings
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.UseJakartaPackages
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.utils.BeanValidationTypes
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_CREATOR
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_IGNORE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE_INCLUDE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE_NON_EMPTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_SUBTYPES
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_SUBTYPES_TYPE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS_EXISTING_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_ID
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_ID_NAME
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPENAME
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrarySupport
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.utils.PATCH
import io.outfoxx.sunday.generator.kotlin.utils.PATCH_OP
import io.outfoxx.sunday.generator.kotlin.utils.PATCH_SET_OP
import io.outfoxx.sunday.generator.kotlin.utils.ProblemCustomProperty
import io.outfoxx.sunday.generator.kotlin.utils.UPDATE_OP
import io.outfoxx.sunday.generator.kotlin.utils.isArray
import io.outfoxx.sunday.generator.kotlin.utils.isCollectionLike
import io.outfoxx.sunday.generator.kotlin.utils.isMapLike
import io.outfoxx.sunday.generator.kotlin.utils.kotlinEnumName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.kotlin.utils.withAnnotatedTypeArgument
import io.outfoxx.sunday.generator.kotlin.utils.withTypeArgument
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.dataType
import io.outfoxx.sunday.generator.utils.discriminator
import io.outfoxx.sunday.generator.utils.discriminatorMapping
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.flattened
import io.outfoxx.sunday.generator.utils.format
import io.outfoxx.sunday.generator.utils.get
import io.outfoxx.sunday.generator.utils.getValue
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.id
import io.outfoxx.sunday.generator.utils.isNameExplicit
import io.outfoxx.sunday.generator.utils.items
import io.outfoxx.sunday.generator.utils.makesNullable
import io.outfoxx.sunday.generator.utils.maxItems
import io.outfoxx.sunday.generator.utils.maxLength
import io.outfoxx.sunday.generator.utils.maximum
import io.outfoxx.sunday.generator.utils.minCount
import io.outfoxx.sunday.generator.utils.minItems
import io.outfoxx.sunday.generator.utils.minLength
import io.outfoxx.sunday.generator.utils.minimum
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.nullable
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.optional
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.pattern
import io.outfoxx.sunday.generator.utils.patternProperties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.scalarValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueId
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.xone
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import javax.annotation.processing.Generated
import kotlin.math.min

class KotlinTypeRegistry(
  val defaultModelPackageName: String?,
  generatedAnnotationName: String?,
  val generationMode: GenerationMode,
  val options: Set<Option>,
  val problemLibrary: KotlinProblemLibrary = KotlinProblemLibrary.QUARKUS,
  val problemRfc: KotlinProblemRfc = KotlinProblemRfc.RFC9457,
  val validateProblemRfc: Boolean = false,
) : TypeRegistry {

  enum class Option {
    ImplementModel,
    ValidationConstraints,
    ContainerElementValid,
    JacksonAnnotations,
    AddGeneratedAnnotation,
    SuppressPublicApiWarnings,
    UseJakartaPackages,
  }

  val generationTimestamp = LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)!!
  private val generatedAnnotationName = ClassName.bestGuess(generatedAnnotationName ?: Generated::class.qualifiedName!!)
  private val typeBuilders = mutableMapOf<ClassName, TypeSpec.Builder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()
  private val beanValidationTypes =
    if (options.contains(UseJakartaPackages)) {
      BeanValidationTypes.JAKARTA
    } else {
      BeanValidationTypes.JAVAX
    }
  val problemLibrarySupport: KotlinProblemLibrarySupport = problemLibrary.support(problemRfc)

  override fun generateFiles(
    categories: Set<GeneratedTypeCategory>,
    outputDirectory: Path,
  ) {

    val builtTypes = buildTypes()

    builtTypes.entries
      .filter { it.key.topLevelClassName() == it.key }
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { FileSpec.get(it.key.packageName, it.value) }
      .forEach { it.writeTo(outputDirectory) }
  }

  fun buildTypes(): Map<ClassName, TypeSpec> {

    // Add nested classes to parents
    typeBuilders.entries
      .toList()
      .sortedByDescending { it.key.simpleNames.size }
      .forEach { (className, typeBuilder) ->
        // Is this a nested class?
        val enclosingClassName = className.enclosingClassName() ?: return@forEach
        typeBuilders[enclosingClassName]?.addType(typeBuilder.build())
      }

    return typeBuilders.mapValues { it.value.build() }
  }

  fun resolveTypeName(
    shapeRef: Shape,
    context: KotlinResolutionContext,
  ): TypeName {

    val shape = context.dereference(shapeRef)

    val shapeKey = if (shape.isNameExplicit) shape.uniqueId else shape.id
    var typeName = typeNameMappings[shapeKey]
    if (typeName == null) {

      typeName = generateTypeName(shape, context)

      typeNameMappings[shapeKey] = typeName
    }

    return typeName
  }

  fun unresolveTypeName(typeName: TypeName) {
    typeBuilders.remove(typeName)
  }

  fun addServiceType(
    className: ClassName,
    serviceType: TypeSpec.Builder,
  ) {

    serviceType
      .addGenerated(true)
      .addSuppress()

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined")
    }
  }

  override fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition,
    shapeIndex: ShapeIndex,
  ): ClassName {

    val problemPackageName = packageNameOf(problemTypeDefinition.definedIn)

    val problemTypeName =
      ClassName.bestGuess("$problemPackageName.${problemCode.toUpperCamelCase()}Problem")

    val problemTypeBuilder =
      TypeSpec
        .classBuilder(problemTypeName)
        .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
        .addGenerated(true)
        .addSuppress()
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addGenerated(false)
            .addProperty(
              PropertySpec
                .builder("TYPE", STRING)
                .addModifiers(KModifier.CONST)
                .initializer("%S", problemTypeDefinition.type)
                .build(),
            ).addProperty(
              PropertySpec
                .builder("TYPE_URI", URI::class.asTypeName())
                .initializer("%T(%L)", URI::class.asTypeName(), "TYPE")
                .build(),
            ).build(),
        )

    val constructorBuilder =
      FunSpec
        .constructorBuilder()
        .apply {
          // Add all custom properties to the constructor
          problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->
            val parameterTypeName =
              resolveTypeReference(
                customPropertyTypeNameStr,
                problemTypeDefinition.source,
                KotlinResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
              )
            addParameter(
              ParameterSpec
                .builder(
                  customPropertyName.kotlinIdentifierName,
                  parameterTypeName,
                ).apply {
                  if (parameterTypeName.isNullable) {
                    defaultValue("null")
                  }
                  if (customPropertyName.kotlinIdentifierName != customPropertyName) {
                    addAnnotation(
                      AnnotationSpec
                        .builder(JACKSON_JSON_PROPERTY)
                        .addMember("value = %S", customPropertyName)
                        .build(),
                    )
                  }
                }.build(),
            )
          }
        }.apply {
          if (options.contains(JacksonAnnotations)) {
            addAnnotation(JACKSON_JSON_CREATOR)
          }
        }

    val customProperties =
      problemTypeDefinition.custom.map { (customPropertyName, customPropertyTypeNameStr) ->
        val parameterTypeName =
          resolveTypeReference(
            customPropertyTypeNameStr,
            problemTypeDefinition.source,
            KotlinResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
          )
        ProblemCustomProperty(
          jsonName = customPropertyName,
          paramName = customPropertyName.kotlinIdentifierName,
          typeName = parameterTypeName,
        )
      }

    if (validateProblemRfc) {
      problemLibrarySupport.validateRfcCompliance()
    }

    problemLibrarySupport.configureProblemType(
      problemTypeBuilder,
      problemTypeName,
      problemTypeDefinition,
      customProperties,
      constructorBuilder,
    )

    problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->
      problemTypeBuilder.addProperty(
        PropertySpec
          .builder(
            customPropertyName.kotlinIdentifierName,
            resolveTypeReference(
              customPropertyTypeNameStr,
              problemTypeDefinition.source,
              KotlinResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
            ),
            KModifier.PUBLIC,
          ).initializer(customPropertyName.kotlinIdentifierName)
          .build(),
      )
    }

    if (options.contains(JacksonAnnotations)) {
      problemTypeBuilder.addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_TYPENAME)
          .addMember("%T.TYPE", problemTypeName)
          .build(),
      )
    }

    typeBuilders[problemTypeName] = problemTypeBuilder

    return problemTypeName
  }

  private fun resolveTypeReference(
    nameStr: String,
    source: DomainElement,
    context: KotlinResolutionContext,
  ): TypeName {
    val typeNameStr = nameStr.removeSuffix("?")
    val elementTypeNameStr = typeNameStr.removeSuffix("[]")
    val elementTypeName =
      when (elementTypeNameStr.lowercase()) {
        "boolean" -> BOOLEAN
        "integer" -> INT
        "number" -> DOUBLE
        "string" -> STRING
        "object" -> MAP.parameterizedBy(STRING, STRING)
        "any" -> ANY
        "file" -> BYTE_ARRAY
        "time-ony" -> LocalTime::class.asTypeName()
        "date-ony" -> LocalDate::class.asTypeName()
        "datetime-only" -> LocalDateTime::class.asTypeName()
        "datetime" -> OffsetDateTime::class.asTypeName()
        else -> {
          val (element, unit) =
            context.resolveRef(elementTypeNameStr, source)
              ?: genError("Invalid type reference '$elementTypeNameStr'", source)
          element as? Shape ?: genError("Invalid type reference '$elementTypeNameStr'", source)

          resolveReferencedTypeName(element, context.copy(unit = unit, suggestedTypeName = null))
        }
      }
    val typeName =
      if (typeNameStr.endsWith("[]")) {
        LIST.parameterizedBy(elementTypeName)
      } else {
        elementTypeName
      }
    return typeName.copy(nullable = nameStr.endsWith("?"))
  }

  private fun resolveReferencedTypeName(
    shape: Shape,
    context: KotlinResolutionContext,
  ): TypeName = resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: ClassName,
    context: KotlinResolutionContext,
  ): TypeName {

    val propertyContext =
      context.copy(
        suggestedTypeName = className.nestedClass(propertyShape.kotlinTypeName),
      )

    val typeName = resolveTypeName(propertyShape.range, propertyContext)
    return if ((propertyShape.minCount ?: 0) == 0) {
      typeName.copy(nullable = true)
    } else {
      typeName
    }
  }

  private fun generateTypeName(
    shape: Shape,
    context: KotlinResolutionContext,
  ): TypeName {

    val kotlinTypeAnn = shape.findStringAnnotation(KotlinType, generationMode)
    if (kotlinTypeAnn != null) {
      return ClassName.bestGuess(kotlinTypeAnn)
    }

    return processShape(shape, context)
  }

  private fun processShape(
    shape: Shape,
    context: KotlinResolutionContext,
  ): TypeName =
    when (shape) {
      is ScalarShape -> processScalarShape(shape, context)
      is ArrayShape -> processArrayShape(shape, context)
      is UnionShape -> processUnionShape(shape, context)
      is NodeShape -> processNodeShape(shape, context)
      is FileShape -> BYTE_ARRAY
      is NilShape -> UNIT
      is AnyShape -> processAnyShape(shape, context)
      else -> genError("Shape type '${shape::class.simpleName}' is unsupported", shape)
    }

  private fun processAnyShape(
    shape: AnyShape,
    context: KotlinResolutionContext,
  ): TypeName =
    when {
      context.hasInherited(shape) && shape is NodeShape ->
        defineClass(
          shape,
          context,
        )

      shape.or.isNotEmpty() ->
        nearestCommonAncestor(shape.or, context) ?: ANY

      shape.xone.isNotEmpty() ->
        nearestCommonAncestor(shape.xone, context) ?: ANY

      else -> ANY
    }

  private fun processScalarShape(
    shape: ScalarShape,
    context: KotlinResolutionContext,
  ): TypeName =
    when (shape.dataType) {
      DataTypes.String() ->

        if (shape.values.isNotEmpty()) {
          defineEnum(shape, context)
        } else {
          when (shape.format) {
            "time" -> LocalTime::class.asTypeName()
            "datetime-only", "date-time-only" -> LocalDateTime::class.asTypeName()
            else -> STRING
          }
        }

      DataTypes.Boolean() -> BOOLEAN

      DataTypes.Integer() ->
        when (shape.format) {
          "int8" -> BYTE
          "int16" -> SHORT
          "int32", "int" -> INT
          "", null -> INT
          else -> genError("Integer format '${shape.format}' is unsupported", shape)
        }

      DataTypes.Long() -> LONG

      DataTypes.Float() -> FLOAT
      DataTypes.Double() -> DOUBLE
      DataTypes.Number() -> DOUBLE

      DataTypes.Decimal() -> BigDecimal::class.asTypeName()

      //      DataTypes.Duration() -> Duration::class.asTypeName()
      DataTypes.Date() -> LocalDate::class.asTypeName()
      DataTypes.Time() -> LocalTime::class.asTypeName()
      DataTypes.DateTimeOnly() -> LocalDateTime::class.asTypeName()
      DataTypes.DateTime() -> OffsetDateTime::class.asTypeName()

      DataTypes.Binary() -> BYTE_ARRAY

      else -> genError("Scalar data type '${shape.dataType}' is unsupported", shape)
    }

  private fun processArrayShape(
    shape: ArrayShape,
    context: KotlinResolutionContext,
  ): TypeName {

    val elementType =
      shape.items
        ?.let { itemsShape ->
          resolveReferencedTypeName(itemsShape, context)
        }
        ?: ANY

    val collectionType =
      if (shape.uniqueItems == true) {
        SET
      } else {
        LIST
      }

    return collectionType.parameterizedBy(elementType)
  }

  private fun processUnionShape(
    shape: UnionShape,
    context: KotlinResolutionContext,
  ): TypeName =
    if (shape.makesNullable) {
      resolveReferencedTypeName(shape.nullableType, context).copy(nullable = true)
    } else {
      nearestCommonAncestor(shape.anyOf, context) ?: ANY
    }

  private fun processNodeShape(
    shape: NodeShape,
    context: KotlinResolutionContext,
  ): TypeName {

    if (
      shape.nonPatternProperties.isEmpty() &&
      context.hasNoInherited(shape) &&
      context.hasNoInheriting(shape)
    ) {

      val patternProperties = shape.patternProperties
      return if (patternProperties.isNotEmpty()) {

        val patternPropertyShapes = collectTypes(patternProperties.map { it.range })

        val valueTypeName = nearestCommonAncestor(patternPropertyShapes, context) ?: ANY

        MAP.parameterizedBy(STRING, valueTypeName)
      } else {

        MAP.parameterizedBy(STRING, ANY)
      }
    }

    return defineClass(shape, context)
  }

  private fun defineClass(
    shape: NodeShape,
    context: KotlinResolutionContext,
  ): TypeName {

    val className = typeNameOf(shape, context)

    // Check for an existing class built or being built
    val existingBuilder = typeBuilders[className]
    if (existingBuilder != null) {
      if (existingBuilder.tags[DefinitionLocation::class] != DefinitionLocation(shape)) {
        genError("Multiple classes defined with name '$className'", shape)
      } else {
        return className
      }
    }

    val typeBuilder =
      defineType(className) { name ->
        if (options.contains(ImplementModel)) {
          TypeSpec.classBuilder(name)
        } else {
          TypeSpec.interfaceBuilder(name)
        }
      }

    typeBuilder.tag(DefinitionLocation(shape))

    val superShape = context.findSuperShapeOrNull(shape) as NodeShape?
    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context)
      if (options.contains(ImplementModel)) {
        typeBuilder.superclass(superClassName)
      } else {
        typeBuilder.addSuperinterface(superClassName)
      }
    }

    val isPatchable = shape.isPatchable(context)

    if (isPatchable) {
      typeBuilder
        .addSuperinterface(PATCH)
        .addAnnotation(
          AnnotationSpec
            .builder(JACKSON_JSON_INCLUDE)
            .addMember("%T.%L", JACKSON_JSON_INCLUDE_INCLUDE, JACKSON_JSON_INCLUDE_NON_EMPTY)
            .build(),
        )
    }

    var inheritedProperties = superShape?.let(context::findAllProperties) ?: emptyList()
    var declaredProperties =
      context.findProperties(shape).filter { dec -> dec.name !in inheritedProperties.map { it.name } }

    val inheritingTypes = context.findInheritingShapes(shape)

    if (inheritingTypes.isNotEmpty() && options.contains(ImplementModel)) {
      typeBuilder.modifiers.add(KModifier.OPEN)
    }

    if (inheritedProperties.isNotEmpty() || declaredProperties.isNotEmpty()) {

      /*
        Computed discriminator values (for jackson generation)
       */

      if (options.contains(JacksonAnnotations)) {

        val discriminatorPropertyName = findDiscriminatorPropertyName(shape, context)
        if (discriminatorPropertyName != null) {

          val discriminatorProperty =
            (inheritedProperties + declaredProperties).find { it.name == discriminatorPropertyName }
              ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

          val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)

          declaredProperties = declaredProperties.filter { it.name != discriminatorPropertyName }
          inheritedProperties = inheritedProperties.filter { it.name != discriminatorPropertyName }

          // Add an abstract discriminator if this is the root of the discriminator tree

          if (context.hasNoInherited(shape)) {

            val discriminatorBuilder =
              PropertySpec.builder(discriminatorProperty.kotlinIdentifierName, discriminatorPropertyTypeName)

            if (shape.findBoolAnnotation(ExternallyDiscriminated, generationMode) == true) {
              discriminatorBuilder.addAnnotation(
                AnnotationSpec
                  .builder(JACKSON_JSON_IGNORE)
                  .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                  .build(),
              )
            }

            if (options.contains(ImplementModel)) {
              discriminatorBuilder.addModifiers(KModifier.ABSTRACT)
              typeBuilder.addModifiers(KModifier.ABSTRACT)
            }

            typeBuilder.addProperty(discriminatorBuilder.build())
          } else if (options.contains(ImplementModel)) {

            // Add concrete discriminator for the leaf of the discriminated tree

            val discriminatorBuilder =
              PropertySpec
                .builder(discriminatorProperty.kotlinIdentifierName, discriminatorPropertyTypeName)
                .addModifiers(KModifier.OVERRIDE)

            val discriminatorValue = findDiscriminatorPropertyValue(shape, context) ?: shape.name!!

            val isEnum = typeBuilders[discriminatorPropertyTypeName]?.enumConstants?.isNotEmpty() ?: false
            if (isEnum) {
              discriminatorBuilder.getter(
                FunSpec
                  .getterBuilder()
                  .addStatement(
                    "return %T.%L",
                    discriminatorPropertyTypeName,
                    discriminatorValue.toUpperCamelCase(),
                  ).build(),
              )
            } else {
              discriminatorBuilder.getter(
                FunSpec
                  .getterBuilder()
                  .addStatement("return %S", discriminatorValue)
                  .build(),
              )
            }

            typeBuilder.addProperty(discriminatorBuilder.build())
          }
        }
      }

      val definedProperties = declaredProperties.filterNot { it.range.hasAnnotation(KotlinImpl, generationMode) }

      /*
        Generate class implementations
       */

      if (options.contains(ImplementModel)) {

        // Build parameter constructor
        //
        var paramConsBuilder: FunSpec.Builder? = null
        if (isPatchable || inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {
          paramConsBuilder = FunSpec.constructorBuilder()
          inheritedProperties.forEach { inheritedProperty ->
            val paramTypeName =
              resolvePropertyTypeName(inheritedProperty, className, context)
                .run {
                  if (isPatchable) {
                    val base = if (inheritedProperty.nullable) PATCH_OP else UPDATE_OP
                    base.parameterizedBy(copy(nullable = false))
                  } else {
                    this
                  }
                }

            paramConsBuilder.addParameter(
              ParameterSpec
                .builder(
                  inheritedProperty.kotlinIdentifierName,
                  paramTypeName,
                ).apply {
                  if (isPatchable) {
                    defaultValue("%T.none()", PATCH_OP)
                  } else {
                    if (inheritedProperty.optional && paramTypeName.isNullable) defaultValue("null")
                  }
                }.build(),
            )

            typeBuilder.addSuperclassConstructorParameter("%L", inheritedProperty.kotlinIdentifierName)
          }
        }

        // toString builder
        //
        val toStringCode = mutableListOf<CodeBlock>()
        inheritedProperties.forEach {
          toStringCode.add(CodeBlock.of("%L='$%L'", it.kotlinIdentifierName, it.kotlinIdentifierName))
        }

        // hash builder
        //
        val hashBuilder: FunSpec.Builder?
        if (definedProperties.isNotEmpty()) {
          hashBuilder =
            FunSpec
              .builder("hashCode")
              .addModifiers(KModifier.OVERRIDE)
              .returns(INT)
          if (superShape != null) {
            hashBuilder.addStatement("var result = 31 * super.hashCode()")
          } else {
            hashBuilder.addStatement("var result = 1")
          }
        } else {
          hashBuilder = null
        }

        // equals builder
        //
        val equalsBuilder: FunSpec.Builder?
        if (definedProperties.isNotEmpty() || inheritingTypes.isNotEmpty()) {

          equalsBuilder =
            FunSpec
              .builder("equals")
              .addModifiers(KModifier.OVERRIDE)
              .returns(BOOLEAN)
              .addParameter("other", ANY.copy(nullable = true))
              .addStatement("if (this === other) return true")
              .addStatement("if (javaClass != other?.javaClass) return false", className)

          if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {
            equalsBuilder
              .addCode("\n")
              .addStatement("other as %T", className)
          }

          equalsBuilder.addCode("\n")
        } else {
          equalsBuilder = null
        }

        fun addEquals(
          propertyTypeName: TypeName,
          property: PropertyShape,
        ) {
          equalsBuilder ?: return
          if (propertyTypeName.isArray) {
            if (propertyTypeName.isNullable) {
              equalsBuilder
                .beginControlFlow("if (%L != null)", property.kotlinIdentifierName)
                .addStatement("if (other.%L == null) return false", property.kotlinIdentifierName)
                .addStatement(
                  "if (!%L.contentEquals(other.%L)) return false",
                  property.kotlinIdentifierName,
                  property.kotlinIdentifierName,
                ).endControlFlow()
                .addStatement("else if (other.%L != null) return false", property.kotlinIdentifierName)
            } else {
              equalsBuilder.addStatement(
                "if (!%L.contentEquals(other.%L)) return false",
                property.kotlinIdentifierName,
                property.kotlinIdentifierName,
              )
            }
          } else {
            equalsBuilder.addStatement(
              "if (%L != other.%L) return false",
              property.kotlinIdentifierName,
              property.kotlinIdentifierName,
            )
          }
        }

        inheritedProperties.forEach { inheritedProperty ->
          addEquals(resolvePropertyTypeName(inheritedProperty, className, context), inheritedProperty)
        }

        // Generate related code for each property
        //
        declaredProperties.forEach { declaredProperty ->

          val declaredPropertyTypeName =
            resolvePropertyTypeName(declaredProperty, className, context)
              .run {
                if (isPatchable) {
                  val base = if (declaredProperty.nullable) PATCH_OP else UPDATE_OP
                  base.parameterizedBy(copy(nullable = false))
                } else {
                  this
                }
              }

          val implAnn = declaredProperty.range.findAnnotation(KotlinImpl, generationMode) as? ObjectNode
          val validationAnnotations = mutableListOf<AnnotationSpec>()
          val validatedTypeName =
            if (implAnn == null) {
              applyUseSiteAnnotations(declaredProperty, declaredPropertyTypeName) {
                validationAnnotations.add(it)
              }
            } else {
              declaredPropertyTypeName
            }

          val declaredPropertyBuilder =
            PropertySpec.builder(declaredProperty.kotlinIdentifierName, validatedTypeName)

          if (isPatchable) {
            declaredPropertyBuilder
              .mutable(true)
          }

          val externalDiscriminatorPropertyName =
            declaredProperty.range.findStringAnnotation(ExternalDiscriminator, generationMode)
          if (externalDiscriminatorPropertyName != null) {

            declaredProperty.range as? NodeShape
              ?: genError(" Externally discriminated types must be 'object'", declaredProperty)

            (inheritedProperties + declaredProperties).find { it.name == externalDiscriminatorPropertyName }
              ?: genError("External discriminator '$externalDiscriminatorPropertyName' not found in object", shape)

            if (options.contains(JacksonAnnotations)) {
              addJacksonPolymorphismOverride(declaredPropertyBuilder, externalDiscriminatorPropertyName)
            }
          }

          if (implAnn != null) {

            declaredPropertyBuilder.addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_IGNORE)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                .build(),
            )

            val code = implAnn.getValue("code") ?: ""
            val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
            val convertedCodeParams =
              codeParams.map { codeParam ->
                val atype = codeParam.getValue("type")
                val avalue = codeParam.getValue("value")
                if (atype != null && avalue != null) {
                  when (atype) {
                    "Type" -> ClassName.bestGuess(avalue)
                    else -> avalue
                  }
                } else {
                  ""
                }
              }

            declaredPropertyBuilder.getter(
              FunSpec
                .getterBuilder()
                .addStatement(code, *convertedCodeParams.toTypedArray())
                .build(),
            )
          } else {
            validationAnnotations.forEach { annotation ->
              declaredPropertyBuilder.addAnnotation(
                annotation.toBuilder().useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD).build(),
              )
            }

            declaredPropertyBuilder.initializer(declaredProperty.kotlinIdentifierName)

            if (declaredProperty.kotlinIdentifierName != declaredProperty.name) {
              declaredPropertyBuilder
                .addAnnotation(
                  AnnotationSpec
                    .builder(JACKSON_JSON_PROPERTY)
                    .useSiteTarget(AnnotationSpec.UseSiteTarget.PARAM)
                    .addMember("value = %S", declaredProperty.name())
                    .build(),
                )
            }

            // Add constructor parameter
            //
            paramConsBuilder?.addParameter(
              ParameterSpec
                .builder(declaredProperty.kotlinIdentifierName, validatedTypeName)
                .apply {
                  if (isPatchable) {
                    defaultValue("%T.none()", PATCH_OP)
                  } else if (declaredProperty.optional && validatedTypeName.isNullable) {
                    defaultValue("null")
                  }
                }.build(),
            )

            // Add toString value
            //
            toStringCode.add(
              CodeBlock.of("%L='$%L'", declaredProperty.kotlinIdentifierName, declaredProperty.kotlinIdentifierName),
            )

            // Add hashCode value
            //
            if (hashBuilder != null) {
              val hashMember = if (declaredPropertyTypeName.isArray) "contentHashCode" else "hashCode"
              if (declaredPropertyTypeName.isNullable) {
                hashBuilder.addStatement(
                  "result = 31 * result + (%L?.%L() ?: 0)",
                  declaredProperty.kotlinIdentifierName,
                  hashMember,
                )
              } else {
                hashBuilder.addStatement(
                  "result = 31 * result + %L.%L()",
                  declaredProperty.kotlinIdentifierName,
                  hashMember,
                )
              }
            }

            addEquals(declaredPropertyTypeName, declaredProperty)
          }

          typeBuilder.addProperty(declaredPropertyBuilder.build())
        }

        // Add a copy method
        //
        if (inheritingTypes.isEmpty() && !isPatchable) {

          val copyBuilder =
            FunSpec
              .builder("copy")
              .returns(className)
              .addCode("return %T(", className)

          val copyArgs =
            (inheritedProperties + definedProperties).map { copyProperty ->

              val propertyTypeName = resolvePropertyTypeName(copyProperty, className, context)

              copyBuilder.addParameter(
                ParameterSpec
                  .builder(copyProperty.kotlinIdentifierName, propertyTypeName.copy(nullable = true))
                  .defaultValue("null")
                  .build(),
              )

              CodeBlock.of("%L ?: this.%L", copyProperty.kotlinIdentifierName, copyProperty.kotlinIdentifierName)
            }

          copyBuilder
            .addCode(copyArgs.joinToString(", "))
            .addCode(")")

          typeBuilder.addFunction(copyBuilder.build())
        }

        // Finish parameter constructor
        paramConsBuilder?.let {
          typeBuilder.primaryConstructor(it.build())
        }

        // Finish hashCode method
        hashBuilder?.let {
          it.addStatement("return result")
          typeBuilder.addFunction(it.build())
        }

        // Finish equals method
        equalsBuilder?.let {
          if (definedProperties.isNotEmpty()) {
            equalsBuilder.addCode("\n")
          }

          equalsBuilder.addStatement("return true")

          typeBuilder.addFunction(it.build())
        }

        // Finish toString method
        val toStringTemplate =
          CodeBlock
            .of(
              "%L(%L)",
              className.simpleName,
              toStringCode.joinToString(",\n "),
            ).toString()

        typeBuilder.addFunction(
          FunSpec
            .builder("toString")
            .returns(STRING)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return %P", toStringTemplate)
            .build(),
        )
      } else {

        // Generate related code for each property
        //
        declaredProperties.forEach { declaredProperty ->

          val propertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)
          val validationAnnotations = mutableListOf<AnnotationSpec>()
          val validatedTypeName =
            applyUseSiteAnnotations(declaredProperty, propertyTypeName) {
              validationAnnotations.add(it)
            }

          // Add public field
          //
          val propertyBuilder = PropertySpec.builder(declaredProperty.kotlinIdentifierName, validatedTypeName)

          validationAnnotations.forEach { annotation ->
            val getAnn =
              annotation
                .toBuilder()
                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                .build()
            propertyBuilder.addAnnotation(getAnn)
          }

          typeBuilder.addProperty(propertyBuilder.build())
        }
      }
    }

    if (isPatchable && options.contains(ImplementModel) && !typeBuilder.modifiers.contains(KModifier.ABSTRACT)) {

      val initLambdaTypeName = LambdaTypeName.get(className, listOf(), UNIT)

      typeBuilder.addType(
        TypeSpec
          .companionObjectBuilder()
          // Add a merge method to a companion object
          .addFunction(
            FunSpec
              .builder("merge")
              .addModifiers(KModifier.INLINE)
              .returns(PATCH_SET_OP.parameterizedBy(className))
              .addParameter("init", initLambdaTypeName)
              .addStatement("val patch = %T()", className)
              .addStatement("patch.init()")
              .addStatement("return %T(patch)", PATCH_SET_OP)
              .build(),
          )
          // Add a patch method to the companion object
          .addFunction(
            FunSpec
              .builder("patch")
              .returns(className)
              .addModifiers(KModifier.INLINE)
              .addParameter("init", initLambdaTypeName)
              .addStatement("return merge(init).value")
              .build(),
          ).build(),
      )
    }

    if (typeBuilder.modifiers.contains(KModifier.ABSTRACT)) {
      typeBuilder.modifiers.remove(KModifier.OPEN)
    }

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    if (options.contains(JacksonAnnotations)) {

      val root = context.findRootShape(shape) as NodeShape

      // Is this shape the root node with discrimination?
      if (root.id == shape.id && shape.supportsDiscrimination) {

        addJacksonPolymorphism(shape, inheritingTypes, typeBuilder, context)
      } else if (!typeBuilder.modifiers.contains(KModifier.ABSTRACT)) {

        // Does the root of this shape tree support discrimination?
        if (root.supportsDiscrimination) {

          val discriminatorMappings = buildDiscriminatorMappings(shape, context)

          val mappedDiscriminator = discriminatorMappings.entries.find { it.value == shape.id }?.key

          val subTypeName = shape.discriminatorValue ?: mappedDiscriminator ?: shape.name
          if (subTypeName != null) {

            typeBuilder.addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_TYPENAME)
                .addMember("%S", subTypeName)
                .build(),
            )
          }
        }
      }
    }

    return className
  }

  private fun defineEnum(
    shape: Shape,
    context: KotlinResolutionContext,
  ): TypeName {

    val className = typeNameOf(shape, context)

    // Check for an existing class built or being built
    val existingBuilder = typeBuilders[className]
    if (existingBuilder != null) {
      if (existingBuilder.tags[DefinitionLocation::class] != DefinitionLocation(shape)) {
        genError("Multiple classes defined with name '$className'", shape)
      } else {
        return className
      }
    }

    val enumBuilder = defineType(className, TypeSpec::enumBuilder)

    enumBuilder.tag(DefinitionLocation(shape))

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->

      if (options.contains(JacksonAnnotations)) {
        val enumType =
          TypeSpec
            .anonymousClassBuilder()
            .addAnnotation(
              AnnotationSpec
                .builder(JACKSON_JSON_PROPERTY)
                .addMember("value = %S", enum.scalarValue!!)
                .build(),
            ).build()

        enumBuilder.addEnumConstant(enum.kotlinEnumName, enumType)
      } else {
        enumBuilder.addEnumConstant(enum.kotlinEnumName)
      }
    }

    return className
  }

  private fun defineType(
    className: ClassName,
    builderBlock: (ClassName) -> TypeSpec.Builder,
  ): TypeSpec.Builder {

    val builder = builderBlock(className)

    builder.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, builder) != null) {
      genError("Multiple types with name '${className.simpleName}' defined in package '${className.packageName}'")
    }

    if (className.enclosingClassName() == null) {

      builder
        .addGenerated(true)
        .addSuppress()
    } else {
      builder.addGenerated(false)
    }

    return builder
  }

  fun applyUseSiteAnnotations(
    use: Shape,
    typeName: TypeName,
    applicator: (AnnotationSpec) -> Unit,
  ): TypeName {

    if (options.contains(ValidationConstraints)) {
      return when (use) {
        is PropertyShape -> applyUseSiteValidationAnnotations(use.range, typeName, applicator)
        else -> applyUseSiteValidationAnnotations(use, typeName, applicator)
      }
    }

    return typeName
  }

  private fun applyUseSiteValidationAnnotations(
    use: Shape,
    typeName: TypeName,
    applicator: (AnnotationSpec) -> Unit,
  ): TypeName {

    when (use) {

      is ScalarShape -> {
        scalarConstraintAnnotations(use).forEach { applicator.invoke(it) }
      }

      is ArrayShape -> {

        // Apply max (if set)
        var sizeBuilder: AnnotationSpec.Builder? = null
        if (use.maxItems != null && use.maxItems != Integer.MAX_VALUE) {
          sizeBuilder = AnnotationSpec.builder(beanValidationTypes.size)
          sizeBuilder.addMember("max = %L", use.maxItems().toString())
        }

        // Apply min (if set)
        if (use.minItems != null && use.minItems != 0) {
          sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(beanValidationTypes.size)
          sizeBuilder.addMember("min = %L", use.minItems().toString())
        }
        sizeBuilder?.let {
          applicator.invoke(it.build())
        }

        val itemScalarAnnotations = scalarConstraintAnnotationsForElement(use.items)
        if (options.contains(ContainerElementValid)) {
          return applyContainerElementValid(use, typeName)
        }
        itemScalarAnnotations.forEach { applicator.invoke(it) }
      }

      is NodeShape -> {

        // Apply @Valid for nested types
        if (typeName != ANY) {
          if (options.contains(ContainerElementValid) && typeName.isMapLike) {
            return applyContainerElementValid(use, typeName)
          }

          applicator.invoke(
            AnnotationSpec
              .builder(beanValidationTypes.valid)
              .build(),
          )
        }
      }
    }

    return typeName
  }

  private fun applyContainerElementValid(
    use: Shape,
    typeName: TypeName,
  ): TypeName {
    val validAnnotation = AnnotationSpec.builder(beanValidationTypes.valid).build()

    return when {
      typeName.isMapLike -> {
        val nodeShape = use as? NodeShape ?: return typeName
        val valueShapes = collectTypes(nodeShape.patternProperties.map { it.range })
        if (valueShapes.isEmpty()) {
          return typeName
        }

        val cascadeShapes = valueShapes.filter { shouldCascade(it) }
        val valueType = (typeName as? ParameterizedTypeName)?.typeArguments?.getOrNull(1) ?: return typeName
        if (valueType == ANY) {
          return typeName
        }
        val cascadeShape = cascadeShapes.singleOrNull()
        val updatedValueType =
          if (cascadeShape != null) {
            applyContainerElementValid(cascadeShape, valueType)
          } else {
            valueType
          }

        var updatedTypeName =
          if (updatedValueType != valueType) {
            typeName.withTypeArgument(1, updatedValueType)
          } else {
            typeName
          }

        val scalarAnnotations = scalarConstraintAnnotationsForSingleValue(nodeShape)
        scalarAnnotations.forEach { annotation ->
          updatedTypeName = updatedTypeName.withAnnotatedTypeArgument(1, annotation)
        }

        if (cascadeShapes.isNotEmpty()) {
          updatedTypeName = updatedTypeName.withAnnotatedTypeArgument(1, validAnnotation)
        }

        updatedTypeName
      }

      typeName.isCollectionLike -> {
        val arrayShape = use as? ArrayShape ?: return typeName
        val itemShape = arrayShape.items ?: return typeName
        val elementType = (typeName as? ParameterizedTypeName)?.typeArguments?.getOrNull(0) ?: return typeName
        if (elementType == ANY) {
          return typeName
        }
        val updatedElementType =
          if (shouldCascade(itemShape)) {
            applyContainerElementValid(itemShape, elementType)
          } else {
            elementType
          }
        var updatedTypeName =
          if (updatedElementType != elementType) {
            typeName.withTypeArgument(0, updatedElementType)
          } else {
            typeName
          }

        val scalarAnnotations = scalarConstraintAnnotationsForElement(itemShape)
        scalarAnnotations.forEach { annotation ->
          updatedTypeName = updatedTypeName.withAnnotatedTypeArgument(0, annotation)
        }

        if (shouldCascade(itemShape)) {
          updatedTypeName = updatedTypeName.withAnnotatedTypeArgument(0, validAnnotation)
        }

        updatedTypeName
      }

      else -> typeName
    }
  }

  private fun scalarConstraintAnnotationsForElement(shape: Shape?): List<AnnotationSpec> {
    val scalarShape =
      when (shape) {
        is ScalarShape -> shape
        is UnionShape -> {
          val nonNil = shape.anyOf.filterNot { it is NilShape }
          val scalar = nonNil.filterIsInstance<ScalarShape>()
          if (scalar.size == 1 && nonNil.size == 1) scalar.single() else null
        }

        else -> null
      }
    return if (scalarShape != null) scalarConstraintAnnotations(scalarShape) else emptyList()
  }

  private fun scalarConstraintAnnotationsForSingleValue(shape: NodeShape): List<AnnotationSpec> {
    val valueShape = shape.patternProperties.map { it.range }.singleOrNull() ?: return emptyList()
    return scalarConstraintAnnotationsForElement(valueShape)
  }

  private fun scalarConstraintAnnotations(shape: Shape): List<AnnotationSpec> {
    val scalar = shape as? ScalarShape ?: return emptyList()
    val annotations = mutableListOf<AnnotationSpec>()

    when (scalar.dataType) {
      "http://www.w3.org/2001/XMLSchema#string" -> {
        var sizeBuilder: AnnotationSpec.Builder? = null
        if (scalar.maxLength != null && scalar.maxLength != Integer.MAX_VALUE) {
          sizeBuilder = AnnotationSpec.builder(beanValidationTypes.size)
          sizeBuilder.addMember("max = %L", scalar.maxLength().toString())
        }
        if (scalar.minLength != null && scalar.minLength != 0) {
          sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(beanValidationTypes.size)
          sizeBuilder.addMember("min = %L", scalar.minLength().toString())
        }
        sizeBuilder?.let { annotations.add(it.build()) }

        if (!scalar.pattern.isNullOrBlank() && scalar.pattern != ".*") {
          annotations.add(
            AnnotationSpec
              .builder(beanValidationTypes.pattern)
              .addMember("regexp = %P", scalar.pattern())
              .build(),
          )
        }
      }

      "http://www.w3.org/2001/XMLSchema#integer", "http://www.w3.org/2001/XMLSchema#long" -> {
        if (scalar.maximum != null) {
          annotations.add(
            AnnotationSpec
              .builder(beanValidationTypes.max)
              .addMember("value = %L", scalar.maximum!!.toLong())
              .build(),
          )
        }
        if (scalar.minimum != null) {
          annotations.add(
            AnnotationSpec
              .builder(beanValidationTypes.min)
              .addMember("value = %L", scalar.minimum!!.toLong())
              .build(),
          )
        }
      }

      "http://www.w3.org/2001/XMLSchema#float", "http://www.w3.org/2001/XMLSchema#double" -> {
        if (scalar.maximum != null) {
          annotations.add(
            AnnotationSpec
              .builder(beanValidationTypes.decimalMax)
              .addMember("value = %S", scalar.maximum!!.toBigDecimal())
              .build(),
          )
        }
        if (scalar.minimum != null) {
          annotations.add(
            AnnotationSpec
              .builder(beanValidationTypes.decimalMin)
              .addMember("value = %S", scalar.minimum!!.toBigDecimal())
              .build(),
          )
        }
      }
    }

    return annotations
  }

  private fun shouldCascade(use: Shape?): Boolean =
    when (use) {
      is NodeShape -> true
      is ArrayShape -> shouldCascade(use.items)
      is UnionShape -> use.anyOf.any { shouldCascade(it) }
      else -> false
    }

  private fun addJacksonPolymorphismOverride(
    propertySpec: PropertySpec.Builder,
    externalDiscriminatorPropertyName: String,
  ) {

    propertySpec.addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_TYPEINFO)
        .addMember("use = %T.%L", JACKSON_JSON_TYPEINFO_ID, JACKSON_JSON_TYPEINFO_ID_NAME)
        .addMember("include = %T.%L", JACKSON_JSON_TYPEINFO_AS, JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY)
        .addMember("property = %S", externalDiscriminatorPropertyName)
        .build(),
    )
  }

  private fun addJacksonPolymorphism(
    shape: NodeShape,
    inheritingTypes: List<Shape>,
    classBuilder: TypeSpec.Builder,
    context: KotlinResolutionContext,
  ) {

    val subTypes =
      inheritingTypes
        .map { inheritingType ->

          "%T(value = %T::class)" to
            listOf(
              JACKSON_JSON_SUBTYPES_TYPE,
              resolveReferencedTypeName(inheritingType, context),
            )
        }

    if (subTypes.isNotEmpty()) {

      if (shape.findBoolAnnotation(ExternallyDiscriminated, generationMode) != true) {

        val discriminator = shape.discriminator ?: genError("Missing required discriminator", shape)

        classBuilder.addAnnotation(
          AnnotationSpec
            .builder(JACKSON_JSON_TYPEINFO)
            .addMember("use = %T.%L", JACKSON_JSON_TYPEINFO_ID, JACKSON_JSON_TYPEINFO_ID_NAME)
            .addMember("include = %T.%L", JACKSON_JSON_TYPEINFO_AS, JACKSON_JSON_TYPEINFO_AS_EXISTING_PROPERTY)
            .addMember("property = %S", discriminator)
            .build(),
        )
      }

      classBuilder.addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_SUBTYPES)
          .addMember(
            CodeBlock
              .builder()
              .add("value = [")
              .indent()
              .add(
                "\n${subTypes.joinToString(",\n") { it.first }}",
                *subTypes.flatMap { it.second }.toTypedArray(),
              ).unindent()
              .add("\n]")
              .build(),
          ).build(),
      )
    }
  }

  private fun typeNameOf(
    shape: Shape,
    context: KotlinResolutionContext,
  ): ClassName {

    if (!shape.isNameExplicit && context.suggestedTypeName != null) {
      return context.suggestedTypeName
    }

    val pkgName = packageNameOf(shape, context)

    val nestedAnn =
      shape.findAnnotation(Nested, generationMode)
        ?: return ClassName.bestGuess("$pkgName.${shape.kotlinTypeName}")

    val (nestedEnclosedIn, nestedName) =
      when {
        nestedAnn is ScalarNode && nestedAnn.value == "dashed" -> {

          val spec = shape.name ?: ""
          val parts = spec.split("-")
          if (parts.size < 2) {
            genError("Nested types using 'dashed' scheme must be named with dashes corresponding to nesting hierarchy.")
          }

          val enclosedIn = parts.dropLast(1).joinToString("-")
          val name = parts.last()

          enclosedIn to name
        }

        nestedAnn is ObjectNode -> {

          val enclosedIn =
            nestedAnn.getValue("enclosedIn")
              ?: genError("Nested annotation is missing 'enclosedIn'", nestedAnn)

          val name =
            nestedAnn.getValue("name")
              ?: genError("Nested annotation is missing name", nestedAnn)

          enclosedIn to name
        }

        else ->
          genError("Nested annotation must be the value 'dashed' or an object containing 'enclosedIn' & 'name' keys")
      }

    val (nestedEnclosingType, nestedEnclosingTypeUnit) =
      context.resolveRef(nestedEnclosedIn, shape)
        ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

    nestedEnclosingType as? Shape
      ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

    val nestedEnclosingTypeContext = context.copy(unit = nestedEnclosingTypeUnit, suggestedTypeName = null)

    val nestedEnclosingTypeName =
      resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as? ClassName
        ?: genError("Nested annotation references non-defining enclosing type", nestedAnn)

    return nestedEnclosingTypeName.nestedClass(nestedName)
  }

  private fun packageNameOf(
    shape: Shape,
    context: KotlinResolutionContext,
  ): String = packageNameOf(context.findDeclaringUnit(shape))

  private fun packageNameOf(unit: BaseUnit?): String =
    (unit as? CustomizableElement)?.findStringAnnotation(KotlinModelPkg, generationMode)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(KotlinModelPkg, generationMode)
      ?: defaultModelPackageName
      ?: genError("No model package specified, one must be specified via options or in each RAML unit")

  private fun collectTypes(types: List<Shape>) = types.flatMap { if (it is UnionShape) it.flattened else listOf(it) }

  private fun nearestCommonAncestor(
    types: List<Shape>,
    context: KotlinResolutionContext,
  ): TypeName? {

    // TODO(https://github.com/outfoxx/sunday-generator/issues/45):
    // Support JVM hierarchy for known JVM types.

    var currentClassNameHierarchy: List<TypeName>? = null
    for (type in types) {
      val propertyClassNameHierarchy = classNameHierarchy(type, context)
      currentClassNameHierarchy =
        if (currentClassNameHierarchy == null) {
          propertyClassNameHierarchy
        } else {
          (0 until min(propertyClassNameHierarchy.size, currentClassNameHierarchy.size))
            .takeWhile { propertyClassNameHierarchy[it] == currentClassNameHierarchy[it] }
            .map { propertyClassNameHierarchy[it] }
        }
    }

    return currentClassNameHierarchy?.firstOrNull()
  }

  private fun classNameHierarchy(
    shape: Shape,
    context: KotlinResolutionContext,
  ): List<TypeName> {

    val names = mutableListOf<TypeName>()

    var current: Shape? = shape
    while (current != null) {
      names.add(resolveReferencedTypeName(current, context))
      current = context.findSuperShapeOrNull(current)
    }

    return names.reversed()
  }

  private fun findDiscriminatorPropertyName(
    shape: NodeShape,
    context: KotlinResolutionContext,
  ): String? =
    when {
      !shape.discriminator.isNullOrEmpty() -> shape.discriminator
      else -> context.findSuperShapeOrNull(shape)?.let { findDiscriminatorPropertyName(it as NodeShape, context) }
    }

  private fun findDiscriminatorPropertyValue(
    shape: NodeShape,
    context: KotlinResolutionContext,
  ): String? =
    if (!shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = context.findRootShape(shape) as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(
    shape: NodeShape,
    context: KotlinResolutionContext,
  ): Map<String, String> =
    shape.discriminatorMapping
      .mapNotNull { mapping ->
        val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
        mapping.templateVariable().value()!! to refElement.id
      }.toMap()

  private fun TypeSpec.Builder.addGenerated(verbose: Boolean): TypeSpec.Builder {
    if (options.contains(AddGeneratedAnnotation)) {
      addAnnotation(
        AnnotationSpec
          .builder(generatedAnnotationName)
          .apply {
            if (verbose) {
              addMember("value = [%S]", this@KotlinTypeRegistry.javaClass.name)
              addMember("date = %S", generationTimestamp)
            }
          }.build(),
      )
    }
    return this
  }

  fun addGeneratedTo(
    builder: TypeSpec.Builder,
    verbose: Boolean,
  ) = builder.addGenerated(verbose)

  private fun TypeSpec.Builder.addSuppress(): TypeSpec.Builder {
    if (options.contains(SuppressPublicApiWarnings)) {
      addAnnotation(
        AnnotationSpec
          .builder(Suppress::class)
          .addMember("%S, %S", "RedundantVisibilityModifier", "RedundantUnitReturnType")
          .build(),
      )
    }
    return this
  }

  private val NodeShape.supportsDiscrimination: Boolean
    get() =
      !discriminator.isNullOrEmpty() || findBoolAnnotation(ExternallyDiscriminated, null) == true

  private fun Shape.isPatchable(context: KotlinResolutionContext): Boolean =
    findBoolAnnotation(Patchable, null) == true ||
      context.findSuperShapeOrNull(this)?.isPatchable(context) == true
}
