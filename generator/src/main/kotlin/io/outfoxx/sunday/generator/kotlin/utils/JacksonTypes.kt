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

import com.squareup.kotlinpoet.ClassName

val JACKSON_JSON_CREATOR = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonCreator")
val JACKSON_JSON_IGNORE = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonIgnore")
val JACKSON_JSON_INCLUDE = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonInclude")
val JACKSON_JSON_INCLUDE_INCLUDE = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonInclude.Include")
const val JACKSON_JSON_INCLUDE_NON_EMPTY = "NON_EMPTY"
val JACKSON_JSON_PROPERTY = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonProperty")
val JACKSON_JSON_SUBTYPES = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonSubTypes")
val JACKSON_JSON_SUBTYPES_TYPE = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonSubTypes.Type")
val JACKSON_JSON_TYPEINFO = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonTypeInfo")
val JACKSON_JSON_TYPEINFO_ID = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonTypeInfo.Id")
const val JACKSON_JSON_TYPEINFO_ID_NAME = "NAME"
val JACKSON_JSON_TYPEINFO_AS = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonTypeInfo.As")
const val JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY = "EXTERNAL_PROPERTY"
const val JACKSON_JSON_TYPEINFO_AS_EXISTING_PROPERTY = "EXISTING_PROPERTY"
val JACKSON_JSON_TYPENAME = ClassName.bestGuess("com.fasterxml.jackson.annotation.JsonTypeName")

val JSON_NODE = ClassName.bestGuess("com.fasterxml.jackson.databind.JsonNode")
val OBJECT_MAPPER = ClassName.bestGuess("com.fasterxml.jackson.databind.ObjectMapper")
