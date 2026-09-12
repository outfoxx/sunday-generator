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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.io.path.writeText

/** Isolated HTTP server shared by library, CLI, and Gradle reference tests. */
class OpenApiHttpFixture : AutoCloseable {
  private val executor = Executors.newVirtualThreadPerTaskExecutor()
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

  /** Request paths observed by the server. */
  val requests = ConcurrentLinkedQueue<String>()

  /** Handlers for individual paths; exact path-and-query matches take precedence. */
  val handlers = ConcurrentHashMap<String, (HttpExchange) -> Unit>()

  /** Base URI for this isolated server. */
  val baseUri: URI get() = URI("http://127.0.0.1:${server.address.port}/")

  init {
    server.executor = executor
    server.createContext("/") { exchange ->
      exchange.use {
        requests.add(it.requestURI.toString())
        val handler = handlers[it.requestURI.toString()] ?: handlers[it.requestURI.path]
        handler?.invoke(it) ?: it.sendResponseHeaders(404, -1)
      }
    }
    server.start()
  }

  /** Installs a fixed response. */
  fun respond(
    path: String,
    body: String,
    status: Int = 200,
    headers: Map<String, String> = emptyMap(),
  ) {
    handlers[path] = { exchange -> exchange.respond(body, status, headers) }
  }

  /** Revalidates changing content using an ETag, returning 404 when the supplied document is absent. */
  fun respondConditionally(
    path: String,
    document: () -> String?,
  ) {
    handlers[path] = { exchange ->
      val body = document()
      if (body == null) {
        exchange.sendResponseHeaders(404, -1)
      } else {
        val etag = "\"${body.hashCode()}\""
        val status = if (exchange.requestHeaders.getFirst("If-None-Match") == etag) 304 else 200
        exchange.respond(body, status, mapOf("ETag" to etag))
      }
    }
  }

  private fun HttpExchange.respond(
    body: String,
    status: Int,
    headers: Map<String, String>,
  ) {
    headers.forEach { (name, value) -> responseHeaders.add(name, value) }
    val bytes = body.toByteArray()
    sendResponseHeaders(status, if (status == 304) -1 else bytes.size.toLong())
    if (status != 304) responseBody.write(bytes)
  }

