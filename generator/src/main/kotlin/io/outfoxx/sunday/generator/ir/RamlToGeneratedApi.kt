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

package io.outfoxx.sunday.generator.ir

import amf.apicontract.client.platform.model.domain.EndPoint
import amf.apicontract.client.platform.model.domain.Operation
import amf.apicontract.client.platform.model.domain.Parameter
import amf.apicontract.client.platform.model.domain.Payload
import amf.apicontract.client.platform.model.domain.Request
import amf.apicontract.client.platform.model.domain.Response
import amf.apicontract.client.platform.model.domain.Server
import amf.apicontract.client.platform.model.domain.Tag
import amf.apicontract.client.platform.model.domain.api.WebApi
import amf.apicontract.client.platform.model.domain.security.ApiKeySettings
import amf.apicontract.client.platform.model.domain.security.HttpSettings
import amf.apicontract.client.platform.model.domain.security.SecurityRequirement
import amf.apicontract.client.platform.model.domain.security.SecurityScheme
import amf.core.client.platform.model.DataTypes
import amf.core.client.platform.model.document.BaseUnit
import amf.core.client.platform.model.document.DeclaresModel
import amf.core.client.platform.model.document.Document
import amf.core.client.platform.model.document.EncodesModel
import amf.core.client.platform.model.domain.ArrayNode
import amf.core.client.platform.model.domain.CustomizableElement
import amf.core.client.platform.model.domain.DataNode
import amf.core.client.platform.model.domain.ObjectNode
import amf.core.client.platform.model.domain.PropertyShape
import amf.core.client.platform.model.domain.ScalarNode
import amf.core.client.platform.model.domain.Shape
import amf.shapes.client.platform.model.domain.AnyShape
import amf.shapes.client.platform.model.domain.ArrayShape
import amf.shapes.client.platform.model.domain.Example
import amf.shapes.client.platform.model.domain.FileShape
import amf.shapes.client.platform.model.domain.NilShape
import amf.shapes.client.platform.model.domain.NodeShape
import amf.shapes.client.platform.model.domain.ScalarShape
import amf.shapes.client.platform.model.domain.UnionShape
import com.damnhandy.uri.template.UriTemplate
import io.outfoxx.sunday.generator.APIAnnotationName
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.APIAnnotations
import io.outfoxx.sunday.generator.common.APIProcessor
import io.outfoxx.sunday.generator.common.ShapeIndex
import io.outfoxx.sunday.generator.utils.accepts
import io.outfoxx.sunday.generator.utils.additionalPropertiesSchema
import io.outfoxx.sunday.generator.utils.allUnits
import io.outfoxx.sunday.generator.utils.allowEmptyValue
import io.outfoxx.sunday.generator.utils.allowReserved
import io.outfoxx.sunday.generator.utils.and
import io.outfoxx.sunday.generator.utils.annotations
import io.outfoxx.sunday.generator.utils.anyOf
import io.outfoxx.sunday.generator.utils.anyValue
import io.outfoxx.sunday.generator.utils.api
import io.outfoxx.sunday.generator.utils.closed
import io.outfoxx.sunday.generator.utils.contentType
import io.outfoxx.sunday.generator.utils.declares
import io.outfoxx.sunday.generator.utils.defaultValue
import io.outfoxx.sunday.generator.utils.deprecated
import io.outfoxx.sunday.generator.utils.description
import io.outfoxx.sunday.generator.utils.discriminator
import io.outfoxx.sunday.generator.utils.discriminatorMapping
import io.outfoxx.sunday.generator.utils.discriminatorValue
import io.outfoxx.sunday.generator.utils.encodes
import io.outfoxx.sunday.generator.utils.endPoints
import io.outfoxx.sunday.generator.utils.examples
import io.outfoxx.sunday.generator.utils.exclusiveMaximum
import io.outfoxx.sunday.generator.utils.exclusiveMinimum
import io.outfoxx.sunday.generator.utils.explode
import io.outfoxx.sunday.generator.utils.findAnnotation
import io.outfoxx.sunday.generator.utils.findArrayAnnotation
import io.outfoxx.sunday.generator.utils.findBoolAnnotation
import io.outfoxx.sunday.generator.utils.findStringAnnotation
import io.outfoxx.sunday.generator.utils.format
import io.outfoxx.sunday.generator.utils.get
import io.outfoxx.sunday.generator.utils.getValue
import io.outfoxx.sunday.generator.utils.hasAnnotation
import io.outfoxx.sunday.generator.utils.headers
import io.outfoxx.sunday.generator.utils.id
import io.outfoxx.sunday.generator.utils.inherits
import io.outfoxx.sunday.generator.utils.isNameExplicit
import io.outfoxx.sunday.generator.utils.items
import io.outfoxx.sunday.generator.utils.linkExpression
import io.outfoxx.sunday.generator.utils.location
import io.outfoxx.sunday.generator.utils.makesNullable
import io.outfoxx.sunday.generator.utils.maxItems
import io.outfoxx.sunday.generator.utils.maxLength
import io.outfoxx.sunday.generator.utils.maxProperties
import io.outfoxx.sunday.generator.utils.maximum
import io.outfoxx.sunday.generator.utils.mediaType
import io.outfoxx.sunday.generator.utils.method
import io.outfoxx.sunday.generator.utils.minItems
import io.outfoxx.sunday.generator.utils.minLength
import io.outfoxx.sunday.generator.utils.minProperties
import io.outfoxx.sunday.generator.utils.minimum
import io.outfoxx.sunday.generator.utils.multipleOf
import io.outfoxx.sunday.generator.utils.name
import io.outfoxx.sunday.generator.utils.nonNullableType
import io.outfoxx.sunday.generator.utils.nonPatternProperties
import io.outfoxx.sunday.generator.utils.nullableType
import io.outfoxx.sunday.generator.utils.objectValue
import io.outfoxx.sunday.generator.utils.operationName
import io.outfoxx.sunday.generator.utils.or
import io.outfoxx.sunday.generator.utils.parameters
import io.outfoxx.sunday.generator.utils.parent
import io.outfoxx.sunday.generator.utils.path
import io.outfoxx.sunday.generator.utils.pattern
import io.outfoxx.sunday.generator.utils.patternName
import io.outfoxx.sunday.generator.utils.patternProperties
import io.outfoxx.sunday.generator.utils.properties
import io.outfoxx.sunday.generator.utils.queryParameters
import io.outfoxx.sunday.generator.utils.queryString
import io.outfoxx.sunday.generator.utils.range
import io.outfoxx.sunday.generator.utils.rawScalarValue
import io.outfoxx.sunday.generator.utils.readOnly
import io.outfoxx.sunday.generator.utils.required
import io.outfoxx.sunday.generator.utils.responses
import io.outfoxx.sunday.generator.utils.root
import io.outfoxx.sunday.generator.utils.scalarValue
import io.outfoxx.sunday.generator.utils.security
import io.outfoxx.sunday.generator.utils.servers
import io.outfoxx.sunday.generator.utils.strict
import io.outfoxx.sunday.generator.utils.stringValue
import io.outfoxx.sunday.generator.utils.structuredValue
import io.outfoxx.sunday.generator.utils.style
import io.outfoxx.sunday.generator.utils.summary
import io.outfoxx.sunday.generator.utils.tags
import io.outfoxx.sunday.generator.utils.templateVariable
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.sunday.generator.utils.uniqueItems
import io.outfoxx.sunday.generator.utils.uriParameters
import io.outfoxx.sunday.generator.utils.url
import io.outfoxx.sunday.generator.utils.values
import io.outfoxx.sunday.generator.utils.variables
import io.outfoxx.sunday.generator.utils.version
import io.outfoxx.sunday.generator.utils.writeOnly
import io.outfoxx.sunday.generator.utils.xone
import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException

/**
 * Converts an AMF API processor result into the Sunday generated API IR.
 */
