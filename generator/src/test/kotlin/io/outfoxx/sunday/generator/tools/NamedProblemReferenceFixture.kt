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

/** Supplies named Problem references with independent alias and property constraints. */
internal fun namedProblemReferenceApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val source = directory.resolve(if (frontend == "raml") "problems.raml" else "problems.yaml")
  source.writeText(
    if (frontend == "raml") {
      """
      #%RAML 1.0
      title: Named Problem references
      types:
        Uri: string
        NestedUri: Uri
        InstanceRoot: {type: Uri, minLength: 3}
        InstanceUri: InstanceRoot
        AllowedType: {type: string, enum: ['about:blank', 'https://example.com']}
        HttpProblem:
          type: object
          properties:
            type?: NestedUri
            title?: string
            status?: integer
            instance?: InstanceUri
        EnumProblem:
          type: object
          properties:
            type?: AllowedType
            title?: string
            status?: integer
        OrdinaryRecord:
          type: object
          properties:
            uri: Uri
      """.trimIndent()
    } else {
      val header =
        if (frontend == "asyncapi") "asyncapi: 3.0.0\nchannels: {}\noperations: {}" else "openapi: 3.1.0\npaths: {}"
      header + "\n" +
        """
        info: {title: Named Problem references, version: '1'}
        components:
          schemas:
            Uri: {type: string, format: uri}
            NestedUri: {${'$'}ref: '#/components/schemas/Uri'}
            InstanceRoot: {${'$'}ref: '#/components/schemas/Uri', minLength: 3}
            InstanceUri: {${'$'}ref: '#/components/schemas/InstanceRoot'}
            AllowedType: {type: string, enum: ['about:blank', 'https://example.com']}
            HttpProblem:
              type: object
              properties:
                type: {${'$'}ref: '#/components/schemas/NestedUri'}
                title: {type: string}
                status: {type: integer}
                instance: {${'$'}ref: '#/components/schemas/InstanceUri'}
            EnumProblem:
              type: object
              properties:
                type: {${'$'}ref: '#/components/schemas/AllowedType'}
                title: {type: string}
                status: {type: integer}
            OrdinaryRecord:
              type: object
              required: [uri]
              properties:
                uri: {${'$'}ref: '#/components/schemas/Uri'}
        """.trimIndent()
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(
      "asyncapi: 3.0.0\ninfo: {title: Named Problem references, version: '1'}\nchannels: {}\noperations: {}\n",
    )
    sources += events.toUri()
  }
  val exported = GeneratedApiIrExporter().export(sources)
  // Frontends differ in how they compose patterns; exercise the retained alias chain at the IR boundary.
  return exported.copy(
    models =
      exported.models.map { model ->
        when (model.name) {
          "InstanceRoot" ->
            model.copy(
              validation =
                model.validation + mapOf("pattern" to "^urn:", "minLength" to "12", "maxLength" to "24"),
            )
          "InstanceUri" ->
            model.copy(
              aliases = listOf(GeneratedTypeRef.named("InstanceRoot")),
              validation =
                model.validation + mapOf("pattern" to ":example", "minLength" to "3", "maxLength" to "100"),
            )
          "HttpProblem" ->
            model.copy(
              properties =
                model.properties.map { property ->
                  if (property.name == "instance") {
                    property.copy(
                      type = GeneratedTypeRef.named("InstanceUri"),
                      validation =
                        property.validation +
                          mapOf("pattern" to "example$", "minLength" to "3", "maxLength" to "100"),
                    )
                  } else {
                    property
                  }
                },
            )
          else -> model
        }
      },
  )
}
