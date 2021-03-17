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

import amf.client.model.document.BaseUnit
import amf.client.model.document.EncodesModel
import amf.client.model.domain.AnyShape
import amf.client.model.domain.ArrayNode
import amf.client.model.domain.ArrayShape
import amf.client.model.domain.CustomizableElement
import amf.client.model.domain.DomainElement
import amf.client.model.domain.FileShape
import amf.client.model.domain.NilShape
import amf.client.model.domain.NodeShape
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.PropertyShape
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.ScalarShape
import amf.client.model.domain.Shape
import amf.client.model.domain.UnionShape
import amf.core.model.DataType
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
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
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
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
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.SuppressPublicApiWarnings
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.utils.isArray
import io.outfoxx.sunday.generator.kotlin.utils.kotlinEnumName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.utils.aggregateInheritanceNode
import io.outfoxx.sunday.generator.utils.aggregateInheritanceSuper
import io.outfoxx.sunday.generator.utils.and
import io.outfoxx.sunday.generator.utils.anyInheritance
import io.outfoxx.sunday.generator.utils.anyInheritanceNode
import io.outfoxx.sunday.generator.utils.anyInheritanceSuper
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.closed
import io.outfoxx.sunday.generator.utils.dataType
import io.outfoxx.sunday.generator.utils.discriminator
import io.outfoxx.sunday.generator.utils.discriminatorMapping
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findDeclaringUnit
import io.outfoxx.sunday.generator.utils.findInheritingTypes
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.format
import io.outfoxx.sunday.generator.utils.get
import io.outfoxx.sunday.generator.utils.getValue
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.id
import io.outfoxx.sunday.generator.utils.inheritanceRoot
import io.outfoxx.sunday.generator.utils.inherits
import io.outfoxx.sunday.generator.utils.inheritsInheritanceNode
import io.outfoxx.sunday.generator.utils.inheritsInheritanceSuper
import io.outfoxx.sunday.generator.utils.inheritsViaAggregation
import io.outfoxx.sunday.generator.utils.inheritsViaInherits
import io.outfoxx.sunday.generator.utils.isOrWasLink
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
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.pattern
import io.outfoxx.sunday.generator.utils.properties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.resolveRef
import io.outfoxx.sunday.generator.utils.scalarValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.xone
import org.zalando.problem.AbstractThrowableProblem
import org.zalando.problem.Exceptional
import org.zalando.problem.Status
import org.zalando.problem.ThrowableProblem
import java.math.BigDecimal
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import java.util.Optional
import javax.annotation.processing.Generated
import javax.validation.Valid
import javax.validation.constraints.DecimalMax
import javax.validation.constraints.DecimalMin
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size
import kotlin.math.min

