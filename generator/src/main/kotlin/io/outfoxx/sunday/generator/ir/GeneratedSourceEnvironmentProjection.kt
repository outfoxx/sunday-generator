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

import io.outfoxx.sunday.generator.GenerationContext
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.emit.securityProfileNames

/** Projects OpenAPI/AsyncAPI deployment annotations while preserving source schemas, references, and wire security. */
object GeneratedSourceEnvironmentProjection {
  /**
   * Returns a new source tree containing only the selected role/profile metadata.
   * Run after aggregation has applied operation visibility and pruned unused components.
   * Source conversion still validates selected providers and complete security alternatives.
   */
  fun project(
    document: Map<String, Any?>,
    context: GenerationContext,
  ): Map<String, Any?> = projectObject(document, context, emptyList())

  private fun projectObject(
    node: Map<String, Any?>,
    context: GenerationContext,
    path: List<String>,
    namedEntries: Boolean = false,
  ): Map<String, Any?> =
    buildMap {
      node.forEach { (name, value) ->
        val location = path + name
        if (!namedEntries && name in setOf("x-sunday-policy", "x-sunday-security")) {
          val projected = projectAnnotation(name, value, context, location)
          if (projected != null) put(name, projected)
        } else if (!namedEntries && name in literalFields) {
          put(name, value)
        } else {
          put(name, projectValue(value, context, location, !namedEntries && name in namedContainers))
        }
      }
    }

  private fun projectValue(
    value: Any?,
    context: GenerationContext,
    path: List<String>,
    namedEntries: Boolean,
  ): Any? =
    when (value) {
      is Map<*, *> ->
        projectObject(
          GeneratedEnvironmentReader.objectValue(value, path.joinToString(".")),
          context,
          path,
          namedEntries,
        )
      is List<*> -> value.mapIndexed { index, item -> projectValue(item, context, path + index.toString(), false) }
      else -> value
    }

  private fun projectAnnotation(
    name: String,
    value: Any?,
    context: GenerationContext,
    path: List<String>,
  ): Map<String, Any?>? {
    val location = path.joinToString(".")
    val binding = name == "x-sunday-security" && "securitySchemes" in path
    when {
      name == "x-sunday-policy" -> GeneratedPolicyReader.read(value, location)
      binding -> GeneratedSecurityReader.binding(value, location)
      else -> GeneratedSecurityReader.selection(value, location)
    }
    val environment =
      GeneratedEnvironmentReader.read(
        value,
        location,
      ) { raw, field -> GeneratedEnvironmentReader.objectValue(raw, field) }
    if (name == "x-sunday-security" &&
      context.profile == null &&
      environment.securityProfileNames(context).isNotEmpty()
    ) {
      genError("$location requires an explicit profile for ${context.role.name.lowercase()} projection")
    }
    // Retain an empty binding when this scheme has no provider for the chosen environment. A downstream
    // generator must diagnose a used, unconfigured scheme instead of switching to manual authentication.
    val selected = environment.resolve(context, ::merge) ?: if (binding) emptyMap() else return null
    val scope = mapOf(context.role.name.lowercase() to selected)
    return context.profile?.let { mapOf("profiles" to mapOf(it to scope)) } ?: scope
  }

  private fun merge(
    base: Map<String, Any?>,
    override: Map<String, Any?>,
  ): Map<String, Any?> =
    base +
      override.mapValues { (key, value) ->
        val previous = base[key]
        if (key != "alternative" && previous is Map<*, *> && value is Map<*, *>) {
          merge(
            GeneratedEnvironmentReader.objectValue(previous, key),
            GeneratedEnvironmentReader.objectValue(value, key),
          )
        } else {
          value
        }
      }

  private val literalFields = setOf("example", "examples", "default", "const", "enum", "security")
  private val namedContainers =
    setOf(
      "properties",
      "patternProperties",
      "schemas",
      "securitySchemes",
      "headers",
      "responses",
      "parameters",
      "requestBodies",
      "scopes",
      "mapping",
      "callbacks",
      "links",
      "paths",
      "webhooks",
      "channels",
      "operations",
      "messages",
      "servers",
    )
}
