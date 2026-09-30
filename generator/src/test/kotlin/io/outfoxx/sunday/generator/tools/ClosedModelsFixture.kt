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

/** Closed and open objects shared by compile-backed decoding tests for every source frontend. */
internal fun closedModelsApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val openapi = requireNotNull(GeneratedApi::class.java.getResource("/openapi/ir/closed-models.yaml")).readText()
  val source = directory.resolve(if (frontend == "raml") "models.raml" else "models.yaml")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Closed models, version: 1.0.0}
    channels:
      records:
        address: /records
        messages:
          record: {payload: {${'$'}ref: '#/components/schemas/ClosedRecord'}}
    operations:
      receiveRecord:
        action: receive
        channel: {${'$'}ref: '#/channels/records'}
        messages: [{${'$'}ref: '#/channels/records/messages/record'}]
    """.trimIndent() + "\ncomponents:\n" + openapi.substringAfter("components:\n")
  source.writeText(
    when (frontend) {
      "raml" ->
        """
        #%RAML 1.0
        title: Closed models
        types:
          ClosedRecord:
            type: object
            additionalProperties: false
            properties:
              display-name: string
              nested?: ClosedRecord
              child?: ClosedChild
              empty?: EmptyClosed
              choice?: Choice
          Cat:
            type: object
            additionalProperties: false
            properties:
              kind: {type: string, pattern: '^cat${'$'}'}
              name: string
          Dog:
            type: object
            additionalProperties: false
            properties:
              kind: {type: string, pattern: '^dog${'$'}'}
              name: string
          Choice: Cat | Dog
          OpenRecord:
            type: object
            properties:
              name?: string
          ClosedChild:
            type: OpenRecord
            additionalProperties: false
            properties:
              count?: integer
          EmptyClosed:
            type: object
            additionalProperties: false
        /closed:
          post:
            body:
              application/json: ClosedRecord
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
      info: {title: Closed models, version: 1.0.0}
      channels:
        records:
          address: /events
          messages:
            event: {payload: {${'$'}ref: '#/components/schemas/EventRecord'}}
      operations:
        receiveEvent:
          action: receive
          channel: {${'$'}ref: '#/channels/records'}
          messages: [{${'$'}ref: '#/channels/records/messages/event'}]
      components:
        schemas:
          EventRecord:
            type: object
            additionalProperties: false
            properties:
              name: {type: string}
      """.trimIndent(),
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
