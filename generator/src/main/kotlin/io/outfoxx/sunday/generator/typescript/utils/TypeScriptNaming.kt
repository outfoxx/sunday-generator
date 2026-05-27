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

package io.outfoxx.sunday.generator.typescript.utils

import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

val String.typeScriptIdentifierName: String get() = toLowerCamelCase()
val String.typeScriptTypeName: String get() = toUpperCamelCase()

// TODO ensure this encompasses all valid identifiers
private val identifierRegex = """[\w\d_]+""".toRegex()

val String.isValidTypeScriptIdentifier
  get() = identifierRegex.matches(this)

val String.quotedIfNotTypeScriptIdentifier
  get() = if (isValidTypeScriptIdentifier) this else "'$this'"
