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

/** Patch payloads containing polymorphic objects and collections across source frontends. */
internal fun polymorphicPatchApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val openapi = requireNotNull(GeneratedApi::class.java.getResource("/openapi/polymorphic-patches.yaml")).readText()
  // AsyncAPI currently projects null unions at use sites; keep named aliases transparent there.
  val asyncapiSchemas =
    openapi
      .substringAfter("  schemas:\n")
      .replace(
        "anyOf: [{${'$'}ref: '#/components/schemas/AnimalList'}, {type: 'null'}]",
        "${'$'}ref: '#/components/schemas/AnimalList'",
      ).replace(
        "allOf: [{${'$'}ref: '#/components/schemas/NullableAnimalList'}]",
        "${'$'}ref: '#/components/schemas/NullableAnimalList'",
      ).replace(
        "{${'$'}ref: '#/components/schemas/NullableAnimalListAlias'}",
        "{anyOf: [{${'$'}ref: '#/components/schemas/NullableAnimalListAlias'}, {type: 'null'}]}",
      )
  val asyncapi =
    """
    asyncapi: 3.0.0
    info: {title: Polymorphic Patches, version: 1.0.0}
    channels:
      zoo:
        address: /zoo
        messages:
          zoo: {payload: {${'$'}ref: '#/components/schemas/Zoo'}}
    operations:
      receiveZoo:
        action: receive
        channel: {${'$'}ref: '#/channels/zoo'}
        messages: [{${'$'}ref: '#/channels/zoo/messages/zoo'}]
    components:
      schemas:
    """.trimIndent() + "\n" + asyncapiSchemas
  val source = directory.resolve(if (frontend == "raml") "zoo.raml" else "zoo.yaml")
  source.writeText(
    when (frontend) {
      "raml" -> requireNotNull(GeneratedApi::class.java.getResource("/raml/polymorphic-patches.raml")).readText()
      "asyncapi" -> asyncapi
      else -> openapi
    },
  )
  val sources = mutableListOf(source.toUri())
  if (frontend == "composed") {
    val events = directory.resolve("events.yaml")
    // These equivalent wire fields use different nullable alias declarations in the two frontends.
    events.writeText(asyncapi.replace("NullableAnimalList", "EventNullableAnimalList").replace("Zoo", "EventZoo"))
    sources += events.toUri()
  }
  return GeneratedApiIrExporter().export(sources)
}
