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

/** The same logical bearer scheme and scoped application providers in each supported source frontend. */
internal fun scopedSecurityApi(
  frontend: String,
  directory: Path,
  profiledServer: Boolean = false,
  endpointBindings: Boolean = false,
  serverQuarkus: String? = null,
  clientQuarkus: String? = null,
): GeneratedApi {
  val binding =
    """
    all: {provider: identity}
    server: {provider: verifier}
    profiles:
      internal:
        client:
          provider: service
          flow: clientCredentials
          tokenUrl: https://identity.internal/token
      external:
        client:
          provider: application
          flow: authorizationCode
          authorizationUrl: https://identity.example/authorize
          tokenUrl: https://identity.example/token
    """.trimIndent()
      .let { source ->
        source
          .let {
            if (serverQuarkus ==
              null
            ) {
              it
            } else {
              it.replace(
                "server: {provider: verifier}",
                "server: {provider: verifier, quarkus: {mode: $serverQuarkus}}",
              )
            }
          }.let {
            if (clientQuarkus ==
              null
            ) {
              it
            } else {
              it.replace("provider: service", "provider: service\n      quarkus: {mode: $clientQuarkus}")
            }
          }
      }.let { declarations ->
        if (clientQuarkus in setOf("propagate", "exchange")) {
          declarations
            .replace("flow: clientCredentials", "flow: external")
            .replace("      tokenUrl: https://identity.internal/token\n", "")
        } else {
          declarations
        }
      }.let { declarations ->
        if (profiledServer) {
          declarations
            .replace("  internal:\n", "  internal:\n    server: {provider: internalVerifier}\n")
            .replace("  external:\n", "  external:\n    server: {provider: externalVerifier}\n")
        } else {
          declarations
        }
      }
  val openapi =
    """
    openapi: 3.1.0
    info: {title: Scoped security, version: 1.0.0}
    security: [{token: [items:read]}]
    components:
      securitySchemes:
        token:
          type: http
          scheme: bearer
          x-sunday-security:
            BINDING
    paths:
      /items:
        get:
          operationId: fetch
          responses: {'204': {description: OK}}
    """.trimIndent().replace("        BINDING", binding.prependIndent("        "))
  val raml =
    """
    #%RAML 1.0
    title: Scoped security
    annotationTypes:
      sunday.security: {type: object, allowedTargets: [API, Resource, Method, SecurityScheme]}
    securitySchemes:
      token:
        type: OAuth 2.0
        settings:
          accessTokenUri: https://identity.example/token
          authorizationGrants: [client_credentials]
        (sunday.security):
          BINDING
    securedBy: [token: {scopes: [items:read]}]
    /items:
      get:
        displayName: fetch
        responses: {204: {}}
    """.trimIndent().replace("      BINDING", binding.prependIndent("      "))
  val asyncapi =
    """
    asyncapi: 2.6.0
    info: {title: Scoped security, version: 1.0.0}
    servers:
      events:
        url: https://events.example
        protocol: https
        security: [{token: [items:read]}]
    components:
      securitySchemes:
        token:
          type: http
          scheme: bearer
          x-sunday-security:
            BINDING
    channels:
      /events:
        subscribe:
          operationId: events
          message: {payload: {type: string}}
    """.trimIndent().replace("        BINDING", binding.prependIndent("        "))
  val asyncapi3 =
    """
    asyncapi: 3.0.0
    info: {title: Scoped security, version: 1.0.0}
    servers:
      events:
        host: events.example
        protocol: https
        security: [{"${'$'}ref": "#/components/securitySchemes/token"}]
    components:
      securitySchemes:
        token:
          type: oauth2
          scopes: [items:read]
          flows:
            authorizationCode:
              authorizationUrl: https://identity.example/authorize
              tokenUrl: https://identity.example/token
              availableScopes: {items:read: Read items}
          x-sunday-security:
            BINDING
    channels:
      events:
        address: /events
        messages:
          item: {payload: {type: string}}
    operations:
      events:
        action: receive
        channel: {"${'$'}ref": "#/channels/events"}
        messages: [{"${'$'}ref": "#/channels/events/messages/item"}]
    """.trimIndent().replace("        BINDING", binding.prependIndent("        "))
  val documents =
    when (frontend) {
      "raml" -> listOf("api.raml" to raml)
      "openapi" -> listOf("api.yaml" to openapi)
      "asyncapi" -> listOf("events.yaml" to asyncapi)
      "asyncapi3" -> listOf("events.yaml" to asyncapi3)
      "composed" -> listOf("api.yaml" to openapi, "events.yaml" to asyncapi)
      else -> error("Unknown frontend '$frontend'")
    }
  return GeneratedApiIrExporter().export(
    documents.map { (name, content) ->
      val selected =
        if (endpointBindings) {
          val annotation = if (name.endsWith(".raml")) "(sunday.security)" else "x-sunday-security"
          val provider = if (name.startsWith("events")) "eventsProvider" else "apiProvider"
          content +
            "\n$annotation:\n  profiles:\n    internal:\n      client:\n        bindings:\n          token: {provider: $provider}\n"
        } else {
          content
        }
      directory.resolve(name).also { it.writeText(selected) }.toUri()
    },
  )
}
