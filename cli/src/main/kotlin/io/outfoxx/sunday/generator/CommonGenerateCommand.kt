package io.outfoxx.sunday.generator

import amf.client.model.document.Document
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

abstract class CommonGenerateCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

  val mediaTypes
    by option(
      "-media-type",
      help = "Specify to control order of initialized media types"
    ).multiple().unique()

  val outputCategories
    by option(
      "-category",
      help = "Add category of type to output ${GeneratedTypeCategory.values()}"
    ).enum<GeneratedTypeCategory>()
      .multiple(GeneratedTypeCategory.values().toList())

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

  abstract val typeRegistry: TypeRegistry
  abstract fun generatorFactory(document: Document): Generator

  override fun run() {

    println("Generating ${this.outputCategories} types")

    files.forEach { file ->

      println("Processing $file")

      val document = parseAndValidate(file.toURI())

      val generator = generatorFactory(document)

      generator.generateServiceTypes()
    }

    typeRegistry.generateFiles(outputCategories.toSet(), outputDirectory.toPath())
  }

}
