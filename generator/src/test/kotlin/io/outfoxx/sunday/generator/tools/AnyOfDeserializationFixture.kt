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

/** Open full and summary alternatives with overlapping structural fields (issue #267). */
internal fun anyOfDeserializationApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val schemas =
    """
    UserSelfResponse:
      type: object
      required: [userId, email, displayName, createdAt, teams]
      properties:
        userId: {type: string, minLength: 2}
        email: {type: string}
        displayName: {type: string}
        createdAt: {type: string, format: date-time}
        teams: {type: array, items: {type: string}, maxItems: 1}
    UserSummaryResponse:
      type: object
      required: [userId, email, displayName]
      properties:
        userId: {type: string, minLength: 2}
        email: {type: string}
        displayName: {type: string}
    UserResponse:
      anyOf: [{${'$'}ref: '#/components/schemas/UserSelfResponse'}, {${'$'}ref: '#/components/schemas/UserSummaryResponse'}]
    """.trimIndent()
  val openapi =
    """
    openapi: 3.1.0
    info: {title: AnyOf deserialization, version: 1.0.0}
    paths:
      /user:
        get:
          operationId: getUserResponse
          responses:
            '200':
              description: UserResponse
              content:
                application/json:
                  schema: {${'$'}ref: '#/components/schemas/UserResponse'}
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: AnyOf deserialization, version: 1.0.0}
    channels:
      user:
        address: /user
        messages:
          user: {payload: {${'$'}ref: '#/components/schemas/UserResponse'}}
    operations:
      receiveUserResponse:
        action: receive
        channel: {${'$'}ref: '#/channels/user'}
        messages: [{${'$'}ref: '#/channels/user/messages/user'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas.prependIndent("    ")
  val raml =
    """
    #%RAML 1.0
    title: AnyOf deserialization
    types:
      UserSelfResponse:
        type: object
        properties:
          userId: {type: string, minLength: 2}
          email: string
          displayName: string
          createdAt: datetime
          teams: {type: array, items: string, maxItems: 1}
      UserSummaryResponse:
        type: object
        properties:
          userId: {type: string, minLength: 2}
          email: string
          displayName: string
      UserResponse: UserSelfResponse | UserSummaryResponse
    /user:
      get:
        responses:
          200:
            body:
              application/json: UserResponse
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
      "asyncapi: 3.0.0\ninfo: {title: AnyOf deserialization, version: 1.0.0}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}

/** Valid wires; only the second input matches the full alternative. */
internal val anyOfValidWires =
  listOf(
    """{"userId":"user-1","email":"user@example.test","displayName":"User","future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":"2026-10-12T00:00:00Z","teams":[],"future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":"2026-10-12T00:00:00Z","future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","teams":[],"future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":"future-format","teams":[],"future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":null,"teams":[],"future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":"2026-10-12T00:00:00Z","teams":{},"future":{"nested":[1,null,true]}}""",
    """{"userId":"user-1","email":"user@example.test","displayName":"User","createdAt":"2026-10-12T00:00:00Z","teams":["one","two"],"future":{"nested":[1,null,true]}}""",
  ).joinToString(prefix = "[", postfix = "]")

/** Wires that match neither alternative, including required, null and constraint failures. */
internal val anyOfInvalidWires =
  listOf(
    """{}""",
    """{"userId":"user-1"}""",
    """{"userId":"x","email":"user@example.test","displayName":"User"}""",
    """{"userId":null,"email":"user@example.test","displayName":"User"}""",
  ).joinToString(prefix = "[", postfix = "]")
