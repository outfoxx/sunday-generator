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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.genError

/** Renders JSON-compatible defaults without losing collection entries or explicit nulls. */
internal fun Any?.pythonValueCode(): PythonCodeBlock =
  when (this) {
    null -> PythonCodeBlock.of("None")
    is Boolean -> PythonCodeBlock.of(if (this) "True" else "False")
    is Number -> PythonCodeBlock.of("%L", this)
    is String -> PythonCodeBlock.of("%S", this)
    is List<*> -> PythonCodeBlock.of("[%C]", PythonCodeBlock.join(map { it.pythonValueCode() }, separator = ", "))
    is Map<*, *> ->
      PythonCodeBlock.of(
        "{%C}",
        PythonCodeBlock.join(
          entries.map { (key, value) ->
            if (key !is String) genError("Python default object keys must be strings")
            PythonCodeBlock.of("%S: %C", key, value.pythonValueCode())
          },
          separator = ", ",
        ),
      )
    else -> genError("Unsupported Python default value '$this'")
  }
