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
import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.CustomizableElement
import amf.core.client.platform.model.domain.DataNode
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import com.damnhandy.uri.template.UriTemplate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import io.outfoxx.sunday.generator.APIAnnotationName.Exclude
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinModelPkg
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinPkg
import io.outfoxx.sunday.generator.APIAnnotationName.Nullify
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUri
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUriParams
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemTypes
import io.outfoxx.sunday.generator.APIAnnotationName.Problems
import io.outfoxx.sunday.generator.APIAnnotationName.ServiceGroup
import io.outfoxx.sunday.generator.APIAnnotationName.ServiceName
import io.outfoxx.sunday.generator.Generator
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.APIAnnotations.groupNullifyIntoStatusesAndProblems
import io.outfoxx.sunday.generator.common.HttpStatus.NO_CONTENT
import io.outfoxx.sunday.generator.common.NameGenerator
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.utils.RESULT_RESPONSE
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE3
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE3
import io.outfoxx.sunday.generator.kotlin.utils.THROWABLE_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.UNI
import io.outfoxx.sunday.generator.kotlin.utils.kotlinConstant
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.kotlin.utils.rawType
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.description
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.failures
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.objectValue
import io.outfoxx.sunday.generator.utils.operations
import io.outfoxx.sunday.generator.utils.parameters
import io.outfoxx.sunday.generator.utils.payloads
import io.outfoxx.sunday.generator.utils.queryParameters
import io.outfoxx.sunday.generator.utils.request
import io.outfoxx.sunday.generator.utils.requests
import io.outfoxx.sunday.generator.utils.required
import io.outfoxx.sunday.generator.utils.root
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.sunday.generator.utils.servers
import io.outfoxx.sunday.generator.utils.statusCode
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.successes
import io.outfoxx.sunday.generator.utils.url
import io.outfoxx.sunday.generator.utils.variables
import io.outfoxx.sunday.generator.utils.version
import java.net.URI
import java.net.URISyntaxException
import java.util.Optional
import java.util.concurrent.CompletionStage

/**
 * Generator for Kotlin language framework targets
 */
