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

import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.*
import amf.shapes.client.platform.model.domain.*
import io.outfoxx.sunday.generator.APIAnnotationName.*
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.TypeRegistry
import io.outfoxx.sunday.generator.common.DefinitionLocation
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.utils.*
import io.outfoxx.sunday.generator.utils.*
import io.outfoxx.typescriptpoet.*
import io.outfoxx.typescriptpoet.TypeName.Companion.ANY
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY_BUFFER
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN
import io.outfoxx.typescriptpoet.TypeName.Companion.NULL
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.SET
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import java.nio.file.Path
import kotlin.math.min

class TypeScriptTypeRegistry(
  private val options: Set<Option>,
) : TypeRegistry {

  data class SpecificationInterface(val value: InterfaceSpec.Builder)

  enum class Option {
    JacksonDecorators,
    AddGenerationHeader,
  }

  private val exportedTypeBuilders = LinkedHashMap<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeBuilders = mutableMapOf<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()

  override fun generateFiles(categories: Set<GeneratedTypeCategory>, outputDirectory: Path) {

    val fileSpecs = generateExportedTypeFiles(categories)

    fileSpecs
      .forEach { it.writeTo(outputDirectory) }

    listOf(generateIndexFile(fileSpecs))
      .forEach { it.writeTo(outputDirectory) }
  }

  fun generateExportedTypeFiles(categories: Set<GeneratedTypeCategory>) =
    buildTypes()
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { (typeName, moduleSpec) ->

        val imported = typeName.base as SymbolSpec.Imported
        val modulePath = imported.source.replaceFirst("!", "")

        modulePath to moduleSpec
      }
      .groupBy({ it.first }, { it.second })
      .map { (modulePath, moduleSpecs) ->

        val fileSpecBuilder =
          if (moduleSpecs.size == 1) {
            FileSpec.get(moduleSpecs.first(), modulePath).toBuilder()
          } else {
            val fileSpecBuilder = FileSpec.builder(modulePath)
            moduleSpecs.forEach { moduleSpec ->
              moduleSpec.members.forEach { member ->
                when (member) {
                  is FunctionSpec -> fileSpecBuilder.addFunction(member)
                  is PropertySpec -> fileSpecBuilder.addProperty(member)
                  is AnyTypeSpec -> fileSpecBuilder.addType(member)
                  is ModuleSpec -> fileSpecBuilder.addModule(member)
                  is CodeBlock -> fileSpecBuilder.addCode(member)
                }
              }
            }
            fileSpecBuilder
          }

        if (options.contains(AddGenerationHeader)) {
          val fileName = "${fileSpecBuilder.build().modulePath}.ts"
          fileSpecBuilder.addComment(GenerationHeaders.create(fileName))
        }

        fileSpecBuilder.build()
      }

  private fun generateIndexFile(files: List<FileSpec>): FileSpec {

    val indexBuilder = FileSpec.builder("index")

    files.forEach { file ->
      indexBuilder.addCode(
        CodeBlock.of(
          "export * from './%L';",
          file.modulePath,
        ),
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
    exportedTypeBuilders
      .toList()
      .sortedByDescending { it.first.simpleNames().size }
      .forEach { (typeName, typeBuilder) ->
        // Is this a nested type?
        val enclosingTypeName = typeName.enclosingTypeName() ?: return@forEach
        val enclosingMod = getTypeModBuilder(enclosingTypeName)

        // Add types
        val typeSpec = typeBuilder.build()
        typeSpec.tag<SpecificationInterface>()?.value?.let { enclosingMod.addInterface(it.build()) }
        enclosingMod.addType(typeSpec)

        // Add nested module (if exists)
        typeModBuilders[typeName]?.let { enclosingMod.addModule(it.build()) }
      }

    return exportedTypeBuilders
      .filterKeys { it.isTopLevelTypeName }
      .mapValues { (typeName, typeBuilder) ->

        val rootTypeSpec = typeBuilder.build()

        val rootModuleSpec =
          ModuleSpec.builder(typeName.simpleName(), ModuleSpec.Kind.MODULE)

        rootModuleSpec.tags.putAll(rootTypeSpec.tags)

        when (typeBuilder) {
          is ClassSpec.Builder -> {
            (typeBuilder.tags[SpecificationInterface::class] as? SpecificationInterface)?.let {
              rootModuleSpec.addInterface(it.value.build())
            }
            (typeBuilder.tags[CodeBlock.Builder::class] as? CodeBlock.Builder)?.let {
              if (it.isNotEmpty()) {
                rootModuleSpec.addCode(it.build())
              }
            }
          }
        }

        rootModuleSpec.addType(rootTypeSpec)

        val typeModBuilder = getTypeModBuilder(typeName)
        if (typeModBuilder.isNotEmpty()) {
          rootModuleSpec.addModule(typeModBuilder.build())
        }

        rootModuleSpec.build()
      }
  }

  fun resolveTypeName(shapeRef: Shape, context: TypeScriptResolutionContext): TypeName {

    val shape = context.dereference(shapeRef)

    var typeName = typeNameMappings[shape.uniqueId]
    if (typeName == null) {

      typeName = generateTypeName(shape, context)

      typeNameMappings[shape.id] = typeName
    }

    return typeName
  }

  fun unresolveTypeName(typeName: TypeName) {
    typeBuilders.remove(typeName)
    exportedTypeBuilders.remove(typeName)
  }

  fun addServiceType(typeName: TypeName.Standard, serviceType: ClassSpec.Builder) {

    serviceType.addModifiers(Modifier.EXPORT)

    serviceType.tag(GeneratedTypeCategory.Service)

    if (exportedTypeBuilders.putIfAbsent(typeName, serviceType) != null) {
      genError("Service type '$typeName' is already defined")
    }
  }

  override fun defineProblemType(
    problemCode: String,
    problemTypeDefinition: ProblemTypeDefinition,
    shapeIndex: ShapeIndex,
  ): TypeName {

    val problemTypeNameStr = "${problemCode.typeScriptTypeName}Problem"
    val problemTypeName = TypeName.namedImport(problemTypeNameStr, "!${problemTypeNameStr.camelCaseToKebabCase()}")

    exportedTypeBuilders.computeIfAbsent(problemTypeName) {

      val problemTypeBuilder =
        ClassSpec.builder(problemTypeName)
          .tag(GeneratedTypeCategory.Model)
          .addModifiers(Modifier.EXPORT)
          .superClass(PROBLEM)
          .addProperty(
            PropertySpec.builder("TYPE", STRING)
              .addModifiers(Modifier.STATIC)
              .initializer("%S", problemTypeDefinition.type)
              .build(),
          )

      val problemTypeConsBuilder =
        FunctionSpec.constructorBuilder()
          .addCode(
            """
            |super({
            |  type: %T.TYPE,
            |  title: %S,
            |  status: %L,
            |  detail: %S,
            |  instance
            |});
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
          TypeScriptResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
        )
        problemTypeBuilder.addProperty(
          PropertySpec
            .builder(customPropertyName.typeScriptIdentifierName, customPropertyTypeName)
            .apply {
              if (options.contains(JacksonDecorators)) {

                if (customPropertyName != customPropertyName.typeScriptIdentifierName) {
                  addDecorator(
                    DecoratorSpec.builder(JSON_PROPERTY)
                      .addJsonPropertyInit(
                        customPropertyName,
                        customPropertyName.typeScriptIdentifierName,
                        customPropertyTypeName.isUndefinable,
                      )
                      .build(),
                  )
                }

                addDecorator(
                  DecoratorSpec.builder(JSON_CLASS_TYPE)
                    .addParameter(
                      null,
                      CodeBlock.builder()
                        .add("{type: () => ")
                        .add(reflectionTypeName(customPropertyTypeName).typeInitializer())
                        .add("}")
                        .build(),
                    )
                    .build(),
                )
              }
            }
            .build(),
        )

        problemTypeConsBuilder
          .addStatement(
            "this.%L = %L",
            customPropertyName.typeScriptIdentifierName,
            customPropertyName.typeScriptIdentifierName,
          )
          .addParameter(
            ParameterSpec
              .builder(
                customPropertyName.typeScriptIdentifierName,
                resolveTypeReference(
                  customPropertyTypeNameStr,
                  problemTypeDefinition.source,
                  TypeScriptResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
                ),
              )
              .build(),
          )
      }

      problemTypeConsBuilder.addParameter(
        ParameterSpec.builder("instance", TypeName.unionType(STRING, URL_TYPE).undefinable)
          .defaultValue("undefined")
          .build(),
      )

      if (options.contains(JacksonDecorators)) {
        problemTypeBuilder.addDecorator(
          DecoratorSpec.builder(JSON_TYPE_NAME)
            .addParameter(null, "{value: %T.TYPE}", problemTypeName)
            .build(),
        )
      }

      problemTypeBuilder.constructor(problemTypeConsBuilder.build())
    }

    return problemTypeName
  }

  private fun resolveTypeReference(
    nameStr: String,
    source: DomainElement,
    context: TypeScriptResolutionContext,
  ): TypeName {
    val typeNameStr = nameStr.removeSuffix("?")
    val elementTypeNameStr = typeNameStr.removeSuffix("[]")
    val elementTypeName =
      when (elementTypeNameStr.lowercase()) {
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

          resolveReferencedTypeName(element, context.copy(unit = unit, suggestedTypeName = null))
        }
      }
    val typeName =
      if (typeNameStr.endsWith("[]")) {
        TypeName.parameterizedType(ARRAY, elementTypeName)
      } else {
        elementTypeName
      }
    return if (nameStr.endsWith("?")) {
      typeName.nullable
    } else {
      typeName
    }
  }

  private fun resolveReferencedTypeName(shape: Shape, context: TypeScriptResolutionContext): TypeName =
    resolveTypeName(shape, context.copy(suggestedTypeName = null))

  private fun resolvePropertyTypeName(
    propertyShape: PropertyShape,
    className: TypeName.Standard,
    context: TypeScriptResolutionContext,
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
      is NilShape -> NULL
      is AnyShape -> processAnyShape(shape, context)
      else -> genError("Shape type '${shape::class.simpleName}' is unsupported", shape)
    }

  private fun processAnyShape(shape: AnyShape, context: TypeScriptResolutionContext): TypeName =
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

  private fun processScalarShape(shape: ScalarShape, context: TypeScriptResolutionContext): TypeName =
    when (shape.dataType) {
      DataTypes.String() ->

        if (shape.values.isNotEmpty()) {
          defineEnum(shape, context)
        } else {
          when (shape.format) {
            "time" -> LOCAL_TIME
            "datetime-only", "date-time-only" -> LOCAL_DATETIME
            else -> STRING
          }
        }

      DataTypes.Boolean() -> BOOLEAN

      DataTypes.Integer() ->
        when (shape.format) {
          "int8" -> NUMBER
          "int16" -> NUMBER
          "int32", "int" -> NUMBER
          "", null -> NUMBER
          else -> genError("Integer format '${shape.format}' is unsupported", shape)
        }

      DataTypes.Long() -> NUMBER

      DataTypes.Float() -> NUMBER
      DataTypes.Double() -> NUMBER
      DataTypes.Number() -> NUMBER

      DataTypes.Decimal() -> NUMBER

//      DataTypes.Duration() -> DURATION
      DataTypes.Date() -> LOCAL_DATE
      DataTypes.Time() -> LOCAL_TIME
      DataTypes.DateTimeOnly() -> LOCAL_DATETIME
      DataTypes.DateTime() -> OFFSET_DATETIME

      DataTypes.Binary() -> ARRAY_BUFFER

      else -> genError("Scalar data type '${shape.dataType}' is unsupported", shape)
    }

  private fun processArrayShape(shape: ArrayShape, context: TypeScriptResolutionContext): TypeName {

    val elementType =
      shape.items
        ?.let { itemsShape ->
          resolveReferencedTypeName(itemsShape, context)
        }
        ?: UNKNOWN

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

    if (
      shape.nonPatternProperties.isEmpty() &&
      context.hasNoInherited(shape) &&
      context.hasNoInheriting(shape)
    ) {

      val patternProperties = shape.patternProperties
      return if (patternProperties.isNotEmpty()) {

        val patternPropertyShapes = collectTypes(patternProperties.map { it.range })

        val valueTypeName = nearestCommonAncestor(patternPropertyShapes, context)

        recordType(STRING, valueTypeName ?: UNKNOWN)
      } else {

        recordType(STRING, UNKNOWN)
      }
    }

    return defineClass(shape, context)
  }

  private fun defineClass(
    shape: NodeShape,
    context: TypeScriptResolutionContext,
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

    val classBuilder =
      defineType(className) { name ->
        ClassSpec.builder(name.simpleName())
          .tag(GeneratedTypeCategory.Model)
          .tag(DefinitionLocation(shape))
          .addModifiers(Modifier.EXPORT)
      } as ClassSpec.Builder

    val ifaceName = className.sibling("Spec")
    val ifaceBuilder =
      InterfaceSpec.builder(ifaceName.simpleName())
        .tag(DefinitionLocation(shape))
        .addModifiers(Modifier.EXPORT)

    classBuilder.addMixin(ifaceName)

    val superShape = context.findSuperShapeOrNull(shape) as NodeShape?
    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context) as TypeName.Standard
      classBuilder.superClass(superClassName)

      ifaceBuilder.addSuperInterface(superClassName.sibling("Spec"))
    }

    var inheritedProperties = superShape?.let(context::findAllProperties) ?: emptyList()
    var declaredProperties = context.findProperties(shape)
      .filter { prop -> prop.name !in inheritedProperties.map { it.name } }

    val inheritingTypes = context.findInheritingShapes(shape).map { it as NodeShape }

    if (inheritedProperties.isNotEmpty() || declaredProperties.isNotEmpty()) {

      /*
        Computed discriminator values (for jackson generation)
       */

      if (options.contains(JacksonDecorators)) {

        val discriminatorPropertyName = findDiscriminatorPropertyName(shape, context)
        if (discriminatorPropertyName != null) {

          val discriminatorProperty =
            (inheritedProperties + declaredProperties).find { it.name == discriminatorPropertyName }
              ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

          val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)

          declaredProperties = declaredProperties.filter { it.name != discriminatorPropertyName }
          inheritedProperties = inheritedProperties.filter { it.name != discriminatorPropertyName }

          // Add concrete discriminator for leaf of the discriminated tree

          if (context.hasNoInheriting(shape)) {

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
                  discriminatorValue.toUpperCamelCase(),
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
            .builder(declaredProperty.typeScriptIdentifierName, propertyTypeName.nonUndefinable)
            .optional(propertyTypeName.isUndefinable)
            .build(),
        )
      }

      /*
        Generate class implementation
       */

      // toString builder
      //
      val toStringCode = mutableListOf<CodeBlock>()
      inheritedProperties.forEach {
        toStringCode.add(
          CodeBlock.of(
            "%L='${'$'}{this.%L}'",
            it.typeScriptIdentifierName,
            it.typeScriptIdentifierName,
          ),
        )
      }

      // Generate related code for each property
      //
      declaredProperties.forEach { declaredProperty ->

        val declaredPropertyTypeName = resolvePropertyTypeName(declaredProperty, className, context)

        val implAnn = declaredProperty.range.findAnnotation(TypeScriptImpl, null) as? ObjectNode
        if (implAnn != null) {

          val declaredPropertyGetterBuilder =
            FunctionSpec.builder(declaredProperty.typeScriptIdentifierName)
              .addModifiers(Modifier.GET)
              .returns(declaredPropertyTypeName)
              .addDecorator(
                DecoratorSpec.builder(JSON_IGNORE)
                  .asFactory()
                  .build(),
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

            val externalDiscriminatorPropertyShape =
              (inheritedProperties + declaredProperties).find { it.name == externalDiscriminatorPropertyName }
                ?: genError("External discriminator '$externalDiscriminatorPropertyName' not found in object", shape)

            if (options.contains(JacksonDecorators)) {
              addJacksonPolymorphismOverride(
                className,
                declaredProperty,
                declaredPropertyBuilder,
                externalDiscriminatorPropertyShape,
                context,
              )
            }
          }

          if (options.contains(JacksonDecorators)) {
            declaredPropertyBuilder
              .addDecorator(
                DecoratorSpec.builder(JSON_PROPERTY)
                  .addJsonPropertyInit(
                    declaredProperty.name!!,
                    declaredProperty.typeScriptIdentifierName,
                    declaredProperty.required,
                  )
                  .build(),
              )
              .addDecorator(
                DecoratorSpec.builder(JSON_CLASS_TYPE)
                  .addParameter(
                    null,
                    CodeBlock.builder()
                      .add("{type: () => ")
                      .add(reflectionTypeName(declaredPropertyTypeName).typeInitializer())
                      .add("}")
                      .build(),
                  )
                  .build(),
              )
          }

          // Add toString value
          //
          toStringCode.add(
            CodeBlock.of(
              "%L='\${this.%L}'",
              declaredProperty.typeScriptIdentifierName,
              declaredProperty.typeScriptIdentifierName,
            ),
          )

          classBuilder.addProperty(declaredPropertyBuilder.build())
        }
      }

      // Add copy method
      //
      if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {
        classBuilder.addFunction(
          FunctionSpec.builder("copy")
            .returns(className)
            .addParameter("changes", PARTIAL.parameterized(ifaceName))
            .addStatement("return new %T(Object.assign({}, this, changes))", className)
            .build(),
        )
      }

      // Build constructor
      //
      if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {

        if (options.contains(JacksonDecorators)) {
          classBuilder.addDecorator(
            DecoratorSpec.builder(JSON_CREATOR)
              .addParameter(null, "{ mode: %Q.PROPERTIES_OBJECT }", JSON_CREATOR_MODE)
              .build(),
          )
        }

        val classSpec = classBuilder.build()

        val consBuilder =
          FunctionSpec.constructorBuilder()
            .addParameter(
              ParameterSpec.builder("init", ifaceName)
                .build(),
            )

        if (superShape != null) {
          val init = if (inheritedProperties.isNotEmpty()) "init" else ""
          consBuilder.addStatement("super($init)")
        }

        for (propertySpec in classSpec.propertySpecs) {
          consBuilder.addStatement("this.%L = init.%L", propertySpec.name, propertySpec.name)
        }

        classBuilder.constructor(consBuilder.build())
      }

      // Finish toString method
      val toStringTemplate =
        CodeBlock.of(
          "%N(%L)",
          className,
          toStringCode.joinToString(", "),
        ).toString()

      classBuilder.addFunction(
        FunctionSpec.builder("toString")
          .returns(STRING)
          .addStatement("return %P", toStringTemplate)
          .build(),
      )
    }

    if (shape.findBoolAnnotation(Patchable, null) == true) {

      if (options.contains(JacksonDecorators)) {
        classBuilder
          .addDecorator(
            DecoratorSpec.builder(JSON_INCLUDE)
              .addParameter(null, "{value: %Q.ALWAYS}", JSON_INCLUDE_TYPE)
              .build(),
          )
      }
    }

    exportedTypeBuilders[className] = classBuilder

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    if (options.contains(JacksonDecorators)) {

      if (context.hasNoInherited(shape) && !shape.discriminator.isNullOrBlank()) {
        addJacksonPolymorphism(shape, inheritingTypes, className, classBuilder, context)
      }
    }

    classBuilder.tag(SpecificationInterface(ifaceBuilder))

    return className
  }

  private fun defineEnum(shape: Shape, context: TypeScriptResolutionContext): TypeName {

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

    val enumBuilder = defineType(className) { name ->
      EnumSpec.builder(name.simpleName())
        .addModifiers(Modifier.EXPORT)
    } as EnumSpec.Builder

    enumBuilder.tag(DefinitionLocation(shape))

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->
      enumBuilder.addConstant(enum.typeScriptEnumName, CodeBlock.of("%S", enum.stringValue))
    }

    exportedTypeBuilders[className] = enumBuilder

    return className
  }

  private fun defineType(
    className: TypeName.Standard,
    builderBlock: (TypeName.Standard) -> AnyTypeSpecBuilder,
  ): AnyTypeSpecBuilder {

    val builder = builderBlock(className)

    builder.tag(GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, builder) != null) {
      genError("Multiple types with name '$className' defined")
    }

    return builder
  }

  private fun addJacksonPolymorphismOverride(
    className: TypeName.Standard,
    valuePropertyShape: PropertyShape,
    valuePropertySpec: PropertySpec.Builder,
    externalDiscriminatorPropertyShape: PropertyShape,
    context: TypeScriptResolutionContext,
  ) {
    val valuePropertyTypeShape = valuePropertyShape.range as? NodeShape ?: return

    val externalDiscriminatorPropertyName = externalDiscriminatorPropertyShape.name?.typeScriptIdentifierName ?: return
    val externalDiscriminatorPropertyTypeName =
      resolvePropertyTypeName(externalDiscriminatorPropertyShape, className, context)
    val isDiscriminatorEnum = typeBuilders[externalDiscriminatorPropertyTypeName.nonOptional] is EnumSpec.Builder

    val inheritingTypes = context.findInheritingShapes(valuePropertyTypeShape).map { it as NodeShape }
    val discriminatorMappings = buildDiscriminatorMappings(valuePropertyTypeShape, context)

    val subTypes = inheritingTypes
      .map { inheritingType ->

        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingType.id }?.key
        val discriminatorValue = inheritingType.discriminatorValue ?: mappedDiscriminator ?: inheritingType.name

        val inheritingTypeName = resolveReferencedTypeName(inheritingType, context) as TypeName.Standard

        if (isDiscriminatorEnum) {
          val enumDiscriminatorValue =
            (typeBuilders[externalDiscriminatorPropertyTypeName.nonOptional] as EnumSpec.Builder)
              .constants.entries
              .first { it.value?.toString() == "'$discriminatorValue'" }.key

          // NOTE: using nested enum types fails to initialize correctly
          // currently. Using the string constant equivalent until the
          // issue is resolved.
          "{class: () => %T, name: %S /* %L.%L */}" to listOf(
            importFromIndex(inheritingTypeName, className),
            discriminatorValue,
            (externalDiscriminatorPropertyTypeName as TypeName.Standard).base.value,
            enumDiscriminatorValue,
          )
        } else {
          "{class: () => %T, name: %S}" to listOf(
            importFromIndex(inheritingTypeName, className),
            discriminatorValue,
          )
        }
      }

    valuePropertySpec.addDecorator(
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
          JSON_TYPE_INFO_ID,
          "NAME",
          JSON_TYPE_INFO_AS,
          "EXTERNAL_PROPERTY",
          externalDiscriminatorPropertyName,
        )
        .build(),
    )

    valuePropertySpec.addDecorator(
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
          *subTypes.map { it.second }.flatten().toTypedArray(),
        )
        .build(),
    )
  }

  private fun addJacksonPolymorphism(
    shape: NodeShape,
    inheritingTypes: List<NodeShape>,
    className: TypeName.Standard,
    classBuilder: ClassSpec.Builder,
    context: TypeScriptResolutionContext,
  ) {

    val discriminatorPropertyName = findDiscriminatorPropertyName(shape, context)
    val discriminatorPropertyShape = context.findAllProperties(shape).first { it.name == discriminatorPropertyName }
    val discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorPropertyShape, className, context)
    val isDiscriminatorEnum = typeBuilders[discriminatorPropertyTypeName.nonOptional] is EnumSpec.Builder

    val discriminatorMappings = buildDiscriminatorMappings(shape, context)

    val subTypes = inheritingTypes
      .map { inheritingType ->

        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingType.id }?.key
        val discriminatorValue =
          inheritingType.discriminatorValue ?: mappedDiscriminator ?: inheritingType.name

        val inheritingTypeName = resolveReferencedTypeName(inheritingType, context) as TypeName.Standard

        if (isDiscriminatorEnum) {
          val enumDiscriminatorValue =
            (typeBuilders[discriminatorPropertyTypeName.nonOptional] as EnumSpec.Builder)
              .constants.entries
              .first { it.value?.toString() == "'$discriminatorValue'" }.key

          // NOTE: using nested enum types fails to initialize correctly
          // currently. Using the string constant equivalent until the
          // issue is resolved.
          "{class: () => %T, name: %S /* %L.%L */}" to listOf(
            importFromIndex(inheritingTypeName, className),
            discriminatorValue,
            (discriminatorPropertyTypeName as TypeName.Standard).base.value,
            enumDiscriminatorValue,
          )
        } else {
          "{class: () => %T, name: %S}" to listOf(
            importFromIndex(inheritingTypeName, className),
            discriminatorValue,
          )
        }
      }

    if (subTypes.isNotEmpty()) {

      if (shape.findBoolAnnotation(ExternallyDiscriminated, null) != true) {

        val discriminator = shape.discriminator ?: genError("Missing required discriminator", shape)

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
              JSON_TYPE_INFO_ID,
              "NAME",
              JSON_TYPE_INFO_AS,
              "PROPERTY",
              discriminator,
            )
            .build(),
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
            *subTypes.map { it.second }.flatten().toTypedArray(),
          )
          .build(),
      )
    }
  }

  private fun typeNameOf(shape: Shape, context: TypeScriptResolutionContext): TypeName.Standard {

    if (!shape.isNameExplicit && context.suggestedTypeName != null) {
      return context.suggestedTypeName
    }

    modulePathOf(shape, context)?.let { modulePath ->
      val fullModulePath =
        if (modulePath.endsWith(".ts"))
          modulePath.removeSuffix(".ts")
        else
          "$modulePath/${shape.typeScriptTypeName.camelCaseToKebabCase()}"
      return TypeName.namedImport(shape.typeScriptTypeName, "!$fullModulePath")
    }

    val nestedAnn = shape.findAnnotation(Nested, null)
      ?: return TypeName.namedImport(
        shape.typeScriptTypeName,
        "!${shape.typeScriptTypeName.camelCaseToKebabCase()}",
      )

    val (nestedEnclosedIn, nestedName) =
      when {
        nestedAnn is ScalarNode && nestedAnn.value == "dashed" -> {

          val spec = shape.name ?: ""
          val parts = spec.split("-")
          if (parts.size < 2) {
            genError(
              "Nested types using 'dashed' scheme must be named with dashes corresponding to nesting hierarchy.",
              shape,
            )
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
          genError(
            "Nested annotation must be the value 'dashed' or an object containing 'enclosedIn' & 'name' keys",
            shape,
          )
      }

    val (nestedEnclosingType, nestedEnclosingTypeUnit) = context.resolveRef(nestedEnclosedIn, shape)
      ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

    nestedEnclosingType as? Shape
      ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

    val nestedEnclosingTypeContext = context.copy(unit = nestedEnclosingTypeUnit, suggestedTypeName = null)

    val nestedEnclosingTypeName =
      resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as TypeName.Standard

    return nestedEnclosingTypeName.nested(nestedName)
  }

  private fun modulePathOf(shape: Shape, context: TypeScriptResolutionContext): String? =
    (shape as? CustomizableElement)?.findStringAnnotation(TypeScriptModelModule, null)
      ?: modulePathOf(context.findDeclaringUnit(shape))

  private fun modulePathOf(unit: BaseUnit?): String? =
    (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModelModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModelModule, null)
      ?: (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModule, null)

  private fun collectTypes(types: List<Shape>) = types.flatMap { if (it is UnionShape) it.flattened else listOf(it) }

  private fun nearestCommonAncestor(types: List<Shape>, context: TypeScriptResolutionContext): TypeName? {

    var currentClassNameHierarchy: List<TypeName>? = null
    for (type in types) {
      val propertyClassNameHierarchy = classNameHierarchy(type, context)
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

  private fun classNameHierarchy(shape: Shape, context: TypeScriptResolutionContext): List<TypeName> {

    val names = mutableListOf<TypeName>()

    var current: Shape? = shape
    while (current != null) {
      names.add(resolveReferencedTypeName(current, context))
      current = context.findSuperShapeOrNull(current)
    }

    return names.reversed()
  }

  private fun findDiscriminatorPropertyName(shape: NodeShape, context: TypeScriptResolutionContext): String? =
    when {
      !shape.discriminator.isNullOrEmpty() -> shape.discriminator
      else -> context.findSuperShapeOrNull(shape)?.let { findDiscriminatorPropertyName(it as NodeShape, context) }
    }

  private fun findDiscriminatorPropertyValue(shape: NodeShape, context: TypeScriptResolutionContext): String? =
    if (!shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = context.findRootShape(shape) as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(shape: NodeShape, context: TypeScriptResolutionContext): Map<String, String> =
    shape.discriminatorMapping.mapNotNull { mapping ->
      val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
      mapping.templateVariable().value()!! to refElement.id
    }.toMap()

  fun reflectionTypeName(typeName: TypeName): TypeName =

    when (val nonOptionalTypeName = typeName.nonOptional) {
      is TypeName.Standard ->
        if (isReflectedAsObject(nonOptionalTypeName)) {
          OBJECT_CLASS
        } else {
          nonOptionalTypeName
        }

      is TypeName.Parameterized ->
        TypeName.parameterizedType(
          reflectionTypeName(nonOptionalTypeName.rawType) as TypeName.Standard,
          *nonOptionalTypeName.typeArgs.map { reflectionTypeName(it) }.toTypedArray(),
        )

      is TypeName.Union ->
        TypeName.unionType(*nonOptionalTypeName.typeChoices.map { reflectionTypeName(it) }.toTypedArray())

      is TypeName.Intersection ->
        TypeName.intersectionType(*nonOptionalTypeName.typeRequirements.map { reflectionTypeName(it) }.toTypedArray())

      is TypeName.Tuple ->
        TypeName.tupleType(*nonOptionalTypeName.memberTypes.map { reflectionTypeName(it) }.toTypedArray())

      is TypeName.Anonymous ->
        TypeName.anonymousType(
          nonOptionalTypeName.members.map {
            TypeName.Anonymous.Member(
              it.name,
              reflectionTypeName(it.type),
              it.optional,
            )
          },
        )

      else ->
        error("Lambda Unsupported TypeName for Rewrite")
    }

  private fun isReflectedAsObject(typeName: TypeName) =
    typeBuilders[typeName.nonOptional] is EnumSpec.Builder || typeName.box() == OBJECT_CLASS

  private fun importFromIndex(typeName: TypeName, fromTypeName: TypeName): TypeName {
    val typeNameImport = ((typeName as? TypeName.Standard)?.base as? SymbolSpec.Imported) ?: return typeName
    val fromTypeNameImport = (fromTypeName as? TypeName.Standard)?.base as? SymbolSpec.Imported
    if (typeNameImport.source == fromTypeNameImport?.source) {
      return typeName
    }
    return TypeName.namedImport(typeNameImport.value, "!index")
  }
}

private fun TypeName.Standard.sibling(name: String): TypeName.Standard =
  when (base) {
    is SymbolSpec.Imported ->
      TypeName.standard(SymbolSpec.importsName(base.value + name, (base as SymbolSpec.Imported).source))

    is SymbolSpec.Implicit ->
      TypeName.standard(SymbolSpec.implicit(base.value + name))
  }

private fun DecoratorSpec.Builder.addJsonPropertyInit(
  declaredName: String,
  codeName: String,
  required: Boolean,
): DecoratorSpec.Builder {
  val differentName = declaredName != codeName
  return when {
    differentName && required ->
      addParameter(null, "{value: %S, required: %L}", declaredName, true)

    !differentName && required ->
      addParameter(null, "{required: %L}", true)

    differentName && !required ->
      addParameter(null, "{value: %S}", declaredName)

    else ->
      addParameter(null, CodeBlock.empty())
  }
}
