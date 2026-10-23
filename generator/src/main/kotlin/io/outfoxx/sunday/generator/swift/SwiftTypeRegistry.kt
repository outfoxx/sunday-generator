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

import io.outfoxx.sunday.generator.GeneratedOutputOwnership
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.common.GenerationHeaders
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.Option.AddGeneratedHeader
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.ExtensionSpec
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

  override val outputOwnership =
    GeneratedOutputOwnership()

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
    selection: GeneratedOutputSelection = GeneratedOutputSelection(),
    outputDirectory: Path,
  ) {

    val builtTypes = buildTypes(selection)

    val generatedFiles =
      builtTypes.entries
        .filter { it.key.topLevelTypeName() == it.key }
        .filter { type -> outputOwnership.includes(selection, type.key) }
        .map { (typeName, typeSpec) ->
          swiftGeneratedFile(typeName, typeSpec) to
            typeName.outputLocation(typeSpec).copy(module = typeName.moduleName.takeIf { it.isNotBlank() })
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

  /** Builds selected declarations after excluding descendants of filtered containers. */
  fun buildTypes(selection: GeneratedOutputSelection = GeneratedOutputSelection()): Map<DeclaredTypeName, TypeSpec> {
    // Assemble copies so another output selection cannot inherit previously attached members.
    val selectedBuilders =
      typeBuilders
        .filter { outputOwnership.includes(selection, it.key) }
        .mapValues { (_, original) ->
          original.build().toBuilder().apply {
            // SwiftPoet's toBuilder omits tags carrying file-level functions and extensions.
            tags.putAll(original.tags)
          }
        }
    selectedBuilders.entries.sortedByDescending { it.key.simpleNames.size }.forEach { (name, builder) ->
      val enclosing = name.enclosingTypeName() ?: return@forEach
      selectedBuilders[enclosing]?.addType(builder.build())
    }
    return selectedBuilders.mapValues { it.value.build() }
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

    outputOwnership.register(className, GeneratedTypeCategory.Service)
    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined by ${outputOwnership.declarationDescription(className)}")
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

    if (io.outfoxx.swiftpoet.Modifier.OPEN !in modelType.build().modifiers) modelType.addModifiers(PUBLIC)

    if (options.contains(AddGeneratedHeader) && className.enclosingTypeName() == null) {
      modelType.addDoc(GenerationHeaders.create("${className.simpleName}.swift"))
    }

    outputOwnership.register(className, GeneratedTypeCategory.Model)
    modelType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, modelType) != null) {
      genError("Model type '$className' is already defined by ${outputOwnership.declarationDescription(className)}")
    }
    typeOutputLocations[className] = TypeOutputLocation(group = outputGroup, directory = outputDirectory)
  }

  private data class TypeOutputLocation(
    val module: String? = null,
    val group: String? = null,
    val directory: OutputDirectory? = null,
  ) {

    fun resolve(outputDirectory: Path): Path {
      val groupSegment = group?.replace(Regex("[^A-Za-z0-9_-]+"), "")?.takeIf { it.isNotEmpty() }
      val directorySegment = directory?.path?.takeUnless { it == groupSegment }
      val segments =
        sequenceOf(
          module,
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
