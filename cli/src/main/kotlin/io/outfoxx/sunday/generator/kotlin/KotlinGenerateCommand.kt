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
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.AddGeneratedAnnotation
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

abstract class KotlinGenerateCommand(name: String, help: String) : CommonGenerateCommand(name = name, help = help) {

  companion object {

    val defaultOptions = setOf(
      ImplementModel,
      JacksonAnnotations,
      ValidationConstraints,
      AddGeneratedAnnotation,
    )
  }

  val packageName by option(
    "-pkg",
    help = "Default package",
  ).required()

  val modelPackageName by option(
    "-model-pkg",
    help = "Default model package, if not specified '-pkg' is used",
  )

  val servicePackageName by option(
    "-service-pkg",
    help = "Default service package, if not specified '-pkg' is used",
  )

  val enabledOptions by option(
    "-enable",
    help = "Enable type generation option",
  ).enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
    .multiple()

  val disabledOptions by option(
    "-disable",
    help = "Disable type generation option",
  ).enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
    .multiple()

  val generatedAnnotationName by option(
    "-generated-annotation",
    help = "Fully qualified name of generated source annotation",
  )

  val options get() = defaultOptions.plus(enabledOptions).minus(disabledOptions)

  override val typeRegistry: KotlinTypeRegistry by lazy {
    KotlinTypeRegistry(
      modelPackageName ?: packageName,
      generatedAnnotationName,
      mode,
      options,
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
