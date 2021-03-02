package io.outfoxx.sunday.generator

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

abstract class KotlinGenerateCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

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

  val mode
    by option(
      "-mode",
      help = "Target 'client' or 'server' for generated services"
    ).enum<GenerationMode> { it.name.camelCaseToKebabCase() }
      .default(GenerationMode.Client)

  val reactiveResponseType
    by option(
      "-reactive",
      help = "Generic result type for reactive service methods"
    )

  val options
    by option(
      "-enable",
      help = "Enable options for type generation"
    ).enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }
      .multiple()

  val mediaTypes
    by option(
      "-media-type",
      help = "Specify to control order of initialized media types"
    ).multiple().unique()

  val outputDirectory
    by option(
      "-out",
      help = "Output directory"
    ).file(mustExist = true, canBeFile = false, canBeDir = true)
      .required()

  val files
    by argument(
      help = "RAML source files"
    ).file(mustExist = true, canBeFile = true, canBeDir = false)
      .multiple(required = true)

}
