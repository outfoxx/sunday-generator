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

/** Shared constant-tag models for compile-backed construction and decoding regressions. */
internal fun constantDiscriminatorsApi(
  directory: Path,
  frontend: String = "openapi",
  discriminator: String = "kind",
  receiverField: Boolean = false,
): GeneratedApi {
  val source = directory.resolve("constants.yaml")
  source.writeText(
    """
    openapi: 3.1.0
    info: {title: Constants, version: 1.0.0}
    paths:
      /extent:
        post:
          operationId: updateExtent
          requestBody:
            content:
              application/json:
                schema: {${'$'}ref: '#/components/schemas/Extent'}
          responses:
            '200':
              description: Extent
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/Extent'}
    components:
      schemas:
        OnlyConstants:
          type: object
          required: [tag-value, zero, flag]
          properties:
            tag-value: {type: string, const: fixed}
            zero: {type: integer, const: 0}
            flag: {type: boolean, const: false}
            optional: {type: string, const: fixed}
        BeatExtent:
          type: object
          required: [kind]
          properties:
            kind: {type: string, const: beat}
            beats: {type: integer}
            ${if (receiverField) "self: {type: string}" else ""}
            constants: {${'$'}ref: '#/components/schemas/OnlyConstants'}
        TimeExtent:
          type: object
          required: [kind]
          properties:
            kind: {type: string, const: time}
            seconds: {type: number}
        Extent:
          oneOf:
            - {${'$'}ref: '#/components/schemas/BeatExtent'}
            - {${'$'}ref: '#/components/schemas/TimeExtent'}
          discriminator:
            propertyName: kind
            mapping:
              beat: '#/components/schemas/BeatExtent'
              time: '#/components/schemas/TimeExtent'
    """.trimIndent().replace("kind", discriminator),
  )
  if (frontend == "openapi") return GeneratedApiIrExporter().export(source.toUri())
  val events = directory.resolve("events.yaml")
  events.writeText(
    """
    asyncapi: 3.0.0
    info: {title: Constants, version: 1.0.0}
    channels:
      extents:
        address: /extents
        messages:
          extent: {payload: {${'$'}ref: '#/components/schemas/Extent'}}
    operations:
      receiveExtent:
        action: receive
        channel: {${'$'}ref: '#/channels/extents'}
        messages: [{${'$'}ref: '#/channels/extents/messages/extent'}]
    """.trimIndent() + "\ncomponents:\n" +
      java.nio.file.Files
        .readString(source)
        .substringAfter("components:\n"),
  )
  if (frontend == "composed") {
    source.writeText("openapi: 3.1.0\ninfo: {title: Constants, version: 1.0.0}\npaths: {}\n")
  }
  return GeneratedApiIrExporter().export(
    if (frontend ==
      "composed"
    ) {
      listOf(source.toUri(), events.toUri())
    } else {
      listOf(events.toUri())
    },
  )
}
