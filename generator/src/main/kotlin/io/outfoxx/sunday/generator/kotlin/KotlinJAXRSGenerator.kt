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

import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Parameter
import amf.apicontract.client.platform.model.domain.Response
import amf.apicontract.client.platform.model.domain.security.SecurityRequirement
import amf.apicontract.client.platform.model.domain.security.SecurityScheme
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.domain.Shape
import amf.shapes.client.platform.model.domain.UnionShape
import com.damnhandy.uri.template.UriTemplate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.outfoxx.sunday.generator.APIAnnotationName.Asynchronous
import io.outfoxx.sunday.generator.APIAnnotationName.EventStream
import io.outfoxx.sunday.generator.APIAnnotationName.JsonBody
import io.outfoxx.sunday.generator.APIAnnotationName.Reactive
import io.outfoxx.sunday.generator.APIAnnotationName.SSE
import io.outfoxx.sunday.generator.APIAnnotationName.JaxrsContext
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.HttpStatus.CREATED
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.utils.*
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.defaultValueStr
import io.outfoxx.sunday.generator.utils.equalsInAnyOrder
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
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
import io.outfoxx.sunday.generator.utils.scalarValue
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.sunday.generator.utils.scheme
import io.outfoxx.sunday.generator.utils.schemes
import io.outfoxx.sunday.generator.utils.security
import io.outfoxx.sunday.generator.utils.statusCode
import io.outfoxx.sunday.generator.utils.successes
import io.outfoxx.sunday.generator.utils.stringValue
import java.net.URI
import kotlin.collections.set

/**
 * Generator for Kotlin/'JAX-RS 2' interfaces
 */
