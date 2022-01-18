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

package io.outfoxx.sunday.generator.swift

import amf.client.model.document.Document
import amf.client.model.domain.EndPoint
import amf.client.model.domain.NodeShape
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.Shape
import amf.client.model.domain.UnionShape
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.APIAnnotationName.Nullify
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.RequestOnly
import io.outfoxx.sunday.generator.APIAnnotationName.ResponseOnly
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.APIAnnotations.groupNullifyIntoStatusesAndProblems
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE
import io.outfoxx.sunday.generator.swift.utils.ASYNC_STREAM
import io.outfoxx.sunday.generator.swift.utils.DATA_RESPONSE
import io.outfoxx.sunday.generator.swift.utils.DICTIONARY_STRING_ANY
import io.outfoxx.sunday.generator.swift.utils.DICTIONARY_STRING_ANY_OPTIONAL
import io.outfoxx.sunday.generator.swift.utils.EMPTY
import io.outfoxx.sunday.generator.swift.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.swift.utils.MEDIA_TYPE_ARRAY
import io.outfoxx.sunday.generator.swift.utils.REQUEST_FACTORY
import io.outfoxx.sunday.generator.swift.utils.URI_TEMPLATE
import io.outfoxx.sunday.generator.swift.utils.URL_REQUEST
import io.outfoxx.sunday.generator.swift.utils.swiftConstant
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
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
import io.outfoxx.swiftpoet.ANY
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DICTIONARY
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.NameAllocator
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.ParameterizedTypeName
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.VOID
import io.outfoxx.swiftpoet.joinToCode
import io.outfoxx.swiftpoet.parameterizedBy
import java.net.URI