abstract class KotlinGenerator(
  val document: Document,
  val shapeIndex: ShapeIndex,
  val typeRegistry: KotlinTypeRegistry,
  override val options: Options,
) : Generator(document.api, options) {

  open class Options(
    val defaultServicePackageName: String?,
    defaultProblemBaseUri: String,
    defaultMediaTypes: List<String>,
    serviceSuffix: String,
  ) : Generator.Options(
    defaultProblemBaseUri,
    defaultMediaTypes,
    serviceSuffix,
  )

  data class URIParameter(val name: String, val typeName: TypeName, val shape: Shape?, val defaultValue: DataNode?)

  protected val generationMode get() = typeRegistry.generationMode

  override fun generateServiceTypes() {

    val endPointGroups = api.endPoints.groupBy { it.root.findStringAnnotation(ServiceGroup, generationMode) }

    endPointGroups.map { (groupName, endPoints) ->

      val servicePrefix = groupName ?: api.findStringAnnotation(ServiceName, generationMode) ?: ""

      val servicePackageName =
        api.findStringAnnotation(KotlinPkg, generationMode)
          ?: options.defaultServicePackageName
          ?: genError("No service package specified, one must be specified via options or in each RAML unit")

      val serviceSimpleName = "${servicePrefix.kotlinTypeName}${options.serviceSuffix}"

      val serviceTypeName = ClassName.bestGuess("$servicePackageName.$serviceSimpleName")

      val serviceTypeBuilder = generateServiceType(serviceTypeName, endPoints)

      typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder)
    }
  }

  open fun generateServiceType(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val serviceTypeBuilder = processServiceBegin(serviceTypeName, endPoints)

    generateClientServiceMethods(serviceTypeName, serviceTypeBuilder, endPoints)

    return processServiceEnd(serviceTypeBuilder)
  }

  abstract fun processServiceBegin(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder

  abstract fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ): FunSpec.Builder

  abstract fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec

  abstract fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec

  abstract fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec

  abstract fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder,
  ): ParameterSpec

  abstract fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    problemTypes: Map<String, ProblemTypeDefinition>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    returnTypeName: TypeName,
  ): TypeName

  abstract fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ): FunSpec

  open fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {

    val companion = (typeBuilder.tags[TypeSpec.Builder::class] as TypeSpec.Builder).build()
    if (
      companion.superinterfaces.isNotEmpty() ||
      companion.enumConstants.isNotEmpty() ||
      companion.propertySpecs.isNotEmpty() ||
      companion.funSpecs.isNotEmpty() ||
      companion.typeSpecs.isNotEmpty() ||
      companion.initializerBlock.isNotEmpty()
    ) {
      typeBuilder.addType(companion)
    }

    return typeBuilder
  }

  private fun generateClientServiceMethods(
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    endPoints: List<EndPoint>,
  ) {

    val nameGenerator = NameGenerator.createDefaultGenerator()

    val problemTypes = findProblemTypes()

    for (endPoint in endPoints) {
      for (operation in endPoint.operations) {

        val operationName = nameGenerator.generate(endPoint, operation).kotlinIdentifierName

        var functionBuilder =
          FunSpec.builder(operationName)
            .returns(UNIT)

        functionBuilder.tag(NameAllocator::class, NameAllocator())

        functionBuilder = processResourceMethodStart(endPoint, operation, typeBuilder, functionBuilder)

        if (operation.description != null) {
          functionBuilder.addKdoc(operation.description + "\n")
        }

        generateClientServiceMethodUriParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodQueryParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodHeaderParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        val request = operation.request ?: operation.requests.firstOrNull()
        if (request != null) {

          request.payloads.firstOrNull()?.schema?.let { payloadSchema ->

            val requestBodyParameterTypeNameContext =
              KotlinResolutionContext(
                document,
                shapeIndex,
                typeName.nestedClass("${operation.kotlinTypeName}RequestBody"),
              )

            val requestBodyParameterTypeName =
              typeRegistry.resolveTypeName(payloadSchema, requestBodyParameterTypeNameContext)

            val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

            val requestBodyParameterBuilder =
              ParameterSpec.builder(
                functionBuilderNameAllocator.newName("body", payloadSchema),
                requestBodyParameterTypeName,
              )

            val requestBodyParameterSpec =
              processResourceMethodBodyParameter(
                endPoint,
                operation,
                payloadSchema,
                typeBuilder,
                functionBuilder,
                requestBodyParameterBuilder,
              )

            functionBuilder.addParameter(requestBodyParameterSpec)
          }
        }

        operation.successes.forEach { response ->

          val responseBodyType = response.payloads.firstOrNull()?.schema
          if (response.statusCode != "${NO_CONTENT.code}" && responseBodyType != null) {

            val responseBodyTypeNameContext =
              KotlinResolutionContext(
                document,
                shapeIndex,
                typeName.nestedClass("${operation.kotlinTypeName}ResponseBody"),
              )

            val responseBodyTypeName = typeRegistry.resolveTypeName(responseBodyType, responseBodyTypeNameContext)

            val processedResponseBodyTypeName =
              processReturnType(
                endPoint,
                operation,
                response,
                responseBodyType,
                problemTypes,
                typeBuilder,
                functionBuilder,
                responseBodyTypeName,
              )

            if (processedResponseBodyTypeName != UNIT) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }
          } else {

            val processedResponseBodyTypeName =
              processReturnType(endPoint, operation, response, null, problemTypes, typeBuilder, functionBuilder, UNIT)
            if (processedResponseBodyTypeName != UNIT) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }
          }
        }

        operation.failures.forEach { response ->

          val bodyType = response.payloads.firstOrNull()?.schema
          if (bodyType != null) {

            typeRegistry.resolveTypeName(bodyType, KotlinResolutionContext(document, shapeIndex, null))
          }
        }

        val responseProblemTypes =
          operation.findArrayAnnotation(Problems, null)?.let { problems ->

            val referencedProblemCodes = problems.mapNotNull { it.stringValue }
            val referencedProblemTypes =
              referencedProblemCodes
                .map { problemCode ->
                  val problemType =
                    problemTypes[problemCode] ?: genError("Unknown problem code referenced: $problemCode", operation)
                  problemCode to problemType
                }
                .toMap()

            referencedProblemTypes
              .map { (problemCode, problemTypeDefinition) ->
                problemTypeDefinition.type to typeRegistry.defineProblemType(
                  problemCode,
                  problemTypeDefinition,
                  shapeIndex,
                )
              }
              .toMap()
          } ?: emptyMap()

        val functionSpec =
          processResourceMethodEnd(endPoint, operation, responseProblemTypes, typeBuilder, functionBuilder)

        if (operation.findBoolAnnotation(Exclude, generationMode) == true) {
          continue
        }

        typeBuilder.addFunction(functionSpec)
      }
    }
  }

  private fun generateClientServiceMethodUriParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ) {

    endPoint.parameters.forEach { parameter ->

      val uriParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          shapeIndex,
          typeName.nestedClass("${operation.kotlinTypeName}${parameter.kotlinTypeName}UriParam"),
        )

      val uriParameterTypeName =
        typeRegistry.resolveTypeName(parameter.schema!!, uriParameterTypeNameContext)
          .run {
            if (parameter.required == false) {
              copy(nullable = true)
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder =
        ParameterSpec.builder(
          functionBuilderNameAllocator.newName(parameter.kotlinIdentifierName, parameter),
          uriParameterTypeName,
        )

      val defaultValue = parameter.schema?.defaultValue
      if (defaultValue != null) {
        uriParameterBuilder.defaultValue(defaultValue.kotlinConstant(uriParameterTypeName, parameter.schema))
      }

      val uriParameterSpec =
        processResourceMethodUriParameter(
          endPoint,
          operation,
          parameter,
          typeBuilder,
          functionBuilder,
          uriParameterBuilder,
        )

      functionBuilder.addParameter(uriParameterSpec)
    }
  }

  private fun generateClientServiceMethodQueryParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.queryParameters.forEach { parameter ->

      val queryParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          shapeIndex,
          typeName.nestedClass("${operation.kotlinTypeName}${parameter.kotlinTypeName}QueryParam"),
        )

      val queryParameterTypeName =
        typeRegistry.resolveTypeName(parameter.schema!!, queryParameterTypeNameContext)
          .run {
            if (parameter.required == false) {
              copy(nullable = true)
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val queryParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(parameter.kotlinIdentifierName, parameter),
        queryParameterTypeName,
      )

      val defaultValue = parameter.schema?.defaultValue
      if (defaultValue != null) {
        queryParameterBuilder.defaultValue(defaultValue.kotlinConstant(queryParameterTypeName, parameter.schema))
      }

      val queryParameterSpec =
        processResourceMethodQueryParameter(
          endPoint,
          operation,
          parameter,
          typeBuilder,
          functionBuilder,
          queryParameterBuilder,
        )

      functionBuilder.addParameter(queryParameterSpec)
    }
  }

  private fun generateClientServiceMethodHeaderParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.headers.forEach { header ->

      val headerParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          shapeIndex,
          typeName.nestedClass("${operation.kotlinTypeName}${header.kotlinTypeName}HeaderParam"),
        )

      val headerParameterTypeName =
        typeRegistry.resolveTypeName(header.schema!!, headerParameterTypeNameContext)
          .run {
            if (header.required == false) {
              copy(nullable = true)
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val headerParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(header.kotlinIdentifierName, header),
        headerParameterTypeName,
      )

      val defaultValue = header.schema?.defaultValue
      if (defaultValue != null) {
        headerParameterBuilder.defaultValue(defaultValue.kotlinConstant(headerParameterTypeName, header.schema))
      }

      val headerParameterSpec =
        processResourceMethodHeaderParameter(
          endPoint,
          operation,
          header,
          typeBuilder,
          functionBuilder,
          headerParameterBuilder,
        )

      functionBuilder.addParameter(headerParameterSpec)
    }
  }

  fun resolveTypeName(shape: Shape, suggestedTypeName: ClassName?): TypeName {
    return typeRegistry.resolveTypeName(shape, KotlinResolutionContext(document, shapeIndex, suggestedTypeName))
  }

  private fun findProblemTypes(): Map<String, ProblemTypeDefinition> {

    val problemBaseUriParams =
      document.api.findAnnotation(
        ProblemBaseUriParams,
        typeRegistry.generationMode,
      )?.objectValue ?: emptyMap()

    fun expand(template: String): URI {
      try {
        return URI(UriTemplate.expand(template, problemBaseUriParams))
      } catch (ignored: URISyntaxException) {
        genError(
          """
            Problem URI is not a valid URI; it cannot be a template.
            Use `problemBaseUri` and/or `problemBaseUriParams` to ensure it is valid.
          """.trimIndent(),
        )
      }
    }

    val baseUri = expand(document.api.servers.firstOrNull()?.url ?: options.defaultProblemBaseUri)

    var problemBaseUri =
      document.api.findAnnotation(
        ProblemBaseUri,
        typeRegistry.generationMode,
      )?.stringValue?.let { expand(it) } ?: baseUri
    if (!problemBaseUri.isAbsolute) {
      problemBaseUri = baseUri.resolve(problemBaseUri)
    }

    val problemDefLocations: Map<BaseUnit, CustomizableElement> =
      document.allUnits.filterIsInstance<CustomizableElement>().associateBy { it as BaseUnit } +
        document.allUnits.filterIsInstance<EncodesModel>().associate { it as BaseUnit to it.encodes }

    return problemDefLocations
      .mapNotNull { (unit, element) ->
        val problemAnn = element.findAnnotation(ProblemTypes, generationMode) as? ObjectNode ?: return@mapNotNull null
        problemAnn
          .properties()
          ?.mapValues { ProblemTypeDefinition(it.key, it.value as ObjectNode, problemBaseUri, unit, problemAnn) }
          ?.entries
      }
      .flatten()
      .associate { it.key to it.value }
      .toMap()
  }

  fun getBaseURIInfo(): Pair<String, List<URIParameter>>? {

    val server = document.api.servers.firstOrNull() ?: return null

    val documentPackageName =
      document.api.findStringAnnotation(KotlinModelPkg, generationMode)
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in each RAML unit")

    val parameters =
      server.variables
        .mapIndexed { idx, variable ->

          val name = variable.name ?: "uriParameter$idx"

          val variableTypeName =
            variable.schema?.let {
              val suggestedName = "${variable.name?.kotlinTypeName}URIParameter"
              resolveTypeName(it, ClassName(documentPackageName, suggestedName))
            } ?: STRING

          val defaultValue =
            if (variable.name == "version") {
              variable.schema?.defaultValue ?: ScalarNode(document.api.version ?: "1", DataTypes.String())
            } else {
              variable.schema?.defaultValue
            }

          URIParameter(name, variableTypeName, variable.schema, defaultValue)
        }

    return server.url to parameters
  }

  fun addNullifyMethod(
    operation: Operation,
    function: FunSpec,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: TypeSpec.Builder,
    copyAnnotaitons: Boolean = false,
  ) {

    val nullifyAnn = operation.findArrayAnnotation(Nullify, null)
    if (nullifyAnn?.isNotEmpty() == true) {

      val (nullifyProblemTypeCodes, nullifyStatuses) = groupNullifyIntoStatusesAndProblems(nullifyAnn)

      val nullifyProblemTypeNames =
        problemTypes
          .filter { (key) -> nullifyProblemTypeCodes.any { key.path.endsWith(it) } }
          .map { it.value }
          .toSet()

      val nullifyFunctionCodeBlock =
        if (supportedReactiveReturnTypes.contains(function.returnType.rawType.copy(nullable = false))) {
          createReactiveNullifyMethodCode(function, nullifyStatuses, nullifyProblemTypeNames)
        } else {
          createNullifyMethodCode(function, nullifyStatuses, nullifyProblemTypeNames)
        }

      val returnType = function.returnType.copy(nullable = false)
      val nullableReturnType =
        when {
          returnType.rawType == RESULT_RESPONSE ->
            returnType.copy(nullable = true)

          returnType is ParameterizedTypeName && returnType.typeArguments.size == 1 -> {

            val elementType = returnType.typeArguments[0]

            val nullableElementType =
              if (rxReturnTypes.contains(returnType.rawType)) {
                Optional::class.asTypeName().parameterizedBy(elementType)
              } else {
                elementType.copy(nullable = true)
              }

            returnType.rawType.parameterizedBy(nullableElementType)
          }

          else -> returnType.copy(nullable = true)
        }

      typeBuilder.addFunction(
        FunSpec.builder("${function.name}OrNull")
          .addModifiers(function.modifiers.filter { it != KModifier.ABSTRACT })
          .addTypeVariables(function.typeVariables)
          .addParameters(
            function.parameters.map {
              // Remove annotations
              val paramBuilder = it.toBuilder()
              paramBuilder.annotations.clear()
              paramBuilder.build()
            },
          )
          .apply {
            if (copyAnnotaitons) {
              addAnnotations(function.annotations)
            }
            nullableReturnType.let {
              returns(it)
            }
          }
          .addKdoc(function.kdoc)
          .addCode(nullifyFunctionCodeBlock)
          .build(),
      )
    }
  }

  private fun createNullifyMethodCode(
    function: FunSpec,
    statuses: Set<Int>,
    problemTypeNames: Set<TypeName>,
  ): CodeBlock {

    val codeBuilder =
      CodeBlock.builder()
        .beginControlFlow("return try")
        .addStatement("%L(%L)", function.name, function.parameters.joinToString { it.name })

    problemTypeNames.forEach {
      codeBuilder
        .nextControlFlow("catch(x: %T)", it)
        .addStatement("null")
    }

    if (statuses.isNotEmpty()) {
      codeBuilder.nextControlFlow("catch(x: %T)", THROWABLE_PROBLEM)

      if (statuses.size == 1) {
        codeBuilder
          .beginControlFlow("if (x.status?.statusCode == %L)", statuses.first())
          .addStatement("null")
          .nextControlFlow("else")
          .addStatement("throw x")
          .endControlFlow()
      } else {
        codeBuilder
          .add("when (x.status?.statusCode) {").indent().add("\n")
          .add("%L -> null\n", statuses.joinToString(", "))
          .add("else -> throw x").unindent().add("\n")
          .add("}").add("\n")
      }
    }

    codeBuilder.endControlFlow()

    return codeBuilder.build()
  }

  private val rxReturnTypes: List<TypeName> = listOf(
    RXSINGLE2,
    RXSINGLE3,
    RXOBSERVABLE2,
    RXOBSERVABLE3,
  )

  private val supportedReactiveReturnTypes = listOf(
    CompletionStage::class.asTypeName(),
    UNI,
  ) + rxReturnTypes

  private fun createReactiveNullifyMethodCode(
    function: FunSpec,
    statuses: Set<Int>,
    problemTypeNames: Set<TypeName>,
  ): CodeBlock {

    val codeBuilder = CodeBlock.builder()

    codeBuilder.add("return %N(%L)", function.name, function.parameters.joinToString { it.name })
      .indent().add("\n")

    val nullLiteral =
      when (function.returnType.rawType.copy(nullable = false)) {

        CompletionStage::class.asTypeName() -> {
          codeBuilder.add(".exceptionally { x ->").indent().add("\n")
          "null"
        }

        UNI -> {
          codeBuilder.add(".onFailure().recoverWithItem { x ->").indent().add("\n")
          "null"
        }

        RXSINGLE3, RXOBSERVABLE3, RXSINGLE2, RXOBSERVABLE2 -> {
          codeBuilder.add(".map { %T.of(it) }", Optional::class.asTypeName())
            .add("\n.onErrorReturn { x ->").indent().add("\n")

          "Optional.empty()"
        }

        else -> throw IllegalArgumentException("Unsupported reactive return type for nullify")
      }

    codeBuilder.add("when {").indent().add("\n")

    problemTypeNames.forEach {
      codeBuilder.addStatement("x is %T -> %L", it, nullLiteral)
    }

    if (statuses.isNotEmpty()) {
      codeBuilder.addStatement(
        "x is %T && ${if (statuses.size == 1) "%L" else "(%L)"} -> %L",
        THROWABLE_PROBLEM,
        statuses.joinToString(" || ") { "x.status?.statusCode == $it" },
        nullLiteral,
      )
    }

    codeBuilder
      .add("else -> throw x").unindent().add("\n")
      .add("}").unindent().add("\n")
      .add("}").unindent().add("\n")

    return codeBuilder.build()
  }
}
