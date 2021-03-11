package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.swiftpoet.ANY
import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.DICTIONARY
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.parameterizedBy

const val SWIFT_MODULE = "Swift"
val CODABLE = typeName("$SWIFT_MODULE.Codable")
val ENCODABLE = typeName("$SWIFT_MODULE.Encodable")
val DECODABLE = typeName("$SWIFT_MODULE.Decodable")
val DECODER = typeName("$SWIFT_MODULE.Decoder")
val DECODING_ERROR = typeName("$SWIFT_MODULE.DecodingError")
val ENCODER = typeName("$SWIFT_MODULE.Encoder")
val ENCODING_ERROR = typeName("$SWIFT_MODULE.EncodingError")
val CODING_KEY = typeName("$SWIFT_MODULE.CodingKey")

val CUSTOM_STRING_CONVERTIBLE = typeName("$SWIFT_MODULE.CustomDebugStringConvertible")

val DICTIONARY_STRING_ANY = DICTIONARY.parameterizedBy(STRING, ANY)
val DICTIONARY_STRING_ANY_OPTIONAL = DICTIONARY.parameterizedBy(STRING, ANY.makeOptional())

val ARRAY_ANY = ARRAY.parameterizedBy(ANY)
val ARRAY_ANY_OPTIONAL = ARRAY.parameterizedBy(ANY.makeOptional())

const val FOUNDATION_MODULE = "Foundation"
val DATE = typeName("$FOUNDATION_MODULE.Date")
val DECIMAL = typeName("$FOUNDATION_MODULE.Decimal")
val URL = typeName("$FOUNDATION_MODULE.URL")
