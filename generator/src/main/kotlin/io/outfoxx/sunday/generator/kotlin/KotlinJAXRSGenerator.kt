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

package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.Document
import amf.client.model.domain.EndPoint
import amf.client.model.domain.NodeShape
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.SecurityRequirement
import amf.client.model.domain.SecurityScheme
import amf.client.model.domain.Shape
import amf.client.model.domain.UnionShape
import com.fasterxml.jackson.databind.ObjectMapper
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.APIAnnotationName.Asynchronous
import io.outfoxx.sunday.generator.APIAnnotationName.EventStream
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.Reactive
import io.outfoxx.sunday.generator.APIAnnotationName.SSE
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.utils.FLOW
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.defaultValueStr
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.flattened
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.utils.mediaType
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.parameterName
import io.outfoxx.sunday.generator.utils.parent
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.queryParameters
import io.outfoxx.sunday.generator.utils.request
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.required
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.sunday.generator.utils.scheme
import io.outfoxx.sunday.generator.utils.schemes
import io.outfoxx.sunday.generator.utils.security
import io.outfoxx.sunday.generator.utils.statusCode
import io.outfoxx.sunday.generator.utils.successes
import java.net.URI
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.DefaultValue
import javax.ws.rs.GET
import javax.ws.rs.HEAD
import javax.ws.rs.HeaderParam
import javax.ws.rs.OPTIONS
import javax.ws.rs.PATCH
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.UriInfo
import javax.ws.rs.sse.SseEventSource

/**
 * Generator for Kotlin/'JAX-RS 2' interfaces
 */