class KotlinJAXRSGenerator(
  document: Document,
  shapeIndex: ShapeIndex,
  typeRegistry: KotlinTypeRegistry,
  override val options: Options,
) : KotlinGenerator(
  document,
  shapeIndex,
  typeRegistry,
  options,
) {

  class Options(
    val coroutineServiceMethods: Boolean,
    val reactiveResponseType: String?,
    val explicitSecurityParameters: Boolean,
    val baseUriMode: BaseUriMode?,
    val alwaysUseResponseReturn: Boolean,
    defaultServicePackageName: String?,
    defaultProblemBaseUri: String,
    defaultMediaTypes: List<String>,
    serviceSuffix: String,
    val quarkus: Boolean,
  ) : KotlinGenerator.Options(
    defaultServicePackageName,
    defaultProblemBaseUri,
    defaultMediaTypes,
    serviceSuffix,
  ) {

    enum class BaseUriMode {
      FULL,
      PATH_ONLY,
    }
  }

  companion object {
    private const val SSE_CONTENT_TYPE = "text/event-stream"
  }

  private val jaxRsTypes: JaxRsTypes =
    if (options.quarkus) {
      JaxRsTypes.QUARKUS
    } else if (typeRegistry.options.contains(KotlinTypeRegistry.Option.UseJakartaPackages)) {
      JaxRsTypes.JAKARTA
    } else {
      JaxRsTypes.JAVAX
    }
  private val referencedProblemTypes = mutableMapOf<URI, TypeName>()
  private val reactiveDefault = options.reactiveResponseType != null && !options.coroutineServiceMethods
  private val reactiveResponseType =
    if (options.quarkus) {
      UNI
    } else {
      options.reactiveResponseType?.let { ClassName.bestGuess(it) }
    }

  private fun isOverridingDefaultMediaTypes(mediaTypes: List<String>) =
    mediaTypes.isNotEmpty() && !mediaTypes.equalsInAnyOrder(defaultMediaTypes)

  override fun processServiceBegin(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val typeBuilder = TypeSpec.interfaceBuilder(serviceTypeName)
    typeBuilder.tag(TypeSpec.Builder::class, TypeSpec.companionObjectBuilder())

    getBaseURIInfo()?.let { (baseURL, baseURLParameters) ->

      val parameterValues = baseURLParameters.associate { param ->
        val defaultValue = param.defaultValue?.scalarValue?.toString() ?: "{${param.name}}"
        param.name to defaultValue
      }

      val expandedBaseURL = UriTemplate.buildFromTemplate(baseURL).build().expand(parameterValues)

      val baseUriMode =
        options.baseUriMode ?: if (generationMode == Client) {
          Options.BaseUriMode.FULL
        } else {
          Options.BaseUriMode.PATH_ONLY
        }

      val finalBaseURL =
        when (baseUriMode) {
          Options.BaseUriMode.FULL -> expandedBaseURL

          Options.BaseUriMode.PATH_ONLY ->
            try {
              URI(expandedBaseURL).path
            } catch (ignored: Throwable) {
              expandedBaseURL.replace("//", "").dropWhile { it != '/' }
            }
        }

      typeBuilder.addAnnotation(jaxRsTypes.path, finalBaseURL)
    }

    if (defaultMediaTypes.isNotEmpty()) {

      typeBuilder.addAnnotation(jaxRsTypes.produces, defaultMediaTypes)

      val consumesMediaTypes =
        if (generationMode == Client) {
          defaultMediaTypes.take(1)
        } else {
          defaultMediaTypes
        }

      typeBuilder.addAnnotation(jaxRsTypes.consumes, consumesMediaTypes)
    }

    return typeBuilder
  }

  override fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {

    if (typeRegistry.options.contains(JacksonAnnotations) && referencedProblemTypes.isNotEmpty()) {
      typeBuilder.addType(
        TypeSpec.companionObjectBuilder()
          .apply { typeRegistry.addGeneratedTo(this, false) }
          .addFunction(
            FunSpec.builder("registerProblems")
              .addParameter("mapper", OBJECT_MAPPER)
              .addCode(
                "mapper.registerSubtypes(⇥\n" +
                  referencedProblemTypes.values.joinToString(",\n") { "%T::class.java" } +
                  "⇤\n)",
                *referencedProblemTypes.values.toTypedArray(),
              )
              .build(),
          )
          .build(),
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
    returnTypeName: TypeName,
  ): TypeName {

    val mediaTypesForPayloads = response.payloads.mapNotNull { it.mediaType }

    if (options.coroutineServiceMethods) {
      functionBuilder.addModifiers(SUSPEND)
    }

    val isSSE = operation.findBoolAnnotation(SSE, generationMode) == true
    val isFlow = operation.hasAnnotation(EventStream, generationMode) && options.coroutineServiceMethods

    val reactive = operation.findBoolAnnotation(Reactive, generationMode) ?: reactiveDefault
    if (reactive && reactiveResponseType != null && !isSSE && !isFlow) {

      return if (generationMode == Server || options.alwaysUseResponseReturn) {
        reactiveResponseType.parameterizedBy(jaxRsTypes.responseType(returnTypeName))
      } else {
        reactiveResponseType.parameterizedBy(returnTypeName)
      }
    }

    if (operation.findBoolAnnotation(Asynchronous, generationMode) == true) {
      return UNIT
    }

    if (isSSE && !isFlow) {
      return functionBuilder.asSSE(operation, body, mediaTypesForPayloads)
    }

    operation.findStringAnnotation(EventStream, generationMode)?.let { type ->
      return functionBuilder.asEventStream(type, operation, body, returnTypeName, isSSE, mediaTypesForPayloads)
    }

    if (isOverridingDefaultMediaTypes(mediaTypesForPayloads)) {
      functionBuilder.addAnnotation(jaxRsTypes.produces, mediaTypesForPayloads)
    }

    return if (generationMode == Server || options.alwaysUseResponseReturn) {
      jaxRsTypes.responseType(returnTypeName)
    } else {
      returnTypeName
    }
  }

  private fun FunSpec.Builder.asSSE(operation: Operation, body: Shape?, mediaTypesForPayloads: List<String>): TypeName {

    addSseElementTypeAnnotation(mediaTypesForPayloads, operation)

    // Ensure SSE "messages" are resolved and therefore defined
    referenceTypeUsage(body)

    return if (generationMode == Client) {
      return jaxRsTypes.sseEventSource
    } else {
      UNIT
    }
  }

  private fun FunSpec.Builder.asEventStream(
    type: String,
    operation: Operation,
    body: Shape?,
    returnTypeName: TypeName,
    isSSE: Boolean,
    mediaTypesForPayloads: List<String>,
  ): TypeName {
    when (type) {
      "simple" -> {
        addSseElementTypeAnnotation(mediaTypesForPayloads, operation)
        return FLOW.parameterizedBy(
          when {
            isSSE && generationMode == Client -> jaxRsTypes.sseInboundEvent
            isSSE && generationMode == Server -> jaxRsTypes.sseOutboundEvent
            else -> returnTypeName
          },
        )
      }

      "discriminated" -> {
        if (body !is UnionShape) {
          genError("Discriminated ($EventStream) requires a union of event types", operation)
        }
        addSseElementTypeAnnotation(mediaTypesForPayloads, operation)
        return FLOW.parameterizedBy(returnTypeName)
      }

      else -> {
        genError("Unsupported event stream type '$type'", operation)
      }
    }
  }

  private fun FunSpec.Builder.addSseElementTypeAnnotation(mediaTypesForPayloads: List<String>, operation: Operation) {
    val elementTypes = mediaTypesForPayloads.ifEmpty { defaultMediaTypes }
    if (elementTypes.size > 1) {
      genError("Multiple media types not supported for Server-Sent Events", operation)
    }
    addAnnotation(jaxRsTypes.produces, listOf(SSE_CONTENT_TYPE))
    jaxRsTypes.sseElementType?.let { ann ->
      elementTypes
        .firstOrNull { it != SSE_CONTENT_TYPE }
        ?.let { elementType ->
          addAnnotation(ann, elementType)
        }
    }
  }

  private fun resolveSecuritySchemeParameterTypeName(
    scheme: SecurityScheme,
    parameter: Parameter,
    type: String,
  ): TypeName {

    val schemeTypeName = ClassName.bestGuess("${scheme.name.kotlinTypeName}SecurityScheme")

    val parameterTypeNameContext =
      KotlinResolutionContext(
        document,
        shapeIndex,
        schemeTypeName.nestedClass("${parameter.kotlinTypeName}${type.replaceFirstChar { it.titlecase() }}Param"),
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
    functionBuilder: FunSpec.Builder,
  ): FunSpec.Builder {

    if (options.explicitSecurityParameters) {
      resolveSecuritySchemes(endPoint, operation).forEach { scheme ->

        scheme.headers?.forEach { header ->
          val headerName = (header.parameterName ?: header.name)!!
          val headerTypeName = resolveSecuritySchemeParameterTypeName(scheme, header, "header")
          val headerParamName =
            "${scheme.name.kotlinIdentifierName}${headerName.kotlinIdentifierName.replaceFirstChar { it.titlecase() }}"
          val parameterBuilder = methodParameter(header, ParameterSpec.builder(headerParamName, headerTypeName))
          parameterBuilder.addAnnotation(jaxRsTypes.headerParam, headerName)
          functionBuilder.addParameter(parameterBuilder.build())
        }

        scheme.queryParameters?.forEach { queryParameter ->
          val queryParameterName = (queryParameter.parameterName ?: queryParameter.name)!!
          val queryParameterTypeName =
            resolveSecuritySchemeParameterTypeName(scheme, queryParameter, "queryParameter")
          val queryParameterParamName =
            scheme.name.kotlinIdentifierName +
              queryParameterName.kotlinIdentifierName.replaceFirstChar { it.titlecase() }
          val parameterBuilder =
            methodParameter(queryParameter, ParameterSpec.builder(queryParameterParamName, queryParameterTypeName))
          functionBuilder.addParameter(parameterBuilder.build())
        }
      }
    }

    functionBuilder.addModifiers(KModifier.ABSTRACT)

    // Add @GET, @POST, @PUT, @DELETE to resource method
    val httpMethodAnnClass = jaxRsTypes.httpMethod(operation.method)
      ?: genError("Unsupported HTTP method", operation)

    functionBuilder.addAnnotation(httpMethodAnnClass)

    // Add @Path
    functionBuilder.addAnnotation(jaxRsTypes.path, endPoint.path)

    return functionBuilder
  }

  private fun methodParameter(
    parameterShape: Parameter,
    parameterBuilder: ParameterSpec.Builder,
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
        AnnotationSpec.builder(jaxRsTypes.defaultValue)
          .addMember("value = %S", defaultValue)
          .build(),
      )
      return newParameter
    }

    return parameterBuilder
  }

  private fun ParameterSpec.Builder.annotateParameter(
    parameter: Parameter,
    parameterType: JaxRsTypes.ParamType,
    requireName: Boolean = false,
  ) =
    addAnnotation(
      AnnotationSpec.builder(jaxRsTypes.paramAnnotation(parameterType))
        .apply {
          if (requireName || jaxRsTypes.isNameRequiredForParameters) {
            addMember("value = %S", parameter.name())
          }
        }
        .build(),
    )

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    // Add @PathParam to URI parameters
    parameterBuilder.annotateParameter(parameter, JaxRsTypes.ParamType.PATH)

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    // Add @QueryParam to URI parameters
    parameterBuilder.annotateParameter(parameter, JaxRsTypes.ParamType.QUERY)

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    // Add @HeaderParam to URI parameters
    parameterBuilder.annotateParameter(parameter, JaxRsTypes.ParamType.HEADER, true)

    return methodParameter(parameter, parameterBuilder).build()
  }

  override fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    typeRegistry.applyUseSiteAnnotations(payloadSchema, parameterBuilder.build().type) {
      parameterBuilder.addAnnotation(it)
    }

    val request = operation.request ?: operation.requests.first()

    val mediaTypesForPayloads = request.payloads.mapNotNull { it.mediaType }

    if (isOverridingDefaultMediaTypes(mediaTypesForPayloads)) {
      functionBuilder.addAnnotation(jaxRsTypes.consumes, mediaTypesForPayloads)
    }

    val isJsonBodyRequested = operation.findBoolAnnotation(JsonBody, generationMode) == true

    return when {
      isJsonBodyRequested -> {
        val orig = parameterBuilder.build()
        ParameterSpec.builder(orig.name, JSON_NODE).build()
      }

      else -> {
        // Finalize
        parameterBuilder.build()
      }
    }
  }

  override fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ): FunSpec {

    referencedProblemTypes.putAll(problemTypes)

    if (generationMode == Server) {
      // Add async response parameter to asynchronous methods
      if (operation.findBoolAnnotation(Asynchronous, generationMode) == true) {
        functionBuilder.addParameter(
          ParameterSpec.builder("asyncResponse", jaxRsTypes.asyncResponse)
            .addAnnotation(jaxRsTypes.suspended)
            .build(),
        )
      }

      // Add Sse & SseEventSink parameter to sse methods
      if (operation.findBoolAnnotation(SSE, generationMode) == true && !options.coroutineServiceMethods) {

        functionBuilder.addParameter(
          ParameterSpec.builder("sse", jaxRsTypes.sse)
            .addAnnotation(jaxRsTypes.context)
            .build(),
        )

        functionBuilder.addParameter(
          ParameterSpec.builder("sseEvents", jaxRsTypes.sseEventSink)
            .addAnnotation(jaxRsTypes.context)
            .build(),
        )
      }

      // Add UriInfo parameter to CREATED methods
      if (operation.successes.firstOrNull()?.statusCode == "${CREATED.code}") {
        functionBuilder.addParameter(
          ParameterSpec.builder("uriInfo", jaxRsTypes.uriInfo)
            .addAnnotation(jaxRsTypes.context)
            .build(),
        )
      }

      // Add JAX-RS context parameters based on jaxrsContext annotation
      val existingParameters = functionBuilder.parameters
      val existingParameterTypes = existingParameters.map { it.type }
      val existingParameterNames = existingParameters.map { it.name }

      fun getUniqueContextParameterName(contextValue: String): String {
        var name = contextValue
        var i = 1
        while (existingParameterNames.contains(name)) {
          name = "$contextValue$i"
          i++
        }
        return name
      }

      val requestedContextValues =
        operation.findArrayAnnotation(JaxrsContext, generationMode)
          ?.mapNotNull { it.stringValue }
          ?.distinct()
          ?: emptyList()

      requestedContextValues.forEach { contextValue ->
        val contextType = jaxRsTypes.contextType(contextValue)
          ?: genError("Unsupported JAX-RS context type '$contextValue'", operation)
        if (!existingParameterTypes.contains(contextType)) {
          val contextParameterName = getUniqueContextParameterName(contextValue)
          functionBuilder.addParameter(
            ParameterSpec.builder(contextParameterName, contextType)
              .addAnnotation(jaxRsTypes.context)
              .build(),
          )
        }
      }
    } else if (!options.alwaysUseResponseReturn) {
      addNullifyMethod(operation, functionBuilder.build(), problemTypes, typeBuilder)
    }

    // Finalize
    return functionBuilder.build()
  }

  private fun referenceTypeUsage(shape: Shape?) {
    shape ?: return
    when (shape) {
      is UnionShape -> shape.flattened.forEach { resolveTypeName(it, null) }
      else -> resolveTypeName(shape, null)
    }
  }

}
