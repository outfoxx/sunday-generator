package io.outfoxx.sunday.generator.swift.utils

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

val Shape.swiftTypeName: String get() = name!!.toUpperCamelCase()

val PropertyShape.swiftIdentifierName: String get() = name!!.toLowerCamelCase()

val ScalarNode.swiftIdentifierName: String get() = stringValue!!.toLowerCamelCase()

val Parameter.swiftTypeName: String get() = parameterName!!.toUpperCamelCase()
val Parameter.swiftIdentifierName: String get() = parameterName!!.toLowerCamelCase()

val Operation.swiftTypeName: String? get() = (operationId ?: name)?.toUpperCamelCase()
val Operation.swiftIdentifierName: String? get() = (operationId ?: name)?.toLowerCamelCase()

val String.swiftTypeName: String get() = toUpperCamelCase()
val String.swiftIdentifierName: String get() = toLowerCamelCase()
