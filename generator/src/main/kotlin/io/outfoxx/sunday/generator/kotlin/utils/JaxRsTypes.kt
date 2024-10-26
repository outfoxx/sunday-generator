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

interface JaxRsTypes {

  companion object {
    val JAVAX = StdJaxRsTypes("javax.ws.rs")
    val JAKARTA = StdJaxRsTypes("jakarta.ws.rs")
    val QUARKUS = Quarkus()
  }

  enum class Method {
    HEAD,
    OPTIONS,
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
  }

  enum class ParamType {
    PATH,
    QUERY,
    HEADER,
    COOKIE,
    FORM,
    MATRIX,
  }

  // Path Annotations
  val path: ClassName

  // HTTP Method Annotations
  val head: ClassName
  val options: ClassName
  val get: ClassName
  val post: ClassName
  val put: ClassName
  val patch: ClassName
  val delete: ClassName
  val defaultValue: ClassName

  // ContentType Annotations
  val consumes: ClassName
  val produces: ClassName

  // Parameter Annotations
  val pathParam: ClassName
  val queryParam: ClassName
  val headerParam: ClassName
  val cookieParam: ClassName
  val formParam: ClassName
  val matrixParam: ClassName

  val suspended: ClassName

  // Parameter Types (Transparently Injected)
  val asyncResponse: ClassName

  // Response Types
  val response: ClassName
  val sseEventSource: ClassName

  // Injection Annotations
  val context: ClassName

  // Injectable Types
  val uriInfo: ClassName
  val sse: ClassName
  val sseEventSink: ClassName

  // Related Types (SSE)

  // Related Types (SSE)
  val sseInboundEvent: ClassName
  val sseOutboundEvent: ClassName

  val separator: ClassName? get() = null
  val sseElementType: ClassName? get() = null
  val responseStatus: ClassName? get() = null
  val responseHeader: ClassName? get() = null
  val cache: ClassName? get() = null
  val dateFormat: ClassName? get() = null

  fun httpMethod(method: String): ClassName? =
    try {
      httpMethod(Method.valueOf(method))
    } catch (e: IllegalArgumentException) {
      null
    }

  fun httpMethod(method: Method): ClassName =
    when (method) {
      Method.HEAD -> head
      Method.OPTIONS -> options
      Method.GET -> get
      Method.POST -> post
      Method.PUT -> put
      Method.PATCH -> patch
      Method.DELETE -> delete
    }

  fun paramAnnotation(type: ParamType): ClassName =
    when (type) {
      ParamType.PATH -> pathParam
      ParamType.QUERY -> queryParam
      ParamType.HEADER -> headerParam
      ParamType.COOKIE -> cookieParam
      ParamType.FORM -> formParam
      ParamType.MATRIX -> matrixParam
    }

  val isNameRequiredForParameters: Boolean

  class StdJaxRsTypes(basePkg: String) : JaxRsTypes {

    override val isNameRequiredForParameters: Boolean = true

    override val path = ClassName(basePkg, "Path")

    override val head = ClassName(basePkg, "HEAD")
    override val options = ClassName(basePkg, "OPTIONS")
    override val get = ClassName(basePkg, "GET")
    override val post = ClassName(basePkg, "POST")
    override val put = ClassName(basePkg, "PUT")
    override val patch = ClassName(basePkg, "PATCH")
    override val delete = ClassName(basePkg, "DELETE")

    override val consumes = ClassName(basePkg, "Consumes")
    override val produces = ClassName(basePkg, "Produces")

    override val pathParam = ClassName(basePkg, "PathParam")
    override val queryParam = ClassName(basePkg, "QueryParam")
    override val headerParam = ClassName(basePkg, "HeaderParam")
    override val cookieParam = ClassName(basePkg, "CookieParam")
    override val formParam = ClassName(basePkg, "FormParam")
    override val matrixParam = ClassName(basePkg, "MatrixParam")
    override val defaultValue = ClassName(basePkg, "DefaultValue")

    override val suspended = ClassName("$basePkg.container", "Suspended")

    override val asyncResponse = ClassName("$basePkg.container", "AsyncResponse")

    override val response = ClassName.bestGuess("$basePkg.Response")
    override val sseEventSource = ClassName("$basePkg.sse", "SseEventSource")

    override val context = ClassName("$basePkg.core", "Context")

    override val uriInfo = ClassName("$basePkg.core", "UriInfo")
    override val sse = ClassName("$basePkg.sse", "Sse")
    override val sseEventSink = ClassName("$basePkg.sse", "SseEventSink")

    override val sseInboundEvent = ClassName("$basePkg.sse", "InboundSseEvent")
    override val sseOutboundEvent = ClassName("$basePkg.sse", "OutboundSseEvent")
  }

  class Quarkus(basePkg: String = RESTEASY) : JaxRsTypes {

    companion object {
      const val RESTEASY = "org.jboss.resteasy.reactive"
      const val REST = "io.quarkus.rest"
      const val MUTINY = "io.smallrye.mutiny"
    }

    override val isNameRequiredForParameters: Boolean = false

    override val path = JAKARTA.path

    override val head = JAKARTA.head
    override val get = JAKARTA.get
    override val post = JAKARTA.post
    override val put = JAKARTA.put
    override val patch = JAKARTA.patch
    override val delete = JAKARTA.delete
    override val options = JAKARTA.options

    override val consumes = JAKARTA.consumes
    override val produces = JAKARTA.produces

    override val pathParam = ClassName(basePkg, "RestPath")
    override val queryParam = ClassName(basePkg, "RestQuery")
    override val headerParam = ClassName(basePkg, "RestHeader")
    override val cookieParam = ClassName(basePkg, "RestCookie")
    override val formParam = ClassName(basePkg, "RestForm")
    override val matrixParam = ClassName(basePkg, "RestMatrix")
    override val defaultValue = JAKARTA.defaultValue

    override val suspended = JAKARTA.suspended

    override val asyncResponse = JAKARTA.asyncResponse

    override val response = ClassName(basePkg, "RestResponse")
    override val sseEventSource = JAKARTA.sseEventSource

    override val context = JAKARTA.context

    override val uriInfo = JAKARTA.uriInfo
    override val sse = JAKARTA.sse
    override val sseEventSink = JAKARTA.sseEventSink

    override val sseInboundEvent = JAKARTA.sseInboundEvent
    override val sseOutboundEvent = JAKARTA.sseOutboundEvent

    override val separator = ClassName(basePkg, "Separator")
    override val sseElementType = ClassName(basePkg, "RestStreamElementType")
    override val responseStatus = ClassName(basePkg, "ResponseStatus")
    override val responseHeader = ClassName(basePkg, "ResponseHeader")
    override val cache = ClassName(basePkg, "Cache")
    override val dateFormat = ClassName(basePkg, "DateFormat")
  }
}
