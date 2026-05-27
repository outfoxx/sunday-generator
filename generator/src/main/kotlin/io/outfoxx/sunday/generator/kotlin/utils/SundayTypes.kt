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

val OPERATION_RESPONSE = ClassName.bestGuess("io.outfoxx.sunday.http.OperationResponse")
val SUNDAY_OPERATION = ClassName.bestGuess("io.outfoxx.sunday.Operation")
val SUNDAY_OPERATION_SPEC = ClassName.bestGuess("io.outfoxx.sunday.OperationSpec")
val SUNDAY_NULLABLE_OPERATION = ClassName.bestGuess("io.outfoxx.sunday.NullableOperation")
val SUNDAY_NULLIFY_SPEC = ClassName.bestGuess("io.outfoxx.sunday.NullifySpec")
val SUNDAY_EVENT_SOURCE = ClassName.bestGuess("io.outfoxx.sunday.EventSource")
val SUNDAY_METHOD = ClassName.bestGuess("io.outfoxx.sunday.http.Method")
val SUNDAY_REQUEST = ClassName.bestGuess("io.outfoxx.sunday.http.Request")
val SUNDAY_RESPONSE = ClassName.bestGuess("io.outfoxx.sunday.http.Response")

val MEDIA_TYPE = ClassName.bestGuess("io.outfoxx.sunday.MediaType")
val TRANSPORT = ClassName.bestGuess("io.outfoxx.sunday.Transport")
val URI_TEMPLATE = ClassName.bestGuess("io.outfoxx.sunday.URITemplate")

val SUNDAY_HTTP_PROBLEM = ClassName.bestGuess("io.outfoxx.sunday.problems.SundayHttpProblem")

val PATCH_OP = ClassName("io.outfoxx.sunday.json.patch", "PatchOp")
val PATCH_SET_OP = PATCH_OP.nestedClass("Set")
val UPDATE_OP = ClassName("io.outfoxx.sunday.json.patch", "UpdateOp")

val PATCH = ClassName("io.outfoxx.sunday.json.patch", "Patch")
