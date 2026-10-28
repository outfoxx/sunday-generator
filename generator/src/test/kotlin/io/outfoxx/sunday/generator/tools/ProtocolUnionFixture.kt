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
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import java.nio.file.Path
import kotlin.io.path.writeText

/** Parent schemas in unions must remain decodable independently of their known descendants. */
internal fun protocolUnionApi(
  frontend: String,
  directory: Path,
  collision: Boolean = false,
  typeNames: Map<String, String> = emptyMap(),
): GeneratedApi {
  val schemas =
    """
    BaseProblem:
      type: object
      required: [type, title, status, detail, problemId]
      properties:
        problemId: {type: string}
        type: {type: string, format: uri}
        title: {type: string, minLength: 2}
        status: {type: integer, minimum: 400}
        detail: {type: string, minLength: 2}
    CollectionProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/BaseProblem'}
        - type: object
          properties:
            status: {type: integer, const: 400}
    CollectionChoice:
      anyOf: [{${'$'}ref: '#/components/schemas/CollectionProblem'}, {${'$'}ref: '#/components/schemas/ChildProblem'}]
    ChildProblem:
      allOf:
        - {${'$'}ref: '#/components/schemas/CollectionProblem'}
        - type: object
          required: [reason]
          properties:
            reason: {type: string, minLength: 3}
    Exclusive:
      oneOf: [{${'$'}ref: '#/components/schemas/BaseProblem'}, {${'$'}ref: '#/components/schemas/ChildProblem'}]
    Inclusive:
      anyOf: [{${'$'}ref: '#/components/schemas/BaseProblem'}, {${'$'}ref: '#/components/schemas/ChildProblem'}]
    ReverseInclusive:
      anyOf: [{${'$'}ref: '#/components/schemas/ChildProblem'}, {${'$'}ref: '#/components/schemas/BaseProblem'}]
    Pet:
      type: object
      discriminator: {propertyName: kind, mapping: {cat: '#/components/schemas/Cat', dog: '#/components/schemas/Dog'}}
      required: [kind]
      properties:
        kind: {type: string}
    Mammal:
      allOf: [{${'$'}ref: '#/components/schemas/Pet'}]
    MammalResponse:
      oneOf: [{${'$'}ref: '#/components/schemas/Mammal'}, {${'$'}ref: '#/components/schemas/BaseProblem'}]
    Cat:
      allOf:
        - {${'$'}ref: '#/components/schemas/Mammal'}
        - type: object
          required: [lives]
          properties:
            kind: {type: string, const: cat}
            lives: {type: integer, minimum: 1}
    Dog:
      allOf:
        - {${'$'}ref: '#/components/schemas/Mammal'}
        - type: object
          required: [bark]
          properties:
            kind: {type: string, const: dog}
            bark: {type: boolean}
    Response:
      oneOf: [{${'$'}ref: '#/components/schemas/Pet'}, {${'$'}ref: '#/components/schemas/BaseProblem'}]
    """.trimIndent()
  val collisionSchema = if (collision) "\nAnyBaseProblem: {type: object, properties: {marker: {type: string}}}" else ""
  val header =
    if (frontend == "asyncapi") {
      "asyncapi: 3.0.0\ninfo: {title: Protocol unions, version: 1.0.0}\nchannels: {}\noperations: {}"
    } else {
      "openapi: 3.1.0\ninfo: {title: Protocol unions, version: 1.0.0}\npaths: {}"
    }
  val raml =
    """
    #%RAML 1.0
    title: Protocol unions
    types:
      BaseProblem:
        type: object
        properties:
          problemId: string
          type: string
          title: {type: string, minLength: 2}
          status: {type: integer, minimum: 400}
          detail: {type: string, minLength: 2}
      CollectionProblem:
        type: BaseProblem
        properties:
          status: {type: integer, minimum: 400, maximum: 400}
      CollectionChoice: CollectionProblem | ChildProblem
      ChildProblem:
        type: CollectionProblem
        properties:
          reason: {type: string, minLength: 3}
      Exclusive: BaseProblem | ChildProblem
      Inclusive: BaseProblem | ChildProblem
      ReverseInclusive: ChildProblem | BaseProblem
      Pet:
        type: object
        discriminator: kind
        properties:
          kind: string
      Cat:
        type: Pet
        discriminatorValue: cat
        properties:
          lives: {type: integer, minimum: 1}
      Dog:
        type: Pet
        discriminatorValue: dog
        properties:
          bark: boolean
      Response: Pet | BaseProblem
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "protocols.raml" else "protocols.yaml")
  val sourceText =
    if (frontend ==
      "raml"
    ) {
      raml
    } else {
      "$header\ncomponents:\n  schemas:\n" + (schemas + collisionSchema).prependIndent("    ")
    }
  source.writeText(
    Regex("\\b[A-Za-z][A-Za-z0-9]*\\b").replace(sourceText) { typeNames[it.value] ?: it.value },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText("asyncapi: 3.0.0\ninfo: {title: Protocol unions, version: 1.0.0}\nchannels: {}\noperations: {}\n")
    sources += events.toUri()
  }
  val api = GeneratedApiIrExporter().export(sources)
  if (frontend != "raml") return api
  // RAML has no URI string format; supply the shared IR format required by Sunday's Problem protocol.
  return api.copy(
    models =
      api.models.map { model ->
        if (model.name != "BaseProblem") {
          model
        } else {
          model.copy(
            properties =
              model.properties.map { property ->
                if (property.name !=
                  "type"
                ) {
                  property
                } else {
                  property.copy(type = GeneratedTypeRef.scalar("string", format = "uri"))
                }
              },
          )
        }
      },
  )
}
