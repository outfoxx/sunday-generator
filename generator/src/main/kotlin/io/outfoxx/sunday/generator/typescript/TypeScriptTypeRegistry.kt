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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.utils.ARRAY_BUFFER_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.BOOLEAN_SCHEMA
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
import io.outfoxx.sunday.generator.typescript.utils.RECORD
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
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeAliasSpec
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
import io.outfoxx.typescriptpoet.tag
import java.nio.file.Files
import java.nio.file.Path

class TypeScriptTypeRegistry(
  private val options: Set<Option>,
  internal val importStyle: ImportStyle = ImportStyle.ESM,
  internal val schemaRuntimeArgumentName: String = "runtime",
) : TypeScriptTypeOutputRegistry {

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

  internal val exportedTypeBuilders = LinkedHashMap<TypeName.Standard, AnyTypeSpecBuilder>()
  internal val typeBuilders = mutableMapOf<TypeName.Standard, AnyTypeSpecBuilder>()
  private val typeModuleExtras = mutableMapOf<TypeName.Standard, MutableList<Any>>()
  internal val classSuperTypes = mutableMapOf<TypeName.Standard, TypeName.Standard?>()
  private val companionSchemaFileMembers = mutableMapOf<String, MutableList<Pair<TypeName.Standard, CodeBlock>>>()
  private val companionSchemaTypeKeys = mutableSetOf<String>()
  private val companionSchemaModules = mutableMapOf<String, TypeName.Standard>()

  fun generateFiles(
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

  override fun addServiceType(
    typeName: TypeName.Standard,
    serviceType: AnyTypeSpecBuilder,
    extras: List<Any>,
  ) {

    when (serviceType) {
      is ClassSpec.Builder -> serviceType.addModifiers(Modifier.EXPORT)
      is InterfaceSpec.Builder -> serviceType.addModifiers(Modifier.EXPORT)
    }

    serviceType.tag(GeneratedTypeCategory.Service)

    if (exportedTypeBuilders.putIfAbsent(typeName, serviceType) != null) {
      genError("Service type '$typeName' is already defined")
    }

    extras.forEach { extra -> addModuleExtra(typeName, extra) }
  }

  /** Adds an IR-generated model type. */
  override fun addModelType(
    typeName: TypeName.Standard,
    modelType: AnyTypeSpecBuilder,
    extras: List<Any>,
  ) {
    modelType.tag(GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(typeName, modelType) != null) {
      genError("Model type '$typeName' is already defined")
    }
    if (exportedTypeBuilders.putIfAbsent(typeName, modelType) != null) {
      genError("Model type '$typeName' is already exported")
    }

    extras.forEach { extra -> addModuleExtra(typeName, extra) }
  }

  /**
   * Marks a generated model as using a companion schema file.
   */
  override fun addCompanionSchemaType(typeName: TypeName.Standard) {
    registerCompanionSchemaType(typeName)
  }

  /**
   * Adds code to the generated model's companion schema file.
   */
  override fun addCompanionSchemaCode(
    typeName: TypeName.Standard,
    member: CodeBlock,
  ) {
    addCompanionSchemaFileExtra(typeName, member)
  }

  internal fun addModuleExtra(
    typeName: TypeName.Standard,
    member: Any,
  ) {
    typeModuleExtras.computeIfAbsent(typeName) { mutableListOf() }.add(member)
  }

  internal fun registerCompanionSchemaType(typeName: TypeName.Standard) {
    companionSchemaTypeKeys.add(typeKey(typeName))
    companionSchemaModules[companionSchemaModulePath(typeName)] = typeName
  }

  internal fun hasCompanionSchemaType(typeName: TypeName.Standard): Boolean =
    companionSchemaTypeKeys.contains(typeKey(typeName))

  internal fun addCompanionSchemaFileExtra(
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

  override fun generatedTypeName(
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
      is SymbolSpec.Implicit -> genError("Unable to resolve imported type for '$typeName'")
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

  override fun schemaInitializer(typeName: TypeName): CodeBlock {
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
          typeBuilders[typeName] is TypeAliasSpec.Builder ||
          typeBuilders[typeName] is ClassSpec.Builder
    }

  override fun runtimeSchemaForType(
    typeName: TypeName,
    runtimeName: String,
    lazyRefType: TypeName.Standard?,
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

  internal fun runtimeSchemaForRequiredType(
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

  internal fun shouldLazyResolveSchema(
    typeName: TypeName.Standard,
    lazyRefType: TypeName.Standard?,
  ): Boolean =
    (lazyRefType != null && typeName == lazyRefType) ||
      typeBuilders[typeName] is ClassSpec.Builder ||
      typeBuilders[typeName] is TypeAliasSpec.Builder

  internal fun appendSchemaCall(
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

  internal fun runtimeResolveSchema(
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

  internal fun zodSchemaForPrimitiveStandard(typeName: TypeName.Standard): CodeBlock? =
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
        } else if (
          typeBuilders[typeName] is EnumSpec.Builder ||
          typeBuilders[typeName] is TypeAliasSpec.Builder ||
          isLocalGeneratedType(typeName)
        ) {
          CodeBlock.of("%T", schemaTargetForGeneratedType(typeName, lazyRefType))
        } else {
          CodeBlock.of("%T.unknown()", Z)
        }
      }
    }

  internal fun schemaTargetForGeneratedType(
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
          typeBuilders[typeName] is ClassSpec.Builder ||
          typeBuilders[typeName] is TypeAliasSpec.Builder
        ) {
          CodeBlock.of("%T", schemaTargetForGeneratedType(typeName, null))
        } else {
          CodeBlock.of("%T.unknown()", Z)
        }
      }
    }

  internal fun isLocalGeneratedType(typeName: TypeName.Standard): Boolean =
    (typeName.base as? SymbolSpec.Imported)?.source?.startsWith("!") == true
}

internal fun TypeName.Standard.sibling(name: String): TypeName.Standard =
  when (base) {
    is SymbolSpec.Imported ->
      TypeName.standard(SymbolSpec.importsName(base.value + name, (base as SymbolSpec.Imported).source))

    is SymbolSpec.Implicit ->
      TypeName.standard(SymbolSpec.implicit(base.value + name))
  }
