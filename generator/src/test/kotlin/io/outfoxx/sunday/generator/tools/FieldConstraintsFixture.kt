/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import java.nio.file.Path
import kotlin.io.path.writeText

/** Ordinary field and scalar-alias restrictions shared by compiled runtime tests. */
internal fun fieldConstraintsApi(
  directory: Path,
  sourceKind: String,
): GeneratedApi {
  val source = directory.resolve("constraints.yaml")
  val document =
    OpenApiReferenceDocuments.document(
      "Field constraints",
      """
      Identifier:
        type: string
        pattern: '^ID-[A-Z]+${'$'}'
      IdentifierAlias:
        ${'$'}ref: '#/components/schemas/Identifier'
        ${if (sourceKind == "asyncapi") "pattern: 'ABC${'$'}'" else ""}
      Probe:
        type: object
        required: [name]
        properties:
          name: {type: string, minLength: 2, maxLength: 5, pattern: '^[a-z]+${'$'}'}
          tags: {type: array, minItems: 1, maxItems: 2, items: {type: string}}
          count: {type: integer, minimum: 0, maximum: 10}
          id: {${'$'}ref: '#/components/schemas/IdentifierAlias'}
      """.trimIndent(),
    )
  val schemas = document.substringAfter("schemas:\n")
  when (sourceKind) {
    "raml" -> {
      source.writeText(
        """
        #%RAML 1.0
        title: Field constraints
        types:
          Identifier:
            type: string
            pattern: '^ID-[A-Z]+${'$'}'
          IdentifierAlias: Identifier
          Probe:
            type: object
            properties:
              name: {type: string, minLength: 2, maxLength: 5, pattern: '^[a-z]+${'$'}'}
              tags?: {type: array, minItems: 1, maxItems: 2, items: string}
              count?: {type: integer, minimum: 0, maximum: 10}
              id?: IdentifierAlias
          NumericProbe:
            type: object
            properties:
              samples:
                type: array
                items: {type: integer, minimum: 0, maximum: 10}
              optionalSamples?:
                type: array
                items: {type: integer, minimum: 0, maximum: 10}
        """.trimIndent(),
      )
      val raml = directory.resolve("constraints.raml")
      java.nio.file.Files
        .move(source, raml)
      return GeneratedApiIrExporter().export(listOf(raml.toUri()))
    }
    "asyncapi" ->
      source.writeText(
        """
        asyncapi: 3.0.0
        info: {title: Field constraints, version: 1.0.0}
        channels:
          probes:
            address: /probes
            messages:
              probe:
                payload: {${'$'}ref: '#/components/schemas/Probe'}
        operations:
          receiveProbe:
            action: receive
            channel: {${'$'}ref: '#/channels/probes'}
            messages:
              - {${'$'}ref: '#/channels/probes/messages/probe'}
        components:
          schemas:
        """.trimIndent() + "\n" + schemas,
      )
    else -> source.writeText(document)
  }
  val sources = mutableListOf(source.toUri())
  if (sourceKind == "composed") {
    val extra = directory.resolve("extra.yaml")
    extra.writeText("asyncapi: 3.0.0\ninfo: {title: Field constraints, version: 1.0.0}\nchannels: {}\n")
    sources += extra.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
