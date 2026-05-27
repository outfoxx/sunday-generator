/*
 * Copyright 2020 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.ir

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.common.APIProcessor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports source API documents into Sunday generated API IR.
 */
class GeneratedApiIrExporter(
  private val options: GeneratedApiIrOptions = GeneratedApiIrOptions(),
  private val ramlProcessor: APIProcessor = APIProcessor(),
  private val ramlMapper: RamlToGeneratedApi = RamlToGeneratedApi(options = options),
  private val openApiMapper: OpenApiToGeneratedApi = OpenApiToGeneratedApi(options),
  private val asyncApiMapper: AsyncApiToGeneratedApi = AsyncApiToGeneratedApi(options),
  private val composer: GeneratedApiComposer = GeneratedApiComposer(),
) {

  /** Processes one source URI and returns generated API IR. */
  fun export(
    sourceUri: URI,
    sourceKind: GeneratedApiIrSourceKind = GeneratedApiIrSourceKind.AUTO,
  ): GeneratedApi = export(listOf(GeneratedApiIrSource(sourceUri, sourceKind)))

  /** Processes source URIs and returns composed generated API IR. */
  fun export(
    sourceUris: List<URI>,
    sourceKind: GeneratedApiIrSourceKind = GeneratedApiIrSourceKind.AUTO,
  ): GeneratedApi = export(sourceUris.map { sourceUri -> GeneratedApiIrSource(sourceUri, sourceKind) })

  /** Processes source documents and returns composed generated API IR. */
  fun export(sources: List<GeneratedApiIrSource>): GeneratedApi {
    require(sources.isNotEmpty()) {
      "At least one source document is required"
    }
    return exportWithIdentity(sources).api
  }

  /** Processes source documents and returns composed generated API IR with its API identity. */
  fun exportWithIdentity(sources: List<GeneratedApiIrSource>): GeneratedApiIrExport {
    require(sources.isNotEmpty()) {
      "At least one source document is required"
    }
    val fragments = sources.map(::exportFragment)
    return GeneratedApiIrExport(composer.compose(fragments), fragments.first().apiId)
  }

  /** Processes source URIs and returns generated API IR as YAML. */
  fun exportYaml(
    sourceUris: List<URI>,
    sourceKind: GeneratedApiIrSourceKind = GeneratedApiIrSourceKind.AUTO,
  ): String = GeneratedApiYaml.writeString(export(sourceUris, sourceKind))

  /** Processes source URIs and writes the generated API IR YAML to a file. */
  fun writeYaml(
    sourceUris: List<URI>,
    outputPath: Path,
    sourceKind: GeneratedApiIrSourceKind = GeneratedApiIrSourceKind.AUTO,
  ): GeneratedApi {
    val api = export(sourceUris, sourceKind)
    outputPath.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(outputPath, GeneratedApiYaml.writeString(api))
    return api
  }

  private fun exportFragment(source: GeneratedApiIrSource): GeneratedApiFragment {
    val sourceKind = source.kind.resolve(source.uri)
    if (sourceKind == GeneratedApiIrSourceKind.ASYNCAPI) {
      return asyncApiMapper.convertFragment(source.uri)
    }
    if (sourceKind == GeneratedApiIrSourceKind.OPENAPI) {
      return openApiMapper.convertFragment(source.uri)
    }
    val result =
      when (sourceKind) {
        GeneratedApiIrSourceKind.RAML -> ramlProcessor.process(source.uri)
        GeneratedApiIrSourceKind.OPENAPI -> error("OpenAPI sources are handled by the native OpenAPI mapper")
        GeneratedApiIrSourceKind.ASYNCAPI -> error("AsyncAPI sources are handled by the native AsyncAPI mapper")
        GeneratedApiIrSourceKind.AUTO -> error("AUTO source kind must be resolved before processing")
      }
    requireValid(result)
    return when (sourceKind) {
      GeneratedApiIrSourceKind.RAML -> ramlMapper.convertFragment(result)
      GeneratedApiIrSourceKind.OPENAPI -> error("OpenAPI sources are handled by the native OpenAPI mapper")
      GeneratedApiIrSourceKind.ASYNCAPI -> error("AsyncAPI sources are handled by the native AsyncAPI mapper")
      GeneratedApiIrSourceKind.AUTO -> error("AUTO source kind must be resolved before mapping")
    }
  }

  private fun GeneratedApiIrSourceKind.resolve(sourceUri: URI): GeneratedApiIrSourceKind =
    if (this == GeneratedApiIrSourceKind.AUTO) detectSourceKind(sourceUri) else this

  private fun detectSourceKind(sourceUri: URI): GeneratedApiIrSourceKind {
    val text = sourceUri.toURL().readText()
    if (text.trimStart().startsWith("#%RAML")) {
      return GeneratedApiIrSourceKind.RAML
    }

    val root = yamlMapper.readTree(text)
    return when {
      root.has("openapi") -> GeneratedApiIrSourceKind.OPENAPI
      root.has("asyncapi") -> GeneratedApiIrSourceKind.ASYNCAPI
      else ->
        throw IllegalArgumentException(
          "Unable to detect source kind for '$sourceUri'. Use --source raml, openapi, or asyncapi.",
        )
    }
  }

  private fun requireValid(result: APIProcessor.Result) {
    require(result.isValid) {
      result.validationLog.joinToString("\n") {
        "${it.level.toString().lowercase()}| ${it.file}:${it.line}: ${it.message}"
      }
    }
  }

  companion object {
    private val yamlMapper = ObjectMapper(YAMLFactory())
  }
}
