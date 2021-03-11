package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.GenerationMode
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

  val packageName
    by option(
      "-pkg",
      help = "Default package"
    ).required()

  val modelPackageName
    by option(
      "-model-pkg",
      help = "Default model package, if not specified '-pkg' is used"
    )

  val servicePackageName
    by option(
      "-service-pkg",
      help = "Default service package, if not specified '-pkg' is used"
    )

  val problemBaseUri
    by option(
      "-problem-base",
      help = "Default problem base URI"
    ).default("http://example.com/")

  val enabledOptions
    by option(
      "-enable",
      help = "Enable type generation option"
    ).enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
      .multiple()

  val disabledOptions
    by option(
      "-disable",
      help = "Disable type generation option"
    ).enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
      .multiple()

  val options get() = defaultOptions.plus(enabledOptions).minus(disabledOptions)

  override val typeRegistry: KotlinTypeRegistry by lazy {
    KotlinTypeRegistry(
      modelPackageName ?: packageName,
      mode,
      options,
    )
  }

  abstract val mode: GenerationMode

  override fun generatorFactory(document: Document) = generatorFactory(document, typeRegistry)

  abstract fun generatorFactory(document: Document, typeRegistry: KotlinTypeRegistry): KotlinGenerator

}
