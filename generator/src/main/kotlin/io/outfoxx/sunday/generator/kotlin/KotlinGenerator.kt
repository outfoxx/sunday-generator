package io.outfoxx.sunday.generator.kotlin

import amf.client.model.document.BaseUnit
import amf.client.model.document.Document
import amf.client.model.document.EncodesModel
import amf.client.model.domain.ArrayNode
import amf.client.model.domain.CustomizableElement
import amf.client.model.domain.EndPoint
import amf.client.model.domain.ObjectNode
import amf.client.model.domain.Operation
import amf.client.model.domain.Parameter
import amf.client.model.domain.Response
import amf.client.model.domain.ScalarNode
import amf.client.model.domain.Shape
import com.damnhandy.uri.template.UriTemplate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import io.outfoxx.sunday.generator.APIAnnotationName.KotlinPkg
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUri
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemBaseUriParams
import io.outfoxx.sunday.generator.APIAnnotationName.ProblemTypes
import io.outfoxx.sunday.generator.APIAnnotationName.Problems
import io.outfoxx.sunday.generator.APIAnnotationName.ServiceGroup
import io.outfoxx.sunday.generator.Generator
import io.outfoxx.sunday.generator.ProblemTypeDefinition
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.description
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.failures
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.objectValue
import io.outfoxx.sunday.generator.utils.operations
import io.outfoxx.sunday.generator.utils.parameters
import io.outfoxx.sunday.generator.utils.path
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
import io.outfoxx.sunday.generator.utils.value
import io.outfoxx.sunday.generator.utils.values
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import javax.ws.rs.core.Response.Status.NO_CONTENT

/**
 * Generator for Kotlin language framework targets
 */
