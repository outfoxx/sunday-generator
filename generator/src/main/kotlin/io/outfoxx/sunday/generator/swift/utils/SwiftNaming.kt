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

package io.outfoxx.sunday.generator.swift.utils

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

val Shape.swiftTypeName: String get() = name!!.toUpperCamelCase()

val PropertyShape.swiftIdentifierName: String get() = name!!.toLowerCamelCase()

val ScalarNode.swiftIdentifierName: String get() = stringValue!!.toLowerCamelCase()

private val enumSplitRegex = """\W""".toRegex()

val ScalarNode.swiftEnumName: String
  get() =
    stringValue!!
      .split(enumSplitRegex)
      .joinToString("") { s -> s.replaceFirstChar { it.titlecase() } }
      .toLowerCamelCase()

val Parameter.swiftTypeName: String get() = parameterName!!.toUpperCamelCase()
val Parameter.swiftIdentifierName: String get() = parameterName!!.toLowerCamelCase()

val Operation.swiftTypeName: String? get() = (operationId ?: name)?.toUpperCamelCase()
val Operation.swiftIdentifierName: String? get() = (operationId ?: name)?.toLowerCamelCase()

val String.swiftTypeName: String get() = toUpperCamelCase()
val String.swiftIdentifierName: String get() = toLowerCamelCase()
