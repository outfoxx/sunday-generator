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
import io.outfoxx.sunday.generator.APIAnnotationName.ExternalDiscriminator
import io.outfoxx.sunday.generator.APIAnnotationName.ExternallyDiscriminated
import io.outfoxx.sunday.generator.APIAnnotationName.Nested
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptImpl
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptModelModule
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptModule
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptType
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.TypeRegistry
import io.outfoxx.sunday.generator.common.DefinitionLocation
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.utils.ARRAY_BUFFER_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.BOOLEAN_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.CREATE_PROBLEM_CODEC
import io.outfoxx.sunday.generator.typescript.utils.DATE
import io.outfoxx.sunday.generator.typescript.utils.DATE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.DEFINE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.DURATION
import io.outfoxx.sunday.generator.typescript.utils.DURATION_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.INSTANT
import io.outfoxx.sunday.generator.typescript.utils.INSTANT_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATE
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATETIME_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_TIME
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_TIME_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.NUMBER_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_DATETIME_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_TIME
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_TIME_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.PARTIAL
import io.outfoxx.sunday.generator.typescript.utils.PROBLEM
import io.outfoxx.sunday.generator.typescript.utils.PROBLEM_WIRE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.RECORD
import io.outfoxx.sunday.generator.typescript.utils.SCHEMA_LIKE
import io.outfoxx.sunday.generator.typescript.utils.SCHEMA_RUNTIME
import io.outfoxx.sunday.generator.typescript.utils.STRING_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.UNKNOWN
import io.outfoxx.sunday.generator.typescript.utils.URL_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.URL_TYPE
import io.outfoxx.sunday.generator.typescript.utils.Z
import io.outfoxx.sunday.generator.typescript.utils.ZONED_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.ZONED_DATETIME_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.isNullable
import io.outfoxx.sunday.generator.typescript.utils.isUndefinable
import io.outfoxx.sunday.generator.typescript.utils.nonNullable
import io.outfoxx.sunday.generator.typescript.utils.nonOptional
import io.outfoxx.sunday.generator.typescript.utils.nonUndefinable
import io.outfoxx.sunday.generator.typescript.utils.nullable
import io.outfoxx.sunday.generator.typescript.utils.recordType
import io.outfoxx.sunday.generator.typescript.utils.typeScriptEnumName
import io.outfoxx.sunday.generator.typescript.utils.typeScriptIdentifierName
import io.outfoxx.sunday.generator.typescript.utils.typeScriptTypeName
import io.outfoxx.sunday.generator.typescript.utils.undefinable
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import io.outfoxx.sunday.generator.utils.closed
import io.outfoxx.sunday.generator.utils.dataType
import io.outfoxx.sunday.generator.utils.discriminator
import io.outfoxx.sunday.generator.utils.discriminatorMapping
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.exclusiveMaximum
import io.outfoxx.sunday.generator.utils.exclusiveMinimum
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
import io.outfoxx.sunday.generator.utils.maxProperties
import io.outfoxx.sunday.generator.utils.maximum
import io.outfoxx.sunday.generator.utils.minItems
import io.outfoxx.sunday.generator.utils.minLength
import io.outfoxx.sunday.generator.utils.minProperties
import io.outfoxx.sunday.generator.utils.minimum
import io.outfoxx.sunday.generator.utils.multipleOf
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.optional
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.pattern
import io.outfoxx.sunday.generator.utils.patternProperties
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueId
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.xone
import io.outfoxx.typescriptpoet.AnyTypeSpec
import io.outfoxx.typescriptpoet.AnyTypeSpecBuilder
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
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
import io.outfoxx.typescriptpoet.TypeName.Companion.NULL
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT_CLASS
import io.outfoxx.typescriptpoet.TypeName.Companion.SET
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.UNDEFINED
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import io.outfoxx.typescriptpoet.tag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.min

