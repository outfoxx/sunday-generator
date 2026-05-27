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

import io.outfoxx.sunday.generator.common.APIProcessor
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exports RAML source documents into Sunday generated API IR.
 */
class RamlIrExporter(
  private val apiProcessor: APIProcessor = APIProcessor(),
  private val mapper: RamlToGeneratedApi = RamlToGeneratedApi(),
) {

  /** Processes a RAML source URI and returns the generated API IR. */
  fun export(sourceUri: URI): GeneratedApi {
    val result = apiProcessor.process(sourceUri)
    require(result.isValid) {
      result.validationLog.joinToString("\n") {
        "${it.level.toString().lowercase()}| ${it.file}:${it.line}: ${it.message}"
      }
    }
    return mapper.convert(result)
  }

  /** Processes a RAML source URI and returns the generated API IR as YAML. */
  fun exportYaml(sourceUri: URI): String = GeneratedApiYaml.writeString(export(sourceUri))

  /** Processes a RAML source URI and writes the generated API IR YAML to a file. */
  fun writeYaml(
    sourceUri: URI,
    outputPath: Path,
  ): GeneratedApi {
    val api = export(sourceUri)
    outputPath.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(outputPath, GeneratedApiYaml.writeString(api))
    return api
  }
}
