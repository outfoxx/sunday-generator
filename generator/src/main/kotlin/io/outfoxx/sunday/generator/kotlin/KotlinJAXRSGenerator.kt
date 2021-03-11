package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.Document
import amf.client.model.domain.EndPoint
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.Shape
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
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
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.Reactive
import io.outfoxx.sunday.generator.APIAnnotationName.SSE
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.utils.kotlinConstant
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.mediaType
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.request
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.schema
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
  reactiveResponseType: String?,
  defaultServicePackageName: String,
  defaultProblemBaseUri: String,
  defaultMediaTypes: List<String>,
) : KotlinGenerator(
  document,
  typeRegistry,
  defaultServicePackageName,
  defaultProblemBaseUri,
  defaultMediaTypes,
) {

  private val referencedProblemTypes = mutableMapOf<URI, TypeName>()
  private val reactiveResponseType = reactiveResponseType?.let { ClassName.bestGuess(it) }

  override fun processServiceBegin(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val typeBuilder = TypeSpec.interfaceBuilder(serviceTypeName)
    typeBuilder.tag(TypeSpec.Builder::class, TypeSpec.companionObjectBuilder())

    if (defaultMediaTypes.isNotEmpty()) {

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

    if (typeRegistry.options.contains(JacksonAnnotations)) {
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

    val isSSE = operation.findBoolAnnotation(SSE, generationMode) == true

    val reactive = operation.findBoolAnnotation(Reactive, generationMode) ?: reactiveResponseType != null
    if (reactive && reactiveResponseType != null && !isSSE) {

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
      return if (generationMode == Client) {
        return SseEventSource::class.asTypeName()
      } else {
        UNIT
      }
    }

    return if (generationMode == Client) {
      returnTypeName
    } else {
      javax.ws.rs.core.Response::class.asTypeName()
    }
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec.Builder {

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
    val defaultValue = parameterShape.schema?.defaultValue
    if (defaultValue != null) {
      val newParameter =
        ParameterSpec.builder(builtParameter.name, builtParameter.type.copy(nullable = false))
          .addKdoc(builtParameter.kdoc)
          .addAnnotations(builtParameter.annotations)
          .addModifiers(builtParameter.modifiers)
      newParameter.addAnnotation(
        AnnotationSpec.builder(DefaultValue::class)
          .addMember("value = %S", defaultValue.kotlinConstant(builtParameter.type, parameterShape.schema))
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

    if (payloadSchema.resolve.findBoolAnnotation(Patchable, generationMode) == true && operation.method == "patch") {
      val orig = parameterBuilder.build()
      return ParameterSpec.builder(orig.name, ObjectNode::class.asTypeName()).build()
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
      if (operation.findBoolAnnotation(SSE, generationMode) == true) {

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
      else -> throw IllegalStateException("Invalid HTTP method: $methodName")
    }

}
