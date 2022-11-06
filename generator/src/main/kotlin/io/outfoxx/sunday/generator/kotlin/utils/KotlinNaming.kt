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

package io.outfoxx.sunday.generator.kotlin.utils

import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Parameter
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.operationId
import io.outfoxx.sunday.generator.utils.parameterName
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase

val Shape.kotlinTypeName: String get() = name!!.toUpperCamelCase()

val PropertyShape.kotlinIdentifierName: String get() = name!!.toLowerCamelCase()

private val enumSplitRegex = """\W""".toRegex()

val ScalarNode.kotlinEnumName: String
  get() = stringValue!!
    .split(enumSplitRegex)
    .joinToString("") { s -> s.replaceFirstChar { it.titlecase() } }
    .toUpperCamelCase()

val Parameter.kotlinTypeName: String get() = parameterName!!.toUpperCamelCase()
val Parameter.kotlinIdentifierName: String get() = parameterName!!.toLowerCamelCase()

val Operation.kotlinTypeName: String? get() = (operationId ?: name)?.toUpperCamelCase()
val Operation.kotlinIdentifierName: String? get() = (operationId ?: name)?.toLowerCamelCase()

val String.kotlinTypeName: String get() = toUpperCamelCase()
val String.kotlinIdentifierName: String get() = toLowerCamelCase()
