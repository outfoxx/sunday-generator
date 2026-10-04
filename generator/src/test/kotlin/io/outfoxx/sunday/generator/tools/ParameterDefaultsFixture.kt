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
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import java.nio.file.Path
import kotlin.io.path.writeText

/** Client parameter presence and defaults shared by compiler-backed HTTP target tests. */
internal fun parameterDefaultsApi(
  frontend: String,
  directory: Path,
  cookies: Boolean = false,
): GeneratedApi {
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Parameter defaults, version: 1.0.0}
    servers:
      - url: https://{host}
        variables:
          host: {default: example.com}
    paths:
      /probe/{pathValue}:
        get:
          operationId: probe
          parameters:
            - {name: pathValue, in: path, required: true, schema: {type: string, default: fallback}}
            - {name: queryValue, in: query, required: true, schema: {type: integer, minimum: 1, default: 5}}
            - {name: nullableValue, in: query, required: true, schema: {type: [string, 'null']}}
            - {name: optionalValue, in: query, schema: {type: string}}
            - {name: zeroValue, in: query, required: true, schema: {type: integer, default: 0}}
            - {name: falseValue, in: query, required: true, schema: {type: boolean, default: false}}
            - {name: headerValue, in: header, required: true, schema: {type: string, default: header}}
            - {name: cookieValue, in: cookie, required: true, schema: {type: string, default: cookie}}
          responses:
            '204': {description: No content}
      /scalar-defaults/{stateValue}:
        get:
          operationId: scalarDefaults
          parameters:
            - {name: stateValue, in: path, required: true, schema: {${'$'}ref: '#/components/schemas/State', default: active}}
            - {name: uriValue, in: query, required: true, schema: {type: string, format: uri, default: 'https://example.com/default'}}
            - {name: nullableState, in: query, required: true, schema: {oneOf: [{${'$'}ref: '#/components/schemas/State'}, {type: 'null'}]}}
          responses:
            '204': {description: No content}
      /required/{pathValue}:
        get:
          operationId: required
          parameters:
            - {name: pathValue, in: path, required: true, schema: {type: string}}
            - {name: queryValue, in: query, required: true, schema: {type: integer}}
          responses:
            '204': {description: No content}
    components:
      schemas:
        State: {type: string, enum: [active, inactive]}
    """.trimIndent()
  val raml =
    """
    #%RAML 1.0
    title: Parameter defaults
    uses:
      sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
    (sunday.problemUriParams): {host: example.com}
    baseUri: https://{host}
    baseUriParameters:
      host: {type: string, default: example.com}
    types:
      State: {type: string, enum: [active, inactive]}
    /probe/{pathValue}:
      uriParameters:
        pathValue: {type: string, default: fallback}
      get:
        displayName: probe
        queryParameters:
          queryValue: {type: integer, minimum: 1, default: 5}
          nullableValue: string | nil
          optionalValue?: string
          zeroValue: {type: integer, default: 0}
          falseValue: {type: boolean, default: false}
        headers:
          headerValue: {type: string, default: header}
        responses:
          204:
    /scalar-defaults/{stateValue}:
      uriParameters:
        stateValue: {type: State, default: active}
      get:
        displayName: scalarDefaults
        queryParameters:
          uriValue: {type: string, default: 'https://example.com/default'}
          nullableState: State | nil
        responses:
          204:
    /required/{pathValue}:
      uriParameters:
        pathValue: string
      get:
        displayName: required
        queryParameters:
          queryValue: integer
        responses:
          204:
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "parameters.raml" else "parameters.yaml")
  source.writeText(if (frontend == "raml") raml else openapi)
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      """
      asyncapi: 3.0.0
      info: {title: Parameter defaults, version: 1.0.0}
      channels:
        events:
          address: /events
          messages:
            value: {payload: {type: string}}
      operations:
        receiveValue:
          action: receive
          channel: {${'$'}ref: '#/channels/events'}
      """.trimIndent(),
    )
    sources += events.toUri()
  }
  val api = GeneratedApiIrExporter().export(sources)
  return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api)).let { normalized ->
    normalized.copy(
      services =
        normalized.services.filter { service -> service.operations.any { it.id == "probe" } }.map { service ->
          service.copy(
            name = "Parameters",
            group = null,
            operations =
              service.operations.map { operation ->
                operation.copy(
                  parameters =
                    operation.parameters.filter {
                      cookies || it.location != GeneratedParameter.Location.COOKIE
                    },
                )
              },
          )
        },
    )
  }
}

/** Exercises RFC 6570 expansion independently of the source frontend's native route syntax. */
internal fun GeneratedApi.withOptionalParameterTemplates(): GeneratedApi =
  copy(
    services =
      services.map { service ->
        service.copy(
          baseUri = "https://example.com{/host}",
          operations =
            service.operations.map { operation ->
              if (operation.id == "probe") operation.copy(path = "/probe{/pathValue}") else operation
            },
        )
      },
  )
