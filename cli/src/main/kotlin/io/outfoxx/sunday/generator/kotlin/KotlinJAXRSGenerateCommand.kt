package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.Document
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase

class KotlinJAXRSGenerateCommand :
  KotlinGenerateCommand(name = "kotlin/jaxrs", help = "Generate Kotlin for JAX-RS framework") {

  override val mode
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

  override fun generatorFactory(document: Document, typeRegistry: KotlinTypeRegistry) =
    KotlinJAXRSGenerator(
      document,
      typeRegistry,
      reactiveResponseType,
      servicePackageName ?: packageName,
      problemBaseUri,
      mediaTypes.toList()
    )

}
