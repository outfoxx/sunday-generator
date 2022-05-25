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

import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.PropertyShape
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.operationId
import io.outfoxx.sunday.generator.utils.parameterName
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

val Shape.typeScriptTypeName: String get() = name!!.toUpperCamelCase()

val PropertyShape.typeScriptIdentifierName: String get() = name!!.toLowerCamelCase()

private val enumSplitRegex = """\W""".toRegex()

val ScalarNode.typeScriptEnumName: String
  get() = stringValue!!
    .split(enumSplitRegex)
    .joinToString("") { s -> s.replaceFirstChar { it.titlecase() } }
    .toUpperCamelCase()

val Parameter.typeScriptTypeName: String get() = parameterName!!.toUpperCamelCase()
val Parameter.typeScriptIdentifierName: String get() = parameterName!!.toLowerCamelCase()

val Operation.typeScriptTypeName: String? get() = (operationId ?: name)?.toUpperCamelCase()
val Operation.typeScriptIdentifierName: String? get() = (operationId ?: name)?.toLowerCamelCase()

val String.typeScriptIdentifierName: String get() = toLowerCamelCase()
val String.typeScriptTypeName: String get() = toUpperCamelCase()

// TODO ensure this encompasses all valid identifiers
private val identifierRegex = """[\w\d_]+""".toRegex()

val String.isValidTypeScriptIdentifier
  get() = identifierRegex.matches(this)

val String.quotedIfNotTypeScriptIdentifier
  get() = if (isValidTypeScriptIdentifier) this else "'$this'"
