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

import io.outfoxx.typescriptpoet.SymbolSpec

const val JACKSON_PKG = "@outfoxx/jackson-js"
val JSON_CLASS_TYPE = SymbolSpec.from("JsonClassType@$JACKSON_PKG")
val JSON_IGNORE = SymbolSpec.from("JsonIgnore@$JACKSON_PKG")
val JSON_INCLUDE = SymbolSpec.from("JsonInclude@$JACKSON_PKG")
val JSON_INCLUDE_TYPE = SymbolSpec.from("JsonIncludeType@$JACKSON_PKG")
val JSON_PROPERTY = SymbolSpec.from("JsonProperty@$JACKSON_PKG")
val JSON_SUB_TYPES = SymbolSpec.from("JsonSubTypes@$JACKSON_PKG")
val JSON_TYPE_INFO = SymbolSpec.from("JsonTypeInfo@$JACKSON_PKG")
val JSON_TYPE_INFO_ID = SymbolSpec.from("JsonTypeInfoId@$JACKSON_PKG")
val JSON_TYPE_INFO_AS = SymbolSpec.from("JsonTypeInfoAs@$JACKSON_PKG")
val JSON_TYPE_NAME = SymbolSpec.from("JsonTypeName@$JACKSON_PKG")
