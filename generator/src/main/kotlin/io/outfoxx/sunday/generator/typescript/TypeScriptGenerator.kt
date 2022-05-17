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
import io.outfoxx.sunday.generator.APIAnnotationName.TypeScriptModule
import io.outfoxx.sunday.generator.Generator
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.common.NameGenerator
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.typescript.utils.URL_TEMPLATE
import io.outfoxx.sunday.generator.typescript.utils.typeScriptConstant
import io.outfoxx.sunday.generator.typescript.utils.typeScriptIdentifierName
import io.outfoxx.sunday.generator.typescript.utils.typeScriptTypeName
import io.outfoxx.sunday.generator.typescript.utils.undefinable
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
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
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.NameAllocator
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import io.outfoxx.typescriptpoet.tag
import java.net.URI
import java.net.URISyntaxException
import javax.ws.rs.core.Response.Status.NO_CONTENT

/**
 * Generator for TypeScript language framework targets
 */
abstract class TypeScriptGenerator(
  val document: Document,
  val typeRegistry: TypeScriptTypeRegistry,
  override val options: Options,
) : Generator(document.api, options) {

  data class URIParameter(val name: String, val typeName: TypeName, val shape: Shape?, val defaultValue: DataNode?)

  override fun generateServiceTypes() {

    val endPointGroups = api.endPoints.groupBy { it.root.findStringAnnotation(ServiceGroup, null) }

    endPointGroups.map { (groupName, endPoints) ->

      val servicePrefix = groupName ?: api.findStringAnnotation(ServiceName, null) ?: ""

      val serviceSimpleName = "${servicePrefix.typeScriptTypeName}${options.serviceSuffix}"

      val modulePath = modulePathOf(document)?.let { "!$it/" } ?: "!"

      val serviceTypeName =
        TypeName.namedImport(serviceSimpleName, "$modulePath${serviceSimpleName.camelCaseToKebabCase()}")

      val serviceTypeBuilder = generateServiceType(serviceTypeName, endPoints)

      typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder)
    }
  }

  open fun generateServiceType(serviceTypeName: TypeName.Standard, endPoints: List<EndPoint>): ClassSpec.Builder {

    var serviceTypeBuilder =
      ClassSpec.builder(serviceTypeName)
        .addModifiers(Modifier.EXPORT)
        .tag(CodeBlock.builder())

    // Add baseUrl function
    getBaseURIInfo()?.let { (baseURL, baseURLParameters) ->

      serviceTypeBuilder.addFunction(
        FunctionSpec.builder("baseURL")
          .addModifiers(Modifier.STATIC)
          .returns(URL_TEMPLATE)
          .apply {
            baseURLParameters.forEach { param ->
              addParameter(param.name, param.typeName, optional = param.defaultValue != null)
            }
          }
          .addCode("return new URLTemplate(%>\n")
          .addCode("%S,\n{", baseURL)
          .apply {
            baseURLParameters.forEachIndexed { idx, param ->

              val defaultValue = param.defaultValue
              if (defaultValue != null) {
                addCode("%L: %L ?? ", param.name, param.name)
                addCode(defaultValue.typeScriptConstant(param.typeName, param.shape?.resolve))
              } else {
                addCode("%L", param.name)
              }

              if (idx < baseURLParameters.size - 1) {
                addCode(", ")
              }
            }
          }
          .addCode("}%<\n);\n")
          .build()
      )
    }

    serviceTypeBuilder = processServiceBegin(endPoints, serviceTypeBuilder)

    generateClientServiceMethods(serviceTypeName, serviceTypeBuilder, endPoints)

    return processServiceEnd(serviceTypeBuilder)
  }

  abstract fun processServiceBegin(endPoints: List<EndPoint>, typeBuilder: ClassSpec.Builder): ClassSpec.Builder

  abstract fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec.Builder

  abstract fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    problemTypes: Map<String, ProblemTypeDefinition>,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder,
    returnTypeName: TypeName
  ): TypeName

  abstract fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<URI, TypeName>,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ): FunctionSpec

  open fun processServiceEnd(typeBuilder: ClassSpec.Builder): ClassSpec.Builder {
    return typeBuilder
  }

  private fun generateClientServiceMethods(
    typeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
    endPoints: List<EndPoint>
  ) {

    val namedGenerator = NameGenerator.createDefaultGenerator()

    val problemTypes = findProblemTypes()

    for (endPoint in endPoints) {
      for (operation in endPoint.operations) {

        val operationName = namedGenerator.generate(endPoint, operation).typeScriptIdentifierName

        var functionBuilder =
          FunctionSpec.builder(operationName)
            .addModifiers(Modifier.ABSTRACT)

        functionBuilder.tag(NameAllocator())

        functionBuilder = processResourceMethodStart(endPoint, operation, typeBuilder, functionBuilder)

        if (operation.description != null) {
          functionBuilder.addTSDoc(operation.description + "\n")
        }

        generateClientServiceMethodUriParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodQueryParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        generateClientServiceMethodHeaderParameters(endPoint, operation, typeName, typeBuilder, functionBuilder)

        val request = operation.request ?: operation.requests.firstOrNull()
        if (request != null) {

          request.payloads.firstOrNull()?.schema?.let { payloadSchema ->

            val requestBodyParameterTypeName =
              resolveTypeName(payloadSchema, typeName.nested("${operation.typeScriptTypeName}RequestBody"))

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

            val responseBodyTypeName =
              resolveTypeName(responseBodyType, typeName.nested("${operation.typeScriptTypeName}ResponseBody"))

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

            resolveTypeName(bodyType, typeName.nested("${operation.typeScriptTypeName}FailureResponseBody"))
          }
        }

        val responseProblemTypes =
          operation.findArrayAnnotation(Problems, null)?.let { problems ->

            val referencedProblemCodes = problems.mapNotNull { it.stringValue }
            val referencedProblemTypes =
              referencedProblemCodes
                .map { problemCode ->
                  val problemType = problemTypes[problemCode] ?: genError("Unknown problem code referenced: $problemCode", operation)
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
    typeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {

    endPoint.parameters.forEach { parameter ->

      val uriParameterSuggestedTypeName =
        typeName.nested("${operation.typeScriptTypeName}${parameter.typeScriptTypeName}UriParam")

      val uriParameterTypeName = resolveTypeName(parameter.schema!!, uriParameterSuggestedTypeName)
        .run {
          if (parameter.schema?.defaultValue != null || parameter.required == false) {
            undefinable
          } else {
            this
          }
        }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder =
        ParameterSpec.builder(
          functionBuilderNameAllocator.newName(parameter.typeScriptIdentifierName, parameter),
          uriParameterTypeName
        )

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
    typeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.queryParameters.forEach { parameter ->

      val queryParameterSuggestedTypeName =
        typeName.nested("${operation.typeScriptTypeName}${parameter.typeScriptTypeName}QueryParam")

      val queryParameterTypeName = resolveTypeName(parameter.schema!!, queryParameterSuggestedTypeName)
        .run {
          if (parameter.schema?.defaultValue != null || parameter.required == false) {
            undefinable
          } else {
            this
          }
        }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(parameter.typeScriptIdentifierName, parameter),
        queryParameterTypeName
      )

      val uriParameterSpec =
        processResourceMethodQueryParameter(
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

  private fun generateClientServiceMethodHeaderParameters(
    endPoint: EndPoint,
    operation: Operation,
    typeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
    functionBuilder: FunctionSpec.Builder
  ) {
    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.headers.forEach { header ->

      val headerParameterSuggestedTypeName =
        typeName.nested("${operation.typeScriptTypeName}${header.typeScriptTypeName}HeaderParam")

      val headerParameterTypeName = resolveTypeName(header.schema!!, headerParameterSuggestedTypeName)
        .run {
          if (header.schema?.defaultValue != null || header.required == false) {
            undefinable
          } else {
            this
          }
        }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val headerParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(header.typeScriptIdentifierName, header),
        headerParameterTypeName
      )

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

  private fun resolveTypeName(shape: Shape, suggestedTypeName: TypeName.Standard): TypeName {
    return typeRegistry.resolveTypeName(shape, TypeScriptResolutionContext(document, suggestedTypeName))
  }

  private fun findProblemTypes(): Map<String, ProblemTypeDefinition> {

    val problemBaseUriParams =
      document.api.findAnnotation(ProblemBaseUriParams, null)?.objectValue ?: emptyMap()

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

    val problemBaseUri =
      document.api.findAnnotation(
        ProblemBaseUri,
        null
      )?.stringValue?.let { baseUri.resolve(expand(it)) } ?: baseUri

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

  private fun modulePathOf(unit: BaseUnit?): String? =
    (unit as? CustomizableElement)?.findStringAnnotation(TypeScriptModule, null)
      ?: (unit as? EncodesModel)?.encodes?.findStringAnnotation(TypeScriptModule, null)

  private fun getBaseURIInfo(): Pair<String, List<URIParameter>>? {

    val server = document.api.servers.firstOrNull() ?: return null

    val parameters =
      server.variables
        .mapIndexed { idx, variable ->

          val name = variable.name ?: "uriParameter$idx"

          val variableTypeName =
            variable.schema?.let {
              val suggestedName = variable.name?.typeScriptTypeName ?: "URIParameter$idx"
              val suggestedModule =
                variable.name?.typeScriptTypeName?.camelCaseToKebabCase() ?: "uri-parameter-idx$idx"
              resolveTypeName(it, TypeName.namedImport(suggestedName, "!$suggestedModule"))
            } ?: TypeName.STRING

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
