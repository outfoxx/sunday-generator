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
import io.outfoxx.sunday.generator.genError

class JaxRsTypes(basePackage: String) {

  val consumes = ClassName.bestGuess("$basePackage.ws.rs.Consumes")
  val delete = ClassName.bestGuess("$basePackage.ws.rs.DELETE")
  val defaultvalue = ClassName.bestGuess("$basePackage.ws.rs.DefaultValue")
  val get = ClassName.bestGuess("$basePackage.ws.rs.GET")
  val head = ClassName.bestGuess("$basePackage.ws.rs.HEAD")
  val headerParam = ClassName.bestGuess("$basePackage.ws.rs.HeaderParam")
  val options = ClassName.bestGuess("$basePackage.ws.rs.OPTIONS")
  val patch = ClassName.bestGuess("$basePackage.ws.rs.PATCH")
  val post = ClassName.bestGuess("$basePackage.ws.rs.POST")
  val put = ClassName.bestGuess("$basePackage.ws.rs.PUT")
  val path = ClassName.bestGuess("$basePackage.ws.rs.Path")
  val pathParam = ClassName.bestGuess("$basePackage.ws.rs.PathParam")
  val produces = ClassName.bestGuess("$basePackage.ws.rs.Produces")
  val queryParam = ClassName.bestGuess("$basePackage.ws.rs.QueryParam")
  val response = ClassName.bestGuess("$basePackage.ws.rs.core.Response")
  val asyncResponse = ClassName.bestGuess("$basePackage.ws.rs.container.AsyncResponse")
  val suspended = ClassName.bestGuess("$basePackage.ws.rs.container.Suspended")
  val context = ClassName.bestGuess("$basePackage.ws.rs.core.Context")
  val uriInfo = ClassName.bestGuess("$basePackage.ws.rs.core.UriInfo")
  val sse = ClassName.bestGuess("$basePackage.ws.rs.sse.Sse")
  val sseEventSink = ClassName.bestGuess("$basePackage.ws.rs.sse.SseEventSink")
  val sseEventSource = ClassName.bestGuess("$basePackage.ws.rs.sse.SseEventSource")
  val sseInboundEvent = ClassName.bestGuess("$basePackage.ws.rs.sse.InboundSseEvent")
  val sseOutboundEvent = ClassName.bestGuess("$basePackage.ws.rs.sse.OutboundSseEvent")

  fun httpMethod(methodName: String) =
    when (methodName.uppercase()) {
      "DELETE" -> delete
      "GET" -> get
      "HEAD" -> head
      "OPTIONS" -> options
      "POST" -> post
      "PUT" -> put
      "PATCH" -> patch
      else -> genError("Unsupported HTTP method: $methodName")
    }

  companion object {

    val JAVAX = JaxRsTypes("javax")
    val JAKARTA = JaxRsTypes("jakarta")
  }
}
