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
import io.outfoxx.sunday.generator.allowEmptyValue
import io.outfoxx.sunday.generator.anyOf
import io.outfoxx.sunday.generator.defaultValue
import io.outfoxx.sunday.generator.defaultValueStr
import io.outfoxx.sunday.generator.discriminatorValue
import io.outfoxx.sunday.generator.findBoolAnnotation
import io.outfoxx.sunday.generator.findStringAnnotation
import io.outfoxx.sunday.generator.hasAnnotation
import io.outfoxx.sunday.generator.mediaType
import io.outfoxx.sunday.generator.method
import io.outfoxx.sunday.generator.name
import io.outfoxx.sunday.generator.path
import io.outfoxx.sunday.generator.payloads
import io.outfoxx.sunday.generator.request
import io.outfoxx.sunday.generator.requests
import io.outfoxx.sunday.generator.resolve
import io.outfoxx.sunday.generator.schema
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
import io.outfoxx.typescriptpoet.TypeName.Companion.OBJECT
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID

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

  override fun processServiceBegin(endPoints: List<EndPoint>, typeBuilder: ClassSpec.Builder): ClassSpec.Builder {

    referencedContentTypes = mutableSetOf()
    referencedAcceptTypes = mutableSetOf()

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

    consBuilder?.let {
      typeBuilder.constructor(it.build())
    }

    // Add default content types (in priority order)
    //

    val contentTypes = defaultMediaTypes.filter { referencedContentTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder(
            "defaultContentTypes",
            TypeName.parameterizedType(ARRAY, MEDIA_TYPE),
            false,
            Modifier.PUBLIC
          )
          .initializer("%L", mediaTypesArray(contentTypes))
          .build()
      )

    // Add default accept types (in priority order)
    //

    val acceptTypes = defaultMediaTypes.filter { referencedAcceptTypes.contains(it) }
    typeBuilder
      .addProperty(
        PropertySpec
          .builder(
            "defaultAcceptTypes",
            TypeName.parameterizedType(ARRAY, MEDIA_TYPE),
            false,
            Modifier.PUBLIC
          )
          .initializer("%L", mediaTypesArray(acceptTypes))
          .build()
      )

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

  override fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val type = parameterBuilder.build().type
    if (type.isOptional) {
      parameterBuilder.defaultValue(CodeBlock.of("%L", if (type.isUndefinable) TypeName.UNDEFINED else TypeName.NULL))
    }

    uriParameters.add(parameter to parameterBuilder.build().type)

    return parameterBuilder.build()
  }

  override fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val type = parameterBuilder.build().type
    if (type.isOptional) {
      parameterBuilder.defaultValue(CodeBlock.of("%L", if (type.isUndefinable) TypeName.UNDEFINED else TypeName.NULL))
    }

    queryParameters.add(parameter to parameterBuilder.build().type)

    return parameterBuilder.build()
  }

  override fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    val type = parameterBuilder.build().type
    if (type.isOptional) {
      parameterBuilder.defaultValue(CodeBlock.of("%L", if (type.isUndefinable) TypeName.UNDEFINED else TypeName.NULL))
    }

    headerParameters.add(parameter to parameterBuilder.build().type)

    return parameterBuilder.build()
  }

  override fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec {

    if (payloadSchema.resolve.findBoolAnnotation(Patchable, null) == true && operation.method == "patch") {
      val orig = parameterBuilder.build()
      return ParameterSpec.builder(orig.name, OBJECT).build()
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
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec {

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

    fun specGen(builder: CodeBlock.Builder) {
      val paramBlocks = mutableListOf<CodeBlock>()
      paramBlocks.add(CodeBlock.of("method: %S", operation.method.toUpperCase()))
      paramBlocks.add(CodeBlock.of("pathTemplate: %S", endPoint.path))

      if (uriParameters.isNotEmpty()) {
        paramBlocks.add(parametersGen("pathParameters", uriParameters))
      }

      if (queryParameters.isNotEmpty()) {
        paramBlocks.add(parametersGen("queryParameters", queryParameters))
      }

      if (requestBodyParameter != null) {
        paramBlocks.add(CodeBlock.of("body: %L", requestBodyParameter))
        if (requestBodyType != null) {

          val requestBodyTypeContext = TypeScriptResolutionContext(document, null, null)

          val requestBodyTypeName = typeRegistry.resolveTypeName(requestBodyType!!, requestBodyTypeContext)
          if (requestBodyTypeName != VOID) {

            val bodyTypePropName = "${generatedFunctionName}BodyType"
            typeProperties[bodyTypePropName] = requestBodyTypeName.typeInitializer()
            paramBlocks.add(CodeBlock.of("bodyType: %L", bodyTypePropName))
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

        paramBlocks.add(CodeBlock.of("contentTypes: %L", contentTypesVal))
      }

      if (resultContentTypes != null && originalReturnType != VOID) {
        val acceptTypes =
          if (resultContentTypes != defaultMediaTypes) {
            mediaTypesArray(resultContentTypes!!)
          } else {
            CodeBlock.of("this.defaultAcceptTypes")
          }
        paramBlocks.add(CodeBlock.of("acceptTypes: %L", acceptTypes))
      }

      if (headerParameters.isNotEmpty()) {
        paramBlocks.add(parametersGen("headers", headerParameters))
      }
      builder.add("{\n%>%L%<\n}", paramBlocks.joinToCode(",\n"))
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
          specGen(builder)
          builder.add(",\neventTypes%]\n);\n")
        }

        else -> {
          builder.add("%[return this.requestFactory.events(\n", originalReturnType)
          specGen(builder)
          builder.add("%]\n);\n")
        }
      }

    } else {

      val requestOnly = operation.findBoolAnnotation(RequestOnly, null) == true
      val responseOnly = operation.findBoolAnnotation(ResponseOnly, null) == true

      val factoryMethod = if (requestOnly) "request" else if (responseOnly) "response" else "result"

      builder.add("%[return this.requestFactory.%L(\n", factoryMethod)

      specGen(builder)

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
      typeCodeBuilder.addStatement("const %N: %T = %L", propName, ANY_TYPE, propInit)
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
