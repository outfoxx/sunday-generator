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

/**
 * Equivalent source defaults across the supported frontends.
 * RAML currently omits model-property default metadata, so its read-side checks retain that behavior.
 */
internal fun modelDefaultsApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val schemas =
    """
    Mode: {type: string, enum: [fast, slow], default: fast}
    DefaultRecord:
      type: object
      required: [name]
      properties:
        name: {type: string, default: required}
        execution-mode: {type: string, default: fast}
        count: {type: integer, default: 3}
        enabled: {type: boolean, default: true}
        choice: {${'$'}ref: '#/components/schemas/Mode', default: fast}
    DefaultChild:
      allOf: [{${'$'}ref: '#/components/schemas/DefaultRecord'}]
      properties:
        local: {type: string, default: child}
    """.trimIndent()
  val openapi = OpenApiReferenceDocuments.document("Model defaults", schemas)
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Model defaults, version: 1.0.0}
    channels:
      records:
        address: /records
        messages:
          record: {payload: {${'$'}ref: '#/components/schemas/DefaultChild'}}
    operations:
      receiveRecord:
        action: receive
        channel: {${'$'}ref: '#/channels/records'}
        messages: [{${'$'}ref: '#/channels/records/messages/record'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val source = directory.resolve(if (frontend == "raml") "defaults.raml" else "defaults.yaml")
  source.writeText(
    when (frontend) {
      "raml" ->
        """
        #%RAML 1.0
        title: Model defaults
        types:
          Mode: {type: string, enum: [fast, slow], default: fast}
          DefaultRecord:
            type: object
            properties:
              name: {type: string, default: required}
              execution-mode?: {type: string, default: fast}
              count?: {type: integer, default: 3}
              enabled?: {type: boolean, default: true}
              choice?: {type: Mode, default: fast}
          DefaultChild:
            type: DefaultRecord
            properties:
              local?: {type: string, default: child}
        """.trimIndent()
      "asyncapi" -> asyncapi
      else -> openapi
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      """
      asyncapi: 3.0.0
      info: {title: Model defaults, version: 1.0.0}
      channels: {}
      operations: {}
      """.trimIndent(),
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
