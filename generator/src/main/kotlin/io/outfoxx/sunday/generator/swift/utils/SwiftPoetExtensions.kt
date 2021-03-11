package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.ParameterizedTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.parameterizedBy


/**
 * Extension methods for SwiftPoet classes/types
 */

fun TypeName.array(): TypeName {
  return ARRAY.parameterizedBy(this)
}
