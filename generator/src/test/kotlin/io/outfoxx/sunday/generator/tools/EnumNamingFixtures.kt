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

/** Loads identical enum contracts through each frontend and through multi-source composition. */
internal fun enumNamingApi(source: String): GeneratedApi {
  val resources =
    listOf("raml/ir/enum-naming.raml", "openapi/ir/enum-naming.yaml", "asyncapi/ir/enum-naming.yaml")
  val selected = if (source == "composed") resources else resources.filter { it.startsWith("$source/") }
  val uris = selected.map { requireNotNull(object {}.javaClass.classLoader.getResource(it)).toURI() }
  return GeneratedApiIrExporter().export(uris)
}

/** Expected public names are explicit so tests do not duplicate the production conversion. */
internal val enumNamingUpperCamelNames =
  listOf(
    "BackgroundTask",
    "UserInitiatedTask",
    "Immediate",
    "LowerSnake",
    "KebabCase",
    "DottedCase",
    "SpaceSeparated",
    "RenderCharacterReference",
    "LowerCamelCase",
    "UpperCamelCase",
    "HttpServer",
    "ParseUrlValue",
    "Http2Response",
    "Version2Value",
    "UnknownValue",
  )

internal val enumNamingSnakeNames =
  listOf(
    "background_task",
    "user_initiated_task",
    "immediate",
    "lower_snake",
    "kebab_case",
    "dotted_case",
    "space_separated",
    "render_character_reference",
    "lower_camel_case",
    "upper_camel_case",
    "http_server",
    "parse_url_value",
    "http2_response",
    "version2_value",
    "unknown_value",
  )
