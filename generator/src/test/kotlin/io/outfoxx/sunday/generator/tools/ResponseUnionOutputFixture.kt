package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Native response unions whose declarations can be emitted separately from their service facades. */
internal class ResponseUnionOutputFixture(
  directory: Path,
  kind: String,
  unionBody: Boolean = true,
) {
  val api: GeneratedApi

  init {
    val schemas =
      """
      components:
        schemas:
          First:
            type: object
            required: [first]
            properties: {first: {type: string}}
          Second:
            type: object
            required: [second]
            properties: {second: {type: string}}
      """.trimIndent()
    val union =
      if (unionBody) {
        """
        anyOf:
          - {${'$'}ref: '#/components/schemas/First'}
          - {${'$'}ref: '#/components/schemas/Second'}
        """.trimIndent()
      } else {
        """
        type: object
        required: [value]
        properties: {value: {type: string}}
        """.trimIndent()
      }
    val openapi =
      """
      openapi: 3.1.0
      info: {title: Example, version: '1'}
      servers: [{url: 'https://example.test'}]
      paths:
        /items:
          get:
            operationId: getItem
            responses:
              '200':
                description: Item
                content:
                  application/json:
                    schema:
      SCHEMA
      """.trimIndent().replace("SCHEMA", union.prependIndent("                ")) + "\n" + schemas
    val asyncapi =
      """
      asyncapi: 3.0.0
      info: {title: Example, version: '1'}
      channels:
        items:
          address: /items
          messages:
            item:
              payload:
      SCHEMA
      operations:
        getItem:
          action: receive
          channel: {${'$'}ref: '#/channels/items'}
      """.trimIndent().replace("SCHEMA", union.prependIndent("          ")) + "\n" + schemas
    val source =
      when (kind) {
        "raml" ->
          """
          #%RAML 1.0
          title: Example
          types:
            First: {type: object, properties: {first: string}}
            Second: {type: object, properties: {second: string}}
          /items:
            get:
              displayName: getItem
              responses:
                200:
                  body:
                    application/json: ${if (unionBody) "First | Second" else "{type: object, properties: {value: string}}"}
          """.trimIndent()
        "asyncapi2" ->
          """
          asyncapi: 2.6.0
          info: {title: Example, version: '1'}
          channels:
            /items:
              subscribe:
                operationId: getItem
                message:
                  payload:
          SCHEMA
          """.trimIndent().replace("SCHEMA", union.prependIndent("          ")) + "\n" + schemas
        "asyncapi" -> asyncapi
        else -> openapi
      }
    val input = Files.writeString(directory.resolve(if (kind == "raml") "api.raml" else "api.yaml"), source)
    val inputs = mutableListOf(input.toUri())
    if (kind == "composed") {
      // A second source contributes to the same service without duplicating model declarations.
      inputs +=
        Files
          .writeString(
            directory.resolve("extra.yaml"),
            """
            openapi: 3.1.0
            info: {title: Example, version: '1'}
            servers: [{url: 'https://example.test'}]
            paths:
              /other:
                get:
                  operationId: other
                  responses:
                    '204': {description: Done}
            """.trimIndent(),
          ).toUri()
    }
    val placement =
      Files.writeString(
        directory.resolve("placement.yaml"),
        """
        defaults:
          kotlin:
            package: example
            models: {package: example.model}
            services: {package: example.api}
          swift:
            models: {module: SharedModels}
            services: {module: SundayGenTest}
        """.trimIndent(),
      )
    api =
      GeneratedApiIrExporter().export(inputs).let {
        GeneratedApiYaml.readString(
          GeneratedApiYaml.writeString(
            it.copy(placementConfiguration = GeneratedPlacementConfiguration.read(placement)),
          ),
        )
      }
  }
}