class RamlToGeneratedApi(
  private val sourceKind: GeneratedSourceSpec.Kind = GeneratedSourceSpec.Kind.RAML,
  private val options: GeneratedApiIrOptions = GeneratedApiIrOptions(),
) {

  /** Converts a processed RAML document into a generated API IR value. */
  fun convert(result: APIProcessor.Result): GeneratedApi {

    val document = result.document
    val api = document.api
    val shapeIndex = result.shapeIndex
    val rootLocation = document.location
    val localModels = LocalModelRegistry(shapeIndex, rootLocation)
    val resolvedEndpointParameters = resolvedEndpointParameters(result.serviceDocument)
    val resolvedOperationRequests = resolvedOperationRequests(result.serviceDocument)
    val apiMedia = media(api.contentType, api.accepts)
    val apiAuth =
      auth(
        securityRequirements =
          if (sourceKind == GeneratedSourceSpec.Kind.OPENAPI) {
            api.security
          } else {
            api.security + api.endPoints.flatMap(::securityRequirements)
          },
        localModels = localModels,
        zanzibar = api.zanzibar(),
        zanzibarUserSource = api.zanzibarUserSource(),
      )
    val server = api.servers.firstOrNull()
    val services =
      api
        .serviceEndpointGroups()
        .mapNotNull { serviceGroup ->
          service(
            name = serviceGroup.name,
            baseUri = server?.url,
            baseUriParameters = server.baseUriParameters(api.version, shapeIndex, rootLocation),
            endPoints = serviceGroup.endPoints,
            apiAuth = apiAuth,
            apiMedia = apiMedia,
            api = api,
            shapeIndex = shapeIndex,
            rootLocation = rootLocation,
            localModels = localModels,
            resolvedEndpointParameters = resolvedEndpointParameters,
            resolvedOperationRequests = resolvedOperationRequests,
          )
        }

    return GeneratedApi(
      name = api.name ?: "API",
      source =
        GeneratedSourceSpec(
          kind = sourceKind,
          location = document.location,
        ),
      services = services,
      models = models(document, api, shapeIndex, rootLocation, localModels),
      problems = problems(document, api),
      auth = apiAuth,
      jaxrs = api.jaxrs(),
      media = apiMedia,
      targets = api.targets(),
      tags = api.tags.mapNotNull { tag -> tag.generatedTag() },
      documentation = documentation(description = api.description),
    )
  }

  /** Converts a processed RAML document into a generated API IR composition fragment. */
  fun convertFragment(result: APIProcessor.Result): GeneratedApiFragment {
    val api = convert(result).withRootProblemReferenceSources(result.document.location)
    val sourceApi = result.document.api
    val serviceGroups =
      sourceApi
        .serviceEndpointGroups()
        .filter { serviceGroup -> api.services.any { service -> service.name == serviceGroup.name } }

    return GeneratedApiFragment(
      api = api,
      apiId = sourceApi.compositionApiIdentity(result.document.location),
      serviceIdentities = serviceGroups.associate { serviceGroup -> serviceGroup.name to serviceGroup.identity },
      operationIdentities =
        serviceGroups
          .flatMap { serviceGroup ->
            serviceGroup.endPoints.flatMap { endPoint ->
              endPoint
                .operations()
                .filterNot { operation -> operation.excluded() }
                .map { operation ->
                  val operationId = operation.generatedOperationId(endPoint.path)
                  GeneratedOperationIdentityKey(serviceGroup.name, operationId) to
                    operation.compositionOperationIdentity(operationId)
                }
            }
          }.toMap(),
      modelIdentities =
        api.models.associate { model ->
          model.name to GeneratedIdentity.native(normalizedCompositionId(model.name))
        },
      problemIdentities =
        api.problems.associate { problem ->
          problem.name to problem.compositionProblemIdentity()
        },
    )
  }

  private fun service(
    name: String,
    baseUri: String?,
    baseUriParameters: List<GeneratedParameter>,
    endPoints: List<EndPoint>,
    apiAuth: GeneratedAuth?,
    apiMedia: GeneratedMedia,
    api: WebApi,
    shapeIndex: ShapeIndex,
    rootLocation: String,
    localModels: LocalModelRegistry,
    resolvedEndpointParameters: Map<String, List<Parameter>>,
    resolvedOperationRequests: Map<ResolvedOperationKey, Request>,
  ): GeneratedService? {
    val operations =
      endPoints.flatMap { endPoint ->
        endPoint
          .operations()
          .filterNot { operation -> operation.excluded() }
          .map { operation ->
            operation(
              operation,
              endPoint,
              name,
              apiAuth,
              apiMedia,
              api,
              shapeIndex,
              rootLocation,
              localModels,
              resolvedEndpointParameters,
              resolvedOperationRequests,
            )
          }
      }

    return operations
      .takeIf { it.isNotEmpty() }
      ?.let {
        GeneratedService(
          name = name,
          baseUri = baseUri,
          baseUriParameters = baseUriParameters,
          operations = operations,
          auth = apiAuth,
          jaxrs = endPoints.serviceJaxrs(),
          media = apiMedia,
          documentation = endPoints.firstOrNull()?.root?.let { documentation(it.summary, it.description) },
        )
      }
  }

  private fun operation(
    operation: Operation,
    endPoint: EndPoint,
    serviceName: String,
    inheritedAuth: GeneratedAuth?,
    inheritedMedia: GeneratedMedia?,
    api: WebApi,
    shapeIndex: ShapeIndex,
    rootLocation: String,
    localModels: LocalModelRegistry,
    resolvedEndpointParameters: Map<String, List<Parameter>>,
    resolvedOperationRequests: Map<ResolvedOperationKey, Request>,
  ): GeneratedOperation {

    val request = operation.request() ?: operation.requests().firstOrNull()
    val operationMedia =
      media(operation.contentType, operation.accepts).takeUnless { it == GeneratedMedia() } ?: inheritedMedia
    val operationAuth =
      auth(
        securityRequirements = resolveSecurityRequirements(api, endPoint, operation),
        localModels = localModels,
        zanzibar = operation.zanzibar(),
        inheritedZanzibar = inheritedAuth?.zanzibar.orEmpty(),
        zanzibarUserSource = operation.zanzibarUserSource(),
        inheritedZanzibarUserSource = inheritedAuth?.zanzibarUserSource,
      ) ?: inheritedAuth

    val operationId = operation.generatedOperationId(endPoint.path)
    val requestParameters =
      request.mergedParameters(
        resolvedRequest = resolvedOperationRequests[ResolvedOperationKey(endPoint.path, operation.method.uppercase())],
        serviceName = serviceName,
        operationName = operationId,
        rootLocation = rootLocation,
        localModels = localModels,
      )

    return GeneratedOperation(
      id = operationId,
      method = operation.method.uppercase(),
      path = endPoint.path,
      parameters =
        endPoint.effectiveParameters(resolvedEndpointParameters).map {
          it.parameter(
            location = GeneratedParameter.Location.PATH,
            required = it.required ?: true,
            serviceName = serviceName,
            operationName = operationId,
            rootLocation = rootLocation,
            localModels = localModels,
          )
        } +
          requestParameters,
      queryString = request?.queryString?.let { localModels.queryStringRef(it, serviceName, operationId) },
      requestBody = request?.requestBody(shapeIndex, serviceName, operationId, rootLocation, localModels),
      responses =
        operation.responses.map { response ->
          response.response(shapeIndex, serviceName, operationId, rootLocation, localModels)
        },
      problems = operation.problems(),
      nullify = operation.nullify(),
      exchange = operation.exchange(),
      auth = operationAuth,
      media = operationMedia,
      policy = operation.policy(),
      streaming = operation.streaming(),
      jaxrs = operation.jaxrs(),
      deprecated = operation.deprecated == true,
      tags = operation.tags.mapNotNull { tag -> tag.name },
      documentation = documentation(operation.summary, operation.description),
    )
  }

  private fun resolvedEndpointParameters(serviceDocument: Document): Map<String, List<Parameter>> =
    if (sourceKind == GeneratedSourceSpec.Kind.OPENAPI) {
      serviceDocument
        .api
        .endPoints
        .associate { endPoint -> endPoint.path to endPoint.parameters }
    } else {
      emptyMap()
    }

  private fun resolvedOperationRequests(serviceDocument: Document): Map<ResolvedOperationKey, Request> =
    if (sourceKind == GeneratedSourceSpec.Kind.OPENAPI) {
      serviceDocument.api
        .endPoints
        .flatMap { endPoint ->
          endPoint.operations().mapNotNull { operation ->
            val request = operation.request() ?: operation.requests().firstOrNull() ?: return@mapNotNull null
            ResolvedOperationKey(endPoint.path, operation.method.uppercase()) to request
          }
        }.toMap()
    } else {
      emptyMap()
    }

  private fun EndPoint.effectiveParameters(resolvedEndpointParameters: Map<String, List<Parameter>>): List<Parameter> {
    val resolvedParameters = resolvedEndpointParameters[path].orEmpty()
    if (resolvedParameters.isEmpty()) {
      return parameters
    }

    val parameterNames =
      parameters
        .map { parameter -> parameter.parameterName().value() ?: parameter.name ?: "" }
        .toSet()

    return parameters +
      resolvedParameters.filter { parameter ->
        (parameter.parameterName().value() ?: parameter.name ?: "") !in parameterNames
      }
  }

  private fun Request?.mergedParameters(
    resolvedRequest: Request?,
    serviceName: String,
    operationName: String,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): List<GeneratedParameter> {
    val declaredParameters =
      this
        ?.parameters(
          serviceName,
          operationName,
          rootLocation,
          localModels,
        ).orEmpty()
    val resolvedParameters =
      resolvedRequest
        ?.parameters(
          serviceName,
          operationName,
          rootLocation,
          localModels,
        ).orEmpty()
    if (resolvedParameters.isEmpty()) {
      return declaredParameters
    }

    val declaredNames = declaredParameters.map { parameter -> parameter.location to parameter.name }.toSet()
    return declaredParameters +
      resolvedParameters.filter { parameter ->
        parameter.location to parameter.name !in declaredNames
      }
  }

  private fun Request.parameters(
    serviceName: String,
    operationName: String,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): List<GeneratedParameter> =
    uriParameters.map { parameter ->
      parameter.parameter(
        GeneratedParameter.Location.PATH,
        required = parameter.required ?: true,
        serviceName = serviceName,
        operationName = operationName,
        rootLocation = rootLocation,
        localModels = localModels,
      )
    } +
      queryParameters.map { parameter ->
        parameter.parameter(
          GeneratedParameter.Location.QUERY,
          serviceName = serviceName,
          operationName = operationName,
          rootLocation = rootLocation,
          localModels = localModels,
        )
      } +
      headers.map { parameter ->
        parameter.parameter(
          GeneratedParameter.Location.HEADER,
          serviceName = serviceName,
          operationName = operationName,
          rootLocation = rootLocation,
          localModels = localModels,
        )
      }

  private fun Parameter.parameter(
    location: GeneratedParameter.Location,
    required: Boolean = this.required ?: false,
    serviceName: String? = null,
    operationName: String? = null,
    rootLocation: String? = null,
    localModels: LocalModelRegistry? = null,
  ): GeneratedParameter {
    val wireName = parameterName().value() ?: name ?: ""
    val generatedName = wireName.toLowerCamelCase()
    val schema = schema()
    val type =
      schema
        ?.let { localModels?.parameterRef(it, serviceName, operationName, location, generatedName, wireName, required) }
        ?: schema?.let { typeRef(it, rootLocation = rootLocation, localModels = localModels) }
        ?: GeneratedTypeRef.scalar("any")
    return GeneratedParameter(
      name = generatedName,
      location = location,
      type = type,
      required = required,
      serializationName = wireName.takeUnless { it == generatedName },
      defaultValue = schema?.defaultValue?.anyValue,
      constantValue = schema?.constantValue(required, location),
      encoding = encoding(),
      validation = schema?.let(::validation).orEmpty(),
      examples = examples.examples() + (schema as? AnyShape)?.examples.orEmpty().examples(),
      deprecated = deprecated == true,
      documentation = documentation(description = description),
    )
  }

  private fun Parameter.encoding(): GeneratedParameterEncoding? =
    GeneratedParameterEncoding(
      style = style,
      explode = explode,
      allowReserved = allowReserved,
      allowEmptyValue = allowEmptyValue,
    ).takeUnless { it == GeneratedParameterEncoding() }

  private fun Shape.constantValue(
    required: Boolean,
    location: GeneratedParameter.Location,
  ): Any? {
    if (!required || location !in constantParameterLocations) return null
    return values.singleOrNull()?.scalarValue
  }

  private fun Request.requestBody(
    shapeIndex: ShapeIndex,
    serviceName: String,
    operationName: String,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): GeneratedPayload? {
    val payloads = payloads()
    val payloadOptions =
      payloads.payloadOptions(
        shapeIndex = shapeIndex,
        serviceName = serviceName,
        operationName = operationName,
        usage = GeneratedModelScope.Usage.REQUEST_BODY,
        rootLocation = rootLocation,
        localModels = localModels,
      )
    val firstPayload = payloadOptions.firstOrNull() ?: return null
    return GeneratedPayload(
      type = firstPayload.type,
      mediaTypes = firstPayload.mediaTypes,
      payloads = payloadOptions.takeIf { it.size > 1 }.orEmpty(),
      examples =
        payloadOptions.flatMap { payload -> payload.examples },
      documentation = documentation(description = description),
    )
  }

  private fun Response.response(
    shapeIndex: ShapeIndex,
    serviceName: String,
    operationName: String,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): GeneratedResponse {
    val status = statusCode().value()?.toIntOrNull()
    val payloadOptions =
      payloads().payloadOptions(
        shapeIndex = shapeIndex,
        serviceName = serviceName,
        operationName = operationName,
        usage = GeneratedModelScope.Usage.RESPONSE_BODY,
        status = status,
        rootLocation = rootLocation,
        localModels = localModels,
      )
    val firstPayload = payloadOptions.firstOrNull()
    return GeneratedResponse(
      status = status,
      type = firstPayload?.type,
      mediaTypes = firstPayload?.mediaTypes.orEmpty(),
      payloads = payloadOptions.takeIf { it.size > 1 }.orEmpty(),
      headers =
        headers.map { header ->
          header.parameter(
            GeneratedParameter.Location.HEADER,
            serviceName = serviceName,
            operationName = operationName,
            rootLocation = rootLocation,
            localModels = localModels,
          )
        },
      examples = payloadOptions.flatMap { payload -> payload.examples },
      documentation = documentation(description = description),
    )
  }

  private fun Server?.baseUriParameters(
    apiVersion: String?,
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): List<GeneratedParameter> =
    this
      ?.variables
      .orEmpty()
      .mapIndexed { idx, variable ->
        val wireName = variable.name ?: variable.parameterName().value() ?: "uriParameter$idx"
        val generatedName = wireName.toLowerCamelCase()
        val schema = variable.schema()
        GeneratedParameter(
          name = generatedName,
          location = GeneratedParameter.Location.PATH,
          type =
            schema?.let(shapeIndex::resolve)?.let { typeRef(it, rootLocation = rootLocation) }
              ?: GeneratedTypeRef.scalar("string"),
          serializationName = wireName.takeUnless { it == generatedName },
          defaultValue =
            schema?.defaultValue?.anyValue
              ?: apiVersion.takeIf { wireName == "version" },
          validation = schema?.let(::validation).orEmpty(),
          examples = variable.examples.examples() + (schema as? AnyShape)?.examples.orEmpty().examples(),
          documentation = documentation(description = variable.description),
        )
      }

  private fun List<Payload>.payloadOptions(
    shapeIndex: ShapeIndex,
    serviceName: String,
    operationName: String,
    usage: GeneratedModelScope.Usage,
    status: Int? = null,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): List<GeneratedPayloadOption> {
    val payloadsByType = linkedMapOf<GeneratedTypeRef, MutableList<Payload>>()
    forEach { payload ->
      val schema = payload.schema()?.let(shapeIndex::resolve) ?: return@forEach
      val type =
        localModels.payloadRef(schema, serviceName, operationName, usage, status)
          ?: typeRef(schema, rootLocation = rootLocation, localModels = localModels)
      payloadsByType
        .getOrPut(type) { mutableListOf() }
        .add(payload)
    }
    return payloadsByType.map { (type, payloads) ->
      GeneratedPayloadOption(
        type = type,
        mediaTypes = payloads.mapNotNull { payload -> payload.mediaType().value() },
        examples =
          payloads.flatMap { payload ->
            val payloadExamples =
              payload
                .examples
                .filterNot { example -> example.isDeclaredExample || example.isInheritedShapeExample }
                .examples(payload.mediaType)
            payloadExamples.ifEmpty { payload.schemaExamples(payload.mediaType) }
          },
      )
    }
  }

  private fun Operation.problems(): List<GeneratedTypeRef> =
    findArrayAnnotation(APIAnnotationName.Problems, null)
      ?.mapNotNull { value -> value.rawScalarValue?.let { GeneratedTypeRef.named(problemName(it)) } }
      ?: listOf()

  private fun Operation.nullify(): GeneratedNullify? =
    findArrayAnnotation(APIAnnotationName.Nullify, null)
      ?.let(APIAnnotations::groupNullifyIntoStatusesAndProblems)
      ?.let { (problemTypes, statuses) ->
        GeneratedNullify(
          problems = problemTypes.map { GeneratedTypeRef.named(problemName(it)) },
          statuses = statuses.toList(),
        )
      }

  private fun Operation.exchange(): GeneratedExchange? =
    when {
      findBoolAnnotation(APIAnnotationName.RequestOnly, null) == true -> GeneratedExchange.REQUEST
      findBoolAnnotation(APIAnnotationName.ResponseOnly, null) == true -> GeneratedExchange.RESPONSE
      else -> null
    }

  private fun Operation.streaming(): GeneratedStreaming? =
    when {
      findBoolAnnotation(APIAnnotationName.EventSource, null) == true ->
        GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_SOURCE)

      hasAnnotation(APIAnnotationName.EventStream, null) ->
        GeneratedStreaming(
          kind = GeneratedStreaming.Kind.EVENT_STREAM,
          eventMode =
            when (findStringAnnotation(APIAnnotationName.EventStream, null)) {
              "simple" -> GeneratedStreaming.EventMode.SIMPLE
              "discriminated" -> GeneratedStreaming.EventMode.DISCRIMINATED
              else -> null
            },
        )

      else -> null
    }

  private fun Operation.jaxrs(): GeneratedJaxrs? =
    GeneratedJaxrs(
      asynchronous = findBoolAnnotation(APIAnnotationName.Asynchronous, null),
      reactive = findBoolAnnotation(APIAnnotationName.Reactive, null),
      sse = modeFlag(APIAnnotationName.SSE),
      jsonBody = modeFlag(APIAnnotationName.JsonBody),
      context =
        findArrayAnnotation(APIAnnotationName.JaxrsContext, options.generationMode)
          ?.mapNotNull { value -> value.stringValue }
          ?.distinct()
          .orEmpty(),
    ).takeUnless { it == GeneratedJaxrs() }

  private fun CustomizableElement.jaxrs(): GeneratedJaxrs? =
    GeneratedJaxrs(restClient = jaxrsRestClient()).takeUnless { it == GeneratedJaxrs() }

  private fun List<EndPoint>.serviceJaxrs(): GeneratedJaxrs? {
    val values = mapNotNull { endPoint -> endPoint.root.jaxrs() }.distinct()
    require(values.size <= 1) {
      "RAML service endpoints have conflicting sunday.jaxrsRestClient metadata. " +
        "Move REST client metadata to the API root or align endpoint metadata."
    }
    return values.singleOrNull()
  }

  private fun CustomizableElement.jaxrsRestClient(): GeneratedJaxrsRestClient? =
    (findAnnotation(APIAnnotationName.JaxrsRestClient, null) as? ObjectNode)
      ?.let { restClient ->
        GeneratedJaxrsRestClient(
          configKey = restClient.getValue("configKey") ?: restClient.getValue("config-key"),
          oidcClient = restClient.getValue("oidcClient") ?: restClient.getValue("oidc-client"),
          providers =
            restClient
              .get<ArrayNode>("providers")
              ?.members()
              ?.mapNotNull { provider -> provider.stringValue?.trimToNull() }
              ?.distinct()
              .orEmpty(),
        )
      }?.takeUnless { it == GeneratedJaxrsRestClient() }

  private fun Operation.modeFlag(name: APIAnnotationName): GeneratedModeFlag? =
    GeneratedModeFlag(
      all = findBoolAnnotation(name, null),
      client = findBoolAnnotation(name, GenerationMode.Client),
      server = findBoolAnnotation(name, GenerationMode.Server),
    ).takeUnless { it == GeneratedModeFlag() }

  private fun models(
    document: BaseUnit,
    api: WebApi,
    shapeIndex: ShapeIndex,
    rootLocation: String,
    localModels: LocalModelRegistry,
  ): List<GeneratedModel> =
    (
      reachableDeclaredShapes(document, api, shapeIndex)
        .mapNotNull { declaration ->
          model(
            shape = declaration.shape,
            shapeIndex = shapeIndex,
            sourceShape = declaration.sourceShape,
            declaringUnit = declaration.unit,
            rootLocation = rootLocation,
            localModels = localModels,
          )
        }.distinctBy { model -> model.name to model.source?.location } + localModels.models
    ).distinctBy { model -> model.name to model.source?.location to model.scope }
      .sortedWith(
        compareBy(
          { model -> model.name },
          { model -> model.source?.location ?: "" },
          { model -> model.scope?.service ?: "" },
          { model -> model.scope?.operation ?: "" },
          { model -> model.scope?.usage?.name ?: "" },
          { model -> model.scope?.name ?: "" },
          { model -> model.scope?.status ?: -1 },
        ),
      )

  private inner class LocalModelRegistry(
    private val shapeIndex: ShapeIndex,
    private val rootLocation: String,
  ) {

    private val modelsByKey = linkedMapOf<String, GeneratedModel>()
    private val refsByShapeId = linkedMapOf<String, GeneratedTypeRef>()

    val models: List<GeneratedModel>
      get() = modelsByKey.values.toList()

    fun refFor(shape: Shape): GeneratedTypeRef? = refsByShapeId[shape.nonNullableType.id]

    fun parameterRef(
      schema: Shape,
      serviceName: String?,
      operationName: String?,
      location: GeneratedParameter.Location,
      parameterName: String,
      wireName: String,
      required: Boolean,
    ): GeneratedTypeRef? {
      if (serviceName == null || operationName == null) return null
      val resolved = shapeIndex.resolve(schema)
      if (resolved.constantValue(required, location) != null) return null
      val scope =
        GeneratedModelScope(
          service = serviceName,
          operation = operationName,
          usage = GeneratedModelScope.Usage.PARAMETER,
          name = parameterName,
        )
      val modelName =
        "${operationName.toUpperCamelCase()}${parameterName.toUpperCamelCase()}${location.localTypeSuffix()}"
      return register(resolved, modelName, scope, wireName.takeUnless { it == parameterName })
    }

    fun payloadRef(
      schema: Shape,
      serviceName: String,
      operationName: String,
      usage: GeneratedModelScope.Usage,
      status: Int?,
    ): GeneratedTypeRef? {
      val resolved = shapeIndex.resolve(schema)
      val scope =
        GeneratedModelScope(
          service = serviceName,
          operation = operationName,
          usage = usage,
          status = status.takeIf { usage == GeneratedModelScope.Usage.RESPONSE_BODY },
        )
      val modelName =
        when (usage) {
          GeneratedModelScope.Usage.PARAMETER -> return null
          GeneratedModelScope.Usage.QUERY_STRING -> return null
          GeneratedModelScope.Usage.REQUEST_BODY -> "${operationName.toUpperCamelCase()}RequestBody"
          GeneratedModelScope.Usage.RESPONSE_BODY -> "${operationName.toUpperCamelCase()}ResponseBody"
          GeneratedModelScope.Usage.SECURITY_QUERY_STRING -> return null
        }

      if (resolved.nonNullableType is ArrayShape) {
        val item = (resolved.nonNullableType as ArrayShape).items()?.let(shapeIndex::resolve) ?: return null
        register(item, modelName, scope)
        return typeRef(resolved, rootLocation = rootLocation, localModels = this)
      }

      return register(resolved, modelName, scope)
    }

    fun queryStringRef(
      schema: Shape,
      serviceName: String,
      operationName: String,
    ): GeneratedTypeRef {
      val resolved = shapeIndex.resolve(schema)
      val scope =
        GeneratedModelScope(
          service = serviceName,
          operation = operationName,
          usage = GeneratedModelScope.Usage.QUERY_STRING,
        )
      val modelName = "${operationName.toUpperCamelCase()}QueryString"
      return register(resolved, modelName, scope)
        ?: typeRef(resolved, rootLocation = rootLocation, localModels = this)
    }

    fun securityQueryStringRef(
      schema: Shape,
      securitySchemeName: String,
    ): GeneratedTypeRef {
      val resolved = shapeIndex.resolve(schema)
      val scope =
        GeneratedModelScope(
          securityScheme = securitySchemeName,
          usage = GeneratedModelScope.Usage.SECURITY_QUERY_STRING,
        )
      val modelName = "${securitySchemeName.toUpperCamelCase()}QueryString"
      return register(resolved, modelName, scope)
        ?: typeRef(resolved, rootLocation = rootLocation, localModels = this)
    }

    fun declaredPropertyRef(
      schema: Shape,
      parentModelName: String,
      propertyName: String,
      source: GeneratedSourceSpec?,
    ): GeneratedTypeRef? {
      val resolved = shapeIndex.resolve(schema)
      val localShape = localDeclarationShape(resolved) as? NodeShape ?: return null
      if (localShape.isMapLike(shapeIndex) || shapeIndex.findOrderedProperties(localShape).isEmpty()) {
        return null
      }

      val modelName = "${parentModelName.toUpperCamelCase()}${propertyName.toUpperCamelCase()}"
      return register(resolved, modelName, scope = null, source = source)
    }

    private fun register(
      shape: Shape,
      modelName: String,
      scope: GeneratedModelScope?,
      serializationName: String? = null,
      source: GeneratedSourceSpec? = null,
    ): GeneratedTypeRef? {
      val localShape = localDeclarationShape(shape) ?: return null
      val key =
        listOf(
          localShape.id,
          modelName,
          scope?.service,
          scope?.operation,
          scope?.usage,
          scope?.name,
          scope?.securityScheme,
          scope?.status,
        ).joinToString("|")
      val model =
        modelsByKey.getOrPut(key) {
          localShape.localModel(modelName, scope, serializationName, source)
        }
      val ref = GeneratedTypeRef.named(model.name, scope = model.scope, source = model.source)
      refsByShapeId[localShape.id] = ref
      return ref
    }

    private fun localDeclarationShape(shape: Shape): Shape? =
      when (val concrete = shapeIndex.resolve(shape).nonNullableType) {
        is ScalarShape -> concrete.takeIf { it.values.isNotEmpty() && !it.hasDurableModelName() }
        is NodeShape -> concrete.takeIf { !it.hasDurableModelName() }
        is AnyShape ->
          concrete.takeIf {
            sourceKind == GeneratedSourceSpec.Kind.OPENAPI &&
              !it.hasDurableModelName() &&
              it.composedBranches().isNotEmpty()
          }
        else -> null
      }

    private fun Shape.localModel(
      modelName: String,
      scope: GeneratedModelScope?,
      serializationName: String?,
      source: GeneratedSourceSpec? = null,
    ): GeneratedModel =
      when (this) {
        is ScalarShape ->
          GeneratedModel(
            name = modelName,
            kind = GeneratedModel.Kind.ENUM,
            source = source,
            scope = scope,
            values = values.mapNotNull { value -> value.rawScalarValue },
            validation = validation(this),
            serializationName = serializationName,
            examples =
              examples
                .orEmpty()
                .filterNot { example -> example.isInheritedShapeExample }
                .examples(),
            deprecated = deprecated == true,
            documentation = documentation(description = description),
          )

        is NodeShape ->
          GeneratedModel(
            name = modelName,
            kind = modelKind(shapeIndex),
            source = source,
            scope = scope,
            properties =
              shapeIndex.findOrderedProperties(this).map { property ->
                val wireName = property.name ?: property.path().value() ?: ""
                val generatedName = wireName.toLowerCamelCase()
                GeneratedModelProperty(
                  name = generatedName,
                  type =
                    typeRef(
                      shapeIndex.resolve(property.range),
                      inlineShapeName = wireName,
                      rootLocation = rootLocation,
                      localModels = this@LocalModelRegistry,
                      shapeIndex = shapeIndex,
                    ),
                  required = property.required,
                  targets = property.range.targets(),
                  serializationName = wireName.takeUnless { it == generatedName },
                  externalDiscriminator =
                    property.range
                      .findStringAnnotation(APIAnnotationName.ExternalDiscriminator, null)
                      ?.toLowerCamelCase(),
                  validation = propertyValidation(property.range, shapeIndex),
                  examples =
                    (property.range as? AnyShape)
                      ?.examples
                      .orEmpty()
                      .filterNot { example -> example.isPayloadSchemaExample }
                      .examples(),
                  readOnly = property.readOnly == true || property.range.readOnly == true,
                  writeOnly = property.writeOnly == true || property.range.writeOnly == true,
                  deprecated = property.deprecated == true || property.range.deprecated == true,
                  documentation = documentation(description = property.description ?: property.range.description),
                )
              },
            aliases = mapAliases(shapeIndex, rootLocation),
            closed = closed?.takeIf { it },
            additionalProperties = generatedAdditionalProperties(shapeIndex, rootLocation),
            patternProperties = generatedPatternProperties(shapeIndex, rootLocation),
            validation = validation(this),
            serializationName = serializationName,
            examples =
              examples
                .orEmpty()
                .filterNot { example -> example.isInheritedShapeExample }
                .examples(),
            deprecated = deprecated == true,
            documentation = documentation(description = description),
          )

        is AnyShape ->
          GeneratedModel(
            name = modelName,
            kind = GeneratedModel.Kind.UNION,
            source = source,
            scope = scope,
            aliases =
              composedBranches()
                .map { branch -> typeRef(shapeIndex.resolve(branch), rootLocation = rootLocation) },
            serializationName = serializationName,
            examples =
              examples
                .orEmpty()
                .filterNot { example -> example.isInheritedShapeExample }
                .examples(),
            deprecated = deprecated == true,
            documentation = documentation(description = description),
          )

        else -> error("Unsupported local model shape '${this::class.simpleName}'")
      }

    private fun Shape.hasDurableModelName(): Boolean = name != null && isNameExplicit && name !in syntheticShapeNames
  }

  private fun GeneratedParameter.Location.localTypeSuffix(): String =
    when (this) {
      GeneratedParameter.Location.PATH -> "UriParam"
      GeneratedParameter.Location.QUERY -> "QueryParam"
      GeneratedParameter.Location.HEADER -> "HeaderParam"
      GeneratedParameter.Location.COOKIE -> "CookieParam"
      GeneratedParameter.Location.BODY -> "BodyParam"
      GeneratedParameter.Location.MESSAGE -> "MessageParam"
    }

  private data class DeclaredShape(
    val unit: BaseUnit,
    val sourceShape: Shape,
    val shape: Shape,
  )

  private fun reachableDeclaredShapes(
    document: BaseUnit,
    api: WebApi,
    shapeIndex: ShapeIndex,
  ): List<DeclaredShape> {
    val declaredShapes =
      document.allUnits
        .filterIsInstance<DeclaresModel>()
        .flatMap { unit ->
          unit.declares
            .filterIsInstance<Shape>()
            .map { shape ->
              DeclaredShape(
                unit = unit as BaseUnit,
                sourceShape = shape,
                shape = shapeIndex.resolve(shape),
              )
            }
        }
    val declarationsByShapeId = declaredShapes.associateBy { declaration -> declaration.shape.id }
    val declarationsBySourceShapeId = declaredShapes.associateBy { declaration -> declaration.sourceShape.id }
    val reachable = linkedMapOf<String, DeclaredShape>()
    val visited = mutableSetOf<String>()

    fun visit(shape: Shape?) {
      val resolved = shape?.let(shapeIndex::resolve) ?: return
      if (!visited.add(resolved.id)) return

      val declaration = declarationsByShapeId[resolved.id] ?: declarationsBySourceShapeId[shape.id]
      if (declaration != null) {
        reachable[declaration.shape.id] = declaration
      }

      when (val concrete = resolved.nonNullableType) {
        is NodeShape -> {
          concrete.inherits.forEach(::visit)
          concrete.properties.forEach { property -> visit(property.range) }
          concrete.patternProperties.forEach { property -> visit(property.range) }
          visit(concrete.additionalPropertiesSchema)
        }

        is ArrayShape -> visit(concrete.items())
        is UnionShape -> concrete.anyOf().forEach(::visit)
      }

      resolved.and.forEach(::visit)
      resolved.or.forEach(::visit)
      resolved.xone.forEach(::visit)
    }

    (document as? DeclaresModel)
      ?.declares
      .orEmpty()
      .filterIsInstance<Shape>()
      .forEach(::visit)

    api.endPoints.forEach { endPoint ->
      endPoint.parameters.forEach { parameter -> visit(parameter.schema()) }
      endPoint.operations().forEach { operation ->
        operation.requests().forEach { request ->
          request.uriParameters.forEach { parameter -> visit(parameter.schema()) }
          request.queryParameters.forEach { parameter -> visit(parameter.schema()) }
          request.headers.forEach { parameter -> visit(parameter.schema()) }
          request.payloads().forEach { payload -> visit(payload.schema()) }
        }
        operation.responses.forEach { response ->
          response.headers.forEach { parameter -> visit(parameter.schema()) }
          response.payloads().forEach { payload -> visit(payload.schema()) }
        }
      }
    }

    return reachable.values.toList()
  }

  private fun model(
    shape: Shape,
    shapeIndex: ShapeIndex,
    sourceShape: Shape = shape,
    declaringUnit: BaseUnit,
    rootLocation: String,
    localModels: LocalModelRegistry? = null,
  ): GeneratedModel? =
    when (shape) {
      is NodeShape ->
        shape.name?.let { name ->
          val unionBranches = shape.composedBranches()
          if (unionBranches.isNotEmpty()) {
            return GeneratedModel(
              name = name,
              kind = GeneratedModel.Kind.UNION,
              source = declaringUnit.sourceSpec(rootLocation),
              aliases =
                unionBranches.map { branch ->
                  typeRef(shapeIndex.resolve(branch), rootLocation = rootLocation)
                },
              targets = declaringUnit.targetDefaults().mergeWith(shape.targets()),
              nested = shape.nested(),
              patchable = shape.patchable(shapeIndex),
              discriminator = shape.discriminator?.toLowerCamelCase(),
              discriminatorMappings = shape.explicitDiscriminatorMappings(),
              examples =
                (sourceShape as? AnyShape)
                  ?.examples
                  .orEmpty()
                  .filterNot { example -> example.isPayloadSchemaExample }
                  .examples(),
              deprecated = shape.deprecated == true || sourceShape.deprecated == true,
              documentation = documentation(description = shape.description),
            )
          }

          val sourcePropertiesByName =
            (sourceShape as? NodeShape)
              ?.properties
              .orEmpty()
              .associateBy { property -> property.name ?: property.path().value() ?: "" }
          val inheritedShapes = shape.inherits.map(shapeIndex::resolve)
          val inherits =
            inheritedShapes
              .filter { inherited ->
                inherited.hasDurableModelName()
              }.map { inherited ->
                typeRef(shapeIndex.resolve(inherited), rootLocation = rootLocation)
              }
          val inheritedProperties =
            inheritedShapes.flatMap { inherited ->
              inherited.localInheritedProperties(
                parentModelName = name,
                shapeIndex = shapeIndex,
                rootLocation = rootLocation,
                localModels = localModels,
                source = declaringUnit.sourceSpec(rootLocation),
              )
            }
          val externallyDiscriminated =
            inherits.isEmpty() && shape.findBoolAnnotation(APIAnnotationName.ExternallyDiscriminated, null) == true
          GeneratedModel(
            name = name,
            kind = shape.modelKind(shapeIndex),
            source = declaringUnit.sourceSpec(rootLocation),
            properties =
              inheritedProperties +
                shape.modelProperties(
                  parentModelName = name,
                  sourcePropertiesByName = sourcePropertiesByName,
                  shapeIndex = shapeIndex,
                  rootLocation = rootLocation,
                  localModels = localModels,
                  source = declaringUnit.sourceSpec(rootLocation),
                ),
            aliases = shape.mapAliases(shapeIndex, rootLocation),
            closed = shape.closed?.takeIf { it },
            additionalProperties = shape.generatedAdditionalProperties(shapeIndex, rootLocation),
            patternProperties = shape.generatedPatternProperties(shapeIndex, rootLocation),
            targets = declaringUnit.targetDefaults().mergeWith(shape.targets()),
            nested = shape.nested(),
            patchable = shape.patchable(shapeIndex),
            inherits = inherits,
            discriminator = shape.discriminator?.toLowerCamelCase()?.takeIf { inherits.isEmpty() },
            discriminatorValue = shape.discriminatorValue,
            externallyDiscriminated = externallyDiscriminated,
            discriminatorMappings =
              discriminatorMappings(shape, shapeIndex, rootLocation)
                .takeIf { externallyDiscriminated }
                .orEmpty(),
            examples =
              (sourceShape as? AnyShape)
                ?.examples
                .orEmpty()
                .filterNot { example -> example.isPayloadSchemaExample }
                .examples(),
            deprecated = shape.deprecated == true || sourceShape.deprecated == true,
            documentation = documentation(description = shape.description),
          )
        }

      is ArrayShape ->
        shape.explicitArrayName()?.let { name ->
          GeneratedModel(
            name = name,
            kind = GeneratedModel.Kind.ARRAY,
            source = declaringUnit.sourceSpec(rootLocation),
            aliases =
              listOf(
                shape.items()?.let { typeRef(it, rootLocation = rootLocation) } ?: GeneratedTypeRef.scalar("any"),
              ),
            collection = shape.collectionKind(),
            targets = declaringUnit.targetDefaults().mergeWith(shape.targets()),
            nested = shape.nested(),
            patchable = shape.patchable(shapeIndex),
            examples =
              (sourceShape as? AnyShape)
                ?.examples
                .orEmpty()
                .filterNot { example -> example.isPayloadSchemaExample }
                .examples(),
            deprecated = shape.deprecated == true,
            documentation = documentation(description = shape.description),
          )
        }

      is ScalarShape ->
        shape.name?.takeIf { shape.values.isNotEmpty() }?.let { name ->
          GeneratedModel(
            name = name,
            kind = GeneratedModel.Kind.ENUM,
            source = declaringUnit.sourceSpec(rootLocation),
            values = shape.values.mapNotNull { value -> value.rawScalarValue },
            targets = declaringUnit.targetDefaults().mergeWith(shape.targets()),
            nested = shape.nested(),
            patchable = shape.patchable(shapeIndex),
            examples =
              (sourceShape as? AnyShape)
                ?.examples
                .orEmpty()
                .filterNot { example -> example.isPayloadSchemaExample }
                .examples(),
            deprecated = shape.deprecated == true,
            documentation = documentation(description = shape.description),
          )
        }

      is UnionShape ->
        shape.name?.let { name ->
          GeneratedModel(
            name = name,
            kind = GeneratedModel.Kind.UNION,
            source = declaringUnit.sourceSpec(rootLocation),
            aliases = shape.anyOf().filterNot(::isNilShape).map { typeRef(it, rootLocation = rootLocation) },
            targets = declaringUnit.targetDefaults().mergeWith(shape.targets()),
            nested = shape.nested(),
            patchable = shape.patchable(shapeIndex),
            examples =
              (sourceShape as? AnyShape)
                ?.examples
                .orEmpty()
                .filterNot { example -> example.isPayloadSchemaExample }
                .examples(),
            deprecated = shape.deprecated == true,
            documentation = documentation(description = shape.description),
          )
        }

      is AnyShape ->
        shape.composedModel(shapeIndex, declaringUnit, rootLocation)

      else -> null
    }

  private fun AnyShape.composedModel(
    shapeIndex: ShapeIndex,
    declaringUnit: BaseUnit,
    rootLocation: String,
  ): GeneratedModel? {
    val name = name ?: return null
    val allOf = and.map(shapeIndex::resolve)
    if (allOf.isNotEmpty()) {
      val inheritedShapes = allOf.filter { shape -> shape.hasDurableModelName() }
      val inheritedRefs =
        inheritedShapes.map { shape -> typeRef(shape, rootLocation = rootLocation) }
      val localProperties =
        allOf
          .filterIsInstance<NodeShape>()
          .filterNot { shape -> shape.hasDurableModelName() }
          .flatMap { shape ->
            shape.modelProperties(
              parentModelName = name,
              sourcePropertiesByName = mapOf(),
              shapeIndex = shapeIndex,
              rootLocation = rootLocation,
            )
          }

      return GeneratedModel(
        name = name,
        kind = GeneratedModel.Kind.OBJECT,
        source = declaringUnit.sourceSpec(rootLocation),
        properties = localProperties,
        targets = declaringUnit.targetDefaults().mergeWith(targets()),
        inherits = inheritedRefs,
        discriminatorValue = discriminatorValueFrom(inheritedShapes),
        examples =
          examples
            .orEmpty()
            .filterNot { example -> example.isPayloadSchemaExample }
            .examples(),
        deprecated = deprecated == true,
        documentation = documentation(description = description),
      )
    }

    val unionBranches = xone.ifEmpty { or }
    if (unionBranches.isNotEmpty()) {
      return GeneratedModel(
        name = name,
        kind = GeneratedModel.Kind.UNION,
        source = declaringUnit.sourceSpec(rootLocation),
        aliases = unionBranches.map { branch -> typeRef(shapeIndex.resolve(branch), rootLocation = rootLocation) },
        targets = declaringUnit.targetDefaults().mergeWith(targets()),
        examples =
          examples
            .orEmpty()
            .filterNot { example -> example.isPayloadSchemaExample }
            .examples(),
        deprecated = deprecated == true,
        documentation = documentation(description = description),
      )
    }

    return null
  }

  private fun Shape.localInheritedProperties(
    parentModelName: String,
    shapeIndex: ShapeIndex,
    rootLocation: String,
    localModels: LocalModelRegistry? = null,
    source: GeneratedSourceSpec? = null,
  ): List<GeneratedModelProperty> =
    when (val shape = shapeIndex.resolve(this).nonNullableType) {
      is NodeShape ->
        shape
          .takeUnless { it.hasDurableModelName() }
          ?.modelProperties(
            parentModelName = parentModelName,
            sourcePropertiesByName = mapOf(),
            shapeIndex = shapeIndex,
            rootLocation = rootLocation,
            localModels = localModels,
            source = source,
          ).orEmpty()

      is AnyShape ->
        shape.and
          .map(shapeIndex::resolve)
          .flatMap { inherited ->
            inherited.localInheritedProperties(
              parentModelName = parentModelName,
              shapeIndex = shapeIndex,
              rootLocation = rootLocation,
              localModels = localModels,
              source = source,
            )
          }

      else -> emptyList()
    }

  private fun NodeShape.modelProperties(
    parentModelName: String,
    sourcePropertiesByName: Map<String, PropertyShape>,
    shapeIndex: ShapeIndex,
    rootLocation: String,
    localModels: LocalModelRegistry? = null,
    source: GeneratedSourceSpec? = null,
  ): List<GeneratedModelProperty> =
    shapeIndex.findOrderedProperties(this).map { property ->
      val wireName = property.name ?: property.path().value() ?: ""
      val sourceProperty = sourcePropertiesByName[wireName]
      val exampleShape = sourceProperty?.range ?: property.range
      val generatedName = wireName.toLowerCamelCase()
      GeneratedModelProperty(
        name = generatedName,
        type =
          localModels?.declaredPropertyRef(
            property.range,
            parentModelName,
            generatedName,
            source,
          )
            ?: typeRef(
              shapeIndex.resolve(property.range),
              inlineShapeName = wireName,
              rootLocation = rootLocation,
              shapeIndex = shapeIndex,
            ),
        required = property.required,
        targets = property.range.targets(),
        serializationName = wireName.takeUnless { it == generatedName },
        externalDiscriminator =
          property.range
            .findStringAnnotation(APIAnnotationName.ExternalDiscriminator, null)
            ?.toLowerCamelCase(),
        validation = propertyValidation(property.range, shapeIndex),
        examples =
          (exampleShape as? AnyShape)
            ?.examples
            .orEmpty()
            .filterNot { example -> example.isPayloadSchemaExample }
            .examples(),
        readOnly = property.readOnly == true || property.range.readOnly == true,
        writeOnly = property.writeOnly == true || property.range.writeOnly == true,
        deprecated = property.deprecated == true || property.range.deprecated == true,
        documentation = documentation(description = property.description ?: property.range.description),
      )
    }

  private fun Shape.discriminatorValueFrom(inheritedShapes: List<Shape>): String? {
    val modelName = name ?: return null
    return inheritedShapes
      .filterIsInstance<NodeShape>()
      .firstNotNullOfOrNull { inheritedShape ->
        val mapping =
          inheritedShape
            .discriminatorMapping
            .orEmpty()
            .firstOrNull { mapping ->
              mapping.linkExpression?.substringAfterLast('/') == modelName ||
                mapping.linkExpression?.substringAfterLast('#')?.substringAfterLast('/') == modelName
            }

        mapping?.templateVariable
      }
  }

  private fun NodeShape.explicitDiscriminatorMappings(): Map<String, GeneratedTypeRef> =
    discriminatorMapping
      .orEmpty()
      .mapNotNull { mapping ->
        val discriminatorValue = mapping.templateVariable ?: return@mapNotNull null
        val modelName =
          mapping
            .linkExpression
            ?.substringAfterLast('/')
            ?.substringAfterLast('#')
            ?: return@mapNotNull null
        discriminatorValue to GeneratedTypeRef.named(modelName)
      }.toMap()

  private fun problems(
    document: BaseUnit,
    api: WebApi,
  ): List<GeneratedProblem> {
    val apiProblemBaseUri = problemBaseUri(api)
    val problemDefLocations: Map<BaseUnit, CustomizableElement> =
      document.allUnits.filterIsInstance<CustomizableElement>().associateBy { it as BaseUnit } +
        document.allUnits.filterIsInstance<EncodesModel>().associate { it as BaseUnit to it.encodes }

    return problemDefLocations
      .flatMap { (unit, element) ->
        val problemTypes =
          element.findAnnotation(APIAnnotationName.ProblemTypes, null) as? ObjectNode ?: return@flatMap listOf()
        val problemBaseUri = if (element == api) apiProblemBaseUri else problemBaseUri(element, apiProblemBaseUri)
        problemTypes.properties().orEmpty().map { (problemCode, problemDef) ->
          problem(problemCode, problemDef as ObjectNode, problemBaseUri, unit)
        }
      }.sortedBy { it.name }
  }

  private fun problemBaseUri(api: WebApi): URI {
    val baseUri = expandProblemUri(api.servers.firstOrNull()?.url ?: "", api.problemBaseUriParams())
    return problemBaseUri(api, baseUri)
  }

  private fun problemBaseUri(
    element: CustomizableElement,
    defaultBaseUri: URI,
  ): URI {

    var problemBaseUri =
      element
        .findStringAnnotation(APIAnnotationName.ProblemBaseUri, null)
        ?.let { template -> expandProblemUri(template, element.problemBaseUriParams()) }
        ?: defaultBaseUri
    if (!problemBaseUri.isAbsolute) {
      problemBaseUri = defaultBaseUri.resolve(problemBaseUri)
    }
    return problemBaseUri
  }

  private fun CustomizableElement.problemBaseUriParams(): Map<String, String> =
    findAnnotation(APIAnnotationName.ProblemBaseUriParams, null)
      ?.objectValue
      ?.mapValues { (_, value) -> value?.toString() ?: "" }
      .orEmpty()

  private fun expandProblemUri(
    template: String,
    params: Map<String, String>,
  ): URI =
    try {
      URI(UriTemplate.expand(template, params))
    } catch (_: URISyntaxException) {
      throw IllegalArgumentException(
        """
        Problem URI is not a valid URI; it cannot be a template.
        Use `problemBaseUri` and/or `problemBaseUriParams` to ensure it is valid.
        """.trimIndent(),
      )
    }

  private fun problem(
    problemCode: String,
    problem: ObjectNode,
    problemBaseUri: URI,
    definedIn: BaseUnit,
  ): GeneratedProblem {
    val status = problem.getValue("status")?.toIntOrNull()
    val title = problem.getValue("title")
    val detail = problem.getValue("detail")
    val fields = problemFields(problem.getProperty("custom").orElse(null) as? ObjectNode)
    val typeUri = problemBaseUri.resolve("./$problemCode").toString()
    return GeneratedProblem(
      name = problemName(problemCode),
      sourceName = problemCode,
      source =
        GeneratedSourceSpec(
          kind = sourceKind,
          location = definedIn.location,
        ),
      typeUri = typeUri,
      status = status,
      title = title,
      detail = detail,
      statusBindings =
        status
          ?.let {
            listOf(
              GeneratedProblemStatusBinding(
                status = it,
                typeUri = typeUri,
                title = title,
                detail = detail,
              ),
            )
          }.orEmpty(),
      payload =
        GeneratedProblemPayload(
          type = GeneratedTypeRef.named(problemName(problemCode)),
          mediaTypes = listOf(PROBLEM_JSON_MEDIA_TYPE),
          fields = problemPayloadFields(fields),
        ),
      fields = fields,
      documentation =
        documentation(
          summary = title,
          description = detail,
        ),
    )
  }

  private fun problemFields(custom: ObjectNode?): List<GeneratedModelProperty> =
    custom
      ?.properties()
      ?.map { (name, type) ->
        GeneratedModelProperty(
          name = name,
          type = typeRef(type),
        )
      }.orEmpty()

  private fun problemPayloadFields(customFields: List<GeneratedModelProperty>): List<GeneratedModelProperty> =
    listOf(
      GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
      GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string")),
    ) + customFields

  private fun List<Example>.examples(mediaType: String? = null): List<GeneratedExample> =
    mapNotNull { example -> example.example(mediaType) }

  private fun Payload.schemaExamples(mediaType: String? = null): List<GeneratedExample> =
    (schema() as? AnyShape)
      ?.examples
      .orEmpty()
      .filterNot { example -> example.isDeclaredExample || example.isInheritedShapeExample }
      .examples(mediaType)

  private fun Example.example(mediaType: String? = null): GeneratedExample? {
    val documentation = documentation(summary = displayName().value(), description = description)
    val value = structuredValue?.anyValue ?: value().value()
    return GeneratedExample(
      name = name,
      mediaType = this.mediaType ?: mediaType,
      value = value,
      strict = strict,
      documentation = documentation,
    ).takeUnless { example ->
      example.name == null &&
        example.mediaType == null &&
        example.value == null &&
        example.strict == null &&
        example.documentation == null
    }
  }

  private fun Tag.generatedTag(): GeneratedTag? {
    val tagName = name ?: return null
    return GeneratedTag(
      name = tagName,
      documentation = documentation(description = description),
    )
  }

  private val Example.isDeclaredExample: Boolean
    // AMF merges operation-local payload examples into referenced shapes; the id keeps their source context.
    get() = id().contains("#/declares/")

  private val Example.isInheritedShapeExample: Boolean
    // AMF 5.6 also exposes examples copied from referenced inherited shapes on operation payload schemas.
    get() = id().contains("/inherits/shape/")

  private val Example.isPayloadSchemaExample: Boolean
    // AMF 5.6 can expose operation payload-local examples on the referenced declaration shape.
    get() = id().contains("/shape/schema/examples/")

  private fun typeRef(value: DataNode): GeneratedTypeRef =
    when (val typeName = value.rawScalarValue) {
      "any" -> GeneratedTypeRef.scalar("any")
      "boolean" -> GeneratedTypeRef.scalar("boolean")
      "integer" -> GeneratedTypeRef.scalar("integer")
      "long" -> GeneratedTypeRef.scalar("long")
      "number" -> GeneratedTypeRef.scalar("number")
      "string" -> GeneratedTypeRef.scalar("string")
      "object" -> GeneratedTypeRef.scalar("object")
      "file" -> GeneratedTypeRef.scalar("file")
      null -> GeneratedTypeRef.scalar("any")
      else -> GeneratedTypeRef.named(typeName)
    }

  private fun Operation.policy(): GeneratedPolicy? =
    (findAnnotation(APIAnnotationName.Policy, null) as? ObjectNode)
      ?.let { policy ->
        GeneratedPolicy(
          timeout = policy.getValue("timeout"),
          retry = policy.stringMap("retry"),
          circuitBreaker = policy.stringMap("circuitBreaker"),
          clientRateLimit = policy.stringMap("clientRateLimit"),
          serverRateLimit = policy.stringMap("serverRateLimit"),
          source = policy.getValue("source"),
        )
      }?.takeUnless { it == GeneratedPolicy() }

  private fun CustomizableElement.zanzibar(): Map<String, String> =
    (findAnnotation(APIAnnotationName.Zanzibar, null) as? ObjectNode)?.zanzibarMap().orEmpty()

  private fun CustomizableElement.zanzibarUserSource(): GeneratedZanzibarUserSource? =
    (findAnnotation(APIAnnotationName.Zanzibar, null) as? ObjectNode)?.zanzibarUserSource()

  private fun ObjectNode.stringMap(propertyName: String): Map<String, String> =
    get<ObjectNode>(propertyName)?.stringMap().orEmpty()

  private fun ObjectNode.stringMap(): Map<String, String> =
    properties().mapValues { (_, value) ->
      value.rawScalarValue ?: value.anyValue?.toString() ?: ""
    }

  private fun ObjectNode.zanzibarMap(): Map<String, String> =
    properties()
      .filterKeys { key -> key !in listOf("user-source", "userSource") }
      .mapValues { (_, value) -> value.rawScalarValue ?: value.anyValue?.toString() ?: "" }

  private fun ObjectNode.zanzibarUserSource(): GeneratedZanzibarUserSource? {
    val userSource = objectValue("user-source") ?: objectValue("userSource") ?: return null
    val jwt = userSource.objectValue("jwt")?.zanzibarJwtUserSource()
    return GeneratedZanzibarUserSource(jwt = jwt).takeUnless { it == GeneratedZanzibarUserSource() }
  }

  private fun Map<*, *>.zanzibarJwtUserSource(): GeneratedZanzibarJwtUserSource? {
    val claims = (this["claims"] as? List<*>)?.mapNotNull { value -> value as? String }.orEmpty()
    val principalFallback =
      this["principal-fallback"] as? Boolean
        ?: this["principalFallback"] as? Boolean
        ?: false
    return GeneratedZanzibarJwtUserSource(claims = claims, principalFallback = principalFallback)
      .takeUnless { it == GeneratedZanzibarJwtUserSource() }
  }

  private fun ObjectNode.objectValue(name: String): Map<*, *>? = get<ObjectNode>(name)?.anyValue as? Map<*, *>

  private fun Map<*, *>.objectValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

  private fun typeRef(
    shape: Shape,
    inlineShapeName: String? = null,
    rootLocation: String? = null,
    localModels: LocalModelRegistry? = null,
    shapeIndex: ShapeIndex? = null,
  ): GeneratedTypeRef =
    when (shape) {
      is UnionShape ->
        if (shape.makesNullable) {
          typeRef(
            shape.nullableType,
            inlineShapeName = inlineShapeName,
            rootLocation = rootLocation,
            localModels = localModels,
          ).copy(nullable = true)
        } else {
          localModels?.refFor(shape)?.copy(nullable = false)
            ?: shape.name
              ?.takeIf { it !in syntheticShapeNames }
              ?.let { GeneratedTypeRef.named(it, source = shape.sourceSpec(rootLocation)) }
            ?: GeneratedTypeRef(
              kind = GeneratedTypeRef.Kind.UNION,
              name = "union",
              arguments =
                shape.anyOf().filterNot(::isNilShape).map {
                  typeRef(it, rootLocation = rootLocation, localModels = localModels)
                },
            )
        }

      is ScalarShape ->
        localModels?.refFor(shape)
          ?: if (shape.name != null && shape.name !in syntheticShapeNames && shape.values.isNotEmpty()) {
            GeneratedTypeRef.named(shape.name!!, source = shape.sourceSpec(rootLocation))
          } else {
            GeneratedTypeRef.scalar(shape.scalarName(), format = shape.format?.ifBlank { null })
          }

      is NodeShape ->
        localModels?.refFor(shape)
          ?: shape.implicitObjectTypeRef(shapeIndex, rootLocation, inlineShapeName)
          ?: shape.name
            ?.takeIf { name ->
              !shape.isInlineShapeName(inlineShapeName) &&
                name !in syntheticShapeNames &&
                (sourceKind != GeneratedSourceSpec.Kind.OPENAPI || shape.hasDurableModelName())
            }?.let { name ->
              GeneratedTypeRef.named(name, source = shape.sourceSpec(rootLocation))
            }
          ?: GeneratedTypeRef.scalar("object")

      is ArrayShape ->
        shape
          .explicitArrayName(inlineShapeName)
          ?.let { GeneratedTypeRef.named(it, source = shape.sourceSpec(rootLocation)) }
          ?: GeneratedTypeRef(
            kind = GeneratedTypeRef.Kind.ARRAY,
            name = "array",
            arguments =
              listOf(
                shape.items()?.let {
                  typeRef(it, rootLocation = rootLocation, localModels = localModels)
                } ?: GeneratedTypeRef.scalar("any"),
              ),
            collection = shape.collectionKind(),
          )

      is FileShape -> GeneratedTypeRef.scalar("file")

      is AnyShape ->
        shape
          .and
          .singleOrNull()
          ?.let { inherited ->
            typeRef(
              inherited,
              inlineShapeName = inlineShapeName,
              rootLocation = rootLocation,
              localModels = localModels,
            )
          }
          ?: localModels?.refFor(shape)
          ?: shape.name
            ?.takeIf {
              it.isNotBlank() &&
                it != inlineShapeName &&
                it.toLowerCamelCase() != inlineShapeName?.toLowerCamelCase() &&
                it !in syntheticShapeNames
            }?.let { GeneratedTypeRef.named(it, source = shape.sourceSpec(rootLocation)) }
          ?: GeneratedTypeRef.scalar("any")

      else ->
        shape.name?.let { GeneratedTypeRef.named(it, source = shape.sourceSpec(rootLocation)) }
          ?: GeneratedTypeRef.scalar("any")
    }

  private fun NodeShape.implicitObjectTypeRef(
    shapeIndex: ShapeIndex?,
    rootLocation: String?,
    inlineShapeName: String?,
  ): GeneratedTypeRef? {
    val hasDurableName = hasDurableModelName() && !isInlineShapeName(inlineShapeName)
    if (shapeIndex == null || hasDurableName) {
      return null
    }

    if (isMapLike(shapeIndex)) {
      return GeneratedTypeRef(
        kind = GeneratedTypeRef.Kind.MAP,
        name = "map",
        arguments = listOf(mapValueType(shapeIndex, rootLocation ?: "")),
      )
    }

    if (shapeIndex.findOrderedProperties(this).isEmpty()) {
      return GeneratedTypeRef.scalar("any")
    }

    return null
  }

  private fun NodeShape.isInlineShapeName(inlineShapeName: String?): Boolean =
    inlineShapeName != null &&
      name != null &&
      name == inlineShapeName

  private fun AnyShape.composedBranches(): List<Shape> = xone.ifEmpty { or }

  private fun isNilShape(shape: Shape): Boolean = shape is NilShape

  private fun discriminatorMappings(
    shape: NodeShape,
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): Map<String, GeneratedTypeRef> =
    shapeIndex
      .findInheriting(shape)
      .filterIsInstance<NodeShape>()
      .mapNotNull { inheriting ->
        val discriminatorValue = inheriting.discriminatorValue ?: return@mapNotNull null
        discriminatorValue to
          GeneratedTypeRef.named(
            inheriting.name ?: return@mapNotNull null,
            source = inheriting.sourceSpec(rootLocation),
          )
      }.toMap()

  private fun NodeShape.modelKind(shapeIndex: ShapeIndex): GeneratedModel.Kind =
    if (isMapLike(shapeIndex)) {
      GeneratedModel.Kind.MAP
    } else {
      GeneratedModel.Kind.OBJECT
    }

  private fun NodeShape.mapAliases(
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): List<GeneratedTypeRef> =
    if (isMapLike(shapeIndex)) {
      listOf(mapValueType(shapeIndex, rootLocation))
    } else {
      listOf()
    }

  private fun NodeShape.isMapLike(shapeIndex: ShapeIndex): Boolean =
    nonPatternProperties.isEmpty() &&
      (patternProperties.isNotEmpty() || additionalPropertiesSchema != null) &&
      shapeIndex.findInherited(this).isEmpty() &&
      shapeIndex.findInheriting(this).isEmpty()

  private fun NodeShape.mapValueType(
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): GeneratedTypeRef {
    additionalPropertiesSchema?.let { additionalProperties ->
      return typeRef(shapeIndex.resolve(additionalProperties), rootLocation = rootLocation)
    }

    val patternTypes =
      patternProperties
        .map { property -> typeRef(shapeIndex.resolve(property.range), rootLocation = rootLocation) }
        .distinct()

    return patternTypes.singleOrNull()
      ?: GeneratedTypeRef(
        kind = GeneratedTypeRef.Kind.UNION,
        name = "union",
        arguments = patternTypes,
      )
  }

  private fun NodeShape.generatedPatternProperties(
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): List<GeneratedPatternProperty> =
    patternProperties.mapNotNull { property ->
      val pattern = property.patternName ?: property.name ?: return@mapNotNull null
      GeneratedPatternProperty(
        pattern = pattern.removeSurrounding("/", "/"),
        type = typeRef(shapeIndex.resolve(property.range), rootLocation = rootLocation),
        validation = validation(property.range),
        documentation = documentation(description = property.description ?: property.range.description),
      )
    }

  private fun NodeShape.generatedAdditionalProperties(
    shapeIndex: ShapeIndex,
    rootLocation: String,
  ): GeneratedAdditionalProperties? {
    if (sourceKind != GeneratedSourceSpec.Kind.OPENAPI) {
      return null
    }

    if (closed == true) {
      return GeneratedAdditionalProperties(allowed = false)
    }

    val additionalProperties =
      additionalPropertiesSchema
        ?: return GeneratedAdditionalProperties(allowed = true).takeIf { nonPatternProperties.isEmpty() }

    return GeneratedAdditionalProperties(
      allowed = true,
      type = typeRef(shapeIndex.resolve(additionalProperties), rootLocation = rootLocation),
      validation = validation(additionalProperties),
      documentation = documentation(description = additionalProperties.description),
    )
  }

  private fun ArrayShape.collectionKind(): GeneratedCollectionKind? =
    if (uniqueItems == true) {
      GeneratedCollectionKind.SET
    } else {
      null
    }

  private fun BaseUnit.sourceSpec(rootLocation: String): GeneratedSourceSpec? =
    GeneratedSourceSpec(
      kind = sourceKind,
      location = location,
    ).takeUnless { source -> source.location == rootLocation }

  private fun GeneratedApi.withRootProblemReferenceSources(rootLocation: String): GeneratedApi {
    val rootSource = rootSourceSpec(rootLocation)
    val problemsByName =
      problems
        .flatMap { problem -> listOfNotNull(problem.name, problem.sourceName).map { name -> name to problem } }
        .groupBy({ (name) -> name }, { (_, problem) -> problem })
    return copy(
      services =
        services.map { service ->
          service.copy(
            operations =
              service.operations.map { operation ->
                operation.copy(
                  problems =
                    operation.problems.map { problem ->
                      problem.withProblemSource(problemsByName, rootSource)
                    },
                  nullify =
                    operation.nullify?.copy(
                      problems =
                        operation.nullify.problems.map { problem ->
                          problem.withProblemSource(problemsByName, rootSource)
                        },
                    ),
                )
              },
          )
        },
    )
  }

  private fun GeneratedTypeRef.withProblemSource(
    problemsByName: Map<String, List<GeneratedProblem>>,
    rootSource: GeneratedSourceSpec,
  ): GeneratedTypeRef {
    if (source != null) {
      return this
    }
    val candidates = problemsByName[name].orEmpty()
    val problem =
      candidates.firstOrNull { problem -> problem.source == rootSource }
        ?: candidates.singleOrNull()
        ?: return this
    return copy(source = problem.source ?: rootSource)
  }

  private fun rootSourceSpec(rootLocation: String): GeneratedSourceSpec =
    GeneratedSourceSpec(
      kind = sourceKind,
      location = rootLocation,
    )

  private fun Shape.sourceSpec(rootLocation: String?): GeneratedSourceSpec? =
    rootLocation
      ?.let {
        GeneratedSourceSpec(
          kind = sourceKind,
          location = annotations.location,
        ).takeUnless { source -> source.location == rootLocation }
      }

  private fun Shape.hasDurableModelName(): Boolean = name != null && isNameExplicit && name !in syntheticShapeNames

  private fun BaseUnit.targetDefaults(): Map<String, GeneratedTarget> =
    (this as? CustomizableElement)?.targets().orEmpty()

  private fun Map<String, GeneratedTarget>.mergeWith(
    overrides: Map<String, GeneratedTarget>,
  ): Map<String, GeneratedTarget> =
    (keys + overrides.keys)
      .associateWith { target -> (this[target] ?: GeneratedTarget()).mergeWith(overrides[target] ?: GeneratedTarget()) }
      .filterValues { target -> target != GeneratedTarget() }

  private fun GeneratedTarget.mergeWith(override: GeneratedTarget): GeneratedTarget =
    GeneratedTarget(
      packageName = override.packageName ?: packageName,
      modelPackageName = override.modelPackageName ?: modelPackageName,
      moduleName = override.moduleName ?: moduleName,
      modelModuleName = override.modelModuleName ?: modelModuleName,
      typeName = override.typeName ?: typeName,
      implementation = override.implementation ?: implementation,
    )

  private fun CustomizableElement.targets(): Map<String, GeneratedTarget> =
    linkedMapOf(
      TARGET_KOTLIN to
        GeneratedTarget(
          packageName = findStringAnnotation(APIAnnotationName.KotlinPkg, null),
          modelPackageName = findStringAnnotation(APIAnnotationName.KotlinModelPkg, null),
          typeName = findStringAnnotation(APIAnnotationName.KotlinType, null),
          implementation = implementation(APIAnnotationName.KotlinImpl, null),
        ),
      TARGET_KOTLIN_CLIENT to
        GeneratedTarget(
          packageName = findStringAnnotation(APIAnnotationName.KotlinPkg, GenerationMode.Client),
          modelPackageName = findStringAnnotation(APIAnnotationName.KotlinModelPkg, GenerationMode.Client),
          typeName = findStringAnnotation(APIAnnotationName.KotlinType, GenerationMode.Client),
          implementation = implementation(APIAnnotationName.KotlinImpl, GenerationMode.Client),
        ),
      TARGET_KOTLIN_SERVER to
        GeneratedTarget(
          packageName = findStringAnnotation(APIAnnotationName.KotlinPkg, GenerationMode.Server),
          modelPackageName = findStringAnnotation(APIAnnotationName.KotlinModelPkg, GenerationMode.Server),
          typeName = findStringAnnotation(APIAnnotationName.KotlinType, GenerationMode.Server),
          implementation = implementation(APIAnnotationName.KotlinImpl, GenerationMode.Server),
        ),
      TARGET_SWIFT to
        GeneratedTarget(
          moduleName = findStringAnnotation(APIAnnotationName.SwiftModule, null),
          modelModuleName = findStringAnnotation(APIAnnotationName.SwiftModelModule, null),
          typeName = findStringAnnotation(APIAnnotationName.SwiftType, null),
          implementation = implementation(APIAnnotationName.SwiftImpl, null),
        ),
      TARGET_TYPESCRIPT to
        GeneratedTarget(
          moduleName = findStringAnnotation(APIAnnotationName.TypeScriptModule, null),
          modelModuleName = findStringAnnotation(APIAnnotationName.TypeScriptModelModule, null),
          typeName = findStringAnnotation(APIAnnotationName.TypeScriptType, null),
          implementation = implementation(APIAnnotationName.TypeScriptImpl, null),
        ),
    ).filterValues { target -> target != GeneratedTarget() }

  private fun CustomizableElement.implementation(
    annotationName: APIAnnotationName,
    generationMode: GenerationMode?,
  ): GeneratedTargetImplementation? =
    (findAnnotation(annotationName, generationMode) as? ObjectNode)
      ?.let { implementation ->
        GeneratedTargetImplementation(
          code = implementation.getValue("code") ?: return@let null,
          parameters =
            implementation
              .get<ArrayNode>("parameters")
              ?.values<ObjectNode>()
              ?.mapNotNull { parameter ->
                val value = parameter.getValue("value") ?: return@mapNotNull null
                GeneratedTargetImplementationParameter(
                  type = parameter.getValue("type") ?: "Literal",
                  value = value,
                )
              }.orEmpty(),
        )
      }

  private fun Shape.nested(): GeneratedNestedType? =
    when (val nested = findAnnotation(APIAnnotationName.Nested, null)) {
      is ScalarNode ->
        if (nested.rawScalarValue == "dashed") {
          dashedNestedType()
        } else {
          null
        }

      is ObjectNode ->
        GeneratedNestedType(
          enclosedIn = nested.getValue("enclosedIn")?.let(GeneratedTypeRef::named),
          name = nested.getValue("name"),
        )

      else -> null
    }

  private fun Shape.dashedNestedType(): GeneratedNestedType {
    val parts = name.orEmpty().split("-")
    return GeneratedNestedType(
      strategy = GeneratedNestedType.Strategy.DASHED,
      enclosedIn =
        parts
          .takeIf { it.size > 1 }
          ?.dropLast(1)
          ?.joinToString("-")
          ?.let(GeneratedTypeRef::named),
      name = parts.takeIf { it.size > 1 }?.last(),
    )
  }

  private fun Shape.patchable(shapeIndex: ShapeIndex): Boolean =
    findBoolAnnotation(APIAnnotationName.Patchable, null) == true ||
      shapeIndex.findInherited(this).any { inherited -> inherited.patchable(shapeIndex) }

  private fun validation(shape: Shape): Map<String, String> =
    when (val constrained = shape.nonNullableType) {
      is ScalarShape -> scalarValidation(constrained)
      is ArrayShape -> arrayValidation(constrained)
      is NodeShape -> objectValidation(constrained)
      else -> mapOf()
    }

  private fun propertyValidation(
    shape: Shape,
    shapeIndex: ShapeIndex,
  ): Map<String, String> {
    val resolved = shapeIndex.resolve(shape)
    val validation = validation(resolved)
    if (validation.isNotEmpty()) {
      return validation
    }

    val itemShape = (resolved.nonNullableType as? ArrayShape)?.items()?.let(shapeIndex::resolve)
    return itemShape?.let(::validation).orEmpty()
  }

  private fun scalarValidation(shape: ScalarShape): Map<String, String> =
    linkedMapOf<String, String>().apply {
      shape.minLength?.takeUnless { it == 0 }?.let { put("minLength", it.toString()) }
      shape.maxLength?.takeUnless { it == Int.MAX_VALUE }?.let { put("maxLength", it.toString()) }
      shape.pattern?.takeUnless { it.isBlank() || it == ".*" }?.let { put("pattern", it) }
      shape.minimum?.let { put("minimum", it.formatConstraint()) }
      shape.maximum?.let { put("maximum", it.formatConstraint()) }
      shape.exclusiveMinimum?.takeIf { it }?.let { put("exclusiveMinimum", it.toString()) }
      shape.exclusiveMaximum?.takeIf { it }?.let { put("exclusiveMaximum", it.toString()) }
      shape.multipleOf?.let { put("multipleOf", it.formatConstraint()) }
    }

  private fun arrayValidation(shape: ArrayShape): Map<String, String> =
    linkedMapOf<String, String>().apply {
      shape.minItems?.takeUnless { it == 0 }?.let { put("minItems", it.toString()) }
      shape.maxItems?.takeUnless { it == Int.MAX_VALUE }?.let { put("maxItems", it.toString()) }
      shape.uniqueItems?.takeIf { it }?.let { put("uniqueItems", it.toString()) }
    }

  private fun objectValidation(shape: NodeShape): Map<String, String> =
    linkedMapOf<String, String>().apply {
      shape.minProperties?.takeUnless { it == 0 }?.let { put("minProperties", it.toString()) }
      shape.maxProperties?.takeUnless { it == Int.MAX_VALUE }?.let { put("maxProperties", it.toString()) }
    }

  private fun Double.formatConstraint(): String = BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

  private fun ArrayShape.explicitArrayName(inlineShapeName: String? = null): String? =
    name?.takeIf { isNameExplicit && it !in syntheticShapeNames && it != inlineShapeName }

  private fun documentation(
    summary: String? = null,
    description: String? = null,
  ): GeneratedDocumentation? =
    GeneratedDocumentation(
      summary = summary?.ifBlank { null },
      description = description?.ifBlank { null },
    ).takeUnless { it == GeneratedDocumentation() }

  private fun ScalarShape.scalarName(): String =
    when (dataType().value()) {
      DataTypes.String() -> "string"
      DataTypes.Boolean() -> "boolean"
      DataTypes.Integer() -> "integer"
      DataTypes.Long() -> "long"
      DataTypes.Float() -> "float"
      DataTypes.Double(), DataTypes.Number(), DataTypes.Decimal() -> "number"
      DataTypes.Date() -> "date"
      DataTypes.Time() -> "time"
      DataTypes.DateTimeOnly() -> "datetime-only"
      DataTypes.DateTime() -> "datetime"
      DataTypes.Binary() -> "file"
      else -> "any"
    }

  private fun media(
    contentTypes: List<String?>,
    acceptTypes: List<String?>,
  ): GeneratedMedia =
    GeneratedMedia(
      request = contentTypes.filterNotNull(),
      response = acceptTypes.filterNotNull().ifEmpty { contentTypes.filterNotNull() },
    )

  private fun securityRequirements(endPoint: EndPoint): List<SecurityRequirement> =
    endPoint.security + endPoint.operations().flatMap { it.security }

  private fun resolveSecurityRequirements(
    api: WebApi,
    endPoint: EndPoint,
    operation: Operation,
  ): List<SecurityRequirement> {
    if (sourceKind == GeneratedSourceSpec.Kind.OPENAPI) {
      return operation.security
        .ifEmpty { endPoint.security }
        .ifEmpty { api.security }
    }

    val requirements = mutableListOf<SecurityRequirement>()
    requirements.addAll(api.security)

    fun addEndpoint(current: EndPoint) {
      current.parent?.let(::addEndpoint)
      requirements.addAll(current.security)
    }

    addEndpoint(endPoint)
    requirements.addAll(operation.security)
    return requirements
  }

  private fun auth(
    securityRequirements: List<SecurityRequirement>,
    localModels: LocalModelRegistry,
    zanzibar: Map<String, String> = mapOf(),
    inheritedZanzibar: Map<String, String> = mapOf(),
    zanzibarUserSource: GeneratedZanzibarUserSource? = null,
    inheritedZanzibarUserSource: GeneratedZanzibarUserSource? = null,
  ): GeneratedAuth? {
    val requirements =
      securityRequirements
        .map { requirement -> requirement.securityRequirement() }
        .filter { requirement -> requirement.schemes.isNotEmpty() }

    val schemes =
      requirements
        .flatMap { requirement -> requirement.schemes }
        .ifEmpty { securityRequirements.mapNotNull { it.name().value() } }
        .distinct()

    val securitySchemes =
      securityRequirements
        .flatMap { requirement -> requirement.schemes().map { scheme -> scheme.scheme() } }
        .distinctBy { scheme -> scheme.name().value() }
        .mapNotNull { scheme -> scheme.securityScheme(localModels) }

    return GeneratedAuth(
      schemes = schemes,
      requirements = requirements,
      securitySchemes = securitySchemes,
      zanzibar = inheritedZanzibar + zanzibar,
      zanzibarUserSource = zanzibarUserSource ?: inheritedZanzibarUserSource,
    ).takeUnless { it == GeneratedAuth() }
  }

  private fun SecurityRequirement.securityRequirement(): GeneratedSecurityRequirement =
    GeneratedSecurityRequirement(
      schemes = schemes().mapNotNull { scheme -> scheme.scheme().name().value() },
    )

  private fun SecurityScheme.securityScheme(localModels: LocalModelRegistry): GeneratedSecurityScheme? {
    val schemeName = name().value()
    val settings = settings()
    val httpSettings = settings as? HttpSettings
    val apiKeyParameter = (settings as? ApiKeySettings)?.apiKeyParameter()
    val documentation = documentation(displayName().value(), description().value())
    return GeneratedSecurityScheme(
      name = schemeName,
      type = type().value(),
      scheme = httpSettings?.scheme()?.value(),
      bearerFormat = httpSettings?.bearerFormat()?.value(),
      headers =
        headers().orEmpty().map { parameter -> parameter.parameter(GeneratedParameter.Location.HEADER) } +
          listOfNotNull(
            apiKeyParameter?.takeIf { parameter ->
              parameter.location == GeneratedParameter.Location.HEADER
            },
          ),
      queryParameters =
        queryParameters().orEmpty().map { parameter -> parameter.parameter(GeneratedParameter.Location.QUERY) } +
          listOfNotNull(
            apiKeyParameter?.takeIf { parameter ->
              parameter.location == GeneratedParameter.Location.QUERY
            },
          ),
      cookieParameters =
        listOfNotNull(
          apiKeyParameter?.takeIf { parameter ->
            parameter.location == GeneratedParameter.Location.COOKIE
          },
        ),
      queryString = queryString?.let { localModels.securityQueryStringRef(it, schemeName) },
      documentation = documentation,
    ).takeUnless { scheme ->
      scheme.type == null &&
        scheme.scheme == null &&
        scheme.bearerFormat == null &&
        scheme.headers.isEmpty() &&
        scheme.queryParameters.isEmpty() &&
        scheme.cookieParameters.isEmpty() &&
        scheme.queryString == null &&
        scheme.documentation == null
    }
  }

  private fun ApiKeySettings.apiKeyParameter(): GeneratedParameter? {
    val wireName = name().value() ?: return null
    val location =
      when (`in`().value()?.lowercase()) {
        "header" -> GeneratedParameter.Location.HEADER
        "query" -> GeneratedParameter.Location.QUERY
        "cookie" -> GeneratedParameter.Location.COOKIE
        else -> return null
      }
    val generatedName = wireName.toLowerCamelCase()
    return GeneratedParameter(
      name = generatedName,
      location = location,
      type = GeneratedTypeRef.scalar("string"),
      required = true,
      serializationName = wireName.takeUnless { it == generatedName },
    )
  }

  private fun serviceName(apiName: String): String =
    apiName
      .removeSuffix(" API")
      .split(Regex("\\s+"))
      .joinToString("") { it.toUpperCamelCase() } + "Service"

  private fun WebApi.serviceEndpointGroups(): List<ServiceEndpointGroup> =
    endPoints
      .groupBy { endPoint -> serviceIdentitySeed(endPoint) }
      .map { (seed, endPoints) ->
        ServiceEndpointGroup(
          name = serviceName(seed.serviceLabel),
          identity = seed.identity,
          endPoints = endPoints,
        )
      }

  private fun Operation.excluded(): Boolean =
    findBoolAnnotation(APIAnnotationName.Exclude, options.generationMode) == true

  private fun WebApi.serviceIdentitySeed(endPoint: EndPoint): ServiceIdentitySeed {
    val explicitService = endPoint.root.findStringAnnotation(APIAnnotationName.Service, null)?.trimToNull()
    if (explicitService != null) {
      return ServiceIdentitySeed(explicitService, GeneratedIdentity.explicit(normalizedCompositionId(explicitService)))
    }

    val serviceGroup = endPoint.root.findStringAnnotation(APIAnnotationName.ServiceGroup, null)?.trimToNull()
    if (serviceGroup != null) {
      return ServiceIdentitySeed(serviceGroup, GeneratedIdentity.native(normalizedCompositionId(serviceGroup)))
    }

    if (options.deriveServicesFromTags) {
      val taggedService = endPoint.root.taggedServiceLabel()
      if (taggedService != null) {
        return ServiceIdentitySeed(taggedService, GeneratedIdentity.native(normalizedCompositionId(taggedService)))
      }
    }

    val serviceLabel =
      findStringAnnotation(APIAnnotationName.ServiceName, null)?.trimToNull()
        ?: name?.trimToNull()
        ?: "API"
    return ServiceIdentitySeed(
      serviceLabel,
      GeneratedIdentity.native(normalizedCompositionId(serviceLabel.withoutApiSuffix())),
    )
  }

  private fun WebApi.compositionApiIdentity(location: String): GeneratedIdentity =
    findStringAnnotation(APIAnnotationName.ApiId, null)
      ?.trimToNull()
      ?.let { GeneratedIdentity.explicit(it) }
      ?: compositionApiName()
        ?.let { GeneratedIdentity.native(normalizedCompositionId(it)) }
      ?: GeneratedIdentity.generated(normalizedCompositionId(location.substringAfterLast('/').substringBeforeLast('.')))

  private fun WebApi.compositionApiName(): String? {
    val apiName = name?.trimToNull() ?: return null
    val serviceName = findStringAnnotation(APIAnnotationName.ServiceName, null)?.trimToNull() ?: return apiName
    val serviceSuffix = Regex("\\s*[-–—:]\\s*${Regex.escape(serviceName)}\\s*$")
    return apiName.replace(serviceSuffix, "").trimToNull() ?: apiName
  }

  private fun GeneratedProblem.compositionProblemIdentity(): GeneratedIdentity =
    GeneratedIdentity.native(
      normalizedCompositionId(
        listOfNotNull(
          source?.location,
          sourceName ?: name,
        ).joinToString(":"),
      ),
    )

  private fun EndPoint.taggedServiceLabel(): String? {
    val tagNames =
      operations()
        .filterNot { operation -> operation.excluded() }
        .mapNotNull { operation -> operation.serviceTagName() }
        .distinct()

    require(tagNames.size <= 1) {
      "Endpoint '$path' has operations with different service tags (${tagNames.joinToString()}). " +
        "Add x-sunday-service to select one generated service explicitly."
    }

    return tagNames.singleOrNull()
  }

  private fun Operation.serviceTagName(): String? = tags.firstOrNull()?.name?.trimToNull()

  private fun Operation.generatedOperationId(path: String): String =
    findStringAnnotation(APIAnnotationName.OperationId, null)?.trimToNull()
      ?: operationName.trimToNull()
      ?: generatedOperationId(method, path)

  private fun generatedOperationId(
    method: String,
    path: String,
  ): String {
    val pathName =
      path
        .trim('/')
        .split("/")
        .filter { segment -> segment.isNotBlank() }
        .joinToString("") { segment ->
          segment
            .removeSurrounding("{", "}")
            .toUpperCamelCase()
        }
    return method.lowercase() + pathName.ifBlank { "Root" }
  }

  private fun Operation.compositionOperationIdentity(operationId: String): GeneratedIdentity =
    findStringAnnotation(APIAnnotationName.OperationId, null)
      ?.trimToNull()
      ?.let { GeneratedIdentity.explicit(it) }
      ?: GeneratedIdentity.native(normalizedCompositionId(operationId))

  private fun String.withoutApiSuffix(): String = replace(Regex("\\s+API$", RegexOption.IGNORE_CASE), "")

  private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

  private fun normalizedCompositionId(value: String): String {
    val parts =
      Regex("[A-Za-z0-9]+")
        .findAll(value)
        .map { match -> match.value }
        .toList()
    if (parts.isEmpty()) return "generated"
    return parts
      .mapIndexed { index, part ->
        val lower =
          if (part.all { char -> !char.isLetter() || char.isUpperCase() }) {
            part.lowercase()
          } else {
            part.replaceFirstChar { char -> char.lowercase() }
          }
        if (index == 0) {
          lower
        } else {
          lower.replaceFirstChar { char -> char.titlecase() }
        }
      }.joinToString("")
  }

  private fun problemName(problemCode: String): String = "${problemCode.toUpperCamelCase()}Problem"

  private data class ServiceIdentitySeed(
    val serviceLabel: String,
    val identity: GeneratedIdentity,
  )

  private data class ServiceEndpointGroup(
    val name: String,
    val identity: GeneratedIdentity,
    val endPoints: List<EndPoint>,
  )

  private data class ResolvedOperationKey(
    val path: String,
    val method: String,
  )

  private companion object {
    val constantParameterLocations = setOf(GeneratedParameter.Location.QUERY, GeneratedParameter.Location.HEADER)
    val syntheticShapeNames = setOf("default", "schema", "type")
    const val PROBLEM_JSON_MEDIA_TYPE = "application/problem+json"
    const val TARGET_KOTLIN = "kotlin"
    const val TARGET_KOTLIN_CLIENT = "kotlinClient"
    const val TARGET_KOTLIN_SERVER = "kotlinServer"
    const val TARGET_SWIFT = "swift"
    const val TARGET_TYPESCRIPT = "typescript"
  }
}
