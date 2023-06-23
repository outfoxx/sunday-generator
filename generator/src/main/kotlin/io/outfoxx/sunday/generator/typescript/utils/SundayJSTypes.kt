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
val REQUEST_FACTORY = TypeName.namedImport("RequestFactory", SUNDAY_PKG)
val ANY_TYPE = TypeName.namedImport("AnyType", SUNDAY_PKG)
val OFFSET_DATETIME = TypeName.namedImport("OffsetDateTime", SUNDAY_PKG)
val LOCAL_DATETIME = TypeName.namedImport("LocalDateTime", SUNDAY_PKG)
val LOCAL_DATE = TypeName.namedImport("LocalDate", SUNDAY_PKG)
val LOCAL_TIME = TypeName.namedImport("LocalTime", SUNDAY_PKG)
val DURATION = TypeName.namedImport("Duration", SUNDAY_PKG)
val PROBLEM = TypeName.namedImport("Problem", SUNDAY_PKG)
val MEDIA_TYPE = TypeName.namedImport("MediaType", SUNDAY_PKG)
val URL_TEMPLATE = TypeName.namedImport("URLTemplate", SUNDAY_PKG)
val RESULT_RESPONSE = TypeName.namedImport("ResultResponse", SUNDAY_PKG)

val NULLIFY_RESPONSE = SymbolSpec.importsName("nullifyResponse", SUNDAY_PKG)
val NULLIFY_PROMISE_RESPONSE = SymbolSpec.importsName("nullifyPromiseResponse", SUNDAY_PKG)
val PROMISE_FROM = SymbolSpec.importsName("promiseFrom", SUNDAY_PKG)
