package io.outfoxx.sunday.generator.kotlin

import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.PropertyShape
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import io.outfoxx.sunday.generator.name
import io.outfoxx.sunday.generator.operationId
import io.outfoxx.sunday.generator.parameterName
import io.outfoxx.sunday.generator.scalarValue
import io.outfoxx.sunday.generator.stringValue
import io.outfoxx.sunday.generator.toLowerCamelCase
import io.outfoxx.sunday.generator.toUpperCamelCase

val Shape.kotlinTypeName: String get() = name!!.toUpperCamelCase()

val PropertyShape.kotlinIdentifierName: String get() = name!!.toLowerCamelCase()

val ScalarNode.kotlinEnumName: String get() = stringValue!!.toUpperCamelCase()

val Parameter.kotlinTypeName: String get() = parameterName!!.toUpperCamelCase()
val Parameter.kotlinIdentifierName: String get() = parameterName!!.toLowerCamelCase()

val Operation.kotlinTypeName: String? get() = (operationId ?: name)?.toUpperCamelCase()
val Operation.kotlinIdentifierName: String? get() = (operationId ?: name)?.toLowerCamelCase()

val String.kotlinIdentifierName: String get() = toLowerCamelCase()
