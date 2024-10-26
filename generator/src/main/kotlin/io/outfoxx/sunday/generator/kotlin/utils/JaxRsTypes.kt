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

class JaxRsTypes(basePackage: String, name: String) {

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

  // Quarkus-specific annotations
  val restPath = ClassName.bestGuess("org.jboss.resteasy.reactive.RestPath")
  val restQuery = ClassName.bestGuess("org.jboss.resteasy.reactive.RestQuery")
  val restForm = ClassName.bestGuess("org.jboss.resteasy.reactive.RestForm")
  val restHeader = ClassName.bestGuess("org.jboss.resteasy.reactive.RestHeader")
  val restMatrix = ClassName.bestGuess("org.jboss.resteasy.reactive.RestMatrix")
  val restCookie = ClassName.bestGuess("org.jboss.resteasy.reactive.RestCookie")
  val separator = ClassName.bestGuess("org.jboss.resteasy.reactive.RestQuery.Separator")
  val restStreamElementType = ClassName.bestGuess("org.jboss.resteasy.reactive.RestStreamElementType")
  val responseStatus = ClassName.bestGuess("org.jboss.resteasy.reactive.ResponseStatus")
  val responseHeader = ClassName.bestGuess("org.jboss.resteasy.reactive.ResponseHeader")
  val cache = ClassName.bestGuess("org.jboss.resteasy.reactive.Cache")
  val dateFormat = ClassName.bestGuess("org.jboss.resteasy.reactive.DateFormat")

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

    private val JAVAX_NAMES = mapOf(
      "consumes" to "javax.ws.rs.Consumes",
      "delete" to "javax.ws.rs.DELETE",
      "defaultvalue" to "javax.ws.rs.DefaultValue",
      "get" to "javax.ws.rs.GET",
      "head" to "javax.ws.rs.HEAD",
      "headerParam" to "javax.ws.rs.HeaderParam",
      "options" to "javax.ws.rs.OPTIONS",
      "patch" to "javax.ws.rs.PATCH",
      "post" to "javax.ws.rs.POST",
      "put" to "javax.ws.rs.PUT",
      "path" to "javax.ws.rs.Path",
      "pathParam" to "javax.ws.rs.PathParam",
      "produces" to "javax.ws.rs.Produces",
      "queryParam" to "javax.ws.rs.QueryParam",
      "response" to "javax.ws.rs.core.Response",
      "asyncResponse" to "javax.ws.rs.container.AsyncResponse",
      "suspended" to "javax.ws.rs.container.Suspended",
      "context" to "javax.ws.rs.core.Context",
      "uriInfo" to "javax.ws.rs.core.UriInfo",
      "sse" to "javax.ws.rs.sse.Sse",
      "sseEventSink" to "javax.ws.rs.sse.SseEventSink",
      "sseEventSource" to "javax.ws.rs.sse.SseEventSource",
      "sseInboundEvent" to "javax.ws.rs.sse.InboundSseEvent",
      "sseOutboundEvent" to "javax.ws.rs.sse.OutboundSseEvent"
    )

    private val JAKARTA_NAMES = mapOf(
      "consumes" to "jakarta.ws.rs.Consumes",
      "delete" to "jakarta.ws.rs.DELETE",
      "defaultvalue" to "jakarta.ws.rs.DefaultValue",
      "get" to "jakarta.ws.rs.GET",
      "head" to "jakarta.ws.rs.HEAD",
      "headerParam" to "jakarta.ws.rs.HeaderParam",
      "options" to "jakarta.ws.rs.OPTIONS",
      "patch" to "jakarta.ws.rs.PATCH",
      "post" to "jakarta.ws.rs.POST",
      "put" to "jakarta.ws.rs.PUT",
      "path" to "jakarta.ws.rs.Path",
      "pathParam" to "jakarta.ws.rs.PathParam",
      "produces" to "jakarta.ws.rs.Produces",
      "queryParam" to "jakarta.ws.rs.QueryParam",
      "response" to "jakarta.ws.rs.core.Response",
      "asyncResponse" to "jakarta.ws.rs.container.AsyncResponse",
      "suspended" to "jakarta.ws.rs.container.Suspended",
      "context" to "jakarta.ws.rs.core.Context",
      "uriInfo" to "jakarta.ws.rs.core.UriInfo",
      "sse" to "jakarta.ws.rs.sse.Sse",
      "sseEventSink" to "jakarta.ws.rs.sse.SseEventSink",
      "sseEventSource" to "jakarta.ws.rs.sse.SseEventSource",
      "sseInboundEvent" to "jakarta.ws.rs.sse.InboundSseEvent",
      "sseOutboundEvent" to "jakarta.ws.rs.sse.OutboundSseEvent"
    )

    private val QUARKUS_NAMES = mapOf(
      "restPath" to "org.jboss.resteasy.reactive.RestPath",
      "restQuery" to "org.jboss.resteasy.reactive.RestQuery",
      "restForm" to "org.jboss.resteasy.reactive.RestForm",
      "restHeader" to "org.jboss.resteasy.reactive.RestHeader",
      "restMatrix" to "org.jboss.resteasy.reactive.RestMatrix",
      "restCookie" to "org.jboss.resteasy.reactive.RestCookie",
      "separator" to "org.jboss.resteasy.reactive.RestQuery.Separator",
      "restStreamElementType" to "org.jboss.resteasy.reactive.RestStreamElementType",
      "responseStatus" to "org.jboss.resteasy.reactive.ResponseStatus",
      "responseHeader" to "org.jboss.resteasy.reactive.ResponseHeader",
      "cache" to "org.jboss.resteasy.reactive.Cache",
      "dateFormat" to "org.jboss.resteasy.reactive.DateFormat"
    )

    val JAVAX = JaxRsTypes("javax", "JAVAX")
    val JAKARTA = JaxRsTypes("jakarta", "JAKARTA")
    val QUARKUS = JaxRsTypes("org.jboss.resteasy.reactive", "QUARKUS")

    fun select(quarkus: Boolean, useJakarta: Boolean): JaxRsTypes {
      return when {
        quarkus -> QUARKUS
        useJakarta -> JAKARTA
        else -> JAVAX
      }
    }
  }
}
