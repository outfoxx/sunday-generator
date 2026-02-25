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
import io.outfoxx.typescriptpoet.TypeName

const val SUNDAY_PKG = "@outfoxx/sunday"
const val ZOD_PKG = "zod"
val REQUEST_FACTORY = TypeName.namedImport("RequestFactory", SUNDAY_PKG)
val SCHEMA_LIKE = TypeName.namedImport("SchemaLike", SUNDAY_PKG)
val SCHEMA_RUNTIME = TypeName.namedImport("SchemaRuntime", SUNDAY_PKG)
val DEFINE_SCHEMA = SymbolSpec.importsName("defineSchema", SUNDAY_PKG)
val CREATE_PROBLEM_CODEC = SymbolSpec.importsName("createProblemCodec", SUNDAY_PKG)
val PROBLEM_WIRE_SCHEMA = TypeName.namedImport("ProblemWireSchema", SUNDAY_PKG)
val Z = TypeName.namedImport("z", ZOD_PKG)

val STRING_SCHEMA = TypeName.namedImport("StringSchema", SUNDAY_PKG)
val NUMBER_SCHEMA = TypeName.namedImport("NumberSchema", SUNDAY_PKG)
val BOOLEAN_SCHEMA = TypeName.namedImport("BooleanSchema", SUNDAY_PKG)
val ARRAY_BUFFER_SCHEMA = TypeName.namedImport("ArrayBufferSchema", SUNDAY_PKG)
val URL_SCHEMA = TypeName.namedImport("URLSchema", SUNDAY_PKG)
val DATE_SCHEMA = TypeName.namedImport("DateSchema", SUNDAY_PKG)
val INSTANT_SCHEMA = TypeName.namedImport("InstantSchema", SUNDAY_PKG)
val ZONED_DATETIME_SCHEMA = TypeName.namedImport("ZonedDateTimeSchema", SUNDAY_PKG)
val OFFSET_DATETIME_SCHEMA = TypeName.namedImport("OffsetDateTimeSchema", SUNDAY_PKG)
val OFFSET_TIME_SCHEMA = TypeName.namedImport("OffsetTimeSchema", SUNDAY_PKG)
val LOCAL_DATETIME_SCHEMA = TypeName.namedImport("LocalDateTimeSchema", SUNDAY_PKG)
val LOCAL_DATE_SCHEMA = TypeName.namedImport("LocalDateSchema", SUNDAY_PKG)
val LOCAL_TIME_SCHEMA = TypeName.namedImport("LocalTimeSchema", SUNDAY_PKG)
val DURATION_SCHEMA = TypeName.namedImport("DurationSchema", SUNDAY_PKG)

val INSTANT = TypeName.namedImport("Instant", SUNDAY_PKG)
val ZONED_DATETIME = TypeName.namedImport("ZonedDateTime", SUNDAY_PKG)
val OFFSET_DATETIME = TypeName.namedImport("OffsetDateTime", SUNDAY_PKG)
val OFFSET_TIME = TypeName.namedImport("OffsetTime", SUNDAY_PKG)
val LOCAL_DATETIME = TypeName.namedImport("LocalDateTime", SUNDAY_PKG)
val LOCAL_DATE = TypeName.namedImport("LocalDate", SUNDAY_PKG)
val LOCAL_TIME = TypeName.namedImport("LocalTime", SUNDAY_PKG)
val DATE = TypeName.standard("Date")
val DURATION = TypeName.namedImport("Duration", SUNDAY_PKG)
val PROBLEM = TypeName.namedImport("Problem", SUNDAY_PKG)
val MEDIA_TYPE = TypeName.namedImport("MediaType", SUNDAY_PKG)
val URL_TEMPLATE = TypeName.namedImport("URLTemplate", SUNDAY_PKG)
val RESULT_RESPONSE = TypeName.namedImport("ResultResponse", SUNDAY_PKG)

val NULLIFY_PROBLEM = SymbolSpec.importsName("nullifyProblem", SUNDAY_PKG)
