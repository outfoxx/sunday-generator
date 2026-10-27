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

/** Source problem hierarchies and structural response unions (issue #269). */
internal fun problemExtensionsApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val raml =
    """
    #%RAML 1.0
    title: Problem extensions
    uses:
      sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
    types:
      Uri:
        type: string
        (sunday.kotlinType): java.net.URI
      Problem:
        type: object
        properties:
          type: Uri
          title: {type: string, minLength: 2}
          status: {type: integer, minimum: 100, maximum: 599}
          detail?: string
          instance?: Uri
      InvalidLimitProblem: Problem
      OtherProblem: Problem
      ClosedProblem:
        type: Problem
        additionalProperties: false
      TypedProblem: Problem
      DeclaredProblem:
        type: Problem
        properties:
          code: string
      PatternProblem:
        type: object
        properties:
          type: Uri
          title: string
          status: integer
          /^x-/: {type: integer, minimum: 1}
      ProblemResponse: InvalidLimitProblem | OtherProblem
      ProblemFixtures:
        type: object
        properties:
          response?: ProblemResponse
          closed?: ClosedProblem
          typed?: TypedProblem
          declared?: DeclaredProblem
          patterned?: PatternProblem
    /user:
      get:
        responses:
          200:
            body:
              application/problem+json: ProblemFixtures
    """.trimIndent()
  val schemas =
    """
    Problem:
      type: object
      required: [type, title, status]
      properties:
        type: {type: string, format: uri}
        title: {type: string, minLength: 2}
        status: {type: integer, minimum: 100, maximum: 599}
        detail: {type: string}
        instance: {type: string, format: uri}
      additionalProperties: true
    InvalidLimitProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/Problem'}
        - type: object
          example: {type: 'https://example.test/problems/invalid-limit', title: Invalid Limit, status: 400}
    OtherProblem:
      allOf: [{${'$'}ref: '#/components/schemas/Problem'}, {type: object}]
    ClosedProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/Problem'}
        - type: object
          properties:
            type: {type: string, format: uri}
            title: {type: string, minLength: 2}
            status: {type: integer, minimum: 100, maximum: 599}
            detail: {type: string}
            instance: {type: string, format: uri}
          additionalProperties: false
    TypedProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/Problem'}
        - type: object
          properties:
            type: {type: string, format: uri}
            title: {type: string, minLength: 2}
            status: {type: integer, minimum: 100, maximum: 599}
            detail: {type: string}
            instance: {type: string, format: uri}
          additionalProperties: {type: integer, minimum: 1}
    DeclaredProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/Problem'}
        - type: object
          required: [code]
          properties:
            code: {type: string}
    PatternProblem:
      type: object
      required: [type, title, status]
      properties:
        type: {type: string, format: uri}
        title: {type: string}
        status: {type: integer}
      patternProperties:
        '^x-': {type: integer, minimum: 1}
      additionalProperties: false
    ProblemFixtures:
      type: object
      properties:
        response: {${'$'}ref: '#/components/schemas/ProblemResponse'}
        closed: {${'$'}ref: '#/components/schemas/ClosedProblem'}
        typed: {${'$'}ref: '#/components/schemas/TypedProblem'}
        declared: {${'$'}ref: '#/components/schemas/DeclaredProblem'}
        patterned: {${'$'}ref: '#/components/schemas/PatternProblem'}
    ProblemResponse:
      anyOf: [{${'$'}ref: '#/components/schemas/InvalidLimitProblem'}, {${'$'}ref: '#/components/schemas/OtherProblem'}]
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Problem extensions, version: 1.0.0}
    paths:
      /user:
        get:
          operationId: getProblemResponse
          responses:
            '200':
              description: ProblemResponse
              content:
                application/problem+json:
                  schema: {${'$'}ref: '#/components/schemas/ProblemResponse'}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  // AsyncAPI preserves named inheritance, but inline allOf restrictions are not lifted onto the child.
  // Exercise those restrictions on root problems here and on inherited OpenAPI problems above.
  val asyncSchemas =
    schemas.replace(
      Regex("(?s)(ClosedProblem|TypedProblem):\\n  allOf:.*?(?=\\n[A-Z]|$)"),
    ) { match ->
      val lines = match.value.lines()
      lines.first() + "\n  type: object\n  required: [type, title, status]\n" +
        lines.drop(4).joinToString("\n") { it.removePrefix("    ") }
    }
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Problem extensions, version: 1.0.0}
    channels:
      user:
        address: /user
        messages:
          user: {payload: {${'$'}ref: '#/components/schemas/ProblemFixtures'}}
    operations:
      receiveProblemResponse:
        action: receive
        channel: {${'$'}ref: '#/channels/user'}
        messages: [{${'$'}ref: '#/channels/user/messages/user'}]
    components:
      schemas:
    """.trimIndent() + "\n" + asyncSchemas.prependIndent("    ")
  val source = directory.resolve(if (frontend == "raml") "problems.raml" else "problems.yaml")
  source.writeText(
    when (frontend) {
      "raml" -> raml
      "asyncapi" -> asyncapi
      "openapi", "composed" -> openapi
      else -> error("Unsupported problem schema frontend: $frontend")
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      "asyncapi: 3.0.0\ninfo: {title: Problem extensions, version: 1.0.0}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