abstract class KotlinGenerator(
  val document: Document,
  val typeRegistry: KotlinTypeRegistry,
  val defaultServicePackageName: String,
  val defaultProblemBaseUri: String,
  defaultMediaTypes: List<String>,
) : Generator(document.api, defaultMediaTypes) {

  protected val generationMode get() = typeRegistry.generationMode

  override fun generateFiles(outputDirectory: Path) {

    generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    builtTypes.entries
      .filter { it.key.topLevelClassName() == it.key }
      .map { FileSpec.get(it.key.packageName, it.value) }
      .forEach { it.writeTo(outputDirectory) }
  }

  override fun generateServiceTypes() {

    val endPointGroups =
      api.endPoints.groupBy { it.root.findStringAnnotation(ServiceGroup, generationMode) }

    endPointGroups.map { (groupName, endPoints) ->

      val servicePackageName =
        api.findStringAnnotation(KotlinPkg, generationMode)
          ?: defaultServicePackageName

      val serviceSimpleName = "${groupName?.capitalize() ?: ""}API"

      val serviceTypeName = ClassName.bestGuess("$servicePackageName.$serviceSimpleName")

      val serviceTypeBuilder = generateServiceType(serviceTypeName, endPoints)

      typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder)
    }

  }

  open fun generateServiceType(serviceTypeName: ClassName, endPoints: List<EndPoint>): TypeSpec.Builder {

    var serviceTypeBuilder = TypeSpec.interfaceBuilder(serviceTypeName)

    serviceTypeBuilder = processServiceBegin(endPoints, serviceTypeBuilder)

    generateClientServiceMethods(serviceTypeName, serviceTypeBuilder, endPoints)

    return processServiceEnd(serviceTypeBuilder)
  }

  abstract fun processServiceBegin(endPoints: List<EndPoint>, typeBuilder: TypeSpec.Builder): TypeSpec.Builder

  abstract fun processResourceMethodStart(
    endPoint: EndPoint,
    operation: Operation,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec.Builder

  abstract fun processResourceMethodUriParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodQueryParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodHeaderParameter(
    endPoint: EndPoint,
    operation: Operation,
    parameter: Parameter,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processResourceMethodBodyParameter(
    endPoint: EndPoint,
    operation: Operation,
    payloadSchema: Shape,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    parameterBuilder: ParameterSpec.Builder
  ): ParameterSpec

  abstract fun processReturnType(
    endPoint: EndPoint,
    operation: Operation,
    response: Response,
    body: Shape?,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder,
    returnTypeName: TypeName
  ): TypeName

  abstract fun processResourceMethodEnd(
    endPoint: EndPoint,
    operation: Operation,
    problemTypes: Map<String, TypeName>,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ): FunSpec

  open fun processServiceEnd(typeBuilder: TypeSpec.Builder): TypeSpec.Builder {
    return typeBuilder
  }

  private fun generateClientServiceMethods(
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    endPoints: List<EndPoint>
  ) {

    val problemTypes = findProblemTypes()

    for (endPoint in endPoints) {
      for (operation in endPoint.operations) {

        val operationName = operation.kotlinIdentifierName
        if (operationName == null) {
          System.err.println("Method ${operation.method} in endpoint with path ${endPoint.path} has no name")
          continue
        }

        var functionBuilder =
          FunSpec.builder(operationName)
            .addModifiers(KModifier.ABSTRACT)

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
                typeName.nestedClass("${operation.kotlinTypeName}RequestBody"),
                null
              )

            val requestBodyParameterTypeName =
              typeRegistry.resolveTypeName(payloadSchema, requestBodyParameterTypeNameContext)

            val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

            val requestBodyParameterBuilder =
              ParameterSpec.builder(
                functionBuilderNameAllocator.newName("body"),
                requestBodyParameterTypeName
              )

            val requestBodyParameterSpec =
              processResourceMethodBodyParameter(
                endPoint,
                operation,
                payloadSchema,
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
              KotlinResolutionContext(
                document,
                typeName.nestedClass("${operation.kotlinTypeName}ResponseBody"),
                null
              )

            val responseBodyTypeName = typeRegistry.resolveTypeName(responseBodyType, responseBodyTypeNameContext)

            val processedResponseBodyTypeName =
              processReturnType(
                endPoint,
                operation,
                response,
                responseBodyType,
                typeBuilder,
                functionBuilder,
                responseBodyTypeName
              )

            if (processedResponseBodyTypeName != UNIT) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }

          } else {

            val processedResponseBodyTypeName =
              processReturnType(endPoint, operation, response, null, typeBuilder, functionBuilder, UNIT)
            if (processedResponseBodyTypeName != UNIT) {
              functionBuilder.returns(processedResponseBodyTypeName)
            }
          }
        }

        operation.failures.forEach { response ->

          val bodyType = response.payloads.firstOrNull()?.schema
          if (bodyType != null) {

            typeRegistry.resolveTypeName(bodyType, KotlinResolutionContext(document, null, null))
          }

        }

        val responseProblemTypes =
          operation.findArrayAnnotation(Problems, null)?.let { problems ->

            val referencedProblemCodes = problems.mapNotNull { it.stringValue }
            val referencedProblemTypes =
              referencedProblemCodes
                .map { problemCode ->
                  val problemType = problemTypes[problemCode] ?: error("Unknown problem code referenced: $problemCode")
                  problemCode to problemType
                }
                .toMap()

            referencedProblemTypes
              .mapValues { (problemCode, problemTypeDefinition) ->
                typeRegistry.defineProblemType(problemCode, problemTypeDefinition)
              }
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
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ) {

    endPoint.parameters.forEach { parameter ->

      val uriParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          typeName.nestedClass("${operation.kotlinTypeName}${parameter.kotlinTypeName}UriParam"),
          null
        )

      var uriParameterTypeName = typeRegistry.resolveTypeName(parameter.schema!!, uriParameterTypeNameContext)

      if (parameter.required != true) {
        uriParameterTypeName = uriParameterTypeName.copy(nullable = true)
      } else if (parameter.schema?.defaultValue != null) {
        uriParameterTypeName = uriParameterTypeName.copy(nullable = false)
      }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder =
        ParameterSpec.builder(
          functionBuilderNameAllocator.newName(parameter.kotlinIdentifierName),
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
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.queryParameters.forEach { parameter ->

      val queryParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          typeName.nestedClass("${operation.kotlinTypeName}${parameter.kotlinTypeName}QueryParam")
        )

      var queryParameterTypeName = typeRegistry.resolveTypeName(parameter.schema!!, queryParameterTypeNameContext)

      if (parameter.required != true) {
        queryParameterTypeName = queryParameterTypeName.copy(nullable = true)
      } else if (parameter.schema?.defaultValue != null) {
        queryParameterTypeName = queryParameterTypeName.copy(nullable = false)
      }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(parameter.kotlinIdentifierName),
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
    typeName: ClassName,
    typeBuilder: TypeSpec.Builder,
    functionBuilder: FunSpec.Builder
  ) {

    val request = operation.request ?: operation.requests.firstOrNull() ?: return

    request.headers.forEach { header ->

      val headerParameterTypeNameContext =
        KotlinResolutionContext(
          document,
          typeName.nestedClass("${operation.kotlinTypeName}${header.kotlinTypeName}HeaderParam")
        )

      var headerParameterTypeName = typeRegistry.resolveTypeName(header.schema!!, headerParameterTypeNameContext)

      if (header.required != true) {
        headerParameterTypeName = headerParameterTypeName.copy(nullable = true)
      } else if (header.schema?.defaultValue != null) {
        headerParameterTypeName = headerParameterTypeName.copy(nullable = false)
      }

      val functionBuilderNameAllocator = functionBuilder.tags[NameAllocator::class] as NameAllocator

      val uriParameterBuilder = ParameterSpec.builder(
        functionBuilderNameAllocator.newName(header.kotlinIdentifierName),
        headerParameterTypeName
      )

      val uriParameterSpec =
        processResourceMethodHeaderParameter(
          endPoint,
          operation,
          header,
          typeBuilder,
          functionBuilder,
          uriParameterBuilder
        )

      functionBuilder.addParameter(uriParameterSpec)
    }
  }

  private fun findProblemTypes(): Map<String, ProblemTypeDefinition> {

    val problemBaseUriParams =
      document.api.findAnnotation(
        ProblemBaseUriParams,
        typeRegistry.generationMode
      )?.objectValue ?: emptyMap()

    fun expand(template: String): URI {
      try {
        return URI(UriTemplate.expand(template, problemBaseUriParams))
      } catch (e: URISyntaxException) {
        throw error(
          """
            Problem URI is not a valid URI; it cannot be a template.
            Use `problemBaseUri` and/or `problemBaseUriParams` to ensure it is valid.
          """.trimIndent()
        )
      }
    }

    val baseUri = expand(document.api.servers.firstOrNull()?.url ?: defaultProblemBaseUri)

    val problemBaseUri =
      document.api.findAnnotation(
        ProblemBaseUri,
        typeRegistry.generationMode
      )?.stringValue?.let { baseUri.resolve(expand(it)) } ?: baseUri

    val problemDefLocations: Map<BaseUnit, CustomizableElement> =
      document.allUnits.filterIsInstance<CustomizableElement>().map { it as BaseUnit to it }.toMap() +
        document.allUnits.filterIsInstance<EncodesModel>().map { it as BaseUnit to it.encodes }.toMap()

    return problemDefLocations
      .mapNotNull { (unit, element) ->
        (element.findAnnotation(ProblemTypes, generationMode) as? ObjectNode)
          ?.properties()
          ?.mapValues { ProblemTypeDefinition(it.key, it.value as ObjectNode, problemBaseUri, unit) }?.entries
      }
      .flatten()
      .associate { it.key to it.value }
      .toMap()
  }

}
