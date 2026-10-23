package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedService
import java.nio.file.Files
import java.nio.file.Path

/** Independent patch pairs expose shared-support ownership without cross-model dependencies. */
internal class SharedHelpersFixture(
  directory: Path,
  kind: String,
  split: Boolean = false,
  collision: Boolean = false,
) {
  private val first = write(directory, if (kind == "composed") "openapi" else kind, "FirstUpdate", collision)
  private val second = write(directory, if (kind == "composed") "asyncapi" else kind, "SecondUpdate", false)

  val api: GeneratedApi =
    GeneratedApiIrExporter().export(listOf(first.toUri(), second.toUri())).let { api ->
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            defaults = placement(false),
            relocations =
              if (split) {
                listOf(
                  GeneratedPlacementConfiguration.Relocation(second.toUri().toString(), placement = placement(true)),
                )
              } else {
                emptyList()
              },
          ),
      )
    }

  fun selection(owner: String): GeneratedOutputSelection =
    when (owner) {
      "first" -> GeneratedOutputSelection(ownerUris = setOf(first.toUri()))
      "second" -> GeneratedOutputSelection(ownerUris = setOf(second.toUri()))
      "neither" -> GeneratedOutputSelection(ownerUris = setOf(first.resolveSibling("excluded.yaml").toUri()))
      else -> GeneratedOutputSelection()
    }

  fun withHelperCollision(
    kind: String,
    name: String,
  ): GeneratedApi =
    if (kind == "problem") {
      api.copy(
        problems =
          listOf(
            GeneratedProblem(
              name,
              source = api.source,
              typeUri = "https://test/problem",
              status = 400,
              title = "Test problem",
              detail = "A problem used to reserve its generated name",
            ),
          ),
      )
    } else {
      api.copy(
        services =
          (if (kind == "service") listOf(name) else listOf("FirstService", "SecondService")).map {
            GeneratedService(
              it,
              group = it,
              operations = listOf(GeneratedOperation("fetch$it", "GET", "/${it.lowercase()}")),
            )
          },
      )
    }

  private fun placement(second: Boolean) =
    GeneratedPlacement(
      kotlin = GeneratedPlacement.Scope(packageName = if (second) "io.other" else "io.test"),
      typescript = GeneratedPlacement.Scope(module = if (second) "other" else null),
      python = GeneratedPlacement.Scope(packageName = if (second) "other_sdk" else "test_sdk"),
      swift = GeneratedPlacement.Scope(module = if (second) "SharedModels" else null),
    )

  private fun write(
    directory: Path,
    kind: String,
    name: String,
    collision: Boolean,
  ): Path {
    val source =
      when (kind) {
        "raml" ->
          """
          #%RAML 1.0
          title: Shared helpers
          annotationTypes:
            sunday.patchable: {type: boolean, allowedTargets: [TypeDeclaration]}
          types:
            $name:
              type: object
              (sunday.patchable): true
              properties:
                value: {type: string, required: false, default: initial}
          """.trimIndent() +
            if (collision) {
              "\n  MergePatchSupport: {type: object, properties: {reserved: string}}\n" +
                "  ModelCodingSupport: {type: object, properties: {reserved: string}}\n"
            } else {
              "\n"
            }
        else ->
          (
            if (kind ==
              "asyncapi"
            ) {
              "asyncapi: 3.0.0\nchannels: {}\noperations: {}"
            } else {
              "openapi: 3.1.0\npaths: {}"
            }
          ) +
            "\n" +
            """
            info: {title: Shared helpers, version: '1'}
            components:
              schemas:
                $name:
                  type: object
                  x-sunday-patchable: true
                  properties:
                    value: {type: string, default: initial}
            """.trimIndent() +
            if (collision) {
              "\n    MergePatchSupport: {type: object, properties: {reserved: {type: string}}}\n" +
                "    ModelCodingSupport: {type: object, properties: {reserved: {type: string}}}\n"
            } else {
              "\n"
            }
      }
    return Files.writeString(directory.resolve("$name.${if (kind == "raml") "raml" else "yaml"}"), source)
  }
}
