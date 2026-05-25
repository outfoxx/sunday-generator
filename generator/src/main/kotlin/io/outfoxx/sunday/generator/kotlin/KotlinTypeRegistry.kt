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
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
import javax.annotation.processing.Generated

class KotlinTypeRegistry(
  override val defaultModelPackageName: String?,
  generatedAnnotationName: String?,
  override val generationMode: GenerationMode,
  override val options: Set<Option>,
  override val problemLibrary: KotlinProblemLibrary = KotlinProblemLibrary.QUARKUS,
  problemRfc: KotlinProblemRfc = KotlinProblemRfc.RFC9457,
  val validateProblemRfc: Boolean = false,
  generationTimestamp: String? = LocalDateTime.now().format(ISO_LOCAL_DATE_TIME),
) : KotlinTypeOutputRegistry {

  enum class Option {
    ImplementModel,
    ValidationConstraints,
    ContainerElementValid,
    JacksonAnnotations,
    AddGeneratedAnnotation,
    SuppressPublicApiWarnings,
    UseJakartaPackages,
  }

  val generationTimestamp = generationTimestamp?.ifBlank { null }
  private val generatedAnnotationName = ClassName.bestGuess(generatedAnnotationName ?: Generated::class.qualifiedName!!)
  internal val typeBuilders = mutableMapOf<ClassName, TypeSpec.Builder>()
  override val beanValidationTypes =
    if (options.contains(UseJakartaPackages)) {
      BeanValidationTypes.JAKARTA
    } else {
      BeanValidationTypes.JAVAX
    }
  override val problemLibrarySupport: KotlinProblemLibrarySupport = problemLibrary.support(problemRfc)

  fun generateFiles(
    categories: Set<GeneratedTypeCategory>,
    outputDirectory: Path,
  ) {

    val builtTypes = buildTypes()

    builtTypes.entries
      .filter { it.key.topLevelClassName() == it.key }
      .filter { type -> categories.contains(type.value.tag(GeneratedTypeCategory::class)) }
      .map { kotlinFileSpec(it.key.packageName, it.value) }
      .forEach { it.writeTo(outputDirectory) }
  }

  fun buildTypes(): Map<ClassName, TypeSpec> {

    // Add nested classes to parents
    typeBuilders.entries
      .toList()
      .sortedByDescending { it.key.simpleNames.size }
      .forEach { (className, typeBuilder) ->
        // Is this a nested class?
        val enclosingClassName = className.enclosingClassName() ?: return@forEach
        typeBuilders[enclosingClassName]?.addType(typeBuilder.build())
      }

    return typeBuilders.mapValues { it.value.build() }
  }

  override fun addServiceType(
    className: ClassName,
    serviceType: TypeSpec.Builder,
  ) {

    serviceType
      .addGenerated(true)
      .addSuppress()

    serviceType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Service)

    if (typeBuilders.putIfAbsent(className, serviceType) != null) {
      genError("Service type '$className' is already defined")
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

    modelType.tag(GeneratedTypeCategory::class, GeneratedTypeCategory.Model)

    if (typeBuilders.putIfAbsent(className, modelType) != null) {
      genError("Model type '$className' is already defined")
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
