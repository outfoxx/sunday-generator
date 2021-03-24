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

package io.outfoxx.sunday.generator.typescript

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
import io.outfoxx.sunday.generator.APIAnnotationName.ExternalDiscriminator
import io.outfoxx.sunday.generator.APIAnnotationName.ExternallyDiscriminated
import io.outfoxx.sunday.generator.APIAnnotationName.Nested
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptImpl
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptModelModule
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptType
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.TypeRegistry
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.utils.DURATION
import io.outfoxx.sunday.generator.typescript.utils.JSON_CLASS_TYPE
import io.outfoxx.sunday.generator.typescript.utils.JSON_IGNORE
import io.outfoxx.sunday.generator.typescript.utils.JSON_PROPERTY
import io.outfoxx.sunday.generator.typescript.utils.JSON_SUB_TYPES
import io.outfoxx.sunday.generator.typescript.utils.JSON_TYPE_INFO
import io.outfoxx.sunday.generator.typescript.utils.JSON_TYPE_INFO_AS
import io.outfoxx.sunday.generator.typescript.utils.JSON_TYPE_INFO_ID
import io.outfoxx.sunday.generator.typescript.utils.JSON_TYPE_NAME
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATE
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_TIME
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.PARTIAL
import io.outfoxx.sunday.generator.typescript.utils.PROBLEM
import io.outfoxx.sunday.generator.typescript.utils.nullable
import io.outfoxx.sunday.generator.typescript.utils.typeInitializer
import io.outfoxx.sunday.generator.typescript.utils.typeScriptEnumName
import io.outfoxx.sunday.generator.typescript.utils.typeScriptIdentifierName
import io.outfoxx.sunday.generator.typescript.utils.typeScriptTypeName
import io.outfoxx.sunday.generator.typescript.utils.undefinable
import io.outfoxx.sunday.generator.utils.aggregateInheritanceNode
import io.outfoxx.sunday.generator.utils.aggregateInheritanceSuper
import io.outfoxx.sunday.generator.utils.and
import io.outfoxx.sunday.generator.utils.anyInheritance
import io.outfoxx.sunday.generator.utils.anyInheritanceNode
import io.outfoxx.sunday.generator.utils.anyInheritanceSuper
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
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
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.optional
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.properties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.xone
import io.outfoxx.typescriptpoet.AnyTypeSpecBuilder
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.DecoratorSpec
import io.outfoxx.typescriptpoet.EnumSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.ANY
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY_BUFFER
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN
import io.outfoxx.typescriptpoet.TypeName.Companion.MAP
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT
import io.outfoxx.typescriptpoet.TypeName.Companion.SET
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import io.outfoxx.typescriptpoet.tag
import java.net.URI
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

