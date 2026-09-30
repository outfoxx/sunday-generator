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

/** OpenAPI pattern schemas shared by compile-backed runtime tests. */
internal fun patternModelsApi(
  directory: Path,
  composed: Boolean = false,
): GeneratedApi {
  val source = directory.resolve("patterns.yaml")
  source.writeText(requireNotNull(GeneratedApi::class.java.getResource("/openapi/ir/pattern-models.yaml")).readText())
  if (!composed) return GeneratedApiIrExporter().export(source.toUri())
  val events = directory.resolve("events.yaml")
  events.writeText(
    """
    asyncapi: 3.0.0
    info: {title: Pattern models, version: 1.0.0}
    channels: {}
    operations: {}
    """.trimIndent(),
  )
  return GeneratedApiIrExporter().export(listOf(source.toUri(), events.toUri()))
}

/** Valid and invalid wire values exercise matching, intersections, constraints, nulls and nested types. */
internal val patternModelValid =
  listOf(
    """{"name":"known","x-value":"ok","x-end":"okay","n-count":2,"v-list":[1,2],"r-object":{"label":"child"},"maybe-value":null,"alias-value":"valid","ref-enum-value":"first","enum-value":"yes-value","const-value":3,"inline-object":{"label":"child"}}""",
    """{"name":"known","x-fixed":"okay","end":"ok"}""",
  )

internal val patternModelInvalid =
  listOf(
    """{"alias-value":1}""",
    """{"alias-value":"ab"}""",
    """{"ref-enum-value":"invalid"}""",
    """{"ref-enum-value":0}""",
    """{"enum-value":"invalid"}""",
    """{"const-value":4}""",
    """{"inline-object":{"extra":1}}""",
    """{"n-count":1.5}""",
    """{"v-list":[1.5]}""",
    """{"extra":1}""",
    """{"extra":null}""",
    """{"prefix-x-value":"ok"}""",
    """{"x-value":1}""",
    """{"x-value":null}""",
    """{"x-value":{}}""",
    """{"x-value":[]}""",
    """{"x-value":"a"}""",
    """{"x-end":"bad"}""",
    """{"x-fixed":"a"}""",
    """{"n-count":0}""",
    """{"n-count":"2"}""",
    """{"n-count":true}""",
    """{"v-list":["wrong"]}""",
    """{"r-object":{"extra":1}}""",
    """{"maybe-value":1}""",
  )
