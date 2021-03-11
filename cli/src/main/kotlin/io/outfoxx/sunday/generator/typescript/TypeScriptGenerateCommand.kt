package io.outfoxx.sunday.generator.typescript

import amf.client.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.CommonGenerateCommand
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.AddGenerationHeader
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

abstract class TypeScriptGenerateCommand(name: String, help: String) : CommonGenerateCommand(name = name, help = help) {

  companion object {

    val defaultOptions = setOf(
      JacksonDecorators,
      AddGenerationHeader,
    )
  }

  val problemBaseUri
    by option(
      "-problem-base",
      help = "Default problem base URI"
    ).default("http://example.com/")

  val enabledOptions
    by option(
      "-enable",
      help = "Enable type generation option"
    ).enum<TypeScriptTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
      .multiple()

  val disabledOptions
    by option(
      "-disable",
      help = "Disable type generation option"
    ).enum<TypeScriptTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
      .multiple()

  val options get() = defaultOptions.plus(enabledOptions).minus(disabledOptions)

  override val typeRegistry: TypeScriptTypeRegistry by lazy {
    TypeScriptTypeRegistry(options)
  }

  override fun generatorFactory(document: Document) = generatorFactory(document, typeRegistry)

  abstract fun generatorFactory(document: Document, typeRegistry: TypeScriptTypeRegistry): TypeScriptGenerator

}
