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
import java.nio.file.Path
import kotlin.io.path.writeText

/** Shared server contract exercised through each source frontend and exact target compiler. */
internal fun clientConfigurationApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val source = directory.resolve(if (frontend == "raml") "api.raml" else "api.yaml")
  source.writeText(
    when (frontend) {
      "raml" ->
        """
        #%RAML 1.0
        title: Example API
        version: '1'
        uses:
          sunday: https://outfoxx.github.io/sunday-generator/sunday.raml
        (sunday.problemUriParams): {tenant: primary}
        baseUri: https://{tenant}.example/v1
        baseUriParameters:
          tenant: {type: string, default: primary, enum: [primary, secondary]}
        /items:
          get:
            displayName: listItems
            responses:
              204:
        """.trimIndent()
      "server-profile" ->
        """
        asyncapi: 2.6.0
        info: {title: Example API, version: '1'}
        servers:
          production:
            url: 'https://{tenant}.example/v1'
            protocol: https
            x-sunday-security-profile: production
            variables:
              tenant: {default: primary, enum: [primary, secondary]}
            security: [{identity: []}]
        channels:
          /items:
            security: [{accessKey: []}]
            subscribe:
              operationId: listItems
              message: {payload: {type: string}}
        components:
          securitySchemes:
            identity:
              type: http
              scheme: bearer
              x-sunday-security:
                profiles:
                  production:
                    client: {provider: identity, flow: static}
            accessKey: {type: httpApiKey, in: header, name: X-API-Key}
        """.trimIndent()
      "asyncapi", "server-security" ->
        """
        asyncapi: 3.0.0
        info: {title: Example API, version: '1'}
        servers:
          production:
            host: '{tenant}.example'
            pathname: /v1
            protocol: https
            variables:
              tenant: {default: primary, enum: [primary, secondary]}
        channels:
          items:
            address: /items
            messages:
              result: {payload: {type: string}}
        operations:
          listItems:
            action: receive
            channel: {${'$'}ref: '#/channels/items'}
            messages: [{${'$'}ref: '#/channels/items/messages/result'}]
        """.trimIndent()
      else ->
        """
        openapi: 3.2.0
        info: {title: Example API, version: '1'}
        x-sunday-apiId: example
        servers:
          - name: production
            url: https://{tenant}.example/v1
            variables:
              tenant: {default: primary, enum: [primary, secondary]}
        paths:
          /items:
            get:
              operationId: listItems
              responses:
                '204': {description: Success}
        """.trimIndent()
    },
  )
  if (frontend == "server-security") {
    source.writeText(
      java.nio.file.Files.readString(source).replace(
        "channels:",
        """
            security: [{${'$'}ref: '#/components/securitySchemes/identity'}]
          development:
            host: dev.example
            protocol: https
            security: [{${'$'}ref: '#/components/securitySchemes/accessKey'}]
        channels:
        """.trimIndent(),
      ) + "\n" +
        """
        components:
          securitySchemes:
            identity: {type: http, scheme: bearer}
            accessKey: {type: httpApiKey, in: header, name: X-API-Key}
        """.trimIndent(),
    )
  }
  if (frontend == "multi") {
    source.writeText(
      java.nio.file.Files
        .readString(
          source,
        ).replace("paths:", "  - name: development\n    url: https://dev.example/v1\npaths:"),
    )
  }
  if (frontend == "security") {
    source.writeText(
      java.nio.file.Files
        .readString(
          source,
        ).replace("name: production", "name: production\n    x-sunday-security-profile: production") +
        "\n" +
        """

        security: [{identity: []}]
        components:
          securitySchemes:
            identity:
              type: http
              scheme: bearer
              x-sunday-security:
                profiles:
                  production:
                    client: {provider: identity, flow: static}
        """.trimIndent(),
    )
  }
  if (frontend in setOf("alternatives", "credential-collision")) {
    source.writeText(
      java.nio.file.Files.readString(source).replace(
        "'204': {description: Success}",
        """
        '204': {description: Success}
          /public:
            get:
              operationId: publicItems
              security: []
              responses: {'204': {description: Success}}
        """.trimIndent(),
      ) + "\n" +
        """
        security: [{identity: [], accessKey: []}, {identity: []}]
        components:
          securitySchemes:
            identity:
              type: http
              scheme: bearer
              x-sunday-security:
                client: {provider: identity, flow: static}
            accessKey:
              type: apiKey
              in: header
              name: X-API-Key
              x-sunday-security:
                client: {provider: accessKey, flow: static}
        """.trimIndent(),
    )
  }
  if (frontend == "credential-collision") {
    source.writeText(
      java.nio.file.Files
        .readString(source)
        .replace("accessKey", "access-key")
        .replace("identity", "access_key"),
    )
  }
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val extra = directory.resolve("extra.yaml")
    extra.writeText(
      """
      openapi: 3.2.0
      info: {title: Example API, version: '1'}
      x-sunday-apiId: example
      paths: {}
      components:
        schemas:
          Extra: {type: string}
      """.trimIndent(),
    )
    sources += extra.toUri()
  }
  val api = GeneratedApiIrExporter().export(sources)
  val normalized = api.copy(services = api.services.map { it.copy(name = "Service", group = null) })
  return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(normalized))
}
