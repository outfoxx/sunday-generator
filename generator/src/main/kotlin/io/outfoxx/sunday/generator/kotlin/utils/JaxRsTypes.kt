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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

interface JaxRsTypes {

  companion object {
    val JAVAX = StdJaxRsTypes("javax.ws.rs")
    val JAKARTA = StdJaxRsTypes("jakarta.ws.rs")
    val QUARKUS = Quarkus(base = JAKARTA)
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

  enum class ContextType(val annotationName: String) {
    URI_INFO("uriInfo"),
    REQUEST("request"),
    HTTP_HEADERS("headers"),
    SECURITY_CONTEXT("securityContext"),
    APPLICATION("application"),
    CONFIGURATION("configuration"),
    RESOURCE_CONTEXT("resourceContext"),
    PROVIDERS("providers"),
    SSE("sse"),
    SSE_EVENT_SINK("sseEventSink"),
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
  val rawResponse: ClassName
  val sseEventSource: ClassName

  // Injection Annotations
  val context: ClassName

  // Injectable Types
  val uriInfo: ClassName
  val request: ClassName
  val httpHeaders: ClassName
  val securityContext: ClassName
  val application: ClassName
  val configuration: ClassName
  val resourceContext: ClassName
  val providers: ClassName
  val sse: ClassName
  val sseEventSink: ClassName

  // Related Types (SSE)
  val sseInboundEvent: ClassName
  val sseOutboundEvent: ClassName

  val separator: ClassName? get() = null
  val sseElementType: ClassName? get() = null
  val responseStatus: ClassName? get() = null
  val responseHeader: ClassName? get() = null
  val cache: ClassName? get() = null
  val dateFormat: ClassName? get() = null

  // Client Only
  val clientHeaderParam: ClassName? get() = null

  fun httpMethod(method: String): ClassName? =
    try {
      httpMethod(Method.valueOf(method.uppercase()))
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

  fun responseType(resultType: TypeName): TypeName =
    rawResponse.parameterizedBy(resultType)

  fun contextType(type: ContextType): ClassName =
    when (type) {
      ContextType.URI_INFO -> uriInfo
      ContextType.REQUEST -> request
      ContextType.HTTP_HEADERS -> httpHeaders
      ContextType.SECURITY_CONTEXT -> securityContext
      ContextType.APPLICATION -> application
      ContextType.CONFIGURATION -> configuration
      ContextType.RESOURCE_CONTEXT -> resourceContext
      ContextType.PROVIDERS -> providers
      ContextType.SSE -> sse
      ContextType.SSE_EVENT_SINK -> sseEventSink
    }

  fun contextType(type: String): ClassName? =
    ContextType.entries
      .firstOrNull { it.annotationName == type }
      ?.let { contextType(it) }

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

    override val rawResponse = ClassName.bestGuess("$basePkg.core.Response")
    override val sseEventSource = ClassName("$basePkg.sse", "SseEventSource")

    override val context = ClassName("$basePkg.core", "Context")

    override val uriInfo = ClassName("$basePkg.core", "UriInfo")
    override val request = ClassName("$basePkg.core", "Request")
    override val httpHeaders = ClassName("$basePkg.core", "HttpHeaders")
    override val securityContext = ClassName("$basePkg.core", "SecurityContext")
    override val application = ClassName("$basePkg.core", "Application")
    override val configuration = ClassName("$basePkg.core", "Configuration")
    override val resourceContext = ClassName("$basePkg.container", "ResourceContext")
    override val providers = ClassName("$basePkg.ext", "Providers")
    override val sse = ClassName("$basePkg.sse", "Sse")
    override val sseEventSink = ClassName("$basePkg.sse", "SseEventSink")

    override val sseInboundEvent = ClassName("$basePkg.sse", "InboundSseEvent")
    override val sseOutboundEvent = ClassName("$basePkg.sse", "OutboundSseEvent")

    override fun responseType(resultType: TypeName) = rawResponse
  }

  class Quarkus(pkg: String = RESTEASY, base: JaxRsTypes) : JaxRsTypes {

    companion object {
      const val RESTEASY = "org.jboss.resteasy.reactive"
      const val REST = "io.quarkus.rest"
      const val MUTINY = "io.smallrye.mutiny"
    }

    override val isNameRequiredForParameters: Boolean = false

    override val path = base.path

    override val head = base.head
    override val get = base.get
    override val post = base.post
    override val put = base.put
    override val patch = base.patch
    override val delete = base.delete
    override val options = base.options

    override val consumes = base.consumes
    override val produces = base.produces

    override val pathParam = ClassName(pkg, "RestPath")
    override val queryParam = ClassName(pkg, "RestQuery")
    override val headerParam = ClassName(pkg, "RestHeader")
    override val cookieParam = ClassName(pkg, "RestCookie")
    override val formParam = ClassName(pkg, "RestForm")
    override val matrixParam = ClassName(pkg, "RestMatrix")
    override val defaultValue = base.defaultValue

    override val suspended = base.suspended

    override val asyncResponse = base.asyncResponse

    override val rawResponse = ClassName(pkg, "RestResponse")
    override val sseEventSource = base.sseEventSource

    override val context = base.context

    override val uriInfo = base.uriInfo
    override val request = base.request
    override val httpHeaders = base.httpHeaders
    override val securityContext = base.securityContext
    override val application = base.application
    override val configuration = base.configuration
    override val resourceContext = base.resourceContext
    override val providers = base.providers
    override val sse = base.sse
    override val sseEventSink = base.sseEventSink

    override val sseInboundEvent = base.sseInboundEvent
    override val sseOutboundEvent = base.sseOutboundEvent

    override val separator = ClassName(pkg, "Separator")
    override val sseElementType = ClassName(pkg, "RestStreamElementType")
    override val responseStatus = ClassName(pkg, "ResponseStatus")
    override val responseHeader = ClassName(pkg, "ResponseHeader")
    override val cache = ClassName(pkg, "Cache")
    override val dateFormat = ClassName(pkg, "DateFormat")

    override val clientHeaderParam = ClassName("org.eclipse.microprofile.rest.client.annotation", "ClientHeaderParam")
  }
}
