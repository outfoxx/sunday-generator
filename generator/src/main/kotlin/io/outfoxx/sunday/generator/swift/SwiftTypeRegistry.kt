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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.AddGeneratedHeader
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.ExtensionSpec
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.tag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

class SwiftTypeRegistry(
  override val options: Set<Option>,
) : SwiftTypeOutputRegistry {

  enum class Option {
    AddGeneratedHeader,
    DefaultIdentifiableTypes,
  }

  /**
   * Default Swift output subdirectories for generated non-service declarations.
   */
  enum class OutputDirectory(
    val path: String,
  ) {
    Models("Models"),
    Requests("Requests"),
    Responses("Responses"),
    Events("Events"),
    Problems("Problems"),
  }

  internal val typeBuilders = mutableMapOf<DeclaredTypeName, TypeSpec.Builder>()
  private val typeOutputLocations = mutableMapOf<DeclaredTypeName, TypeOutputLocation>()
  internal var referenceTypes = mutableMapOf<TypeName, TypeName>()

  fun generateFiles(
    categories: Set<GeneratedTypeCategory>,
    outputDirectory: Path,
  ) {

    fun addExtensions(
      builder: FileSpec.Builder,
      typeSpec: TypeSpec,
    ) {
      typeSpec.tag<AssociatedExtensions>()?.forEach { builder.addExtension(it) }
      typeSpec.typeSpecs.forEach {
        if (it is TypeSpec) {
          addExtensions(builder, it)
        }
      }
    }

    val builtTypes = buildTypes()

    val generatedFiles =
      builtTypes.entries
        .filter { it.key.topLevelTypeName() == it.key }
        .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
        .map { (typeName, typeSpec) ->
          FileSpec
            .builder(typeName.moduleName, typeSpec.name)
            .addType(typeSpec)
            .apply { addExtensions(this, typeSpec) }
            .build() to typeName.outputLocation(typeSpec)
        }

    val generatedPaths =
      generatedFiles.map { (fileSpec, outputLocation) ->
        outputLocation
          .resolve(outputDirectory)
          .resolve("${fileSpec.name}.swift")
          .relativeTo(outputDirectory)
      }

    SwiftGeneratedOutputManifest.clean(outputDirectory, generatedPaths)

    generatedFiles.forEach { (fileSpec, outputLocation) ->
      val fileOutputDirectory = outputLocation.resolve(outputDirectory)
      Files.createDirectories(fileOutputDirectory)
      fileSpec.writeTo(fileOutputDirectory)
    }

    SwiftGeneratedOutputManifest.write(outputDirectory, generatedPaths)
  }

  private fun DeclaredTypeName.outputLocation(typeSpec: TypeSpec): TypeOutputLocation =
    typeOutputLocations[this]
      ?: when (typeSpec.tag(GeneratedTypeCategory::class)) {
        GeneratedTypeCategory.Model -> TypeOutputLocation(directory = OutputDirectory.Models)
        else -> TypeOutputLocation()
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

  override fun getReferenceType(className: TypeName): TypeName? = referenceTypes[className]

  /**
   * Registers a polymorphic wire reference type for a generated model type.
   */
  override fun addReferenceType(
    className: TypeName,
    referenceType: TypeName,
  ) {
    referenceTypes[className] = referenceType
  }

  override fun addServiceType(
    className: DeclaredTypeName,
    serviceType: TypeSpec.Builder,
    outputGroup: String?,
  ) {

    serviceType.addModifiers(PUBLIC)

    if (options.contains(AddGeneratedHeader)) {
      serviceType.addDoc(GenerationHeaders.create("${className.simpleName}.swift"))
    }

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined")
    }
    typeOutputLocations[className] = TypeOutputLocation(group = outputGroup)
  }

  /** Registers an IR-generated model type. */
  override fun addModelType(
    className: DeclaredTypeName,
    modelType: TypeSpec.Builder,
    outputDirectory: OutputDirectory,
    outputGroup: String?,
  ) {

    modelType.addModifiers(PUBLIC)

    if (options.contains(AddGeneratedHeader) && className.enclosingTypeName() == null) {
      modelType.addDoc(GenerationHeaders.create("${className.simpleName}.swift"))
    }

    modelType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, modelType) != null) {
      genError("Model type '$className' is already defined")
    }
    typeOutputLocations[className] = TypeOutputLocation(group = outputGroup, directory = outputDirectory)
  }

  private data class TypeOutputLocation(
    val group: String? = null,
    val directory: OutputDirectory? = null,
  ) {

    fun resolve(outputDirectory: Path): Path {
      val groupSegment = group?.replace(Regex("[^A-Za-z0-9_-]+"), "")?.takeIf { it.isNotEmpty() }
      val directorySegment = directory?.path?.takeUnless { it == groupSegment }
      val segments =
        sequenceOf(
          groupSegment,
          directorySegment,
        )

      return segments.filterNotNull().fold(outputDirectory) { path, segment -> path.resolve(segment) }
    }
  }
}

class AssociatedExtensions : ArrayList<ExtensionSpec>()

internal val TypeSpec.Builder.associatedExtensions: AssociatedExtensions
  get() {
    var value = tags[AssociatedExtensions::class] as AssociatedExtensions?
    if (value == null) {
      value = AssociatedExtensions()
      tag(value)
    }
    return value
  }
