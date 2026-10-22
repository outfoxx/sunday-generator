package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Native declarations with configuration-file relocations, retained through serialized IR. */
internal class PlacementScopeFixture(
  directory: Path,
  kind: String,
) {
  val api: GeneratedApi

  init {
    val parsed = PlacementRegressionFixture(directory, kind).inline()
    val child = parsed.models.single { model -> model.properties.any { it.name == "value" } }
    val source = if (kind == "raml") "api.raml" else "api.yaml"
    val config = directory.resolve("placement.yaml")
    Files.writeString(
      config,
      """
      defaults:
        kotlin:
          models: {package: example.defaults}
          services: {package: example.services}
        swift:
          models: {module: SharedModels}
          services: {module: SundayGenTest}
        typescript:
          models: {module: defaults}
          services: {module: services}
        python:
          models: {package: default_sdk, module: models}
          services: {package: service_sdk}
      relocations:
        - select: {target: './$source#', kind: service}
          placement:
            kotlin:
              package: example.services
              models: {package: example.common}
              client:
                models: {package: example.owned}
              server:
                models: {package: example.serverowned}
            swift:
              module: SundayGenTest
              models: {module: SharedModels}
            typescript:
              module: services
              models: {module: owned}
            python:
              package: service_sdk
              models: {package: owned_sdk, module: models}
        - select: {target: './$source#${child.declarationUri!!.substringAfter('#')}', kind: model}
          placement:
            kotlin:
              package: example.common
              client: {package: example.exact}
              server: {package: example.serverexact}
            swift: {module: SharedModels}
            typescript: {module: exact}
            python: {package: exact_sdk, module: models}
      """.trimIndent(),
    )
    api =
      GeneratedApiYaml.readString(
        GeneratedApiYaml.writeString(
          parsed.copy(placementConfiguration = GeneratedPlacementConfiguration.read(config)),
        ),
      )
  }

  companion object {
    /** Problem declarations have native syntax in RAML and OpenAPI; composition also includes AsyncAPI. */
    fun problems(
      directory: Path,
      kind: String,
    ): GeneratedApi {
      val raml = kind == "raml"
      val source = directory.resolve(if (raml) "problems.raml" else "problems.yaml")
      if (raml) {
        PlacementRegressionFixture::class.java.getResourceAsStream("/sunday.raml")!!.use {
          Files.copy(it, directory.resolve("sunday.raml"))
        }
      }
      val header =
        if (raml) {
          "#%RAML 1.0\ntitle: Problems\nuses: {sunday: sunday.raml}"
        } else {
          "openapi: 3.1.0\ninfo: {title: Problems, version: '1'}\npaths: {}"
        }
      val extension = { name: String -> if (raml) "(sunday.$name)" else "x-sunday-$name" }
      Files.writeString(
        source,
        header + "\n" +
          """
          ${extension("apiId")}: problems
          ${extension("problemBaseUri")}: 'https://example.test/problems/'
          ${extension("problemTypes")}:
            failure: {status: 400, title: Failure, detail: Failed}
          """.trimIndent(),
      )
      val inputs = mutableListOf(source)
      if (kind == "composed") {
        val events = directory.resolve("events.yaml")
        Files.writeString(
          events,
          "asyncapi: 3.0.0\ninfo: {title: Problems, version: '1'}\nx-sunday-apiId: problems\nchannels: {}",
        )
        inputs.add(events)
      }
      val config = directory.resolve("placement.yaml")
      Files.writeString(
        config,
        """
        defaults:
          kotlin: {models: {package: example.models}}
        relocations:
          - select: {target: './${source.fileName}#', kind: problem}
            placement:
              kotlin: {package: example.problems}
              swift: {module: SharedModels}
              typescript: {module: problems}
              python: {package: problem_sdk, module: errors}
        """.trimIndent(),
      )
      return GeneratedApiYaml.readString(
        GeneratedApiYaml.writeString(
          GeneratedApiIrExporter()
            .export(
              inputs.map {
                it.toUri()
              },
            ).copy(placementConfiguration = GeneratedPlacementConfiguration.read(config)),
        ),
      )
    }
  }
}
