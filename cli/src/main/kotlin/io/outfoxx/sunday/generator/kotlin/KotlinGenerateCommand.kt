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

package io.outfoxx.sunday.generator.kotlin

import amf.core.client.platform.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.*
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.*
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemRfc

abstract class KotlinGenerateCommand(name: String, help: String) : CommonGenerateCommand(name = name, help = help) {

  companion object {
    val impliedRegistryOptions = setOf(AddGeneratedAnnotation)
  }

  val packageName by option(
    "-pkg",
    help = "Default package",
  )

  val modelPackageName by option(
    "-model-pkg",
    help = "Default model package, if not specified '-pkg' is used",
  )

  val servicePackageName by option(
    "-service-pkg",
    help = "Default service package, if not specified '-pkg' is used",
  )

  val registryOptions by flags<KotlinTypeRegistry.Option> {
    ImplementModel to "Generate classes for model types, instead of interfaces".default(true)
    JacksonAnnotations to "Add Jackson annotations to model classes".default(true)
    ValidationConstraints to "Add validation constraints to model classes".default(true)
    ContainerElementValid to "Use container element @Valid for cascaded validation".default(true)
    SuppressPublicApiWarnings to "Suppress warnings for Kotlin Public API style code".default(false)
    UseJakartaPackages to "Use Jakarta EE package name instead of Java EE package name".default(false)
  }.grouped("Model Generation Options")

  val generatedAnnotationName by option(
    "-generated-annotation",
    help = "Fully qualified name of generated source annotation",
  )

  val problemLibrary by option(
    "-problem-library",
    help = "Problem library to target (quarkus, zalando, or sunday)",
  ).enum<KotlinProblemLibrary> { it.id }
    .default(KotlinProblemLibrary.QUARKUS)

  val problemRfc by option(
    "-problem-rfc",
    help = "Problem Details RFC compliance mode (rfc7807 or rfc9457)",
  ).enum<KotlinProblemRfc> { it.id }
    .default(KotlinProblemRfc.RFC9457)

  private fun implyRegistryOptions(): Set<KotlinTypeRegistry.Option> {
    val options = mutableSetOf<KotlinTypeRegistry.Option>()
    if (generatedAnnotationName != null) {
      options += AddGeneratedAnnotation
    }
    return options.toSet()
  }

  fun allRegistryOptions() = registryOptions + implyRegistryOptions()

  protected open fun effectiveProblemLibrary(): KotlinProblemLibrary = problemLibrary

  protected open fun effectiveProblemRfc(): KotlinProblemRfc = problemRfc

  override val typeRegistry: KotlinTypeRegistry by lazy {
    KotlinTypeRegistry(
      modelPackageName ?: packageName,
      generatedAnnotationName,
      mode,
      allRegistryOptions(),
      effectiveProblemLibrary(),
      effectiveProblemRfc(),
    )
  }

  abstract val mode: GenerationMode

  override fun generatorFactory(document: Document, shapeIndex: ShapeIndex) =
    generatorFactory(document, shapeIndex, typeRegistry)

  abstract fun generatorFactory(
    document: Document,
    shapeIndex: ShapeIndex,
    typeRegistry: KotlinTypeRegistry,
  ): KotlinGenerator
}
