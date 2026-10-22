package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import java.nio.file.Files
import java.nio.file.Path

/** Owner-only fixture with a resolved consumer model in every supported source frontend. */
internal class OutputSelectionFixture(
  directory: Path,
  kind: String,
) {
  val api: GeneratedApi
  val selection: GeneratedOutputSelection

  init {
    val raml = kind == "raml"
    val owner = directory.resolve(if (raml) "common.raml" else "common.yaml")
    Files.writeString(
      owner,
      if (raml) {
        """
        #%RAML 1.0 Library
        types:
          Shared:
            type: string
            enum: [one, two]
        """.trimIndent()
      } else {
        """
        openapi: 3.1.0
        info: {title: Example, version: '1'}
        paths: {}
        components:
          schemas:
            Shared: {type: string, enum: [one, two]}
        """.trimIndent()
      },
    )
    val input = directory.resolve(if (raml) "api.raml" else "api.yaml")
    val schemas =
      """
      components:
        schemas:
          Shared: {${'$'}ref: './common.yaml#/components/schemas/Shared'}
          Item:
            type: object
            properties:
              shared: {${'$'}ref: '#/components/schemas/Shared'}
      """.trimIndent()
    val openapi =
      """
      openapi: 3.1.0
      x-sunday-apiId: example
      info: {title: Example, version: '1'}
      paths: {}
      """.trimIndent() + "\n" + schemas
    val asyncapi =
      """
      asyncapi: 3.0.0
      x-sunday-apiId: example
      info: {title: Example, version: '1'}
      channels: {}
      operations: {}
      """.trimIndent() + "\n" + schemas
    Files.writeString(
      input,
      when (kind) {
        "raml" ->
          """
          #%RAML 1.0
          title: Example
          uses:
            common: common.raml
          types:
            Item:
              type: object
              properties:
                shared?: common.Shared
          """.trimIndent()
        "asyncapi" -> asyncapi
        else -> openapi
      },
    )
    val inputs =
      if (kind == "composed") {
        listOf(input.toUri(), Files.writeString(directory.resolve("events.yaml"), asyncapi).toUri())
      } else {
        listOf(input.toUri())
      }
    api = GeneratedApiIrExporter().export(inputs)
    selection = GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model), setOf(owner.toUri()))
  }
}