class KotlinJAXRSGenerator(
  document: Document,
  typeRegistry: KotlinTypeRegistry,
  override val options: Options
) : KotlinGenerator(
  document,
  typeRegistry,
  options
) {

  class Options(
    val coroutineServiceMethods: Boolean,
    val reactiveResponseType: String?,
    val explicitSecurityParameters: Boolean,
    defaultServicePackageName: String,
    defaultProblemBaseUri: String,
    defaultMediaTypes: List<String>,
    serviceSuffix: String,
  ) : KotlinGenerator.Options(
    defaultServicePackageName,
    defaultProblemBaseUri,
    defaultMediaTypes,
    serviceSuffix,
  )

  private val referencedProblemTypes = mutableMapOf<URI, TypeName>()
  private val reactiveDefault = options.reactiveResponseType != null && !options.coroutineServiceMethods
  private val reactiveResponseType = options.reactiveResponseType?.let { ClassName.bestGuess(it) }

  override fun processServiceBegin(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val typeBuilder = TypeSpec.interfaceBuilder(serviceTypeName)
    typeBuilder.tag(TypeSpec.Builder::class, TypeSpec.companionObjectBuilder())

    if (options.defaultMediaTypes.isNotEmpty()) {

      val prodAnn = AnnotationSpec.builder(Produces::class)
        .addMember("value = [%L]", defaultMediaTypes.joinToString(",") { "\"$it\"" })
        .build()
      typeBuilder.addAnnotation(prodAnn)

      val consAnn = AnnotationSpec.builder(Consumes::class)
        .addMember("value = [%L]", defaultMediaTypes.joinToString(",") { "\"$it\"" })
        .build()
      typeBuilder.addAnnotation(consAnn)
    }

    return typeBuilder
  }

  override fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {

    if (typeRegistry.options.contains(JacksonAnnotations) && referencedProblemTypes.isNotEmpty()) {
      typeBuilder.addType(
        TypeSpec.companionObjectBuilder()
          .addFunction(
            FunSpec.builder("registerProblems")
              .addParameter("mapper", ObjectMapper::class)
              .addCode(
                "mapper.registerSubtypes(⇥\n${referencedProblemTypes.values.joinToString(",\n") { "%T::class.java" }}⇤\n)",
                *referencedProblemTypes.values.toTypedArray()
              )
              .build()
          )
          .build()
      )
    }

    return super.processServiceEnd(typeBuilder)
  }

  override fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    problemTypes: Map<String, ProblemTypeDefinition>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    returnTypeName: TypeName
  ): TypeName {

    val mediaTypesForPayloads = response.payloads.mapNotNull { it.mediaType }

    if (mediaTypesForPayloads.isNotEmpty() && mediaTypesForPayloads != defaultMediaTypes) {
      val prodAnn = AnnotationSpec.builder(Produces::class)
        .addMember("value = [%L]", mediaTypesForPayloads.joinToString(",") { "\"$it\"" })
        .build()
      functionBuilder.addAnnotation(prodAnn)
    }

    if (options.coroutineServiceMethods) {
      functionBuilder.addModifiers(KModifier.SUSPEND)
    }

    val isSSE = operation.findBoolAnnotation(SSE, generationMode) == true && !options.coroutineServiceMethods
    val isFlow = operation.hasAnnotation(EventStream, generationMode) && options.coroutineServiceMethods

    val reactive = operation.findBoolAnnotation(Reactive, generationMode) ?: reactiveDefault
    if (reactive && reactiveResponseType != null && !isSSE && !isFlow) {

      return if (generationMode == Client) {
        reactiveResponseType.parameterizedBy(returnTypeName)
      } else {
        reactiveResponseType.parameterizedBy(javax.ws.rs.core.Response::class.asTypeName())
      }
    }

    if (operation.findBoolAnnotation(Asynchronous, generationMode) == true) {
      return UNIT
    }

    if (isSSE) {

      // Ensure SSE "messages" are resolved and therefore defined
      if (body is UnionShape) {
        val types = body.flattened.filterIsInstance<NodeShape>()
        types.forEach { resolveTypeName(it, null) }
      }

      return if (generationMode == Client) {
        return SseEventSource::class.asTypeName()
      } else {
        UNIT
      }
    }

    if (operation.findStringAnnotation(EventStream, null) == "discriminated") {
      if (body !is UnionShape) {
        genError("Discriminated ($EventStream) requires a union of event types", operation)
      }
      return FLOW.parameterizedBy(returnTypeName)
    }

    return if (generationMode == Client) {
      returnTypeName
    } else {
      javax.ws.rs.core.Response::class.asTypeName()
    }
  }

  private fun resolveSecuritySchemeParameterTypeName(
    scheme: SecurityScheme,
    parameter: Parameter,
    type: String
  ): TypeName {

    val schemeTypeName = ClassName.bestGuess("${scheme.name.kotlinTypeName}SecurityScheme")

    val parameterTypeNameContext =
      KotlinResolutionContext(
        document,
        schemeTypeName.nestedClass("${parameter.kotlinTypeName}${type.capitalize()}Param")
      )

    return typeRegistry.resolveTypeName(parameter.schema!!, parameterTypeNameContext)
      .run {
        if (parameter.required == false) {
          copy(nullable = true)
        } else {
          this
        }
      }
  }

  private fun resolveSecurityRequirements(endPoint: EndPoint, operation: Operation): List<SecurityRequirement> {

    val reqs = mutableListOf<SecurityRequirement>()

    reqs.addAll(document.api.security)

    fun addEndpoint(endPoint: EndPoint) {
      endPoint.parent?.let(::addEndpoint)
      reqs.addAll(endPoint.security)
    }

    addEndpoint(endPoint)

    reqs += operation.security

    return reqs
  }

  private fun resolveSecuritySchemes(endPoint: EndPoint, operation: Operation): List<SecurityScheme> {
    val schemes = mutableMapOf<String, SecurityScheme>()
    resolveSecurityRequirements(endPoint, operation).forEach { securityRequirement ->
      securityRequirement.schemes.forEach { parametrizedSecurityScheme ->
        val scheme = parametrizedSecurityScheme.scheme
        schemes[scheme.name] = scheme
      }
    }
    return schemes.values.toList()
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec.Builder {

    if (options.explicitSecurityParameters) {
      resolveSecuritySchemes(endPoint, operation).forEach { scheme ->

        scheme.headers?.forEach { header ->
          val headerName = (header.parameterName ?: header.name)!!
          val headerTypeName = resolveSecuritySchemeParameterTypeName(scheme, header, "header")
          val headerParamName = "${scheme.name.kotlinIdentifierName}${headerName.kotlinIdentifierName.capitalize()}"
          val parameterBuilder = methodParameter(header, ParameterSpec.builder(headerParamName, headerTypeName))
          parameterBuilder.addAnnotation(
            AnnotationSpec.builder(HeaderParam::class)
              .addMember("value = %S", headerName)
              .build()
          )
          functionBuilder.addParameter(parameterBuilder.build())
        }

        scheme.queryParameters?.forEach { queryParameter ->
          val queryParameterName = (queryParameter.parameterName ?: queryParameter.name)!!
          val queryParameterTypeName =
            resolveSecuritySchemeParameterTypeName(scheme, queryParameter, "queryParameter")
          val queryParameterParamName =
            "${scheme.name.kotlinIdentifierName}${queryParameterName.kotlinIdentifierName.capitalize()}"
          val parameterBuilder =
            methodParameter(queryParameter, ParameterSpec.builder(queryParameterParamName, queryParameterTypeName))
          functionBuilder.addParameter(parameterBuilder.build())
        }
      }
    }

    functionBuilder.addModifiers(KModifier.ABSTRACT)

    // Add @GET, @POST, @PUT, @DELETE to resource method
    val httpMethodAnnClass = httpMethod(operation.method)
    functionBuilder.addAnnotation(httpMethodAnnClass)

    // Add @Path
    val pathAnn = AnnotationSpec.builder(javax.ws.rs.Path::class)
      .addMember("value = %S", endPoint.path)
      .build()
    functionBuilder.addAnnotation(pathAnn)

    return functionBuilder
  }

  private fun methodParameter(
    parameterShape: Parameter,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec.Builder {

    val builtParameter = parameterBuilder.build()

    typeRegistry.applyUseSiteAnnotations(parameterShape.schema!!, builtParameter.type) {
      parameterBuilder.addAnnotation(it)
    }

    // Add @DefaultValue (if provided)
    val defaultValue = parameterShape.schema?.defaultValueStr
    if (defaultValue != null) {
      val newParameter =
        ParameterSpec.builder(builtParameter.name, builtParameter.type.copy(nullable = false))
          .addKdoc(builtParameter.kdoc)
          .addAnnotations(builtParameter.annotations)
          .addModifiers(builtParameter.modifiers)
      newParameter.addAnnotation(
        AnnotationSpec.builder(DefaultValue::class)
          .addMember("value = %S", defaultValue)
          .build()
      )
      return newParameter
    }

    return parameterBuilder
  }

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    // Add @PathParam to URI parameters
    parameterBuilder.addAnnotation(
      AnnotationSpec.builder(PathParam::class)
        .addMember("value = %S", parameter.name())
        .build()
    )

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    // Add @QueryParam to URI parameters
    parameterBuilder.addAnnotation(
      AnnotationSpec.builder(QueryParam::class)
        .addMember("value = %S", parameter.name())
        .build()
    )

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    // Add @HeaderParam to URI parameters
    parameterBuilder.addAnnotation(
      AnnotationSpec.builder(HeaderParam::class)
        .addMember("value = %S", parameter.name())
        .build()
    )

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    if (
      payloadSchema.resolve.findBoolAnnotation(Patchable, generationMode) == true &&
      operation.method.equals("patch", ignoreCase = true)
    ) {
      val orig = parameterBuilder.build()
      val origTypeName = orig.type as ClassName
      return ParameterSpec.builder(orig.name, origTypeName.nestedClass("Patch")).build()
    }

    typeRegistry.applyUseSiteAnnotations(payloadSchema, parameterBuilder.build().type) {
      parameterBuilder.addAnnotation(it)
    }

    val request = operation.request ?: operation.requests.first()

    val mediaTypesForPayloads = request.payloads.mapNotNull { it.mediaType }

    if (mediaTypesForPayloads.isNotEmpty() && mediaTypesForPayloads != defaultMediaTypes) {
      val prodAnn = AnnotationSpec.builder(Consumes::class)
        .addMember("value = [%L]", mediaTypesForPayloads.joinToString(",") { "\"$it\"" })
        .build()
      functionBuilder.addAnnotation(prodAnn)
    }

    // Finalize
    return parameterBuilder.build()
  }

  override fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec {

    referencedProblemTypes.putAll(problemTypes)

    if (generationMode == Server) {
      // Add async response parameter to asynchronous methods
      if (operation.findBoolAnnotation(Asynchronous, generationMode) == true) {
        functionBuilder.addParameter(
          ParameterSpec.builder("asyncResponse", AsyncResponse::class)
            .addAnnotation(Suspended::class)
            .build()
        )
      }

      // Add Sse & SseEventSink parameter to sse methods
      if (operation.findBoolAnnotation(SSE, generationMode) == true && !options.coroutineServiceMethods) {

        functionBuilder.addParameter(
          ParameterSpec.builder("sse", javax.ws.rs.sse.Sse::class)
            .addAnnotation(Context::class)
            .build()
        )

        functionBuilder.addParameter(
          ParameterSpec.builder("sseEvents", javax.ws.rs.sse.SseEventSink::class)
            .addAnnotation(Context::class)
            .build()
        )
      }

      // Add UriInfo parameter to CREATED methods
      if (operation.successes.firstOrNull()?.statusCode == CREATED.statusCode.toString()) {
        functionBuilder.addParameter(
          ParameterSpec.builder("uriInfo", UriInfo::class)
            .addAnnotation(Context::class)
            .build()
        )
      }
    } else {
      addNullifyMethod(operation, functionBuilder.build(), problemTypes, typeBuilder)
    }

    // Finalize
    return functionBuilder.build()
  }

  private fun httpMethod(methodName: String) =
    when (methodName.toUpperCase()) {
      "DELETE" -> DELETE::class
      "GET" -> GET::class
      "HEAD" -> HEAD::class
      "OPTIONS" -> OPTIONS::class
      "POST" -> POST::class
      "PUT" -> PUT::class
      "PATCH" -> PATCH::class
      else -> genError("Invalid HTTP method: $methodName")
    }
}
