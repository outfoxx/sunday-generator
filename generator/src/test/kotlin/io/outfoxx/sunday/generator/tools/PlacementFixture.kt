package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import java.nio.file.Files
import java.nio.file.Path

/** Source-owned placement fixture with a cross-document enum reference. */
internal class PlacementFixture(
  directory: Path,
  kind: String,
) {
  val api: GeneratedApi

  init {
    OutputSelectionFixture(directory, kind)

    val sourcePath = directory.resolve(if (kind == "raml") "api.raml" else "api.yaml")
    val sourceText = Files.readString(sourcePath)
    Files.writeString(
      sourcePath,
      when (kind) {
        "raml" ->
          sourceText +
            """

            /items:
              get:
                displayName: getItems
                responses:
                  200:
                    body:
                      application/json: Item
            """.trimIndent().let {
              "\n$it\n"
            }
        "asyncapi" ->
          sourceText.replace(
            "channels: {}\noperations: {}",
            """
            channels:
              items:
                address: /items
                messages:
                  item:
                    payload: {${'$'}ref: '#/components/schemas/Item'}
            operations:
              receiveItems:
                action: receive
                channel: {${'$'}ref: '#/channels/items'}
            """.trimIndent(),
          )
        else ->
          sourceText.replace(
            "paths: {}",
            """
            paths:
              /items:
                get:
                  operationId: getItems
                  responses:
                    '200':
                      description: Item
                      content:
                        application/json:
                          schema: {${'$'}ref: '#/components/schemas/Item'}
            """.trimIndent(),
          )
      },
    )

    fun fields(shared: Boolean): String {
      val name = if (shared) "shared" else "consumer"
      return "  kotlin: {package: example.$name, services: {package: example.services}}\n" +
        "  python: {package: example_$name, models: {module: models}, services: {package: example_services}}\n" +
        "  typescript: {module: $name, services: {module: services}}\n"
    }
    if (kind == "raml") {
      javaClass.getResourceAsStream("/sunday.raml")!!.use { Files.copy(it, directory.resolve("sunday.raml")) }
      val owner = directory.resolve("common.raml")
      Files.writeString(
        owner,
        Files.readString(owner).replace(
          "types:",
          "uses:\n  sunday: sunday.raml\n(sunday.placement):\n" + fields(true) + "types:",
        ),
      )
      val input = directory.resolve("api.raml")
      Files.writeString(
        input,
        Files.readString(input).replace(
          "uses:",
          "(sunday.placement):\n" + fields(false) + "uses:\n  sunday: sunday.raml",
        ),
      )
    } else {
      Files.list(directory).use { files ->
        files.filter { it.toString().endsWith(".yaml") }.forEach { file ->
          Files.writeString(
            file,
            Files.readString(file) + "\nx-sunday-placement:\n" + fields(file.fileName.toString() == "common.yaml"),
          )
        }
      }
    }
    if (kind == "composed") {
      val events = directory.resolve("events.yaml")
      val text = Files.readString(events)
      Files.writeString(
        events,
        text.substringBefore("    Item:") +
          "    Item: {${'$'}ref: './api.yaml#/components/schemas/Item'}\n" +
          "x-sunday-placement:" + text.substringAfter("x-sunday-placement:"),
      )
    }
    val inputs =
      if (kind ==
        "composed"
      ) {
        listOf("api.yaml", "events.yaml")
      } else {
        listOf(if (kind == "raml") "api.raml" else "api.yaml")
      }
    api = GeneratedApiIrExporter().export(inputs.map { directory.resolve(it).toUri() })
  }
}
