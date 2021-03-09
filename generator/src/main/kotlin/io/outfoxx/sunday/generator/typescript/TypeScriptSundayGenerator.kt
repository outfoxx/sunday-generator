package io.outfoxx.sunday.generator.typescript

import amf.client.model.document.Document
import amf.client.model.domain.EndPoint
import amf.client.model.domain.NodeShape
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.Shape
import amf.client.model.domain.UnionShape
import io.outfoxx.sunday.generator.APIAnnotationName.EventSource
import io.outfoxx.sunday.generator.APIAnnotationName.EventStream
import io.outfoxx.sunday.generator.APIAnnotationName.Patchable
import io.outfoxx.sunday.generator.APIAnnotationName.RequestOnly
import io.outfoxx.sunday.generator.APIAnnotationName.ResponseOnly
import io.outfoxx.sunday.generator.typescript.utils.ANY_TYPE
import io.outfoxx.sunday.generator.typescript.utils.BODY_INIT
import io.outfoxx.sunday.generator.typescript.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.typescript.utils.EVENT_TYPES
import io.outfoxx.sunday.generator.typescript.utils.MEDIA_TYPE
import io.outfoxx.sunday.generator.typescript.utils.OBSERVABLE
import io.outfoxx.sunday.generator.typescript.utils.REQUEST_FACTORY
import io.outfoxx.sunday.generator.typescript.utils.isOptional
import io.outfoxx.sunday.generator.typescript.utils.isUndefinable
import io.outfoxx.sunday.generator.typescript.utils.isValidTypeScriptIdentifier
import io.outfoxx.sunday.generator.typescript.utils.quotedIfNotTypeScriptIdentifier
import io.outfoxx.sunday.generator.typescript.utils.typeInitializer
import io.outfoxx.sunday.generator.utils.allowEmptyValue
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.defaultValueStr
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.mediaType
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.request
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.resolve
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
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import java.net.URI

/**
 * Generator for TypeScript/Sunday interfaces
 */
