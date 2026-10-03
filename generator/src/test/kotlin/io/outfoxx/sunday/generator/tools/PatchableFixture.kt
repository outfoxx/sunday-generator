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

/** Merge-patch presence contracts across native, RAML, and composed source inputs. */
internal fun patchableApi(
  frontend: String,
  directory: Path,
  autoPatchable: Boolean = true,
): GeneratedApi {
  val openapi =
    requireNotNull(GeneratedApi::class.java.getResource("/openapi/patchable.yaml")).readText().let {
      if (frontend == "collisions") {
        it.replace(
          "            labels:",
          "            __proto__: {type: string}\n            constructor: {type: string}\n            labels:",
        ) +
          """

          MergePatchSupport:
            type: object
            properties:
              patch: {type: string}
              merge: {type: string}
          """.trimIndent().prependIndent("    ") + "\n"
      } else {
        it
      }
    }
  val schemas =
    openapi
      .substringAfter("  schemas:\n")
      .replace("    SomeRequest:\n", "    SomeRequest:\n      x-sunday-patchable: true\n")
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Patch probe, version: 1.0.0}
    channels:
      records:
        address: /records
        messages:
          record: {payload: {${'$'}ref: '#/components/schemas/SomeRequest'}}
    operations:
      receiveRecord:
        action: receive
        channel: {${'$'}ref: '#/channels/records'}
        messages: [{${'$'}ref: '#/channels/records/messages/record'}]
    components:
      schemas:
    """.trimIndent() + "\n" + schemas
  val fields =
    """
    required-nullable: {type: 'string?', required: true}
    required-alias: {type: NullableString, required: true}
    optional-alias: {type: NullableString, required: false}
    title: {type: string, minLength: 2, default: initial, required: false}
    description: {type: 'string?', required: false}
    state: {type: State, required: false}
    display-name: {type: string, minLength: 2, required: false}
    details: {type: Details, required: false}
    numbers: {type: array, items: 'integer?', required: false}
    labels: {type: Labels, required: false}
    """.trimIndent()
  val raml =
    """
    #%RAML 1.0
    title: Patch probe
    annotationTypes:
      sunday.patchable: {type: boolean, allowedTargets: [TypeDeclaration]}
      sunday.unknownValue: {type: string, allowedTargets: [TypeDeclaration]}
    types:
      NullableString: {type: 'string?'}
      State: {type: string, enum: [active, unknown], (sunday.unknownValue): unknown}
      Details:
        type: object
        properties:
          name: {type: string, minLength: 2}
          note: {type: string, minLength: 2, default: default-note, required: false}
          child: {type: Details, required: false}
      LabelValue: {type: string, minLength: 2}
      Labels:
        type: object
        properties:
          /.+/: LabelValue
      Base:
        type: object
        (sunday.patchable): true
        properties:
          count: {type: integer, minimum: 1, default: 5}
      SomeRequest:
        type: Base
        properties:
    FIELDS
    /request:
      put:
        displayName: updateRequest
        body:
          application/merge-patch+json: SomeRequest
        responses:
          204:
      patch:
        displayName: plainRequest
        body:
          application/json: SomeRequest
        responses:
          204:
      post:
        displayName: createRequest
        body:
          application/json: SomeRequest
        responses:
          200:
            body:
              application/json: SomeRequest
    """.trimIndent().replace("FIELDS", fields.prependIndent("      "))
  val referenced =
    if (frontend == "reference") {
      directory.resolve("models.yaml").writeText(openapi)
      openapi.replace(Regex("    SomeRequest:[\\s\\S]*?(?=    State:)")) {
        "    SomeRequest: {${'$'}ref: './models.yaml#/components/schemas/SomeRequest'}\n"
      }
    } else {
      openapi
    }
  val source = directory.resolve(if (frontend.startsWith("raml")) "patch.raml" else "patch.yaml")
  source.writeText(
    when (frontend) {
      "raml" -> raml
      "raml-auto" ->
        raml
          .replace("    (sunday.patchable): true\n", "")
      "asyncapi" -> asyncapi
      else -> referenced
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend.startsWith("composed")) {
    val events = directory.resolve("events.yaml")
    events.writeText(
      if (frontend.startsWith("composed-collisions")) {
        asyncapi
          .replace(
            "channels:\n",
            """
            channels:
              reserved:
                address: /reserved
                messages:
                  names: {payload: {${'$'}ref: '#/components/schemas/ReservedNames'}}
            """.trimIndent() + "\n",
          ).replace(
            "operations:\n",
            """
            operations:
              receiveReserved:
                action: receive
                channel: {${'$'}ref: '#/channels/reserved'}
                messages: [{${'$'}ref: '#/channels/reserved/messages/names'}]
            """.trimIndent() + "\n",
          ) +
          """

          ReservedNames:
            type: object
            properties:
              request: {${'$'}ref: '#/components/schemas/SomeRequestPatch'}
              base: {${'$'}ref: '#/components/schemas/BasePatch'}
              details: {${'$'}ref: '#/components/schemas/DetailsPatch'}
              details2: {${'$'}ref: '#/components/schemas/DetailsPatch2'}
          SomeRequestPatch: {type: object, properties: {reserved: {type: string}}}
          BasePatch: {type: object, properties: {reserved: {type: string}}}
          DetailsPatch: {type: object, properties: {reserved: {type: string}}}
          DetailsPatch2: {type: object, properties: {reserved: {type: string}}}
          """.trimIndent().prependIndent("    ") + "\n"
      } else {
        asyncapi
      },
    )
    sources += events.toUri()
  }
  if (frontend.endsWith("-reversed")) sources.reverse()
  val api = GeneratedApiIrExporter(GeneratedApiIrOptions(autoPatchable = autoPatchable)).export(sources)
  return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
}
