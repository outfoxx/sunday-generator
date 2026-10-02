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

/** Object unions whose branches share a wire shape but have different field constraints. */
internal fun objectUnionValidationApi(
  frontend: String,
  directory: Path,
  discriminated: Boolean = false,
  commonMaximum: Int? = null,
  mappingOnly: Boolean = false,
): GeneratedApi {
  val schemas =
    """
    Small:
      type: object
      additionalProperties: false
      required: [value${if (discriminated) ", kind" else ""}]
      properties:
        ${if (discriminated) "kind: {type: string, const: small}" else ""}
        value: {type: integer, minimum: 1, maximum: 3}
    Large:
      type: object
      additionalProperties: false
      required: [value${if (discriminated) ", kind" else ""}]
      properties:
        ${if (discriminated) "kind: {type: string, const: large}" else ""}
        value: {type: integer, minimum: 2, maximum: 9}
    Choice:
      ${if (mappingOnly) "type: object" else ""}
      ${commonMaximum?.let {
      "properties: {${if (mappingOnly) "kind: {type: string}, " else ""}value: {type: integer, maximum: $it}}"
    } ?: ""}
      ${if (discriminated) "discriminator: {propertyName: kind, mapping: {small: '#/components/schemas/Small', large: '#/components/schemas/Large'}}" else ""}
      ${if (mappingOnly) "" else "oneOf: [{\$ref: '#/components/schemas/Small'}, {\$ref: '#/components/schemas/Large'}]"}
    Holder:
      type: object
      properties:
        choice: {${'$'}ref: '#/components/schemas/Choice'}
      additionalProperties: {${'$'}ref: '#/components/schemas/Choice'}
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Object union validation, version: 1.0.0}
    paths:
      /holder:
        get:
          operationId: getHolder
          responses:
            '200':
              description: Holder
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/Holder'}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Object union validation, version: 1.0.0}
    channels:
      holder:
        address: /holder
        messages:
          holder: {payload: {${'$'}ref: '#/components/schemas/Holder'}}
    operations:
      receiveHolder:
        action: receive
        channel: {${'$'}ref: '#/channels/holder'}
        messages: [{${'$'}ref: '#/channels/holder/messages/holder'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val raml =
    """
    #%RAML 1.0
    title: Object union validation
    types:
      Small:
        type: object
        additionalProperties: false
        properties:
          value: {type: integer, minimum: 1, maximum: 3}
      Large:
        type: object
        additionalProperties: false
        properties:
          value: {type: integer, minimum: 2, maximum: 9}
      Choice: Small | Large
      Holder:
        type: object
        properties:
          choice?: Choice
          /^extra-/: Choice
    /holder:
      get:
        responses:
          200:
            body:
              application/json: Holder
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "unions.raml" else "unions.yaml")
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
      "asyncapi: 3.0.0\ninfo: {title: Object union validation, version: 1.0.0}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
