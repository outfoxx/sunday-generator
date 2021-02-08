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
import com.squareup.kotlinpoet.FileSpec
import io.outfoxx.sunday.generator.kotlin.KotlinJAXRSGenerator
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry

class GenerateCommand : CliktCommand() {

  override fun run() = Unit

}

abstract class KotlinGenerateCommand(name: String, help: String) : CliktCommand(name = name, help = help) {

  val packageName by option("-pkg", help = "Default package").required()
  val modelPackageName by option("-model-pkg", help = "Default model package, if not specified '-pkg' is used")
  val servicePackageName by option("-service-pkg", help = "Default service package, if not specified '-pkg' is used")
  val problemBaseUri by option("-problem-base", help = "Default problem base URI").default("http://example.com/")
  val mode by option().enum<GenerationMode> { it.name.camelCaseToKebabCase() }.default(GenerationMode.Client)
  val reactiveResponseType by option("-reactive")
  val options by option("-enable").enum<KotlinTypeRegistry.Option> { it.name.camelCaseToKebabCase() }.multiple()
  val mediaTypes by option("-media-type").multiple().unique()
  val outputDirectory by option("-out").file(mustExist = true, canBeFile = false, canBeDir = true).required()
  val files by argument().file(mustExist = true, canBeFile = true, canBeDir = false).multiple(required = true)

}

class KotlinJAXRSGenerateCommand : KotlinGenerateCommand(name = "kotlin/jaxrs", help = "Generate Kotlin for JAX-RS framework") {

  override fun run() {
    val typeRegistry =
      KotlinTypeRegistry(
        modelPackageName ?: packageName,
        mode,
        options.toSet(),
      )

    files.forEach { file ->

      val generator =
        KotlinJAXRSGenerator(
          parseAndValidate(file.toURI()),
          typeRegistry,
          reactiveResponseType,
          servicePackageName ?: packageName,
          problemBaseUri,
          mediaTypes.toList()
        )

      generator.generateServiceTypes()
    }

    typeRegistry.buildTypes()
      .filter { it.key.topLevelClassName() == it.key }
      .map { FileSpec.get(it.key.packageName, it.value) }
      .map { it.writeTo(outputDirectory) }
  }

}
