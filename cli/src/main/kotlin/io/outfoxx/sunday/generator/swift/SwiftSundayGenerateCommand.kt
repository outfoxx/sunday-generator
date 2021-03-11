package io.outfoxx.sunday.generator.swift

import amf.client.model.document.Document

class SwiftSundayGenerateCommand :
  SwiftGenerateCommand(name = "swift/sunday", help = "Generate Swift client for Sunday framework") {

  override fun generatorFactory(document: Document, typeRegistry: SwiftTypeRegistry) =
    SwiftSundayGenerator(
      document,
      typeRegistry,
      problemBaseUri,
      mediaTypes.toList()
    )

}
