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
import amf.client.model.domain.Shape
import amf.client.model.domain.UnionShape
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.EventSource
import io.outfoxx.sunday.MediaType
import io.outfoxx.sunday.RequestFactory
import io.outfoxx.sunday.URITemplate
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.RequestOnly
import io.outfoxx.sunday.generator.APIAnnotationName.ResponseOnly
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.utils.FLOW
import io.outfoxx.sunday.generator.kotlin.utils.REQUEST
import io.outfoxx.sunday.generator.kotlin.utils.RESPONSE
import io.outfoxx.sunday.generator.kotlin.utils.kotlinConstant
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.flattened
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.mediaType
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.request
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.http.Method
import java.net.URI

class KotlinSundayGenerator(
  document: Document,
  typeRegistry: KotlinTypeRegistry,
  options: Options
) : KotlinGenerator(
  document,
  typeRegistry,
  options
) {

  init {
    require(typeRegistry.generationMode == GenerationMode.Client) {
      "Kotlin/Sunday requires only supports 'Client' generation mode"
    }
  }

  private var uriParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var queryParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var headerParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var originalReturnType: TypeName? = null
  private var requestBodyParameter: String? = null
  private var requestBodyType: Shape? = null
  private var requestBodyContentType: String? = null
  private var resultBodyType: Shape? = null
  private var resultContentTypes: List<String>? = null
  private var consBuilder: FunSpec.Builder? = null

  private var referencedContentTypes = mutableSetOf<String>()
  private var referencedAcceptTypes = mutableSetOf<String>()
  private var referencedProblemTypes = mutableMapOf<URI, TypeName>()

  override fun processServiceBegin(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val serviceTypeBuilder = TypeSpec.classBuilder(serviceTypeName)
    serviceTypeBuilder.tag(TypeSpec.Builder::class, TypeSpec.companionObjectBuilder())

    // Add baseUrl function
    getBaseURIInfo()?.let { (baseURL, baseURLParameters) ->

      val companionTypeBuilder = serviceTypeBuilder.tags[TypeSpec.Builder::class] as TypeSpec.Builder
      companionTypeBuilder
        .apply { typeRegistry.addGeneratedTo(this, false) }
        .addFunction(
          FunSpec.builder("baseURL")
            .returns(URITemplate::class)
            .apply {
              baseURLParameters.forEach { param ->
                val paramTypeName =
                  if (param.defaultValue != null) param.typeName.copy(nullable = false) else param.typeName
                addParameter(
                  ParameterSpec.builder(param.name, paramTypeName)
                    .apply {
                      if (param.defaultValue != null) {
                        defaultValue(param.defaultValue.kotlinConstant(paramTypeName, param.shape?.resolve))
                      }
                    }
                    .build()
                )
              }
            }
            .addCode("return %T(⇥\n", URITemplate::class)
            .addCode("%S,\nmapOf(", baseURL)
            .apply {
              baseURLParameters.forEachIndexed { idx, param ->

                addCode("%S to %L", param.name, param.name)

                if (idx < baseURLParameters.size - 1) {
                  addCode(", ")
                }
              }
            }
            .addCode(")⇤\n)\n")
            .build()
        )
    }

    referencedContentTypes = mutableSetOf()
    referencedAcceptTypes = mutableSetOf("application/problem+json")

    serviceTypeBuilder
      .addProperty(
        PropertySpec.builder("requestFactory", RequestFactory::class, KModifier.PUBLIC)
          .initializer("requestFactory")
          .build()
      )

    consBuilder = FunSpec.constructorBuilder()
      .addParameter("requestFactory", RequestFactory::class)

    return serviceTypeBuilder
  }

  override fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {

    // Add default content types (in priority order)
    //

    val contentTypes = defaultMediaTypes.filter { referencedContentTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultContentTypes", List::class.parameterizedBy(MediaType::class))
          .initializer("defaultContentTypes")
          .build()
      )

    // Add default accept types (in priority order)
    //

    val acceptTypes = defaultMediaTypes.filter { referencedAcceptTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", List::class.parameterizedBy(MediaType::class))
          .initializer("defaultAcceptTypes")
          .build()
      )

    consBuilder?.let { consBuilder ->

      consBuilder
        .addParameter(
          ParameterSpec.builder("defaultContentTypes", List::class.parameterizedBy(MediaType::class))
            .defaultValue("%L", mediaTypesArray(contentTypes))
            .build()
        )
        .addParameter(
          ParameterSpec.builder("defaultAcceptTypes", List::class.parameterizedBy(MediaType::class))
            .defaultValue("%L", mediaTypesArray(acceptTypes))
            .build()
        )

      referencedProblemTypes.forEach { (typeId, typeName) ->
        consBuilder.addStatement("requestFactory.registerProblem(%S, %T::class)", typeId, typeName)
      }

      typeBuilder.primaryConstructor(consBuilder.build())
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

    resultBodyType = body
    originalReturnType = returnTypeName

    val mediaTypesForPayloads = response.payloads.mapNotNull { it.mediaType }
    resultContentTypes =
      if (mediaTypesForPayloads.isNotEmpty()) {
        mediaTypesForPayloads
      } else {
        defaultMediaTypes
      }
    referencedAcceptTypes.addAll(resultContentTypes ?: emptyList())

    if (operation.findBoolAnnotation(APIAnnotationName.EventSource, null) == true) {
      return EventSource::class.asTypeName()
    }

    if (operation.findStringAnnotation(APIAnnotationName.EventStream, null) == "discriminated") {
      if (body !is UnionShape) {
        genError("Discriminated (${APIAnnotationName.EventStream}) requires a union of event types", operation)
      }
      return FLOW.parameterizedBy(returnTypeName)
    }

    return when {
      operation.findBoolAnnotation(RequestOnly, null) == true -> REQUEST
      operation.findBoolAnnotation(ResponseOnly, null) == true -> RESPONSE
      else -> returnTypeName
    }
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec.Builder {

    functionBuilder.addModifiers(KModifier.SUSPEND)

    uriParameters = mutableListOf()
    queryParameters = mutableListOf()
    originalReturnType = null
    requestBodyParameter = null
    requestBodyType = null
    requestBodyContentType = null
    resultContentTypes = null

    return functionBuilder
  }

  private fun methodParameter(parameterBuilder: ParameterSpec.Builder): ParameterSpec {

    val parameter = parameterBuilder.build()

    val type = parameter.type
    if (type.isNullable && parameterBuilder.build().defaultValue == null) {
      parameterBuilder.defaultValue(CodeBlock.of("null"))
    }

    return parameterBuilder.build()
  }

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    uriParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    queryParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    headerParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val actualParameterBuilder =
      if (payloadSchema.resolve.findBoolAnnotation(Patchable, null) == true && operation.method == "patch") {

        val original = parameterBuilder.build()
        val originalTypeName = original.type
        val patchTypeName =
          if (originalTypeName is ClassName)
            originalTypeName.nestedClass("Patch")
          else
            Any::class.asTypeName()

        ParameterSpec.builder(original.name, patchTypeName)
      } else {
        parameterBuilder
      }

    val request = operation.request ?: operation.requests.first()

    val mediaTypesForPayloads = request.payloads.mapNotNull { it.mediaType }
    val requestBodyContentTypes =
      if (mediaTypesForPayloads.isNotEmpty()) {
        mediaTypesForPayloads
      } else {
        defaultMediaTypes
      }
    referencedContentTypes.addAll(requestBodyContentTypes)

    requestBodyContentType = requestBodyContentTypes.firstOrNull()
    requestBodyParameter = actualParameterBuilder.build().name
    requestBodyType = payloadSchema

    val parameter = actualParameterBuilder.build()

    return if (requestBodyContentType == "application/octet-stream") {
      ParameterSpec.builder(parameter.name, ByteArray::class, *parameter.modifiers.toTypedArray()).build()
    } else {
      parameter
    }
  }

  override fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec {

    referencedProblemTypes.putAll(problemTypes)

    val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

    fun parametersGen(fieldName: String, parameters: List<Pair<Parameter, TypeName>>): CodeBlock {
      val parametersBlock = CodeBlock.builder().add("%L = mapOf(⇥\n", fieldName)
      parameters.forEachIndexed { idx, parameterInfo ->
        val (param) = parameterInfo
        val origName = param.name!!
        val paramName = functionBuilderNameAllocator[param]

        parametersBlock.add("%S to %L", origName, paramName)

        if (idx < parameters.size - 1) {
          parametersBlock.add(",\n")
        }
      }
      parametersBlock.add("⇤\n)")
      return parametersBlock.build()
    }

    fun specGen(): CodeBlock {
      val builder = CodeBlock.builder()
      builder.add("method = %T.%L", Method::class, operation.method.replaceFirstChar { it.titlecase() })
      builder.add(",\n")
      builder.add("pathTemplate = %S", endPoint.path)

      if (uriParameters.isNotEmpty()) {
        builder.add(",\n")
        builder.add(parametersGen("pathParameters", uriParameters))
      }

      if (queryParameters.isNotEmpty()) {
        builder.add(",\n")
        builder.add(parametersGen("queryParameters", queryParameters))
      }

      if (requestBodyParameter != null) {
        builder.add(",\n")
        builder.add(CodeBlock.of("body = %L", requestBodyParameter))

        val contentTypesVal =
          when {
            requestBodyContentType != null && !defaultMediaTypes.contains(requestBodyContentType) ->
              mediaTypesArray(requestBodyContentType!!)

            requestBodyParameter != null ->
              CodeBlock.of("this.defaultContentTypes")

            else -> CodeBlock.of("nil")
          }

        builder.add(",\n")
        builder.add("contentTypes = %L", contentTypesVal)
      }

      if (resultContentTypes != null && originalReturnType != Unit::class.asTypeName()) {
        val acceptTypes =
          if (resultContentTypes != defaultMediaTypes) {
            mediaTypesArray(resultContentTypes!!)
          } else {
            CodeBlock.of("this.defaultAcceptTypes")
          }
        builder.add(",\n")
        builder.add("acceptTypes = %L", acceptTypes)
      }

      if (headerParameters.isNotEmpty()) {
        builder.add(",\n")
        builder.add(parametersGen("headers", headerParameters))
      }

      return builder.build()
    }

    val builder = CodeBlock.builder()

    if (
      operation.findBoolAnnotation(APIAnnotationName.EventSource, null) == true ||
      operation.hasAnnotation(APIAnnotationName.EventStream, null)
    ) {

      // Generate EventSource/Event Stream handling method
      when (operation.findStringAnnotation(APIAnnotationName.EventStream, null)) {

        "discriminated" -> {

          val types = (resultBodyType as UnionShape).flattened.filterIsInstance<NodeShape>()
          val typesTemplate = types.joinToString(",") { "\n%S to %M<%T>()" }
          val typesParams = types.flatMap {
            val typeOf = MemberName("io.outfoxx.sunday", "typeOf")
            val typeName = resolveTypeName(it, null)
            val discValue =
              (it.resolve as? NodeShape)?.discriminatorValue ?: (typeName as? ClassName)?.simpleName ?: "$typeName"
            listOf(discValue, typeOf, typeName)
          }

          builder.add("return this.requestFactory.eventStream(⇥\n", originalReturnType)
          builder.add(specGen())
          builder.add(
            ",\neventTypes = mapOf(⇥$typesTemplate⇤\n)",
            *typesParams.toTypedArray()
          )
          builder.add("⇤\n)")
        }

        else -> {
          builder.add("return this.requestFactory.eventSource(⇥\n")
          builder.add(specGen())
          builder.add("⇤\n)")
        }
      }
    } else {

      val requestOnly = operation.findBoolAnnotation(RequestOnly, null) == true
      val responseOnly = operation.findBoolAnnotation(ResponseOnly, null) == true

      val factoryMethod = if (requestOnly) "request" else if (responseOnly) "response" else "result"

      builder.add("return this.requestFactory.%L(⇥\n", factoryMethod)

      builder.add(specGen())

      builder.add("⇤\n)\n")

      if (!requestOnly && !responseOnly) {
        addNullifyMethod(operation, functionBuilder.build(), problemTypes, typeBuilder)
      }
    }

    functionBuilder.addCode(builder.build())

    return functionBuilder.build()
  }

  private fun mediaTypesArray(mimeTypes: List<String>): CodeBlock {
    return mediaTypesArray(*mimeTypes.toTypedArray())
  }

  private fun mediaTypesArray(vararg mimeTypes: String): CodeBlock {
    return mimeTypes.distinct().map { mediaType(it) }.joinToCode(prefix = "listOf(", suffix = ")")
  }

  private fun mediaType(value: String) =
    when (value) {
      "text/plain" -> CodeBlock.of("%T.Plain", MediaType::class)
      "text/html" -> CodeBlock.of("%T.HTML", MediaType::class)
      "application/json" -> CodeBlock.of("%T.JSON", MediaType::class)
      "application/yaml" -> CodeBlock.of("%T.YAML", MediaType::class)
      "application/cbor" -> CodeBlock.of("%T.CBOR", MediaType::class)
      "application/octet-stream" -> CodeBlock.of("%T.OctetStream", MediaType::class)
      "text/event-stream" -> CodeBlock.of("%T.EventStream", MediaType::class)
      "application/x-www-form-urlencoded" -> CodeBlock.of("%T.WWWFormUrlEncoded", MediaType::class)
      "application/problem+json" -> CodeBlock.of("%T.ProblemJSON", MediaType::class)
      "application/x-x509-ca-cert" -> CodeBlock.of("%T.X509CACert", MediaType::class)
      "application/x-x509-user-cert" -> CodeBlock.of("%T.X509UserCert", MediaType::class)
      else -> CodeBlock.of("MediaType.from(%S)", value)
    }
}
