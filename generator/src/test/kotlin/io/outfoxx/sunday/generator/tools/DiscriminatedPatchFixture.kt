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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import java.nio.file.Path
import kotlin.io.path.writeText

/** Discriminated patch contracts for automatic, explicit, and composed source inputs. */
internal fun discriminatedPatchApi(
  frontend: String,
  directory: Path,
  extraDogField: String? = null,
): GeneratedApi {
  val openapi =
    requireNotNull(GeneratedApi::class.java.getResource("/openapi/discriminated-patch.yaml"))
      .readText()
      .let { source ->
        if (extraDogField == null) {
          source
        } else {
          source.replace(
            "trained: {type: boolean}",
            "trained: {type: boolean}\n            $extraDogField: {type: string}",
          )
        }
      }
  val marked = openapi.replace("    Animal:\n", "    Animal:\n      x-sunday-patchable: true\n")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Animals, version: 1.0.0}
    channels:
      animals:
        address: /animals
        messages:
          animal: {payload: {${'$'}ref: '#/components/schemas/Animal'}}
    operations:
      receiveAnimal:
        action: receive
        channel: {${'$'}ref: '#/channels/animals'}
        messages: [{${'$'}ref: '#/channels/animals/messages/animal'}]
    components:
      schemas:
    """.trimIndent() + "\n" +
      marked.substringAfter("  schemas:\n").replace("{type: string, nullable: true}", "{type: [string, 'null']}")
  val raml =
    """
    #%RAML 1.0
    title: Animals
    annotationTypes:
      sunday.patchable: {type: boolean, allowedTargets: [TypeDeclaration]}
    types:
      Kind: {type: string, enum: [cat, dog]}
      Animal:
        type: object
        (sunday.patchable): true
        discriminator: kind
        properties:
          kind: Kind
          name: string
          detail: {type: "string?", required: true}
          nickname?: string
      Cat:
        type: Animal
        discriminatorValue: cat
        properties:
          indoor?: boolean
      Dog:
        type: Animal
        discriminatorValue: dog
        properties:
          trained?: boolean
    /animals:
      patch:
        displayName: updateAnimal
        body:
          application/merge-patch+json: Animal
        responses:
          204:
    """.trimIndent()
  val source = directory.resolve(if (frontend == "raml") "animals.raml" else "animals.yaml")
  source.writeText(
    when (frontend) {
      "three-level" ->
        openapi
          .replace("enum: [cat, dog]", "enum: [cat, dog, Mammal]")
          .replace(
            "    Cat:\n",
            "    Mammal:\n      allOf:\n        - {${'$'}ref: '#/components/schemas/Animal'}\n    Cat:\n",
          ).replace(
            "    Cat:\n      allOf:\n        - {${'$'}ref: '#/components/schemas/Animal'}",
            "    Cat:\n      allOf:\n        - {${'$'}ref: '#/components/schemas/Mammal'}",
          )
      "raml" ->
        if (extraDogField == null) {
          raml
        } else {
          raml.replace(
            "trained?: boolean",
            "trained?: boolean\n      $extraDogField?: string",
          )
        }
      "asyncapi" -> asyncapi
      "explicit", "composed" -> marked
      "tolerant" ->
        openapi
          .replace("    Animal:\n", "    Animal:\n      x-sunday-tolerance: response\n")
          .replace("kind: {type: string, enum: [cat, dog]}", "kind: {type: string}")
      else -> openapi
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(asyncapi)
    sources += events.toUri()
  }
  val api = GeneratedApiIrExporter(GeneratedApiIrOptions(autoPatchable = frontend != "explicit")).export(sources)
  val renamed =
    if (frontend == "renamed") {
      api.copy(
        models =
          api.models.map { model ->
            model.copy(
              properties =
                model.properties.map { property ->
                  if (property.name == "kind") property.copy(serializationName = "animal-kind") else property
                },
            )
          },
      )
    } else {
      api
    }
  return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(renamed))
}