class TypeScriptSundayGenerator(
  document: Document,
  typeRegistry: TypeScriptTypeRegistry,
  defaultProblemBaseUri: String,
  defaultMediaTypes: List<String>,
) : TypeScriptGenerator(
  document,
  typeRegistry,
  defaultProblemBaseUri,
  defaultMediaTypes,
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

  override fun processServiceBegin(endPoints: List<EndPoint>, typeBuilder: ClassSpec.Builder): ClassSpec.Builder {

    referencedContentTypes = mutableSetOf()
    referencedAcceptTypes = mutableSetOf("application/problem+json")

    typeBuilder
      .addProperty(
        PropertySpec.builder("requestFactory", REQUEST_FACTORY, false, Modifier.PUBLIC)
          .initializer("requestFactory")
          .build()
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
          .initializer("defaultContentTypes")
          .build()
      )

    // Add default accept types (in priority order)
    //

    val acceptTypes = defaultMediaTypes.filter { referencedAcceptTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE))
          .initializer("defaultAcceptTypes")
          .build()
      )

    consBuilder?.let { consBuilder ->

      consBuilder
        .addParameter(
          ParameterSpec.builder("defaultContentTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE))
            .defaultValue("%L", mediaTypesArray(contentTypes))
            .build()
        )
        .addParameter(
          ParameterSpec.builder("defaultAcceptTypes", TypeName.parameterizedType(ARRAY, MEDIA_TYPE))
            .defaultValue("%L", mediaTypesArray(acceptTypes))
            .build()
        )

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
    typeBuilder: ClassSpec.Builder,
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

    if (operation.findBoolAnnotation(EventSource, null) == true) {
      return EVENT_SOURCE
    }

    if (operation.findStringAnnotation(EventStream, null) == "discriminated") {
      if (body !is UnionShape) {
        throw IllegalStateException(
          "Discriminated eventObservable requires a union of event types"
        )
      }
    }

    return TypeName.parameterizedType(OBSERVABLE, returnTypeName)
  }

  override fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: ClassSpec.Builder,
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
    typeBuilder: ClassSpec.Builder,
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
    typeBuilder: ClassSpec.Builder,
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
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val actualParameterBuilder =
      if (payloadSchema.resolve.findBoolAnnotation(Patchable, null) == true && operation.method == "patch") {

        val original = parameterBuilder.build()
        val originalTypeName = original.type
        val patchTypeName =
          if (originalTypeName is TypeName.Standard)
            originalTypeName.nested("Patch")
          else
            TypeName.ANY

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
    functionBuilder: FunctionSpec.Builder
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
            param.schema?.defaultValueStr ?: "null"
          )
        } else if (paramName != origName || !origName.isValidTypeScriptIdentifier) {
          parametersBlock.add(
            "%L: %L",
            origName.quotedIfNotTypeScriptIdentifier,
            paramName
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
      builder.add("method: %S", operation.method.toUpperCase())
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

          val requestBodyTypeContext = TypeScriptResolutionContext(document, null)

          val requestBodyTypeName = typeRegistry.resolveTypeName(requestBodyType!!, requestBodyTypeContext)
          if (requestBodyTypeName != VOID) {

            val bodyTypePropName = "${generatedFunctionName}BodyType"
            typeProperties[bodyTypePropName] = requestBodyTypeName.typeInitializer()
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

    if (operation.findBoolAnnotation(EventSource, null) == true || operation.hasAnnotation(EventStream, null)) {

      // Generate EventSource/Event Stream handling method
      when (operation.findStringAnnotation(EventStream, null)) {

        "discriminated" -> {

          val types = (resultBodyType as UnionShape).anyOf.filterIsInstance<NodeShape>()
          val typesTemplate = types.joinToString { "\n%S : [%T]" }
          val typesParams = types.flatMap {
            val typeName = typeRegistry.resolveTypeName(it, TypeScriptResolutionContext(document, null))
            val discValue = it.discriminatorValue ?: (typeName as? TypeName.Standard)?.simpleName() ?: "$typeName"
            listOf(discValue, typeName)
          }

          builder.add(
            "const eventTypes: %T<%T> = {%>$typesTemplate%<\n};\n",
            EVENT_TYPES,
            originalReturnType,
            *typesParams.toTypedArray()
          )

          builder.add("%[return this.requestFactory.events<%T>(\n", originalReturnType)
          builder.add(specGen())
          builder.add(",\n")
          builder.add("eventTypes%]\n);\n")
        }

        else -> {
          builder.add("%[return this.requestFactory.events(\n", originalReturnType)
          builder.add(specGen())
          builder.add("%]\n);\n")
        }
      }

    } else {

      val requestOnly = operation.findBoolAnnotation(RequestOnly, null) == true
      val responseOnly = operation.findBoolAnnotation(ResponseOnly, null) == true

      val factoryMethod = if (requestOnly) "request" else if (responseOnly) "response" else "result"

      builder.add("%[return this.requestFactory.%L(\n", factoryMethod)

      builder.add(specGen())

      if (!requestOnly && !responseOnly && originalReturnType != null && originalReturnType != VOID) {

        val retTypePropName = "${generatedFunctionName}ReturnType"
        typeProperties[retTypePropName] = originalReturnType!!.typeInitializer()
        builder.add(",\n%L", retTypePropName)
      }

      builder.add("%]\n);\n")
    }

    functionBuilder.addCode(builder.build())

    val resultMethod = functionBuilder.build()

    val typeCodeBuilder = typeBuilder.tags[CodeBlock.Builder::class] as CodeBlock.Builder
    typeProperties.forEach { (propName, propInit) ->
      typeCodeBuilder.add("%[const %N: %T = ", propName, ANY_TYPE)
      typeCodeBuilder.add(propInit)
      typeCodeBuilder.add(";\n%]")
    }

    return resultMethod
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
      "application/x-x509-ca-cert" -> CodeBlock.of("%T.X509CACert", MEDIA_TYPE)
      "application/x-www-form-urlencoded" -> CodeBlock.of("%T.WWWFormURLEncoded", MEDIA_TYPE)
      "application/problem+json" -> CodeBlock.of("%T.ProblemJSON", MEDIA_TYPE)
      else -> CodeBlock.of("MediaType.from(%S)", value)
    }

}