class TypeScriptTypeRegistry(
  private val options: Set<Option>
) : TypeRegistry {

  data class Id(val value: String)
  data class ImplementationClass(val value: ClassSpec.Builder)

  enum class Option {
    JacksonDecorators,
    AddGenerationHeader
  }

  private val typeBuilders = mutableMapOf<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()

  override fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path) {

    val builtTypes = buildTypes()

    builtTypes
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { (typeName, moduleSpec) ->

        val imported = typeName.base as SymbolSpec.Imported
        val modulePath = imported.source.replaceFirst("!", "")

        FileSpec.get(moduleSpec, modulePath)
      }
      .forEach { it.writeTo(outputDirectory) }

    listOf(generateIndexFile(builtTypes))
      .forEach { it.writeTo(outputDirectory) }
  }

  private fun generateIndexFile(types: Map<TypeName.Standard, ModuleSpec>): FileSpec {

    val indexBuilder = FileSpec.builder("index")

    types.keys.forEach { name ->
      indexBuilder.addCode(
        CodeBlock.of(
          "export * from './%L';",
          name.base.value.removePrefix("!").camelCaseToKebabCase()
        )
      )
    }

    return indexBuilder.build()
  }

  fun buildTypes(): Map<TypeName.Standard, ModuleSpec> {

    val typeModBuilders = mutableMapOf<TypeName.Standard, ModuleSpec.Builder>()

    fun getTypeModBuilder(typeName: TypeName.Standard) =
      typeModBuilders.computeIfAbsent(typeName) {
        ModuleSpec.builder(typeName.simpleName(), ModuleSpec.Kind.NAMESPACE)
          .addModifier(Modifier.EXPORT)
      }

    // Add nested classes to parent modules
    typeBuilders.entries
      .toList()
      .sortedByDescending { it.key.simpleNames().size }
      .forEach { (typeName, typeBuilder) ->
        // Is this a nested type?
        val enclosingTypeName = typeName.enclosingTypeName() ?: return@forEach
        val enclosingMod = getTypeModBuilder(enclosingTypeName)

        // Add types
        val typeSpec = typeBuilder.build()
        enclosingMod.addType(typeSpec)
        typeSpec.tag<ImplementationClass>()?.value?.let { enclosingMod.addClass(it.build()) }

        // Add nested module (if exists)
        typeModBuilders[typeName]?.let { enclosingMod.addModule(it.build()) }
      }

    return typeBuilders
      .filterKeys { it.isTopLevelTypeName }
      .mapValues { (typeName, typeBuilder) ->

        val rootTypeSpec = typeBuilder.build()

        val rootModuleSpec =
          ModuleSpec.builder(typeName.simpleName(), ModuleSpec.Kind.MODULE)
            .addType(rootTypeSpec)

        rootModuleSpec.tags.putAll(rootTypeSpec.tags)

        when (typeBuilder) {
          is InterfaceSpec.Builder -> {
            (typeBuilder.tags[ImplementationClass::class] as? ImplementationClass)?.let {
              rootModuleSpec.addClass(it.value.build())
            }
          }
          is ClassSpec.Builder -> {
            (typeBuilder.tags[CodeBlock.Builder::class] as? CodeBlock.Builder)?.let {
              if (it.isNotEmpty()) {
                rootModuleSpec.addCode(it.build())
              }
            }
          }
        }

        val typeModBuilder = getTypeModBuilder(typeName)
        if (typeModBuilder.isNotEmpty()) {
          rootModuleSpec.addModule(typeModBuilder.build())
        }

        rootModuleSpec.build()
      }
  }

  fun resolveTypeName(shape: Shape, context: TypeScriptResolutionContext): TypeName {

    val resolvedShape = shape.resolve

    var typeName = typeNameMappings[resolvedShape.id]
    if (typeName == null) {

      typeName = generateTypeName(resolvedShape, context)

      typeNameMappings[resolvedShape.id] = typeName
    }

    return typeName
  }

  fun addServiceType(typeName: TypeName.Standard, serviceType: ClassSpec.Builder) {

    serviceType.addModifiers(Modifier.EXPORT)

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (options.contains(AddGenerationHeader)) {
      serviceType.addTSDoc("Generated By: %L\n", javaClass.name)
      serviceType.addTSDoc("Generated On: %L\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    if (typeBuilders.putIfAbsent(typeName, serviceType) != null) {
      genError("Service type '$typeName' is already defined")
    }
  }

  fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition
  ): TypeName {

    val problemTypeNameStr = "${problemCode.typeScriptTypeName}Problem"
    val problemTypeName = TypeName.namedImport(problemTypeNameStr, "!${problemTypeNameStr.camelCaseToKebabCase()}")

    typeBuilders.computeIfAbsent(problemTypeName) {

      val problemTypeBuilder =
        ClassSpec.builder(problemTypeName)
          .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
          .addModifiers(Modifier.EXPORT)
          .superClass(PROBLEM)
          .addProperty(
            PropertySpec.builder("TYPE", STRING)
              .addModifiers(Modifier.STATIC)
              .initializer("%S", problemTypeDefinition.type)
              .build()
          )

      val problemTypeConsBuilder =
        FunctionSpec.constructorBuilder()
          .addCode(
            """
            |super(
            |  %T.TYPE,
            |  %S,
            |  %L,
            |  %S,
            |  instance
            |);
            |
            """.trimMargin(),
            problemTypeName,
            problemTypeDefinition.title,
            problemTypeDefinition.status,
            problemTypeDefinition.detail,
          )

      // Add all custom properties
      problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->

        val customPropertyTypeName = resolveTypeReference(
          customPropertyTypeNameStr,
          problemTypeDefinition.source,
          TypeScriptResolutionContext(problemTypeDefinition.definedIn, null)
        )
        val customPropertyClassName =
          if (typeBuilders[customPropertyTypeName] !is EnumSpec.Builder)
            customPropertyTypeName
          else
            OBJECT

        problemTypeBuilder.addProperty(
          PropertySpec
            .builder(customPropertyName.kotlinIdentifierName, customPropertyTypeName)
            .apply {
              if (options.contains(JacksonDecorators)) {

                if (customPropertyName != customPropertyName.kotlinIdentifierName) {
                  addDecorator(
                    DecoratorSpec.builder(JSON_PROPERTY)
                      .addParameter(null, "{value: %S}", customPropertyName)
                      .build()
                  )
                }

                addDecorator(
                  DecoratorSpec.builder(JSON_CLASS_TYPE)
                    .addParameter(
                      null,
                      CodeBlock.builder()
                        .add("{type: () => ")
                        .add(customPropertyClassName.typeInitializer())
                        .add("}")
                        .build()
                    )
                    .build()
                )
              }
            }
            .build()
        )

        problemTypeConsBuilder
          .addStatement(
            "this.%L = %L",
            customPropertyName.kotlinIdentifierName,
            customPropertyName.kotlinIdentifierName
          )
          .addParameter(
            ParameterSpec
              .builder(
                customPropertyName.kotlinIdentifierName,
                resolveTypeReference(
                  customPropertyTypeNameStr,
                  problemTypeDefinition.source,
                  TypeScriptResolutionContext(problemTypeDefinition.definedIn, null)
                )
              )
              .build()
          )
      }

      problemTypeConsBuilder.addParameter(
        ParameterSpec.builder("instance", STRING.nullable)
          .defaultValue("null")
          .build()
      )

      if (options.contains(JacksonDecorators)) {
        problemTypeBuilder.addDecorator(
          DecoratorSpec.builder(JSON_TYPE_NAME)
            .addParameter(null, "{value: %T.TYPE}", problemTypeName)
            .build()
        )
      }

      problemTypeBuilder.constructor(problemTypeConsBuilder.build())
    }

    return problemTypeName
  }

  private fun resolveTypeReference(
    nameStr: String,
    source: DomainElement,
    context: TypeScriptResolutionContext
  ): TypeName {
    val typeNameStr = nameStr.removeSuffix("?")
    val elementTypeNameStr = typeNameStr.removeSuffix("[]")
    val elementTypeName =
      when (elementTypeNameStr.toLowerCase()) {
        "boolean" -> BOOLEAN
        "integer" -> NUMBER
        "number" -> NUMBER
        "string" -> STRING
        "object" -> TypeName.mapType(STRING, STRING)
        "any" -> ANY
        "file" -> ARRAY_BUFFER
        "time-ony" -> LOCAL_TIME
        "date-ony" -> LOCAL_DATE
        "datetime-only" -> LOCAL_DATETIME
        "datetime" -> OFFSET_DATETIME
        else -> {
          val (element, unit) = context.resolveRef(elementTypeNameStr, source)
            ?: genError("Invalid type reference '$elementTypeNameStr'", source)
          element as? Shape ?: genError("Invalid type reference '$elementTypeNameStr'", source)

          resolveReferencedTypeName(element, TypeScriptResolutionContext(unit, null))
        }
      }
    val typeName =
      if (typeNameStr.endsWith("[]")) {
        TypeName.parameterizedType(ARRAY, elementTypeName)
      } else {
        elementTypeName
      }
    return if (nameStr.endsWith("?"))
      typeName.nullable
    else
      typeName
  }

  private fun resolveReferencedTypeName(shape: Shape, context: TypeScriptResolutionContext): TypeName =
    resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: TypeName.Standard,
    context: TypeScriptResolutionContext
  ): TypeName {

    val propertyContext = context.copy(suggestedTypeName = className.nested(propertyShape.typeScriptTypeName))

    val baseTypeName = resolveTypeName(propertyShape.range, propertyContext)
    return if (propertyShape.optional) {
      baseTypeName.undefinable
    } else {
      baseTypeName
    }
  }

  private fun generateTypeName(shape: Shape, context: TypeScriptResolutionContext): TypeName {

    val typeScriptTypeAnn = shape.findStringAnnotation(TypeScriptType, null)
    if (typeScriptTypeAnn != null) {
      return TypeName.standard(typeScriptTypeAnn)
    }

    return processShape(shape, context)
  }

  private fun processShape(shape: Shape, context: TypeScriptResolutionContext): TypeName =
    when (shape) {
      is ScalarShape -> processScalarShape(shape, context)
      is ArrayShape -> processArrayShape(shape, context)
      is UnionShape -> processUnionShape(shape, context)
      is NodeShape -> processNodeShape(shape, context)
      is FileShape -> ARRAY_BUFFER
      is NilShape -> VOID
      is AnyShape -> processAnyShape(shape, context)
      else -> genError("Shape type '${shape::class.simpleName}' is unsupported", shape)
    }

  private fun processAnyShape(shape: AnyShape, context: TypeScriptResolutionContext): TypeName =
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

  private fun processScalarShape(shape: ScalarShape, context: TypeScriptResolutionContext): TypeName =
    when (shape.dataType) {
      DataType.String() ->

        if (shape.values.isNotEmpty()) {
          defineEnum(shape, context)
        } else {
          when (shape.format) {
            "time" -> LOCAL_TIME
            "datetime-only", "date-time-only" -> LOCAL_DATETIME
            else -> STRING
          }
        }

      DataType.Boolean() -> BOOLEAN

      DataType.Integer() ->
        when (shape.format) {
          "int8" -> NUMBER
          "int16" -> NUMBER
          "int32", "int" -> NUMBER
          "", null -> NUMBER
          else -> genError("Integer format '${shape.format}' is unsupported", shape)
        }

      DataType.Long() -> NUMBER

      DataType.Float() -> NUMBER
      DataType.Double() -> NUMBER
      DataType.Number() -> NUMBER

      DataType.Decimal() -> NUMBER

      DataType.Duration() -> DURATION
      DataType.Date() -> LOCAL_DATE
      DataType.Time() -> LOCAL_TIME
      DataType.DateTimeOnly() -> LOCAL_DATETIME
      DataType.DateTime() -> OFFSET_DATETIME

      DataType.Binary() -> ARRAY_BUFFER

      else -> genError("Scalar data type '${shape.dataType}' is unsupported", shape)
    }

  private fun processArrayShape(shape: ArrayShape, context: TypeScriptResolutionContext): TypeName {

    val elementType = resolveReferencedTypeName(shape.items!!, context)

    val collectionType =
      if (shape.uniqueItems == true) {
        SET
      } else {
        ARRAY
      }

    return TypeName.parameterizedType(collectionType, elementType)
  }

  private fun processUnionShape(shape: UnionShape, context: TypeScriptResolutionContext): TypeName =
    if (shape.makesNullable) {
      resolveReferencedTypeName(shape.nullableType, context).nullable
    } else {
      nearestCommonAncestor(shape.anyOf, context)
        ?: TypeName.unionType(*shape.anyOf.map { resolveReferencedTypeName(it, context) }.toTypedArray())
    }

  private fun processNodeShape(shape: NodeShape, context: TypeScriptResolutionContext): TypeName {

    if (shape.properties.isEmpty() && shape.inherits.size == 1 && context.unit.findInheritingTypes(shape).isEmpty()) {
      return resolveReferencedTypeName(shape.inherits[0], context)
    }

    if (shape.properties.isEmpty() && shape.inherits.isEmpty() && shape.closed != true) {

      val allTypes = collectTypes(shape.properties().map { it.range })

      val commonType = nearestCommonAncestor(allTypes, context) ?: return OBJECT

      return TypeName.parameterizedType(MAP, STRING, commonType)
    }

    return defineClass(shape, shape.inherits.firstOrNull()?.let { it.resolve as NodeShape }, shape, context)
  }

  private fun defineClass(
    shape: Shape,
    superShape: Shape?,
    propertyContainerShape: NodeShape,
    context: TypeScriptResolutionContext
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

    val ifaceBuilder =
      defineType(className) { name ->
        InterfaceSpec.builder(name.simpleName())
          .tag(Id::class, Id(shape.id))
          .addModifiers(Modifier.EXPORT)
      } as InterfaceSpec.Builder

    val classBuilder =
      ClassSpec.builder(className.simpleName())
        .tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)
        .tag(Id::class, Id(shape.id))
        .addModifiers(Modifier.EXPORT)
        .addMixin(className)

    val superClassName =
      if (superShape != null) {
        val superClassName = resolveReferencedTypeName(superShape, context)
        ifaceBuilder.addSuperInterface(superClassName)
        classBuilder.superClass(superClassName)
        superClassName
      } else {
        null
      }

    var inheritedProperties = collectProperties(superShape)
    var declaredProperties = propertyContainerShape.properties

    val inheritingTypes = context.unit.findInheritingTypes(shape)

    if (inheritedProperties.isNotEmpty() || declaredProperties.isNotEmpty()) {

      /*
        Computed discriminator values (for jackson generation)
       */

      if (options.contains(JacksonDecorators)) {

        val discriminatorPropertyName = findDiscriminatorPropertyName(shape)
        if (discriminatorPropertyName != null) {

          val discriminatorProperty =
            (inheritedProperties + declaredProperties).find { it.name == discriminatorPropertyName }
              ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

          val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)

          declaredProperties = declaredProperties.filter { it.name != discriminatorPropertyName }
          inheritedProperties = inheritedProperties.filter { it.name != discriminatorPropertyName }

          if (propertyContainerShape.discriminator == discriminatorPropertyName) {
            ifaceBuilder.addProperty(discriminatorProperty.typeScriptIdentifierName, discriminatorPropertyTypeName)
          }

          // Add concrete discriminator for leaf of the discriminated tree

          if (propertyContainerShape.discriminator != discriminatorPropertyName) {

            val discriminatorBuilder =
              FunctionSpec.builder(discriminatorProperty.typeScriptIdentifierName)
                .addModifiers(Modifier.GET)
                .returns(discriminatorPropertyTypeName)

            val discriminatorValue = findDiscriminatorPropertyValue(shape, context) ?: shape.name!!

            val isEnum = typeBuilders[discriminatorPropertyTypeName] is EnumSpec.Builder
            if (isEnum) {
              discriminatorBuilder
                .addStatement(
                  "return %T.%L",
                  discriminatorPropertyTypeName,
                  discriminatorValue.toUpperCamelCase()
                )
                .build()
            } else {
              discriminatorBuilder
                .addStatement("return %S", discriminatorValue)
                .build()
            }

            classBuilder.addFunction(discriminatorBuilder.build())
          } else {
            if (declaredProperties.isEmpty()) {
              classBuilder.addModifiers(Modifier.ABSTRACT)
            }
          }
        }
      }

      val definedProperties = declaredProperties.filterNot { it.range.hasAnnotation(TypeScriptImpl, null) }

      /*
        Generate interface implementation
       */

      definedProperties.forEach { declaredProperty ->

        val propertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)

        // Add public field
        //
        ifaceBuilder.addProperty(
          PropertySpec
            .builder(declaredProperty.typeScriptIdentifierName, propertyTypeName)
            .build()
        )
      }

      /*
        Generate class implementation
       */

      // Build parameter constructor
      //
      var paramConsBuilder: FunctionSpec.Builder? = null
      if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {

        paramConsBuilder = FunctionSpec.constructorBuilder()

        inheritedProperties.forEach { inheritedProperty ->
          paramConsBuilder.addParameter(
            inheritedProperty.typeScriptIdentifierName,
            resolvePropertyTypeName(inheritedProperty, className, context),
          )
        }

        if (superClassName != null) {
          paramConsBuilder
            .addStatement(
              "super(${inheritedProperties.joinToString(",%W") { "%N" }})",
              *inheritedProperties.map { it.typeScriptIdentifierName }.toTypedArray()
            )
        }

        definedProperties.forEach { propertyShape ->
          paramConsBuilder.addStatement(
            "this.%L = %L",
            propertyShape.kotlinIdentifierName, propertyShape.kotlinIdentifierName
          )
        }
      }

      // toString builder
      //
      val toStringCode = mutableListOf<CodeBlock>()
      inheritedProperties.forEach {
        toStringCode.add(
          CodeBlock.of(
            "%L='${'$'}{this.%L}'",
            it.typeScriptIdentifierName,
            it.typeScriptIdentifierName
          )
        )
      }

      // Generate related code for each property
      //
      declaredProperties.forEach { declaredProperty ->

        val declaredPropertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)
        val declaredPropertyClassName =
          if (typeBuilders[declaredPropertyTypeName] !is EnumSpec.Builder)
            declaredPropertyTypeName
          else
            OBJECT

        val implAnn = declaredProperty.range.findAnnotation(TypeScriptImpl, null) as? ObjectNode
        if (implAnn != null) {

          val declaredPropertyGetterBuilder =
            FunctionSpec.builder(declaredProperty.typeScriptIdentifierName)
              .addModifiers(Modifier.GET)
              .returns(declaredPropertyTypeName)
              .addDecorator(
                DecoratorSpec.builder(JSON_IGNORE)
                  .asFactory()
                  .build()
              )

          val code = implAnn.getValue("code") ?: ""
          val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
          val convertedCodeParams = codeParams.map { codeParam ->
            val atype = codeParam.getValue("type")
            val avalue = codeParam.getValue("value")
            if (atype != null && avalue != null) {
              when (atype) {
                "Type" -> TypeName.standard(avalue.toString())
                else -> avalue.toString()
              }
            } else {
              ""
            }
          }

          declaredPropertyGetterBuilder
            .addStatement(code, *convertedCodeParams.toTypedArray())

          classBuilder.addFunction(declaredPropertyGetterBuilder.build())
        } else {

          val declaredPropertyBuilder =
            PropertySpec.builder(
              declaredProperty.typeScriptIdentifierName,
              declaredPropertyTypeName,
            )

          val externalDiscriminatorPropertyName =
            declaredProperty.range.findStringAnnotation(ExternalDiscriminator, null)
          if (externalDiscriminatorPropertyName != null) {

            declaredProperty.range as? NodeShape
              ?: genError("Externally discriminated types must be 'object'", declaredProperty)

            (inheritedProperties + declaredProperties).find { it.name == externalDiscriminatorPropertyName }
              ?: genError("External discriminator '$externalDiscriminatorPropertyName' not found in object", shape)

            if (options.contains(JacksonDecorators)) {
              addJacksonPolymorphismOverride(declaredPropertyBuilder, externalDiscriminatorPropertyName)
            }
          }

          if (options.contains(JacksonDecorators)) {
            if (declaredProperty.typeScriptIdentifierName != declaredProperty.name) {
              declaredPropertyBuilder
                .addDecorator(
                  DecoratorSpec.builder(JSON_PROPERTY)
                    .addParameter(null, "{value: %S}", declaredProperty.name())
                    .build()
                )
            }

            declaredPropertyBuilder
              .addDecorator(
                DecoratorSpec.builder(JSON_CLASS_TYPE)
                  .addParameter(
                    null,
                    CodeBlock.builder()
                      .add("{type: () => ")
                      .add(declaredPropertyClassName.typeInitializer())
                      .add("}")
                      .build()
                  )
                  .build()
              )
          }

          // Add constructor parameter
          //
          paramConsBuilder?.addParameter(declaredProperty.typeScriptIdentifierName, declaredPropertyTypeName)

          // Add toString value
          //
          toStringCode.add(
            CodeBlock.of(
              "%L='\${this.%L}'",
              declaredProperty.typeScriptIdentifierName,
              declaredProperty.typeScriptIdentifierName
            )
          )

          classBuilder.addProperty(declaredPropertyBuilder.build())
        }
      }

      // Add copy method
      //
      if (inheritingTypes.isEmpty()) {

        val copyBuilder =
          FunctionSpec.builder("copy")
            .returns(className)
            .addParameter("src", TypeName.parameterizedType(PARTIAL, className))
            .addCode("%[return new %T(", className)

        val copyArgs =
          (inheritedProperties + definedProperties).map { copyProperty ->

            CodeBlock.of(
              "src.%L ?? this.%L",
              copyProperty.typeScriptIdentifierName,
              copyProperty.typeScriptIdentifierName
            )
          }

        copyBuilder
          .addCode(copyArgs.joinToString(",%W"))
          .addCode(");%]\n")

        classBuilder.addFunction(copyBuilder.build())
      }

      // Finish parameter constructor
      paramConsBuilder?.let {
        classBuilder.constructor(it.build())
      }

      // Finish toString method
      val toStringTemplate =
        CodeBlock.of(
          "%N(%L)",
          className, toStringCode.joinToString(", ")
        ).toString()

      classBuilder.addFunction(
        FunctionSpec.builder("toString")
          .returns(STRING)
          .addStatement("return %P", toStringTemplate)
          .build()
      )
    }

    if (shape.findBoolAnnotation(Patchable, null) == true) {

      val patchClassName = className.nested("Patch")

      val patchClassBuilder =
        defineType(patchClassName) { name ->
          ClassSpec.builder(name.simpleName())
            .addModifiers(Modifier.EXPORT)
        } as ClassSpec.Builder

      val patchClassConsBuilder = FunctionSpec.constructorBuilder()

      val patchFields = mutableListOf<CodeBlock>()

      for (propertyDecl in propertyContainerShape.properties) {
        val propertyTypeName = resolveReferencedTypeName(propertyDecl.range, context).nullable

        patchClassBuilder.addProperty(
          PropertySpec.builder(propertyDecl.typeScriptIdentifierName, propertyTypeName, optional = true)
            .initializer(propertyDecl.typeScriptIdentifierName)
            .build()
        )
        patchClassConsBuilder.addParameter(
          ParameterSpec.builder(propertyDecl.typeScriptIdentifierName, propertyTypeName, optional = true)
            .build()
        )

        patchFields.add(
          CodeBlock.of(
            "source[%S] !== undefined ? this.%L : null",
            propertyDecl.typeScriptIdentifierName, propertyDecl.typeScriptIdentifierName
          )
        )
      }

      patchClassBuilder.constructor(patchClassConsBuilder.build())

      classBuilder.addFunction(
        FunctionSpec.builder("patch")
          .addParameter(
            ParameterSpec.builder("source", PARTIAL.parameterized(className))
              .build()
          )
          .returns(patchClassName)
          .addCode(
            CodeBlock.builder()
              .add("return new %T(", patchClassName).indent().add("\n")
              .add(patchFields.joinToCode(",\n"))
              .unindent().add("\n);\n")
              .build()
          )
          .build()
      )

      if (options.contains(AddGenerationHeader)) {
        classBuilder.addTSDoc("Generated By: %L\n", javaClass.name)
        classBuilder.addTSDoc("Generated On: %L\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      }
    }

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    if (options.contains(JacksonDecorators)) {

      if (!propertyContainerShape.discriminator.isNullOrBlank()) {
        addJacksonPolymorphism(propertyContainerShape, inheritingTypes, className, classBuilder, context)
      }
    }

    if (options.contains(AddGenerationHeader)) {
      ifaceBuilder.addTSDoc("Generated By: %L\n", javaClass.name)
      ifaceBuilder.addTSDoc("Generated On: %L\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
      classBuilder.addTSDoc("Generated By: %L\n", javaClass.name)
      classBuilder.addTSDoc("Generated On: %L\n", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    ifaceBuilder.tag(ImplementationClass::class, ImplementationClass(classBuilder))

    return className
  }

  private fun defineEnum(shape: Shape, context: TypeScriptResolutionContext): TypeName {

    val className = typeNameOf(shape, context)

    val enumBuilder = defineType(className) { name ->
      EnumSpec.builder(name.simpleName())
        .addModifiers(Modifier.EXPORT)
    } as EnumSpec.Builder

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->
      enumBuilder.addConstant(enum.typeScriptEnumName, CodeBlock.of("%S", enum.stringValue))
    }

    if (options.contains(AddGenerationHeader)) {
      enumBuilder.addTSDoc("Generated By: %L", javaClass.name)
      enumBuilder.addTSDoc("Generated On: %L", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    }

    return className
  }

  private fun defineType(
    className: TypeName.Standard,
    builderBlock: (TypeName.Standard) -> AnyTypeSpecBuilder
  ): AnyTypeSpecBuilder {

    val builder = builderBlock(className)

    builder.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, builder) != null) {
      genError("Multiple types with name '$className' defined")
    }

    return builder
  }

  private fun addJacksonPolymorphismOverride(
    propertySpec: PropertySpec.Builder,
    externalDiscriminatorPropertyName: String
  ) {

    propertySpec.addDecorator(
      DecoratorSpec.builder(JSON_TYPE_INFO)
        .addParameter(
          null,
          """
            {
              use: %Q.%L,
              include: %Q.%L,
              property: %S,
            }
          """.trimIndent(),
          JSON_TYPE_INFO_ID, "NAME",
          JSON_TYPE_INFO_AS, "EXTERNAL_PROPERTY",
          externalDiscriminatorPropertyName
        )
        .build()
    )
  }

  private fun addJacksonPolymorphism(
    shape: NodeShape,
    inheritingTypes: List<Shape>,
    className: TypeName.Standard,
    classBuilder: ClassSpec.Builder,
    context: TypeScriptResolutionContext
  ) {

    val discriminatorPropertyName = findDiscriminatorPropertyName(shape)
    val discriminatorPropertyShape = collectProperties(shape).first { it.name == discriminatorPropertyName }
    val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorPropertyShape, className, context)
    val isDiscriminatorEnum = typeBuilders[discriminatorPropertyTypeName] is EnumSpec.Builder

    val discriminatorMappings = buildDiscriminatorMappings(shape, context)

    val subTypes = inheritingTypes
      .map { inheritingType ->

        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingType.id }?.key
        val discriminatorValue =
          inheritingType.anyInheritanceNode?.discriminatorValue ?: mappedDiscriminator ?: inheritingType.name

        if (isDiscriminatorEnum) {
          val enumDiscriminatorValue =
            (typeBuilders[discriminatorPropertyTypeName] as EnumSpec.Builder)
              .constants.entries
              .first { it.value?.toString() == "'$discriminatorValue'" }.key

          "{class: () => eval('%T'), name: %T.%L}" to listOf(
            resolveReferencedTypeName(inheritingType, context),
            discriminatorPropertyTypeName,
            enumDiscriminatorValue,
          )
        } else {
          "{class: () => eval('%T'), name: %S}" to listOf(
            resolveReferencedTypeName(inheritingType, context),
            discriminatorValue,
          )
        }
      }

    if (subTypes.isNotEmpty()) {

      if (shape.findBoolAnnotation(ExternallyDiscriminated, null) != true) {
        classBuilder.addDecorator(
          DecoratorSpec.builder(JSON_TYPE_INFO)
            .addParameter(
              null,
              """
                {
                  use: %Q.%L,
                  include: %Q.%L,
                  property: %S,
                }
              """.trimIndent(),
              JSON_TYPE_INFO_ID, "NAME",
              JSON_TYPE_INFO_AS, "PROPERTY",
              shape.discriminator
            )
            .build()
        )
      }

      classBuilder.addDecorator(
        DecoratorSpec.builder(JSON_SUB_TYPES)
          .addParameter(
            null,
            """
            |{
            |  types: [
            |    ${subTypes.joinToString(",\n    ") { it.first }}
            |  ]
            |}
            """.trimMargin(),
            *subTypes.map { it.second }.flatten().toTypedArray()
          )
          .build()
      )
    }
  }

  private fun typeNameOf(shape: Shape, context: TypeScriptResolutionContext): TypeName.Standard {

    if (context.suggestedTypeName != null) {
      val typeIdFrag = URI(shape.id).fragment
      val typeName = shape.name
      if (typeIdFrag.endsWith("/property/$typeName/$typeName") || !typeIdFrag.startsWith("/declarations")) {
        return context.suggestedTypeName
      }
    }

    val modulePath = modulePathOf(shape, context)?.let { "!$it/" } ?: "!"

    val nestedAnn = shape.findAnnotation(Nested, null) as? ObjectNode

    return if (nestedAnn != null) {

      val nestedEnclosedIn = nestedAnn.getValue("enclosedIn")
        ?: genError("Nested annotation is missing parent", nestedAnn)

      val (nestedEnclosingType, nestedEnclosingTypeUnit) = context.resolveRef(nestedEnclosedIn, shape)
        ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

      nestedEnclosingType as? Shape
        ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

      val nestedEnclosingTypeContext = TypeScriptResolutionContext(nestedEnclosingTypeUnit, null)

      val nestedEnclosingTypeName =
        resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as TypeName.Standard

      val nestedName = nestedAnn.getValue("name")
        ?: genError("Nested annotation is missing name", nestedAnn)

      nestedEnclosingTypeName.nested(nestedName)
    } else {

      TypeName.namedImport(
        shape.typeScriptTypeName,
        "$modulePath${shape.typeScriptTypeName.camelCaseToKebabCase()}"
      )
    }
  }

  private fun modulePathOf(shape: Shape, context: TypeScriptResolutionContext): String? =
    modulePathOf(context.unit.findDeclaringUnit(shape))

  private fun modulePathOf(unit: BaseUnit?): String? =
    (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModelModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModelModule, null)

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

  private fun nearestCommonAncestor(types: List<Shape>, context: TypeScriptResolutionContext): TypeName? {

    var currentClassNameHierarchy: List<TypeName>? = null
    for (type in types) {
      val propertyClassNameHierarchy = classNameHierarchy(type.resolve, context)
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

  private fun classNameHierarchy(shape: Shape, context: TypeScriptResolutionContext): List<TypeName> {

    val names = mutableListOf<TypeName>()

    var current: Shape? = shape
    while (current != null) {
      val currentClass = resolveReferencedTypeName(current, context)
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

  private fun findDiscriminatorPropertyValue(shape: Shape, context: TypeScriptResolutionContext): String? =
    if (shape is NodeShape && !shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = shape.inheritanceRoot as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(shape: NodeShape, context: TypeScriptResolutionContext): Map<String, String> =
    shape.discriminatorMapping.mapNotNull { mapping ->
      val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
      mapping.templateVariable().value()!! to refElement.id
    }.toMap()
}
