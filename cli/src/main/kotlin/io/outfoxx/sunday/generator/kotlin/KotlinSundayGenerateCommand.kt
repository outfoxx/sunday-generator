package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.Document
import io.outfoxx.sunday.generator.GenerationMode

class KotlinSundayGenerateCommand :
  KotlinGenerateCommand(name = "kotlin/sunday", help = "Generate Kotlin client for Sunday framework") {

  override val mode = GenerationMode.Client

  override fun generatorFactory(document: Document, typeRegistry: KotlinTypeRegistry) =
    KotlinSundayGenerator(
      document,
      typeRegistry,
      servicePackageName ?: packageName,
      problemBaseUri,
      mediaTypes.toList()
    )

}
