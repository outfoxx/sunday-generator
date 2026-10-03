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

/** Merge-patch presence contracts across native, RAML patch-only, and composed source inputs. */
internal fun patchableApi(
  frontend: String,
  directory: Path,
  autoPatchable: Boolean = true,
): GeneratedApi {
  val openapi = requireNotNull(GeneratedApi::class.java.getResource("/openapi/patchable.yaml")).readText()
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
      Base:
        type: object
        properties:
          count: {type: integer, minimum: 1, default: 5}
      SomeRequest:
        type: Base
        properties:
    FIELDS
      BasePatch:
        type: object
        (sunday.patchable): true
        properties:
          count: {type: integer, minimum: 1, default: 5}
      SomeRequestPatch:
        type: BasePatch
        properties:
    FIELDS
    /request:
      put:
        displayName: updateRequest
        body:
          application/merge-patch+json: SomeRequestPatch
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
          .replace(Regex("  BasePatch:[\\s\\S]*?(?=/request:)"), "")
          .replace("application/merge-patch+json: SomeRequestPatch", "application/merge-patch+json: SomeRequest")
      "asyncapi" -> asyncapi
      else -> referenced
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    events.writeText(asyncapi)
    sources += events.toUri()
  }
  val api = GeneratedApiIrExporter(GeneratedApiIrOptions(autoPatchable = autoPatchable)).export(sources)
  return GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))
}
