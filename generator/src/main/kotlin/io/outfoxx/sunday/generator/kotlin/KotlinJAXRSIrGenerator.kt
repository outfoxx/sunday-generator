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

import com.damnhandy.uri.template.UriTemplate
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.GenerationMode.Server
import io.outfoxx.sunday.generator.common.HttpStatus
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedJaxrsRestClient
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.GeneratedZanzibarJwtUserSource
import io.outfoxx.sunday.generator.ir.GeneratedZanzibarUserSource
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter
import io.outfoxx.sunday.generator.ir.emit.contextParameters
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.ir.emit.enabledFor
import io.outfoxx.sunday.generator.ir.emit.flattenedUnionTypes
import io.outfoxx.sunday.generator.ir.emit.isAsynchronous
import io.outfoxx.sunday.generator.ir.emit.isNoContent
import io.outfoxx.sunday.generator.ir.emit.isReactive
import io.outfoxx.sunday.generator.ir.emit.jsonBodyEnabled
import io.outfoxx.sunday.generator.ir.emit.modelOrNull
import io.outfoxx.sunday.generator.ir.emit.operationParameterViews
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.ir.emit.primarySuccessResponse
import io.outfoxx.sunday.generator.ir.emit.problemOrNull
import io.outfoxx.sunday.generator.ir.emit.referencedProblems
import io.outfoxx.sunday.generator.ir.emit.resolvedTypeUri
import io.outfoxx.sunday.generator.ir.emit.sseEnabled
import io.outfoxx.sunday.generator.ir.emit.target
import io.outfoxx.sunday.generator.ir.emit.withLocation
import io.outfoxx.sunday.generator.ir.mergeWith
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ImplementModel
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.JacksonAnnotations
import io.outfoxx.sunday.generator.kotlin.KotlinTypeRegistry.Option.ValidationConstraints
import io.outfoxx.sunday.generator.kotlin.utils.BeanValidationTypes
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_DESERIALIZATION_CONTEXT
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_CREATOR
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZER
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZER_NONE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_IGNORE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_MAPPING_EXCEPTION
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_PARSER
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_SUBTYPES
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_SUBTYPES_TYPE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS_EXISTING_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_ID
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPEINFO_ID_NAME
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_TYPENAME
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_VALUE
import io.outfoxx.sunday.generator.kotlin.utils.JSON_NODE
import io.outfoxx.sunday.generator.kotlin.utils.JaxRsTypes
import io.outfoxx.sunday.generator.kotlin.utils.KotlinEnumEntriesResolver
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.MULTI
import io.outfoxx.sunday.generator.kotlin.utils.OBJECT_MAPPER
import io.outfoxx.sunday.generator.kotlin.utils.OPERATION_RESPONSE
import io.outfoxx.sunday.generator.kotlin.utils.QUARKUS_HTTP_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXOBSERVABLE3
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE2
import io.outfoxx.sunday.generator.kotlin.utils.RXSINGLE3
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_HTTP_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.UNI
import io.outfoxx.sunday.generator.kotlin.utils.VERTX_MUTINY_BUFFER
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_ABSTRACT_THROWABLE_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_EXCEPTIONAL
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_STATUS
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_THROWABLE_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.addAnnotation
import io.outfoxx.sunday.generator.kotlin.utils.addQuarkusHttpProblemAlias
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIntegerScalarTypeName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.kotlin.utils.rawType
import io.outfoxx.sunday.generator.utils.equalsInAnyOrder
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.net.URI
import java.security.Principal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage

/**
 * Kotlin/JAX-RS service generator backed by Sunday IR.
 */
