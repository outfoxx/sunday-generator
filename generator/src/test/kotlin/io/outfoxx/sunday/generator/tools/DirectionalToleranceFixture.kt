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

/** Shared request, response, and event schemas for compiler-backed directional tolerance tests. */
internal fun directionalToleranceApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val schemas =
    """
    State: {type: string, enum: [active, unknown], x-unknown-value: unknown, x-sunday-tolerance: response}
    OpenState: {type: string, enum: [active, unknown], x-unknown-value: unknown, x-sunday-tolerance: all}
    Item:
      type: object
      required: [state]
      properties:
        state: {${'$'}ref: '#/components/schemas/State'}
        openState: {${'$'}ref: '#/components/schemas/OpenState'}
        event: {${'$'}ref: '#/components/schemas/Event'}
        next: {${'$'}ref: '#/components/schemas/Item'}
        states: {type: array, items: {${'$'}ref: '#/components/schemas/State'}}
    Event:
      oneOf: [{${'$'}ref: '#/components/schemas/Created'}]
      discriminator: {propertyName: kind, mapping: {created: '#/components/schemas/Created'}}
      x-sunday-tolerance: response
      properties:
        kind: {type: string, minLength: 2, pattern: "^[a-z]+$"}
        note: {type: string, minLength: 2}
        state: {${'$'}ref: '#/components/schemas/State'}
    Created:
      type: object
      required: [kind, count]
      properties:
        kind: {type: string, const: created}
        count: {type: integer, minimum: 1}
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Directional tolerance, version: 1.0.0}
    paths:
      /items:
        put:
          operationId: updateItem
          requestBody:
            required: true
            content:
              application/json:
                schema: {${'$'}ref: '#/components/schemas/Item'}
          responses:
            '200':
              description: Item
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/Item'}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Directional tolerance, version: 1.0.0}
    channels:
      items:
        address: /items
        messages:
          item: {payload: {${'$'}ref: '#/components/schemas/Item'}}
    operations:
      receiveItem:
        action: receive
        channel: {${'$'}ref: '#/channels/items'}
        messages: [{${'$'}ref: '#/channels/items/messages/item'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val raml =
    """
    #%RAML 1.0
    title: Directional tolerance
    annotationTypes:
      sunday.unknownValue: {type: string, allowedTargets: [TypeDeclaration]}
      sunday.tolerance: {type: string, enum: [response, all], allowedTargets: [TypeDeclaration]}
    types:
      State: {type: string, enum: [active, unknown], (sunday.unknownValue): unknown, (sunday.tolerance): response}
      OpenState: {type: string, enum: [active, unknown], (sunday.unknownValue): unknown, (sunday.tolerance): all}
      Item:
        type: object
        properties:
          state: State
          openState?: OpenState
          event?: Event
          next?: Item
          states?: State[]
      Event:
        type: object
        discriminator: kind
        (sunday.tolerance): response
        properties:
          kind: {type: string, minLength: 2, pattern: "^[a-z]+$"}
          note?: {type: string, minLength: 2}
          state?: State
      Created:
        type: Event
        discriminatorValue: created
        properties:
          count: {type: integer, minimum: 1}
    /items:
      put:
        body:
          application/json: Item
        responses:
          200:
            body:
              application/json: Item
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "api.raml" else "api.yaml")
  source.writeText(
    when (frontend) {
      "raml" -> raml
      "asyncapi" -> asyncapi
      else -> openapi
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      "asyncapi: 3.0.0\ninfo: {title: Directional tolerance, version: 1.0.0}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
