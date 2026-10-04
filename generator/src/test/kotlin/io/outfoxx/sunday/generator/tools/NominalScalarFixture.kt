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

/** Nominal scalars shared by compiler-backed tests for every supported source frontend. */
internal fun nominalScalarApi(
  frontend: String,
  directory: Path,
  parameterDefaults: Boolean = false,
): GeneratedApi {
  val schemas =
    """
    BaseFactSid: {type: string, pattern: '^sid:f:[A-Za-z0-9_-]+${'$'}', default: 'sid:f:default', x-sunday-wrapper-type: true}
    BaseLossSid: {type: string, pattern: '^sid:l:[A-Za-z0-9_-]+${'$'}', x-sunday-wrapper-type: true}
    BroadSid: {type: string, pattern: '^sid:', x-sunday-wrapper-type: true}
    PositiveCount: {type: integer, minimum: 1, x-sunday-wrapper-type: true}
    Ratio: {type: number, minimum: 0, maximum: 1, x-sunday-wrapper-type: true}
    Enabled: {type: boolean, x-sunday-wrapper-type: true}
    AnySid:
      oneOf:
        - {${'$'}ref: '#/components/schemas/BaseFactSid'}
        - {${'$'}ref: '#/components/schemas/BaseLossSid'}
    AmbiguousSid:
      oneOf:
        - {${'$'}ref: '#/components/schemas/BaseFactSid'}
        - {${'$'}ref: '#/components/schemas/BroadSid'}
    Defaults:
      type: object
      properties:
        fact: {${'$'}ref: '#/components/schemas/BaseFactSid', default: 'sid:f:default'}
    Record:
      type: object
      required: [fact, identifiers, count, ratio, enabled]
      properties:
        fact: {${'$'}ref: '#/components/schemas/BaseFactSid'}
        ambiguity: {${'$'}ref: '#/components/schemas/AmbiguousSid'}
        identifiers: {type: array, items: {${'$'}ref: '#/components/schemas/AnySid'}}
        count: {${'$'}ref: '#/components/schemas/PositiveCount'}
        ratio: {${'$'}ref: '#/components/schemas/Ratio'}
        enabled: {${'$'}ref: '#/components/schemas/Enabled'}
        defaults: {${'$'}ref: '#/components/schemas/Defaults'}
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Nominal scalars, version: 1.0.0}
    paths:
      /records/{id}:
        get:
          operationId: getRecord
          parameters:
            - {name: id, in: path, required: true, schema: {${'$'}ref: '#/components/schemas/AnySid'${if (parameterDefaults) ", default: 'sid:f:default'" else ""}}}
            - {name: fact, in: query, schema: {${'$'}ref: '#/components/schemas/BaseFactSid'}}
          responses:
            '200':
              description: Record
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/Record'}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Nominal scalars, version: 1.0.0}
    channels:
      records:
        address: /records
        ${if (parameterDefaults) "parameters: {id: {schema: {\$ref: '#/components/schemas/AnySid', default: 'sid:f:default'}}}" else ""}
        messages:
          record: {payload: {${'$'}ref: '#/components/schemas/Record'}}
    operations:
      receiveRecord:
        action: receive
        channel: {${'$'}ref: '#/channels/records'}
        messages: [{${'$'}ref: '#/channels/records/messages/record'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val raml =
    """
    #%RAML 1.0
    title: Nominal scalars
    annotationTypes:
      sunday.wrapperType: {type: boolean, allowedTargets: [TypeDeclaration]}
    types:
      BaseFactSid: {type: string, pattern: '^sid:f:[A-Za-z0-9_-]+${'$'}', (sunday.wrapperType): true}
      BaseLossSid: {type: string, pattern: '^sid:l:[A-Za-z0-9_-]+${'$'}', (sunday.wrapperType): true}
      BroadSid: {type: string, pattern: '^sid:', (sunday.wrapperType): true}
      PositiveCount: {type: integer, minimum: 1, (sunday.wrapperType): true}
      Ratio: {type: number, minimum: 0, maximum: 1, (sunday.wrapperType): true}
      Enabled: {type: boolean, (sunday.wrapperType): true}
      AnySid: BaseFactSid | BaseLossSid
      AmbiguousSid: BaseFactSid | BroadSid
      Defaults:
        type: object
        properties:
          fact?: {type: BaseFactSid, default: 'sid:f:default'}
      Record:
        type: object
        properties:
          fact: BaseFactSid
          ambiguity?: AmbiguousSid
          identifiers: AnySid[]
          count: PositiveCount
          ratio: Ratio
          enabled: Enabled
          defaults?: Defaults
    /records/{id}:
      uriParameters:
        id: ${if (parameterDefaults) "{type: AnySid, default: 'sid:f:default'}" else "AnySid"}
      get:
        queryParameters:
          fact?: BaseFactSid
        responses:
          200:
            body:
              application/json: Record
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "nominal.raml" else "nominal.yaml")
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
    events.writeText("asyncapi: 3.0.0\ninfo: {title: Nominal scalars, version: 1.0.0}\nchannels: {}\noperations: {}\n")
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
