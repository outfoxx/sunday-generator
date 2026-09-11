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

package io.outfoxx.sunday.generator.ir

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.GenerationException

/** Carries diagnostic provenance and bound dialect through normalization without adding fields to the document or IR. */
internal class OpenApiSchema(
  fields: Map<String, Any?>,
  private val source: Source,
  private val pointer: String,
  val usesBooleanExclusiveBounds: Boolean = false,
) : LinkedHashMap<String, Any?>(fields) {
  fun withFields(
    fields: Map<String, Any?>,
    usesBooleanExclusiveBounds: Boolean = this.usesBooleanExclusiveBounds,
  ): OpenApiSchema = OpenApiSchema(fields, source, pointer, usesBooleanExclusiveBounds)

  fun error(message: String): Nothing {
    val location = source.location(pointer)
    throw GenerationException(message, source.uri, location.lineNr, location.columnNr)
  }

  class Source(
    val uri: String,
    private val bytes: ByteArray,
  ) {
    fun location(pointer: String): JsonLocation {
      YAMLFactory().createParser(bytes).use { parser ->
        while (parser.nextToken() != null) {
          if (parser.parsingContext.pathAsPointer().toString() == pointer) return parser.currentTokenLocation()
        }
      }
      return JsonLocation.NA
    }
  }
}