  /** Exports a fixture combining redirects, relative references, resource IDs, and anchors. */
  fun export(directory: Path): GeneratedApi {
    respond("/redirect", "", 302, mapOf("Location" to "/schemas/user.yaml"))
    respond(
      "/schemas/user.yaml",
      OpenApiReferenceDocuments.document(
        "Shared resources",
        OpenApiReferenceDocuments.booleanSchemas,
        """
        BooleanValues:
          type: object
          required: [truth, empty]
          properties:
            truth: {${'$'}ref: '#/components/schemas/Anything'}
            empty: {${'$'}ref: '#/components/schemas/Empty'}
            inlineTruth: true
            inlineEmpty: {}
        User:
          ${'$'}id: ./user-resource.yaml
          ${'$'}anchor: user
          type: object
          required: [id, address, node, composedNode, maybeAddress]
          properties:
            id: {type: string}
            address: {${'$'}ref: 'address.yaml#address'}
            repeated: {${'$'}ref: '${baseUri}identities/address#address'}
            maybeAddress:
              ${'$'}anchor: maybeAddress
              anyOf: [{${'$'}ref: 'address.yaml#address'}, {type: 'null'}]
            copiedAddress: {${'$'}ref: '#maybeAddress'}
            composedAddress: {allOf: [{${'$'}ref: '#maybeAddress'}]}
            extendedAddress:
              ${'$'}ref: 'address.yaml#address'
              required: [postalCode]
              properties:
                street: {minLength: 2}
                postalCode: {type: string}
            node:
              ${'$'}anchor: node
              type: [object, 'null']
              required: [child]
              properties:
                value: {type: string}
                child: {${'$'}ref: '#node'}
            copiedNode: {${'$'}ref: '#node'}
            composedNode: {allOf: [{${'$'}ref: '#node'}]}
            profile:
              type: object
              properties:
                localValue: {type: string}
            externalProfile: {${'$'}ref: 'user-profile.yaml'}
            arbitrary: {nullable: false}
            nullableArbitrary: {nullable: true}
        AnnotatedNode:
          ${'$'}ref: '#/components/schemas/User/properties/node'
          description: Annotated node
          readOnly: true
          deprecated: true
        NullableText: {type: [string, 'null']}
        """.trimIndent(),
        OpenApiReferenceDocuments.nullableValues,
        """
        State: {type: [string, 'null'], enum: [active, inactive]}
        """.trimIndent(),
        OpenApiReferenceDocuments.pet,
        OpenApiReferenceDocuments.mappedPet(includeDog = true),
        OpenApiReferenceDocuments.cat,
        OpenApiReferenceDocuments.dog,
        OpenApiReferenceDocuments.records,
        """
        Restrictions:
          ${'$'}id: ./restrictions.yaml
          type: object
          required: [address, text, state]
          properties:
            address:
              type: object
              anyOf: [{${'$'}ref: 'address.yaml#address'}, {type: 'null'}]
            text:
              ${'$'}anchor: text
              oneOf: [{${'$'}ref: 'user.yaml#/components/schemas/NullableText'}, {type: 'null'}]
            copiedText: {${'$'}ref: '#text'}
            state: {${'$'}ref: 'user.yaml#/components/schemas/State'}
        """.trimIndent(),
      ),
    )
    respond(
      "/schemas/user-profile.yaml",
      "type: object\nproperties:\n  remoteValue: {type: integer}",
    )
    respond(
      "/schemas/mapped-cat",
      OpenApiReferenceDocuments.mappedCat(),
    )
    respond(
      "/schemas/mapped-dog",
      OpenApiReferenceDocuments.mappedDog,
    )
    respond(
      "/schemas/address.yaml",
      """
      ${'$'}id: ${baseUri}identities/address
      ${'$'}anchor: address
      type: object
      required: [street]
      properties:
        street: {type: string}
      """.trimIndent(),
    )
    val source = directory.resolve("api.yaml")
    source.writeText(
      """
      openapi: 3.0.3
      info: {title: References, version: 1.0.0}
      servers: [{url: 'https://example.test'}]
      paths:
        /users:
          get:
            operationId: getUser
            parameters:
              - name: limit
                in: query
                schema: {type: integer, default: 20, minimum: 1, maximum: 100}
            responses:
              '200':
                description: User
                content:
                  application/json:
                    schema: {${'$'}ref: '#/components/schemas/User'}
      components:
        schemas:
          cat: {type: object, properties: {unrelated: {type: boolean}}}
          BooleanValues: {${'$'}ref: '${baseUri}redirect#/components/schemas/BooleanValues'}
          Unbounded: {type: integer, exclusiveMinimum: false, exclusiveMaximum: false}
          DocumentedRecord: {${'$'}ref: '${baseUri}redirect#/components/schemas/DocumentedRecord'}
          MappedPets:
            type: object
            required: [animal]
            properties:
              animal: {${'$'}ref: '${baseUri}redirect#/components/schemas/MappedPet'}
          Pets:
            type: object
            required: [animal]
            properties:
              animal: {${'$'}ref: '${baseUri}redirect#/components/schemas/Pet'}
              cat: {${'$'}ref: '${baseUri}redirect#/components/schemas/Cat'}
              dog: {${'$'}ref: '${baseUri}redirect#/components/schemas/Dog'}
          User: {${'$'}ref: '${baseUri}redirect#/components/schemas/User'}
          AnnotatedNode: {${'$'}ref: '${baseUri}redirect#/components/schemas/AnnotatedNode'}
          Restrictions: {${'$'}ref: '${baseUri}redirect#/components/schemas/Restrictions'}
          Limit: {${'$'}ref: '#/paths/~1users/get/parameters/0/schema'}
          Nullability:
            type: object
            required: [strictText, values]
            properties:
              strictText: {type: string, allOf: [{type: string, nullable: true}]}
              values: {${'$'}ref: '${baseUri}redirect#values'}
          Measurement:
            allOf:
              - type: object
                properties:
                  count: {type: number, minimum: 0, exclusiveMinimum: true}
              - type: object
                properties:
                  count: {type: number, minimum: 1, exclusiveMinimum: false}
      """.trimIndent(),
    )
    return GeneratedApiIrExporter(
      GeneratedApiIrOptions(openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"))),
    ).export(source.toUri())
  }

  override fun close() {
    server.stop(0)
    executor.close()
  }
}
