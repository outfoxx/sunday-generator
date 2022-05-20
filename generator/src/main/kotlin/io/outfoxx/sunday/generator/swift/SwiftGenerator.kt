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

import amf.client.model.document.BaseUnit
import amf.client.model.document.Document
import amf.client.model.document.EncodesModel
import amf.client.model.domain.CustomizableElement
import amf.client.model.domain.DataNode
import amf.client.model.domain.EndPoint
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import amf.core.model.DataType
import com.damnhandy.uri.template.UriTemplate
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUri
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUriParams
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemTypes
import io.outfoxx.sunday.generator.APIAnnotationName.Problems
import io.outfoxx.sunday.generator.APIAnnotationName.ServiceGroup
import io.outfoxx.sunday.generator.APIAnnotationName.ServiceName
import io.outfoxx.sunday.generator.APIAnnotationName.SwiftModelModule
import io.outfoxx.sunday.generator.APIAnnotationName.SwiftModule
import io.outfoxx.sunday.generator.Generator
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.NameGenerator
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.swift.utils.swiftConstant
import io.outfoxx.sunday.generator.swift.utils.swiftIdentifierName
import io.outfoxx.sunday.generator.swift.utils.swiftTypeName
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.description
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.failures
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
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
import io.outfoxx.sunday.generator.utils.resolve
import io.outfoxx.sunday.generator.utils.root
import io.outfoxx.sunday.generator.utils.schema
import io.outfoxx.sunday.generator.utils.servers
import io.outfoxx.sunday.generator.utils.statusCode
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.successes
import io.outfoxx.sunday.generator.utils.url
import io.outfoxx.sunday.generator.utils.variables
import io.outfoxx.sunday.generator.utils.version
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.NameAllocator
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.VOID
import java.net.URI
import java.net.URISyntaxException
import javax.ws.rs.core.Response.Status.NO_CONTENT

/**
 * Generator for Swift language framework targets
 */
