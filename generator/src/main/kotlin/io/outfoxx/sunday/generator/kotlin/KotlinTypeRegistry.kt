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

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.tag
import io.outfoxx.sunday.generator.GeneratedOutputOwnership
import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.SuppressPublicApiWarnings
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.UseJakartaPackages
import io.outfoxx.sunday.generator.kotlin.utils.BeanValidationTypes
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrarySupport
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc
import io.outfoxx.sunday.generator.kotlin.utils.kotlinFileSpec
import io.outfoxx.sunday.generator.utils.GeneratedProperties
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import javax.annotation.processing.Generated

class KotlinTypeRegistry(
  generatedAnnotationName: String?,
  override val generationMode: GenerationMode,
  override val options: Set<Option>,
  override val problemLibrary: KotlinProblemLibrary = KotlinProblemLibrary.QUARKUS,
  problemRfc: KotlinProblemRfc = KotlinProblemRfc.RFC9457,
  val validateProblemRfc: Boolean = false,
  generationTimestamp: String? = LocalDateTime.now().format(ISO_LOCAL_DATE_TIME),
  override val applicationMetadata: KotlinApplicationMetadataOptions = KotlinApplicationMetadataOptions(),
) : KotlinTypeOutputRegistry {

  override val outputOwnership =
    GeneratedOutputOwnership()

  enum class Option {
    ImplementModel,

    /** Opts ordinary implemented models into structural equality and matching hash codes. */
    ModelValueEquality,
    ValidationConstraints,
    ContainerElementValid,
    JacksonAnnotations,

    /** Compatibility marker; generation options now control preservation and default it to enabled. */
    PreserveUnknownFields,
    AddGeneratedAnnotation,
    SuppressPublicApiWarnings,
    UseJakartaPackages,
  }

  val generationTimestamp = generationTimestamp?.ifBlank { null }
  private val generatedAnnotationName = ClassName.bestGuess(generatedAnnotationName ?: Generated::class.qualifiedName!!)
  private val serviceProviders = linkedMapOf<ClassName, MutableSet<ClassName>>()
  private val propertyResources = linkedMapOf<String, MutableMap<String, String>>()
  private val propertyContributions = mutableListOf<Triple<String, Map<String, String>, Any>>()
  private val propertyOwners = linkedMapOf<String, MutableSet<String>>()
  internal val typeBuilders = mutableMapOf<ClassName, TypeSpec.Builder>()
  override val beanValidationTypes =
    if (options.contains(UseJakartaPackages)) {
      BeanValidationTypes.JAKARTA
    } else {
      BeanValidationTypes.JAVAX
    }
  override val problemLibrarySupport: KotlinProblemLibrarySupport = problemLibrary.support(problemRfc)

  fun generateFiles(
    selection: GeneratedOutputSelection = GeneratedOutputSelection(),
    outputDirectory: Path,
  ) {

    val builtTypes = buildTypes(selection)
    val selectedProperties = linkedMapOf<String, MutableMap<String, String>>()
    propertyContributions.filter { outputOwnership.includes(selection, it.third) }.forEach { (path, values, _) ->
      GeneratedProperties.merge(selectedProperties.getOrPut(path) { linkedMapOf() }, values, path)
    }
    if (selection.categories.isEmpty() || GeneratedTypeCategory.Service in selection.categories) {
      selectedProperties.keys.forEach {
        GeneratedProperties
          .destination(outputDirectory, it)
      }
    }

    builtTypes.entries
      .filter { it.key.topLevelClassName() == it.key }
      .filter { type -> outputOwnership.includes(selection, type.key) }
      .map { kotlinFileSpec(it.key.packageName, it.value) }
      .forEach { it.writeTo(outputDirectory) }
    if (selection.categories.isEmpty() || GeneratedTypeCategory.Service in selection.categories) {
      selectedProperties.forEach { (path, values) ->
        GeneratedProperties
          .write(outputDirectory, path, values)
      }
      serviceProviders.forEach { (service, implementations) ->
        val selected = implementations.filter { outputOwnership.includes(selection, it) }
        if (selected.isEmpty()) return@forEach
        val resource = outputDirectory.resolve("META-INF/services/${service.canonicalName}")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, selected.joinToString("\n", postfix = "\n") { it.canonicalName })
      }
    }
  }

  override fun addProperties(
    path: String,
    values: Map<String, String>,
    owner: String,
  ) {
    val contribution = Any()
    outputOwnership.register(contribution, GeneratedTypeCategory.Service)
    propertyContributions.add(Triple(path, values, contribution))
    GeneratedProperties
      .validatePath(path)
    val artifactValues =
      propertyResources.values
        .flatMap { it.entries }
        .associate { it.toPair() }
        .toMutableMap()
    GeneratedProperties.merge(
      artifactValues,
      values,
      "$path from $propertyOwners and $owner",
    )
    val owners = propertyOwners.getOrPut(path) { linkedSetOf() }
    GeneratedProperties.merge(
      propertyResources.getOrPut(path) { linkedMapOf() },
      values,
      "$path from ${owners + owner}",
    )
    owners.add(owner)
  }

  override fun addServiceProvider(
    service: ClassName,
    implementation: ClassName,
  ) {
    serviceProviders.getOrPut(service) { linkedSetOf() }.add(implementation)
  }

  /** Builds selected declarations after excluding descendants of filtered containers. */
  fun buildTypes(selection: GeneratedOutputSelection = GeneratedOutputSelection()): Map<ClassName, TypeSpec> {
    // Assemble copies so another output selection cannot inherit previously attached members.
    val selectedBuilders =
      typeBuilders
        .filter { outputOwnership.includes(selection, it.key) }
        .mapValues { it.value.build().toBuilder() }
    selectedBuilders.entries.sortedByDescending { it.key.simpleNames.size }.forEach { (name, builder) ->
      val enclosing = name.enclosingClassName() ?: return@forEach
      selectedBuilders[enclosing]?.addType(builder.build())
    }
    return selectedBuilders.mapValues { it.value.build() }
  }

  override fun addServiceType(
    className: ClassName,
    serviceType: TypeSpec.Builder,
  ) {

    serviceType
      .addGenerated(true)
      .addSuppress()

    outputOwnership.register(className, GeneratedTypeCategory.Service)
    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined by ${outputOwnership.declarationDescription(className)}")
    }
  }

  /** Registers an IR-generated model type. */
  override fun addModelType(
    className: ClassName,
    modelType: TypeSpec.Builder,
  ) {

    modelType
      .addGenerated(true)
      .addSuppress()

    outputOwnership.register(className, GeneratedTypeCategory.Model)
    modelType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, modelType) != null) {
      genError("Model type '$className' is already defined by ${outputOwnership.declarationDescription(className)}")
    }
  }

  private fun TypeSpec.Builder.addGenerated(verbose: Boolean): TypeSpec.Builder {
    if (options.contains(AddGeneratedAnnotation)) {
      addAnnotation(
        AnnotationSpec
          .builder(generatedAnnotationName)
          .apply {
            if (verbose) {
              addMember("value = [%S]", this@KotlinTypeRegistry.javaClass.name)
              if (generationTimestamp != null) {
                addMember("date = %S", generationTimestamp)
              }
            }
          }.build(),
      )
    }
    return this
  }

  override fun addGeneratedTo(
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
}
