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

import io.outfoxx.sunday.generator.genError

/** Strict source reader shared by environment-scoped policy and security extensions. */
internal object GeneratedEnvironmentReader {
  fun <T> read(
    value: Any?,
    location: String,
    readValue: (Any?, String) -> T,
  ): GeneratedEnvironment<T> {
    val scopes = objectValue(value, location, setOf("all", "client", "server", "profiles"))

    fun selected(name: String): T? = if (scopes.containsKey(name)) readValue(scopes[name], "$location.$name") else null
    val profiles =
      if (scopes.containsKey(
          "profiles",
        )
      ) {
        objectValue(scopes["profiles"], "$location.profiles")
      } else {
        emptyMap()
      }
    return GeneratedEnvironment(
      all = selected("all"),
      client = selected("client"),
      server = selected("server"),
      profiles =
        profiles.entries.associate { (profile, value) ->
          if (profile.isBlank()) genError("$location.profiles requires non-blank profile names")
          val path = "$location.profiles.$profile"
          val scope = objectValue(value, path, setOf("all", "client", "server"))

          fun role(name: String): T? = if (scope.containsKey(name)) readValue(scope[name], "$path.$name") else null
          profile to GeneratedEnvironment.Scope(all = role("all"), client = role("client"), server = role("server"))
        },
    )
  }

  fun objectValue(
    value: Any?,
    location: String,
    allowed: Set<String>? = null,
  ): Map<String, Any?> {
    val map = value as? Map<*, *> ?: genError("$location must be an object")
    val result =
      map.entries.associate { (key, item) ->
        (key as? String ?: genError("$location requires string member names")) to item
      }
    allowed?.let {
      val unknown = result.keys - allowed
      if (unknown.isNotEmpty()) genError("Unsupported $location member(s): ${unknown.sorted().joinToString()}")
    }
    return result
  }
}
