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
import java.nio.file.Files
import java.nio.file.Path

/** Open objects, typed extensions and inherited closure shared by all target runtime tests. */
internal fun openModelFieldsApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val raml =
    """
    #%RAML 1.0
    title: Open Fields
    types:
      Notice:
        type: object
        properties:
          id: string
          data: NoticeData
          typed?: TypedRecord
          closed?: ClosedRecord
          child?: ClosedChild
          extended?: ExtendedRecord
      NoticeData:
        type: object
        properties:
          name: string
          additionalProperties: string
      BaseRecord:
        type: object
        properties:
          id: string
      ExtendedRecord:
        type: BaseRecord
        properties:
          kind: string
      ClosedRecord:
        type: BaseRecord
        additionalProperties: false
      ClosedChild:
        type: BaseRecord
        additionalProperties: false
        properties:
          name: string
      TypedRecord:
        type: object
        properties:
          id: string
          /^future$/: integer
    /notice:
      get:
        responses:
          200:
            body:
              application/json: Notice
    """.trimIndent()
  val schemas =
    """
    components:
      schemas:
        Notice:
          type: object
          required: [id, data]
          properties:
            id: {type: string}
            data: {${'$'}ref: '#/components/schemas/NoticeData'}
            typed: {${'$'}ref: '#/components/schemas/TypedRecord'}
            closed: {${'$'}ref: '#/components/schemas/ClosedRecord'}
            child: {${'$'}ref: '#/components/schemas/ClosedChild'}
            extended: {${'$'}ref: '#/components/schemas/ExtendedRecord'}
        NoticeData:
          type: object
          required: [name, additionalProperties]
          properties:
            name: {type: string}
            additionalProperties: {type: string}
        BaseRecord:
          type: object
          required: [id]
          properties:
            id: {type: string}
        ExtendedRecord:
          allOf: [{${'$'}ref: '#/components/schemas/BaseRecord'}]
          type: object
          required: [id, kind]
          properties:
            id: {type: string}
            kind: {type: string}
        ClosedRecord:
          type: object
          additionalProperties: false
          required: [id]
          properties:
            id: {type: string}
        ClosedChild:
          allOf: [{${'$'}ref: '#/components/schemas/BaseRecord'}]
          type: object
          additionalProperties: false
          required: [id, name]
          properties:
            id: {type: string}
            name: {type: string}
        TypedRecord:
          type: object
          additionalProperties: {type: integer, const: 2}
          required: [id]
          properties:
            id: {type: string}
    """.trimIndent()
  val prefix =
    if (frontend == "asyncapi") {
      """
      asyncapi: 3.0.0
      channels:
        notices:
          address: /notices
          messages:
            notice: {payload: {${'$'}ref: '#/components/schemas/Notice'}}
      operations:
        receiveNotice:
          action: receive
          channel: {${'$'}ref: '#/channels/notices'}
          messages: [{${'$'}ref: '#/channels/notices/messages/notice'}]
      """.trimIndent()
    } else {
      """
      openapi: 3.1.0
      paths:
        /notice:
          get:
            operationId: getNotice
            responses:
              '200':
                description: Notice
                content:
                  application/json:
                    schema: {${'$'}ref: '#/components/schemas/Notice'}
      """.trimIndent()
    }
  val source = directory.resolve(if (frontend == "raml") "models.raml" else "models.yaml")
  Files.writeString(
    source,
    if (frontend == "raml") {
      raml
    } else {
      "$prefix\ninfo: {title: Open Fields, version: 1.0.0}\n$schemas"
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    Files.writeString(events, "asyncapi: 3.0.0\ninfo: {title: Open Fields, version: 1.0.0}\nchannels: {}")
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}

/** Includes explicit nulls and dictionaries nested inside arrays to detect lossy JSON encoding. */
internal val openModelWire =
  """{"id":"one","data":{"name":"name","additionalProperties":"declared","future":null,"nested":{"unknown":[1,null,{"x":true}]}},"future":{"nested":null}}"""
