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

package io.outfoxx.sunday.generator.swift.utils

import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.qualifiedTypeName
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.parameterizedBy

const val SUNDAY_MODULE = "Sunday"
val TRANSPORT = typeName("$SUNDAY_MODULE.Transport")
val TRANSPORT_REQUEST = typeName(".TransportType.Request")
val TRANSPORT_RESPONSE = typeName(".TransportType.Response")
val EVENT_SOURCE = typeName("$SUNDAY_MODULE.EventSource")
val MEDIA_TYPE = typeName("$SUNDAY_MODULE.MediaType")
val MEDIA_TYPE_ARRAY = ARRAY.parameterizedBy(MEDIA_TYPE)
val URI_TEMPLATE = typeName("$SUNDAY_MODULE.URI.Template")
val PROBLEM = typeName("$SUNDAY_MODULE.Problem")
val QUALIFIED_PROBLEM = qualifiedTypeName("$SUNDAY_MODULE.Problem")
val GENERIC_PROBLEM = typeName("$SUNDAY_MODULE.GenericProblem")
val PROBLEM_REGISTRATION = typeName("$SUNDAY_MODULE.ProblemRegistration")
val DESCRIPTION_BUILDER = typeName("$SUNDAY_MODULE.DescriptionBuilder")
val HTTP_METHOD = typeName("$SUNDAY_MODULE.HTTP.Method")
val EMPTY = typeName("$SUNDAY_MODULE.Empty")
val OPERATION = qualifiedTypeName("$SUNDAY_MODULE.Operation")
val OPERATION_SPEC = qualifiedTypeName("$SUNDAY_MODULE.OperationSpec")
val NILABLE_OPERATION = qualifiedTypeName("$SUNDAY_MODULE.NilableOperation")
val NILIFY_SPEC = qualifiedTypeName("$SUNDAY_MODULE.NilifySpec")
val STREAMING_BODY = typeName("$SUNDAY_MODULE.StreamingBody")
val STREAMING_OPERATION = qualifiedTypeName("$SUNDAY_MODULE.StreamingOperation")
val PARAMETER_VALUES = typeName("$SUNDAY_MODULE.ParameterValues")