class KotlinJAXRSIrGenerator(
  private val api: GeneratedApi,
  private val typeRegistry: KotlinTypeOutputRegistry,
  private val options: KotlinJAXRSOptions,
) {

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(options.defaultMediaTypes)
  private val apiIndex = GeneratedApiIndex(api)
  private val kotlinEnumEntries = KotlinEnumEntriesResolver()
  private val generationMode = typeRegistry.generationMode
  private val reactiveDefault = options.reactiveResponseType != null && !options.coroutineServiceMethods
  private val reactiveResponseType =
    if (options.quarkus) {
      UNI
    } else {
      options.reactiveResponseType?.let { ClassName.bestGuess(it) }
    }
  private val beanValidationTypes =
    if (typeRegistry.options.contains(KotlinTypeRegistry.Option.UseJakartaPackages)) {
      BeanValidationTypes.JAKARTA
    } else {
      BeanValidationTypes.JAVAX
    }
  private val jaxRsTypes: JaxRsTypes =
    if (options.quarkus) {
      JaxRsTypes.QUARKUS
    } else if (typeRegistry.options.contains(KotlinTypeRegistry.Option.UseJakartaPackages)) {
      JaxRsTypes.JAKARTA
    } else {
      JaxRsTypes.JAVAX
    }

  /**
   * Generates Kotlin/JAX-RS service types from IR and registers them in the type registry.
   */
  fun generateServiceTypes() {
    val services = api.jaxRsServices()

    generateModelTypes()
    generateProblemTypes(services)
    generateZanzibarUserExtractorType()

    val serviceTypes =
      services.map { service ->
        GeneratedJaxRsService(
          service = service,
          typeName = serviceTypeName(service),
          subresourcePath =
            if (options.aggregateServices && services.size > 1) {
              service.subresourcePath() ?: "/"
            } else {
              null
            },
        )
      }

    serviceTypes.forEach { service ->
      typeRegistry.addServiceType(
        service.typeName,
        service.service.serviceType(service.typeName, service.subresourcePath),
      )
    }

    if (options.aggregateServices && serviceTypes.size > 1) {
      val aggregateTypeName = aggregateServiceTypeName()
      if (serviceTypes.any { service -> service.typeName == aggregateTypeName }) {
        genError(
          "Cannot generate Kotlin/JAX-RS aggregate service '$aggregateTypeName' because it matches a generated service",
        )
      }
      typeRegistry.addServiceType(aggregateTypeName, generateAggregateServiceType(aggregateTypeName, serviceTypes))
    }
  }

  private fun generateModelTypes() {
    api.models
      .filter { model -> model.scope == null }
      .mapNotNull { model -> model.modelType()?.let { model.kotlinClassName() to it } }
      .forEach { (className, typeBuilder) -> typeRegistry.addModelType(className, typeBuilder) }
  }

  private fun GeneratedApi.jaxRsServices(): List<GeneratedService> =
    services
      .mapNotNull { service ->
        service
          .copy(operations = service.operations.filter { operation -> operation.isJaxRsOperation() })
          .takeIf { filtered -> filtered.operations.isNotEmpty() }
      }

  private fun GeneratedOperation.isJaxRsOperation(): Boolean =
    method.uppercase() !in asyncApiOperationMethods ||
      (path.startsWith("/") && !hasNonHttpProtocolBinding())

  private fun GeneratedOperation.hasNonHttpProtocolBinding(): Boolean =
    protocol
      ?.bindings
      .orEmpty()
      .any { binding -> !binding.protocol.isHttpProtocol() }

  private fun String.isHttpProtocol(): Boolean = equals("http", ignoreCase = true) || equals("https", ignoreCase = true)

  private fun generateProblemTypes(services: List<GeneratedService>) {
    val referencedProblems =
      services
        .flatMap { service -> service.referencedProblems(apiIndex) }
        .distinctBy { problem -> problem.typeUri }

    referencedProblems.forEach { problem ->
      typeRegistry.addModelType(problem.typeName(), problem.problemType())
    }
  }

  private fun generateZanzibarUserExtractorType() {
    if (!options.quarkus || generationMode != Server) {
      return
    }

    val userSource = api.zanzibarUserSourceOrNull() ?: return
    userSource.jwt?.let { jwtUserSource ->
      typeRegistry.addServiceType(zanzibarJwtUserExtractorTypeName(), jwtUserSource.zanzibarJwtUserExtractorType())
    }
  }

  private fun GeneratedApi.zanzibarUserSourceOrNull(): GeneratedZanzibarUserSource? {
    val sources =
      (
        listOfNotNull(auth?.zanzibarUserSource) +
          services.flatMap { service ->
            listOfNotNull(service.auth?.zanzibarUserSource) +
              service.operations.mapNotNull { operation -> operation.auth?.zanzibarUserSource }
          }
      ).distinct()

    if (sources.size > 1) {
      genError(
        "Cannot generate more than one Zanzibar user source: " +
          sources.joinToString("; ") { it.diagnosticDescription() },
      )
    }

    return sources.singleOrNull()
  }

  private fun GeneratedZanzibarUserSource.diagnosticDescription(): String =
    listOfNotNull(
      jwt?.let { jwt -> "jwt(${jwt.diagnosticDescription()})" },
    ).takeIf { it.isNotEmpty() }?.joinToString()
      ?: toString()

  private fun GeneratedZanzibarJwtUserSource.diagnosticDescription(): String =
    "claims=${claims.distinct()}, principalFallback=$principalFallback"

  private fun GeneratedZanzibarJwtUserSource.zanzibarJwtUserExtractorType(): TypeSpec.Builder {
    val principalParameterName = if (principalFallback) "principal" else "_principal"
    val claimExpressions =
      claims
        .distinct()
        .map { claim ->
          if (claim == "sub") {
            CodeBlock.of("jwt.subject")
          } else {
            CodeBlock.of("jwt.getClaim<String>(%S)", claim)
          }
        }
    val fallbackExpression =
      if (principalFallback) {
        CodeBlock.of("principal.name.takeIf·{·it.isNotBlank()·}")
      } else {
        CodeBlock.of("null")
      }
    val userIdExpression =
      CodeBlock
        .builder()
        .apply {
          if (claimExpressions.isEmpty()) {
            add("%L", fallbackExpression)
          } else {
            add("listOfNotNull(\n")
            indent()
            claimExpressions.forEach { claimExpression -> add("%L,\n", claimExpression) }
            unindent()
            add(").firstOrNull·{·it.isNotBlank()·}")
            if (principalFallback) {
              add("·?:·%L", fallbackExpression)
            }
          }
        }.build()

    val extractUser =
      FunSpec
        .builder("extractUser")
        .addModifiers(KModifier.OVERRIDE)
        .addParameter(principalParameterName, Principal::class.asTypeName())
        .addParameter("discoveredUserType", STRING.copy(nullable = true))
        .returns(OPTIONAL.parameterizedBy(fgaUser))
        .addCode(
          CodeBlock
            .builder()
            .add("val userId =·%L\n", userIdExpression)
            .add("val userType = discoveredUserType ?: return %T.empty()\n", OPTIONAL)
            .add("return userId?.let·{·%T.of(%T(userType,·it))·}·?:·%T.empty()\n", OPTIONAL, fgaUser, OPTIONAL)
            .build(),
        ).build()

    return TypeSpec
      .classBuilder(zanzibarJwtUserExtractorTypeName())
      .addAnnotation(applicationScopedAnnotation)
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter("jwt", jsonWebToken)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("jwt", jsonWebToken, KModifier.PRIVATE)
          .initializer("jwt")
          .build(),
      ).addSuperinterface(userExtractor)
      .addFunction(extractUser)
  }

  private fun zanzibarJwtUserExtractorTypeName(): ClassName {
    val servicePackageName =
      api.target(preferredTargetId, "kotlin")?.packageName
        ?: options.defaultServicePackageName
        ?: genError("No service package specified, one must be specified via options or in source IR")
    return ClassName.bestGuess("$servicePackageName.ZanzibarJwtUserExtractor")
  }

  private fun GeneratedService.serviceType(
    serviceTypeName: ClassName,
    subresourcePath: String? = null,
  ): TypeSpec.Builder {
    val typeBuilder = TypeSpec.interfaceBuilder(serviceTypeName)

    if (subresourcePath == null) {
      servicePath()
        ?.takeIf { path -> path.isNotEmpty() }
        ?.let { path -> typeBuilder.addAnnotation(jaxRsTypes.path, path) }
      typeBuilder.addQuarkusRestClientAnnotations(
        expandedBaseUri(),
        api.jaxrs?.restClient.mergeWith(jaxrs?.restClient),
      )
    }

    if (defaultMediaTypes.isNotEmpty()) {
      typeBuilder.addAnnotation(jaxRsTypes.produces, defaultMediaTypes)
      typeBuilder.addAnnotation(
        jaxRsTypes.consumes,
        if (generationMode == Client) defaultMediaTypes.take(1) else defaultMediaTypes,
      )
    }

    problemRegistrationType()?.let(typeBuilder::addType)

    operations.forEach { operation ->
      val renderedOperation = operation.withoutSubresourcePath(subresourcePath)
      val operationParameters = renderedOperation.operationParameters()
      renderedOperation.nullifyFunction(operationParameters)?.let(typeBuilder::addFunction)
      typeBuilder.addFunction(renderedOperation.operationFunction(this, operationParameters))
    }

    apiIndex
      .referencedScopedModels(this)
      .mapNotNull { model -> model.modelType() }
      .map { modelType -> modelType.build() }
      .forEach(typeBuilder::addType)

    return typeBuilder
  }

  private fun aggregateServiceTypeName(): ClassName {
    val servicePackageName =
      api.target(preferredTargetId, "kotlin")?.packageName
        ?: options.defaultServicePackageName
        ?: genError("No service package specified, one must be specified via options or in source IR")
    val serviceName = (options.aggregateServiceName ?: options.serviceSuffix).ifBlank { "API" }
    return ClassName.bestGuess("$servicePackageName.$serviceName")
  }

  private fun generateAggregateServiceType(
    aggregateTypeName: ClassName,
    services: List<GeneratedJaxRsService>,
  ): TypeSpec.Builder {
    val names = NameAllocator()
    val typeBuilder =
      TypeSpec
        .interfaceBuilder(aggregateTypeName)
        .addModifiers(KModifier.PUBLIC)

    typeBuilder.addAnnotation(jaxRsTypes.path, aggregateServicePath(services))
    typeBuilder.addQuarkusRestClientAnnotations(
      services.firstNotNullOfOrNull { service ->
        service.service.expandedBaseUri()
      },
      api.jaxrs?.restClient,
    )

    if (defaultMediaTypes.isNotEmpty()) {
      typeBuilder.addAnnotation(jaxRsTypes.produces, defaultMediaTypes)
      typeBuilder.addAnnotation(
        jaxRsTypes.consumes,
        if (generationMode == Client) defaultMediaTypes.take(1) else defaultMediaTypes,
      )
    }

    services
      .forEach { service ->
        val methodName = names.newName(service.service.aggregateMethodName(), service)
        typeBuilder.addFunction(service.subresourceLocator(methodName))
      }

    return typeBuilder
  }

  private fun aggregateServicePath(services: List<GeneratedJaxRsService>): String =
    services
      .mapNotNull { service -> service.service.servicePath() }
      .distinct()
      .singleOrNull()
      ?.takeIf { path -> path.isNotEmpty() }
      ?: "/"

  private fun GeneratedJaxRsService.subresourceLocator(methodName: String): FunSpec {
    val path =
      subresourcePath
        ?: genError("Cannot generate aggregate locator for service '${service.name}' without path")
    val names = NameAllocator()
    val functionBuilder =
      FunSpec
        .builder(methodName)
        .addModifiers(KModifier.ABSTRACT)
        .returns(typeName)

    if (path.isNotEmpty()) {
      functionBuilder.addAnnotation(jaxRsTypes.path, path)
    }

    service.subresourcePathParameters(path).forEach { parameter ->
      val proposedName = parameter.name.kotlinIdentifierName
      functionBuilder.addParameter(
        parameter.parameterSpec(
          names.newName(proposedName, parameter),
          GeneratedParameter.Location.PATH,
        ),
      )
    }

    return functionBuilder.build()
  }

  private fun GeneratedService.aggregateMethodName(): String =
    typeSimpleName()
      .removeSuffix(options.serviceSuffix)
      .ifBlank { name.removeSuffix("Service") }
      .toLowerCamelCase()
      .kotlinIdentifierName

  private fun GeneratedService.subresourcePath(): String? {
    val operationSegments = operations.map { operation -> operation.path.pathSegments() }.filter { it.isNotEmpty() }
    if (operationSegments.isEmpty()) {
      return null
    }

    val firstSegments = operationSegments.first()
    val commonSegments =
      firstSegments
        .indices
        .takeWhile { index -> operationSegments.all { segments -> segments.getOrNull(index) == firstSegments[index] } }
        .map { index -> firstSegments[index] }

    if (commonSegments.isEmpty()) {
      return null
    }

    return "/" + commonSegments.joinToString("/")
  }

  private fun GeneratedService.subresourcePathParameters(path: String): List<GeneratedParameter> {
    val parameterNames = path.pathParameterNames()
    if (parameterNames.isEmpty()) {
      return emptyList()
    }

    val pathParameters =
      operations
        .flatMap { operation -> operation.parameters }
        .filter { parameter -> parameter.location == GeneratedParameter.Location.PATH }
        .distinctBy { parameter -> parameter.serializationName ?: parameter.name }
        .associateBy { parameter -> parameter.serializationName ?: parameter.name }

    return parameterNames.map { name ->
      pathParameters[name]
        ?: genError("No path parameter '$name' found for aggregate service '${this.name}'")
    }
  }

  private fun GeneratedOperation.withoutSubresourcePath(subresourcePath: String?): GeneratedOperation {
    if (subresourcePath == null) {
      return this
    }

    val subresourceParameterNames = subresourcePath.pathParameterNames().toSet()
    val filteredParameters =
      parameters.filterNot { parameter ->
        parameter.location == GeneratedParameter.Location.PATH &&
          (parameter.serializationName ?: parameter.name) in subresourceParameterNames
      }

    return copy(
      path = path.relativeToSubresourcePath(subresourcePath),
      parameters = filteredParameters,
    )
  }

  private fun String.relativeToSubresourcePath(subresourcePath: String): String {
    val pathSegments = pathSegments()
    val subresourceSegments = subresourcePath.pathSegments()
    if (subresourceSegments.isEmpty() || !pathSegments.startsWithSegments(subresourceSegments)) {
      return this
    }

    val relativeSegments = pathSegments.drop(subresourceSegments.size)
    return if (relativeSegments.isEmpty()) "" else "/" + relativeSegments.joinToString("/")
  }

  private fun String.pathSegments(): List<String> =
    trim('/')
      .split("/")
      .filter { segment -> segment.isNotEmpty() }

  private fun String.pathParameterNames(): List<String> =
    pathSegments()
      .mapNotNull { segment ->
        segment
          .takeIf { it.startsWith("{") && it.endsWith("}") }
          ?.removePrefix("{")
          ?.removeSuffix("}")
      }.distinct()

  private fun List<String>.startsWithSegments(prefix: List<String>): Boolean =
    size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

  private fun GeneratedService.servicePath(): String? {
    val expandedBaseUri = expandedBaseUri() ?: return null
    val baseUriMode =
      options.baseUriMode ?: if (generationMode == Client && !options.quarkus) {
        KotlinJAXRSOptions.BaseUriMode.FULL
      } else {
        KotlinJAXRSOptions.BaseUriMode.PATH_ONLY
      }

    return when (baseUriMode) {
      KotlinJAXRSOptions.BaseUriMode.FULL -> expandedBaseUri
      KotlinJAXRSOptions.BaseUriMode.PATH_ONLY ->
        try {
          URI(expandedBaseUri).path
        } catch (_: Throwable) {
          expandedBaseUri.replace("//", "").dropWhile { it != '/' }
        }
    }
  }

  private fun GeneratedService.problemRegistrationType(): TypeSpec? {
    val referencedProblems = referencedProblems(apiIndex)
    if (!typeRegistry.options.contains(JacksonAnnotations) || referencedProblems.isEmpty()) {
      return null
    }

    return TypeSpec
      .companionObjectBuilder()
      .addFunction(
        FunSpec
          .builder("registerProblems")
          .addParameter("mapper", OBJECT_MAPPER)
          .addCode(
            "mapper.registerSubtypes(⇥\n" +
              referencedProblems.joinToString(",\n") { "%T::class.java" } +
              "⇤\n)",
            *referencedProblems.map { problem -> problem.typeName() }.toTypedArray(),
          ).build(),
      ).build()
  }

  private fun GeneratedOperation.operationParameters(): List<GeneratedOperationParameter> {
    val names = NameAllocator()
    return operationParameterViews(
      identifierName = { parameter -> parameter.name.kotlinIdentifierName },
      allocateName = { parameter, proposedName -> names.newName(proposedName, parameter) },
    )
  }

  private fun GeneratedOperation.operationFunction(
    service: GeneratedService,
    operationParameters: List<GeneratedOperationParameter>,
  ): FunSpec {
    val functionBuilder =
      FunSpec
        .builder(id.kotlinIdentifierName)
        .addModifiers(KModifier.ABSTRACT)

    val httpMethodAnnClass =
      jaxRsTypes.httpMethod(jaxrsHttpMethod())
        ?: genError("Unsupported HTTP method '$method'")
    functionBuilder.addAnnotation(httpMethodAnnClass)
    if (path.isNotEmpty()) {
      functionBuilder.addAnnotation(jaxRsTypes.path, path)
    }
    addQuarkusFaultToleranceAnnotations(functionBuilder)
    addQuarkusZanzibarAnnotations(service, functionBuilder)

    val names = NameAllocator()

    if (options.explicitSecurityParameters) {
      api
        .effectiveAuth(service, this)
        ?.securitySchemes
        .orEmpty()
        .forEach { scheme ->
          scheme.explicitSecurityParameters(names).forEach(functionBuilder::addParameter)
        }
    }

    operationParameters
      .withLocation(GeneratedParameter.Location.PATH)
      .forEach { parameter ->
        functionBuilder.addParameter(parameter.source.parameterSpec(parameter.name, GeneratedParameter.Location.PATH))
      }

    operationParameters
      .withLocation(GeneratedParameter.Location.QUERY)
      .forEach { parameter ->
        functionBuilder.addParameter(parameter.source.parameterSpec(parameter.name, GeneratedParameter.Location.QUERY))
      }

    operationParameters
      .withLocation(GeneratedParameter.Location.HEADER)
      .forEach { parameter ->
        parameter.source.headerParameterSpecOrNull(parameter.name, functionBuilder)?.let(functionBuilder::addParameter)
      }

    requestBody?.let { requestBody ->
      if (requestBody.mediaTypes.isOverridingDefaultMediaTypes() && !operationParameters.hasClientContentTypeHeader()) {
        functionBuilder.addAnnotation(jaxRsTypes.consumes, requestBody.mediaTypes)
      }
      functionBuilder.addParameter(requestBody.bodyParameterSpec(jaxrs?.jsonBodyEnabled(generationMode) == true))
    }

    val response = primarySuccessResponse()
    val responseTypeName = operationReturnTypeName(response, functionBuilder)
    if (responseTypeName != UNIT) {
      functionBuilder.returns(responseTypeName)
    }

    if (generationMode == Server && jaxrs?.isAsynchronous == true) {
      functionBuilder.addParameter(
        ParameterSpec
          .builder("asyncResponse", jaxRsTypes.asyncResponse)
          .addAnnotation(jaxRsTypes.suspended)
          .build(),
      )
    }

    if (generationMode == Server && needsSseContextParameters()) {
      functionBuilder.addParameter(
        ParameterSpec
          .builder("sse", jaxRsTypes.sse)
          .addAnnotation(jaxRsTypes.context)
          .build(),
      )
      functionBuilder.addParameter(
        ParameterSpec
          .builder("sseEvents", jaxRsTypes.sseEventSink)
          .addAnnotation(jaxRsTypes.context)
          .build(),
      )
    }

    if (generationMode == Server && response?.status == 201) {
      functionBuilder.addParameter(
        ParameterSpec
          .builder("uriInfo", jaxRsTypes.uriInfo)
          .addAnnotation(jaxRsTypes.context)
          .build(),
      )
    }

    addContextParameters(functionBuilder)

    return functionBuilder.build()
  }

  private fun GeneratedOperation.jaxrsHttpMethod(): String =
    when {
      method.equals("SUBSCRIBE", ignoreCase = true) &&
        streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM -> "GET"
      method.equals("PUBLISH", ignoreCase = true) -> "POST"
      else -> method
    }

  private fun GeneratedOperation.addContextParameters(functionBuilder: FunSpec.Builder) {
    val contextParameters = jaxrs?.contextParameters.orEmpty()
    if (contextParameters.isEmpty()) {
      return
    }

    val existingParameterTypes = functionBuilder.parameters.map { parameter -> parameter.type }
    val existingParameterNames = functionBuilder.parameters.map { parameter -> parameter.name }.toMutableSet()

    contextParameters.forEach { contextParameter ->
      val contextType =
        jaxRsTypes.contextType(contextParameter)
          ?: genError("Unsupported JAX-RS context type '$contextParameter'")

      if (!existingParameterTypes.contains(contextType)) {
        val contextParameterName = uniqueContextParameterName(contextParameter, existingParameterNames)
        functionBuilder.addParameter(
          ParameterSpec
            .builder(contextParameterName, contextType)
            .addAnnotation(jaxRsTypes.context)
            .build(),
        )
      }
    }
  }

  private fun GeneratedOperation.addQuarkusFaultToleranceAnnotations(functionBuilder: FunSpec.Builder) {
    if (!options.quarkus) {
      return
    }

    val effectivePolicy = policy ?: return

    effectivePolicy.timeout
      ?.durationAnnotation(timeoutAnnotation, "value", "unit")
      ?.let(functionBuilder::addAnnotation)

    effectivePolicy.retry.retryAnnotation()?.let(functionBuilder::addAnnotation)
    effectivePolicy.circuitBreaker.circuitBreakerAnnotation()?.let(functionBuilder::addAnnotation)

    val rateLimit =
      when (generationMode) {
        Client -> effectivePolicy.clientRateLimit
        Server -> effectivePolicy.serverRateLimit
      }
    rateLimit.rateLimitAnnotation()?.let(functionBuilder::addAnnotation)
  }

  private fun GeneratedOperation.addQuarkusZanzibarAnnotations(
    service: GeneratedService,
    functionBuilder: FunSpec.Builder,
  ) {
    if (!options.quarkus || generationMode != Server) {
      return
    }

    val zanzibar = api.effectiveAuth(service, this)?.zanzibar.orEmpty()
    if (zanzibar.isEmpty()) {
      return
    }

    if (zanzibar.booleanValue("ignore", "ignored")) {
      functionBuilder.addAnnotation(fgaIgnoreAnnotation)
      return
    }

    zanzibar.objectAnnotation()?.let(functionBuilder::addAnnotation)
    zanzibar.relationAnnotation()?.let(functionBuilder::addAnnotation)
    zanzibar["userType"]?.let { userType ->
      functionBuilder.addAnnotation(
        AnnotationSpec
          .builder(fgaUserTypeAnnotation)
          .addMember("%S", userType)
          .build(),
      )
    }
  }

  private fun Map<String, String>.objectAnnotation(): AnnotationSpec? {
    val type = value("objectType", "resourceType") ?: return null
    return value("objectId", "resourceId")
      ?.let { id ->
        AnnotationSpec
          .builder(fgaObjectAnnotation)
          .addMember("id = %S", id)
          .addMember("type = %S", type)
          .build()
      }
      ?: value("pathParam", "pathParameter", "objectIdPathParam", "resourceIdPathParam", "param")
        ?.let { parameter ->
          AnnotationSpec
            .builder(fgaPathObjectAnnotation)
            .addMember("param = %S", parameter)
            .addMember("type = %S", type)
            .build()
        }
      ?: value("queryParam", "queryParameter", "objectIdQueryParam", "resourceIdQueryParam")
        ?.let { parameter ->
          AnnotationSpec
            .builder(fgaQueryObjectAnnotation)
            .addMember("param = %S", parameter)
            .addMember("type = %S", type)
            .build()
        }
      ?: value("header", "headerName", "objectIdHeader", "resourceIdHeader")
        ?.let { header ->
          AnnotationSpec
            .builder(fgaHeaderObjectAnnotation)
            .addMember("name = %S", header)
            .addMember("type = %S", type)
            .build()
        }
      ?: value("requestProperty", "property", "objectIdRequestProperty", "resourceIdRequestProperty")
        ?.let { property ->
          AnnotationSpec
            .builder(fgaRequestObjectAnnotation)
            .addMember("property = %S", property)
            .addMember("type = %S", type)
            .build()
        }
      ?: genError(
        "Zanzibar object type '$type' requires one of objectId, pathParam, queryParam, header, or requestProperty",
      )
  }

  private fun Map<String, String>.relationAnnotation(): AnnotationSpec? {
    val relation = value("relation", "permission") ?: return null
    val relationMember =
      if (relation.equals("any", ignoreCase = true) || relation == "*") {
        CodeBlock.of("%T.ANY", fgaRelationAnnotation)
      } else {
        CodeBlock.of("%S", relation)
      }
    return AnnotationSpec
      .builder(fgaRelationAnnotation)
      .addMember("%L", relationMember)
      .build()
  }

  private fun Map<String, String>.value(vararg names: String): String? =
    names.firstNotNullOfOrNull { name -> this[name]?.takeIf { it.isNotBlank() } }

  private fun Map<String, String>.booleanValue(vararg names: String): Boolean =
    value(*names)?.toBooleanStrictOrNull() == true

  private fun String.durationAnnotation(
    annotation: ClassName,
    valueMember: String,
    unitMember: String,
  ): AnnotationSpec =
    runCatching { toPolicyDuration() }
      .getOrElse {
        genError(
          "Quarkus timeout policy key '$valueMember' must be an ISO-8601 duration " +
            "(e.g. \"PT5S\") or a PT{n}MS milliseconds literal (e.g. \"PT100MS\")",
        )
      }.let { duration ->
        AnnotationSpec
          .builder(annotation)
          .addMember("$valueMember = %L", duration.value)
          .addMember("$unitMember = %T.%L", CHRONO_UNIT, duration.unit.name)
          .build()
      }

  private fun Map<String, String>.retryAnnotation(): AnnotationSpec? {
    if (isEmpty()) {
      return null
    }
    validatePolicyKeys("retry", retryPolicyKeys)

    return AnnotationSpec
      .builder(retryAnnotation)
      .apply {
        intValue("retry", "maxRetries")?.let { addMember("maxRetries = %L", it) }
        durationValue("retry", "delay")?.let { addDurationMembers("delay", "delayUnit", it) }
        durationValue("retry", "maxDuration")?.let { addDurationMembers("maxDuration", "durationUnit", it) }
        durationValue("retry", "jitter")?.let { addDurationMembers("jitter", "jitterDelayUnit", it) }
      }.build()
  }

  private fun Map<String, String>.circuitBreakerAnnotation(): AnnotationSpec? {
    if (isEmpty()) {
      return null
    }
    validatePolicyKeys("circuitBreaker", circuitBreakerPolicyKeys)

    return AnnotationSpec
      .builder(circuitBreakerAnnotation)
      .apply {
        intValue("circuitBreaker", "requestVolumeThreshold")?.let { addMember("requestVolumeThreshold = %L", it) }
        intValue("circuitBreaker", "successThreshold")?.let { addMember("successThreshold = %L", it) }
        doubleValue("circuitBreaker", "failureRatio")?.let { addMember("failureRatio = %L", it) }
        durationValue("circuitBreaker", "delay")?.let { addDurationMembers("delay", "delayUnit", it) }
      }.build()
  }

  private fun Map<String, String>.rateLimitAnnotation(): AnnotationSpec? {
    if (isEmpty()) {
      return null
    }
    validatePolicyKeys("rateLimit", rateLimitPolicyKeys)
    val value = intValue("rateLimit", "value") ?: genError("Quarkus rateLimit policy requires integer key 'value'")

    return AnnotationSpec
      .builder(rateLimitAnnotation)
      .apply {
        addMember("value = %L", value)
        durationValue("rateLimit", "window")?.let { addDurationMembers("window", "windowUnit", it) }
        durationValue("rateLimit", "minSpacing")?.let { addDurationMembers("minSpacing", "minSpacingUnit", it) }
        this@rateLimitAnnotation["type"]?.let { type ->
          if (type.enumMemberName() !in rateLimitTypes) {
            genError("Quarkus rateLimit policy key 'type' must be one of ${rateLimitTypes.joinToString()}")
          }
          addMember("type = %T.%L", rateLimitType, type.enumMemberName())
        }
      }.build()
  }

  private fun AnnotationSpec.Builder.addDurationMembers(
    valueMember: String,
    unitMember: String,
    duration: PolicyDuration,
  ) {
    addMember("$valueMember = %L", duration.value)
    addMember("$unitMember = %T.%L", CHRONO_UNIT, duration.unit.name)
  }

  private fun Map<String, String>.validatePolicyKeys(
    policyName: String,
    supportedKeys: Set<String>,
  ) {
    val unsupportedKeys = keys - supportedKeys
    if (unsupportedKeys.isNotEmpty()) {
      genError("Unsupported Quarkus $policyName policy key(s): ${unsupportedKeys.sorted().joinToString(", ")}")
    }
  }

  private fun Map<String, String>.intValue(
    policyName: String,
    name: String,
  ): Int? =
    this[name]?.let { value ->
      value.toIntOrNull()
        ?: genError("Quarkus $policyName policy key '$name' must be an integer")
    }

  private fun Map<String, String>.doubleValue(
    policyName: String,
    name: String,
  ): Double? =
    this[name]?.let { value ->
      value.toDoubleOrNull()
        ?: genError("Quarkus $policyName policy key '$name' must be a number")
    }

  private fun Map<String, String>.durationValue(
    policyName: String,
    name: String,
  ): PolicyDuration? =
    this[name]?.let { value ->
      runCatching { value.toPolicyDuration() }
        .getOrElse {
          genError(
            "Quarkus $policyName policy key '$name' must be an ISO-8601 duration " +
              "(e.g. \"PT5S\") or a PT{n}MS milliseconds literal (e.g. \"PT100MS\")",
          )
        }
    }

  private fun String.toPolicyDuration(): PolicyDuration {
    val duration = parsePolicyDuration()
    val millis = duration.toMillis()
    return when {
      millis % ChronoUnit.HOURS.duration.toMillis() == 0L ->
        PolicyDuration(
          millis / ChronoUnit.HOURS.duration.toMillis(),
          ChronoUnit.HOURS,
        )
      millis % ChronoUnit.MINUTES.duration.toMillis() == 0L ->
        PolicyDuration(
          millis / ChronoUnit.MINUTES.duration.toMillis(),
          ChronoUnit.MINUTES,
        )
      millis % ChronoUnit.SECONDS.duration.toMillis() == 0L ->
        PolicyDuration(
          millis / ChronoUnit.SECONDS.duration.toMillis(),
          ChronoUnit.SECONDS,
        )
      else -> PolicyDuration(millis, ChronoUnit.MILLIS)
    }
  }

  private fun String.parsePolicyDuration(): Duration =
    runCatching { Duration.parse(this) }
      .getOrElse {
        policyMillisRegex
          .matchEntire(this)
          ?.groupValues
          ?.get(1)
          ?.toLongOrNull()
          ?.let(Duration::ofMillis)
          ?: throw it
      }

  private fun String.enumMemberName(): String = replace(enumMemberSplitRegex, "_").uppercase()

  private fun GeneratedService.expandedBaseUri(): String? {
    val baseUri = baseUri ?: return null
    val parameterValues =
      baseUriParameters.associate { parameter ->
        val wireName = parameter.serializationName ?: parameter.name
        val defaultValue = parameter.defaultValue?.toString() ?: "{$wireName}"
        wireName to defaultValue
      }
    return UriTemplate.buildFromTemplate(baseUri).build().expand(parameterValues)
  }

  private fun TypeSpec.Builder.addQuarkusRestClientAnnotations(
    baseUri: String?,
    restClient: GeneratedJaxrsRestClient?,
  ) {
    if (generationMode != Client || !options.quarkus) {
      return
    }

    val annotation = jaxRsTypes.registerRestClient ?: return
    addAnnotation(
      AnnotationSpec
        .builder(annotation)
        .apply {
          restClient?.configKey?.let { value -> addMember("configKey = %S", value) }
          baseUri?.let { value -> addMember("baseUri = %S", value) }
        }.build(),
    )
    restClient?.oidcClient?.let { clientName ->
      jaxRsTypes.oidcClientFilter?.let { oidcClientFilter ->
        addAnnotation(
          AnnotationSpec
            .builder(oidcClientFilter)
            .addMember("%S", clientName)
            .build(),
        )
      }
    }
    restClient
      ?.providers
      .orEmpty()
      .map { provider -> provider.providerClassName() }
      .forEach { provider ->
        jaxRsTypes.registerProvider?.let { registerProvider ->
          addAnnotation(
            AnnotationSpec
              .builder(registerProvider)
              .addMember("%T::class", provider)
              .build(),
          )
        }
      }
  }

  private fun String.providerClassName(): ClassName =
    try {
      ClassName.bestGuess(this)
    } catch (error: IllegalArgumentException) {
      genError("Invalid provider class name '$this' in REST client metadata: ${error.message}")
    }

  private fun uniqueContextParameterName(
    contextParameter: String,
    existingParameterNames: MutableSet<String>,
  ): String {
    var name = contextParameter
    var index = 1
    while (!existingParameterNames.add(name)) {
      name = "$contextParameter$index"
      index += 1
    }
    return name
  }

  private fun GeneratedPayload.bodyParameterSpec(jsonBodyEnabled: Boolean): ParameterSpec {
    val typeName = bodyTypeName(jsonBodyEnabled, includeValidationAnnotations = true)
    val builder = ParameterSpec.builder("body", typeName)

    if (typeName != JSON_NODE && !isQuarkusStreamingRequestBody) {
      builder.addValidationAnnotations(
        GeneratedParameter(
          name = "body",
          location = GeneratedParameter.Location.BODY,
          type = type,
        ),
        typeName,
      )
    }

    return builder.build()
  }

  private fun GeneratedPayload.bodyTypeName(
    jsonBodyEnabled: Boolean,
    includeValidationAnnotations: Boolean,
  ): TypeName =
    when {
      // Streaming request bodies are raw transport streams; they intentionally bypass JSON body overrides.
      isQuarkusStreamingRequestBody -> MULTI.parameterizedBy(VERTX_MUTINY_BUFFER)
      jsonBodyEnabled -> JSON_NODE
      includeValidationAnnotations -> type.kotlinTypeName().withUseSiteValidationAnnotations(type)
      else -> type.kotlinTypeName()
    }

  private val GeneratedPayload.isQuarkusStreamingRequestBody: Boolean
    get() = options.quarkus && streaming?.enabledFor(generationMode) == true

  private fun GeneratedOperation.nullifyFunction(operationParameters: List<GeneratedOperationParameter>): FunSpec? {
    if (generationMode != Client || options.alwaysUseResponseReturn) {
      return null
    }

    val nullify = nullify ?: return null
    val responseTypeName = nullifyReturnTypeName(primarySuccessResponse())
    if (responseTypeName == UNIT) {
      return null
    }

    val functionParameters =
      operationParameters
        .filterNot { parameter -> parameter.isConstant }
        .map { parameter -> parameter.nullifyParameterSpec() } +
        listOfNotNull(
          requestBody?.let { requestBody ->
            val typeName =
              requestBody.bodyTypeName(
                jaxrs?.jsonBodyEnabled(generationMode) == true,
                includeValidationAnnotations = false,
              )
            ParameterSpec.builder("body", typeName).build()
          },
        )
    val problemTypeNames =
      nullify.problems
        .mapNotNull { problem -> problem.problemOrNull(apiIndex) }
        .filter { problem -> problems.any { referenced -> referenced.name == problem.name } }
        .map { problem -> problem.typeName() }
        .toSet()

    val functionBuilder =
      FunSpec
        .builder("${id.kotlinIdentifierName}OrNull")
        .addParameters(functionParameters)
        .returns(responseTypeName.nullableNullifyReturnType())
        .addCode(
          if (supportedReactiveReturnTypes.contains(responseTypeName.rawType.copy(nullable = false))) {
            reactiveNullifyCode(
              id.kotlinIdentifierName,
              functionParameters,
              responseTypeName,
              nullify.statuses.toSet(),
              problemTypeNames,
            )
          } else {
            nullifyCode(id.kotlinIdentifierName, functionParameters, nullify.statuses.toSet(), problemTypeNames)
          },
        )

    if (options.coroutineServiceMethods && streaming?.kind != GeneratedStreaming.Kind.EVENT_STREAM) {
      functionBuilder.addModifiers(KModifier.SUSPEND)
    }

    return functionBuilder.build()
  }

  private fun GeneratedOperationParameter.nullifyParameterSpec(): ParameterSpec =
    ParameterSpec
      .builder(name, type.kotlinTypeName().copy(nullable = isNullable))
      .build()

  private fun nullifyCode(
    functionName: String,
    functionParameters: List<ParameterSpec>,
    statuses: Set<Int>,
    problemTypeNames: Set<TypeName>,
  ): CodeBlock {
    val builder =
      CodeBlock
        .builder()
        .beginControlFlow("return try")
        .addStatement("%L(%L)", functionName, functionParameters.joinToString { parameter -> parameter.name })

    val throwableType = typeRegistry.problemLibrarySupport.throwableType
    val statusAccess = typeRegistry.problemLibrarySupport.statusCodeAccess("x")

    problemTypeNames.forEach { problemTypeName ->
      builder
        .nextControlFlow("catch(_: %T)", problemTypeName)
        .addStatement("null")
    }

    if (statuses.isNotEmpty()) {
      builder.nextControlFlow("catch(x: %T)", throwableType)

      if (statuses.size == 1) {
        builder
          .beginControlFlow("if ($statusAccess == %L)", statuses.first())
          .addStatement("null")
          .nextControlFlow("else")
          .addStatement("throw x")
          .endControlFlow()
      } else {
        builder
          .add("when ($statusAccess) {")
          .indent()
          .add("\n")
          .add("%L -> null\n", statuses.joinToString(", "))
          .add("else -> throw x")
          .unindent()
          .add("\n")
          .add("}")
          .add("\n")
      }
    }

    builder.endControlFlow()

    return builder.build()
  }

  private fun reactiveNullifyCode(
    functionName: String,
    functionParameters: List<ParameterSpec>,
    responseTypeName: TypeName,
    statuses: Set<Int>,
    problemTypeNames: Set<TypeName>,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    val throwableType = typeRegistry.problemLibrarySupport.throwableType
    val statusAccess = typeRegistry.problemLibrarySupport.statusCodeAccess("x")

    builder
      .add("return %L(%L)", functionName, functionParameters.joinToString { parameter -> parameter.name })
      .indent()
      .add("\n")

    val nullLiteral =
      when (responseTypeName.rawType.copy(nullable = false)) {
        CompletionStage::class.asTypeName() -> {
          builder.add(".exceptionally { x ->").indent().add("\n")
          "null"
        }

        UNI -> {
          builder.add(".onFailure().recoverWithItem { x ->").indent().add("\n")
          "null"
        }

        RXSINGLE3, RXOBSERVABLE3, RXSINGLE2, RXOBSERVABLE2 -> {
          builder
            .add(".map { %T.of(it) }", Optional::class.asTypeName())
            .add("\n.onErrorReturn { x ->")
            .indent()
            .add("\n")

          "Optional.empty()"
        }

        else -> genError("Unsupported reactive return type for nullify")
      }

    builder.add("when {").indent().add("\n")

    problemTypeNames.forEach { problemTypeName ->
      builder.addStatement("x is %T -> %L", problemTypeName, nullLiteral)
    }

    if (statuses.isNotEmpty()) {
      builder.addStatement(
        "x is %T && ${if (statuses.size == 1) "%L" else "(%L)"} -> %L",
        throwableType,
        statuses.joinToString(" || ") { status -> "$statusAccess == $status" },
        nullLiteral,
      )
    }

    builder
      .add("else -> throw x")
      .unindent()
      .add("\n")
      .add("}")
      .unindent()
      .add("\n")
      .add("}")
      .unindent()
      .add("\n")

    return builder.build()
  }

  private fun GeneratedOperation.nullifyReturnTypeName(response: GeneratedResponse?): TypeName {
    if (response == null) {
      return UNIT
    }
    val successType = response.takeUnless { it.isNoContent }?.type?.kotlinTypeName() ?: UNIT
    val reactive = jaxrs?.isReactive ?: reactiveDefault
    if (reactive && reactiveResponseType != null) {
      return if (response.hasHeaders) {
        reactiveResponseType.parameterizedBy(jaxRsTypes.responseType(successType))
      } else {
        reactiveResponseType.parameterizedBy(successType)
      }
    }
    return returnTypeName(response)
  }

  private fun TypeName.nullableNullifyReturnType(): TypeName {
    val returnType = copy(nullable = false)
    return when {
      returnType.rawType == OPERATION_RESPONSE ->
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
  }

  private val rxReturnTypes: List<TypeName> =
    listOf(
      RXSINGLE2,
      RXSINGLE3,
      RXOBSERVABLE2,
      RXOBSERVABLE3,
    )

  private val supportedReactiveReturnTypes =
    listOf(
      CompletionStage::class.asTypeName(),
      UNI,
    ) + rxReturnTypes

  private fun List<String>.isOverridingDefaultMediaTypes(): Boolean =
    isNotEmpty() && !equalsInAnyOrder(defaultMediaTypes)

  private fun Iterable<GeneratedOperationParameter>.hasClientContentTypeHeader(): Boolean =
    generationMode == Client &&
      any { parameter ->
        parameter.location == GeneratedParameter.Location.HEADER &&
          parameter.wireName.equals("Content-Type", ignoreCase = true)
      }

  private fun GeneratedSecurityScheme.explicitSecurityParameters(names: NameAllocator): List<ParameterSpec> =
    headers.map { header ->
      val wireName = header.serializationName ?: header.name
      val name =
        names.newName(
          name.kotlinIdentifierName + wireName.kotlinTypeName,
          header,
        )
      header.parameterSpec(name, GeneratedParameter.Location.HEADER, requireName = true)
    } +
      queryParameters.map { queryParameter ->
        val wireName = queryParameter.serializationName ?: queryParameter.name
        val name =
          names.newName(
            name.kotlinIdentifierName + wireName.kotlinTypeName,
            queryParameter,
          )
        queryParameter.parameterSpec(name, GeneratedParameter.Location.QUERY, annotate = false)
      }

  private fun GeneratedParameter.headerParameterSpecOrNull(
    name: String,
    functionBuilder: FunSpec.Builder,
  ): ParameterSpec? {
    val wireName = serializationName ?: this.name
    if (generationMode == Server && wireName.equals("Content-Type", ignoreCase = true)) {
      return null
    }

    if (constantValue != null) {
      val clientHeaderParam = jaxRsTypes.clientHeaderParam
      if (generationMode == Client && clientHeaderParam != null) {
        functionBuilder.addAnnotation(
          AnnotationSpec
            .builder(clientHeaderParam)
            .addMember("name = %S", wireName)
            .addMember("value = %S", constantValue.toString())
            .build(),
        )
        return null
      } else if (generationMode == Server) {
        return null
      }
    }

    return parameterSpec(name, GeneratedParameter.Location.HEADER, requireName = true)
  }

  private fun GeneratedParameter.parameterSpec(
    name: String,
    location: GeneratedParameter.Location,
    requireName: Boolean = false,
    annotate: Boolean = true,
  ): ParameterSpec {
    val typeName =
      type
        .kotlinTypeName()
        .copy(nullable = defaultValue == null && (type.nullable || !required))
    val builder = ParameterSpec.builder(name, typeName)

    if (annotate) {
      builder.addJaxRsParameterAnnotation(location, serializationName ?: this.name, requireName)
    }

    builder.addValidationAnnotations(this, typeName)

    defaultValue?.let { defaultValue ->
      builder.addAnnotation(
        AnnotationSpec
          .builder(jaxRsTypes.defaultValue)
          .addMember("value = %S", defaultValue.defaultValueString())
          .build(),
      )
    }

    return builder.build()
  }

  private fun ParameterSpec.Builder.addJaxRsParameterAnnotation(
    location: GeneratedParameter.Location,
    wireName: String,
    requireName: Boolean,
  ): ParameterSpec.Builder {
    val paramType =
      when (location) {
        GeneratedParameter.Location.PATH -> JaxRsTypes.ParamType.PATH
        GeneratedParameter.Location.QUERY -> JaxRsTypes.ParamType.QUERY
        GeneratedParameter.Location.HEADER -> JaxRsTypes.ParamType.HEADER
        GeneratedParameter.Location.COOKIE -> JaxRsTypes.ParamType.COOKIE
        else -> return this
      }

    return addAnnotation(
      AnnotationSpec
        .builder(jaxRsTypes.paramAnnotation(paramType))
        .apply {
          if (requireName || jaxRsTypes.isNameRequiredForParameters) {
            addMember("value = %S", wireName)
          }
        }.build(),
    )
  }

  private fun ParameterSpec.Builder.addValidationAnnotations(
    parameter: GeneratedParameter,
    typeName: TypeName,
  ): ParameterSpec.Builder {
    if (!typeRegistry.options.contains(ValidationConstraints)) {
      return this
    }

    if (parameter.requiresNotNullValidation()) {
      addAnnotation(beanValidationTypes.notNull)
    }

    val model = parameter.type.modelOrNull(apiIndex)
    if (model?.kind == GeneratedModel.Kind.OBJECT && typeName != ANY) {
      addAnnotation(beanValidationTypes.valid)
    }

    parameter.validation.validationAnnotations(parameter.type).forEach(::addAnnotation)

    return this
  }

  private fun GeneratedParameter.requiresNotNullValidation(): Boolean =
    required &&
      defaultValue == null &&
      !type.nullable &&
      location != GeneratedParameter.Location.PATH

  private fun TypeName.withUseSiteValidationAnnotations(type: GeneratedTypeRef): TypeName {
    if (!typeRegistry.options.contains(ValidationConstraints) || this !is ParameterizedTypeName) {
      return this
    }

    val model = type.modelOrNull(apiIndex)
    val typeRefArguments =
      when {
        type.arguments.isNotEmpty() -> type.arguments
        model?.kind == GeneratedModel.Kind.ARRAY -> model.aliases
        model?.kind == GeneratedModel.Kind.MAP -> model.aliases
        else -> emptyList()
      }
    if (typeRefArguments.isEmpty()) {
      return this
    }

    val argumentTypes =
      typeArguments.zip(typeRefArguments).map { (argumentTypeName, argumentType) ->
        val argumentModel = argumentType.modelOrNull(apiIndex)
        if (argumentModel?.kind == GeneratedModel.Kind.OBJECT) {
          argumentTypeName.copy(
            annotations =
              argumentTypeName.annotations +
                AnnotationSpec
                  .builder(beanValidationTypes.valid)
                  .build(),
          )
        } else {
          argumentTypeName.withUseSiteValidationAnnotations(argumentType)
        }
      }

    return rawType.parameterizedBy(argumentTypes)
  }

  private fun Map<String, String>.validationAnnotations(
    type: GeneratedTypeRef,
    useSiteTarget: AnnotationSpec.UseSiteTarget? = null,
  ): List<AnnotationSpec> =
    buildList {
      val sizeBuilder = sizeAnnotationBuilder()
      if (sizeBuilder != null) {
        add(sizeBuilder.withUseSiteTarget(useSiteTarget).build())
      }

      if (type.format.equals("email", ignoreCase = true)) {
        add(AnnotationSpec.builder(beanValidationTypes.email).withUseSiteTarget(useSiteTarget).build())
      }

      this@validationAnnotations["pattern"]?.let { pattern ->
        add(
          AnnotationSpec
            .builder(beanValidationTypes.pattern)
            .withUseSiteTarget(useSiteTarget)
            .addMember("regexp = %P", pattern)
            .build(),
        )
      }

      if (type.name in listOf("integer", "int32", "int64", "long")) {
        this@validationAnnotations["maximum"]?.let { maximum ->
          add(
            AnnotationSpec
              .builder(beanValidationTypes.max)
              .withUseSiteTarget(useSiteTarget)
              .addMember("value = %L", maximum)
              .build(),
          )
        }
        this@validationAnnotations["minimum"]?.let { minimum ->
          add(
            AnnotationSpec
              .builder(beanValidationTypes.min)
              .withUseSiteTarget(useSiteTarget)
              .addMember("value = %L", minimum)
              .build(),
          )
        }
      } else if (type.name in listOf("number", "double")) {
        this@validationAnnotations["maximum"]?.let { maximum ->
          add(
            AnnotationSpec
              .builder(beanValidationTypes.decimalMax)
              .withUseSiteTarget(useSiteTarget)
              .addMember("value = %S", maximum)
              .build(),
          )
        }
        this@validationAnnotations["minimum"]?.let { minimum ->
          add(
            AnnotationSpec
              .builder(beanValidationTypes.decimalMin)
              .withUseSiteTarget(useSiteTarget)
              .addMember("value = %S", minimum)
              .build(),
          )
        }
      }
    }

  private fun AnnotationSpec.Builder.withUseSiteTarget(
    useSiteTarget: AnnotationSpec.UseSiteTarget?,
  ): AnnotationSpec.Builder =
    apply {
      if (useSiteTarget != null) {
        useSiteTarget(useSiteTarget)
      }
    }

  private fun Map<String, String>.sizeAnnotationBuilder(): AnnotationSpec.Builder? {
    var sizeBuilder: AnnotationSpec.Builder? = null
    this["maxLength"]?.let { maxLength ->
      sizeBuilder = AnnotationSpec.builder(beanValidationTypes.size).addMember("max = %L", maxLength)
    }
    this["minLength"]?.let { minLength ->
      sizeBuilder = (sizeBuilder ?: AnnotationSpec.builder(beanValidationTypes.size)).addMember("min = %L", minLength)
    }
    this["maxItems"]?.let { maxItems ->
      sizeBuilder = AnnotationSpec.builder(beanValidationTypes.size).addMember("max = %L", maxItems)
    }
    this["minItems"]?.let { minItems ->
      sizeBuilder = (sizeBuilder ?: AnnotationSpec.builder(beanValidationTypes.size)).addMember("min = %L", minItems)
    }
    return sizeBuilder
  }

  private fun GeneratedOperation.returnTypeName(response: GeneratedResponse?): TypeName {
    if (response == null) {
      return UNIT
    }
    val successType = response.takeUnless { it.isNoContent }?.type?.kotlinTypeName() ?: UNIT
    return if (generationMode == Server || options.alwaysUseResponseReturn || response.hasHeaders) {
      jaxRsTypes.responseType(successType)
    } else {
      successType
    }
  }

  private fun GeneratedOperation.operationReturnTypeName(
    response: GeneratedResponse?,
    functionBuilder: FunSpec.Builder,
  ): TypeName {
    val successType = response?.takeUnless { it.isNoContent }?.type?.kotlinTypeName() ?: UNIT
    val isSse = jaxrs?.sseEnabled(generationMode) == true
    val isEventStream = streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM
    val isQuarkusEventStream = options.quarkus && isEventStream
    val isStandardJaxRsEventStream = !options.quarkus && isEventStream

    if (
      (isEventStream && options.coroutineFlowMethods && !isQuarkusEventStream && !isStandardJaxRsEventStream) ||
      (!isEventStream && options.coroutineServiceMethods)
    ) {
      functionBuilder.addModifiers(KModifier.SUSPEND)
    }

    val reactive = jaxrs?.isReactive ?: reactiveDefault
    if (reactive && reactiveResponseType != null && !isSse && !isEventStream) {
      return if (generationMode == Server || options.alwaysUseResponseReturn || response.hasHeaders) {
        reactiveResponseType.parameterizedBy(jaxRsTypes.responseType(successType))
      } else {
        reactiveResponseType.parameterizedBy(successType)
      }
    }

    if (jaxrs?.isAsynchronous == true) {
      return UNIT
    }

    if (isSse && !isEventStream) {
      functionBuilder.addSseElementTypeAnnotation(response?.mediaTypes.orEmpty())
      return if (generationMode == Client) {
        jaxRsTypes.sseEventSource
      } else {
        UNIT
      }
    }

    if (isEventStream) {
      functionBuilder.addSseElementTypeAnnotation(response?.mediaTypes.orEmpty())
      if (isStandardJaxRsEventStream) {
        return if (generationMode == Client) {
          jaxRsTypes.sseEventSource
        } else {
          UNIT
        }
      }
      return MULTI.parameterizedBy(eventStreamElementType(response?.type, successType, isSse))
    }

    if (response?.mediaTypes.orEmpty().isOverridingDefaultMediaTypes()) {
      functionBuilder.addAnnotation(jaxRsTypes.produces, response?.mediaTypes.orEmpty())
    }

    return returnTypeName(response)
  }

  private fun FunSpec.Builder.addSseElementTypeAnnotation(mediaTypes: List<String>) {
    val elementTypes = mediaTypes.ifEmpty { defaultMediaTypes }
    if (elementTypes.size > 1) {
      genError("Multiple media types not supported for Server-Sent Events")
    }

    addAnnotation(jaxRsTypes.produces, listOf(SSE_CONTENT_TYPE))
    jaxRsTypes.sseElementType?.let { annotation ->
      elementTypes
        .firstOrNull { mediaType -> mediaType != SSE_CONTENT_TYPE }
        ?.let { elementType -> addAnnotation(annotation, elementType) }
    }
  }

  private fun GeneratedOperation.eventStreamElementType(
    type: GeneratedTypeRef?,
    successType: TypeName,
    isSse: Boolean,
  ): TypeName =
    when (streaming?.eventMode) {
      GeneratedStreaming.EventMode.SIMPLE ->
        when {
          isSse && generationMode == Client -> jaxRsTypes.sseInboundEvent
          isSse && generationMode == Server -> jaxRsTypes.sseOutboundEvent
          else -> successType
        }

      GeneratedStreaming.EventMode.DISCRIMINATED -> type?.eventStreamCommonTypeName() ?: successType
      null -> successType
    }

  private fun GeneratedTypeRef.eventStreamCommonTypeName(): TypeName {
    val eventTypes = flattenedUnionTypes().filter { eventType -> eventType.kind == GeneratedTypeRef.Kind.NAMED }
    val commonBase =
      eventTypes
        .mapNotNull { eventType -> eventType.modelOrNull(apiIndex)?.inherits?.firstOrNull() }
        .takeIf { inheritedTypes -> inheritedTypes.size == eventTypes.size }
        ?.distinctBy { inheritedType -> inheritedType.name to inheritedType.scope }
        ?.singleOrNull()

    return commonBase?.kotlinTypeName() ?: kotlinTypeName()
  }

  private fun GeneratedOperation.needsSseContextParameters(): Boolean =
    (jaxrs?.sseEnabled(generationMode) == true && !options.coroutineServiceMethods) ||
      (streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM && !options.quarkus)

  private val GeneratedResponse?.hasHeaders: Boolean
    get() = this?.headers?.isNotEmpty() == true

  private fun GeneratedService.typeSimpleName(): String {
    val defaultUngroupedName =
      api.name
        .removeSuffix(" API")
        .split(Regex("\\s+"))
        .joinToString("") { it.toUpperCamelCase() } + "Service"
    val servicePrefix =
      when {
        group != null -> group
        name == defaultUngroupedName -> ""
        name.endsWith("Service") -> name.removeSuffix("Service")
        else -> name
      }

    return "${servicePrefix.kotlinTypeName}${options.serviceSuffix}"
  }

  private fun GeneratedModel.modelType(): TypeSpec.Builder? =
    when (kind) {
      GeneratedModel.Kind.ENUM -> {
        val entries = kotlinEnumEntries.entries(this)
        TypeSpec
          .enumBuilder(kotlinClassName())
          .addModifiers(KModifier.PUBLIC)
          .primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addParameter("wireValue", STRING)
              .build(),
          ).addProperty(
            PropertySpec
              .builder("wireValue", STRING, KModifier.PRIVATE)
              .initializer("wireValue")
              .build(),
          ).addFunction(
            FunSpec
              .builder("toString")
              .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
              .returns(STRING)
              .apply {
                if (typeRegistry.options.contains(JacksonAnnotations)) {
                  addAnnotation(JACKSON_JSON_VALUE)
                }
              }.addStatement("return wireValue")
              .build(),
          ).apply {
            if (typeRegistry.options.contains(JacksonAnnotations)) {
              addType(jsonCreatorCompanionType())
            }
            entries.forEach { entry ->
              addEnumConstant(
                entry.name,
                TypeSpec
                  .anonymousClassBuilder()
                  .addSuperclassConstructorParameter("%S", entry.value)
                  .build(),
              )
            }
          }
      }

      GeneratedModel.Kind.OBJECT ->
        objectTypeSpec()

      GeneratedModel.Kind.UNION ->
        objectUnionTypeSpecOrNull()

      else -> null
    }

  private fun GeneratedModel.objectTypeSpec(): TypeSpec.Builder {
    val inheritedProperties = inheritedModelProperties()
    val localProperties = localModelProperties(inheritedProperties)

    if (typeRegistry.options.contains(ImplementModel) || isProblemModel()) {
      return classTypeSpec(inheritedProperties, localProperties)
    }

    return TypeSpec
      .interfaceBuilder(kotlinClassName())
      .addModifiers(KModifier.PUBLIC)
      .apply {
        addJacksonPolymorphism(this@objectTypeSpec)
        inherits.forEach { inherited ->
          addSuperinterface(inherited.kotlinTypeName())
        }
        directUnionSupertypes().forEach { union ->
          addSuperinterface(union.kotlinClassName())
        }
        localProperties.forEach { property ->
          addProperty(property.propertySpec())
        }
      }
  }

  private fun GeneratedModel.classTypeSpec(
    inheritedProperties: List<GeneratedModelProperty>,
    localProperties: List<GeneratedModelProperty>,
  ): TypeSpec.Builder {
    val isProblemRoot = isProblemRootModel()
    val constructor =
      FunSpec
        .constructorBuilder()
        .apply {
          inheritedProperties.forEach { property ->
            addParameter(property.constructorParameterSpec())
          }
          localProperties.forEach { property ->
            addParameter(property.constructorParameterSpec())
          }
        }.build()

    return TypeSpec
      .classBuilder(kotlinClassName())
      .addModifiers(KModifier.PUBLIC)
      .primaryConstructor(constructor)
      .apply {
        addJacksonPolymorphism(this@classTypeSpec)
        addJacksonUnionMemberDeserializerOverride(this@classTypeSpec)
        if (hasInheritors) {
          addModifiers(KModifier.OPEN)
        }
        if (isProblemRoot) {
          configureSourceProblemRootModel(this@classTypeSpec, inheritedProperties + localProperties)
        } else {
          inherits.firstOrNull()?.let { inherited ->
            superclass(inherited.kotlinTypeName())
            inheritedProperties.forEach { property ->
              addSuperclassConstructorParameter("%N", property.name.kotlinIdentifierName)
            }
          }
        }
        directUnionSupertypes().forEach { union ->
          addSuperinterface(union.kotlinClassName())
        }
        localProperties
          .filterNot { property -> isProblemRoot && property.isBaseProblemProperty() }
          .forEach { property ->
            val propertyBuilder =
              PropertySpec
                .builder(property.name.kotlinIdentifierName, property.modelPropertyTypeName())
                .addAnnotations(property.jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.GET))
                .addAnnotations(
                  property.validation.validationAnnotations(
                    property.type,
                    AnnotationSpec.UseSiteTarget.GET,
                  ),
                )
            addProperty(
              propertyBuilder
                .initializer(property.name.kotlinIdentifierName)
                .build(),
            )
          }
      }
  }

  private fun GeneratedModel.isProblemRootModel(): Boolean = inheritanceRootModel() == this && isProblemShape()

  private fun GeneratedModel.isProblemModel(): Boolean = inheritanceRootModel().isProblemShape()

  private fun GeneratedModel.inheritanceRootModel(): GeneratedModel {
    val parent = inherits.firstOrNull()?.modelOrNull(apiIndex)
    return parent?.inheritanceRootModel() ?: this
  }

  private fun GeneratedModel.isProblemShape(): Boolean =
    name.endsWith("Problem") &&
      properties.any { property -> property.name == "type" } &&
      properties.any { property -> property.name == "title" } &&
      properties.any { property -> property.name == "status" }

  private fun GeneratedModelProperty.isBaseProblemProperty(): Boolean =
    serializationName == null && name in baseProblemProperties

  private fun TypeSpec.Builder.configureSourceProblemRootModel(
    model: GeneratedModel,
    properties: List<GeneratedModelProperty>,
  ) {
    when (typeRegistry.problemLibrary) {
      KotlinProblemLibrary.QUARKUS -> {
        superclass(QUARKUS_HTTP_PROBLEM)
        addQuarkusHttpProblemAlias()
        addSuperclassConstructorParameter("%L", model.quarkusProblemBuilderCode(properties))
      }

      KotlinProblemLibrary.SUNDAY -> configureSundaySourceProblemRootModel(properties)
      KotlinProblemLibrary.ZALANDO -> configureZalandoSourceProblemRootModel(properties)
    }
  }

  private fun TypeSpec.Builder.configureSundaySourceProblemRootModel(properties: List<GeneratedModelProperty>) {
    superclass(SUNDAY_HTTP_PROBLEM)
    addSuperclassConstructorParameter(
      "%L",
      sourceProblemBaseArgument(properties, "type", "%T.create(%S)", URI::class, "about:blank"),
    )
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "title", "%L", "null"))
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "status", "%L", "null"))
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "detail", "%L", "null"))
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "instance", "%L", "null"))
  }

  private fun TypeSpec.Builder.configureZalandoSourceProblemRootModel(properties: List<GeneratedModelProperty>) {
    superclass(ZALANDO_ABSTRACT_THROWABLE_PROBLEM)
    addSuperclassConstructorParameter(
      "%L",
      sourceProblemBaseArgument(properties, "type", "%T.create(%S)", URI::class, "about:blank"),
    )
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "title", "%L", "null"))
    addSuperclassConstructorParameter("%L", sourceProblemStatusArgument(properties))
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "detail", "%L", "null"))
    addSuperclassConstructorParameter("%L", sourceProblemBaseArgument(properties, "instance", "%L", "null"))
    addSuperclassConstructorParameter("null")
    addFunction(
      FunSpec
        .builder("getCause")
        .addAnnotation(JACKSON_JSON_IGNORE)
        .returns(ZALANDO_EXCEPTIONAL.copy(nullable = true))
        .addModifiers(KModifier.OVERRIDE)
        .addCode("return super.cause")
        .build(),
    )
  }

  private fun sourceProblemStatusArgument(properties: List<GeneratedModelProperty>): CodeBlock {
    val property =
      properties.firstOrNull { candidate -> candidate.name == "status" }
        ?: return CodeBlock.of("null")
    val name = property.name.kotlinIdentifierName
    return if (property.modelPropertyTypeName().isNullable) {
      CodeBlock.of("%N?.let { %T.valueOf(it) }", name, ZALANDO_STATUS)
    } else {
      CodeBlock.of("%T.valueOf(%N)", ZALANDO_STATUS, name)
    }
  }

  private fun sourceProblemBaseArgument(
    properties: List<GeneratedModelProperty>,
    propertyName: String,
    defaultFormat: String,
    vararg defaultArguments: Any,
  ): CodeBlock {
    val property =
      properties.firstOrNull { candidate -> candidate.name == propertyName }
        ?: return CodeBlock.of(defaultFormat, *defaultArguments)
    val name = property.name.kotlinIdentifierName
    if (defaultFormat == "%L" && defaultArguments.singleOrNull() == "null") {
      return CodeBlock.of("%N", name)
    }
    return if (property.modelPropertyTypeName().isNullable) {
      CodeBlock.of("%N ?: %L", name, CodeBlock.of(defaultFormat, *defaultArguments))
    } else {
      CodeBlock.of("%N", name)
    }
  }

  private fun GeneratedModel.quarkusProblemBuilderCode(properties: List<GeneratedModelProperty>): CodeBlock {
    val builder = CodeBlock.builder()
    val propertiesByName = properties.associateBy { property -> property.name }

    builder.add("run {\n").indent()
    builder.addStatement("val builder = %T.builder()", QUARKUS_HTTP_PROBLEM)
    propertiesByName["type"]?.let { property -> builder.addQuarkusProblemBuilderCall(property, "withType") }
    propertiesByName["title"]?.let { property -> builder.addQuarkusProblemBuilderCall(property, "withTitle") }
    propertiesByName["status"]?.let { property -> builder.addQuarkusProblemBuilderCall(property, "withStatus") }
    propertiesByName["detail"]?.let { property -> builder.addQuarkusProblemBuilderCall(property, "withDetail") }
    propertiesByName["instance"]?.let { property -> builder.addQuarkusProblemBuilderCall(property, "withInstance") }
    properties
      .filterNot { property -> property.isBaseProblemProperty() }
      .forEach { property ->
        val name = property.name.kotlinIdentifierName
        val wireName = property.serializationName ?: property.name
        if (property.modelPropertyTypeName().isNullable) {
          builder.beginControlFlow("if (%N != null)", name)
          builder.addStatement("builder.with(%S, %N)", wireName, name)
          builder.endControlFlow()
        } else {
          builder.addStatement("builder.with(%S, %N)", wireName, name)
        }
      }
    builder.addStatement("builder")
    builder.unindent().add("}")

    return builder.build()
  }

  private fun CodeBlock.Builder.addQuarkusProblemBuilderCall(
    property: GeneratedModelProperty,
    methodName: String,
  ) {
    val name = property.name.kotlinIdentifierName
    if (property.modelPropertyTypeName().isNullable) {
      beginControlFlow("if (%N != null)", name)
      addStatement("builder.%L(%N)", methodName, name)
      endControlFlow()
    } else {
      addStatement("builder.%L(%N)", methodName, name)
    }
  }

  private fun GeneratedModelProperty.constructorParameterSpec(): ParameterSpec =
    ParameterSpec
      .builder(name.kotlinIdentifierName, modelPropertyTypeName())
      .apply {
        addAnnotations(jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.PARAM))
        if (serializationName != null || name.kotlinIdentifierName != name) {
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_PROPERTY)
              .addMember("value = %S", serializationName ?: name)
              .build(),
          )
        }
        if (!required || type.nullable) {
          defaultValue("null")
        }
      }.build()

  private fun GeneratedModel.jsonCreatorCompanionType(): TypeSpec =
    TypeSpec
      .companionObjectBuilder()
      .addFunction(
        FunSpec
          .builder("fromValue")
          .addAnnotation(JACKSON_JSON_CREATOR)
          .addAnnotation(ClassName("kotlin.jvm", "JvmStatic"))
          .addParameter("rawValue", STRING)
          .returns(kotlinClassName())
          .beginControlFlow("for (entry in entries)")
          .beginControlFlow("if (entry.wireValue == rawValue)")
          .addStatement("return entry")
          .endControlFlow()
          .endControlFlow()
          .addStatement(
            "throw %T(%S + rawValue)",
            IllegalArgumentException::class.asTypeName(),
            "Unknown ${name.toUpperCamelCase()} value: ",
          ).build(),
      ).build()

  private fun GeneratedModel.objectUnionTypeSpecOrNull(): TypeSpec.Builder? {
    if (!isObjectUnionSealedInterface) {
      return null
    }

    val unionTypeName = kotlinClassName()
    val cases = unionCaseModels()
    val directCases = usesDirectUnionCases(unionTypeName, cases)
    val propertyBranches = cases.map { model -> UnionPropertyBranch(model, model.uniqueRequiredWireNames(cases)) }

    return TypeSpec
      .interfaceBuilder(unionTypeName)
      .addModifiers(KModifier.PUBLIC, KModifier.SEALED)
      .apply {
        if (typeRegistry.options.contains(JacksonAnnotations)) {
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_DESERIALIZE)
              .addMember("using = %T::class", unionTypeName.nestedClass("Deserializer"))
              .build(),
          )
        }
        if (!directCases) {
          cases.forEach { model ->
            addType(model.unionCaseType(unionTypeName))
          }
        }
        if (typeRegistry.options.contains(JacksonAnnotations)) {
          addType(deserializerType(unionTypeName, cases, propertyBranches, directCases))
        }
      }
  }

  private fun GeneratedModel.unionCaseType(unionTypeName: ClassName): TypeSpec {
    val caseTypeName = unionCaseTypeName(unionTypeName)
    val valueTypeName = kotlinClassName()

    return TypeSpec
      .classBuilder(caseTypeName)
      .addModifiers(KModifier.PUBLIC, KModifier.DATA)
      .primaryConstructor(
        FunSpec
          .constructorBuilder()
          .addParameter("value", valueTypeName)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("value", valueTypeName, KModifier.PUBLIC)
          .initializer("value")
          .build(),
      ).addSuperinterface(unionTypeName)
      .build()
  }

  private fun GeneratedModel.deserializerType(
    unionTypeName: ClassName,
    cases: List<GeneratedModel>,
    propertyBranches: List<UnionPropertyBranch>,
    directCases: Boolean,
  ): TypeSpec {
    val deserialize =
      FunSpec
        .builder("deserialize")
        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
        .addParameter("parser", JACKSON_JSON_PARSER)
        .addParameter("context", JACKSON_DESERIALIZATION_CONTEXT)
        .returns(unionTypeName)
        .addStatement("val tree = parser.codec.readTree<%T>(parser)", JSON_NODE)
        .apply {
          val discriminator = unionDiscriminator(cases)
          if (discriminator != null) {
            addStatement("val discriminatorValue = tree.get(%S)?.asText()", discriminator.wireName)
            discriminator.cases.forEach { case ->
              beginControlFlow("if (discriminatorValue == %S)", case.value)
              addStatement("return %L", case.model.unionCaseDecodeCode(unionTypeName, directCases))
              endControlFlow()
            }
            addStatement(
              "throw %T.from(parser, %S)",
              JACKSON_JSON_MAPPING_EXCEPTION,
              "unsupported value for \"${discriminator.wireName}\"",
            )
          } else {
            propertyBranches
              .filter { branch -> branch.uniqueRequiredWireNames.isNotEmpty() }
              .forEach { branch ->
                beginControlFlow("if (%L)", branch.uniqueRequiredWireNames.presenceExpression("||"))
                addStatement("return %L", branch.model.unionCaseDecodeCode(unionTypeName, directCases))
                endControlFlow()
              }

            propertyBranches
              .filter { branch -> branch.uniqueRequiredWireNames.isEmpty() }
              .forEach { branch ->
                beginControlFlow("if (%L)", branch.model.requiredWireNames().presenceExpression("&&"))
                addStatement("return %L", branch.model.unionCaseDecodeCode(unionTypeName, directCases))
                endControlFlow()
              }

            addStatement(
              "throw %T.from(parser, %S)",
              JACKSON_JSON_MAPPING_EXCEPTION,
              "unable to determine union case for \"$name\"",
            )
          }
        }.build()

    return TypeSpec
      .classBuilder("Deserializer")
      .addModifiers(KModifier.PUBLIC)
      .superclass(JACKSON_JSON_DESERIALIZER.parameterizedBy(unionTypeName))
      .addFunction(deserialize)
      .build()
  }

  private fun GeneratedModel.unionCaseDecodeCode(
    unionTypeName: ClassName,
    directCases: Boolean,
  ): CodeBlock =
    if (directCases) {
      CodeBlock.of("parser.codec.treeToValue(tree, %T::class.java)", kotlinClassName())
    } else {
      CodeBlock.of(
        "%T(parser.codec.treeToValue(tree, %T::class.java))",
        unionCaseTypeName(unionTypeName),
        kotlinClassName(),
      )
    }

  private fun TypeSpec.Builder.addJacksonPolymorphism(model: GeneratedModel) {
    if (!typeRegistry.options.contains(JacksonAnnotations)) {
      return
    }
    if (model.discriminatorMappings.isEmpty()) {
      return
    }

    val discriminator = model.discriminator ?: model.externalDiscriminatorNameOrNull() ?: return
    val include =
      if (model.discriminator == null) {
        JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY
      } else {
        JACKSON_JSON_TYPEINFO_AS_EXISTING_PROPERTY
      }
    val mappedTypes =
      model.discriminatorMappings.mapNotNull { (value, typeRef) ->
        val mappedModel = typeRef.modelOrNull(apiIndex) ?: return@mapNotNull null
        value to mappedModel.kotlinClassName()
      }
    if (mappedTypes.isEmpty()) {
      return
    }

    addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_TYPEINFO)
        .addMember("use = %T.%L", JACKSON_JSON_TYPEINFO_ID, JACKSON_JSON_TYPEINFO_ID_NAME)
        .addMember("include = %T.%L", JACKSON_JSON_TYPEINFO_AS, include)
        .addMember("property = %S", discriminator)
        .build(),
    )
    addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_SUBTYPES)
        .addMember(
          CodeBlock
            .builder()
            .add("value = [")
            .indent()
            .apply {
              mappedTypes.forEachIndexed { index, (value, typeName) ->
                if (index == 0) {
                  add("\n")
                } else {
                  add(",\n")
                }
                add("%T(value = %T::class, name = %S)", JACKSON_JSON_SUBTYPES_TYPE, typeName, value)
              }
            }.unindent()
            .add("\n]")
            .build(),
        ).build(),
    )
  }

  private fun TypeSpec.Builder.addJacksonUnionMemberDeserializerOverride(model: GeneratedModel) {
    if (!typeRegistry.options.contains(JacksonAnnotations)) {
      return
    }
    if (model.directUnionSupertypes().isEmpty()) {
      return
    }

    addAnnotation(
      AnnotationSpec
        .builder(JACKSON_JSON_DESERIALIZE)
        .addMember("using = %T::class", JACKSON_JSON_DESERIALIZER_NONE)
        .build(),
    )
  }

  private fun GeneratedModelProperty.propertySpec(): PropertySpec =
    PropertySpec
      .builder("`${name.kotlinIdentifierName}`", modelPropertyTypeName())
      .addAnnotations(jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.GET))
      .build()

  private fun GeneratedModelProperty.jacksonExternalDiscriminatorAnnotations(
    useSiteTarget: AnnotationSpec.UseSiteTarget,
  ): List<AnnotationSpec> {
    if (!typeRegistry.options.contains(JacksonAnnotations)) {
      return emptyList()
    }

    val discriminator = externalDiscriminator ?: return emptyList()
    val mappedTypes =
      type
        .modelOrNull(apiIndex)
        ?.discriminatorMappings
        .orEmpty()
        .mapNotNull { (value, typeRef) ->
          val mappedModel = typeRef.modelOrNull(apiIndex) ?: return@mapNotNull null
          value to mappedModel.kotlinClassName()
        }
    if (mappedTypes.isEmpty()) {
      return emptyList()
    }

    return listOf(
      AnnotationSpec
        .builder(JACKSON_JSON_TYPEINFO)
        .useSiteTarget(useSiteTarget)
        .addMember("use = %T.%L", JACKSON_JSON_TYPEINFO_ID, JACKSON_JSON_TYPEINFO_ID_NAME)
        .addMember("include = %T.%L", JACKSON_JSON_TYPEINFO_AS, JACKSON_JSON_TYPEINFO_AS_EXTERNAL_PROPERTY)
        .addMember("property = %S", discriminator)
        .build(),
      AnnotationSpec
        .builder(JACKSON_JSON_SUBTYPES)
        .useSiteTarget(useSiteTarget)
        .addMember(
          CodeBlock
            .builder()
            .add("value = [")
            .indent()
            .apply {
              mappedTypes.forEachIndexed { index, (value, typeName) ->
                if (index == 0) {
                  add("\n")
                } else {
                  add(",\n")
                }
                add("%T(value = %T::class, name = %S)", JACKSON_JSON_SUBTYPES_TYPE, typeName, value)
              }
            }.unindent()
            .add("\n]")
            .build(),
        ).build(),
    )
  }

  private fun GeneratedModelProperty.modelPropertyTypeName(): TypeName =
    type.kotlinTypeName().copy(nullable = type.nullable || !required)

  private fun GeneratedModel.allModelProperties(): List<GeneratedModelProperty> =
    inherits.flatMap { inherited -> inherited.modelOrNull(apiIndex)?.allModelProperties().orEmpty() } + properties

  private fun GeneratedModel.inheritedModelProperties(): List<GeneratedModelProperty> =
    inherits.flatMap { inherited -> inherited.modelOrNull(apiIndex)?.allModelProperties().orEmpty() }

  private fun GeneratedModel.localModelProperties(
    inheritedProperties: List<GeneratedModelProperty>,
  ): List<GeneratedModelProperty> {
    val inheritedWireNames = inheritedProperties.map { property -> property.wireName }.toSet()
    return properties.filterNot { property -> property.wireName in inheritedWireNames }
  }

  private val GeneratedModel.hasInheritors: Boolean
    get() = api.models.any { model -> model.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == this } }

  private val GeneratedModelProperty.wireName: String
    get() = serializationName ?: name

  private fun GeneratedProblem.problemType(): TypeSpec.Builder {
    val problemTypeName = typeName()
    val constructorBuilder =
      FunSpec
        .constructorBuilder()
        .apply {
          if (typeRegistry.options.contains(JacksonAnnotations)) {
            addAnnotation(JACKSON_JSON_CREATOR)
          }
        }

    fields.forEach { field ->
      constructorBuilder.addParameter(field.problemParameterSpec())
    }

    val typeBuilder =
      TypeSpec
        .classBuilder(problemTypeName)
        .addType(
          TypeSpec
            .companionObjectBuilder()
            .addProperty(
              PropertySpec
                .builder("TYPE", STRING)
                .addModifiers(KModifier.CONST)
                .initializer("%S", resolvedTypeUri(options.defaultProblemBaseUri))
                .build(),
            ).addProperty(
              PropertySpec
                .builder("TYPE_URI", URI::class.asTypeName())
                .initializer("%T(%L)", URI::class.asTypeName(), "TYPE")
                .build(),
            ).build(),
        )

    fields.forEach { field ->
      typeBuilder.addProperty(
        PropertySpec
          .builder(field.name.kotlinIdentifierName, field.problemTypeName(), KModifier.PUBLIC)
          .initializer(field.name.kotlinIdentifierName)
          .build(),
      )
    }

    if (typeRegistry.options.contains(JacksonAnnotations)) {
      typeBuilder.addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_TYPENAME)
          .addMember("%T.TYPE", problemTypeName)
          .build(),
      )
    }

    when (typeRegistry.problemLibrary) {
      KotlinProblemLibrary.QUARKUS -> configureQuarkusProblemType(typeBuilder, constructorBuilder)
      KotlinProblemLibrary.SUNDAY -> configureSundayProblemType(typeBuilder, constructorBuilder)
      KotlinProblemLibrary.ZALANDO -> configureZalandoProblemType(typeBuilder, constructorBuilder)
    }

    return typeBuilder
  }

  private fun GeneratedProblem.configureQuarkusProblemType(
    typeBuilder: TypeSpec.Builder,
    constructorBuilder: FunSpec.Builder,
  ) {
    constructorBuilder
      .addParameter(
        ParameterSpec
          .builder("instance", URI::class.asTypeName().copy(nullable = true))
          .defaultValue("null")
          .build(),
      ).addParameter(
        ParameterSpec
          .builder("cause", Throwable::class.asTypeName().copy(nullable = true))
          .defaultValue("null")
          .build(),
      )

    typeBuilder
      .superclass(QUARKUS_HTTP_PROBLEM)
      .addQuarkusHttpProblemAlias()
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("%L", quarkusProblemBuilderCode())
      .addInitializerBlock(
        CodeBlock
          .builder()
          .beginControlFlow("if (cause != null)")
          .addStatement("initCause(cause)")
          .endControlFlow()
          .build(),
      )
  }

  private fun GeneratedProblem.quarkusProblemBuilderCode(): CodeBlock {
    val builder = CodeBlock.builder()
    builder.add("run {\n").indent()
    builder.addStatement("val builder = %T.builder()", QUARKUS_HTTP_PROBLEM)
    builder.addStatement("builder.withType(TYPE_URI)")
    builder.addStatement("builder.withTitle(%S)", requiredTitle())
    builder.addStatement("builder.withStatus(%L)", requiredStatus())
    builder.addStatement("builder.withDetail(%S)", requiredDetail())
    builder.beginControlFlow("if (instance != null)")
    builder.addStatement("builder.withInstance(instance)")
    builder.endControlFlow()
    fields.forEach { field ->
      val name = field.name.kotlinIdentifierName
      if (field.problemTypeName().isNullable) {
        builder.beginControlFlow("if (%N != null)", name)
        builder.addStatement("builder.with(%S, %N)", field.serializationName ?: field.name, name)
        builder.endControlFlow()
      } else {
        builder.addStatement("builder.with(%S, %N)", field.serializationName ?: field.name, name)
      }
    }
    builder.addStatement("builder")
    builder.unindent().add("}")
    return builder.build()
  }

  private fun GeneratedProblem.configureSundayProblemType(
    typeBuilder: TypeSpec.Builder,
    constructorBuilder: FunSpec.Builder,
  ) {
    constructorBuilder.addParameter(
      ParameterSpec
        .builder("instance", URI::class.asTypeName().copy(nullable = true))
        .defaultValue("null")
        .build(),
    )

    typeBuilder
      .superclass(SUNDAY_HTTP_PROBLEM)
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("TYPE_URI")
      .addSuperclassConstructorParameter("%S", requiredTitle())
      .addSuperclassConstructorParameter("%L", requiredStatus())
      .addSuperclassConstructorParameter("%S", requiredDetail())
      .addSuperclassConstructorParameter("instance")
  }

  private fun GeneratedProblem.configureZalandoProblemType(
    typeBuilder: TypeSpec.Builder,
    constructorBuilder: FunSpec.Builder,
  ) {
    constructorBuilder
      .addParameter(
        ParameterSpec
          .builder("instance", URI::class.asTypeName().copy(nullable = true))
          .defaultValue("null")
          .build(),
      ).addParameter(
        ParameterSpec
          .builder("cause", ZALANDO_THROWABLE_PROBLEM.copy(nullable = true))
          .defaultValue("null")
          .build(),
      )

    typeBuilder
      .superclass(ZALANDO_ABSTRACT_THROWABLE_PROBLEM)
      .primaryConstructor(constructorBuilder.build())
      .addSuperclassConstructorParameter("TYPE_URI")
      .addSuperclassConstructorParameter("%S", requiredTitle())
      .addSuperclassConstructorParameter("%T.%L", ZALANDO_STATUS, HttpStatus.valueOf(requiredStatus()).name)
      .addSuperclassConstructorParameter("%S", requiredDetail())
      .addSuperclassConstructorParameter("instance")
      .addSuperclassConstructorParameter("cause")
      .addFunction(
        FunSpec
          .builder("getCause")
          .addAnnotation(JACKSON_JSON_IGNORE)
          .returns(ZALANDO_EXCEPTIONAL.copy(nullable = true))
          .addModifiers(KModifier.OVERRIDE)
          .addCode("return super.cause")
          .build(),
      )
  }

  private fun GeneratedModelProperty.problemParameterSpec(): ParameterSpec =
    ParameterSpec
      .builder(name.kotlinIdentifierName, problemTypeName())
      .apply {
        if (serializationName != null || name.kotlinIdentifierName != name) {
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_PROPERTY)
              .addMember("value = %S", serializationName ?: name)
              .build(),
          )
        }
      }.build()

  private fun GeneratedModelProperty.problemTypeName(): TypeName = type.kotlinTypeName().copy(nullable = type.nullable)

  private fun GeneratedProblem.requiredStatus(): Int = status ?: genError("Problem '$name' is missing status")

  private fun GeneratedProblem.requiredTitle(): String = title ?: genError("Problem '$name' is missing title")

  private fun GeneratedProblem.requiredDetail(): String = detail ?: genError("Problem '$name' is missing detail")

  private fun GeneratedTypeRef.kotlinTypeName(): TypeName {
    val typeName =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR -> scalarTypeName()
        GeneratedTypeRef.Kind.NAMED -> namedTypeName()
        GeneratedTypeRef.Kind.ARRAY -> LIST.parameterizedBy(arguments.firstOrNull()?.kotlinTypeName() ?: STRING)
        GeneratedTypeRef.Kind.MAP -> MAP.parameterizedBy(STRING, arguments.firstOrNull()?.kotlinTypeName() ?: STRING)
        GeneratedTypeRef.Kind.UNION -> ANY
      }

    return typeName.copy(nullable = nullable)
  }

  private fun GeneratedTypeRef.scalarTypeName(): TypeName =
    kotlinIntegerScalarTypeName() ?: formattedScalarTypeName() ?: when (name) {
      "any" -> ANY
      "string" -> STRING
      "boolean" -> BOOLEAN
      "integer", "int32" -> INT
      "int64", "long" -> LONG
      "number", "double" -> DOUBLE
      "file" -> BYTE_ARRAY
      "nil" -> UNIT
      else -> STRING
    }

  private fun GeneratedTypeRef.formattedScalarTypeName(): TypeName? =
    when (format?.lowercase()?.ifBlank { null } ?: name.lowercase()) {
      "date",
      "full-date",
      "date-only",
      -> LocalDate::class.asTypeName()

      "time",
      "partial-time",
      "time-only",
      -> LocalTime::class.asTypeName()

      "datetime-only",
      "date-time-only",
      -> LocalDateTime::class.asTypeName()

      "datetime",
      "date-time",
      -> OffsetDateTime::class.asTypeName()

      "uuid" -> UUID::class.asTypeName()
      "uri", "url", "uri-reference", "iri", "iri-reference" -> URI::class.asTypeName()
      "byte", "binary" -> BYTE_ARRAY
      else -> null
    }

  private fun Any.defaultValueString(): String =
    when (this) {
      is String -> this
      else -> toString()
    }

  private fun GeneratedTypeRef.namedTypeName(): TypeName {
    val model = modelOrNull(apiIndex)
    if (model?.isFreeformObject == true) {
      return MAP.parameterizedBy(STRING, ANY)
    }

    if (model?.isObjectUnionSealedInterface == true) {
      return model.kotlinClassName()
    }

    if (model?.isAliasLike == true) {
      return model.aliasTypeName()
    }
    if (model != null) {
      return model.kotlinClassName()
    }

    val packageName =
      api.target(preferredTargetId, "kotlin")?.modelPackageName
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in source IR")

    return ClassName(packageName, name.toUpperCamelCase())
  }

  private fun GeneratedModel.aliasTypeName(): TypeName =
    when (kind) {
      GeneratedModel.Kind.SCALAR_ALIAS ->
        aliases.singleOrNull()?.kotlinTypeName() ?: ANY

      GeneratedModel.Kind.ARRAY -> {
        val itemType = aliases.singleOrNull()?.kotlinTypeName() ?: STRING
        if (collection == GeneratedCollectionKind.SET) {
          SET.parameterizedBy(itemType)
        } else {
          LIST.parameterizedBy(itemType)
        }
      }

      GeneratedModel.Kind.MAP ->
        MAP.parameterizedBy(STRING, aliases.singleOrNull()?.kotlinTypeName() ?: STRING)

      GeneratedModel.Kind.UNION -> {
        val options = aliases.map { alias -> alias.kotlinTypeName() }.distinct()
        options.singleOrNull() ?: ANY
      }

      GeneratedModel.Kind.OBJECT,
      GeneratedModel.Kind.ENUM,
      -> kotlinClassName()
    }

  private fun GeneratedModel.kotlinClassName(): ClassName {
    val owningServiceName = scope?.service
    if (owningServiceName != null) {
      val owningService =
        api.services.firstOrNull { service -> service.name == owningServiceName }
          ?: genError("Unknown scoped model service '$owningServiceName'")
      return serviceTypeName(owningService).nestedClass(name.toUpperCamelCase())
    }

    val target = target(preferredTargetId, "kotlin")
    val explicitTypeName = target?.typeName
    if (explicitTypeName != null && "." in explicitTypeName) {
      return ClassName.bestGuess(explicitTypeName)
    }

    val packageName =
      target?.modelPackageName
        ?: api.target(preferredTargetId, "kotlin")?.modelPackageName
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in source IR")
    val simpleName = explicitTypeName ?: name.toUpperCamelCase()
    return ClassName(packageName, simpleName)
  }

  private fun serviceTypeName(service: GeneratedService): ClassName {
    val servicePackageName =
      api.target(preferredTargetId, "kotlin")?.packageName
        ?: options.defaultServicePackageName
        ?: genError("No service package specified, one must be specified via options or in source IR")

    return ClassName.bestGuess("$servicePackageName.${service.typeSimpleName()}")
  }

  private data class GeneratedJaxRsService(
    val service: GeneratedService,
    val typeName: ClassName,
    val subresourcePath: String?,
  )

  private fun GeneratedProblem.typeName(): ClassName {
    val packageName =
      api.target(preferredTargetId, "kotlin")?.modelPackageName
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in source IR")

    return ClassName(packageName, name.toUpperCamelCase())
  }

  private val preferredTargetId: String
    get() =
      when (generationMode) {
        Client -> "kotlinClient"
        Server -> "kotlinServer"
      }

  private val GeneratedModel.isAliasLike: Boolean
    get() =
      kind == GeneratedModel.Kind.SCALAR_ALIAS ||
        kind == GeneratedModel.Kind.ARRAY ||
        kind == GeneratedModel.Kind.MAP ||
        (kind == GeneratedModel.Kind.UNION && !isObjectUnionSealedInterface)

  private val GeneratedModel.isFreeformObject: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        scope != null &&
        properties.isEmpty() &&
        patternProperties.isEmpty() &&
        discriminatorMappings.isEmpty() &&
        !hasInheritors &&
        inherits.isEmpty()

  private val GeneratedModel.isObjectUnionSealedInterface: Boolean
    get() =
      kind == GeneratedModel.Kind.UNION &&
        aliases.size >= 2 &&
        unionCaseModels().size == aliases.size

  private fun GeneratedModel.unionCaseModels(): List<GeneratedModel> =
    aliases
      .mapNotNull { alias -> alias.modelOrNull(apiIndex) }
      .filter { model -> model.kind == GeneratedModel.Kind.OBJECT }

  private fun GeneratedModel.directUnionSupertypes(): List<GeneratedModel> =
    api.models.filter { union ->
      val cases =
        union
          .takeIf { model -> model.isObjectUnionSealedInterface }
          ?.unionCaseModels()
          .orEmpty()
      this in cases && union.usesDirectUnionCases(union.kotlinClassName(), cases)
    }

  private fun GeneratedModel.usesDirectUnionCases(
    unionTypeName: ClassName,
    cases: List<GeneratedModel>,
  ): Boolean = cases.all { model -> model.kotlinClassName().packageName == unionTypeName.packageName }

  private fun GeneratedModel.unionCaseTypeName(unionTypeName: ClassName): ClassName =
    unionTypeName.nestedClass("${name.toUpperCamelCase()}Value")

  private fun GeneratedModel.allWireNames(): Set<String> =
    allModelProperties()
      .map { property -> property.wireName }
      .toSet()

  private fun GeneratedModel.requiredWireNames(): List<String> =
    allModelProperties()
      .filter { property -> property.required }
      .map { property -> property.wireName }

  private fun GeneratedModel.uniqueRequiredWireNames(cases: List<GeneratedModel>): List<String> {
    val otherNames =
      cases
        .filterNot { model -> model == this }
        .flatMap { model -> model.allWireNames() }
        .toSet()
    return requiredWireNames().filterNot { name -> name in otherNames }
  }

  private data class UnionPropertyBranch(
    val model: GeneratedModel,
    val uniqueRequiredWireNames: List<String>,
  )

  private data class UnionDiscriminator(
    val wireName: String,
    val cases: List<UnionDiscriminatorCase>,
  )

  private data class UnionDiscriminatorCase(
    val value: String,
    val model: GeneratedModel,
  )

  private fun GeneratedModel.unionDiscriminator(cases: List<GeneratedModel>): UnionDiscriminator? =
    explicitUnionDiscriminator(cases) ?: inheritedUnionDiscriminator(cases)

  private fun GeneratedModel.explicitUnionDiscriminator(cases: List<GeneratedModel>): UnionDiscriminator? {
    val discriminatorName = discriminator ?: return null
    if (discriminatorMappings.isEmpty()) {
      return null
    }

    val mappedCases =
      discriminatorMappings.mapNotNull { (value, typeRef) ->
        val model = typeRef.modelOrNull(apiIndex)?.takeIf { candidate -> candidate in cases } ?: return@mapNotNull null
        UnionDiscriminatorCase(value, model)
      }
    if (mappedCases.size != cases.size) {
      return null
    }

    val wireName =
      cases.firstNotNullOfOrNull { model -> model.discriminatorPropertyOrNull(discriminatorName)?.wireName }
        ?: discriminatorName
    return UnionDiscriminator(wireName, mappedCases)
  }

  private fun GeneratedModel.inheritedUnionDiscriminator(cases: List<GeneratedModel>): UnionDiscriminator? {
    val discriminatorName = cases.firstNotNullOfOrNull { model -> model.discriminatorNameOrNull() } ?: return null
    if (
      cases.any { model ->
        model.discriminatorNameOrNull() != discriminatorName ||
          model.discriminatorValue == null
      }
    ) {
      return null
    }
    val wireName =
      cases.firstNotNullOfOrNull { model -> model.discriminatorPropertyOrNull(discriminatorName)?.wireName }
        ?: discriminatorName
    val mappedCases =
      cases.mapNotNull { model ->
        UnionDiscriminatorCase(model.discriminatorValue ?: return@mapNotNull null, model)
      }
    return UnionDiscriminator(wireName, mappedCases)
  }

  private fun GeneratedModel.discriminatorPropertyOrNull(
    discriminator: String? = discriminatorNameOrNull(),
  ): GeneratedModelProperty? {
    discriminator ?: return null
    return properties.firstOrNull { property -> property.name == discriminator }
      ?: inherits.firstNotNullOfOrNull { inherited ->
        inherited
          .modelOrNull(apiIndex)
          ?.discriminatorPropertyOrNull(discriminator)
      }
  }

  private fun GeneratedModel.discriminatorNameOrNull(): String? =
    discriminator
      ?: inherits.firstNotNullOfOrNull { inherited -> inherited.modelOrNull(apiIndex)?.discriminatorNameOrNull() }

  private fun GeneratedModel.externalDiscriminatorNameOrNull(): String? =
    api.models.firstNotNullOfOrNull { model ->
      model.properties.firstNotNullOfOrNull { property ->
        property.externalDiscriminator.takeIf { property.type.name == name }
      }
    }

  private fun List<String>.presenceExpression(operator: String): CodeBlock =
    if (isEmpty()) {
      CodeBlock.of("true")
    } else {
      map { wireName -> CodeBlock.of("tree.has(%S)", wireName) }.joinToCode(" $operator ")
    }

  private companion object {
    const val SSE_CONTENT_TYPE = "text/event-stream"

    val CHRONO_UNIT = ChronoUnit::class.asTypeName()
    val timeoutAnnotation = ClassName("org.eclipse.microprofile.faulttolerance", "Timeout")
    val retryAnnotation = ClassName("org.eclipse.microprofile.faulttolerance", "Retry")
    val circuitBreakerAnnotation = ClassName("org.eclipse.microprofile.faulttolerance", "CircuitBreaker")
    val rateLimitAnnotation = ClassName("io.smallrye.faulttolerance.api", "RateLimit")
    val rateLimitType = ClassName("io.smallrye.faulttolerance.api", "RateLimitType")
    val fgaHeaderObjectAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAHeaderObject")
    val fgaIgnoreAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAIgnore")
    val fgaObjectAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAObject")
    val fgaPathObjectAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAPathObject")
    val fgaQueryObjectAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAQueryObject")
    val fgaRelationAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGARelation")
    val fgaRequestObjectAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGARequestObject")
    val fgaUserTypeAnnotation = ClassName("io.quarkiverse.zanzibar.annotations", "FGAUserType")
    val applicationScopedAnnotation = ClassName("jakarta.enterprise.context", "ApplicationScoped")
    val jsonWebToken = ClassName("org.eclipse.microprofile.jwt", "JsonWebToken")
    val userExtractor = ClassName("io.quarkiverse.zanzibar", "UserExtractor")
    val fgaUser = userExtractor.nestedClass("User")
    val OPTIONAL = ClassName("java.util", "Optional")

    val retryPolicyKeys = setOf("maxRetries", "delay", "maxDuration", "jitter")
    val circuitBreakerPolicyKeys = setOf("requestVolumeThreshold", "successThreshold", "failureRatio", "delay")
    val rateLimitPolicyKeys = setOf("value", "window", "minSpacing", "type")
    val rateLimitTypes = setOf("FIXED", "ROLLING", "SMOOTH")

    val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
    val baseProblemProperties = setOf("type", "title", "status", "detail", "instance")
    val enumMemberSplitRegex = """\W+""".toRegex()
    val policyMillisRegex = """PT(\d+)MS""".toRegex(RegexOption.IGNORE_CASE)
  }

  private data class PolicyDuration(
    val value: Long,
    val unit: ChronoUnit,
  )
}
