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
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.parameterizedBy

const val SUNDAY_MODULE = "Sunday"
val REQUEST_FACTORY = typeName("$SUNDAY_MODULE.RequestFactory")
val EVENT_SOURCE = typeName("$SUNDAY_MODULE.EventSource")
val MEDIA_TYPE = typeName("$SUNDAY_MODULE.MediaType")
val MEDIA_TYPE_ARRAY = ARRAY.parameterizedBy(MEDIA_TYPE)
val URI_TEMPLATE = typeName("$SUNDAY_MODULE.URI.Template")
val PROBLEM = typeName("$SUNDAY_MODULE.Problem")
val DESCRIPTION_BUILDER = typeName("$SUNDAY_MODULE.DescriptionBuilder")
val REQUEST_PUBLISHER = typeName("$SUNDAY_MODULE.RequestPublisher")
val RESPONSE_PUBLISHER = typeName("$SUNDAY_MODULE.RequestResponsePublisher")
val REQUEST_RESULT_PUBLISHER = typeName("$SUNDAY_MODULE.RequestResultPublisher")
val REQUEST_COMPLETE_PUBLISHER = typeName("$SUNDAY_MODULE.RequestCompletePublisher")
val REQUEST_EVENT_PUBLISHER = typeName("$SUNDAY_MODULE.RequestEventPublisher")
val HTTP_METHOD = typeName("$SUNDAY_MODULE.HTTP.Method")
val EMPTY = typeName("$SUNDAY_MODULE.Empty")
