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

package io.outfoxx.sunday.generator.typescript

import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Parameter
import amf.apicontract.client.platform.model.domain.Response
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.domain.Shape
import amf.shapes.client.platform.model.domain.NodeShape
import amf.shapes.client.platform.model.domain.UnionShape
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.APIAnnotationName.EventSource
import io.outfoxx.sunday.generator.APIAnnotationName.EventStream
import io.outfoxx.sunday.generator.APIAnnotationName.Exclude
import io.outfoxx.sunday.generator.APIAnnotationName.RequestOnly
import io.outfoxx.sunday.generator.APIAnnotationName.ResponseOnly
import io.outfoxx.sunday.generator.Generator
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.APIAnnotations
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.typescript.utils.ABORT_SIGNAL
import io.outfoxx.sunday.generator.typescript.utils.ANY_TYPE
import io.outfoxx.sunday.generator.typescript.utils.BODY_INIT
import io.outfoxx.sunday.generator.typescript.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.typescript.utils.MEDIA_TYPE
import io.outfoxx.sunday.generator.typescript.utils.NULLIFY_PROMISE_RESPONSE
import io.outfoxx.sunday.generator.typescript.utils.NULLIFY_RESPONSE
import io.outfoxx.sunday.generator.typescript.utils.OBSERVABLE
import io.outfoxx.sunday.generator.typescript.utils.PROMISE_FROM
import io.outfoxx.sunday.generator.typescript.utils.REQUEST
import io.outfoxx.sunday.generator.typescript.utils.REQUEST_FACTORY
import io.outfoxx.sunday.generator.typescript.utils.RESPONSE
import io.outfoxx.sunday.generator.typescript.utils.RESULT_RESPONSE
import io.outfoxx.sunday.generator.typescript.utils.isOptional
import io.outfoxx.sunday.generator.typescript.utils.isUndefinable
import io.outfoxx.sunday.generator.typescript.utils.isValidTypeScriptIdentifier
import io.outfoxx.sunday.generator.typescript.utils.nullable
import io.outfoxx.sunday.generator.typescript.utils.quotedIfNotTypeScriptIdentifier
import io.outfoxx.sunday.generator.typescript.utils.typeInitializer
import io.outfoxx.sunday.generator.typescript.utils.typeScriptConstant
import io.outfoxx.sunday.generator.utils.allowEmptyValue
import io.outfoxx.sunday.generator.utils.defaultValue
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
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.NameAllocator
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY
import io.outfoxx.typescriptpoet.TypeName.Companion.PROMISE
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import java.net.URI
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

/**
 * Generator for TypeScript/Sunday interfaces
 */
