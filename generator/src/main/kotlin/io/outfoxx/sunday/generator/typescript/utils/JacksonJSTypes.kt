package io.outfoxx.sunday.generator.typescript.utils

import io.outfoxx.typescriptpoet.SymbolSpec

const val JACKSON_PKG = "@outfoxx/jackson-js"
val JSON_CLASS_TYPE = SymbolSpec.from("JsonClassType@$JACKSON_PKG")
val JSON_IGNORE = SymbolSpec.from("JsonIgnore@$JACKSON_PKG")
val JSON_PROPERTY = SymbolSpec.from("JsonProperty@$JACKSON_PKG")
val JSON_SUB_TYPES = SymbolSpec.from("JsonSubTypes@$JACKSON_PKG")
val JSON_TYPE_INFO = SymbolSpec.from("JsonTypeInfo@$JACKSON_PKG")
val JSON_TYPE_INFO_ID = SymbolSpec.from("JsonTypeInfoId@$JACKSON_PKG")
val JSON_TYPE_INFO_AS = SymbolSpec.from("JsonTypeInfoAs@$JACKSON_PKG")
val JSON_TYPE_NAME = SymbolSpec.from("JsonTypeName@$JACKSON_PKG")