abstract class SwiftGenerator(
  val document: Document,
  val typeRegistry: SwiftTypeRegistry,
  override val options: Options,
) : Generator(document.api, options) {

  data class URIParameter(val name: String, val typeName: TypeName, val shape: Shape?, val defaultValue: DataNode?)

  override fun generateServiceTypes() {

    val endPointGroups =
      api.endPoints.groupBy { it.root.findStringAnnotation(ServiceGroup, null) }

    endPointGroups.map { (groupName, endPoints) ->

      val servicePrefix = groupName ?: api.findStringAnnotation(ServiceName, null) ?: ""

      val serviceSimpleName = "${servicePrefix.swiftTypeName}${options.serviceSuffix}"

      val serviceModuleName =
        api.findStringAnnotation(SwiftModule, null)
          ?: ""

      val serviceTypeName = DeclaredTypeName.typeName("$serviceModuleName.$serviceSimpleName")

      val serviceTypeBuilder = generateServiceType(serviceTypeName, endPoints)

      typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder)
    }
  }

  open fun generateServiceType(serviceTypeName: DeclaredTypeName, endPoints: List<EndPoint>): TypeSpec.Builder {

    val serviceTypeBuilder = processServiceBegin(serviceTypeName, endPoints)

    generateClientServiceMethods(serviceTypeName, serviceTypeBuilder, endPoints)

    return processServiceEnd(serviceTypeBuilder)
  }

  abstract fun processServiceBegin(serviceTypeName: DeclaredTypeName, endPoints: List<EndPoint>): TypeSpec.Builder

  abstract fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec.Builder

  abstract fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    problemTypes: Map<String, ProblemTypeDefinition>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    returnTypeName: TypeName
  ): TypeName

  abstract fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, DeclaredTypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec

  open fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {
    return typeBuilder
  }

  private fun generateClientServiceMethods(
    typeName: DeclaredTypeName,
    typeBuilder: TypeSpec.Builder,
    endPoints: List<EndPoint>
  ) {

    val nameGenerator = NameGenerator.createDefaultGenerator()

    val problemTypes = findProblemTypes()

    for (endPoint in endPoints) {
      for (operation in endPoint.operations) {

        val operationName = nameGenerator.generate(endPoint, operation)

        var functionBuilder =
          FunctionSpec.builder(operationName)
            .addModifiers(PUBLIC)
            .returns(VOID)

        functionBuilder.tag(NameAllocator::class, NameAllocator())

        functionBuilder = processResourceMethodStart(endPoint, operation, typeBuilder, functionBuilder)

        if (operation.description != null) {
          functionBuilder.addDoc(operation.description + "\n")
        }

        generateClientServiceMethodUriParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodQueryParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodHeaderParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        val request = operation.request ?: operation.requests.firstOrNull()
        if (request != null) {

          request.payloads.firstOrNull()?.schema?.let { payloadSchema ->

            val requestBodyParameterTypeNameContext =
              SwiftResolutionContext(
                document,
                typeName.nestedType("${operation.swiftTypeName}RequestBody"),
              )

            val requestBodyParameterTypeName =
              typeRegistry.resolveTypeName(payloadSchema, requestBodyParameterTypeNameContext)

            val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

            val requestBodyParameterBuilder =
              ParameterSpec.builder(
                functionBuilderNameAllocator.newName("body", payloadSchema),
                requestBodyParameterTypeName
              )

            val requestBodyParameterSpec =
              processResourceMethodBodyParameter(
                endPoint,
                operation,
                payloadSchema.resolve,
                typeBuilder,
                functionBuilder,
                requestBodyParameterBuilder
              )

            functionBuilder.addParameter(requestBodyParameterSpec)
          }
        }

        operation.successes.forEach { response ->

          val responseBodyType = response.payloads.firstOrNull()?.schema
          if (response.statusCode != NO_CONTENT.statusCode.toString() && responseBodyType != null) {

            val responseBodyTypeNameContext =
              SwiftResolutionContext(
                document,
                typeName.nestedType("${operation.swiftTypeName}ResponseBody"),
              )

            val responseBodyTypeName = typeRegistry.resolveTypeName(responseBodyType, responseBodyTypeNameContext)

            val processedResponseBodyTypeName =
              processReturnType(
                endPoint,
                operation,
                response,
                responseBodyType.resolve,
                problemTypes,
                typeBuilder,
                functionBuilder,
                responseBodyTypeName
              )

            if (processedResponseBodyTypeName != VOID) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }
          } else {

            val processedResponseBodyTypeName =
              processReturnType(endPoint, operation, response, null, problemTypes, typeBuilder, functionBuilder, VOID)
            if (processedResponseBodyTypeName != VOID) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }
          }
        }

        operation.failures.forEach { response ->

          val bodyType = response.payloads.firstOrNull()?.schema
          if (bodyType != null) {

            typeRegistry.resolveTypeName(bodyType, SwiftResolutionContext(document, null))
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
                problemTypeDefinition.type to typeRegistry.defineProblemType(problemCode, problemTypeDefinition)
              }
              .toMap()
          } ?: emptyMap()

        val functionSpec =
          processResourceMethodEnd(endPoint, operation, responseProblemTypes, typeBuilder, functionBuilder)

        typeBuilder.addFunction(functionSpec)
      }
    }
  }

  private fun generateClientServiceMethodUriParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: DeclaredTypeName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {

    endPoint.parameters.forEach { parameter ->

      val uriParameterTypeNameContext =
        SwiftResolutionContext(
          document,
          typeName.nestedType("${operation.swiftTypeName}${parameter.swiftTypeName}UriParam"),
        )

      val uriParameterTypeName =
        typeRegistry.resolveTypeName(parameter.schema!!, uriParameterTypeNameContext)
          .run {
            if (parameter.required == false) {
              makeOptional()
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder =
        ParameterSpec.builder(
          functionBuilderNameAllocator.newName(parameter.swiftIdentifierName, parameter),
          uriParameterTypeName
        )

      val defaultValue = parameter.schema?.defaultValue
      if (defaultValue != null) {
        uriParameterBuilder.defaultValue(defaultValue.swiftConstant(uriParameterTypeName, parameter.schema))
      }

      val uriParameterSpec =
        processResourceMethodUriParameter(
          endPoint,
          operation,
          parameter,
          typeBuilder,
          functionBuilder,
          uriParameterBuilder
        )

      functionBuilder.addParameter(uriParameterSpec)
    }
  }

  private fun generateClientServiceMethodQueryParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: DeclaredTypeName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.queryParameters.forEach { parameter ->

      val queryParameterTypeNameContext =
        SwiftResolutionContext(
          document,
          typeName.nestedType("${operation.swiftTypeName}${parameter.swiftTypeName}QueryParam")
        )

      val queryParameterTypeName =
        typeRegistry.resolveTypeName(parameter.schema!!, queryParameterTypeNameContext)
          .run {
            if (parameter.required == false) {
              makeOptional()
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val queryParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(parameter.swiftIdentifierName, parameter),
        queryParameterTypeName
      )

      val defaultValue = parameter.schema?.defaultValue
      if (defaultValue != null) {
        queryParameterBuilder.defaultValue(defaultValue.swiftConstant(queryParameterTypeName, parameter.schema))
      }

      val queryParameterSpec =
        processResourceMethodQueryParameter(
          endPoint,
          operation,
          parameter,
          typeBuilder,
          functionBuilder,
          queryParameterBuilder
        )

      functionBuilder.addParameter(queryParameterSpec)
    }
  }

  private fun generateClientServiceMethodHeaderParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: DeclaredTypeName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.headers.forEach { header ->

      val headerParameterTypeNameContext =
        SwiftResolutionContext(
          document,
          typeName.nestedType("${operation.swiftTypeName}${header.swiftTypeName}HeaderParam")
        )

      val headerParameterTypeName =
        typeRegistry.resolveTypeName(header.schema!!, headerParameterTypeNameContext)
          .run {
            if (header.required == false) {
              makeOptional()
            } else {
              this
            }
          }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val headerParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(header.swiftIdentifierName, header),
        headerParameterTypeName
      )

      val defaultValue = header.schema?.defaultValue
      if (defaultValue != null) {
        headerParameterBuilder.defaultValue(defaultValue.swiftConstant(headerParameterTypeName, header.schema))
      }

      val headerParameterSpec =
        processResourceMethodHeaderParameter(
          endPoint,
          operation,
          header,
          typeBuilder,
          functionBuilder,
          headerParameterBuilder
        )

      functionBuilder.addParameter(headerParameterSpec)
    }
  }

  fun resolveTypeName(shape: Shape, suggestedTypeName: DeclaredTypeName?): TypeName {
    return typeRegistry.resolveTypeName(shape, SwiftResolutionContext(document, suggestedTypeName))
  }

  private fun findProblemTypes(): Map<String, ProblemTypeDefinition> {

    val problemBaseUriParams =
      document.api.findAnnotation(
        ProblemBaseUriParams,
        null
      )?.objectValue ?: emptyMap()

    fun expand(template: String): URI {
      try {
        return URI(UriTemplate.expand(template, problemBaseUriParams))
      } catch (e: URISyntaxException) {
        genError(
          """
            Problem URI is not a valid URI; it cannot be a template.
            Use `problemBaseUri` and/or `problemBaseUriParams` to ensure it is valid.
          """.trimIndent()
        )
      }
    }

    val baseUri = expand(document.api.servers.firstOrNull()?.url ?: options.defaultProblemBaseUri)

    var problemBaseUri =
      document.api.findAnnotation(
        ProblemBaseUri,
        null
      )?.stringValue?.let { expand(it) } ?: baseUri
    if (!problemBaseUri.isAbsolute) {
      problemBaseUri = baseUri.resolve(problemBaseUri)
    }

    val problemDefLocations: Map<BaseUnit, CustomizableElement> =
      document.allUnits.filterIsInstance<CustomizableElement>().associateBy { it as BaseUnit } +
        document.allUnits.filterIsInstance<EncodesModel>().associate { it as BaseUnit to it.encodes }

    return problemDefLocations
      .mapNotNull { (unit, element) ->
        val problemAnn = element.findAnnotation(ProblemTypes, null) as? ObjectNode ?: return@mapNotNull null
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

    val parameters =
      server.variables
        .mapIndexed { idx, variable ->

          val name = variable.name ?: "uriParameter$idx"

          val variableTypeName =
            variable.schema?.let { shape ->
              val suggestedModuleName = api.findStringAnnotation(SwiftModelModule, null) ?: ""
              val suggestedName = variable.name?.let { "${it}URIParameter".kotlinTypeName } ?: name.kotlinTypeName
              val suggestedTypeName = DeclaredTypeName.typeName("$suggestedModuleName.$suggestedName")
              resolveTypeName(shape, suggestedTypeName)
            } ?: STRING

          val defaultValue =
            if (variable.name == "version")
              variable.schema?.defaultValue ?: ScalarNode(document.api.version ?: "1", DataType.String())
            else
              variable.schema?.defaultValue

          URIParameter(name, variableTypeName, variable.schema, defaultValue)
        }

    return server.url to parameters
  }
}
