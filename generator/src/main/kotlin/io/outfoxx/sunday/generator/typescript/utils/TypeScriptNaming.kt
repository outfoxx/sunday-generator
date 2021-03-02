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

val ScalarNode.typeScriptEnumName: String get() = stringValue!!.toUpperCamelCase()

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
