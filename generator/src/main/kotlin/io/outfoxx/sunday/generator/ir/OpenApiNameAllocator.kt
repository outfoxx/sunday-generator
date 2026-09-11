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

import io.outfoxx.sunday.generator.utils.toUpperCamelCase

/** Reserves the spelling shared by language emitters without renaming existing declarations. */
internal class OpenApiNameAllocator(
  declaredNames: Iterable<String> = emptyList(),
) {
  private val reserved = declaredNames.mapTo(mutableSetOf(), String::toUpperCamelCase)

  fun reserveAll(names: Iterable<String>) {
    names.mapTo(reserved, String::toUpperCamelCase)
  }

  fun allocate(hint: String): String {
    var name = hint
    var suffix = 2
    while (!reserved.add(name.toUpperCamelCase())) name = "$hint${suffix++}"
    return name
  }
}