class TypeScriptTypeRegistry(
  private val options: Set<Option>,
  private val importStyle: ImportStyle = ImportStyle.ESM,
  private val schemaRuntimeArgumentName: String = "runtime",
) : TypeRegistry {

  data class SpecificationInterface(
    val value: InterfaceSpec.Builder,
  )

  enum class ImportStyle(
    val importExtension: String,
  ) {
    ESM(""),
    NodeNext(".js"),
  }

  enum class Option {
    AddGenerationHeader,
  }

  private val exportedTypeBuilders = LinkedHashMap<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeBuilders = mutableMapOf<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeNameMappings = mutableMapOf<String, TypeName>()
  private val typeModuleExtras = mutableMapOf<TypeName.Standard, MutableList<Any>>()
  private val classSuperTypes = mutableMapOf<TypeName.Standard, TypeName.Standard?>()
  private val companionSchemaFileMembers = mutableMapOf<String, MutableList<Pair<TypeName.Standard, CodeBlock>>>()
  private val companionSchemaTypeKeys = mutableSetOf<String>()
  private val companionSchemaModules = mutableMapOf<String, TypeName.Standard>()

  override fun generateFiles(
    categories: Set<GeneratedTypeCategory>,
    outputDirectory: Path,
  ) {

    val fileSpecs = generateExportedTypeFiles(categories)

    fileSpecs
      .forEach { fileSpec -> writeFileSpec(fileSpec, outputDirectory) }

    listOf(generateIndexFile(fileSpecs))
      .forEach { it.writeTo(outputDirectory) }
  }

  private fun writeFileSpec(
    fileSpec: FileSpec,
    outputDirectory: Path,
  ) {
    val outputModulePath = normalizeModulePath(fileSpec.modulePath)
    val outputPath = outputDirectory.resolve("$outputModulePath.ts")
    outputPath.parent?.let { Files.createDirectories(it) }
    Files.newBufferedWriter(outputPath).use { writer ->
      fileSpec.writeTo(writer, outputDirectory)
    }
  }

  fun generateExportedTypeFiles(categories: Set<GeneratedTypeCategory>): List<FileSpec> {

    val generatedFiles =
      buildTypes()
        .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
        .map { (typeName, moduleSpec) ->
          val importModulePath = importModulePath(typeName)
          val outputModulePath = normalizeModulePath(importModulePath)
          Triple(outputModulePath, typeName, moduleSpec)
        }.groupBy { it.first }
        .map { (modulePath, moduleSpecs) ->
          val orderedModuleSpecs =
            moduleSpecs
              .sortedWith(
                compareBy<Triple<String, TypeName.Standard, ModuleSpec>> {
                  typeInheritanceRank(it.second, modulePath)
                }.thenBy { it.second.simpleName() },
              ).map { it.third }

          val importModulePath = renderImportModulePath(modulePath)
          val fileSpecBuilder =
            if (orderedModuleSpecs.size == 1) {
              FileSpec.get(orderedModuleSpecs.first(), importModulePath).toBuilder()
            } else {
              val fileSpecBuilder = FileSpec.builder(importModulePath)
              orderedModuleSpecs.forEach { moduleSpec ->
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
            val fileName = "$modulePath.ts"
            fileSpecBuilder.addComment(GenerationHeaders.create(fileName))
          }

          fileSpecBuilder.build()
        }

    val companionSchemaFiles =
      if (!categories.contains(GeneratedTypeCategory.Model)) {
        emptyList()
      } else {
        companionSchemaFileMembers
          .map { (modulePath, members) ->
            val sortedMembers =
              members.sortedWith(
                compareByDescending<Pair<TypeName.Standard, CodeBlock>> {
                  typeInheritanceRank(it.first, modulePath)
                }.thenBy { it.first.simpleName() },
              )
            val fileSpecBuilder =
              FileSpec.builder(renderImportModulePath(modulePath)).apply {
                sortedMembers.forEach { (_, code) -> addCode(code) }
              }
            if (options.contains(AddGenerationHeader)) {
              val fileName = "$modulePath.ts"
              fileSpecBuilder.addComment(GenerationHeaders.create(fileName))
            }
            fileSpecBuilder.build()
          }
      }

    return generatedFiles + companionSchemaFiles
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
        ModuleSpec
          .builder(typeName.simpleName(), ModuleSpec.Kind.NAMESPACE)
          .addModifier(Modifier.EXPORT)
      }

    fun ModuleSpec.Builder.addExtras(typeName: TypeName.Standard) {
      typeModuleExtras[typeName]?.forEach { member ->
        when (member) {
          is FunctionSpec -> addFunction(member)
          is PropertySpec -> addProperty(member)
          is AnyTypeSpec -> addType(member)
          is ModuleSpec -> addModule(member)
          is CodeBlock -> addCode(member)
        }
      }
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
        enclosingMod.addExtras(typeName)

        // Add nested module (if exists)
        typeModBuilders[typeName]?.let { enclosingMod.addModule(it.build()) }
      }

    val rootModules =
      exportedTypeBuilders
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
          rootModuleSpec.addExtras(typeName)

          val typeModBuilder = getTypeModBuilder(typeName)
          if (typeModBuilder.isNotEmpty()) {
            rootModuleSpec.addModule(typeModBuilder.build())
          }

          rootModuleSpec.build()
        }

    val companionModules =
      companionSchemaFileMembers
        .mapValues { it.value.toList() }
        .mapNotNull { (modulePath, members) ->
          val ownerTypeName =
            companionSchemaModules[modulePath]
              ?: return@mapNotNull null
          val schemaTypeName =
            TypeName.namedImport(
              "${ownerTypeName.simpleName()}Schema",
              "!${renderImportModulePath(modulePath)}",
            )

          val moduleSpecBuilder = ModuleSpec.builder(schemaTypeName.simpleName(), ModuleSpec.Kind.MODULE)
          members
            .sortedWith(
              compareByDescending<Pair<TypeName.Standard, CodeBlock>> {
                typeInheritanceRank(it.first, modulePath)
              }.thenBy { it.first.simpleName() },
            ).forEach { (_, code) -> moduleSpecBuilder.addCode(code) }

          schemaTypeName to moduleSpecBuilder.build()
        }.toMap()

    return rootModules + companionModules
  }

  fun resolveTypeName(
    shapeRef: Shape,
    context: TypeScriptResolutionContext,
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
    exportedTypeBuilders.remove(typeName)
  }

  fun addServiceType(
    typeName: TypeName.Standard,
    serviceType: ClassSpec.Builder,
  ) {

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
    val problemTypeName =
      TypeName.namedImport(
        problemTypeNameStr,
        "!${problemTypeNameStr.camelCaseToKebabCase()}${importStyle.importExtension}",
      )

    exportedTypeBuilders.computeIfAbsent(problemTypeName) {

      val customProperties = mutableListOf<Pair<String, TypeName>>()

      val problemTypeBuilder =
        ClassSpec
          .builder(problemTypeName)
          .tag(GeneratedTypeCategory.Model)
          .addModifiers(Modifier.EXPORT)
          .superClass(PROBLEM)
          .addProperty(
            PropertySpec
              .builder("TYPE", STRING)
              .addModifiers(Modifier.STATIC)
              .initializer("%S", problemTypeDefinition.type)
              .build(),
          )

      val problemTypeConsBuilder =
        FunctionSpec
          .constructorBuilder()

      // Add all custom properties
      problemTypeDefinition.custom.forEach { (customPropertyName, customPropertyTypeNameStr) ->

        val customPropertyTypeName =
          resolveTypeReference(
            customPropertyTypeNameStr,
            problemTypeDefinition.source,
            TypeScriptResolutionContext(problemTypeDefinition.definedIn, shapeIndex, null),
          )

        customProperties.add(customPropertyName.typeScriptIdentifierName to customPropertyTypeName)

        problemTypeBuilder.addProperty(
          PropertySpec
            .builder(customPropertyName.typeScriptIdentifierName, customPropertyTypeName)
            .build(),
        )
      }

      val constructorSpecType =
        TypeName.anonymousType(
          customProperties.map { (customPropertyName, customPropertyType) ->
            TypeName.Anonymous.Member(
              customPropertyName,
              customPropertyType.nonUndefinable,
              customPropertyType.isUndefinable,
            )
          } +
            listOf(
              TypeName.Anonymous.Member(
                "instance",
                TypeName.unionType(STRING, URL_TYPE),
                true,
              ),
            ),
        )

      problemTypeConsBuilder.addParameter(
        ParameterSpec
          .builder("spec", constructorSpecType)
          .apply {
            if (customProperties.isEmpty()) {
              defaultValue("{}")
            }
          }.build(),
      )

      val problemSuperSpec =
        CodeBlock
          .builder()
          .add(
            """
            |super({
            |  type: %T.TYPE,
            |  title: %S,
            |  status: %L,
            |  detail: %S,
            |  instance: spec.instance
            """.trimMargin(),
            problemTypeName,
            problemTypeDefinition.title,
            problemTypeDefinition.status,
            problemTypeDefinition.detail,
          ).apply {
            if (customProperties.isNotEmpty()) {
              add(",\n")
              customProperties.forEachIndexed { idx, (customPropertyName, _) ->
                add("  %L: spec.%L", customPropertyName, customPropertyName)
                if (idx < customProperties.size - 1) {
                  add(",")
                }
                add("\n")
              }
            } else {
              add("\n")
            }
          }.add("});\n\n")
          .build()

      problemTypeConsBuilder.addCode(problemSuperSpec)

      customProperties.forEach { (customPropertyName, _) ->
        problemTypeConsBuilder.addStatement(
          "this.%L = spec.%L",
          customPropertyName,
          customPropertyName,
        )
      }

      problemTypeBuilder.constructor(problemTypeConsBuilder.build())

      addModuleExtra(
        problemTypeName,
        CodeBlock
          .builder()
          .add(
            "export const %L: %T = %Q((runtime: %T) => {\n",
            problemTypeName.sibling("Schema").simpleName(),
            SCHEMA_LIKE.parameterized(problemTypeName),
            DEFINE_SCHEMA,
            SCHEMA_RUNTIME,
          ).add("  const wireSchema = %T.extend({", PROBLEM_WIRE_SCHEMA)
          .apply {
            if (customProperties.isNotEmpty()) {
              add("\n")
              customProperties.forEachIndexed { idx, (customPropertyName, customPropertyType) ->
                add("    %S: ", customPropertyName)
                add(runtimeSchemaForType(customPropertyType, schemaRuntimeArgumentName))
                if (idx < customProperties.size - 1) {
                  add(",")
                }
                add("\n")
              }
              add("  ")
            }
          }.add("});\n")
          .add(
            "  return %Q(%T, wireSchema);\n",
            CREATE_PROBLEM_CODEC,
            problemTypeName,
          ).add("});\n")
          .build(),
      )

      problemTypeBuilder
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
        "time-only" -> LOCAL_TIME
        "date-only" -> LOCAL_DATE
        "datetime-only" -> LOCAL_DATETIME
        "datetime" -> OFFSET_DATETIME
        "instant" -> INSTANT
        "zoned-date-time" -> ZONED_DATETIME
        "offset-time" -> OFFSET_TIME
        "date" -> DATE
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

  private fun resolveReferencedTypeName(
    shape: Shape,
    context: TypeScriptResolutionContext,
  ): TypeName = resolveTypeName(shape, context.copy(suggestedTypeName = null))

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

  private fun generateTypeName(
    shape: Shape,
    context: TypeScriptResolutionContext,
  ): TypeName {

    val typeScriptTypeAnn = shape.findStringAnnotation(TypeScriptType, null)
    if (typeScriptTypeAnn != null) {
      return resolveTypeScriptTypeName(typeScriptTypeAnn)
    }

    return processShape(shape, context)
  }

  private fun resolveTypeScriptTypeName(typeScriptTypeName: String): TypeName =
    when (typeScriptTypeName) {
      DURATION.simpleName() -> DURATION
      else -> TypeName.standard(typeScriptTypeName)
    }

  private fun processShape(
    shape: Shape,
    context: TypeScriptResolutionContext,
  ): TypeName =
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

  private fun processAnyShape(
    shape: AnyShape,
    context: TypeScriptResolutionContext,
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
    context: TypeScriptResolutionContext,
  ): TypeName =
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

  private fun processArrayShape(
    shape: ArrayShape,
    context: TypeScriptResolutionContext,
  ): TypeName {

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

  private fun processUnionShape(
    shape: UnionShape,
    context: TypeScriptResolutionContext,
  ): TypeName =
    if (shape.makesNullable) {
      resolveReferencedTypeName(shape.nullableType, context).nullable
    } else {
      nearestCommonAncestor(shape.anyOf, context)
        ?: TypeName.unionType(*shape.anyOf.map { resolveReferencedTypeName(it, context) }.toTypedArray())
    }

  private fun processNodeShape(
    shape: NodeShape,
    context: TypeScriptResolutionContext,
  ): TypeName {

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
        ClassSpec
          .builder(name.simpleName())
          .tag(GeneratedTypeCategory.Model)
          .tag(DefinitionLocation(shape))
          .addModifiers(Modifier.EXPORT)
      } as ClassSpec.Builder

    val ifaceName = className.sibling("Spec")
    val ifaceBuilder =
      InterfaceSpec
        .builder(ifaceName.simpleName())
        .tag(DefinitionLocation(shape))
        .addModifiers(Modifier.EXPORT)

    classBuilder.addMixin(ifaceName)

    val superShape = context.findSuperShapeOrNull(shape) as NodeShape?
    if (superShape != null) {
      val superClassName = resolveReferencedTypeName(superShape, context) as TypeName.Standard
      classSuperTypes[className] = superClassName
      classBuilder.superClass(superClassName)

      ifaceBuilder.addSuperInterface(superClassName.sibling("Spec"))
    } else {
      classSuperTypes[className] = null
    }

    var inheritedProperties = superShape?.let(context::findAllProperties) ?: emptyList()
    var declaredProperties =
      context
        .findProperties(shape)
        .filter { prop -> prop.name !in inheritedProperties.map { it.name } }

    val inheritingTypes = context.findInheritingShapes(shape).map { it as NodeShape }
    val rootShape = context.findRootShape(shape) as? NodeShape
    val isExternallyDiscriminated = rootShape?.findBoolAnnotation(ExternallyDiscriminated, null) == true
    val isDiscriminatorBase =
      context.hasNoInherited(shape) &&
        inheritingTypes.isNotEmpty() &&
        (!shape.discriminator.isNullOrBlank() || isExternallyDiscriminated)
    val shouldEmitCompanionSchema = className.isTopLevelTypeName && isInDiscriminatedHierarchy(shape, context)
    if (shouldEmitCompanionSchema) {
      registerCompanionSchemaType(className)
    }
    var discriminatorPropertyTypeName: TypeName? = null
    var leafDiscriminatorPropertyName: String? = null
    var leafDiscriminatorValue: String? = null

    if (inheritedProperties.isNotEmpty() || declaredProperties.isNotEmpty()) {

      val discriminatorPropertyName = findDiscriminatorPropertyName(shape, context)
      if (discriminatorPropertyName != null) {

        val discriminatorProperty =
          (inheritedProperties + declaredProperties).find { it.name == discriminatorPropertyName }
            ?: genError("Discriminator property '$discriminatorPropertyName' not found", shape)

        discriminatorPropertyTypeName = resolvePropertyTypeName(discriminatorProperty, className, context)

        declaredProperties = declaredProperties.filter { it.name != discriminatorPropertyName }
        inheritedProperties = inheritedProperties.filter { it.name != discriminatorPropertyName }

        // Add concrete discriminator for the leaf of the discriminated tree

        if (context.hasNoInheriting(shape)) {

          val discriminatorValue = findDiscriminatorPropertyValue(shape, context) ?: shape.name!!

          if (!isExternallyDiscriminated) {
            leafDiscriminatorPropertyName = discriminatorProperty.name ?: discriminatorProperty.typeScriptIdentifierName
            leafDiscriminatorValue = discriminatorValue
          }

          val discriminatorBuilder =
            FunctionSpec
              .builder(discriminatorProperty.typeScriptIdentifierName)
              .addModifiers(Modifier.GET)
              .returns(discriminatorPropertyTypeName)

          val isEnum = typeBuilders[discriminatorPropertyTypeName] is EnumSpec.Builder
          if (isEnum) {
            discriminatorBuilder
              .addStatement(
                "return %T.%L",
                discriminatorPropertyTypeName,
                discriminatorValue.toUpperCamelCase(),
              ).build()
          } else {
            discriminatorBuilder
              .addStatement("return %S", discriminatorValue)
              .build()
          }

          classBuilder.addFunction(discriminatorBuilder.build())
        } else {
          classBuilder.addModifiers(Modifier.ABSTRACT)
          classBuilder.addProperty(
            PropertySpec
              .builder(discriminatorProperty.typeScriptIdentifierName, discriminatorPropertyTypeName)
              .addModifiers(Modifier.ABSTRACT, Modifier.READONLY)
              .build(),
          )
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
            $$"%L='${this.%L}'",
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
            FunctionSpec
              .builder(declaredProperty.typeScriptIdentifierName)
              .addModifiers(Modifier.GET)
              .returns(declaredPropertyTypeName)

          val code = implAnn.getValue("code") ?: ""
          val codeParams = implAnn.get<ArrayNode>("parameters")?.members()?.map { it as ObjectNode } ?: emptyList()
          val convertedCodeParams =
            codeParams.map { codeParam ->
              val atype = codeParam.getValue("type")
              val avalue = codeParam.getValue("value")
              if (atype != null && avalue != null) {
                when (atype) {
                  "Type" -> TypeName.standard(avalue)
                  else -> avalue
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
          }

          // Add toString value
          //
          toStringCode.add(
            CodeBlock.of(
              $$"%L='${this.%L}'",
              declaredProperty.typeScriptIdentifierName,
              declaredProperty.typeScriptIdentifierName,
            ),
          )

          classBuilder.addProperty(declaredPropertyBuilder.build())
        }
      }

      // Add a copy method
      //
      if ((inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) && !isDiscriminatorBase) {
        classBuilder.addFunction(
          FunctionSpec
            .builder("copy")
            .returns(className)
            .addParameter("changes", PARTIAL.parameterized(ifaceName))
            .addStatement("return new %T(Object.assign({}, this, changes))", className)
            .build(),
        )
      }

      // Build constructor
      //
      if (inheritedProperties.isNotEmpty() || definedProperties.isNotEmpty()) {

        val classSpec = classBuilder.build()

        val consBuilder =
          FunctionSpec
            .constructorBuilder()
            .addParameter(
              ParameterSpec
                .builder("init", ifaceName)
                .build(),
            )

        if (superShape != null) {
          val init = if (inheritedProperties.isNotEmpty()) "init" else ""
          consBuilder.addStatement("super($init)")
        }

        for (propertySpec in classSpec.propertySpecs) {
          if (propertySpec.modifiers.contains(Modifier.ABSTRACT)) {
            continue
          }
          consBuilder.addStatement("this.%L = init.%L", propertySpec.name, propertySpec.name)
        }

        classBuilder.constructor(consBuilder.build())
      }

      // Finish toString method
      val toStringTemplate =
        CodeBlock
          .of(
            "%N(%L)",
            className,
            toStringCode.joinToString(", "),
          ).toString()

      classBuilder.addFunction(
        FunctionSpec
          .builder("toString")
          .returns(STRING)
          .addStatement("return %P", toStringTemplate)
          .build(),
      )
    }

    val allProperties = inheritedProperties + declaredProperties
    val serializableProperties =
      allProperties.filter { prop -> prop.range.findAnnotation(TypeScriptImpl, null) == null }
    val externalDiscriminatedProps = mutableListOf<Pair<PropertyShape, PropertyShape>>()

    serializableProperties.forEach { prop ->
      val externalName = prop.range.findStringAnnotation(ExternalDiscriminator, null)
      if (externalName != null) {
        val externalProperty =
          allProperties.find { it.name == externalName }
            ?: genError("External discriminator '$externalName' not found in object", shape)
        externalDiscriminatedProps.add(prop to externalProperty)
      }
    }

    val schemaCode =
      CodeBlock
        .builder()
        .add(
          "export const %L = %Q((runtime: %T) => {\n",
          className.sibling("Schema").simpleName(),
          DEFINE_SCHEMA,
          SCHEMA_RUNTIME,
        )

    if (isDiscriminatorBase && !isExternallyDiscriminated) {
      val discriminatorName = requireNotNull(findDiscriminatorPropertyName(shape, context))
      val variants =
        inheritingTypes.map { inheritingType ->
          val inheritingTypeName = resolveReferencedTypeName(inheritingType, context) as TypeName.Standard
          val mappedDiscriminator =
            buildDiscriminatorMappings(shape, context)
              .entries
              .find {
                it.value ==
                  inheritingType.id
              }?.key
          val discriminatorValue = inheritingType.discriminatorValue ?: mappedDiscriminator ?: inheritingType.name!!
          val inheritingSchemaTypeName =
            if (inheritingTypeName.isTopLevelTypeName && isInDiscriminatedHierarchy(inheritingType, context)) {
              registerCompanionSchemaType(inheritingTypeName)
              schemaTargetForGeneratedType(inheritingTypeName, null)
            } else {
              inheritingTypeName.sibling("Schema")
            }
          Triple(inheritingTypeName, inheritingSchemaTypeName, discriminatorValue)
        }
      val useDiscriminatedUnion =
        canUseDiscriminatedUnion(discriminatorName, variants.map { it.third })

      if (useDiscriminatedUnion) {
        schemaCode
          .add("  const wireSchema = %T.discriminatedUnion(%S, [\n", Z, discriminatorName)
          .add(
            variants
              .map { (_, inheritingSchemaTypeName, _) ->
                CodeBlock.of("runtime.resolveSchema(%T)", inheritingSchemaTypeName)
              }.joinToCode(",\n"),
          ).add("\n  ]);\n")
      } else {
        schemaCode
          .add("  const wireSchema = %T.union([\n", Z)
          .add(
            variants
              .map { (_, inheritingSchemaTypeName, _) ->
                CodeBlock.of("runtime.resolveSchema(%T)", inheritingSchemaTypeName)
              }.joinToCode(",\n"),
          ).add("\n  ]);\n")
      }

      schemaCode
        .add("  return %T.codec(wireSchema, %T.instanceof(%T), {\n", Z, Z, className)
        .add("    decode: (value) => value as %T,\n", className)
        .add("    encode: (value) => value as z.infer<typeof wireSchema>,\n")
        .add("  });\n")
    } else {
      val wireProperties =
        buildList {
          if (leafDiscriminatorPropertyName != null) {
            val discriminatorStandardType =
              discriminatorPropertyTypeName?.nonOptional?.nonNullable as? TypeName.Standard
            add(
              leafDiscriminatorPropertyName to
                discriminatorLiteralSchema(discriminatorStandardType, requireNotNull(leafDiscriminatorValue)),
            )
          }
          addAll(
            serializableProperties
              .mapNotNull { prop ->
                val propertyTypeName = resolvePropertyTypeName(prop, className, context)
                if (propertyTypeName == VOID) {
                  return@mapNotNull null
                }

                val propertySchema =
                  if (externalDiscriminatedProps.any { it.first == prop }) {
                    externalDiscriminatedPropertySchema(propertyTypeName)
                  } else {
                    runtimeSchemaForShape(prop.range, propertyTypeName, schemaRuntimeArgumentName, className)
                  }

                (prop.name ?: prop.typeScriptIdentifierName) to propertySchema
              },
          )
        }

      val shapeFactory =
        if (shape.closed != true || shape.patternProperties.isNotEmpty()) {
          "looseObject"
        } else {
          "object"
        }

      schemaCode.add("  const wireSchema = %T.%L({", Z, shapeFactory)
      if (wireProperties.isNotEmpty()) {
        schemaCode.add("\n")
        wireProperties.forEachIndexed { idx, (jsonName, propertySchema) ->
          schemaCode.add("    %S: ", jsonName).add(propertySchema)
          if (idx < wireProperties.size - 1) {
            schemaCode.add(",")
          }
          schemaCode.add("\n")
        }
        schemaCode.add("  ")
      }
      schemaCode.add("});\n")

      if (shape.minProperties != null) {
        schemaCode.add(
          "  const minPropsSchema = wireSchema.refine((value) => Object.keys(value).length >= %L, { message: %S });\n",
          shape.minProperties!!,
          "Must have at least ${shape.minProperties} properties",
        )
      }
      if (shape.maxProperties != null) {
        val source = if (shape.minProperties != null) "minPropsSchema" else "wireSchema"
        schemaCode.add(
          "  const constrainedWireSchema = %L.refine((value) => Object.keys(value).length <= %L, { message: %S });\n",
          source,
          shape.maxProperties!!,
          "Must have at most ${shape.maxProperties} properties",
        )
      }

      val (baseWireSchemaName, finalWireSchemaName) =
        when {
          shape.maxProperties != null -> "wireSchema" to "constrainedWireSchema"
          shape.minProperties != null -> "wireSchema" to "minPropsSchema"
          else -> "wireSchema" to "wireSchema"
        }
      val resolvedWireSchemaName =
        applyExternalDiscriminatorConstraints(
          externalDiscriminatedProps,
          baseWireSchemaName,
          finalWireSchemaName,
          className,
          context,
          schemaCode,
        )

      if (isDiscriminatorBase) {
        schemaCode
          .add("  return %T.codec(%L, %T.instanceof(%T), {\n", Z, resolvedWireSchemaName, Z, className)
          .add(
            "    decode: () => { throw new TypeError(%P); },\n",
            "${className.simpleName()} requires external discriminator",
          ).add(
            "    encode: (value) => value as unknown as Record<string, unknown>,\n",
          ).add("  });")
      } else {

        schemaCode
          .add("  return %T.codec(%L, %T.instanceof(%T), {\n", Z, resolvedWireSchemaName, Z, className)

        val decodeProperties =
          serializableProperties.mapNotNull { prop ->
            val propertyTypeName = resolvePropertyTypeName(prop, className, context)
            if (propertyTypeName == VOID) {
              return@mapNotNull null
            }
            val requiredPropertyTypeName = propertyTypeName.nonOptional.nonNullable
            val jsonName = prop.name ?: prop.typeScriptIdentifierName
            val codeName = prop.typeScriptIdentifierName
            if (requiredPropertyTypeName is TypeName.Parameterized && requiredPropertyTypeName.rawType == SET) {
              val jsonValue = CodeBlock.of("value[%S]", jsonName)
              CodeBlock.of("      %L: %L,", codeName, setDecodeExpression(jsonValue, propertyTypeName))
            } else {
              CodeBlock.of("      %L: value[%S],", codeName, jsonName)
            }
          }

        if (decodeProperties.isEmpty()) {
          schemaCode.add("    decode: () => new %T(),\n", className)
        } else {
          schemaCode.add("    decode: (value) => new %T({\n", className)
          decodeProperties.forEachIndexed { index, property ->
            schemaCode.add(property)
            if (index < decodeProperties.lastIndex) {
              schemaCode.add("\n")
            }
          }
          schemaCode.add("\n    }),\n")
        }

        schemaCode
          .add("    encode: (value) => ({\n")

        val encodeProperties = mutableListOf<CodeBlock>()
        if (leafDiscriminatorPropertyName != null) {
          val discriminatorValue = requireNotNull(leafDiscriminatorValue)
          val discriminatorStandardType =
            discriminatorPropertyTypeName?.nonOptional?.nonNullable as? TypeName.Standard
          val discriminatorLiteral = discriminatorLiteralValue(discriminatorStandardType, discriminatorValue)
          encodeProperties += CodeBlock.of("      %S: %L,", leafDiscriminatorPropertyName, discriminatorLiteral)
        }
        encodeProperties +=
          serializableProperties.mapNotNull { prop ->
            val propertyTypeName = resolvePropertyTypeName(prop, className, context)
            if (propertyTypeName == VOID) {
              return@mapNotNull null
            }
            val requiredPropertyTypeName = propertyTypeName.nonOptional.nonNullable
            val jsonName = prop.name ?: prop.typeScriptIdentifierName
            val codeName = prop.typeScriptIdentifierName
            if (requiredPropertyTypeName is TypeName.Parameterized && requiredPropertyTypeName.rawType == SET) {
              val codeValue = CodeBlock.of("value.%L", codeName)
              CodeBlock.of("      %S: %L,", jsonName, setEncodeExpression(codeValue, propertyTypeName))
            } else {
              CodeBlock.of("      %S: value.%L,", jsonName, codeName)
            }
          }
        encodeProperties.forEachIndexed { index, property ->
          schemaCode.add(property)
          if (index < encodeProperties.lastIndex) {
            schemaCode.add("\n")
          }
        }

        schemaCode
          .add("\n    }) as z.infer<typeof %L>,\n  });", resolvedWireSchemaName)
      }
    }

    schemaCode.add("\n});\n")

    if (shouldEmitCompanionSchema) {
      addCompanionSchemaFileExtra(className, schemaCode.build())
    } else {
      addModuleExtra(className, schemaCode.build())
    }

    exportedTypeBuilders[className] = classBuilder

    inheritingTypes.forEach { inheritingType ->
      resolveReferencedTypeName(inheritingType, context)
    }

    classBuilder.tag(SpecificationInterface(ifaceBuilder))

    return className
  }

  private fun defineEnum(
    shape: Shape,
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

    val enumBuilder =
      defineType(className) { name ->
        EnumSpec
          .builder(name.simpleName())
          .addModifiers(Modifier.EXPORT)
      } as EnumSpec.Builder

    enumBuilder.tag(DefinitionLocation(shape))

    shape.values.filterIsInstance<ScalarNode>().forEach { enum ->
      enumBuilder.addConstant(enum.typeScriptEnumName, CodeBlock.of("%S", enum.stringValue))
    }

    exportedTypeBuilders[className] = enumBuilder

    addModuleExtra(
      className,
      CodeBlock
        .builder()
        .add(
          "export const %L = ",
          className.sibling("Schema").simpleName(),
        ).add("%T.enum(%T)", Z, className)
        .add(";\n")
        .build(),
    )

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

  private fun addModuleExtra(
    typeName: TypeName.Standard,
    member: Any,
  ) {
    typeModuleExtras.computeIfAbsent(typeName) { mutableListOf() }.add(member)
  }

  private fun registerCompanionSchemaType(typeName: TypeName.Standard) {
    companionSchemaTypeKeys.add(typeKey(typeName))
    companionSchemaModules[companionSchemaModulePath(typeName)] = typeName
  }

  private fun hasCompanionSchemaType(typeName: TypeName.Standard): Boolean =
    companionSchemaTypeKeys.contains(typeKey(typeName))

  private fun addCompanionSchemaFileExtra(
    typeName: TypeName.Standard,
    member: CodeBlock,
  ) {
    val modulePath = companionSchemaModulePath(typeName)
    companionSchemaFileMembers.computeIfAbsent(modulePath) { mutableListOf() }.add(typeName to member)
  }

  private fun companionSchemaModulePath(typeName: TypeName.Standard): String = "${modulePath(typeName)}-schema"

  private fun sourceToImportModulePath(source: String): String = source.removePrefix("!")

  private fun renderImportModulePath(modulePath: String): String = "$modulePath${importStyle.importExtension}"

  private fun normalizeModulePath(importModulePath: String): String =
    importModulePath.removeSuffix(importStyle.importExtension)

  internal fun generatedTypeName(
    simpleName: String,
    modulePath: String,
  ): TypeName.Standard =
    TypeName.namedImport(
      simpleName,
      "!${renderImportModulePath(normalizeModulePath(modulePath))}",
    )

  private fun importModulePath(typeName: TypeName.Standard): String =
    sourceToImportModulePath(
      importedType(typeName).source,
    )

  private fun modulePath(typeName: TypeName.Standard): String = normalizeModulePath(importModulePath(typeName))

  private fun importedType(typeName: TypeName.Standard): SymbolSpec.Imported =
    when (val base = typeName.base) {
      is SymbolSpec.Imported -> base
      is SymbolSpec.Implicit ->
        typeNameMappings.values
          .filterIsInstance<TypeName.Standard>()
          .firstOrNull {
            it.base is SymbolSpec.Imported &&
              (it.base as SymbolSpec.Imported).value == base.value
          }?.base as? SymbolSpec.Imported
          ?: genError("Unable to resolve imported type for '$typeName'")
    }

  private fun typeKey(typeName: TypeName.Standard): String =
    "${modulePath(typeName)}::${typeName.simpleNames().joinToString(".")}"

  private fun typeInheritanceRank(
    typeName: TypeName.Standard,
    modulePath: String,
    cache: MutableMap<TypeName.Standard, Int> = mutableMapOf(),
    visiting: MutableSet<TypeName.Standard> = mutableSetOf(),
  ): Int {
    cache[typeName]?.let { return it }
    if (!visiting.add(typeName)) {
      return 0
    }

    val superType = classSuperTypes[typeName]
    val rank =
      if (superType == null || modulePath(superType) != modulePath) {
        0
      } else {
        1 + typeInheritanceRank(superType, modulePath, cache, visiting)
      }

    visiting.remove(typeName)
    cache[typeName] = rank
    return rank
  }

  private fun applyExternalDiscriminatorConstraints(
    externalDiscriminatedProps: List<Pair<PropertyShape, PropertyShape>>,
    baseWireSchemaName: String,
    wireSchemaName: String,
    className: TypeName.Standard,
    context: TypeScriptResolutionContext,
    schemaCode: CodeBlock.Builder,
  ): String {
    if (externalDiscriminatedProps.isEmpty()) {
      return wireSchemaName
    }

    if (externalDiscriminatedProps.size == 1) {
      val externallyConstrainedSchemaName = "externallyConstrainedWireSchema1"
      val (discriminatedProp, discriminatorProp) = externalDiscriminatedProps.first()
      schemaCode
        .add("  const %L = ", externallyConstrainedSchemaName)
        .add(
          externalDiscriminatorMergedSchema(
            discriminatedProp,
            discriminatorProp,
            className,
            context,
            baseWireSchemaName,
          ),
        ).add(";\n")
      return externallyConstrainedSchemaName
    }

    var constrainedSchemaName = wireSchemaName

    externalDiscriminatedProps.forEachIndexed { index, (discriminatedProp, discriminatorProp) ->
      val discriminatorSchemaName = "externalDiscriminatorSchema${index + 1}"
      val externallyConstrainedSchemaName = "externallyConstrainedWireSchema${index + 1}"

      schemaCode
        .add("  const %L = ", discriminatorSchemaName)
        .add(externalDiscriminatorConstraintSchema(discriminatedProp, discriminatorProp, className, context))
        .add(";\n")
        .add(
          "  const %L = %T.intersection(%L, %L);\n",
          externallyConstrainedSchemaName,
          Z,
          constrainedSchemaName,
          discriminatorSchemaName,
        )

      constrainedSchemaName = externallyConstrainedSchemaName
    }

    return constrainedSchemaName
  }

  private fun externalDiscriminatorMergedSchema(
    discriminatedProp: PropertyShape,
    discriminatorProp: PropertyShape,
    className: TypeName.Standard,
    context: TypeScriptResolutionContext,
    baseWireSchemaName: String,
  ): CodeBlock {
    val discriminatedPropertyTypeName = resolvePropertyTypeName(discriminatedProp, className, context)
    val discriminatorPropertyTypeName =
      resolvePropertyTypeName(discriminatorProp, className, context).nonOptional.nonNullable as? TypeName.Standard
    val discriminatedShape =
      discriminatedProp.range as? NodeShape
        ?: genError("External discriminator property must reference an object type", discriminatedProp)

    val discriminatedPropertyName = discriminatedProp.name ?: discriminatedProp.typeScriptIdentifierName
    val discriminatorPropertyName = discriminatorProp.name ?: discriminatorProp.typeScriptIdentifierName
    val discriminatorMappings = buildDiscriminatorMappings(discriminatedShape, context)
    val inheritingTypes = context.findInheritingShapes(discriminatedShape)

    val variants =
      inheritingTypes.map { inheritingType ->
        val inheritingNode =
          inheritingType as? NodeShape
            ?: genError("External discriminator inheritance target must be an object", inheritingType)
        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingNode.id }?.key
        val discriminatorValue = inheritingNode.discriminatorValue ?: mappedDiscriminator ?: inheritingNode.name!!
        val inheritingTypeName = resolveReferencedTypeName(inheritingNode, context) as TypeName.Standard
        val variantSchema =
          CodeBlock
            .builder()
            .add("%T.looseObject({ ...%L.shape, %S: ", Z, baseWireSchemaName, discriminatorPropertyName)
            .add(discriminatorLiteralSchema(discriminatorPropertyTypeName, discriminatorValue))
            .add(", %S: ", discriminatedPropertyName)
            .add(runtimeResolveSchema(inheritingTypeName, "runtime", className))
            .add(" })")
            .build()
        discriminatorValue to variantSchema
      }

    if (variants.isEmpty()) {
      return CodeBlock.of("%L", baseWireSchemaName)
    }

    val optionalVariants = mutableListOf<CodeBlock>()
    if (discriminatedPropertyTypeName.isUndefinable) {
      optionalVariants +=
        CodeBlock.of(
          "%T.looseObject({ ...%L.shape, %S: %T.undefined().optional() })",
          Z,
          baseWireSchemaName,
          discriminatedPropertyName,
          Z,
        )
    }
    if (discriminatedPropertyTypeName.isNullable) {
      optionalVariants +=
        CodeBlock.of(
          "%T.looseObject({ ...%L.shape, %S: %T.null() })",
          Z,
          baseWireSchemaName,
          discriminatedPropertyName,
          Z,
        )
    }

    val variantSchemas = variants.map { it.second }
    val discriminatorSchema =
      if (
        canUseDiscriminatedUnion(
          discriminatorPropertyName,
          variants.map { it.first },
        )
      ) {
        CodeBlock
          .builder()
          .add("%T.discriminatedUnion(%S, [\n", Z, discriminatorPropertyName)
          .add(variantSchemas.joinToCode(",\n"))
          .add("\n  ])")
          .build()
      } else {
        CodeBlock
          .builder()
          .add("%T.union([\n", Z)
          .add(variantSchemas.joinToCode(",\n"))
          .add("\n  ])")
          .build()
      }

    if (optionalVariants.isEmpty()) {
      return discriminatorSchema
    }

    return CodeBlock
      .builder()
      .add("%T.union([\n", Z)
      .add((listOf(discriminatorSchema) + optionalVariants).joinToCode(",\n"))
      .add("\n  ])")
      .build()
  }

  private fun externalDiscriminatedPropertySchema(propertyTypeName: TypeName): CodeBlock {
    var schema = CodeBlock.of("%T.custom<%T>()", Z, propertyTypeName.nonOptional.nonNullable)
    if (propertyTypeName.isNullable) {
      schema = appendSchemaCall(schema, "nullable()")
    }
    if (propertyTypeName.isUndefinable) {
      schema = appendSchemaCall(schema, "optional()")
    }
    return schema
  }

  private fun externalDiscriminatorConstraintSchema(
    discriminatedProp: PropertyShape,
    discriminatorProp: PropertyShape,
    className: TypeName.Standard,
    context: TypeScriptResolutionContext,
  ): CodeBlock {
    val discriminatedPropertyTypeName = resolvePropertyTypeName(discriminatedProp, className, context)
    val discriminatorPropertyTypeName =
      resolvePropertyTypeName(discriminatorProp, className, context).nonOptional.nonNullable as? TypeName.Standard
    val discriminatedShape =
      discriminatedProp.range as? NodeShape
        ?: genError("External discriminator property must reference an object type", discriminatedProp)

    val discriminatedPropertyName = discriminatedProp.name ?: discriminatedProp.typeScriptIdentifierName
    val discriminatorPropertyName = discriminatorProp.name ?: discriminatorProp.typeScriptIdentifierName
    val discriminatorMappings = buildDiscriminatorMappings(discriminatedShape, context)
    val inheritingTypes = context.findInheritingShapes(discriminatedShape)

    val variants =
      inheritingTypes.map { inheritingType ->
        val inheritingNode =
          inheritingType as? NodeShape
            ?: genError("External discriminator inheritance target must be an object", inheritingType)
        val mappedDiscriminator = discriminatorMappings.entries.find { it.value == inheritingNode.id }?.key
        val discriminatorValue = inheritingNode.discriminatorValue ?: mappedDiscriminator ?: inheritingNode.name!!
        val inheritingTypeName = resolveReferencedTypeName(inheritingNode, context) as TypeName.Standard
        val variantSchema =
          CodeBlock
            .builder()
            .add("%T.object({ %S: ", Z, discriminatorPropertyName)
            .add(discriminatorLiteralSchema(discriminatorPropertyTypeName, discriminatorValue))
            .add(", %S: ", discriminatedPropertyName)
            .add(runtimeResolveSchema(inheritingTypeName, "runtime", className))
            .add(" })")
            .build()
        discriminatorValue to
          variantSchema
      }

    if (variants.isEmpty()) {
      return CodeBlock.of("%T.looseObject({})", Z)
    }

    val discriminatorSchema =
      if (
        canUseDiscriminatedUnion(
          discriminatorPropertyName,
          variants.map { it.first },
        )
      ) {
        CodeBlock
          .builder()
          .add("%T.discriminatedUnion(%S, [\n", Z, discriminatorPropertyName)
          .indent()
          .add(variants.map { it.second }.joinToCode(",\n"))
          .unindent()
          .add("\n  ])")
          .build()
      } else {
        CodeBlock.of("%T.union([\n%L\n  ])", Z, variants.map { it.second }.joinToCode(",\n"))
      }

    val optionalVariants = mutableListOf<CodeBlock>()
    if (discriminatedPropertyTypeName.isUndefinable) {
      optionalVariants +=
        CodeBlock.of(
          "%T.object({ %S: %T.undefined().optional() })",
          Z,
          discriminatedPropertyName,
          Z,
        )
    }
    if (discriminatedPropertyTypeName.isNullable) {
      optionalVariants +=
        CodeBlock.of(
          "%T.object({ %S: %T.null() })",
          Z,
          discriminatedPropertyName,
          Z,
        )
    }

    return if (optionalVariants.isEmpty()) {
      discriminatorSchema
    } else {
      CodeBlock
        .builder()
        .add("%T.union([\n", Z)
        .add((optionalVariants + discriminatorSchema).joinToCode(",\n"))
        .add("\n  ])")
        .build()
    }
  }

  private fun discriminatorLiteralSchema(
    discriminatorPropertyTypeName: TypeName.Standard?,
    discriminatorValue: String,
  ): CodeBlock {
    if (
      discriminatorPropertyTypeName != null &&
      (
        typeBuilders[discriminatorPropertyTypeName] is EnumSpec.Builder ||
          isLocalGeneratedType(discriminatorPropertyTypeName)
      )
    ) {
      return CodeBlock.of(
        "%T.literal(%T.%L)",
        Z,
        discriminatorPropertyTypeName,
        discriminatorEnumMemberName(discriminatorValue),
      )
    }

    return CodeBlock.of("%T.literal(%S)", Z, discriminatorValue)
  }

  private fun discriminatorLiteralValue(
    discriminatorPropertyTypeName: TypeName.Standard?,
    discriminatorValue: String,
  ): CodeBlock {
    if (
      discriminatorPropertyTypeName != null &&
      (
        typeBuilders[discriminatorPropertyTypeName] is EnumSpec.Builder ||
          isLocalGeneratedType(discriminatorPropertyTypeName)
      )
    ) {
      return CodeBlock.of("%T.%L", discriminatorPropertyTypeName, discriminatorEnumMemberName(discriminatorValue))
    }

    return CodeBlock.of("%S", discriminatorValue)
  }

  private fun discriminatorEnumMemberName(discriminatorValue: String): String =
    discriminatorValue
      .split("""\W""".toRegex())
      .joinToString("") { segment -> segment.replaceFirstChar { it.titlecase() } }
      .toUpperCamelCase()

  private fun typeNameOf(
    shape: Shape,
    context: TypeScriptResolutionContext,
  ): TypeName.Standard {

    if (!shape.isNameExplicit && context.suggestedTypeName != null) {
      return context.suggestedTypeName
    }

    modulePathOf(shape, context)?.let { modulePath ->
      val fullModulePath =
        if (modulePath.endsWith(".ts")) {
          modulePath.removeSuffix(".ts")
        } else {
          "$modulePath/${shape.typeScriptTypeName.camelCaseToKebabCase()}"
        }
      return TypeName.namedImport(
        shape.typeScriptTypeName,
        "!$fullModulePath${importStyle.importExtension}",
      )
    }

    val nestedAnn =
      shape.findAnnotation(Nested, null)
        ?: return TypeName.namedImport(
          shape.typeScriptTypeName,
          "!${shape.typeScriptTypeName.camelCaseToKebabCase()}${importStyle.importExtension}",
        )

    val (nestedEnclosedIn, nestedName) =
      when (nestedAnn) {
        is ScalarNode if nestedAnn.value == "dashed" -> {

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

        is ObjectNode -> {

          val enclosedIn =
            nestedAnn.getValue("enclosedIn")
              ?: genError("Nested annotation is missing 'enclosedIn'", nestedAnn)

          val name =
            nestedAnn.getValue("name")
              ?: genError("Nested annotation is missing name", nestedAnn)

          enclosedIn to name
        }

        else ->
          genError(
            "Nested annotation must be the value 'dashed' or an object containing 'enclosedIn' & 'name' keys",
            shape,
          )
      }

    val (nestedEnclosingType, nestedEnclosingTypeUnit) =
      context.resolveRef(nestedEnclosedIn, shape)
        ?: genError("Nested annotation references invalid enclosing type", nestedAnn)

    nestedEnclosingType as? Shape
      ?: genError("Nested annotation enclosing type references non-type definition", nestedAnn)

    val nestedEnclosingTypeContext = context.copy(unit = nestedEnclosingTypeUnit, suggestedTypeName = null)

    val nestedEnclosingTypeName =
      resolveTypeName(nestedEnclosingType, nestedEnclosingTypeContext) as TypeName.Standard

    return nestedEnclosingTypeName.nested(nestedName)
  }

  private fun modulePathOf(
    shape: Shape,
    context: TypeScriptResolutionContext,
  ): String? =
    (shape as? CustomizableElement)?.findStringAnnotation(TypeScriptModelModule, null)
      ?: modulePathOf(context.findDeclaringUnit(shape))

  private fun modulePathOf(unit: BaseUnit?): String? =
    (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModelModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModelModule, null)
      ?: (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModule, null)

  private fun collectTypes(types: List<Shape>) = types.flatMap { if (it is UnionShape) it.flattened else listOf(it) }

  private fun nearestCommonAncestor(
    types: List<Shape>,
    context: TypeScriptResolutionContext,
  ): TypeName? {

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
    context: TypeScriptResolutionContext,
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
    context: TypeScriptResolutionContext,
  ): String? =
    when {
      !shape.discriminator.isNullOrEmpty() -> shape.discriminator
      else -> context.findSuperShapeOrNull(shape)?.let { findDiscriminatorPropertyName(it as NodeShape, context) }
    }

  private fun findDiscriminatorPropertyValue(
    shape: NodeShape,
    context: TypeScriptResolutionContext,
  ): String? =
    if (!shape.discriminatorValue.isNullOrEmpty()) {
      shape.discriminatorValue!!
    } else {
      val root = context.findRootShape(shape) as NodeShape
      buildDiscriminatorMappings(root, context).entries.find { it.value == shape.id }?.key
    }

  private fun buildDiscriminatorMappings(
    shape: NodeShape,
    context: TypeScriptResolutionContext,
  ): Map<String, String> =
    shape.discriminatorMapping
      .mapNotNull { mapping ->
        val (refElement) = context.resolveRef(mapping.linkExpression().value(), shape) ?: return@mapNotNull null
        mapping.templateVariable().value()!! to refElement.id
      }.toMap()

  private fun isInDiscriminatedHierarchy(
    shape: NodeShape,
    context: TypeScriptResolutionContext,
  ): Boolean {
    val rootShape = context.findRootShape(shape) as? NodeShape ?: return false
    val hasHierarchy = context.findInheritingShapes(rootShape).isNotEmpty()
    if (!hasHierarchy) {
      return false
    }

    return !findDiscriminatorPropertyName(rootShape, context).isNullOrBlank() ||
      rootShape.findBoolAnnotation(ExternallyDiscriminated, null) == true
  }

  fun schemaInitializer(typeName: TypeName): CodeBlock {
    if (isDirectSchemaRefType(typeName)) {
      return schemaRefForStandard(typeName.nonOptional.nonNullable as TypeName.Standard)
    }

    if (isSetType(typeName.nonOptional.nonNullable)) {
      return setSchemaInitializer(typeName)
    }

    val runtimeSchema = runtimeSchemaForType(typeName, "runtime")
    if (!runtimeSchema.toString().contains("runtime.")) {
      return runtimeSchema
    }

    return CodeBlock
      .builder()
      .add("%Q((runtime: %T) => ", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add(runtimeSchema)
      .add(")")
      .build()
  }

  private fun setSchemaInitializer(typeName: TypeName): CodeBlock {
    val setType = typeName.nonOptional.nonNullable as TypeName.Parameterized
    val elementType = setType.typeArgs.firstOrNull() ?: UNKNOWN
    val elementSchema = runtimeSchemaForType(elementType, "runtime")

    var schema =
      CodeBlock
        .builder()
        .add("%T.codec(", Z)
        .add("%T.array(", Z)
        .add(elementSchema)
        .add("), ")
        .add("%T.set(", Z)
        .add(elementSchema)
        .add("), {\n")
        .add("  decode: (value) => new %T(value),\n", SET)
        .add("  encode: (value) => %T.from(value),\n", ARRAY)
        .add("})")
        .build()

    if (typeName.isNullable) {
      schema = appendSchemaCall(schema, "nullable()")
    }
    if (typeName.isUndefinable) {
      schema = appendSchemaCall(schema, "optional()")
    }

    return CodeBlock
      .builder()
      .add("%Q((runtime: %T) => ", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add(schema)
      .add(")")
      .build()
  }

  private fun isDirectSchemaRefType(typeName: TypeName): Boolean {
    if (typeName.isNullable || typeName.isUndefinable) {
      return false
    }
    return when (val required = typeName.nonOptional.nonNullable) {
      is TypeName.Standard -> isDirectSchemaRefStandard(required)
      else -> false
    }
  }

  private fun isDirectSchemaRefStandard(typeName: TypeName.Standard): Boolean =
    when (typeName) {
      STRING,
      NUMBER,
      BOOLEAN,
      ARRAY_BUFFER,
      URL_TYPE,
      DATE,
      INSTANT,
      LOCAL_DATE,
      LOCAL_TIME,
      LOCAL_DATETIME,
      OFFSET_DATETIME,
      OFFSET_TIME,
      ZONED_DATETIME,
      DURATION,
      -> true

      else ->
        isLocalGeneratedType(typeName) ||
          typeBuilders[typeName] is EnumSpec.Builder ||
          typeBuilders[typeName] is ClassSpec.Builder
    }

  private fun runtimeSchemaForType(
    typeName: TypeName,
    runtimeName: String,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock {
    val required = typeName.nonOptional.nonNullable
    var schema = runtimeSchemaForRequiredType(required, runtimeName, lazyRefType)
    if (typeName.isNullable) {
      schema = appendSchemaCall(schema, "nullable()")
    }
    if (typeName.isUndefinable) {
      schema = appendSchemaCall(schema, "optional()")
    }
    return schema
  }

  private fun runtimeSchemaForShape(
    shape: Shape,
    typeName: TypeName,
    runtimeName: String,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock {
    val required = typeName.nonOptional.nonNullable
    var schema = runtimeSchemaForRequiredType(required, runtimeName, lazyRefType)
    schema = applyShapeConstraints(shape, schema)
    if (typeName.isNullable) {
      schema = appendSchemaCall(schema, "nullable()")
    }
    if (typeName.isUndefinable) {
      schema = appendSchemaCall(schema, "optional()")
    }
    return schema
  }

  private fun runtimeSchemaForRequiredType(
    typeName: TypeName,
    runtimeName: String,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock =
    when (typeName) {
      is TypeName.Standard -> {
        zodSchemaForPrimitiveStandard(typeName)?.let { primitiveSchema ->
          return primitiveSchema
        }

        if (shouldLazyResolveSchema(typeName, lazyRefType)) {
          CodeBlock
            .builder()
            .add("%T.lazy(() => ", Z)
            .add(runtimeResolveSchema(typeName, runtimeName, lazyRefType))
            .add(")")
            .build()
        } else {
          runtimeResolveSchema(typeName, runtimeName, lazyRefType)
        }
      }

      is TypeName.Parameterized -> {
        val typeArg = typeName.typeArgs.firstOrNull()
        when (typeName.rawType) {
          ARRAY if typeArg != null ->
            CodeBlock
              .builder()
              .add("%T.array(", Z)
              .add(runtimeSchemaForType(typeArg, runtimeName, lazyRefType))
              .add(")")
              .build()

          SET if typeArg != null ->
            CodeBlock
              .builder()
              .add("%T.array(", Z)
              .add(runtimeSchemaForType(typeArg, runtimeName, lazyRefType))
              .add(")")
              .build()

          RECORD if typeArg != null ->
            CodeBlock
              .builder()
              .add("%T.record(", Z)
              .add("%T.string(), ", Z)
              .add(runtimeSchemaForType(typeName.typeArgs[1], runtimeName, lazyRefType))
              .add(")")
              .build()

          else -> CodeBlock.of("%T.unknown()", Z)
        }
      }

      is TypeName.Union -> {
        val nonOptionalChoices = typeName.typeChoices.filter { it != NULL && it != UNDEFINED }
        if (nonOptionalChoices.isEmpty()) {
          CodeBlock.of("%T.unknown()", Z)
        } else if (nonOptionalChoices.size == 1) {
          runtimeSchemaForType(nonOptionalChoices.first(), runtimeName, lazyRefType)
        } else {
          CodeBlock
            .builder()
            .add("%T.union([", Z)
            .add(nonOptionalChoices.map { runtimeSchemaForType(it, runtimeName, lazyRefType) }.joinToCode(", "))
            .add("])")
            .build()
        }
      }

      else -> CodeBlock.of("%T.unknown()", Z)
    }

  private fun isSetType(typeName: TypeName): Boolean = typeName is TypeName.Parameterized && typeName.rawType == SET

  private fun setDecodeExpression(
    valueRef: CodeBlock,
    typeName: TypeName,
  ): CodeBlock =
    when {
      typeName.isUndefinable && typeName.isNullable ->
        CodeBlock.of(
          "%L === undefined ? undefined : %L === null ? null : new %T(%L)",
          valueRef,
          valueRef,
          SET,
          valueRef,
        )

      typeName.isUndefinable ->
        CodeBlock.of("%L !== undefined ? new %T(%L) : undefined", valueRef, SET, valueRef)

      typeName.isNullable ->
        CodeBlock.of("%L === null ? null : new %T(%L)", valueRef, SET, valueRef)

      else -> CodeBlock.of("new %T(%L)", SET, valueRef)
    }

  private fun setEncodeExpression(
    valueRef: CodeBlock,
    typeName: TypeName,
  ): CodeBlock =
    when {
      typeName.isUndefinable && typeName.isNullable ->
        CodeBlock.of(
          "%L === undefined ? undefined : %L === null ? null : %T.from(%L)",
          valueRef,
          valueRef,
          ARRAY,
          valueRef,
        )

      typeName.isUndefinable ->
        CodeBlock.of("%L !== undefined ? %T.from(%L) : undefined", valueRef, ARRAY, valueRef)

      typeName.isNullable ->
        CodeBlock.of("%L === null ? null : %T.from(%L)", valueRef, ARRAY, valueRef)

      else -> CodeBlock.of("%T.from(%L)", ARRAY, valueRef)
    }

  private fun applyShapeConstraints(
    shapeRef: Shape,
    schema: CodeBlock,
  ): CodeBlock =
    when (shapeRef) {
      is ScalarShape -> applyScalarConstraints(shapeRef, schema)
      is ArrayShape -> applyArrayConstraints(shapeRef, schema)
      is NodeShape -> applyNodeConstraints(shapeRef, schema)
      else -> schema
    }

  private fun applyScalarConstraints(
    shape: ScalarShape,
    schema: CodeBlock,
  ): CodeBlock {
    var constrained = schema

    if (shape.minLength != null) {
      constrained = appendSchemaCall(constrained, "min(%L)", shape.minLength!!)
    }
    if (shape.maxLength != null) {
      constrained = appendSchemaCall(constrained, "max(%L)", shape.maxLength!!)
    }
    if (!shape.pattern.isNullOrBlank() && shape.pattern != ".*") {
      constrained = appendSchemaCall(constrained, "regex(/%L/)", shape.pattern!!)
    }
    if (shape.minimum != null) {
      constrained =
        if (shape.exclusiveMinimum == true) {
          appendSchemaCall(constrained, "gt(%L)", shape.minimum!!)
        } else {
          appendSchemaCall(constrained, "gte(%L)", shape.minimum!!)
        }
    }
    if (shape.maximum != null) {
      constrained =
        if (shape.exclusiveMaximum == true) {
          appendSchemaCall(constrained, "lt(%L)", shape.maximum!!)
        } else {
          appendSchemaCall(constrained, "lte(%L)", shape.maximum!!)
        }
    }
    if (shape.multipleOf != null) {
      constrained = appendSchemaCall(constrained, "multipleOf(%L)", shape.multipleOf!!)
    }

    return constrained
  }

  private fun applyArrayConstraints(
    shape: ArrayShape,
    schema: CodeBlock,
  ): CodeBlock {
    var constrained = schema
    if (shape.minItems != null) {
      constrained = appendSchemaCall(constrained, "min(%L)", shape.minItems!!)
    }
    if (shape.maxItems != null) {
      constrained = appendSchemaCall(constrained, "max(%L)", shape.maxItems!!)
    }
    return constrained
  }

  private fun canUseDiscriminatedUnion(
    discriminatorName: String,
    variantValues: List<String>,
  ): Boolean =
    discriminatorName.isNotBlank() &&
      variantValues.isNotEmpty() &&
      variantValues.none { it.isBlank() } &&
      variantValues.distinct().size == variantValues.size

  private fun shouldLazyResolveSchema(
    typeName: TypeName.Standard,
    lazyRefType: TypeName.Standard?,
  ): Boolean =
    (lazyRefType != null && typeName == lazyRefType) ||
      typeBuilders[typeName] is ClassSpec.Builder

  private fun applyNodeConstraints(
    shape: NodeShape,
    schema: CodeBlock,
  ): CodeBlock {
    var constrained = schema
    if (shape.minProperties != null) {
      constrained =
        appendSchemaCall(
          constrained,
          "refine((value) => Object.keys(value).length >= %L)",
          shape.minProperties!!,
        )
    }
    if (shape.maxProperties != null) {
      constrained =
        appendSchemaCall(
          constrained,
          "refine((value) => Object.keys(value).length <= %L)",
          shape.maxProperties!!,
        )
    }
    return constrained
  }

  private fun appendSchemaCall(
    schema: CodeBlock,
    methodCall: String,
    vararg args: Any,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add(schema)
      .add(".")
      .add(methodCall, *args)
      .build()

  private fun runtimeResolveSchema(
    typeName: TypeName.Standard,
    runtimeName: String,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add("%L.resolveSchema(", runtimeName)
      .add(runtimeResolveTargetForStandard(typeName, lazyRefType))
      .add(")")
      .build()

  private fun zodSchemaForPrimitiveStandard(typeName: TypeName.Standard): CodeBlock? =
    when (typeName) {
      STRING -> CodeBlock.of("%T.string()", Z)
      NUMBER -> CodeBlock.of("%T.number()", Z)
      BOOLEAN -> CodeBlock.of("%T.boolean()", Z)
      ANY -> CodeBlock.of("%T.any()", Z)
      NULL -> CodeBlock.of("%T.null()", Z)
      UNKNOWN, OBJECT_CLASS -> CodeBlock.of("%T.unknown()", Z)
      else -> null
    }

  private fun runtimeResolveTargetForStandard(
    typeName: TypeName.Standard,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock =
    when (typeName) {
      ARRAY_BUFFER -> CodeBlock.of("%T", ARRAY_BUFFER_SCHEMA)
      URL_TYPE -> CodeBlock.of("%T", URL_SCHEMA)
      DATE -> CodeBlock.of("%T", DATE_SCHEMA)
      INSTANT -> CodeBlock.of("%T", INSTANT_SCHEMA)
      LOCAL_DATE -> CodeBlock.of("%T", LOCAL_DATE_SCHEMA)
      LOCAL_TIME -> CodeBlock.of("%T", LOCAL_TIME_SCHEMA)
      LOCAL_DATETIME -> CodeBlock.of("%T", LOCAL_DATETIME_SCHEMA)
      OFFSET_DATETIME -> CodeBlock.of("%T", OFFSET_DATETIME_SCHEMA)
      OFFSET_TIME -> CodeBlock.of("%T", OFFSET_TIME_SCHEMA)
      ZONED_DATETIME -> CodeBlock.of("%T", ZONED_DATETIME_SCHEMA)
      DURATION -> CodeBlock.of("%T", DURATION_SCHEMA)
      else -> {
        if (typeBuilders[typeName] is ClassSpec.Builder) {
          CodeBlock.of("%T", schemaTargetForGeneratedType(typeName, lazyRefType))
        } else if (typeBuilders[typeName] is EnumSpec.Builder || isLocalGeneratedType(typeName)) {
          CodeBlock.of("%T", schemaTargetForGeneratedType(typeName, lazyRefType))
        } else {
          CodeBlock.of("%T.unknown()", Z)
        }
      }
    }

  private fun schemaTargetForGeneratedType(
    typeName: TypeName.Standard,
    lazyRefType: TypeName.Standard?,
  ): TypeName.Standard {
    if (typeName == lazyRefType) {
      return typeName.sibling("Schema")
    }

    if (hasCompanionSchemaType(typeName)) {
      return TypeName.namedImport(
        "${typeName.simpleName()}Schema",
        "!${companionSchemaModulePath(typeName)}${importStyle.importExtension}",
      )
    }

    return typeName.sibling("Schema")
  }

  private fun schemaRefForStandard(typeName: TypeName.Standard): CodeBlock =
    when (typeName) {
      STRING -> CodeBlock.of("%T", STRING_SCHEMA)
      NUMBER -> CodeBlock.of("%T", NUMBER_SCHEMA)
      BOOLEAN -> CodeBlock.of("%T", BOOLEAN_SCHEMA)
      ARRAY_BUFFER -> CodeBlock.of("%T", ARRAY_BUFFER_SCHEMA)
      URL_TYPE -> CodeBlock.of("%T", URL_SCHEMA)
      DATE -> CodeBlock.of("%T", DATE_SCHEMA)
      INSTANT -> CodeBlock.of("%T", INSTANT_SCHEMA)
      LOCAL_DATE -> CodeBlock.of("%T", LOCAL_DATE_SCHEMA)
      LOCAL_TIME -> CodeBlock.of("%T", LOCAL_TIME_SCHEMA)
      LOCAL_DATETIME -> CodeBlock.of("%T", LOCAL_DATETIME_SCHEMA)
      OFFSET_DATETIME -> CodeBlock.of("%T", OFFSET_DATETIME_SCHEMA)
      OFFSET_TIME -> CodeBlock.of("%T", OFFSET_TIME_SCHEMA)
      ZONED_DATETIME -> CodeBlock.of("%T", ZONED_DATETIME_SCHEMA)
      DURATION -> CodeBlock.of("%T", DURATION_SCHEMA)
      ANY -> CodeBlock.of("%T.any()", Z)
      NULL -> CodeBlock.of("%T.null()", Z)
      UNKNOWN, OBJECT_CLASS -> CodeBlock.of("%T.unknown()", Z)
      else -> {
        if (
          isLocalGeneratedType(typeName) ||
          typeBuilders[typeName] is EnumSpec.Builder ||
          typeBuilders[typeName] is ClassSpec.Builder
        ) {
          CodeBlock.of("%T", schemaTargetForGeneratedType(typeName, null))
        } else {
          CodeBlock.of("%T.unknown()", Z)
        }
      }
    }

  private fun isLocalGeneratedType(typeName: TypeName.Standard): Boolean =
    (typeName.base as? SymbolSpec.Imported)?.source?.startsWith("!") == true
}

private fun TypeName.Standard.sibling(name: String): TypeName.Standard =
  when (base) {
    is SymbolSpec.Imported ->
      TypeName.standard(SymbolSpec.importsName(base.value + name, (base as SymbolSpec.Imported).source))

    is SymbolSpec.Implicit ->
      TypeName.standard(SymbolSpec.implicit(base.value + name))
  }
