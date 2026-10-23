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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.GeneratedDocumentUri
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/** Invocation fallbacks and deliberate relocation, independent of output selection. */
data class GeneratedPlacementConfiguration(
  val defaults: GeneratedPlacement = GeneratedPlacement(),
  val relocations: List<Relocation> = emptyList(),
) {
  /** One relocation with an absolute document identity and decoded source pointer. */
  data class Relocation(
    val target: String,
    val kind: String? = null,
    val tags: Set<String> = emptySet(),
    val placement: GeneratedPlacement,
  ) {
    init {
      val uri = URI(target)
      require(uri.isAbsolute) { "Placement target must be absolute: $target" }
      require(kind == null || kind in setOf("model", "service", "problem")) { "Unknown placement kind '$kind'" }
      require(tags.all { it.isNotBlank() }) { "Placement tags must be non-empty names" }
      require(tags.isEmpty() || kind == "service") { "Placement tags require kind: service" }
      val pointer = uri.fragment.orEmpty()
      require(pointer.isEmpty() || pointer.startsWith('/')) { "Placement fragment must be a JSON Pointer: $target" }
      require(!Regex("~(?![01])").containsMatchIn(pointer)) { "Invalid JSON Pointer escape: $target" }
    }
  }

  companion object {
    /** Loads configuration with references relative to the configuration file, never the working directory. */
    fun read(path: Path): GeneratedPlacementConfiguration {
      val root = ObjectMapper(YAMLFactory()).readValue(Files.readString(path), Map::class.java)
      require(root.keys.all { it in setOf("defaults", "relocations") }) {
        "Unknown placement configuration fields"
      }
      val defaults =
        if (root.containsKey(
            "defaults",
          )
        ) {
          GeneratedPlacement.read(root["defaults"], "defaults")
        } else {
          GeneratedPlacement()
        }
      val rules =
        if (root.containsKey("relocations")) {
          root["relocations"] as? List<*> ?: error("relocations must be an array")
        } else {
          emptyList<Any>()
        }
      return GeneratedPlacementConfiguration(
        defaults,
        rules.mapIndexed { index, value ->
          val rule = value as? Map<*, *> ?: error("relocations[$index] must be an object")
          require(rule.keys.all { it in setOf("select", "placement") }) { "Unknown relocation fields at $index" }
          val select = rule["select"] as? Map<*, *> ?: error("relocations[$index].select must be an object")
          require(select.keys.all { it in setOf("target", "kind", "tags") }) { "Unknown selector fields at $index" }
          val reference = select["target"] as? String ?: error("relocations[$index].select.target must be a reference")
          require(reference.isNotBlank()) { "Empty placement target" }
          val resolved = path.toAbsolutePath().toUri().resolve(URI(reference))
          val document = GeneratedDocumentUri.canonical(resolved)
          val target = document.toString() + (resolved.rawFragment?.let { "#$it" } ?: "")
          val kind =
            if (select.containsKey(
                "kind",
              )
            ) {
              select["kind"] as? String ?: error("kind must be a string")
            } else {
              null
            }
          val tags =
            if (select.containsKey("tags")) {
              val entries = select["tags"] as? List<*> ?: error("tags must be an array")
              require(
                entries.isNotEmpty() && entries.all { it is String && it.isNotBlank() },
              ) { "tags must contain non-empty names" }
              entries.filterIsInstance<String>().toSet()
            } else {
              emptySet()
            }
          Relocation(target, kind, tags, GeneratedPlacement.read(rule["placement"], "relocations[$index].placement"))
        },
      )
    }
  }
}

/** Selects the single invocation configuration supplied through either library entry point. */
internal fun GeneratedPlacementConfiguration.orInvocation(
  other: GeneratedPlacementConfiguration,
): GeneratedPlacementConfiguration {
  val empty = GeneratedPlacementConfiguration()
  require(this == empty || other == empty || this == other) { "Specify one placement configuration per invocation" }
  return if (this == empty) other else this
}
