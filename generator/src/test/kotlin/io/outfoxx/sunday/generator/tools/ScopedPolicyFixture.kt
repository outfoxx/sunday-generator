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

/** Source fixtures preserving the same role, profile, and declaration semantics across frontends. */
internal fun scopedPolicyApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val inherited =
    """
    all:
      timeout: PT5S
      retry: {maxRetries: 3, retryOn: java.io.IOException, abortOn: java.lang.IllegalArgumentException}
    client:
      rateLimit: {value: 3, window: PT1S}
    server:
      rateLimit: {value: 7, window: PT1S}
    profiles:
      internal:
        all: {timeout: PT9S}
        client:
          retry: {maxRetries: 1}
          circuitBreaker: {failureRatio: 0.5, skipOn: java.lang.IllegalArgumentException}
    """.trimIndent()
  val local =
    """
    all:
      timeout: PT2S
      retry: {delay: PT0.000000001S, abortOn: []}
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Scoped policies, version: 1.0.0}
    paths:
      /items:
        x-sunday-policy:
          INHERITED
        get:
          operationId: fetch
          x-sunday-policy:
            LOCAL
          responses:
            '200':
              description: OK
              content:
                application/json:
                  schema: {type: string}
    """.trimIndent()
      .replace(
        "      INHERITED",
        inherited.prependIndent("      "),
      ).replace("        LOCAL", local.prependIndent("        "))
  val raml =
    """
    #%RAML 1.0
    title: Scoped policies
    annotationTypes:
      sunday.policy: {type: object, allowedTargets: [Resource, Method]}
    /items:
      (sunday.policy):
        INHERITED
      get:
        displayName: fetch
        (sunday.policy):
          LOCAL
        responses:
          200:
            body:
              application/json:
                type: string
    """.trimIndent()
      .replace(
        "    INHERITED",
        inherited.prependIndent("    "),
      ).replace("      LOCAL", local.prependIndent("      "))
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Scoped policies, version: 1.0.0}
    channels:
      events:
        address: /events
        x-sunday-policy:
          INHERITED
        messages:
          event: {payload: {type: string}}
    operations:
      events:
        action: receive
        channel: {${'$'}ref: '#/channels/events'}
        messages: [{${'$'}ref: '#/channels/events/messages/event'}]
        x-sunday-policy:
          LOCAL
    """.trimIndent()
      .replace(
        "      INHERITED",
        inherited.prependIndent("      "),
      ).replace("      LOCAL", local.prependIndent("      "))
  val documents =
    when (frontend) {
      "raml" -> listOf("api.raml" to raml)
      "openapi" -> listOf("api.yaml" to openapi)
      "asyncapi" -> listOf("events.yaml" to asyncapi)
      "composed" -> listOf("api.yaml" to openapi, "events.yaml" to asyncapi)
      else -> error("Unknown fixture frontend $frontend")
    }
  return GeneratedApiIrExporter().export(
    documents.map { (name, source) ->
      directory.resolve(name).also { it.writeText(source) }.toUri()
    },
  )
}
