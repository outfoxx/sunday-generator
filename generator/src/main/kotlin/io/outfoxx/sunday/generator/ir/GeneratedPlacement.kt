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

import io.outfoxx.sunday.generator.python.isPythonIdentifier

/** Language-specific placement, preserved until a generation role is selected. */
data class GeneratedPlacement(
  val kotlin: Scope? = null,
  val swift: Scope? = null,
  val typescript: Scope? = null,
  val python: Scope? = null,
) {
  init {
    for (language in listOf("kotlin", "swift", "typescript", "python")) {
      language(language)?.let { readScope(it.syntax(), language, language) }
    }
  }

  /** One lexical placement scope; role blocks override category defaults at this scope. */
  data class Scope(
    val packageName: String? = null,
    val module: String? = null,
    val models: Scope? = null,
    val services: Scope? = null,
    val client: Scope? = null,
    val server: Scope? = null,
  ) {
    internal fun syntax(): Map<String, Any> =
      buildMap {
        packageName?.let { put("package", it) }
        module?.let { put("module", it) }
        models?.let { put("models", it.syntax()) }
        services?.let { put("services", it.syntax()) }
        client?.let { put("client", it.syntax()) }
        server?.let { put("server", it.syntax()) }
      }

    /** Resolves independent fields in role-first order. */
    fun resolve(
      role: String,
      category: String,
    ): Scope {
      val specific = if (role == "server") server else client

      fun Scope.category() = if (category == "service") services else models
      val layers = listOfNotNull(specific?.category(), specific, category(), this)
      return Scope(layers.firstNotNullOfOrNull { it.packageName }, layers.firstNotNullOfOrNull { it.module })
    }
  }

  /** Selects a language without changing another language's placement. */
  fun language(name: String): Scope? =
    when (name) {
      "kotlin" -> kotlin
      "swift" -> swift
      "typescript" -> typescript
      "python" -> python
      else -> error("Unknown placement language '$name'")
    }

  /** Rejects category defaults on a declaration that cannot own that category. */
  internal fun requireDeclaration(
    category: String,
    context: String,
  ) {
    for (language in listOf("kotlin", "swift", "typescript", "python")) {
      val scope = language(language) ?: continue
      for (value in listOfNotNull(scope, scope.client, scope.server)) {
        require(value.services == null && (category == "service" || value.models == null)) {
          "$context: $category placement cannot contain ${if (category == "service") "services" else "models or services"} defaults"
        }
      }
    }
  }

  companion object {
    /** Reads source syntax strictly; omitted fields inherit while null and blank fields are invalid. */
    fun read(
      value: Any?,
      context: String,
    ): GeneratedPlacement {
      val map = value as? Map<*, *> ?: error("$context must be an object")
      require(
        map.keys.all {
          it in setOf("kotlin", "swift", "typescript", "python")
        },
      ) { "$context contains unknown languages" }

      fun language(name: String) = if (map.containsKey(name)) readScope(map[name], name, "$context.$name") else null
      return GeneratedPlacement(language("kotlin"), language("swift"), language("typescript"), language("python"))
    }

    private fun readScope(
      value: Any?,
      language: String,
      context: String,
      role: Boolean = false,
      leaf: Boolean = false,
    ): Scope {
      val map = value as? Map<*, *> ?: error("$context must be an object")
      val fields =
        when (language) {
          "kotlin" -> setOf("package")
          "python" -> setOf("package", "module")
          else -> setOf("module")
        }
      val allowed =
        fields + (if (leaf) emptySet() else setOf("models", "services")) +
          (if (role || leaf) emptySet() else setOf("client", "server"))
      require(map.keys.all { it in allowed }) { "$context contains unsupported fields: ${map.keys - allowed}" }

      fun text(name: String): String? {
        if (!map.containsKey(name)) return null
        val text = map[name]
        require(text is String && text.isNotBlank()) { "$context.$name must be a non-empty string" }
        val valid =
          when (language) {
            "typescript" ->
              !text.startsWith("/") &&
                !text.contains('\\') &&
                text.split('/').all { it.isNotEmpty() && it != "." && it != ".." && !it.contains(':') }
            "swift" -> text.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))
            "python" -> text.split('.').all { it.isPythonIdentifier }
            else -> text.split('.').all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) }
          }
        require(valid) { "$context.$name is not a valid $language placement: '$text'" }
        return text
      }

      fun child(
        name: String,
        childRole: Boolean,
        childLeaf: Boolean,
      ) = if (map.containsKey(name)) readScope(map[name], language, "$context.$name", childRole, childLeaf) else null
      return Scope(
        text("package"),
        text("module"),
        child("models", role, true),
        child("services", role, true),
        child("client", true, false),
        child("server", true, false),
      )
    }
  }
}
