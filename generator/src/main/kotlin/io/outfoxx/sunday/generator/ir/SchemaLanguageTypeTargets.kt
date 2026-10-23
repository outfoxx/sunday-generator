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

/** Reads language mappings shared by native schema frontends. */
internal fun Map<*, *>.languageTypeTargets(context: String): Map<String, GeneratedTarget> =
  mapOf(
    "x-sunday-kotlinType" to "kotlin",
    "x-sunday-kotlinType:client" to "kotlinClient",
    "x-sunday-kotlinType:server" to "kotlinServer",
    "x-sunday-swiftType" to "swift",
    "x-sunday-typeScriptType" to "typescript",
    "x-sunday-pythonType" to "python",
  ).mapNotNull { (extension, target) ->
    if (!containsKey(extension)) return@mapNotNull null
    val value = this[extension]
    require(value is String && value.isNotBlank()) {
      "$context $extension must be a non-empty language type name"
    }
    val module = if (target == "typescript") this["x-sunday-typeScriptImport"] else null
    require(module == null || module is String && module.isNotBlank()) {
      "$context x-sunday-typeScriptImport must be a non-empty module name"
    }
    target to GeneratedTarget(typeName = value, moduleName = module)
  }.toMap()
