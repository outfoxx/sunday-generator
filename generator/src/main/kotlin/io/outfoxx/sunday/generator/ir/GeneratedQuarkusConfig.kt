/*
 * Copyright 2020 Outfox, Inc.
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

import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.utils.GeneratedProperties

/** Role/profile-scoped native configuration; server is a declared server name or zero-based index. */
data class GeneratedQuarkusConfig(
  val properties: Map<String, String> = emptyMap(),
  val server: String? = null,
) {
  /** Applies a more-local configuration declaration. */
  fun merge(other: GeneratedQuarkusConfig): GeneratedQuarkusConfig =
    GeneratedQuarkusConfig(properties + other.properties, other.server ?: server)

  /** Combines peer declarations without silently choosing a conflicting contract. */
  fun combine(other: GeneratedQuarkusConfig): GeneratedQuarkusConfig {
    require(server == null || other.server == null || server == other.server) { "Conflicting Quarkus server selectors" }
    val combined = properties.toMutableMap()
    GeneratedProperties.merge(combined, other.properties, "composed Quarkus configuration")
    return GeneratedQuarkusConfig(combined, server ?: other.server)
  }

  companion object {
    /** Reads native configuration through the common role/profile scope parser. */
    fun read(
      value: Any?,
      location: String,
    ): GeneratedEnvironment<GeneratedQuarkusConfig>? =
      value?.let {
        GeneratedEnvironmentReader.read(it, location) { raw, path ->
          val fields = GeneratedEnvironmentReader.objectValue(raw, path, setOf("properties", "server"))
          val server = fields["server"]
          require(server == null || server is String || server is Int) { "$path.server must be a server name or index" }
          GeneratedQuarkusConfig(readProperties(fields["properties"], "$path.properties"), server?.toString())
        }
      }

    /** Converts scalar native values without accepting accidental nested objects or nulls. */
    internal fun readProperties(
      value: Any?,
      path: String,
    ): Map<String, String> =
      if (value == null) {
        emptyMap()
      } else {
        GeneratedEnvironmentReader.objectValue(value, path).mapValues { (key, item) ->
          if (key.isBlank() || item !is String && item !is Boolean && item !is Number) {
            genError("$path.$key must be a non-null scalar native property")
          }
          item.toString()
        }
      }
  }
}
