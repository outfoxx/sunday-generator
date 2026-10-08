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

package io.outfoxx.sunday.generator.utils

import java.nio.file.Files
import java.nio.file.Path

/** Deterministic application-default resources shared by generation and packaging. */
object GeneratedProperties {
  /** Accepts portable relative properties paths without application-owned configuration files. */
  fun validatePath(name: String) {
    require(
      name.isNotBlank() &&
        name.none { it.code < 32 } &&
        !name.startsWith("/") &&
        !name.contains('\\') &&
        !name.contains(':') &&
        name.split('/').none { it.isBlank() || it == "." || it == ".." } &&
        name.endsWith(".properties") &&
        !name.substringAfterLast('/').equals("application.properties", ignoreCase = true),
    ) {
      "Application metadata must be an output-relative .properties resource path (not application.properties): $name"
    }
  }

  /** Rejects ambiguous defaults instead of depending on input or classpath ordering. */
  fun merge(
    target: MutableMap<String, String>,
    values: Map<String, String>,
    owner: String,
  ) {
    values.forEach { (key, value) ->
      require(key !in target || target[key] == value) {
        "Conflicting generated property '$key' in $owner: '${target[key]}' versus '$value'"
      }
      target[key] = value
    }
  }

  /** Writes timestamp-free Java properties; refuses existing files and symlink traversal. */
  fun write(
    root: Path,
    name: String,
    values: Map<String, String>,
  ) {
    val destination = destination(root, name)
    Files.createDirectories(destination.parent)
    Files.writeString(destination, render(values), java.nio.file.StandardOpenOption.CREATE_NEW)
  }

  /** Preflights a resource destination before publishing generated sources. */
  fun destination(
    root: Path,
    name: String,
  ): Path {
    validatePath(name)
    require(!Files.isSymbolicLink(root)) { "Generated resource root is a symlink: $root" }
    val destination = root.resolve(name)
    var ancestor: Path? = destination
    while (ancestor != null && ancestor != root) {
      require(!Files.isSymbolicLink(ancestor)) { "Generated resource path traverses a symlink: $destination" }
      ancestor = ancestor.parent
    }
    require(!Files.exists(destination)) {
      "Generated resource already exists: $destination; use separate output directories or configured resource paths"
    }
    return destination
  }

  /** Serializes properties with one escaping layer, preserving expressions and list escapes. */
  fun render(values: Map<String, String>): String =
    (values + ("config_ordinal" to "100")).toSortedMap().entries.joinToString("\n", postfix = "\n") {
      escape(it.key, true) + "=" + escape(it.value, false)
    }

  private fun escape(
    value: String,
    key: Boolean,
  ): String =
    buildString {
      value.forEachIndexed { index, char ->
        append(
          when (char) {
            '\\' -> "\\\\"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            '=', ':', '#', '!' -> if (key) "\\$char" else char.toString()
            ' ' -> if (key || index == 0) "\\ " else " "
            else -> if (char.code < 32 || char.code > 126) "\\u%04x".format(char.code) else char.toString()
          },
        )
      }
    }
}
