package io.outfoxx.sunday.generator.typescript

import amf.client.model.document.Document

class TypeScriptSundayGenerateCommand :
  TypeScriptGenerateCommand(name = "typescript/sunday", help = "Generate TypeScript client for Sunday framework") {

  override fun generatorFactory(document: Document, typeRegistry: TypeScriptTypeRegistry) =
    TypeScriptSundayGenerator(
      document,
      typeRegistry,
      problemBaseUri,
      mediaTypes.toList()
    )

}
