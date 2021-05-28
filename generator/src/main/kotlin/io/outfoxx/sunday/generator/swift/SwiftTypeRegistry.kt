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

package io.outfoxx.sunday.generator.swift

import amf.client.model.domain.AnyShape
import amf.client.model.domain.ArrayNode
import amf.client.model.domain.ArrayShape
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
import io.outfoxx.sunday.generator.APIAnnotationName.ExternalDiscriminator
import io.outfoxx.sunday.generator.APIAnnotationName.ExternallyDiscriminated
import io.outfoxx.sunday.generator.APIAnnotationName.Nested
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.SwiftImpl
import io.outfoxx.sunday.generator.APIAnnotationName.SwiftType
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.TypeRegistry
import io.outfoxx.sunday.generator.common.DefinitionLocation
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.AddGeneratedHeader
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE
import io.outfoxx.sunday.generator.swift.utils.ARRAY_ANY
import io.outfoxx.sunday.generator.swift.utils.ARRAY_ANY_OPTIONAL
import io.outfoxx.sunday.generator.swift.utils.CODABLE
import io.outfoxx.sunday.generator.swift.utils.CODING_KEY
import io.outfoxx.sunday.generator.swift.utils.CUSTOM_STRING_CONVERTIBLE
import io.outfoxx.sunday.generator.swift.utils.DATE
import io.outfoxx.sunday.generator.swift.utils.DECIMAL
import io.outfoxx.sunday.generator.swift.utils.DECODER
import io.outfoxx.sunday.generator.swift.utils.DECODING_ERROR
import io.outfoxx.sunday.generator.swift.utils.DESCRIPTION_BUILDER
import io.outfoxx.sunday.generator.swift.utils.DICTIONARY_STRING_ANY
import io.outfoxx.sunday.generator.swift.utils.DICTIONARY_STRING_ANY_OPTIONAL
import io.outfoxx.sunday.generator.swift.utils.ENCODER
import io.outfoxx.sunday.generator.swift.utils.ENCODING_ERROR
import io.outfoxx.sunday.generator.swift.utils.PROBLEM
import io.outfoxx.sunday.generator.swift.utils.URL
import io.outfoxx.sunday.generator.swift.utils.swiftIdentifierName
import io.outfoxx.sunday.generator.swift.utils.swiftTypeName
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
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findInheritingTypes
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.flattened
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
import io.outfoxx.sunday.generator.utils.minCount
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.optional
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.properties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.required
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.xone
import io.outfoxx.swiftpoet.ANY
import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.BOOL
import io.outfoxx.swiftpoet.CASE_ITERABLE
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DICTIONARY
import io.outfoxx.swiftpoet.DOUBLE
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FLOAT
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.INT
import io.outfoxx.swiftpoet.INT16
import io.outfoxx.swiftpoet.INT32
import io.outfoxx.swiftpoet.INT64
import io.outfoxx.swiftpoet.INT8
import io.outfoxx.swiftpoet.Modifier.FILEPRIVATE
import io.outfoxx.swiftpoet.Modifier.OVERRIDE
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.REQUIRED
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.OPTIONAL
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.ParameterizedTypeName
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.SET
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.SelfTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.VOID
import io.outfoxx.swiftpoet.joinToCode
import io.outfoxx.swiftpoet.parameterizedBy
import io.outfoxx.swiftpoet.tag
import java.net.URI
import java.nio.file.Path
import kotlin.math.min