class SwiftSundayGenerator(
  document: Document,
  typeRegistry: SwiftTypeRegistry,
  options: Options,
) : SwiftGenerator(
  document,
  typeRegistry,
  options,
) {

  private var uriParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var queryParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var headerParameters = mutableListOf<Pair<Parameter, TypeName>>()
  private var originalReturnType: TypeName? = null
  private var requestBodyParameter: String? = null
  private var requestBodyType: Shape? = null
  private var requestBodyContentType: String? = null
  private var resultBodyType: Shape? = null
  private var resultContentTypes: List<String>? = null
  private var consBuilder: FunctionSpec.Builder? = null

  private var referencedContentTypes = mutableSetOf<String>()
  private var referencedAcceptTypes = mutableSetOf<String>()
  private var referencedProblemTypes = mutableMapOf<URI, TypeName>()

  override fun processServiceBegin(serviceTypeName: DeclaredTypeName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val serviceTypeBuilder = TypeSpec.classBuilder(serviceTypeName)

    // Add baseUrl function
    getBaseURIInfo()?.let { (baseURL, baseURLParameters) ->

      serviceTypeBuilder.addFunction(
        FunctionSpec.builder("baseURL")
          .addModifiers(PUBLIC, STATIC)
          .returns(URI_TEMPLATE)
          .apply {
            baseURLParameters.forEach { param ->
              val paramTypeName = if (param.defaultValue != null) param.typeName.makeNonOptional() else param.typeName
              addParameter(
                ParameterSpec.builder(param.name, paramTypeName)
                  .apply {
                    if (param.defaultValue != null) {
                      defaultValue(param.defaultValue.swiftConstant(paramTypeName, param.shape?.resolve))
                    }
                  }
                  .build()
              )
            }
          }
          .addCode("return %T(%>\n", URI_TEMPLATE)
          .addCode("format: %S,\nparameters: [%>\n", baseURL)
          .apply {
            if (baseURLParameters.isEmpty()) {
              addCode(":")
            }
            baseURLParameters.forEachIndexed { idx, param ->

              addCode("%S: %L", param.name, param.name)

              if (idx < baseURLParameters.size - 1) {
                addCode(",\n")
              }
            }
          }
          .addCode("%<\n]%<\n)\n")
          .build()
      )
    }

    referencedContentTypes = mutableSetOf()
    referencedAcceptTypes = mutableSetOf("application/problem+json")

    serviceTypeBuilder.addProperty("requestFactory", REQUEST_FACTORY, PUBLIC)

    consBuilder = FunctionSpec.constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("requestFactory", REQUEST_FACTORY)
      .addStatement("self.requestFactory = requestFactory")

    return serviceTypeBuilder
  }

  override fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {

    // Add default content types (in priority order)
    //

    val contentTypes = defaultMediaTypes.filter { referencedContentTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
          .addModifiers(PUBLIC)
          .build()
      )

    // Add default accept types (in priority order)
    //

    val acceptTypes = defaultMediaTypes.filter { referencedAcceptTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
          .addModifiers(PUBLIC)
          .build()
      )

    consBuilder?.let { consBuilder ->

      consBuilder
        .addParameter(
          ParameterSpec.builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
            .defaultValue("%L", mediaTypesArray(contentTypes))
            .build()
        )
        .addStatement("self.defaultContentTypes = defaultContentTypes")
        .addParameter(
          ParameterSpec.builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
            .defaultValue("%L", mediaTypesArray(acceptTypes))
            .build()
        )
        .addStatement("self.defaultAcceptTypes = defaultAcceptTypes")
        .build()

      referencedProblemTypes.forEach { (typeId, typeName) ->
        consBuilder.addStatement("requestFactory.registerProblem(type: %S, problemType: %T.self)", typeId, typeName)
      }

      typeBuilder.addFunction(consBuilder.build())
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
    functionBuilder: FunctionSpec.Builder,
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
      return EVENT_SOURCE
    }

    if (operation.findStringAnnotation(APIAnnotationName.EventStream, null) == "discriminated") {
      if (body !is UnionShape) {
        genError("Discriminated (${APIAnnotationName.EventStream}) requires a union of event types", operation)
      }
      return ASYNC_STREAM.parameterizedBy(returnTypeName)
    }

    val elementReturnType =
      when (returnTypeName.makeNonOptional()) {
        DICTIONARY_STRING_ANY, DICTIONARY_STRING_ANY_OPTIONAL -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
        ANY -> ANY_VALUE
        else -> returnTypeName
      }

    return when {
      operation.findBoolAnnotation(RequestOnly, null) == true -> URL_REQUEST
      operation.findBoolAnnotation(ResponseOnly, null) == true -> DATA_RESPONSE
      else -> elementReturnType
    }
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec.Builder {

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
    if (type.optional && parameterBuilder.build().defaultValue == null) {
      parameterBuilder.defaultValue(CodeBlock.of("nil"))
    }

    return parameterBuilder.build()
  }

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
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
    functionBuilder: FunctionSpec.Builder,
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
    functionBuilder: FunctionSpec.Builder,
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
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val actualParameterBuilder =
      if (payloadSchema.resolve.findBoolAnnotation(Patchable, null) == true && operation.method == "patch") {

        val original = parameterBuilder.build()
        val originalTypeName = original.type
        val patchTypeName =
          if (originalTypeName is DeclaredTypeName)
            originalTypeName.nestedType("Patch")
          else
            ANY

        ParameterSpec.builder(original.parameterName, patchTypeName)
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
    requestBodyParameter = actualParameterBuilder.build().parameterName
    requestBodyType = payloadSchema

    val parameter = actualParameterBuilder.build()

    return if (requestBodyContentType == "application/octet-stream") {
      ParameterSpec.builder(parameter.parameterName, DATA, *parameter.modifiers.toTypedArray()).build()
    } else {
      parameter
    }
  }

  override fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, DeclaredTypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec {

    referencedProblemTypes.putAll(problemTypes)

    val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

    fun parametersGen(fieldName: String, parameters: List<Pair<Parameter, TypeName>>): CodeBlock {
      val parametersBlock = CodeBlock.builder().add("%L: [%>\n", fieldName)
      parameters.forEachIndexed { idx, parameterInfo ->
        val (param, paramType) = parameterInfo
        val origName = param.name!!
        val paramName = functionBuilderNameAllocator[param]

        parametersBlock.add("%S: %L", origName, paramName)
        if (paramType.optional) {
          parametersBlock.add(" as Any")
        }

        if (idx < parameters.size - 1) {
          parametersBlock.add(",\n")
        }
      }
      parametersBlock.add("%<\n]")
      return parametersBlock.build()
    }

    fun reqGen(): CodeBlock {
      val builder = CodeBlock.builder()
      builder.add("method: .%L", operation.method.lowercase())
      builder.add(",\n")
      builder.add("pathTemplate: %S", endPoint.path)

      builder.add(",\n")
      if (uriParameters.isNotEmpty()) {
        builder.add(parametersGen("pathParameters", uriParameters))
      } else {
        builder.add("pathParameters: nil")
      }

      builder.add(",\n")
      if (queryParameters.isNotEmpty()) {
        builder.add(parametersGen("queryParameters", queryParameters))
      } else {
        builder.add("queryParameters: nil")
      }

      builder.add(",\n")
      if (requestBodyParameter != null) {
        builder.add(CodeBlock.of("body: %L", requestBodyParameter))

        val contentTypesVal =
          when {
            requestBodyContentType != null && !defaultMediaTypes.contains(requestBodyContentType) ->
              mediaTypesArray(requestBodyContentType!!)

            requestBodyParameter != null ->
              CodeBlock.of("self.defaultContentTypes")

            else -> CodeBlock.of("nil")
          }

        builder.add(",\n")
        builder.add("contentTypes: %L", contentTypesVal)
      } else {
        builder.add("body: %T.none,\ncontentTypes: nil", EMPTY)
      }

      builder.add(",\n")
      if (resultContentTypes != null && originalReturnType != VOID) {
        val acceptTypes =
          if (resultContentTypes != defaultMediaTypes) {
            mediaTypesArray(resultContentTypes!!)
          } else {
            CodeBlock.of("self.defaultAcceptTypes")
          }
        builder.add("acceptTypes: %L", acceptTypes)
      } else {
        builder.add("acceptTypes: self.defaultAcceptTypes")
      }

      builder.add(",\n")
      if (headerParameters.isNotEmpty()) {
        builder.add(parametersGen("headers", headerParameters))
      } else {
        builder.add("headers: nil")
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
          val typesTemplate = types.joinToString(",") { "\n%S: .erase(%T.self)" }
          val typesParams = types.flatMap {
            val typeName = resolveTypeName(it, null)
            val discValue =
              (it.resolve as? NodeShape)?.discriminatorValue
                ?: (typeName as? DeclaredTypeName)?.simpleName
                ?: "$typeName"
            listOf(discValue, typeName)
          }

          builder.add("return self.requestFactory.eventStream(%>\n", originalReturnType)
          builder.add(reqGen())
          builder.add(
            ",\neventTypes: [%>$typesTemplate%<\n]",
            *typesParams.toTypedArray()
          )
          builder.add("%<\n)")
        }

        else -> {
          builder.add("return self.requestFactory.eventSource(%>\n")
          builder.add(reqGen())
          builder.add("%<\n)")
        }
      }
    } else {

      val requestOnly = operation.findBoolAnnotation(RequestOnly, null) == true
      val responseOnly = operation.findBoolAnnotation(ResponseOnly, null) == true

      val factoryMethod = if (requestOnly) "request" else if (responseOnly) "response" else "result"

      builder.add("return try await self.requestFactory.%L(%>\n", factoryMethod)

      builder.add(reqGen())

      builder.add("%<\n)\n")

      if (!requestOnly && !responseOnly) {
        addNullifyMethod(operation, functionBuilder.build(), problemTypes, typeBuilder)
      }

      functionBuilder
        .async(true)
        .throws(true)
    }

    functionBuilder.addCode(builder.build())

    return functionBuilder.build()
  }

  private fun addNullifyMethod(
    operation: Operation,
    function: FunctionSpec,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder
  ) {

    val nullifyAnn = operation.findArrayAnnotation(Nullify, null)
    if (nullifyAnn == null || nullifyAnn.isEmpty()) {
      return
    }

    val (nullifyProblemTypeCodes, nullifyStatuses) = groupNullifyIntoStatusesAndProblems(nullifyAnn)

    val nullifyProblemTypeNames =
      problemTypes
        .filter { (key) -> nullifyProblemTypeCodes.any { key.path.endsWith(it) } }
        .map { it.value }
        .toTypedArray()

    val nullFunCodeBuilder =
      CodeBlock.builder()
        .add(
          """
          |return try await nilifyResponse(
          |    statuses: [${nullifyStatuses.joinToString { "$it" }}],
          |    problemTypes: [${nullifyProblemTypeNames.joinToString { "%T.self" }}]
          |  ) {
          |    try await %N(${function.parameters.map { it.parameterName }.joinToString { "$it: $it" }})
          |  }
          |
          """.trimMargin(),
          *nullifyProblemTypeNames, function.name,
        )

    val returnType = function.returnType
    val returnTypeOptional =
      when {
        returnType is ParameterizedTypeName && returnType.typeArguments.size == 1 ->
          returnType.rawType.parameterizedBy(returnType.typeArguments[0].makeOptional())
        else -> returnType?.makeOptional()
      }

    typeBuilder.addFunction(
      FunctionSpec.builder("${function.name}OrNil")
        .addModifiers(function.modifiers)
        .addTypeVariables(function.typeVariables)
        .addParameters(function.parameters)
        .apply {
          function.attributes.forEach { addAttribute(it) }
          returnTypeOptional?.let { returns(it) }
        }
        .async(true)
        .throws(true)
        .addDoc(function.doc)
        .addCode(nullFunCodeBuilder.build())
        .build()
    )
  }

  private fun mediaTypesArray(mimeTypes: List<String>): CodeBlock {
    return mediaTypesArray(*mimeTypes.toTypedArray())
  }

  private fun mediaTypesArray(vararg mimeTypes: String): CodeBlock {
    return mimeTypes.distinct().map { mediaType(it) }.joinToCode(prefix = "[", suffix = "]")
  }

  private fun mediaType(value: String) =
    when (value) {
      "text/plain" -> CodeBlock.of(".plain")
      "text/html" -> CodeBlock.of(".html")
      "application/json" -> CodeBlock.of(".json")
      "application/yaml" -> CodeBlock.of(".yaml")
      "application/cbor" -> CodeBlock.of(".cbor")
      "application/octet-stream" -> CodeBlock.of(".octetStream")
      "text/event-stream" -> CodeBlock.of(".eventStream")
      "application/x-www-form-urlencoded" -> CodeBlock.of(".wwwFormUrlEncoded")
      "application/problem+json" -> CodeBlock.of(".problem")
      "application/x-x509-ca-cert" -> CodeBlock.of(".x509CACert")
      "application/x-x509-user-cert" -> CodeBlock.of(".x509UserCert")
      else -> CodeBlock.of(".init(valid: %S)", value)
    }
}