class TypeScriptSundayGenerator(
  document: Document,
  shapeIndex: ShapeIndex,
  typeRegistry: TypeScriptTypeRegistry,
  override val options: Options,
) : TypeScriptGenerator(
  document,
  shapeIndex,
  typeRegistry,
  options,
) {

  class Options(
    val useResultResponseReturn: Boolean,
    val enableAbortablePromises: Boolean,
    defaultProblemBaseUri: String,
    defaultMediaTypes: List<String>,
    serviceSuffix: String,
  ) : Generator.Options(
    defaultProblemBaseUri,
    defaultMediaTypes,
    serviceSuffix,
  )

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

  override fun processServiceBegin(endPoints: List<EndPoint>, typeBuilder: ClassSpec.Builder): ClassSpec.Builder {

    referencedContentTypes = mutableSetOf()
    referencedAcceptTypes = mutableSetOf("application/problem+json")

    typeBuilder
      .addProperty(
        PropertySpec.builder("requestFactory", REQUEST_FACTORY, false, Modifier.PUBLIC)
          .initializer("requestFactory")
          .build(),
      )

    consBuilder = FunctionSpec.constructorBuilder()
      .addParameter("requestFactory", REQUEST_FACTORY)

    return typeBuilder
  }

  override fun processServiceEnd(typeBuilder: ClassSpec.Builder): ClassSpec.Builder {

    // Add default content types (in priority order)
    //

    val contentTypes = defaultMediaTypes.filter { referencedContentTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultContentTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE))
          .build(),
      )

    // Add default accept types (in priority order)
    //

    val acceptTypes = defaultMediaTypes.filter { referencedAcceptTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE))
          .build(),
      )

    consBuilder?.let { consBuilder ->

      val optionsType =
        TypeName.anonymousType(
          listOf(
            TypeName.Anonymous.Member("defaultContentTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE), true),
            TypeName.Anonymous.Member("defaultAcceptTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE), true),
          ),
        )

      consBuilder
        .addParameter(
          ParameterSpec.builder("options", optionsType, true)
            .defaultValue("undefined")
            .build(),
        )
        .addStatement("this.defaultContentTypes =\noptions?.defaultContentTypes ?? %L", mediaTypesArray(contentTypes))
        .addStatement("this.defaultAcceptTypes =\noptions?.defaultAcceptTypes ?? %L", mediaTypesArray(acceptTypes))

      referencedProblemTypes.forEach { (typeId, typeName) ->
        consBuilder.addStatement("requestFactory.registerProblem(%S, %T)", typeId, typeName)
      }

      typeBuilder.constructor(consBuilder.build())
    }

    return super.processServiceEnd(typeBuilder)
  }

  override fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    problemTypes: Map<String, ProblemTypeDefinition>,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    returnTypeName: TypeName,
  ): TypeName {

    resultBodyType = body
    originalReturnType = returnTypeName

    var asyncWrapper =
      if (
        operation.findBoolAnnotation(EventSource, null) == true ||
        operation.hasAnnotation(EventStream, null)
      ) {
        resultContentTypes = listOf("text/event-stream")

        OBSERVABLE
      } else {

        val mediaTypesForPayloads = response.payloads.mapNotNull { it.mediaType }
        resultContentTypes = mediaTypesForPayloads.ifEmpty { defaultMediaTypes }
        referencedAcceptTypes.addAll(resultContentTypes ?: emptyList())

        if (options.enableAbortablePromises) PROMISE else OBSERVABLE
      }

    if (operation.findBoolAnnotation(EventSource, null) == true) {
      return EVENT_SOURCE
    }

    when (operation.findStringAnnotation(EventStream, null)) {
      "simple" -> Unit
      "discriminated" -> {
        if (body !is UnionShape) {
          genError("Discriminated ($EventStream) requires a union of event types", operation)
        }
      }
    }

    val remappedTypeName =
      when {
        operation.findBoolAnnotation(RequestOnly, null) == true -> REQUEST
        operation.findBoolAnnotation(ResponseOnly, null) == true -> RESPONSE
        options.useResultResponseReturn -> RESULT_RESPONSE.parameterized(returnTypeName)
        else -> returnTypeName
      }

    return TypeName.parameterizedType(asyncWrapper, remappedTypeName)
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
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
    if (type.isOptional) {
      parameterBuilder.defaultValue(CodeBlock.of("%L", if (type.isUndefinable) TypeName.UNDEFINED else TypeName.NULL))
    }

    return parameterBuilder.build()
  }

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    uriParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    queryParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    val parameterSpec = methodParameter(parameterBuilder)

    headerParameters.add(parameter to parameterSpec.type)

    return parameterSpec
  }

  override fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec {

    val request = operation.request ?: operation.requests.first()

    val mediaTypesForPayloads = request.payloads.mapNotNull { it.mediaType }
    val requestBodyContentTypes = mediaTypesForPayloads.ifEmpty { defaultMediaTypes }
    referencedContentTypes.addAll(requestBodyContentTypes)

    requestBodyContentType = requestBodyContentTypes.firstOrNull()
    requestBodyParameter = parameterBuilder.build().name
    requestBodyType = payloadSchema

    val parameter = parameterBuilder.build()

    return if (requestBodyContentType == "application/octet-stream") {
      ParameterSpec.builder(parameter.name, BODY_INIT, false, *parameter.modifiers.toTypedArray()).build()
    } else {
      parameter
    }
  }

  override fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
  ): FunctionSpec {

    referencedProblemTypes.putAll(problemTypes)

    val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

    val generatedFunctionName = functionBuilder.build().name

    val typeProperties = mutableMapOf<String, CodeBlock>()

    fun parametersGen(fieldName: String, parameters: List<Pair<Parameter, TypeName>>): CodeBlock {
      val parametersBlock = CodeBlock.builder().add("%L: {%>\n", fieldName)
      parameters.forEachIndexed { idx, parameterInfo ->
        val (param, paramType) = parameterInfo
        val origName = param.name!!
        val paramName = functionBuilderNameAllocator[param]

        if (paramType.isOptional && (param.schema?.defaultValue != null || param.allowEmptyValue == true)) {
          parametersBlock.add(
            "%L: %L ?? %L",
            origName.quotedIfNotTypeScriptIdentifier,
            paramName,
            param.schema?.defaultValue?.typeScriptConstant(paramType, param.schema) ?: "null",
          )
        } else if (paramName != origName || !origName.isValidTypeScriptIdentifier) {
          parametersBlock.add(
            "%L: %L",
            origName.quotedIfNotTypeScriptIdentifier,
            paramName,
          )
        } else {
          parametersBlock.add("%L", paramName)
        }

        if (idx < parameters.size - 1) {
          parametersBlock.add(",\n")
        }
      }
      parametersBlock.add("%<\n}")
      return parametersBlock.build()
    }

    fun specGen(): CodeBlock {
      val builder = CodeBlock.builder()
      builder.add("{%>\n")
      builder.add("method: %S", operation.method.uppercase())
      builder.add(",\n")
      builder.add("pathTemplate: %S", endPoint.path)

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
        builder.add(CodeBlock.of("body: %L", requestBodyParameter))
        if (requestBodyType != null) {

          val requestBodyTypeContext = TypeScriptResolutionContext(document, shapeIndex, null)

          val requestBodyTypeName = typeRegistry.resolveTypeName(requestBodyType!!, requestBodyTypeContext)
          if (requestBodyTypeName != VOID) {

            val bodyTypePropName = "${generatedFunctionName}BodyType"
            typeProperties[bodyTypePropName] = typeRegistry.reflectionTypeName(requestBodyTypeName).typeInitializer()
            builder.add(",\n")
            builder.add("bodyType: %L", bodyTypePropName)
          }
        }

        val contentTypesVal =
          when {
            requestBodyContentType != null && !defaultMediaTypes.contains(requestBodyContentType) ->
              mediaTypesArray(requestBodyContentType!!)

            requestBodyParameter != null ->
              CodeBlock.of("this.defaultContentTypes")

            else -> CodeBlock.of("nil")
          }

        builder.add(",\n")
        builder.add("contentTypes: %L", contentTypesVal)
      }

      if (resultContentTypes != null && originalReturnType != VOID) {
        val acceptTypes =
          if (resultContentTypes != defaultMediaTypes) {
            mediaTypesArray(resultContentTypes!!)
          } else {
            CodeBlock.of("this.defaultAcceptTypes")
          }
        builder.add(",\n")
        builder.add("acceptTypes: %L", acceptTypes)
      }

      if (headerParameters.isNotEmpty()) {
        builder.add(",\n")
        builder.add(parametersGen("headers", headerParameters))
      }

      builder.add("%<\n}")

      return builder.build()
    }

    val builder = CodeBlock.builder()

    if (
      operation.findBoolAnnotation(EventSource, null) == true ||
      operation.hasAnnotation(EventStream, null)
    ) {
      resultContentTypes = listOf("text/event-stream")

      // Generate EventSource/Event Stream handling method
      when (operation.findStringAnnotation(EventStream, null)) {

        "simple" -> {
          builder.add("%[return this.requestFactory.eventStream<%T>(\n", originalReturnType)
          builder.add(specGen())
          builder.add(",\n")
          builder.add("(decoder, event, id, data) => decoder.decodeText(data, [%T])%]\n);\n", originalReturnType)
        }

        "discriminated" -> {

          val types = (resultBodyType as UnionShape).flattened.filterIsInstance<NodeShape>()
          val typesTemplate = types.joinToString("\n  ") { "case %S: return decoder.decodeText(data, [%T]);" }
          val typesParams = types.flatMap {
            val typeName = typeRegistry.resolveTypeName(it, TypeScriptResolutionContext(document, shapeIndex, null))
            val discValue =
              (it as? NodeShape)?.discriminatorValue
                ?: (typeName as? TypeName.Standard)?.simpleName()
                ?: "$typeName"
            listOf(discValue, typeName)
          }

          builder.add("%[return this.requestFactory.eventStream<%T>(\n", originalReturnType)
          builder.add(specGen())
          builder.add(
            """,
            |(decoder, event, id, data, logger) => {
            |  switch (event) {
            |  $typesTemplate
            |  default:
            |    logger?.error?.(`Unknown event type, ignoring event: event=${'$'}{event}`);
            |    return undefined;
            |  }
            |},
            """.trimMargin(),
            *typesParams.toTypedArray(),
          )
          builder.add("%]\n);\n")
        }

        else -> {
          builder.add("%[return this.requestFactory.eventSource(\n", originalReturnType)
          builder.add(specGen())
          builder.add("%]\n);\n")
        }
      }
    } else {

      val requestOnly = operation.findBoolAnnotation(RequestOnly, null) == true
      val responseOnly = operation.findBoolAnnotation(ResponseOnly, null) == true

      if (options.enableAbortablePromises) {
        functionBuilder.addParameter("signal", ABORT_SIGNAL, true)
      }

      val factoryMethod =
        when {
          requestOnly -> "request"
          responseOnly -> "response"
          options.useResultResponseReturn -> "resultResponse"
          else -> "result"
        }

      if (options.enableAbortablePromises) {
        builder.add("%[return %Q(this.requestFactory.%L(\n", PROMISE_FROM, factoryMethod)
      } else {
        builder.add("%[return this.requestFactory.%L(\n", factoryMethod)
      }

      builder.add(specGen())

      if (!requestOnly && !responseOnly && originalReturnType != null && originalReturnType != VOID) {

        val retTypePropName = "${generatedFunctionName}ReturnType"
        typeProperties[retTypePropName] = typeRegistry.reflectionTypeName(originalReturnType!!).typeInitializer()
        builder.add(",\n%L", retTypePropName)
      }

      if (options.enableAbortablePromises) {
        builder.add("%]\n), signal);\n")
      } else {
        builder.add("%]\n);\n")
      }

      if (!requestOnly && !responseOnly) {
        addNullifyMethod(operation, functionBuilder.build(), problemTypes, typeBuilder)
      }
    }

    functionBuilder.addCode(builder.build())

    val resultMethod = functionBuilder.build()

    if (operation.findBoolAnnotation(Exclude, null) != true) {

      val typeCodeBuilder = typeBuilder.tags[CodeBlock.Builder::class] as CodeBlock.Builder
      typeProperties.forEach { (propName, propInit) ->
        typeCodeBuilder.add("%[const %N: %T = ", propName, ANY_TYPE)
        typeCodeBuilder.add(propInit)
        typeCodeBuilder.add(";\n%]")
      }
    }

    return resultMethod
  }

  private fun addNullifyMethod(
    operation: Operation,
    function: FunctionSpec,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: ClassSpec.Builder,
  ) {

    val nullifyAnn = operation.findArrayAnnotation(APIAnnotationName.Nullify, null)
    if (nullifyAnn.isNullOrEmpty()) {
      return
    }

    val (nullifyProblemTypeCodes, nullifyStatuses) = APIAnnotations.groupNullifyIntoStatusesAndProblems(nullifyAnn)

    val nullifyProblemTypeNames =
      problemTypes
        .filter { (key) -> nullifyProblemTypeCodes.any { key.path.endsWith(it) } }
        .map { it.value }
        .toTypedArray()

    val nullFunCodeBuilder =
      CodeBlock.builder()
        .add(
          """
          |return this.%N(${function.parameters.joinToString { it.name }})
          |  .%L(%Q(
          |    [${nullifyStatuses.joinToString { "$it" }}],
          |    [${nullifyProblemTypeNames.joinToString { "%T" }}]
          |  ));
          |
          """.trimMargin(),
          function.name,
          if (options.enableAbortablePromises) "catch" else "pipe",
          if (options.enableAbortablePromises) NULLIFY_PROMISE_RESPONSE else NULLIFY_RESPONSE,
          *nullifyProblemTypeNames,
        )

    val returnType = function.returnType
    val returnTypeOptional =
      when {
        returnType is TypeName.Parameterized && returnType.typeArgs.size == 1 ->
          returnType.rawType.parameterized(returnType.typeArgs[0].nullable)

        else -> returnType?.nullable
      }

    typeBuilder.addFunction(
      FunctionSpec.builder("${function.name}OrNull")
        .addDecorators(function.decorators)
        .addModifiers(function.modifiers)
        .addTypeVariables(function.typeVariables)
        .addParameters(function.parameters)
        .apply {
          returnTypeOptional?.let { returns(it) }
        }
        .addTSDoc(function.tsDoc)
        .addCode(nullFunCodeBuilder.build())
        .build(),
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
      "text/plain" -> CodeBlock.of("%T.Text", MEDIA_TYPE)
      "text/html" -> CodeBlock.of("%T.HTML", MEDIA_TYPE)
      "application/json" -> CodeBlock.of("%T.JSON", MEDIA_TYPE)
      "application/yaml" -> CodeBlock.of("%T.YAML", MEDIA_TYPE)
      "application/cbor" -> CodeBlock.of("%T.CBOR", MEDIA_TYPE)
      "application/octet-stream" -> CodeBlock.of("%T.OctetStream", MEDIA_TYPE)
      "text/event-stream" -> CodeBlock.of("%T.EventStream", MEDIA_TYPE)
      "application/x-www-form-urlencoded" -> CodeBlock.of("%T.WWWFormURLEncoded", MEDIA_TYPE)
      "application/problem+json" -> CodeBlock.of("%T.Problem", MEDIA_TYPE)
      "application/x-x509-ca-cert" -> CodeBlock.of("%T.X509CACert", MEDIA_TYPE)
      "application/x-x509-user-cert" -> CodeBlock.of("%T.X509UserCert", MEDIA_TYPE)
      "application/json-patch+json" -> CodeBlock.of("%T.JsonPatch", MEDIA_TYPE)
      "application/merge-patch+json" -> CodeBlock.of("%T.MergePatch", MEDIA_TYPE)
      else -> CodeBlock.of("%T.from(%S)", MEDIA_TYPE, value)
    }
}