class KotlinTypeRegistry(
  val defaultModelPackageName: String,
  val generationMode: GenerationMode,
  val options: Set<Option>
) : TypeRegistry {

  data class Id(val value: String)

  enum class Option {
    ImplementModel,
    ValidationConstraints,
    JacksonAnnotations,
    AddGeneratedAnnotation,
    SuppressPublicApiWarnings
  }

  private val typeBuilders = mutableMapOf<ClassName, TypeSpec.Builder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()

  override fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path) {

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
      .groupBy { entry -> entry.key.enclosingClassName() }
      .toSortedMap { o1, o2 -> (o2?.canonicalName?.length ?: 0) - (o1?.canonicalName?.length ?: 0) }
      .values
      .flatMap { list -> list.sortedBy { it.key.canonicalName } }
      .forEach { (className, typeBuilder) ->
        // Is this a nested class?
        val enclosingClassName = className.enclosingClassName() ?: return@forEach
        typeBuilders[enclosingClassName]?.addType(typeBuilder.build())
      }

    return typeBuilders.mapValues { it.value.build() }
  }

  fun resolveTypeName(shape: Shape, context: KotlinResolutionContext): TypeName {

    val resolvedShape = shape.resolve

    var typeName = typeNameMappings[resolvedShape.id]
    if (typeName == null) {

      typeName = generateTypeName(resolvedShape, context)

      typeNameMappings[resolvedShape.id] = typeName
    }

    return typeName
  }

  fun addServiceType(className: ClassName, serviceType: TypeSpec.Builder) {

    if (options.contains(AddGeneratedAnnotation)) {
      serviceType.addAnnotation(
        AnnotationSpec.builder(Generated::class)
          .addMember("value = [%S]", javaClass.name)
          .addMember("date = %S", LocalDateTime.now().format(ISO_LOCAL_DATE_TIME))
          .build()
      )
    }
    if (options.contains(SuppressPublicApiWarnings)) {
      serviceType.addAnnotation(
        AnnotationSpec.builder(Suppress::class)
          .addMember("%S, %S", "RedundantVisibilityModifier", "RedundantUnitReturnType")
          .build()
      )
    }

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined")
    }
  }

  fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition
  ): ClassName {

    val problemPackageName = packageNameOf(problemTypeDefinition.definedIn)

    val problemTypeName =
      ClassName.bestGuess("$problemPackageName.${problemCode.toUpperCamelCase()}Problem")

    val problemTypeBuilder =
      TypeSpec.classBuilder(problemTypeName)
        .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
        .superclass(AbstractThrowableProblem::class.asTypeName())
        .addType(
          TypeSpec.companionObjectBuilder()
            .addProperty(
              PropertySpec.builder("TYPE", STRING)
                .addModifiers(KModifier.CONST)
                .initializer("%S", problemTypeDefinition.type)
                .build()
            )
            .addProperty(
              PropertySpec.builder("TYPE_URI", URI::class.asTypeName())
                .initializer("%T(%L)", URI::class.asTypeName(), "TYPE")
                .build()
            )
            .build()
        )
        .primaryConstructor(
          FunSpec.constructorBuilder()
            .apply {
              // Add all custom properties to constructor
              problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->
                addParameter(
                  ParameterSpec
                    .builder(
                      customPropertyName.kotlinIdentifierName,
                      resolveTypeReference(
                        customPropertyTypeNameStr,
                        problemTypeDefinition.source,
                        KotlinResolutionContext(problemTypeDefinition.definedIn, null)
                      )
                    )
                    .apply {
                      if (customPropertyName.kotlinIdentifierName != customPropertyName) {
                        addAnnotation(
                          AnnotationSpec.builder(JsonProperty::class)
                            .addMember("value = %S", customPropertyName)
                            .build()
                        )
                      }
                    }
                    .build()
                )
              }
            }
            .addParameter(
              ParameterSpec.builder("instance", URI::class.asTypeName().copy(nullable = true))
                .defaultValue("null")
                .build()
            )
            .addParameter(
              ParameterSpec.builder("cause", ThrowableProblem::class.asTypeName().copy(nullable = true))
                .defaultValue("null")
                .build()
            )
            .apply {
              if (options.contains(JacksonAnnotations)) {
                addAnnotation(JsonCreator::class)
              }
            }
            .build()
        )
        .addSuperclassConstructorParameter("TYPE_URI")
        .addSuperclassConstructorParameter("%S", problemTypeDefinition.title)
        .addSuperclassConstructorParameter(
          "%T.%L",
          Status::class.asTypeName(),
          Status.valueOf(problemTypeDefinition.status).name
        )
        .addSuperclassConstructorParameter("%S", problemTypeDefinition.detail)
        .addSuperclassConstructorParameter("instance")
        .addSuperclassConstructorParameter("cause")
        .apply {
          problemTypeDefinition.custom.map { (customPropertyName, customPropertyTypeNameStr) ->
            addProperty(
              PropertySpec
                .builder(
                  customPropertyName.kotlinIdentifierName,
                  resolveTypeReference(
                    customPropertyTypeNameStr,
                    problemTypeDefinition.source,
                    KotlinResolutionContext(problemTypeDefinition.definedIn, null)
                  ),
                  KModifier.PUBLIC
                )
                .initializer(customPropertyName.kotlinIdentifierName)
                .build()
            )
          }
        }
        .addFunction(
          FunSpec.builder("getCause")
            .returns(Exceptional::class.asTypeName().copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .addCode("return super.cause")
            .build()
        )

    if (options.contains(JacksonAnnotations)) {
      problemTypeBuilder.addAnnotation(
        AnnotationSpec.builder(JsonTypeName::class)
          .addMember("%T.TYPE", problemTypeName)
          .build()
      )
    }

    typeBuilders[problemTypeName] = problemTypeBuilder

    return problemTypeName
  }

  private fun resolveTypeReference(name: String, source: DomainElement, context: KotlinResolutionContext): TypeName =
    when (name.toLowerCase()) {
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
        val (element, unit) = context.resolveRef(name, source) ?: genError("Invalid type reference '$name'", source)
        element as? Shape ?: genError("Invalid type reference '$name'", source)
        val resContext = KotlinResolutionContext(unit, null)

        resolveReferencedTypeName(element, resContext)
      }
    }

  private fun resolveReferencedTypeName(shape: Shape, context: KotlinResolutionContext): TypeName =
    resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: ClassName,
    context: KotlinResolutionContext
  ): TypeName {

    val propertyContext = context.copy(
      suggestedTypeName = className.nestedClass(propertyShape.kotlinTypeName),
    )

    val typeName = resolveTypeName(propertyShape.range, propertyContext)
    return if (propertyShape.minCount ?: 0 == 0) {
      typeName.copy(nullable = true)
    } else {
      typeName
    }
  }

  private fun generateTypeName(shape: Shape, context: KotlinResolutionContext): TypeName {

    val kotlinTypeAnn = shape.findStringAnnotation(KotlinType, generationMode)
    if (kotlinTypeAnn != null) {
      return ClassName.bestGuess(kotlinTypeAnn)
    }

    return processShape(shape, context)
  }

  private fun processShape(shape: Shape, context: KotlinResolutionContext): TypeName =
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

  private fun processAnyShape(shape: AnyShape, context: KotlinResolutionContext): TypeName =
    when {
      shape.inheritsViaAggregation ->
        defineClass(
          shape,
          shape.and.first { it.isOrWasLink }.resolve,
          shape.and.first { !it.isOrWasLink } as NodeShape,
          context
        )

      shape.or.isNotEmpty() ->
        nearestCommonAncestor(shape.or, context) ?: ANY

      shape.xone.isNotEmpty() ->
        nearestCommonAncestor(shape.xone, context) ?: ANY

      else -> ANY
    }

  private fun processScalarShape(type: ScalarShape, context: KotlinResolutionContext): TypeName =
    when (type.dataType) {
      DataType.String() ->

        if (type.values.isNotEmpty()) {
          defineEnum(type, context)
        } else {
          when (type.format) {
            "time" -> LocalTime::class.asTypeName()
            "datetime-only", "date-time-only" -> LocalDateTime::class.asTypeName()
            else -> STRING
          }
        }

      DataType.Boolean() -> BOOLEAN

      DataType.Integer() ->
        when (type.format) {
          "int8" -> BYTE
          "int16" -> SHORT
          "int32", "int" -> INT
          "", null -> INT
          else -> genError("Integer format '${type.format}' is unsupported", type)
        }

      DataType.Long() -> LONG

      DataType.Float() -> FLOAT
      DataType.Double() -> DOUBLE
      DataType.Number() -> DOUBLE

      DataType.Decimal() -> BigDecimal::class.asTypeName()

      DataType.Duration() -> Duration::class.asTypeName()
      DataType.Date() -> LocalDate::class.asTypeName()
      DataType.Time() -> LocalTime::class.asTypeName()
      DataType.DateTimeOnly() -> LocalDateTime::class.asTypeName()
      DataType.DateTime() -> OffsetDateTime::class.asTypeName()

      DataType.Binary() -> BYTE_ARRAY

      else -> genError("Scalar data type '${type.dataType}' is unsupported", type)
    }

  private fun processArrayShape(type: ArrayShape, context: KotlinResolutionContext): TypeName {

    val elementType = resolveReferencedTypeName(type.items!!, context)

    val collectionType =
      if (type.uniqueItems == true) {
        SET
      } else {
        LIST
      }

    return collectionType.parameterizedBy(elementType)
  }

  private fun processUnionShape(type: UnionShape, context: KotlinResolutionContext): TypeName =
    if (type.makesNullable) {
      resolveReferencedTypeName(type.nullableType, context).copy(nullable = true)
    } else {
      nearestCommonAncestor(type.anyOf, context) ?: ANY
    }

  private fun processNodeShape(type: NodeShape, context: KotlinResolutionContext): TypeName {

    if (type.properties.isEmpty() && type.inherits.size == 1 && context.unit.findInheritingTypes(type).isEmpty()) {
      return resolveReferencedTypeName(type.inherits[0], context)
    }

    if (type.properties.isEmpty() && type.inherits.isEmpty() && type.closed != true) {

      val allTypes = collectTypes(type.properties().map { it.range })

      val commonType = nearestCommonAncestor(allTypes, context) ?: ANY

      return MAP.parameterizedBy(STRING, commonType)
    }

    return defineClass(type, type.inherits.firstOrNull()?.let { it.resolve as NodeShape }, type, context)
  }

  private fun defineClass(
    shape: Shape,
    superShape: Shape?,
    propertyContainerShape: NodeShape,
    context: KotlinResolutionContext
  ): TypeName {

    val className = typeNameOf(shape, context)

    // Check for an existing class built or being built
    val existingBuilder = typeBuilders[className]
    if (existingBuilder != null) {
      if (existingBuilder.tags[Id::class] != Id(shape.id)) {
        genError("Multiple classes defined with name '$className'", shape)
      } else {
        return className
      }
    }

    val typeBuilder =
      defineType(className) { name ->
        if (options.contains(ImplementModel))
          TypeSpec.classBuilder(name)
        else
          TypeSpec.interfaceBuilder(name)
      }

    typeBuilder.tag(Id::class, Id(shape.id))

    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context)
      if (options.contains(ImplementModel)) {
        typeBuilder.superclass(superClassName)
      } else {
        typeBuilder.addSuperinterface(superClassName)
      }
    }

    var inheritedProperties = collectProperties(superShape)
    var declaredProperties = propertyContainerShape.properties

    val inheritingTypes = context.unit.findInheritingTypes(shape)

    if (inheritingTypes.isNotEmpty() && options.contains(ImplementModel)) {
      typeBuilder.modifiers.add(KModifier.OPEN)
    }

    if (inheritedProperties.isNotEmpty() || declaredProperties.isNotEmpty()) {

      /*
        Computed discriminator values (for jackson generation)
       */

      if (options.contains(JacksonAnnotations)) {

        val discriminatorPropertyName = findDiscriminatorPropertyName(shape)
        if (discriminatorPropertyName != null) {

          val discriminatorProperty =
            (inheritedProperties + declaredProperties).find { it.name == discriminatorPropertyName }
              ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

          val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)

          declaredProperties = declaredProperties.filter { it.name != discriminatorPropertyName }
          inheritedProperties = inheritedProperties.filter { it.name != discriminatorPropertyName }

          // Add abstract discriminator if this is the root of the discriminator tree

          if (propertyContainerShape.discriminator == discriminatorPropertyName) {

            val discriminatorBuilder =
              PropertySpec.builder(discriminatorProperty.kotlinIdentifierName, discriminatorPropertyTypeName)

            if (shape.findBoolAnnotation(ExternallyDiscriminated, generationMode) == true) {
              discriminatorBuilder.addAnnotation(
                AnnotationSpec.builder(JsonIgnore::class)
                  .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                  .build()
              )
            }

            if (options.contains(ImplementModel)) {
              discriminatorBuilder.addModifiers(KModifier.ABSTRACT)
              typeBuilder.addModifiers(KModifier.ABSTRACT)
            }

            typeBuilder.addProperty(discriminatorBuilder.build())
          } else if (options.contains(ImplementModel)) {

            // Add concrete discriminator for leaf of the discriminated tree

            val discriminatorBuilder =
              PropertySpec.builder(discriminatorProperty.kotlinIdentifierName, discriminatorPropertyTypeName)
                .addModifiers(KModifier.OVERRIDE)

            val discriminatorValue = findDiscriminatorPropertyValue(shape, context) ?: shape.name!!

            val isEnum = typeBuilders[discriminatorPropertyTypeName]?.enumConstants?.isNotEmpty() ?: false
            if (isEnum) {
              discriminatorBuilder.getter(
                FunSpec.getterBuilder()
                  .addStatement(
                    "return %T.%L",
                    discriminatorPropertyTypeName,
                    discriminatorValue.toUpperCamelCase()
                  )
                  .build()
              )
            } else {
              discriminatorBuilder.getter(
                FunSpec.getterBuilder()
                  .addStatement("return %S", discriminatorValue)
                  .build()
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
        if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {
          paramConsBuilder = FunSpec.constructorBuilder()
          inheritedProperties.forEach { inheritedProperty ->
            paramConsBuilder.addParameter(
              inheritedProperty.kotlinIdentifierName,
              resolvePropertyTypeName(inheritedProperty, className, context)
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
            FunSpec.builder("hashCode")
              .addModifiers(KModifier.OVERRIDE)
              .returns(INT)
          if (superShape != null)
            hashBuilder.addStatement("var result = 31 * super.hashCode()")
          else
            hashBuilder.addStatement("var result = 1")
        } else {
          hashBuilder = null
        }

        // equals builder
        //
        val equalsBuilder: FunSpec.Builder?
        if (definedProperties.isNotEmpty() || inheritingTypes.isNotEmpty()) {

          equalsBuilder =
            FunSpec.builder("equals")
              .addModifiers(KModifier.OVERRIDE)
              .returns(BOOLEAN)
              .addParameter("other", ANY.copy(nullable = true))
              .addStatement("if (this === other) return true")
              .addStatement("if (javaClass != other?.javaClass) return false", className)

          if (definedProperties.isNotEmpty()) {
            equalsBuilder
              .addCode("\n")
              .addStatement("other as %T", className)
          }

          equalsBuilder.addCode("\n")

          if (superShape != null) {
            equalsBuilder.addStatement("if (!super.equals(other)) return false")
          }
        } else {
          equalsBuilder = null
        }

        // Generate related code for each property
        //
        declaredProperties.forEach { declaredProperty ->

          val declaredPropertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)

          val declaredPropertyBuilder =
            PropertySpec.builder(declaredProperty.kotlinIdentifierName, declaredPropertyTypeName)

          val externalDiscriminatorPropertyName =
            declaredProperty.range.findStringAnnotation(ExternalDiscriminator, generationMode)
          if (externalDiscriminatorPropertyName != null) {

            declaredProperty.range.resolve as? NodeShape
              ?: genError(" Externally discriminated types must be 'object'", declaredProperty)

            (inheritedProperties + declaredProperties).find { it.name == externalDiscriminatorPropertyName }
              ?: genError("External discriminator '$externalDiscriminatorPropertyName' not found in object", shape)

            if (options.contains(JacksonAnnotations)) {
              addJacksonPolymorphismOverride(declaredPropertyBuilder, externalDiscriminatorPropertyName)
            }
          }

          val implAnn = declaredProperty.range.findAnnotation(KotlinImpl, generationMode) as? ObjectNode
          if (implAnn != null) {

            declaredPropertyBuilder.addAnnotation(
              AnnotationSpec.builder(JsonIgnore::class)
                .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
                .build()
            )

            val code = implAnn.getValue("code") ?: ""
            val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
            val convertedCodeParams = codeParams.map { codeParam ->
              val atype = codeParam.getValue("type")
              val avalue = codeParam.getValue("value")
              if (atype != null && avalue != null) {
                when (atype) {
                  "Type" -> ClassName.bestGuess(avalue.toString())
                  else -> avalue.toString()
                }
              } else
                ""
            }

            declaredPropertyBuilder.getter(
              FunSpec.getterBuilder()
                .addStatement(code, *convertedCodeParams.toTypedArray())
                .build()
            )
          } else {

            applyUseSiteAnnotations(declaredProperty, declaredPropertyTypeName) {
              declaredPropertyBuilder.addAnnotation(it)
            }

            declaredPropertyBuilder.initializer(declaredProperty.kotlinIdentifierName)

            if (declaredProperty.kotlinIdentifierName != declaredProperty.name) {
              declaredPropertyBuilder
                .addAnnotation(
                  AnnotationSpec.builder(JsonProperty::class)
                    .addMember("value = %S", declaredProperty.name())
                    .build()
                )
            }

            // Add constructor parameter
            //
            paramConsBuilder?.addParameter(declaredProperty.kotlinIdentifierName, declaredPropertyTypeName)

            // Add toString value
            //
            toStringCode.add(
              CodeBlock.of("%L='\$%L'", declaredProperty.kotlinIdentifierName, declaredProperty.kotlinIdentifierName)
            )

            // Add hashCode value
            //
            if (hashBuilder != null) {
              val hashMember = if (declaredPropertyTypeName.isArray) "contentHashCode" else "hashCode"
              if (declaredPropertyTypeName.isNullable) {
                hashBuilder.addStatement(
                  "result = 31 * result + (%L?.%L() ?: 0)",
                  declaredProperty.kotlinIdentifierName, hashMember
                )
              } else {
                hashBuilder.addStatement(
                  "result = 31 * result + %L.%L()",
                  declaredProperty.kotlinIdentifierName, hashMember
                )
              }
            }

            // Add equals value
            //
            if (equalsBuilder != null) {
              if (declaredPropertyTypeName.isArray) {
                if (declaredPropertyTypeName.isNullable) {
                  equalsBuilder
                    .beginControlFlow("if (%L != null)", declaredProperty.kotlinIdentifierName)
                    .addStatement("if (other.%L == null) return false", declaredProperty.kotlinIdentifierName)
                    .addStatement(
                      "if (!%L.contentEquals(other.%L)) return false",
                      declaredProperty.kotlinIdentifierName, declaredProperty.kotlinIdentifierName
                    )
                    .endControlFlow()
                    .addStatement("else if (other.%L != null) return false", declaredProperty.kotlinIdentifierName)
                } else {
                  equalsBuilder.addStatement(
                    "if (!%L.contentEquals(other.%L)) return false",
                    declaredProperty.kotlinIdentifierName, declaredProperty.kotlinIdentifierName
                  )
                }
              } else {
                equalsBuilder.addStatement(
                  "if (%L != other.%L) return false",
                  declaredProperty.kotlinIdentifierName, declaredProperty.kotlinIdentifierName
                )
              }
            }
          }

          typeBuilder.addProperty(declaredPropertyBuilder.build())
        }

        // Add copy method
        //
        if (inheritingTypes.isEmpty()) {

          val copyBuilder =
            FunSpec.builder("copy")
              .addCode("return %T(", className)

          val copyArgs =
            (inheritedProperties + definedProperties).map { copyProperty ->

              val propertyTypeName = resolvePropertyTypeName(copyProperty, className, context)

              copyBuilder.addParameter(
                ParameterSpec.builder(copyProperty.kotlinIdentifierName, propertyTypeName.copy(nullable = true))
                  .defaultValue("null")
                  .build()
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
          CodeBlock.of(
            "%L(%L)",
            className.simpleName, toStringCode.joinToString(",\n ")
          ).toString()

        typeBuilder.addFunction(
          FunSpec.builder("toString")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return %P", toStringTemplate)
            .build()
        )
      } else {

        // Generate related code for each property
        //
        declaredProperties.forEach { declaredProperty ->

          val propertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)

          // Add public field
          //
          val propertyBuilder = PropertySpec.builder(declaredProperty.kotlinIdentifierName, propertyTypeName)

          applyUseSiteAnnotations(declaredProperty, propertyTypeName) {

            val getAnn = it.toBuilder()
              .useSiteTarget(AnnotationSpec.UseSiteTarget.GET)
              .build()

            propertyBuilder.addAnnotation(getAnn)
          }

          typeBuilder.addProperty(propertyBuilder.build())
        }
      }
    }

    if (shape.findBoolAnnotation(Patchable, generationMode) == true) {

      val patchClassName = className.nestedClass("Patch")

      val patchClassBuilder =
        TypeSpec.classBuilder(patchClassName)
          .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
          .addModifiers(KModifier.DATA)
      val patchClassConsBuilder = FunSpec.constructorBuilder()

      val patchFields = mutableListOf<CodeBlock>()

      for (propertyDecl in propertyContainerShape.properties) {
        val propertyTypeName = resolveReferencedTypeName(propertyDecl.range, context).copy(nullable = false)
        val optionalPropertyTypeName =
          Optional::class.asTypeName().parameterizedBy(propertyTypeName).copy(nullable = true)

        patchClassBuilder.addProperty(
          PropertySpec.builder(propertyDecl.kotlinIdentifierName, optionalPropertyTypeName)
            .initializer(propertyDecl.kotlinIdentifierName)
            .build()
        )
        patchClassConsBuilder.addParameter(
          ParameterSpec.builder(propertyDecl.kotlinIdentifierName, optionalPropertyTypeName)
            .build()
        )

        patchFields.add(
          CodeBlock.of(
            "if (source.has(%S)) Optional.ofNullable(%L) else null",
            propertyDecl.kotlinIdentifierName, propertyDecl.kotlinIdentifierName
          )
        )
      }

      patchClassBuilder.primaryConstructor(patchClassConsBuilder.build())

      typeBuilder.addFunction(
        FunSpec.builder("patch")
          .addParameter(
            ParameterSpec.builder("source", com.fasterxml.jackson.databind.node.ObjectNode::class.asTypeName())
              .build()
          )
          .returns(patchClassName)
          .addCode(
            CodeBlock.builder()
              .add("return %T(", patchClassName).indent().add("\n")
              .add(patchFields.joinToCode(",\n"))
              .unindent().add("\n)")
              .build()
          )
          .build()
      )

      typeBuilder.addType(patchClassBuilder.build())
    }

    if (typeBuilder.modifiers.contains(KModifier.ABSTRACT)) {
      typeBuilder.modifiers.remove(KModifier.OPEN)
    }

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    if (options.contains(JacksonAnnotations)) {

      if (!propertyContainerShape.discriminator.isNullOrBlank()) {
        addJacksonPolymorphism(propertyContainerShape, inheritingTypes, typeBuilder, context)
      }
    }

    return className
  }

  private fun defineEnum(shape: Shape, context: KotlinResolutionContext): TypeName {

    val className = typeNameOf(shape, context)

    val enumBuilder = defineType(className, (TypeSpec)::enumBuilder)

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->

      if (options.contains(JacksonAnnotations)) {
        val enumType =
          TypeSpec.anonymousClassBuilder()
            .addAnnotation(
              AnnotationSpec.builder(JsonProperty::class)
                .addMember("value = %S", enum.scalarValue!!)
                .build()
            )
            .build()

        enumBuilder.addEnumConstant(enum.kotlinEnumName, enumType)
      } else {
        enumBuilder.addEnumConstant(enum.kotlinEnumName)
      }
    }

    return className
  }

  private fun defineType(
    className: ClassName,
    builderBlock: (ClassName) -> TypeSpec.Builder
  ): TypeSpec.Builder {

    val builder = builderBlock(className)

    builder.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, builder) != null) {
      genError("Multiple types with name '${className.simpleName}' defined in package '${className.packageName}'")
    }

    if (className.enclosingClassName() == null) {

      if (options.contains(AddGeneratedAnnotation)) {
        builder.addAnnotation(
          AnnotationSpec.builder(Generated::class)
            .addMember("value = [%S]", javaClass.name)
            .addMember("date = %S", LocalDateTime.now().format(ISO_LOCAL_DATE_TIME))
            .build()
        )
      }

      if (options.contains(SuppressPublicApiWarnings)) {
        builder.addAnnotation(
          AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "RedundantVisibilityModifier")
            .build()
        )
      }
    }

    return builder
  }

  fun applyUseSiteAnnotations(use: Shape, typeName: TypeName, applicator: (AnnotationSpec) -> Unit) {

    if (options.contains(ValidationConstraints)) {
      when (use) {
        is PropertyShape -> applyUseSiteValidationAnnotations(use.range, typeName, applicator)
        else -> applyUseSiteValidationAnnotations(use, typeName, applicator)
      }
    }
  }

  private fun applyUseSiteValidationAnnotations(use: Shape, typeName: TypeName, applicator: (AnnotationSpec) -> Unit) {

    when (use) {

      is ScalarShape -> {

        when (use.dataType) {
          "http://www.w3.org/2001/XMLSchema#string" -> {

            // Apply min/max (if set)
            var sizeBuilder: AnnotationSpec.Builder? = null
            if (use.maxLength != null && use.maxLength != Integer.MAX_VALUE) {
              sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(Size::class)
              sizeBuilder.addMember("max = %L", use.maxLength().toString())
            }
            if (use.minLength != null && use.minLength != 0) {
              sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(Size::class)
              sizeBuilder.addMember("min = %L", use.minLength().toString())
            }
            sizeBuilder?.let {
              applicator.invoke(it.build())
            }

            // Apply pattern (if set)
            if (!use.pattern.isNullOrBlank() && use.pattern != ".*") {
              applicator.invoke(
                AnnotationSpec.builder(Pattern::class)
                  .addMember("regexp = %P", use.pattern())
                  .build()
              )
            }
          }

          "http://www.w3.org/2001/XMLSchema#integer", "http://www.w3.org/2001/XMLSchema#long" -> {

            // Apply max (if set)
            if (use.maximum != null) {
              applicator.invoke(
                AnnotationSpec.builder(Max::class)
                  .addMember("value = %L", use.maximum!!.toLong())
                  .build()
              )
            }

            // Apply min (if set)
            if (use.minimum != null) {
              applicator.invoke(
                AnnotationSpec.builder(Min::class)
                  .addMember("value = %L", use.minimum!!.toLong())
                  .build()
              )
            }
          }

          "http://www.w3.org/2001/XMLSchema#float", "http://www.w3.org/2001/XMLSchema#double" -> {

            // Apply max (if set)
            if (use.maximum != null) {
              applicator.invoke(
                AnnotationSpec.builder(DecimalMax::class)
                  .addMember("value = %S", use.maximum!!.toBigDecimal())
                  .build()
              )
            }
            // Apply min (if set)
            if (use.minimum != null) {
              applicator.invoke(
                AnnotationSpec.builder(DecimalMin::class)
                  .addMember("value = %S", use.minimum!!.toBigDecimal())
                  .build()
              )
            }
          }
        }
      }

      is ArrayShape -> {

        // Apply max (if set)
        var sizeBuilder: AnnotationSpec.Builder? = null
        if (use.maxItems != null && use.maxItems != Integer.MAX_VALUE) {
          sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(Size::class)
          sizeBuilder.addMember("max = %L", use.maxItems().toString())
        }

        // Apply min (if set)
        if (use.minItems != null && use.minItems != 0) {
          sizeBuilder = sizeBuilder ?: AnnotationSpec.builder(Size::class)
          sizeBuilder.addMember("min = %L", use.minItems().toString())
        }
        sizeBuilder?.let {
          applicator.invoke(it.build())
        }
      }

      is NodeShape -> {

        // Apply @Valid for nested types
        if (typeName != ANY) {
          applicator.invoke(
            AnnotationSpec.builder(Valid::class)
              .build()
          )
        }
      }
    }
  }

  private fun addJacksonPolymorphismOverride(
    propertySpec: PropertySpec.Builder,
    externalDiscriminatorPropertyName: String
  ) {

    propertySpec.addAnnotation(
      AnnotationSpec.builder(JsonTypeInfo::class)
        .addMember("use = %T.%L", JsonTypeInfo.Id::class, "NAME")
        .addMember("include = %T.%L", JsonTypeInfo.As::class, "EXTERNAL_PROPERTY")
        .addMember("property = %S", externalDiscriminatorPropertyName)
        .build()
    )
  }

  private fun addJacksonPolymorphism(
    shape: NodeShape,
    inheritingTypes: List<Shape>,
    classBuilder: TypeSpec.Builder,
    context: KotlinResolutionContext
  ) {

    val discriminatorMappings = buildDiscriminatorMappings(shape, context)

    val subTypes = inheritingTypes
      .map { inheritingType ->

        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingType.id }?.key

        "%T(name = %S, value = %T::class)" to listOf(
          JsonSubTypes.Type::class,
          inheritingType.anyInheritanceNode?.discriminatorValue ?: mappedDiscriminator ?: inheritingType.name,
          resolveReferencedTypeName(inheritingType, context)
        )
      }

    if (subTypes.isNotEmpty()) {

      if (shape.findBoolAnnotation(ExternallyDiscriminated, generationMode) != true) {
        classBuilder.addAnnotation(
          AnnotationSpec.builder(JsonTypeInfo::class)
            .addMember("use = %T.%L", JsonTypeInfo.Id::class, "NAME")
            .addMember("include = %T.%L", JsonTypeInfo.As::class, "EXISTING_PROPERTY")
            .addMember("property = %S", shape.discriminator())
            .build()
        )
      }

      classBuilder.addAnnotation(
        AnnotationSpec.builder(JsonSubTypes::class)
          .addMember(
            CodeBlock.builder()
              .add("value = [")
              .indent()
              .add(
                "\n${subTypes.joinToString(",\n") { it.first }}",
                *subTypes.map { it.second }.flatten().toTypedArray()
              )
              .unindent()
              .add("\n]")
              .build()
          )
          .build()
      )
    }
  }

  private fun typeNameOf(shape: Shape, context: KotlinResolutionContext): ClassName {

    if (context.suggestedTypeName != null) {
      val typeIdFrag = URI(shape.id).fragment
      val typeName = shape.name
      if (typeIdFrag.endsWith("/property/$typeName/$typeName") || !typeIdFrag.startsWith("/declarations")) {
        return context.suggestedTypeName
      }
    }

    val pkgName = packageNameOf(shape, context)

    val nestedAnn = shape.findAnnotation(Nested, generationMode) as? ObjectNode

    return if (nestedAnn != null) {

      val nestedEnclosedIn = nestedAnn.getValue("enclosedIn")
        ?: genError("Nested annotation is missing parent", nestedAnn)

      val (nestedEnclosingType, nestedEnclosingTypeUnit) = context.resolveRef(nestedEnclosedIn, shape)
        ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

      nestedEnclosingType as? Shape
        ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

      val nestedEnclosingTypeContext = KotlinResolutionContext(nestedEnclosingTypeUnit, null)

      val nestedEnclosingTypeName = resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as? ClassName
        ?: genError("Nested annotation references non-defining enclosing type", nestedAnn)

      val nestedName = nestedAnn.getValue("name")
        ?: genError("Nested annotation is missing name", nestedAnn)

      nestedEnclosingTypeName.nestedClass(nestedName)
    } else {

      ClassName.bestGuess("$pkgName.${shape.kotlinTypeName}")
    }
  }

  private fun packageNameOf(shape: Shape, context: KotlinResolutionContext): String =
    packageNameOf(context.unit.findDeclaringUnit(shape))

  private fun packageNameOf(unit: BaseUnit?): String =
    (unit as? CustomizableElement)?.findStringAnnotation(KotlinModelPkg, generationMode)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(KotlinModelPkg, generationMode)
      ?: defaultModelPackageName

  private fun collectTypes(types: List<Shape>) = types.flatMap { if (it is UnionShape) it.anyOf else listOf(it) }

  private fun collectProperties(shape: Shape?): List<PropertyShape> =
    when {
      shape == null -> emptyList()
      shape is NodeShape && shape.inheritsViaInherits -> collectInheritedProperties(shape)
      shape.inheritsViaAggregation -> collectAggregatedProperties(shape)
      shape is NodeShape -> shape.properties
      else -> emptyList()
    }

  private fun collectAggregatedProperties(shape: Shape): List<PropertyShape> {
    val parent = shape.aggregateInheritanceSuper.resolve
    val current = shape.aggregateInheritanceNode
    val parentProperties =
      when {
        parent.inheritsViaAggregation -> collectAggregatedProperties(parent)
        parent is NodeShape -> parent.properties
        else -> emptyList()
      }
    return parentProperties.plus(current.properties)
  }

  private fun collectInheritedProperties(shape: NodeShape): List<PropertyShape> {
    val parent = shape.inheritsInheritanceSuper.resolve
    val current = shape.inheritsInheritanceNode
    val parentProperties =
      when {
        parent is NodeShape && parent.inheritsViaInherits -> collectInheritedProperties(parent)
        parent is NodeShape -> parent.properties
        else -> emptyList()
      }
    return parentProperties.plus(current.properties)
  }

  private fun nearestCommonAncestor(types: List<Shape>, context: KotlinResolutionContext): ClassName? {

    var currentClassNameHierarchy: List<ClassName>? = null
    for (type in types) {
      val propertyClassNameHierarchy = classNameHierarchy(type.resolve, context) ?: break
      currentClassNameHierarchy =
        if (currentClassNameHierarchy == null)
          propertyClassNameHierarchy
        else
          (0 until min(propertyClassNameHierarchy.size, currentClassNameHierarchy.size))
            .takeWhile { propertyClassNameHierarchy[it] == currentClassNameHierarchy!![it] }
            .map { propertyClassNameHierarchy[it] }
    }

    return currentClassNameHierarchy?.firstOrNull()
  }

  private fun classNameHierarchy(shape: Shape, context: KotlinResolutionContext): List<ClassName>? {

    val names = mutableListOf<ClassName>()

    var current: Shape? = shape
    while (current != null) {
      val currentClass = resolveReferencedTypeName(current, context) as? ClassName ?: return null
      names.add(currentClass)
      current = current.anyInheritanceSuper
    }

    return names.reversed()
  }

  private fun findDiscriminatorPropertyName(shape: Shape): String? =
    when {
      shape is NodeShape && !shape.discriminator.isNullOrEmpty() -> shape.discriminator
      shape.anyInheritance && !shape.anyInheritanceNode?.discriminator.isNullOrEmpty() -> shape.anyInheritanceNode?.discriminator
      else -> shape.anyInheritanceSuper?.let { findDiscriminatorPropertyName(it) }
    }

  private fun findDiscriminatorPropertyValue(shape: Shape, context: KotlinResolutionContext): String? =
    if (shape is NodeShape && !shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = shape.inheritanceRoot as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(shape: NodeShape, context: KotlinResolutionContext): Map<String, String> =
    shape.discriminatorMapping.mapNotNull { mapping ->
      val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
      mapping.templateVariable().value()!! to refElement.id
    }.toMap()
}
