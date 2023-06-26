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
import io.outfoxx.sunday.generator.APIAnnotationName
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
import io.outfoxx.sunday.generator.common.ShapeIndex
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
import io.outfoxx.sunday.generator.swift.utils.swiftEnumName
import io.outfoxx.sunday.generator.swift.utils.swiftIdentifierName
import io.outfoxx.sunday.generator.swift.utils.swiftTypeName
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
import io.outfoxx.sunday.generator.utils.items
import io.outfoxx.sunday.generator.utils.makesNullable
import io.outfoxx.sunday.generator.utils.minCount
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.nullable
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.optional
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.patternProperties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.required
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
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.ExtensionSpec
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
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.ParameterizedTypeName
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.SET
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.SelfTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.TypeVariableName.Bound.Constraint.SAME_TYPE
import io.outfoxx.swiftpoet.TypeVariableName.Companion.bound
import io.outfoxx.swiftpoet.TypeVariableName.Companion.typeVariable
import io.outfoxx.swiftpoet.VOID
import io.outfoxx.swiftpoet.joinToCode
import io.outfoxx.swiftpoet.parameterizedBy
import io.outfoxx.swiftpoet.tag
import java.nio.file.Path
import kotlin.math.min

class SwiftTypeRegistry(
  val options: Set<Option>,
) : TypeRegistry {

  enum class Option {
    AddGeneratedHeader,
  }

  private val typeBuilders = mutableMapOf<DeclaredTypeName, TypeSpec.Builder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()
  private var referenceTypes = mutableMapOf<TypeName, TypeName>()

  override fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path) {

    fun addExtensions(builder: FileSpec.Builder, typeSpec: TypeSpec) {
      typeSpec.tag<AssociatedExtensions>()?.forEach { builder.addExtension(it) }
      typeSpec.typeSpecs.forEach {
        if (it is TypeSpec) {
          addExtensions(builder, it)
        }
      }
    }

    val builtTypes = buildTypes()

    builtTypes.entries
      .filter { it.key.topLevelTypeName() == it.key }
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { (typeName, typeSpec) ->
        FileSpec.builder(typeName.moduleName, typeSpec.name)
          .addType(typeSpec)
          .apply { addExtensions(this, typeSpec) }
          .build()
      }
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

    context.getReferenceTarget(shape)?.let { return resolveTypeName(it, context) }

    var typeName = typeNameMappings[shape.id]
    if (typeName == null) {

      typeName = generateTypeName(shape, context)

      typeNameMappings[shape.id] = typeName
    }

    return typeName
  }

  fun getReferenceType(className: TypeName): TypeName? = referenceTypes[className]

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

  override fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition,
    shapeIndex: ShapeIndex,
  ): DeclaredTypeName {

    val moduleName = moduleNameOf(problemTypeDefinition.definedIn)

    val problemTypeName =
      typeName("$moduleName.${problemCode.toUpperCamelCase()}Problem")

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
            .build(),
        )
        .addFunction(
          FunctionSpec.constructorBuilder()
            .addModifiers(PUBLIC)
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
                        SwiftResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
                      ),
                    )
                    .build(),
                )
              }
            }
            .addParameter(
              ParameterSpec.builder("instance", URL.makeOptional())
                .defaultValue("nil")
                .build(),
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
            .build(),
        )
        .apply {
          problemTypeDefinition.custom.map { (customPropertyName, customPropertyTypeNameStr) ->
            addProperty(
              PropertySpec.builder(
                customPropertyName.swiftIdentifierName,
                resolveTypeReference(
                  customPropertyTypeNameStr,
                  problemTypeDefinition.source,
                  SwiftResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
                ),
                PUBLIC,
              )
                .build(),
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
                    .toTypedArray(),
                )
                .build(),
            )
            .build(),
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
                    SwiftResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
                  )
                addStatement(
                  "self.%N = try container.decode%L(%T.self, forKey: %T.%N)",
                  customPropertyName.swiftIdentifierName,
                  if (customPropertyTypeName.optional) "IfPresent" else "",
                  customPropertyTypeName.makeNonOptional(),
                  problemCodingKeysTypeName,
                  customPropertyName.swiftIdentifierName,
                )
              }
            }
            .addStatement("try super.init(from: decoder)")
            .build(),
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
                  it.key.swiftIdentifierName,
                  problemCodingKeysTypeName,
                  it.key.swiftIdentifierName,
                )
              }
            }
            .build(),
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

    if (options.contains(AddGeneratedHeader)) {
      problemTypeBuilder.addDoc(GenerationHeaders.create("${problemTypeName.simpleName}.swift"))
    }

    typeBuilders[problemTypeName] = problemTypeBuilder

    return problemTypeName
  }

  private fun resolveTypeReference(nameStr: String, source: DomainElement, context: SwiftResolutionContext): TypeName {
    val typeNameStr = nameStr.removeSuffix("?")
    val elementTypeNameStr = typeNameStr.removeSuffix("[]")
    val elementTypeName =
      when (elementTypeNameStr.lowercase()) {
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

          resolveReferencedTypeName(element, context.copy(unit = unit, suggestedTypeName = null))
        }
      }
    val typeName =
      if (typeNameStr.endsWith("[]")) {
        ARRAY.parameterizedBy(elementTypeName)
      } else {
        elementTypeName
      }
    return if (nameStr.endsWith("?")) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun resolveReferencedTypeName(shape: Shape, context: SwiftResolutionContext): TypeName =
    resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: DeclaredTypeName,
    context: SwiftResolutionContext,
  ): TypeName {

    val propertyContext = context.copy(
      suggestedTypeName = className.nestedType(propertyShape.swiftTypeName),
    )

    val typeName = resolveTypeName(propertyShape.range, propertyContext)
    return if ((propertyShape.minCount ?: 0) == 0) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun generateTypeName(shape: Shape, context: SwiftResolutionContext): TypeName {

    val swiftTypeAnn = shape.findStringAnnotation(SwiftType, null)
    if (swiftTypeAnn != null) {
      return typeName(swiftTypeAnn)
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
      context.hasInherited(shape) && shape is NodeShape ->
        defineClass(
          shape,
          context,
        )

      shape.or.isNotEmpty() ->
        nearestCommonAncestor(shape.or, context) ?: ANY

      shape.xone.isNotEmpty() ->
        nearestCommonAncestor(shape.xone, context) ?: ANY

      else -> ANY_VALUE
    }

  private fun processScalarShape(shape: ScalarShape, context: SwiftResolutionContext): TypeName =
    when (shape.dataType) {
      DataTypes.String() ->

        if (shape.values.isNotEmpty()) {
          defineEnum(shape, context)
        } else {
          when (shape.format) {
            "time", "datetime-only", "date-time-only" -> DATE
            else -> STRING
          }
        }

      DataTypes.Boolean() -> BOOL

      DataTypes.Integer() ->
        when (shape.format) {
          "int8" -> INT8
          "int16" -> INT16
          "int32" -> INT32
          "int64" -> INT64
          "int", "", null -> INT
          else -> genError("Integer format '${shape.format}' is unsupported", shape)
        }

      DataTypes.Long() -> INT64

      DataTypes.Float() -> FLOAT
      DataTypes.Double() -> DOUBLE
      DataTypes.Number() -> DOUBLE

      DataTypes.Decimal() -> DECIMAL

//      DataTypes.Duration() -> INT64
      DataTypes.Date() -> DATE
      DataTypes.Time() -> DATE
      DataTypes.DateTimeOnly() -> DATE
      DataTypes.DateTime() -> DATE

      DataTypes.Binary() -> DATE

      else -> genError("Scalar data type '${shape.dataType}' is unsupported", shape)
    }

  private fun processArrayShape(shape: ArrayShape, context: SwiftResolutionContext): TypeName {

    val elementType = resolveReferencedTypeName(shape.items!!, context)

    val collectionType =
      if (shape.uniqueItems == true) {
        SET
      } else {
        ARRAY
      }

    return collectionType.parameterizedBy(elementType)
  }

  private fun processUnionShape(shape: UnionShape, context: SwiftResolutionContext): TypeName =
    if (shape.makesNullable) {
      resolveReferencedTypeName(shape.nullableType, context).makeOptional()
    } else {
      nearestCommonAncestor(shape.anyOf, context) ?: ANY
    }

  private fun processNodeShape(shape: NodeShape, context: SwiftResolutionContext): TypeName {

    if (
      shape.nonPatternProperties.isEmpty() &&
      context.hasNoInherited(shape) &&
      context.hasNoInheriting(shape)
    ) {

      val patternProperties = shape.patternProperties
      return if (patternProperties.isNotEmpty()) {

        val patternPropertyShapes = collectTypes(patternProperties.map { it.range })

        val valueTypeName = nearestCommonAncestor(patternPropertyShapes, context) ?: ANY

        DICTIONARY.parameterizedBy(STRING, valueTypeName)
      } else {

        DICTIONARY.parameterizedBy(STRING, ANY)
      }
    }

    return defineClass(shape, context)
  }

  private fun defineClass(
    shape: NodeShape,
    context: SwiftResolutionContext,
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

    val superShape = context.findSuperShapeOrNull(shape) as NodeShape?
    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context)
      typeBuilder.addSuperType(superClassName)
    }

    val isRoot = superShape == null
    val isPatchable = shape.findBoolAnnotation(Patchable, null) == true

    val codingKeysTypeName = className.nestedType(CODING_KEYS_NAME)
    var codingKeysType: TypeSpec? = null

    val originalInheritedProperties = superShape?.let(context::findAllProperties) ?: emptyList()
    val originalInheritedDeclaredProperties =
      originalInheritedProperties.filterNot { it.range.hasAnnotation(SwiftImpl, null) }
    var inheritedDeclaredProperties = originalInheritedDeclaredProperties

    val originalLocalProperties = context.findProperties(shape)
    var localProperties = originalLocalProperties
    val originalLocalDeclaredProperties = localProperties.filterNot { it.range.hasAnnotation(SwiftImpl, null) }
    var localDeclaredProperties = originalLocalDeclaredProperties

    val inheritingTypes = context.findInheritingShapes(shape).map { it as NodeShape }

    /*
      Computed discriminator values (for generating polymorphic Codable)
     */

    var discriminatorProperty: PropertyShape? = null
    val discriminatorPropertyName = findDiscriminatorPropertyName(shape, context)
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

      if (context.hasNoInherited(shape)) {

        typeBuilder.addProperty(
          PropertySpec.builder(discriminatorProperty.swiftIdentifierName, discriminatorPropertyTypeName, PUBLIC)
            .getter(
              FunctionSpec.getterBuilder()
                .addStatement("fatalError(\"abstract type method\")")
                .build(),
            )
            .build(),
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
              "let type = try container.decode(%T.self, forKey: %T.%N)\n",
              discriminatorPropertyTypeName,
              codingKeysTypeName,
              discriminatorPropertyName,
            )
            .beginControlFlow("switch", "type")
          val refEncoderBuilder = FunctionSpec.builder("encode")
            .addModifiers(*if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE))
            .addParameter("to", "encoder", ENCODER)
            .throws(true)
            .addStatement("var container = encoder.singleValueContainer()")
            .beginControlFlow("switch", "self")
          val refValueBuilder = FunctionSpec.getterBuilder()
            .beginControlFlow("switch", "self")
          val refDescriptionBuilder = FunctionSpec.getterBuilder()
            .beginControlFlow("switch", "self")

          val usedDiscriminators = mutableSetOf<String>()

          inheritingTypes.forEach {

            val inheritingTypeName = resolveReferencedTypeName(it, context)
            val discriminatorValue = findDiscriminatorPropertyValue(it, context) ?: it.name!!

            val discriminatorCase = discriminatorValue.swiftIdentifierName

            refTypeBuilder.addEnumCase(discriminatorCase, inheritingTypeName)

            refValueInitBuilder.addStatement(
              "case let value as %T:%Wself = .%N(value)",
              inheritingTypeName,
              discriminatorCase,
            )

            if (!discriminatorPropertyTypeEnumCases.isNullOrEmpty()) {
              refDecoderBuilder.addStatement(
                "case .%N:%Wself = .%N(try %T(from: decoder))",
                discriminatorCase,
                discriminatorCase,
                inheritingTypeName,
              )
              usedDiscriminators.add(discriminatorCase)
            } else {
              refDecoderBuilder.addStatement(
                "case %S:%Wself = .%N(try %T(from: decoder))",
                discriminatorValue,
                discriminatorCase,
                inheritingTypeName,
              )
              usedDiscriminators.add(discriminatorValue)
            }

            refEncoderBuilder.addStatement(
              "case .%N(let value):%Wtry container.encode(value)",
              discriminatorCase,
            )

            refValueBuilder.addStatement("case .%N(let value):%Wreturn value", discriminatorCase)

            refDescriptionBuilder.addStatement(
              "case .%N(let value):%Wreturn value.%N",
              discriminatorCase,
              DESCRIPTION_PROP_NAME,
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
              DECODING_ERROR,
              codingKeysTypeName,
              discriminatorPropertyName,
              "unsupported value for \"$discriminatorPropertyName\"",
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
              .build(),
          )

          refDescriptionBuilder.endControlFlow("switch")
          refTypeBuilder.addProperty(
            PropertySpec.builder(DESCRIPTION_PROP_NAME, STRING, PUBLIC)
              .getter(refDescriptionBuilder.build())
              .build(),
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
                discriminatorValue.swiftIdentifierName,
              )
              .build(),
          )
        } else {
          discriminatorBuilder.getter(
            FunctionSpec.getterBuilder()
              .addStatement("return %S", discriminatorValue)
              .build(),
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

    if (isRoot || localDeclaredProperties.isNotEmpty() || discriminatorProperty != null) {

      val codingKeysBuilder =
        TypeSpec.enumBuilder(codingKeysTypeName)
          .addModifiers(FILEPRIVATE)

      if (isRoot && discriminatorProperty != null) {
        codingKeysBuilder.addSuperType(STRING)
        codingKeysBuilder.addEnumCase(discriminatorProperty.swiftIdentifierName, discriminatorProperty.name!!)
      }

      if (localDeclaredProperties.any { it.name != discriminatorPropertyName }) {
        codingKeysBuilder.addSuperType(STRING)
      }

      codingKeysBuilder.addSuperType(CODING_KEY)

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

    if (localDeclaredProperties.isNotEmpty() || isRoot) {
      decoderInitFunctionBuilder.addStatement(
        "let %L = try decoder.container(keyedBy: %T.self)",
        if (localDeclaredProperties.isNotEmpty()) "container" else "_",
        codingKeysTypeName,
      )
    }

    val encoderFunctionBuilder = FunctionSpec.builder("encode")
      .addModifiers(*if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE))
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
    if (!isRoot) {
      encoderFunctionBuilder.addStatement("try super.encode(to: encoder)")
    }
    if (localDeclaredProperties.isNotEmpty() || (isRoot && discriminatorProperty != null)) {
      encoderFunctionBuilder.addStatement("var container = encoder.container(keyedBy: %T.self)", codingKeysTypeName)
    } else if (isRoot) {
      encoderFunctionBuilder.addStatement("let _ = encoder.container(keyedBy: %T.self)", codingKeysTypeName)
    }

    if (isRoot && discriminatorProperty != null) {
      encoderFunctionBuilder
        .addStatement(
          "try container.encode(self.%N, forKey: .%N)",
          discriminatorProperty.swiftIdentifierName,
          discriminatorProperty.swiftIdentifierName,
        )
    }

    // Unpack all properties without (externalDiscriminator) annotation
    localDeclaredProperties.filterNot { it.range.hasAnnotation(ExternalDiscriminator, null) }.forEach { prop ->
      var propertyTypeName = resolvePropertyTypeName(prop, className, context)
      if (propertyTypeName == VOID) {
        return@forEach
      }

      val (coderSuffix, isOptional) =
        if (isPatchable) {
          propertyTypeName = propertyTypeName.makeNonOptional()
          "IfExists" to true
        } else if (propertyTypeName.optional) {
          propertyTypeName = propertyTypeName.makeNonOptional()
          "IfPresent" to true
        } else {
          "" to false
        }

      val decoderPropertyRef = CodeBlock.of("self.%N", prop.swiftIdentifierName)
      var decoderPost = ""

      var encoderPropertyRef = CodeBlock.of("self.%N", prop.swiftIdentifierName)
      var encoderPre = ""

      val isLeaf = context.hasNoInheriting(prop.range)
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
          coderSuffix,
          propertyTypeName,
          prop.swiftIdentifierName,
          decoderPost,
        )

      encoderFunctionBuilder
        .addCode("%[try container.encode%L(", coderSuffix)
        .addCode(encoderPropertyRef)
        .addCode(
          "%L, forKey: .%N)%]\n",
          encoderPre,
          prop.swiftIdentifierName,
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
        val propertyTypeDerivedShapes = context.findInheritingShapes(prop.range).map { it as NodeShape }

        if (externalDiscriminatorProperty.optional && prop.required) {
          genError("($ExternalDiscriminator) property is not required but the property it discriminates is", shape)
        }

        val switchOn =
          if (externalDiscriminatorProperty.optional) {
            decoderInitFunctionBuilder.beginControlFlow(
              "if",
              "let %N = self.%N",
              externalDiscriminatorPropertyName,
              externalDiscriminatorPropertyName,
            )
            encoderFunctionBuilder.beginControlFlow(
              "if",
              "let %N = self.%N",
              externalDiscriminatorPropertyName,
              externalDiscriminatorPropertyName,
            )
            CodeBlock.of("%N", externalDiscriminatorPropertyName)
          } else {
            CodeBlock.of("self.%N", externalDiscriminatorPropertyName)
          }

        decoderInitFunctionBuilder.beginControlFlow("switch", "%L", switchOn)
        encoderFunctionBuilder.beginControlFlow("switch", "%L", switchOn)

        val usedCases = mutableSetOf<String>()

        propertyTypeDerivedShapes.forEach { propertyTypeDerivedShape ->

          val discriminatorValue =
            findDiscriminatorPropertyValue(propertyTypeDerivedShape, context) ?: propertyTypeDerivedShape.name!!

          val discriminatorCase = discriminatorValue.swiftIdentifierName
          val propDerivedTypeName = resolveReferencedTypeName(propertyTypeDerivedShape, context)
          val propDerivedTypeSuffix = if (propertyTypeName.optional) "?" else ""

          usedCases.add(discriminatorCase)

          if (!externalDiscriminatorPropertyEnumCases.isNullOrEmpty()) {
            decoderInitFunctionBuilder.addStatement(
              "case .%N:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
              discriminatorCase,
              prop.swiftIdentifierName,
              coderSuffix,
              propDerivedTypeName,
              prop.swiftIdentifierName,
            )
            encoderFunctionBuilder.addStatement(
              "case .%N:%Wtry container.encode%L(self.%N as! %T%L, forKey: .%N)",
              discriminatorCase,
              coderSuffix,
              prop.swiftIdentifierName,
              propDerivedTypeName,
              propDerivedTypeSuffix,
              prop.swiftIdentifierName,
            )
          } else {
            decoderInitFunctionBuilder.addStatement(
              "case %S:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
              discriminatorValue,
              prop.swiftIdentifierName,
              coderSuffix,
              propDerivedTypeName,
              prop.swiftIdentifierName,
            )
            encoderFunctionBuilder.addStatement(
              "case %S:%Wtry container.encode%L(self.%N as! %T%L, forKey: .%N)",
              discriminatorValue,
              coderSuffix,
              prop.swiftIdentifierName,
              propDerivedTypeName,
              propDerivedTypeSuffix,
              prop.swiftIdentifierName,
            )
          }
        }

        if (
          externalDiscriminatorPropertyEnumCases.isNullOrEmpty() ||
          !usedCases.containsAll(externalDiscriminatorPropertyEnumCases)
        ) {
          decoderInitFunctionBuilder.addStatement(
            "default:\nthrow %T.%N(%>\nforKey: %T.%N,\nin: container,\ndebugDescription: %S%<\n)",
            DECODING_ERROR,
            "dataCorruptedError",
            codingKeysTypeName,
            externalDiscriminatorPropertyName,
            "unsupported value for \"$externalDiscriminatorPropertyName\"",
          )
          encoderFunctionBuilder.addStatement(
            "default:\n" +
              "throw %T.%N(%>\n%L,\n%T(%>\ncodingPath: encoder.codingPath + [%T.%N],\ndebugDescription: %S%<\n)%<\n)",
            ENCODING_ERROR,
            "invalidValue",
            switchOn,
            ENCODING_ERROR.nestedType("Context"),
            codingKeysTypeName,
            externalDiscriminatorPropertyName,
            "unsupported value for \"$externalDiscriminatorPropertyName\"",
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

      val propertyTypeName =
        resolvePropertyTypeName(propertyDeclaration, className, context)
          .run {
            if (isPatchable) {
              val base = if (propertyDeclaration.nullable) PATCH_OP else UPDATE_OP
              base.parameterizedBy(makeNonOptional()).makeOptional()
            } else {
              this
            }
          }

      val implAnn = propertyDeclaration.range.findAnnotation(SwiftImpl, null) as? ObjectNode
      if (implAnn != null) {

        val propertyBuilder = PropertySpec.varBuilder(propertyDeclaration.swiftIdentifierName, propertyTypeName, PUBLIC)

        val code = implAnn.getValue("code") ?: ""
        val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
        val convertedCodeParams = codeParams.map { codeParam ->
          val atype = codeParam.getValue("type")
          val avalue = codeParam.getValue("value")
          if (atype != null && avalue != null) {
            when (atype) {
              "Type" -> typeName(avalue.toString())
              else -> avalue.toString()
            }
          } else {
            ""
          }
        }

        propertyBuilder.getter(
          FunctionSpec.getterBuilder()
            .addStatement(code, *convertedCodeParams.toTypedArray())
            .build(),
        )

        typeBuilder.addProperty(propertyBuilder.build())
      } else {

        // Add public field
        //
        val fieldBuilder = PropertySpec.varBuilder(propertyDeclaration.swiftIdentifierName, propertyTypeName, PUBLIC)
        typeBuilder.addProperty(fieldBuilder.build())

        // Add constructor parameter & initializer
        //
        paramConsBuilder
          .addParameter(
            ParameterSpec
              .builder(
                propertyDeclaration.swiftIdentifierName,
                propertyTypeName,
              )
              .apply {
                if (isPatchable) {
                  defaultValue(".none")
                }
              }
              .build(),
          )

        paramConsBuilder.addStatement(
          "self.%N = %N",
          propertyDeclaration.swiftIdentifierName,
          propertyDeclaration.swiftIdentifierName,
        )

        // Add description value
        //
        descriptionCodeBuilder.add(
          ".add(%N, named: %S)\n",
          propertyDeclaration.swiftIdentifierName,
          propertyDeclaration.swiftIdentifierName,
        )
      }
    }

    // Finish parameter constructor
    //

    if (!isRoot) {
      paramConsBuilder.addStatement(
        "super.init(%L)",
        inheritedDeclaredProperties.map { CodeBlock.of("%L: %N", it.swiftIdentifierName, it.swiftIdentifierName) }
          .joinToCode(",%W"),
      )
    }
    typeBuilder.addFunction(paramConsBuilder.build())

    // Finish description property
    //

    typeBuilder.addProperty(
      PropertySpec.builder(
        DESCRIPTION_PROP_NAME,
        STRING,
        *if (isRoot) arrayOf(PUBLIC) else arrayOf(PUBLIC, OVERRIDE),
      )
        .getter(
          FunctionSpec.getterBuilder()
            .addCode(descriptionCodeBuilder.add(".build()%]\n").build())
            .build(),
        )
        .build(),
    )

    // Add codable methods
    //

    typeBuilder.addFunction(decoderInitFunction)
    typeBuilder.addFunction(encoderFunction)

    // Add fluent builders
    //

    (inheritedDeclaredProperties + localDeclaredProperties).forEach { propertyDeclaration ->

      val isInherited = !isRoot && inheritedDeclaredProperties.contains(propertyDeclaration)
      val propertyTypeName =
        resolvePropertyTypeName(propertyDeclaration, className, context)
          .run {
            if (isPatchable) {
              val base = if (propertyDeclaration.nullable) PATCH_OP else UPDATE_OP
              base.parameterizedBy(makeNonOptional()).makeOptional()
            } else {
              this
            }
          }

      val fluentBuilder =
        FunctionSpec.builder("with" + propertyDeclaration.swiftIdentifierName.replaceFirstChar { it.titlecase() })
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
                it.swiftIdentifierName,
              )
            }.joinToCode(",%W"),
          )

      typeBuilder.addFunction(fluentBuilder.build())
    }

    // Add PatchOp extension

    if (isPatchable) {

      val typeSpec = typeBuilder.build()
      val propertySpecs = typeSpec.propertySpecs.filter { it.name != DESCRIPTION_PROP_NAME }

      val patchOpExt =
        ExtensionSpec.builder(ANY_PATCH_OP)
          .addConditionalConstraint(typeVariable("Value", bound(SAME_TYPE, className)))
          .addFunction(
            FunctionSpec.builder("merge")
              .addModifiers(PUBLIC, STATIC)
              .returns(SelfTypeName.INSTANCE)
              .apply {
                for (propertySpec in propertySpecs) {
                  addParameter(
                    ParameterSpec.builder(propertySpec.name, propertySpec.type)
                      .defaultValue(".none")
                      .build(),
                  )
                }
              }
              .addStatement(
                "%T.merge(%T(%L))",
                SelfTypeName.INSTANCE,
                className,
                propertySpecs.map { CodeBlock.of("%L: %L", it.name, it.name) }.joinToCode(",%W"),
              )
              .build(),
          )
          .build()

      typeBuilder.associatedExtensions.add(patchOpExt)
    }

    codingKeysType?.let { typeBuilder.addType(it) }

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    return className
  }

  private fun defineEnum(shape: Shape, context: SwiftResolutionContext): DeclaredTypeName {

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

    val enumBuilder = defineType(className) {
      TypeSpec.enumBuilder(it)
        .addModifiers(PUBLIC)
        .addSuperTypes(listOf(STRING, CASE_ITERABLE, CODABLE))
    }

    enumBuilder.tag(DefinitionLocation(shape))

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->
      enumBuilder.addEnumCase(enum.swiftEnumName, enum.stringValue!!)
    }

    return className
  }

  private fun defineType(
    className: DeclaredTypeName,
    builderBlock: (DeclaredTypeName) -> TypeSpec.Builder,
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

    if (!shape.hasExplicitName() && context.suggestedTypeName != null) {
      return context.suggestedTypeName
    }

    val moduleName = moduleNameOf(shape, context)

    val nestedAnn = shape.findAnnotation(Nested, null)
      ?: return typeName("$moduleName.${shape.swiftTypeName}")

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

    val nestedEnclosingTypeContext = context.copy(unit = nestedEnclosingTypeUnit, suggestedTypeName = null)

    val nestedEnclosingTypeName =
      resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as? DeclaredTypeName
        ?: genError("Nested annotation references non-defining enclosing type", nestedAnn)

    return nestedEnclosingTypeName.nestedType(nestedName)
  }

  private fun moduleNameOf(shape: Shape, context: SwiftResolutionContext): String =
    moduleNameOf(context.findDeclaringUnit(shape))

  private fun moduleNameOf(unit: BaseUnit?): String =
    (unit as? CustomizableElement)?.findStringAnnotation(APIAnnotationName.SwiftModelModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(APIAnnotationName.SwiftModelModule, null)
      ?: ""

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

  private fun nearestCommonAncestor(types: List<Shape>, context: SwiftResolutionContext): DeclaredTypeName? {

    var currentClassNameHierarchy: List<DeclaredTypeName>? = null
    for (type in types) {
      val propertyClassNameHierarchy = classNameHierarchy(type, context) ?: break
      currentClassNameHierarchy =
        if (currentClassNameHierarchy == null) {
          propertyClassNameHierarchy
        } else {
          (0 until min(propertyClassNameHierarchy.size, currentClassNameHierarchy.size))
            .takeWhile { propertyClassNameHierarchy[it] == currentClassNameHierarchy!![it] }
            .map { propertyClassNameHierarchy[it] }
        }
    }

    return currentClassNameHierarchy?.firstOrNull()
  }

  private fun classNameHierarchy(shape: Shape, context: SwiftResolutionContext): List<DeclaredTypeName>? {

    val names = mutableListOf<DeclaredTypeName>()

    var current: Shape? = shape
    while (current != null) {
      val currentClass = resolveReferencedTypeName(current, context) as? DeclaredTypeName ?: return null
      names.add(currentClass)
      current = context.findSuperShapeOrNull(current)
    }

    return names.reversed()
  }

  private fun findDiscriminatorPropertyName(shape: NodeShape, context: SwiftResolutionContext): String? =
    when {
      !shape.discriminator.isNullOrEmpty() -> shape.discriminator
      else -> context.findSuperShapeOrNull(shape)?.let { findDiscriminatorPropertyName(it as NodeShape, context) }
    }

  private fun findDiscriminatorPropertyValue(shape: NodeShape, context: SwiftResolutionContext): String? =
    if (!shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = context.findRootShape(shape) as NodeShape
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

    private const val SUNDAY_MODULE = "Sunday"
    private val ANY_PATCH_OP = typeName("$SUNDAY_MODULE.AnyPatchOp")
    private val UPDATE_OP = typeName("$SUNDAY_MODULE.UpdateOp")
    private val PATCH_OP = typeName("$SUNDAY_MODULE.PatchOp")
  }
}

class AssociatedExtensions : ArrayList<ExtensionSpec>()

private val TypeSpec.Builder.associatedExtensions: AssociatedExtensions
  get() {
    var value = tags[AssociatedExtensions::class] as AssociatedExtensions?
    if (value == null) {
      value = AssociatedExtensions()
      tag(value)
    }
    return value
  }