class SwiftTypeRegistry(
  val options: Set<Option>
) : TypeRegistry {

  enum class Option {
    AddGeneratedHeader,
  }

  private val typeBuilders = mutableMapOf<DeclaredTypeName, TypeSpec.Builder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()
  private var referenceTypes = mutableMapOf<TypeName, TypeName>()

  override fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path) {

    val builtTypes = buildTypes()

    builtTypes.entries
      .filter { it.key.topLevelTypeName() == it.key }
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { FileSpec.get(it.key.moduleName, it.value) }
      .forEach { it.writeTo(outputDirectory) }
  }

  fun buildTypes(): Map<DeclaredTypeName, TypeSpec> {

    // Add nested classes to parents
    typeBuilders.entries
      .toList()
      .sortedByDescending { it.key.simpleNames.size }
      .forEach { (className, typeBuilder) ->
        // Is this a nested class?
        val enclosingClassName = className.enclosingTypeName() ?: return@forEach
        typeBuilders[enclosingClassName]?.addType(typeBuilder.build())
      }

    return typeBuilders.mapValues { it.value.build() }
  }

  fun resolveTypeName(shape: Shape, context: SwiftResolutionContext): TypeName {

    val resolvedShape = shape.resolve

    var typeName = typeNameMappings[resolvedShape.id]
    if (typeName == null) {

      typeName = generateTypeName(resolvedShape, context)

      typeNameMappings[resolvedShape.id] = typeName
    }

    return typeName
  }

  fun addServiceType(className: DeclaredTypeName, serviceType: TypeSpec.Builder) {

    serviceType.addModifiers(PUBLIC)

    if (options.contains(AddGeneratedHeader)) {
      serviceType.addDoc(GenerationHeaders.create("${className.simpleName}.swift"))
    }

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined")
    }
  }

  fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition
  ): DeclaredTypeName {

    val problemTypeName =
      DeclaredTypeName.typeName(".${problemCode.toUpperCamelCase()}Problem")

    val problemCodingKeysTypeName = problemTypeName.nestedType(CODING_KEYS_NAME)

    val problemTypeBuilder =
      TypeSpec.classBuilder(problemTypeName)
        .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
        .addModifiers(PUBLIC)
        .addSuperType(PROBLEM)
        .addProperty(
          PropertySpec.builder("type", URL, PUBLIC)
            .addModifiers(STATIC)
            .initializer("%T(string: %S)!", URL, problemTypeDefinition.type)
            .build()
        )
        .addFunction(
          FunctionSpec.constructorBuilder()
            .apply {
              // Add all custom properties to constructor
              problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->
                addParameter(
                  ParameterSpec
                    .builder(
                      customPropertyName.swiftIdentifierName,
                      resolveTypeReference(
                        customPropertyTypeNameStr,
                        problemTypeDefinition.source,
                        SwiftResolutionContext(problemTypeDefinition.definedIn, null)
                      )
                    )
                    .build()
                )
              }
            }
            .addParameter(
              ParameterSpec.builder("instance", URL.makeOptional())
                .defaultValue("nil")
                .build()
            )
            .apply {
              problemTypeDefinition.custom.map {
                addStatement("self.%N = %N", it.key.swiftIdentifierName, it.key.swiftIdentifierName)
              }
            }
            .addStatement(
              "super.init(type: %T.type,%Wtitle: %S,%Wstatus: %L,%Wdetail: %S,%Winstance: instance,%Wparameters: nil)",
              SelfTypeName.INSTANCE,
              problemTypeDefinition.title,
              problemTypeDefinition.status,
              problemTypeDefinition.detail,
            )
            .build()
        )
        .apply {
          problemTypeDefinition.custom.map { (customPropertyName, customPropertyTypeNameStr) ->
            addProperty(
              PropertySpec.builder(
                customPropertyName.swiftIdentifierName,
                resolveTypeReference(
                  customPropertyTypeNameStr,
                  problemTypeDefinition.source,
                  SwiftResolutionContext(problemTypeDefinition.definedIn, null)
                ),
                PUBLIC
              )
                .build()
            )
          }
        }
        .addProperty(
          PropertySpec.builder("description", STRING, OVERRIDE, PUBLIC)
            .getter(
              FunctionSpec.getterBuilder()
                .addStatement(
                  "return %T(%T.self)\n" +
                    ".add(type, named: %S)\n" +
                    ".add(title, named: %S)\n" +
                    ".add(status, named: %S)\n" +
                    ".add(detail, named: %S)\n" +
                    ".add(instance, named: %S)\n" +
                    problemTypeDefinition.custom.map { ".add(%N, named: %S)\n" }.joinToString("") +
                    ".build()",
                  DESCRIPTION_BUILDER, SelfTypeName.INSTANCE, "type", "title", "status", "detail", "instance",
                  *problemTypeDefinition.custom
                    .flatMap { listOf(it.key.swiftIdentifierName, it.key.swiftIdentifierName) }
                    .toTypedArray()
                )
                .build()
            )
            .build()
        )
        .addFunction(
          FunctionSpec.constructorBuilder()
            .addModifiers(PUBLIC, REQUIRED)
            .addParameter("from", "decoder", DECODER)
            .throws(true)
            .apply {
              if (problemTypeDefinition.custom.isNotEmpty()) {
                addStatement("let container = try decoder.container(keyedBy: %T.self)", problemCodingKeysTypeName)
              }
              problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->
                val customPropertyTypeName =
                  resolveTypeReference(
                    customPropertyTypeNameStr,
                    problemTypeDefinition.source,
                    SwiftResolutionContext(problemTypeDefinition.definedIn, null)
                  )
                addStatement(
                  "self.%N = try container.decode%L(%T.self, forKey: %T.%N)",
                  customPropertyName.swiftIdentifierName,
                  if (customPropertyTypeName.optional) "IfPresent" else "",
                  customPropertyTypeName.makeNonOptional(),
                  problemCodingKeysTypeName,
                  customPropertyName.swiftIdentifierName
                )
              }
            }
            .addStatement("try super.init(from: decoder)")
            .build()
        )
        .addFunction(
          FunctionSpec.builder("encode")
            .addModifiers(PUBLIC, OVERRIDE)
            .addParameter("to", "encoder", ENCODER)
            .throws(true)
            .addStatement("try super.encode(to: encoder)")
            .apply {
              if (problemTypeDefinition.custom.isNotEmpty()) {
                addStatement("var container = encoder.container(keyedBy: %T.self)", problemCodingKeysTypeName)
              }
              problemTypeDefinition.custom.forEach {
                addStatement(
                  "try container.encode(self.%N, forKey: %T.%N)",
                  it.key.swiftIdentifierName, problemCodingKeysTypeName, it.key.swiftIdentifierName
                )
              }
            }
            .build()
        )

    if (problemTypeDefinition.custom.isNotEmpty()) {

      val problemCodingKeysTypeBuilder =
        TypeSpec.enumBuilder(CODING_KEYS_NAME)
          .addModifiers(FILEPRIVATE)
          .addSuperType(STRING)
          .addSuperType(CODING_KEY)

      problemTypeDefinition.custom.forEach {
        problemCodingKeysTypeBuilder.addEnumCase(it.key.swiftIdentifierName, it.key)
      }

      problemTypeBuilder.addType(problemCodingKeysTypeBuilder.build())
    }

    typeBuilders[problemTypeName] = problemTypeBuilder

    return problemTypeName
  }

  private fun resolveTypeReference(nameStr: String, source: DomainElement, context: SwiftResolutionContext): TypeName {
    val typeNameStr = nameStr.removeSuffix("?")
    val elementTypeNameStr = typeNameStr.removeSuffix("[]")
    val elementTypeName =
      when (elementTypeNameStr.toLowerCase()) {
        "boolean" -> BOOL
        "integer" -> INT
        "number" -> DOUBLE
        "string" -> STRING
        "object" -> DICTIONARY_STRING_ANY
        "any" -> ANY
        "file" -> DATA
        "time-ony" -> DATE
        "date-ony" -> DATE
        "datetime-only" -> DATE
        "datetime" -> DATE
        else -> {
          val (element, unit) = context.resolveRef(elementTypeNameStr, source)
            ?: genError("Invalid type reference '$elementTypeNameStr'", source)
          element as? Shape ?: genError("Invalid type reference '$elementTypeNameStr'", source)
          val resContext = SwiftResolutionContext(unit, null)

          resolveReferencedTypeName(element, resContext)
        }
      }
    val typeName =
      if (typeNameStr.endsWith("[]")) {
        ARRAY.parameterizedBy(elementTypeName)
      } else {
        elementTypeName
      }
    return if (nameStr.endsWith("?"))
      typeName.makeOptional()
    else
      typeName
  }

  private fun resolveReferencedTypeName(shape: Shape, context: SwiftResolutionContext): TypeName =
    resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: DeclaredTypeName,
    context: SwiftResolutionContext
  ): TypeName {

    val propertyContext = context.copy(
      suggestedTypeName = className.nestedType(propertyShape.swiftTypeName),
    )

    val typeName = resolveTypeName(propertyShape.range, propertyContext)
    return if (propertyShape.minCount ?: 0 == 0) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun generateTypeName(shape: Shape, context: SwiftResolutionContext): TypeName {

    val swiftTypeAnn = shape.findStringAnnotation(SwiftType, null)
    if (swiftTypeAnn != null) {
      return DeclaredTypeName.typeName(swiftTypeAnn)
    }

    return processShape(shape, context)
  }

  private fun processShape(shape: Shape, context: SwiftResolutionContext): TypeName =
    when (shape) {
      is ScalarShape -> processScalarShape(shape, context)
      is ArrayShape -> processArrayShape(shape, context)
      is UnionShape -> processUnionShape(shape, context)
      is NodeShape -> processNodeShape(shape, context)
      is FileShape -> DATA
      is NilShape -> VOID
      is AnyShape -> processAnyShape(shape, context)
      else -> genError("Shape type '${shape::class.simpleName}' is unsupported", shape)
    }

  private fun processAnyShape(shape: AnyShape, context: SwiftResolutionContext): TypeName =
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

      else -> ANY_VALUE
    }

  private fun processScalarShape(type: ScalarShape, context: SwiftResolutionContext): TypeName =
    when (type.dataType) {
      DataType.String() ->

        if (type.values.isNotEmpty()) {
          defineEnum(type, context)
        } else {
          when (type.format) {
            "time", "datetime-only", "date-time-only" -> DATE
            else -> STRING
          }
        }

      DataType.Boolean() -> BOOL

      DataType.Integer() ->
        when (type.format) {
          "int8" -> INT8
          "int16" -> INT16
          "int32" -> INT32
          "int64" -> INT64
          "int", "", null -> INT
          else -> genError("Integer format '${type.format}' is unsupported", type)
        }

      DataType.Long() -> INT64

      DataType.Float() -> FLOAT
      DataType.Double() -> DOUBLE
      DataType.Number() -> DOUBLE

      DataType.Decimal() -> DECIMAL

      DataType.Duration() -> INT64
      DataType.Date() -> DATE
      DataType.Time() -> DATE
      DataType.DateTimeOnly() -> DATE
      DataType.DateTime() -> DATE

      DataType.Binary() -> DATE

      else -> genError("Scalar data type '${type.dataType}' is unsupported", type)
    }

  private fun processArrayShape(type: ArrayShape, context: SwiftResolutionContext): TypeName {

    val elementType = resolveReferencedTypeName(type.items!!, context)

    val collectionType =
      if (type.uniqueItems == true) {
        SET
      } else {
        ARRAY
      }

    return collectionType.parameterizedBy(elementType)
  }

  private fun processUnionShape(type: UnionShape, context: SwiftResolutionContext): TypeName =
    if (type.makesNullable) {
      resolveReferencedTypeName(type.nullableType, context).makeOptional()
    } else {
      nearestCommonAncestor(type.anyOf, context) ?: ANY
    }

  private fun processNodeShape(type: NodeShape, context: SwiftResolutionContext): TypeName {

    if (type.properties.isEmpty() && type.inherits.size == 1 && context.unit.findInheritingTypes(type).isEmpty()) {
      return resolveReferencedTypeName(type.inherits[0], context)
    }

    if (type.properties.isEmpty() && type.inherits.isEmpty() && type.closed != true) {

      val allTypes = collectTypes(type.properties().map { it.range })

      val commonType = nearestCommonAncestor(allTypes, context) ?: ANY

      return DICTIONARY.parameterizedBy(STRING, commonType)
    }

    return defineClass(type, type.inherits.firstOrNull()?.let { it.resolve as NodeShape }, type, context)
  }

  private fun defineClass(
    shape: Shape,
    superShape: Shape?,
    propertyContainerShape: NodeShape,
    context: SwiftResolutionContext
  ): DeclaredTypeName {

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
        TypeSpec.classBuilder(name)
      }

    typeBuilder.tag(DefinitionLocation(shape))

    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context)
      typeBuilder.addSuperType(superClassName)
    }

    val isRoot = superShape == null

    val codingKeysTypeName = className.nestedType(CODING_KEYS_NAME)
    var codingKeysType: TypeSpec? = null

    val originalInheritedProperties = collectProperties(superShape)
    val originalInheritedDeclaredProperties =
      originalInheritedProperties.filterNot { it.range.hasAnnotation(SwiftImpl, null) }
    var inheritedDeclaredProperties = originalInheritedDeclaredProperties

    val originalLocalProperties = propertyContainerShape.properties
    var localProperties = originalLocalProperties
    val originalLocalDeclaredProperties = localProperties.filterNot { it.range.hasAnnotation(SwiftImpl, null) }
    var localDeclaredProperties = originalLocalDeclaredProperties

    val inheritingTypes = context.unit.findInheritingTypes(shape)

    if (originalInheritedProperties.isNotEmpty() || originalLocalProperties.isNotEmpty()) {

      /*
        Computed discriminator values (for generating polymorphic Codable)
       */

      var discriminatorProperty: PropertyShape? = null
      val discriminatorPropertyName = findDiscriminatorPropertyName(shape)
      if (discriminatorPropertyName != null) {

        discriminatorProperty = (originalInheritedProperties + originalLocalProperties)
          .find { it.name == discriminatorPropertyName }
          ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

        val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)
        val discriminatorPropertyTypeEnumCases =
          typeBuilders[discriminatorPropertyTypeName]?.build()?.enumCases?.map { it.name }

        // Remove discriminator from property sets
        inheritedDeclaredProperties = inheritedDeclaredProperties.filter { it.name != discriminatorPropertyName }
        localProperties = localProperties.filter { it.name != discriminatorPropertyName }
        localDeclaredProperties = localDeclaredProperties.filter { it.name != discriminatorPropertyName }

        // Add abstract discriminator if this is the root of the discriminator tree

        if (propertyContainerShape.discriminator == discriminatorPropertyName) {

          typeBuilder.addProperty(
            PropertySpec.builder(discriminatorProperty.swiftIdentifierName, discriminatorPropertyTypeName, PUBLIC)
              .getter(
                FunctionSpec.getterBuilder()
                  .addStatement("fatalError(\"abstract type method\")")
                  .build()
              )
              .build()
          )

          if (shape.findBoolAnnotation(ExternallyDiscriminated, null) != true) {

            /*
            Generate polymorphic encoding/decoding type (replaces type name)
           */

            val refTypeName = className.nestedType(ANY_REF_NAME)
            referenceTypes[className] = refTypeName

            val refTypeBuilder =
              TypeSpec.enumBuilder(refTypeName)
                .addModifiers(PUBLIC)
                .addSuperTypes(listOf(CODABLE, CUSTOM_STRING_CONVERTIBLE))

            val refValueInitBuilder = FunctionSpec.constructorBuilder()
              .addModifiers(PUBLIC)
              .addParameter("value", className)
              .beginControlFlow("switch", "value")
            val refDecoderBuilder = FunctionSpec.constructorBuilder()
              .addModifiers(PUBLIC)
              .addParameter("from", "decoder", DECODER)
              .throws(true)
              .addCode("let container = try decoder.container(keyedBy: %T.self)\n", codingKeysTypeName)
              .addCode(
                "let type = try container.decode(%T.self, forKey: %T.%N)\n", discriminatorPropertyTypeName,
                codingKeysTypeName, discriminatorPropertyName
              )
              .beginControlFlow("switch", "type")
            val refEncoderBuilder = FunctionSpec.builder("encode")
              .addModifiers(*if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE))
              .addParameter("to", "encoder", ENCODER)
              .throws(true)
              .addCode("var container = encoder.container(keyedBy: %T.self)\n", codingKeysTypeName)
              .beginControlFlow("switch", "self")
            val refValueBuilder = FunctionSpec.getterBuilder()
              .beginControlFlow("switch", "self")
            val refDescriptionBuilder = FunctionSpec.getterBuilder()
              .beginControlFlow("switch", "self")

            val usedDiscriminators = mutableSetOf<String>()

            inheritingTypes.forEach {

              val inheritingTypeName = resolveReferencedTypeName(it, context)
              val discriminatorValue = findDiscriminatorPropertyValue(it, context) ?: it.name!!

              usedDiscriminators.add(discriminatorValue)

              val discriminatorCase = discriminatorValue.swiftIdentifierName

              refTypeBuilder.addEnumCase(discriminatorCase, inheritingTypeName)

              refValueInitBuilder.addStatement(
                "case let value as %T:%Wself = .%N(value)",
                inheritingTypeName, discriminatorCase
              )

              if (!discriminatorPropertyTypeEnumCases.isNullOrEmpty()) {
                refDecoderBuilder.addStatement(
                  "case .%N:%Wself = .%N(try %T(from: decoder))",
                  discriminatorCase, discriminatorCase, inheritingTypeName
                )
              } else {
                refDecoderBuilder.addStatement(
                  "case %S:%Wself = .%N(try %T(from: decoder))",
                  discriminatorValue, discriminatorCase, inheritingTypeName
                )
              }

              refEncoderBuilder.addStatement(
                "case .%N(let value):\ntry container.encode(%S, forKey: .%N)\ntry value.encode(to: encoder)",
                discriminatorCase, discriminatorValue, discriminatorPropertyName
              )

              refValueBuilder.addStatement("case .%N(let value):%Wreturn value", discriminatorCase)

              refDescriptionBuilder.addStatement(
                "case .%N(let value):%Wreturn value.%N",
                discriminatorCase, DESCRIPTION_PROP_NAME
              )
            }

            refValueInitBuilder.addStatement("default:%WfatalError(\"Invalid value type\")")
            refValueInitBuilder.endControlFlow("switch")
            refTypeBuilder.addFunction(refValueInitBuilder.build())

            if (
              discriminatorPropertyTypeEnumCases.isNullOrEmpty() ||
              !usedDiscriminators.containsAll(discriminatorPropertyTypeEnumCases)
            ) {
              refDecoderBuilder.addStatement(
                "default:\nthrow %T.dataCorruptedError(%>\nforKey: %T.%N,\nin: container,\ndebugDescription: %S%<\n)",
                DECODING_ERROR, codingKeysTypeName, discriminatorPropertyName,
                "unsupported value for \"$discriminatorPropertyName\""
              )
            }
            refDecoderBuilder.endControlFlow("switch")
            refTypeBuilder.addFunction(refDecoderBuilder.build())

            refEncoderBuilder.endControlFlow("switch")
            refTypeBuilder.addFunction(refEncoderBuilder.build())

            refValueBuilder.endControlFlow("switch")
            refTypeBuilder.addProperty(
              PropertySpec.builder("value", className, PUBLIC)
                .getter(refValueBuilder.build())
                .build()
            )

            refDescriptionBuilder.endControlFlow("switch")
            refTypeBuilder.addProperty(
              PropertySpec.builder(DESCRIPTION_PROP_NAME, STRING, PUBLIC)
                .getter(refDescriptionBuilder.build())
                .build()
            )

            val refType = refTypeBuilder.build()

            typeBuilder.addType(refType)
          } else {
            discriminatorProperty = null
          }
        } else {

          // Add concrete discriminator for leaf of the discriminated tree

          val discriminatorBuilder =
            PropertySpec.builder(discriminatorProperty.swiftIdentifierName, discriminatorPropertyTypeName, PUBLIC)
              .addModifiers(OVERRIDE)

          val discriminatorValue = findDiscriminatorPropertyValue(shape, context) ?: shape.name!!

          if (!discriminatorPropertyTypeEnumCases.isNullOrEmpty()) {
            discriminatorBuilder.getter(
              FunctionSpec.getterBuilder()
                .addStatement(
                  "return %T.%N",
                  discriminatorPropertyTypeName,
                  discriminatorValue.swiftIdentifierName
                )
                .build()
            )
          } else {
            discriminatorBuilder.getter(
              FunctionSpec.getterBuilder()
                .addStatement("return %S", discriminatorValue)
                .build()
            )
          }

          typeBuilder.addProperty(discriminatorBuilder.build())
        }
      }

      /*
        Generate Codable support
      */

      if (isRoot) {
        typeBuilder.addSuperType(CODABLE)
      }

      if (localDeclaredProperties.isNotEmpty() || discriminatorProperty != null) {
        val codingKeysBuilder =
          TypeSpec.enumBuilder(codingKeysTypeName)
            .addModifiers(FILEPRIVATE)
            .addSuperType(STRING)
            .addSuperType(CODING_KEY)

        if (isRoot && discriminatorProperty != null) {
          codingKeysBuilder.addEnumCase(discriminatorProperty.swiftIdentifierName, discriminatorProperty.name!!)
        }

        localDeclaredProperties
          .filter { it.name != discriminatorProperty?.name }
          .forEach {
            codingKeysBuilder.addEnumCase(it.swiftIdentifierName, it.name!!)
          }

        codingKeysType = codingKeysBuilder.build()
      }

      val decoderInitFunctionBuilder = FunctionSpec.constructorBuilder()
        .addModifiers(PUBLIC, REQUIRED)
        .addParameter("from", "decoder", DECODER)
        .throws(true)

      if (localDeclaredProperties.isNotEmpty()) {
        decoderInitFunctionBuilder.addStatement(
          "let container = try decoder.container(keyedBy: %T.self)",
          codingKeysTypeName
        )
      }

      val encoderFunctionBuilder = FunctionSpec.builder("encode")
        .addModifiers(*if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE))
        .addParameter("to", "encoder", ENCODER)
        .throws(true)
      if (!isRoot) {
        encoderFunctionBuilder.addStatement("try super.encode(to: encoder)")
      }
      if (localDeclaredProperties.isNotEmpty()) {
        encoderFunctionBuilder.addStatement("var container = encoder.container(keyedBy: %T.self)", codingKeysTypeName)
      }

      // Unpack all properties without (externalDiscriminator) annotation

      localDeclaredProperties.filterNot { it.range.hasAnnotation(ExternalDiscriminator, null) }.forEach { prop ->
        var propertyTypeName = resolvePropertyTypeName(prop, className, context)
        if (propertyTypeName == VOID) {
          return@forEach
        }

        val (coderSuffix, isOptional) =
          if (propertyTypeName.optional) {
            propertyTypeName = propertyTypeName.makeNonOptional()
            "IfPresent" to true
          } else {
            "" to false
          }

        val decoderPropertyRef = CodeBlock.of("self.%N", prop.swiftIdentifierName)
        var decoderPost = ""

        var encoderPropertyRef = CodeBlock.of("self.%N", prop.swiftIdentifierName)
        var encoderPre = ""

        val isLeaf = context.unit.findInheritingTypes(prop.range).isEmpty()
        val (refCollection, refElement) = replaceCollectionValueTypesWithReferenceTypes(propertyTypeName)

        if (!isLeaf) {
          propertyTypeName = if (isOptional) {
            (propertyTypeName.makeNonOptional() as DeclaredTypeName).nestedType(ANY_REF_NAME).makeOptional()
          } else {
            (propertyTypeName as DeclaredTypeName).nestedType(ANY_REF_NAME)
          }

          decoderPost = "${if (isOptional) "?" else ""}.value"
        } else if (refCollection != propertyTypeName) {

          propertyTypeName = refCollection
          decoderPost = "${if (isOptional) "?" else ""}.mapValues { $0.value }"
          encoderPre = "${if (isOptional) "?" else ""}.mapValues { ${refElement.name}(value: $0) }"
        } else if (propertyTypeName == DICTIONARY_STRING_ANY || propertyTypeName == DICTIONARY_STRING_ANY_OPTIONAL) {

          propertyTypeName = DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
          decoderPost = "${if (isOptional) "?" else ""}.mapValues { $0.unwrapped as Any }"
          encoderPre = "${if (isOptional) "?" else ""}.mapValues { try AnyValue.wrapped($0) }"
        } else if (propertyTypeName == ARRAY_ANY || propertyTypeName == ARRAY_ANY_OPTIONAL) {

          propertyTypeName = ARRAY.parameterizedBy(ANY_VALUE)
          decoderPost = "${if (isOptional) "?" else ""}.map { $0.unwrapped }"
          encoderPre = "${if (isOptional) "?" else ""}.map { try AnyValue.wrapped($0) }"
        } else if (propertyTypeName.unwrapOptional() == ANY) {

          propertyTypeName = ANY_VALUE
          decoderPost = "${if (isOptional) "?" else ""}.unwrapped"
          encoderPropertyRef = CodeBlock.of("%T.wrapped(%N)", ANY_VALUE, prop.swiftIdentifierName)
        }

        decoderInitFunctionBuilder
          .addCode("%[")
          .addCode(decoderPropertyRef)
          .addCode(
            " = try container.decode%L(%T.self, forKey: .%N)%L%]\n",
            coderSuffix, propertyTypeName, prop.swiftIdentifierName, decoderPost
          )

        encoderFunctionBuilder
          .addCode("%[try container.encode%L(", coderSuffix)
          .addCode(encoderPropertyRef)
          .addCode(
            "%L, forKey: .%N)%]\n",
            encoderPre, prop.swiftIdentifierName
          )
      }

      // Unpack all properties with (externalDiscriminator) annotation, because we know the discriminator is already unpacked!

      localDeclaredProperties.filter { it.range.hasAnnotation(ExternalDiscriminator, null) }
        .forEach { prop ->
          val propertyTypeName = resolvePropertyTypeName(prop, className, context)
          val coderSuffix = if (propertyTypeName.optional) "IfPresent" else ""

          val externalDiscriminator = prop.range.findStringAnnotation(ExternalDiscriminator, null)!!
          val externalDiscriminatorProperty =
            (originalInheritedProperties + originalLocalProperties).firstOrNull { it.name == externalDiscriminator }
              ?: genError("($ExternalDiscriminator) property '$externalDiscriminator' is not valid", shape)
          val externalDiscriminatorPropertyName = externalDiscriminatorProperty.swiftIdentifierName
          val externalDiscriminatorPropertyTypeName =
            resolvePropertyTypeName(externalDiscriminatorProperty, className, context)
          val externalDiscriminatorPropertyEnumCases =
            typeBuilders[externalDiscriminatorPropertyTypeName]?.build()?.enumCases?.map { it.name }
          val propertyTypeDerivedShapes = context.unit.findInheritingTypes(prop.range)

          if (externalDiscriminatorProperty.optional && prop.required) {
            genError("($ExternalDiscriminator) property is not required but the property it discriminates is", shape)
          }

          val switchOn =
            if (externalDiscriminatorProperty.optional) {
              decoderInitFunctionBuilder.beginControlFlow(
                "if", "let %N = self.%N",
                externalDiscriminatorPropertyName, externalDiscriminatorPropertyName
              )
              encoderFunctionBuilder.beginControlFlow(
                "if", "let %N = self.%N",
                externalDiscriminatorPropertyName, externalDiscriminatorPropertyName
              )
              CodeBlock.of("%N", externalDiscriminatorPropertyName)
            } else {
              CodeBlock.of("self.%N", externalDiscriminatorPropertyName)
            }

          decoderInitFunctionBuilder.beginControlFlow("switch", "%L", switchOn)
          encoderFunctionBuilder.beginControlFlow("switch", "%L", switchOn)

          val usedDiscriminators = mutableSetOf<String>()

          propertyTypeDerivedShapes.forEach { propertyTypeDerivedShape ->

            val discriminatorValue =
              findDiscriminatorPropertyValue(propertyTypeDerivedShape, context) ?: propertyTypeDerivedShape.name!!

            usedDiscriminators.add(discriminatorValue)

            val discriminatorCase = discriminatorValue.swiftIdentifierName
            val propDerivedTypeName = resolveReferencedTypeName(propertyTypeDerivedShape, context)
            val propDerivedTypeSuffix = if (propertyTypeName.optional) "?" else ""

            if (!externalDiscriminatorPropertyEnumCases.isNullOrEmpty()) {
              decoderInitFunctionBuilder.addStatement(
                "case .%N:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
                discriminatorCase,
                prop.swiftIdentifierName,
                coderSuffix,
                propDerivedTypeName,
                prop.swiftIdentifierName
              )
              encoderFunctionBuilder.addStatement(
                "case .%N:%Wtry container.encode%L(self.%N as! %T%L, forKey: .%N)",
                discriminatorCase, coderSuffix, prop.swiftIdentifierName,
                propDerivedTypeName, propDerivedTypeSuffix, prop.swiftIdentifierName
              )
            } else {
              decoderInitFunctionBuilder.addStatement(
                "case %S:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
                discriminatorValue, prop.swiftIdentifierName,
                coderSuffix, propDerivedTypeName, prop.swiftIdentifierName
              )
              encoderFunctionBuilder.addStatement(
                "case %S:%Wtry container.encode%L(self.%N as! %T%L, forKey: .%N)",
                discriminatorValue, coderSuffix, prop.swiftIdentifierName,
                propDerivedTypeName, propDerivedTypeSuffix, prop.swiftIdentifierName
              )
            }
          }

          if (
            externalDiscriminatorPropertyEnumCases.isNullOrEmpty() ||
            !usedDiscriminators.containsAll(externalDiscriminatorPropertyEnumCases)
          ) {
            decoderInitFunctionBuilder.addStatement(
              "default:\nthrow %T.%N(%>\nforKey: %T.%N,\nin: container,\ndebugDescription: %S%<\n)",
              DECODING_ERROR, "dataCorruptedError", codingKeysTypeName, externalDiscriminatorPropertyName,
              "unsupported value for \"$externalDiscriminatorPropertyName\""
            )
            encoderFunctionBuilder.addStatement(
              "default:\nthrow %T.%N(%>\n%L,\n%T(%>\ncodingPath: encoder.codingPath + [%T.%N],\ndebugDescription: %S%<\n)%<\n)",
              ENCODING_ERROR, "invalidValue", switchOn, ENCODING_ERROR.nestedType("Context"), codingKeysTypeName,
              externalDiscriminatorPropertyName, "unsupported value for \"$externalDiscriminatorPropertyName\""
            )
          }
          decoderInitFunctionBuilder.endControlFlow("switch")
          encoderFunctionBuilder.endControlFlow("switch")

          if (externalDiscriminatorProperty.optional) {
            decoderInitFunctionBuilder.nextControlFlow("else", "")
            decoderInitFunctionBuilder.addStatement("self.%N = nil", prop.swiftIdentifierName)
            decoderInitFunctionBuilder.endControlFlow("if")
            encoderFunctionBuilder.endControlFlow("if")
          }
        }

      if (!isRoot) {
        decoderInitFunctionBuilder.addStatement("try super.init(from: decoder)")
      }

      val decoderInitFunction = decoderInitFunctionBuilder.build()
      val encoderFunction = encoderFunctionBuilder.build()

      /*
        Generate class implementations
       */

      // Build parameter constructor
      //
      val paramConsBuilder = FunctionSpec.constructorBuilder()
        .addModifiers(*if (!isRoot && localDeclaredProperties.isEmpty()) arrayOf(PUBLIC, OVERRIDE) else arrayOf(PUBLIC))

      inheritedDeclaredProperties.forEach {
        var paramType = resolvePropertyTypeName(it, className, context)
        paramType = if (it.required) {
          paramType.makeNonOptional()
        } else {
          paramType.makeOptional()
        }
        paramConsBuilder.addParameter(it.swiftIdentifierName, paramType)
      }

      // Description builder
      //
      val descriptionCodeBuilder = CodeBlock.builder()
        .add("%[return %T(%T.self)\n", DESCRIPTION_BUILDER, className)
      originalInheritedDeclaredProperties.forEach {
        descriptionCodeBuilder.add(".add(%N, named: %S)\n", it.swiftIdentifierName, it.swiftIdentifierName)
      }
      if (isRoot) {
        typeBuilder.addSuperType(CUSTOM_STRING_CONVERTIBLE)
      }

      // Generate related code for each property
      //
      localProperties.forEach { propertyDeclaration ->

        val propertyTypeName = resolvePropertyTypeName(propertyDeclaration, className, context)

        val implAnn = propertyDeclaration.range.findAnnotation(SwiftImpl, null) as? ObjectNode
        if (implAnn != null) {

          val propertyBuilder = PropertySpec.builder(propertyDeclaration.swiftIdentifierName, propertyTypeName, PUBLIC)

          val code = implAnn.getValue("code") ?: ""
          val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
          val convertedCodeParams = codeParams.map { codeParam ->
            val atype = codeParam.getValue("type")
            val avalue = codeParam.getValue("value")
            if (atype != null && avalue != null) {
              when (atype) {
                "Type" -> DeclaredTypeName.typeName(avalue.toString())
                else -> avalue.toString()
              }
            } else
              ""
          }

          propertyBuilder.getter(
            FunctionSpec.getterBuilder()
              .addStatement(code, *convertedCodeParams.toTypedArray())
              .build()
          )

          typeBuilder.addProperty(propertyBuilder.build())
        } else {

          // Add public field
          //
          val fieldBuilder = PropertySpec.builder(propertyDeclaration.swiftIdentifierName, propertyTypeName, PUBLIC)
          typeBuilder.addProperty(fieldBuilder.build())

          // Add constructor parameter & initializer
          //
          paramConsBuilder.addParameter(propertyDeclaration.swiftIdentifierName, propertyTypeName)
          paramConsBuilder.addStatement(
            "self.%N = %N",
            propertyDeclaration.swiftIdentifierName, propertyDeclaration.swiftIdentifierName
          )

          // Add description value
          //
          descriptionCodeBuilder.add(
            ".add(%N, named: %S)\n",
            propertyDeclaration.swiftIdentifierName, propertyDeclaration.swiftIdentifierName
          )
        }
      }

      // Finish parameter constructor
      //

      if (!isRoot) {
        paramConsBuilder.addStatement(
          "super.init(%L)",
          inheritedDeclaredProperties.map { CodeBlock.of("%L: %N", it.swiftIdentifierName, it.swiftIdentifierName) }
            .joinToCode(",%W")
        )
      }
      typeBuilder.addFunction(paramConsBuilder.build())

      // Finish description property
      //

      typeBuilder.addProperty(
        PropertySpec.builder(
          DESCRIPTION_PROP_NAME, STRING,
          *if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE)
        )
          .getter(
            FunctionSpec.getterBuilder()
              .addCode(descriptionCodeBuilder.add(".build()%]\n").build())
              .build()
          )
          .build()
      )

      // Add codable methods
      //

      typeBuilder.addFunction(decoderInitFunction)
      typeBuilder.addFunction(encoderFunction)

      // Add fluent builders
      //

      (inheritedDeclaredProperties + localDeclaredProperties).forEach { propertyDeclaration ->

        val isInherited = !isRoot && inheritedDeclaredProperties.contains(propertyDeclaration)
        val propertyTypeName = resolvePropertyTypeName(propertyDeclaration, className, context)

        val fluentBuilder =
          FunctionSpec.builder("with" + propertyDeclaration.swiftIdentifierName.capitalize())
            .addModifiers(*if (!isInherited) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE))
            .returns(className)
            .addParameter(propertyDeclaration.swiftIdentifierName, propertyTypeName)
            .addStatement(
              "return %T(%L)",
              className,
              (inheritedDeclaredProperties + localDeclaredProperties).map {
                CodeBlock.of(
                  "%L: %N",
                  it.swiftIdentifierName,
                  it.swiftIdentifierName
                )
              }.joinToCode(",%W")
            )

        typeBuilder.addFunction(fluentBuilder.build())
      }
    }

    codingKeysType?.let { typeBuilder.addType(it) }

    if (shape.findBoolAnnotation(Patchable, null) == true) {

      val patchClassName = className.nestedType("Patch")

      val patchClassBuilder =
        TypeSpec.structBuilder(patchClassName)
          .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
          .addSuperType(CODABLE)
          .addModifiers(PUBLIC)

      val patchClassConsBuilder = FunctionSpec.constructorBuilder()

      val patchFields = mutableListOf<CodeBlock>()

      for (propertyDecl in propertyContainerShape.properties) {
        val propertyTypeName = resolveReferencedTypeName(propertyDecl.range, context)
        val optionalPropertyTypeName = propertyTypeName.makeOptional()

        patchClassBuilder.addProperty(propertyDecl.swiftIdentifierName, optionalPropertyTypeName)
        patchClassConsBuilder.addParameter(propertyDecl.swiftIdentifierName, optionalPropertyTypeName)

        patchClassConsBuilder.addStatement(
          "self.%N = %N",
          propertyDecl.swiftIdentifierName, propertyDecl.swiftIdentifierName
        )

        patchFields.add(
          CodeBlock.of(
            "%N: source.keys.contains(%S) ? %T.some(%N) : nil",
            propertyDecl.swiftIdentifierName, propertyDecl.swiftIdentifierName,
            OPTIONAL, propertyDecl.swiftIdentifierName
          )
        )
      }

      patchClassBuilder.addFunction(patchClassConsBuilder.build())

      typeBuilder.addFunction(
        FunctionSpec.builder("patch")
          .addParameter(
            ParameterSpec.builder("source", DICTIONARY_STRING_ANY)
              .build()
          )
          .returns(patchClassName)
          .addCode(
            CodeBlock.builder()
              .add("return %T(", patchClassName)
              .add(patchFields.joinToCode(",%W"))
              .add(")\n")
              .build()
          )
          .build()
      )

      typeBuilder.addType(patchClassBuilder.build())
    }

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    return className
  }

  private fun defineEnum(shape: Shape, context: SwiftResolutionContext): DeclaredTypeName {

    val className = typeNameOf(shape, context)

    val enumBuilder = defineType(className) {
      TypeSpec.enumBuilder(it)
        .addModifiers(PUBLIC)
        .addSuperTypes(listOf(STRING, CASE_ITERABLE, CODABLE))
    }

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->
      enumBuilder.addEnumCase(enum.swiftIdentifierName, enum.stringValue!!)
    }

    return className
  }

  private fun defineType(
    className: DeclaredTypeName,
    builderBlock: (DeclaredTypeName) -> TypeSpec.Builder
  ): TypeSpec.Builder {

    val builder = builderBlock(className)
    builder.addModifiers(PUBLIC)

    builder.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, builder) != null) {
      genError("Multiple types declared with name '${className.simpleName}'")
    }

    if (className.enclosingTypeName() == null) {

      if (options.contains(AddGeneratedHeader)) {
        builder.addDoc(GenerationHeaders.create("${className.simpleName}.swift"))
      }
    }

    return builder
  }

  private fun typeNameOf(shape: Shape, context: SwiftResolutionContext): DeclaredTypeName {

    if (context.suggestedTypeName != null) {
      val typeIdFrag = URI(shape.id).fragment
      val typeName = shape.name
      if (typeIdFrag.endsWith("/property/$typeName/$typeName") || !typeIdFrag.startsWith("/declarations")) {
        return context.suggestedTypeName
      }
    }

    val nestedAnn = shape.findAnnotation(Nested, null)
      ?: return DeclaredTypeName.typeName(".${shape.swiftTypeName}")

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

          val enclosedIn = nestedAnn.getValue("enclosedIn")
            ?: genError("Nested annotation is missing 'enclosedIn'", nestedAnn)

          val name = nestedAnn.getValue("name")
            ?: genError("Nested annotation is missing name", nestedAnn)

          enclosedIn to name
        }

        else ->
          genError("Nested annotation must be the value 'dashed' or an object containing 'enclosedIn' & 'name' keys")
      }

    val (nestedEnclosingType, nestedEnclosingTypeUnit) = context.resolveRef(nestedEnclosedIn, shape)
      ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

    nestedEnclosingType as? Shape
      ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

    val nestedEnclosingTypeContext = SwiftResolutionContext(nestedEnclosingTypeUnit, null)

    val nestedEnclosingTypeName =
      resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as? DeclaredTypeName
        ?: genError("Nested annotation references non-defining enclosing type", nestedAnn)

    return nestedEnclosingTypeName.nestedType(nestedName)
  }

  private fun replaceCollectionValueTypesWithReferenceTypes(typeName: TypeName): Pair<TypeName, TypeName> {
    val baseTypeName =
      typeName.makeNonOptional() as? ParameterizedTypeName ?: return typeName to typeName
    val (mappedTypeName, elementRefTypeName) = when (baseTypeName.rawType) {
      DICTIONARY -> {
        val valueType = baseTypeName.typeArguments[1]
        val refValueType = referenceTypes[valueType] ?: valueType
        DICTIONARY.parameterizedBy(baseTypeName.typeArguments[0], refValueType) to refValueType
      }
      ARRAY -> {
        val valueType = baseTypeName.typeArguments[0]
        val refValueType = referenceTypes[valueType] ?: valueType
        ARRAY.parameterizedBy(referenceTypes[valueType] ?: valueType) to refValueType
      }
      SET -> {
        val valueType = baseTypeName.typeArguments[0]
        val refValueType = referenceTypes[valueType] ?: valueType
        SET.parameterizedBy(referenceTypes[valueType] ?: valueType) to refValueType
      }
      else -> typeName to typeName
    }
    return (if (typeName.optional) mappedTypeName.makeOptional() else mappedTypeName) to elementRefTypeName
  }

  private fun collectTypes(types: List<Shape>) = types.flatMap { if (it is UnionShape) it.flattened else listOf(it) }

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

  private fun nearestCommonAncestor(types: List<Shape>, context: SwiftResolutionContext): DeclaredTypeName? {

    var currentClassNameHierarchy: List<DeclaredTypeName>? = null
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

  private fun classNameHierarchy(shape: Shape, context: SwiftResolutionContext): List<DeclaredTypeName>? {

    val names = mutableListOf<DeclaredTypeName>()

    var current: Shape? = shape
    while (current != null) {
      val currentClass = resolveReferencedTypeName(current, context) as? DeclaredTypeName ?: return null
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

  private fun findDiscriminatorPropertyValue(shape: Shape, context: SwiftResolutionContext): String? =
    if (shape is NodeShape && !shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = shape.inheritanceRoot as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(shape: NodeShape, context: SwiftResolutionContext): Map<String, String> =
    shape.discriminatorMapping.mapNotNull { mapping ->
      val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
      mapping.templateVariable().value()!! to refElement.id
    }.toMap()

  companion object {

    private const val DESCRIPTION_PROP_NAME = "debugDescription"
    private const val ANY_REF_NAME = "AnyRef"
    private const val CODING_KEYS_NAME = "CodingKeys"
  }
}
