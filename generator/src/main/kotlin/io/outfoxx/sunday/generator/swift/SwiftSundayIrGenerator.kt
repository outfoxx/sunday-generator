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

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedDocumentation
import io.outfoxx.sunday.generator.ir.GeneratedExchange
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedModelScope
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.ir.emit.enabledFor
import io.outfoxx.sunday.generator.ir.emit.explicitAcceptTypes
import io.outfoxx.sunday.generator.ir.emit.explicitContentTypes
import io.outfoxx.sunday.generator.ir.emit.flattenedUnionTypes
import io.outfoxx.sunday.generator.ir.emit.modelOrNull
import io.outfoxx.sunday.generator.ir.emit.operationParameterViews
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.ir.emit.primarySuccessResponse
import io.outfoxx.sunday.generator.ir.emit.problemOrNull
import io.outfoxx.sunday.generator.ir.emit.referencedProblems
import io.outfoxx.sunday.generator.ir.emit.resolvedTypeUri
import io.outfoxx.sunday.generator.ir.emit.target
import io.outfoxx.sunday.generator.ir.emit.withLocation
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.OutputDirectory
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE_DECODER
import io.outfoxx.sunday.generator.swift.utils.ASYNC_STREAM
import io.outfoxx.sunday.generator.swift.utils.CODABLE
import io.outfoxx.sunday.generator.swift.utils.CODING_KEY
import io.outfoxx.sunday.generator.swift.utils.CUSTOM_STRING_CONVERTIBLE
import io.outfoxx.sunday.generator.swift.utils.DATE
import io.outfoxx.sunday.generator.swift.utils.DECODER
import io.outfoxx.sunday.generator.swift.utils.DECODING_ERROR
import io.outfoxx.sunday.generator.swift.utils.DESCRIPTION_BUILDER
import io.outfoxx.sunday.generator.swift.utils.EMPTY
import io.outfoxx.sunday.generator.swift.utils.ENCODER
import io.outfoxx.sunday.generator.swift.utils.ENCODING_ERROR
import io.outfoxx.sunday.generator.swift.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.swift.utils.GENERIC_PROBLEM
import io.outfoxx.sunday.generator.swift.utils.IDENTIFIABLE
import io.outfoxx.sunday.generator.swift.utils.MEDIA_TYPE_ARRAY
import io.outfoxx.sunday.generator.swift.utils.NILABLE_OPERATION
import io.outfoxx.sunday.generator.swift.utils.NILIFY_SPEC
import io.outfoxx.sunday.generator.swift.utils.OPERATION
import io.outfoxx.sunday.generator.swift.utils.OPERATION_SPEC
import io.outfoxx.sunday.generator.swift.utils.PARAMETER_VALUES
import io.outfoxx.sunday.generator.swift.utils.PROBLEM
import io.outfoxx.sunday.generator.swift.utils.PROBLEM_REGISTRATION
import io.outfoxx.sunday.generator.swift.utils.QUALIFIED_PROBLEM
import io.outfoxx.sunday.generator.swift.utils.SENDABLE
import io.outfoxx.sunday.generator.swift.utils.STREAMING_BODY
import io.outfoxx.sunday.generator.swift.utils.STREAMING_OPERATION
import io.outfoxx.sunday.generator.swift.utils.SUNDAY_MODULE
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT_REQUEST
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT_RESPONSE
import io.outfoxx.sunday.generator.swift.utils.URI_TEMPLATE
import io.outfoxx.sunday.generator.swift.utils.URL
import io.outfoxx.sunday.generator.swift.utils.swiftEnumCaseName
import io.outfoxx.sunday.generator.swift.utils.swiftIdentifierName
import io.outfoxx.sunday.generator.swift.utils.swiftStringFormatTypeName
import io.outfoxx.sunday.generator.swift.utils.swiftTypeName
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.swiftpoet.ANY
import io.outfoxx.swiftpoet.ARRAY
import io.outfoxx.swiftpoet.BOOL
import io.outfoxx.swiftpoet.CASE_ITERABLE
import io.outfoxx.swiftpoet.CodeBlock
import io.outfoxx.swiftpoet.DATA
import io.outfoxx.swiftpoet.DICTIONARY
import io.outfoxx.swiftpoet.DOUBLE
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.ExtensionSpec
import io.outfoxx.swiftpoet.FunctionSpec
import io.outfoxx.swiftpoet.INT
import io.outfoxx.swiftpoet.Modifier.FILEPRIVATE
import io.outfoxx.swiftpoet.Modifier.FINAL
import io.outfoxx.swiftpoet.Modifier.OVERRIDE
import io.outfoxx.swiftpoet.Modifier.PUBLIC
import io.outfoxx.swiftpoet.Modifier.REQUIRED
import io.outfoxx.swiftpoet.Modifier.STATIC
import io.outfoxx.swiftpoet.NameAllocator
import io.outfoxx.swiftpoet.ParameterSpec
import io.outfoxx.swiftpoet.PropertySpec
import io.outfoxx.swiftpoet.SET
import io.outfoxx.swiftpoet.STRING
import io.outfoxx.swiftpoet.SelfTypeName
import io.outfoxx.swiftpoet.TypeName
import io.outfoxx.swiftpoet.TypeSpec
import io.outfoxx.swiftpoet.TypeVariableName.Bound.Constraint.SAME_TYPE
import io.outfoxx.swiftpoet.TypeVariableName.Companion.bound
import io.outfoxx.swiftpoet.TypeVariableName.Companion.typeVariable
import io.outfoxx.swiftpoet.VOID
import io.outfoxx.swiftpoet.joinToCode
import io.outfoxx.swiftpoet.parameterizedBy
import io.outfoxx.swiftpoet.tag

/**
 * Swift/Sunday service generator that renders service declarations from Sunday IR.
 */
class SwiftSundayIrGenerator(
  private val api: GeneratedApi,
  private val typeRegistry: SwiftTypeOutputRegistry,
  private val options: SwiftSundayOptions,
) {

  private val transportTypeVariable = typeVariable("TransportType", bound(TRANSPORT))

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(options.defaultMediaTypes)
  private val apiIndex = GeneratedApiIndex(api)
  private val runtimeProblemTypeName: DeclaredTypeName =
    if (api.hasGeneratedSwiftTypeNamed(PROBLEM.simpleName)) {
      QUALIFIED_PROBLEM
    } else {
      PROBLEM
    }

  /** Generates Swift/Sunday service types from IR and registers them in the type registry. */
  fun generateServiceTypes() {
    val services = api.swiftSundayServices()
    val serviceOutputGroups = services.swiftOutputGroups()
    generateModelTypes(services, serviceOutputGroups)
    generateProblemTypes(services, serviceOutputGroups)

    val serviceTypes =
      services.map { service ->
        val serviceTypeName = DeclaredTypeName.typeName(".${service.typeSimpleName()}")
        val serviceTypeBuilder = generateServiceType(serviceTypeName, service)

        typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder, outputGroup = serviceOutputGroups[service])
        GeneratedSwiftService(service, serviceTypeName)
      }

    if (options.aggregateServices && serviceTypes.size > 1) {
      val aggregateTypeName = aggregateServiceTypeName()
      if (serviceTypes.any { serviceType -> serviceType.typeName == aggregateTypeName }) {
        genError(
          "Cannot generate Swift/Sunday aggregate service '$aggregateTypeName' because it matches a generated service",
        )
      }
      typeRegistry.addServiceType(aggregateTypeName, generateAggregateServiceType(aggregateTypeName, serviceTypes))
    }
  }

  private fun GeneratedApi.swiftSundayServices(): List<GeneratedService> =
    services
      .mapNotNull { service ->
        service
          .copy(operations = service.operations.filter { operation -> operation.isSwiftSundayOperation() })
          .withSwiftSundayBaseUri()
          .takeIf { filtered -> filtered.operations.isNotEmpty() }
      }

  private fun GeneratedApi.hasGeneratedSwiftTypeNamed(simpleName: String): Boolean =
    models.any { model -> model.scope == null && model.swiftDeclaredTypeName().simpleName == simpleName } ||
      problems.any { problem -> problem.swiftProblemTypeName().simpleName == simpleName } ||
      swiftSundayServices().any { service -> service.typeSimpleName() == simpleName } ||
      aggregateServiceTypeName().simpleName == simpleName

  private fun GeneratedOperation.isSwiftSundayOperation(): Boolean =
    method !in asyncApiOperationMethods ||
      (path.startsWith("/") && !hasNonHttpProtocolBinding())

  private fun GeneratedService.withSwiftSundayBaseUri(): GeneratedService =
    takeUnless { service ->
      service.operations.any { operation -> operation.isHttpPathAsyncApiOperation() } &&
        service.baseUri?.hasHttpScheme() != true
    }
      ?: copy(
        baseUri =
          api
            .protocol
            ?.servers
            ?.firstOrNull { server -> server.isHttpServer() }
            ?.url
            ?: baseUri,
      )

  private fun GeneratedOperation.isHttpPathAsyncApiOperation(): Boolean =
    method in asyncApiOperationMethods &&
      path.startsWith("/") &&
      !hasNonHttpProtocolBinding()

  private fun GeneratedOperation.hasNonHttpProtocolBinding(): Boolean =
    protocol
      ?.bindings
      .orEmpty()
      .any { binding -> !binding.protocol.isHttpProtocol() }

  private fun String.hasHttpScheme(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

  private fun String.isHttpProtocol(): Boolean = equals("http", ignoreCase = true) || equals("https", ignoreCase = true)

  private fun GeneratedServer.isHttpServer(): Boolean =
    protocol?.isHttpProtocol() == true ||
      url.hasHttpScheme()

  private fun generateModelTypes(
    services: List<GeneratedService>,
    serviceOutputGroups: Map<GeneratedService, String>,
  ) {
    val modelOutputGroups = services.modelOutputGroups(serviceOutputGroups)
    val eventModelKeys = services.eventModelKeys()

    api
      .models
      .filter { model -> model.scope == null }
      .mapNotNull { model ->
        val outputDirectory = model.swiftOutputDirectory(model.swiftModelKey() in eventModelKeys)
        val outputGroup = modelOutputGroups[model.swiftModelKey()]
        model
          .swiftTypeSpecBuilderOrNull(outputDirectory, outputGroup)
          ?.let { typeBuilder -> GeneratedSwiftModel(model, outputDirectory, outputGroup, typeBuilder) }
      }.forEach { generatedModel ->
        val (model, outputDirectory, outputGroup, typeBuilder) = generatedModel
        val typeName = model.swiftDeclaredTypeName()
        typeRegistry.addModelType(
          typeName,
          typeBuilder,
          outputDirectory = outputDirectory,
          outputGroup = outputGroup,
        )
      }
  }

  private fun generateProblemTypes(
    services: List<GeneratedService>,
    serviceOutputGroups: Map<GeneratedService, String>,
  ) {
    val problemOutputGroups = services.problemOutputGroups(serviceOutputGroups)

    services
      .flatMap { service -> service.referencedProblems(apiIndex) }
      .distinctBy { problem -> problem.swiftProblemTypeName() }
      .forEach { problem ->
        typeRegistry.addModelType(
          problem.swiftProblemTypeName(),
          problem.swiftProblemTypeSpec(),
          outputDirectory = OutputDirectory.Problems,
          outputGroup = problemOutputGroups[problem.swiftProblemKey()],
        )
      }
  }

  private fun List<GeneratedService>.swiftOutputGroups(): Map<GeneratedService, String> =
    mapNotNull { service -> service.group?.swiftTypeName?.let { group -> service to group } }.toMap()

  private fun List<GeneratedService>.modelOutputGroups(
    serviceOutputGroups: Map<GeneratedService, String>,
  ): Map<SwiftModelKey, String> {
    val referencedGroups =
      flatMap { service ->
        service.operations.flatMap { operation ->
          val outputGroup = serviceOutputGroups[service] ?: operation.swiftOutputGroup()
          operation
            .referencedTopLevelModels()
            .mapNotNull { model -> outputGroup?.let { group -> model.swiftModelKey() to group } }
        }
      }
    val directGroups =
      referencedGroups
        .groupBy({ (modelKey) -> modelKey }, { (_, group) -> group })
        .mapNotNull { (modelKey, groups) -> groups.distinct().singleOrNull()?.let { group -> modelKey to group } }
        .toMap()

    return directGroups.withDiscriminatorFamilyGroups()
  }

  private fun Map<SwiftModelKey, String>.withDiscriminatorFamilyGroups(): Map<SwiftModelKey, String> {
    val outputGroups = toMutableMap()
    val modelKeys =
      api
        .models
        .filter { model -> model.scope == null }
        .associateBy { model -> model.swiftModelKey() }

    var changed = true
    while (changed) {
      changed = false

      api
        .models
        .filter { model -> model.scope == null }
        .forEach { model ->
          val familyKeys = model.discriminatorFamilyKeys(modelKeys)
          val familyGroups = familyKeys.mapNotNull { modelKey -> outputGroups[modelKey] }.distinct()
          if (familyGroups.size != 1) {
            // Ambiguous shared families stay ungrouped unless every grouped reference agrees on the same group.
            return@forEach
          }

          val familyGroup = familyGroups.single()
          familyKeys.forEach { modelKey ->
            if (modelKey !in outputGroups) {
              outputGroups[modelKey] = familyGroup
              changed = true
            }
          }
        }
    }

    return outputGroups
  }

  private fun GeneratedModel.discriminatorFamilyKeys(
    modelKeys: Map<SwiftModelKey, GeneratedModel>,
  ): Set<SwiftModelKey> =
    buildSet {
      add(swiftModelKey())

      inherits
        .mapNotNull { type -> type.modelOrNull(apiIndex)?.takeIf { model -> model.scope == null } }
        .forEach { model -> add(model.swiftModelKey()) }

      discriminatorMappings
        .values
        .mapNotNull { type -> type.modelOrNull(apiIndex)?.takeIf { model -> model.scope == null } }
        .forEach { model -> add(model.swiftModelKey()) }

      discriminator
        ?.let { discriminatorName -> properties.firstOrNull { property -> property.name == discriminatorName } }
        ?.type
        ?.modelOrNull(apiIndex)
        ?.takeIf { model -> model.scope == null }
        ?.let { model -> add(model.swiftModelKey()) }

      val modelKey = swiftModelKey()
      modelKeys
        .values
        .filter { model -> model.inherits.any { type -> type.modelOrNull(apiIndex)?.swiftModelKey() == modelKey } }
        .forEach { model -> add(model.swiftModelKey()) }
    }

  private fun List<GeneratedService>.problemOutputGroups(
    serviceOutputGroups: Map<GeneratedService, String>,
  ): Map<SwiftProblemKey, String> =
    flatMap { service ->
      service.operations.flatMap { operation ->
        val outputGroup = serviceOutputGroups[service] ?: operation.swiftOutputGroup()
        operation
          .referencedProblems(apiIndex)
          .mapNotNull { problem -> outputGroup?.let { group -> problem.swiftProblemKey() to group } }
      }
    }.groupBy({ (problemKey) -> problemKey }, { (_, group) -> group })
      .mapNotNull { (problemKey, groups) -> groups.distinct().singleOrNull()?.let { group -> problemKey to group } }
      .toMap()

  private fun List<GeneratedService>.eventModelKeys(): Set<SwiftModelKey> =
    flatMap { service ->
      service.operations
        .filter { operation -> operation.method in asyncApiOperationMethods || operation.streaming?.eventMode != null }
        .flatMap { operation -> operation.referencedTopLevelModels() }
    }.map { model -> model.swiftModelKey() }
      .toSet()

  private fun GeneratedService.referencedTopLevelModels(): Set<GeneratedModel> =
    operations.flatMap { operation -> operation.referencedTopLevelModels() }.toSet()

  private fun GeneratedOperation.swiftOutputGroup(): String? =
    tags
      .firstOrNull()
      ?.swiftTypeName

  private fun GeneratedOperation.referencedTopLevelModels(): Set<GeneratedModel> =
    buildSet {
      val visited = linkedSetOf<SwiftModelKey>()

      fun add(type: GeneratedTypeRef) {
        type.arguments.forEach(::add)

        val model = type.modelOrNull(apiIndex) ?: return
        if (!visited.add(model.swiftModelKey())) {
          return
        }
        if (model.scope == null) {
          add(model)
        }
        model.inherits.forEach(::add)
        model.discriminatorMappings.values.forEach(::add)
        model.aliases.forEach(::add)
        model.additionalProperties?.type?.let(::add)
        model.patternProperties.forEach { property -> add(property.type) }
        model.properties.forEach { property -> add(property.type) }
      }

      parameters.forEach { parameter -> add(parameter.type) }
      queryString?.let(::add)
      requestBody?.type?.let(::add)
      requestBody?.payloads.orEmpty().forEach { payload -> add(payload.type) }
      responses.forEach { response ->
        response.type?.let(::add)
        response.payloads.forEach { payload -> add(payload.type) }
        response.headers.forEach { header -> add(header.type) }
      }
    }

  private fun GeneratedModel.swiftOutputDirectory(referencedByEventOperation: Boolean): OutputDirectory =
    when {
      referencedByEventOperation || isSwiftEventModel() -> OutputDirectory.Events
      name.endsWith("Request") || name.endsWith("RequestBody") -> OutputDirectory.Requests
      name.endsWith("Response") || name.endsWith("ResponseBody") -> OutputDirectory.Responses
      scope?.usage in requestModelUsages -> OutputDirectory.Requests
      scope?.usage == GeneratedModelScope.Usage.RESPONSE_BODY -> OutputDirectory.Responses
      else -> OutputDirectory.Models
    }

  private fun GeneratedModel.isSwiftEventModel(): Boolean =
    name == "EventEnvelope" ||
      name == "EventData" ||
      inherits.any { type -> type.name == "EventData" } ||
      discriminatorMappings.values.any { type -> type.name == "EventData" || type.name.endsWith("Data") }

  private fun GeneratedModel.swiftModelKey(): SwiftModelKey = SwiftModelKey(name, scope, source)

  private fun GeneratedProblem.swiftProblemKey(): SwiftProblemKey = SwiftProblemKey(name, source)

  private data class SwiftModelKey(
    val name: String,
    val scope: GeneratedModelScope?,
    val source: GeneratedSourceSpec?,
  )

  private data class SwiftProblemKey(
    val name: String,
    val source: GeneratedSourceSpec?,
  )

  private val recursiveSwiftObjectModelKeys: Set<SwiftModelKey> by lazy {
    val objectModels =
      api.models
        .filter { model -> model.kind == GeneratedModel.Kind.OBJECT }
        .associateBy { model -> model.swiftModelKey() }

    val edges =
      objectModels.mapValues { (_, model) ->
        model
          .properties
          .mapNotNull { property -> property.type.directNamedModelOrNull() }
          .filter { referenced -> referenced.kind == GeneratedModel.Kind.OBJECT }
          .map { referenced -> referenced.swiftModelKey() }
          .toSet()
      }

    objectModels.keys.filterTo(mutableSetOf()) { key -> key.reaches(key, edges, mutableSetOf()) }
  }

  private data class GeneratedSwiftService(
    val service: GeneratedService,
    val typeName: DeclaredTypeName,
  )

  private data class GeneratedSwiftModel(
    val model: GeneratedModel,
    val outputDirectory: OutputDirectory,
    val outputGroup: String?,
    val typeBuilder: TypeSpec.Builder,
  )

  private data class UnionPropertyBranch(
    val model: GeneratedModel,
    val uniqueRequiredWireNames: List<String>,
  )

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

    return "${servicePrefix.swiftTypeName}${options.serviceSuffix}"
  }

  private fun aggregateServiceTypeName(): DeclaredTypeName =
    DeclaredTypeName.typeName(".${(options.aggregateServiceName ?: options.serviceSuffix).ifBlank { "API" }}")

  private fun generateAggregateServiceType(
    aggregateTypeName: DeclaredTypeName,
    services: List<GeneratedSwiftService>,
  ): TypeSpec.Builder {
    val mediaSelection = services.aggregateMediaSelection()
    val referencedProblems = services.aggregateReferencedProblems()
    val names = NameAllocator()
    val serviceProperties =
      services.map { service ->
        val proposedName = service.service.aggregatePropertyName()
        AggregateServiceProperty(service.typeName, names.newName(proposedName, service))
      }

    val constructorBuilder =
      FunctionSpec
        .constructorBuilder()
        .addModifiers(PUBLIC)
        .addParameter("transport", transportTypeVariable)
        .addStatement("self.transport = transport")
        .addParameter(
          ParameterSpec
            .builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
            .defaultValue("%L", mediaTypesArray(mediaSelection.contentTypes))
            .build(),
        ).addStatement("self.defaultContentTypes = defaultContentTypes")
        .addParameter(
          ParameterSpec
            .builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
            .defaultValue("%L", mediaTypesArray(mediaSelection.acceptTypes))
            .build(),
        ).addStatement("self.defaultAcceptTypes = defaultAcceptTypes")
        .addParameter(
          ParameterSpec
            .builder("problemTypes", ARRAY.parameterizedBy(PROBLEM_REGISTRATION))
            .defaultValue("%T.problemTypes", aggregateTypeName)
            .build(),
        )

    val typeBuilder =
      TypeSpec
        .classBuilder(aggregateTypeName)
        .addModifiers(PUBLIC, FINAL)
        .addTypeVariable(transportTypeVariable)
        .addSuperType(SENDABLE)
        .addProperty(problemTypesProperty(referencedProblems))
        .addProperty(
          PropertySpec
            .builder("transport", transportTypeVariable)
            .addModifiers(PUBLIC)
            .build(),
        ).addProperty(
          PropertySpec
            .builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
            .addModifiers(PUBLIC)
            .build(),
        ).addProperty(
          PropertySpec
            .builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
            .addModifiers(PUBLIC)
            .build(),
        )

    serviceProperties.forEach { serviceProperty ->
      typeBuilder.addProperty(
        PropertySpec
          .builder(serviceProperty.name, serviceProperty.typeName.parameterizedBy(transportTypeVariable))
          .addModifiers(PUBLIC)
          .build(),
      )
      constructorBuilder.addCode(
        "self.%N = %T(%>\ntransport: transport,\ndefaultContentTypes: defaultContentTypes," +
          "\ndefaultAcceptTypes: defaultAcceptTypes,\nproblemTypes: problemTypes%<\n)\n",
        serviceProperty.name,
        serviceProperty.typeName,
      )
    }

    return typeBuilder.addFunction(constructorBuilder.build())
  }

  private fun GeneratedService.aggregatePropertyName(): String =
    typeSimpleName()
      .removeSuffix(options.serviceSuffix)
      .ifBlank { name.removeSuffix("Service") }
      .toLowerCamelCase()
      .swiftIdentifierName

  private fun List<GeneratedSwiftService>.aggregateMediaSelection(): GeneratedMediaSelection {
    val contentTypes = linkedSetOf<String>()
    val acceptTypes = linkedSetOf<String>()

    forEach { service ->
      val mediaSelection = service.service.defaultMediaSelection(defaultMediaTypes)
      contentTypes.addAll(mediaSelection.contentTypes)
      acceptTypes.addAll(mediaSelection.acceptTypes)
    }

    return GeneratedMediaSelection(
      defaultMediaTypes.filter(contentTypes::contains),
      defaultMediaTypes.filter(acceptTypes::contains),
    )
  }

  private fun List<GeneratedSwiftService>.aggregateReferencedProblems(): List<GeneratedProblem> =
    flatMap { service -> service.service.referencedProblems(apiIndex) }
      .distinctBy { problem -> problem.typeUri }

  private data class AggregateServiceProperty(
    val typeName: DeclaredTypeName,
    val name: String,
  )

  private fun generateServiceType(
    serviceTypeName: DeclaredTypeName,
    service: GeneratedService,
  ): TypeSpec.Builder {
    val mediaSelection = service.defaultMediaSelection(defaultMediaTypes)
    val referencedProblems = service.referencedProblems(apiIndex)
    val serviceTypeBuilder =
      TypeSpec
        .classBuilder(serviceTypeName)
        .addModifiers(PUBLIC, FINAL)
        .addTypeVariable(transportTypeVariable)
        .addSuperType(SENDABLE)
        .addSwiftDoc(service.documentation)
        .addProperty(problemTypesProperty(referencedProblems))
    val constructorBuilder =
      FunctionSpec
        .constructorBuilder()
        .addModifiers(PUBLIC)
        .addParameter("transport", transportTypeVariable)
        .addStatement("self.transport = transport")

    serviceTypeBuilder
      .addProperty(
        PropertySpec
          .builder("transport", transportTypeVariable)
          .addModifiers(PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
          .addModifiers(PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
          .addModifiers(PUBLIC)
          .build(),
      )

    constructorBuilder
      .addParameter(
        ParameterSpec
          .builder("defaultContentTypes", MEDIA_TYPE_ARRAY)
          .defaultValue("%L", mediaTypesArray(mediaSelection.contentTypes))
          .build(),
      ).addStatement("self.defaultContentTypes = defaultContentTypes")
      .addParameter(
        ParameterSpec
          .builder("defaultAcceptTypes", MEDIA_TYPE_ARRAY)
          .defaultValue("%L", mediaTypesArray(mediaSelection.acceptTypes))
          .build(),
      ).addStatement("self.defaultAcceptTypes = defaultAcceptTypes")
      .addParameter(
        ParameterSpec
          .builder("problemTypes", ARRAY.parameterizedBy(PROBLEM_REGISTRATION))
          .defaultValue("%T.problemTypes", serviceTypeName)
          .build(),
      ).addStatement("problemTypes.forEach { ${'$'}0.register(on: transport) }")

    serviceTypeBuilder.addFunction(constructorBuilder.build())
    service.baseUrlFunctionOrNull()?.let(serviceTypeBuilder::addFunction)

    service.localTypeSpecs().forEach { typeSpec ->
      serviceTypeBuilder.addType(typeSpec)
    }

    service.operations.forEach { operation ->
      val operationFunction = operation.operationFunction(service)
      serviceTypeBuilder.addFunction(operationFunction)
    }

    return serviceTypeBuilder
  }

  private fun problemTypesProperty(problems: List<GeneratedProblem>): PropertySpec =
    PropertySpec
      .builder("problemTypes", ARRAY.parameterizedBy(PROBLEM_REGISTRATION), PUBLIC, STATIC)
      .getter(problemTypesGetter(problems))
      .build()

  private fun problemTypesGetter(problems: List<GeneratedProblem>): FunctionSpec =
    FunctionSpec
      .getterBuilder()
      .addCode("%L", problemTypesReturnCode(problems))
      .build()

  private fun problemTypesReturnCode(problems: List<GeneratedProblem>): CodeBlock =
    if (problems.isEmpty()) {
      CodeBlock.of("return []\n")
    } else {
      val builder = CodeBlock.builder().add("return [%>\n")
      problems.forEachIndexed { idx, problem ->
        val problemTypeName = problem.swiftProblemTypeName()
        if (problem.hasDuplicateSwiftProblemTypeName()) {
          builder.add(
            "%T(type: %T(string: %S)!, problemType: %T.self)",
            PROBLEM_REGISTRATION,
            URL,
            problem.resolvedTypeUri(options.defaultProblemBaseUri),
            problemTypeName,
          )
        } else {
          builder.add("%T(type: %T.type, problemType: %T.self)", PROBLEM_REGISTRATION, problemTypeName, problemTypeName)
        }
        if (idx < problems.size - 1) {
          builder.add(",\n")
        }
      }
      builder.add("%<\n]\n").build()
    }

  private fun GeneratedService.baseUrlFunctionOrNull(): FunctionSpec? {
    val baseUri = baseUri ?: return null

    return FunctionSpec
      .builder("baseURL")
      .addModifiers(PUBLIC, STATIC)
      .returns(URI_TEMPLATE)
      .apply {
        baseUriParameters.forEach { parameter ->
          addParameter(parameter.swiftBaseUriParameterSpec())
        }
      }.addCode("return %T(%>\n", URI_TEMPLATE)
      .addCode("format: %S,\nparameters: [%>\n", baseUri)
      .apply {
        if (baseUriParameters.isEmpty()) {
          addCode(":")
        }
        baseUriParameters.forEachIndexed { idx, parameter ->
          addCode("%S: %N", parameter.serializationName ?: parameter.name, parameter.name.swiftIdentifierName)
          if (idx < baseUriParameters.size - 1) {
            addCode(",\n")
          }
        }
      }.addCode("%<\n]%<\n)\n")
      .build()
  }

  private fun GeneratedParameter.swiftBaseUriParameterSpec(): ParameterSpec {
    val typeName =
      if (defaultValue != null) {
        type.swiftTypeName().makeNonOptional()
      } else {
        type.swiftTypeName()
      }

    return ParameterSpec
      .builder(name.swiftIdentifierName, typeName)
      .apply {
        defaultValue?.let { defaultValue ->
          defaultValue(defaultValue.swiftValueCode(typeName, type))
        }
      }.build()
  }

  private fun GeneratedService.localTypeSpecs(): List<TypeSpec> =
    apiIndex
      .referencedScopedModels(this)
      .mapNotNull { model -> model.swiftTypeSpecBuilderOrNull()?.build() }

  private fun GeneratedModel.swiftTypeSpecBuilderOrNull(
    outputDirectory: OutputDirectory = OutputDirectory.Models,
    outputGroup: String? = null,
  ): TypeSpec.Builder? =
    when (kind) {
      GeneratedModel.Kind.ENUM ->
        TypeSpec
          .enumBuilder(swiftDeclaredTypeName())
          .addModifiers(PUBLIC)
          .addSwiftDoc(documentation)
          .addSuperTypes(listOf(STRING, CASE_ITERABLE, CODABLE, SENDABLE))
          .apply {
            values.filterIsInstance<String>().forEach { value ->
              addEnumCase(value.swiftEnumCaseName, value)
            }
          }

      GeneratedModel.Kind.OBJECT ->
        typedEventEnvelopeOrNull()?.swiftTypeSpec()
          ?: when {
            isExternalDiscriminatorBaseProtocolModel -> swiftExternalDiscriminatorBaseProtocolTypeSpec()
            isExternalDiscriminatorCaseValueModel -> swiftExternalDiscriminatorCaseValueTypeSpec()
            else -> swiftObjectTypeSpec(outputDirectory, outputGroup)
          }

      GeneratedModel.Kind.UNION -> swiftUnionTypeSpecOrNull()
      else -> null
    }

  private fun GeneratedModel.typedEventEnvelopeOrNull(): TypedEventEnvelope? {
    val dataProperty =
      properties.firstOrNull { property -> property.externalDiscriminator != null }
        ?: return null
    val discriminatorProperty =
      properties.firstOrNull { property -> property.name == dataProperty.externalDiscriminator }
        ?: return null
    val dataBaseModel =
      dataProperty.type.modelOrNull(apiIndex)
        ?: return null
    val cases =
      dataBaseModel
        .discriminatorMappings
        .mapNotNull { (value, typeRef) ->
          typeRef.modelOrNull(apiIndex)?.let { model -> TypedEventEnvelopeCase(value, model) }
        }
    if (cases.isEmpty()) {
      return null
    }
    return TypedEventEnvelope(this, discriminatorProperty, dataProperty, cases)
  }

  private fun TypedEventEnvelope.swiftTypeSpec(): TypeSpec.Builder {
    val typeName = model.swiftDeclaredTypeName()
    val identifiableProperty = model.identifiablePropertyOrNull()

    return TypeSpec
      .enumBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSwiftDoc(model.documentation)
      .addSuperTypes(
        buildList {
          add(CODABLE)
          add(CUSTOM_STRING_CONVERTIBLE)
          add(SENDABLE)
          if (identifiableProperty != null) {
            add(IDENTIFIABLE)
          }
        },
      ).apply {
        cases.forEach { case ->
          addEnumCase(case.caseName, case.typeName(typeName))
        }
        model.properties.forEach { property ->
          addProperty(swiftEventEnvelopeProperty(property))
        }
        addProperty(swiftEventEnvelopeDescriptionProperty())
        addFunction(swiftEventEnvelopeDecoder(typeName))
        addFunction(swiftEventEnvelopeEncoder())
        addType(codingKeysType(listOf(discriminatorProperty)))
        cases.forEach { case ->
          addType(case.swiftTypeSpec(typeName, this@swiftTypeSpec))
        }
      }
  }

  private fun TypedEventEnvelope.swiftEventEnvelopeProperty(property: GeneratedModelProperty): PropertySpec =
    PropertySpec
      .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false), PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .apply {
            beginControlFlow("switch", "self")
            cases.forEach { case ->
              addStatement("case .%N(let value):%Wreturn value.%N", case.caseName, property.name.swiftIdentifierName)
            }
            endControlFlow("switch")
          }.build(),
      ).build()

  private fun TypedEventEnvelope.swiftEventEnvelopeDescriptionProperty(): PropertySpec =
    PropertySpec
      .builder("debugDescription", STRING, PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .apply {
            beginControlFlow("switch", "self")
            cases.forEach { case ->
              addStatement("case .%N(let value):%Wreturn value.debugDescription", case.caseName)
            }
            endControlFlow("switch")
          }.build(),
      ).build()

  private fun TypedEventEnvelope.swiftEventEnvelopeDecoder(typeName: DeclaredTypeName): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
      .addStatement(
        "let discriminatorValue = try container.decode(%T.self, forKey: .%N)",
        discriminatorProperty.swiftTypeName().makeNonOptional(),
        discriminatorProperty.name.swiftIdentifierName,
      ).apply {
        cases.forEach { case ->
          beginControlFlow("if", "discriminatorValue == %L", case.discriminatorValueCode(discriminatorProperty))
          addStatement("self = .%N(try %T(from: decoder))", case.caseName, case.typeName(typeName))
          addStatement("return")
          endControlFlow("if")
        }
        addStatement(
          "throw %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
          DECODING_ERROR,
          discriminatorProperty.name.swiftIdentifierName,
          "unsupported value for \"${discriminatorProperty.name}\"",
        )
      }.build()

  private fun TypedEventEnvelope.swiftEventEnvelopeEncoder(): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .apply {
        beginControlFlow("switch", "self")
        cases.forEach { case ->
          addStatement("case .%N(let value):%Wtry value.encode(to: encoder)", case.caseName)
        }
        endControlFlow("switch")
      }.build()

  private fun TypedEventEnvelopeCase.swiftTypeSpec(
    envelopeTypeName: DeclaredTypeName,
    envelope: TypedEventEnvelope,
  ): TypeSpec {
    val caseTypeName = typeName(envelopeTypeName)
    val caseDataProperty = envelope.caseDataProperty(this)
    val commonProperties =
      envelope.model.properties
        .filterNot { property -> property == envelope.discriminatorProperty || property == envelope.dataProperty }
    val allProperties = commonProperties + caseDataProperty
    val identifiableProperty = envelope.model.identifiablePropertyOrNull()

    return TypeSpec
      .structBuilder(caseTypeName.simpleName)
      .addModifiers(PUBLIC)
      .addSuperTypes(
        buildList {
          add(CODABLE)
          add(CUSTOM_STRING_CONVERTIBLE)
          add(SENDABLE)
          if (identifiableProperty != null) {
            add(IDENTIFIABLE)
          }
        },
      ).apply {
        addProperty(
          PropertySpec
            .builder(
              envelope.discriminatorProperty.name.swiftIdentifierName,
              envelope.discriminatorProperty.swiftTypeName().makeNonOptional(),
              PUBLIC,
            ).getter(
              FunctionSpec
                .getterBuilder()
                .addStatement(
                  "return %L",
                  discriminatorValueCode(envelope.discriminatorProperty),
                ).build(),
            ).build(),
        )
        allProperties.forEach { property ->
          addProperty(
            PropertySpec
              .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false), PUBLIC)
              .addSwiftDoc(property.documentation)
              .build(),
          )
        }
        addProperty(debugDescriptionProperty(caseTypeName, listOf(envelope.discriminatorProperty) + allProperties))
        addFunction(envelope.caseConstructor(allProperties))
        addFunction(envelope.caseDecoderConstructor(allProperties))
        addFunction(envelope.caseEncoderFunction(this@swiftTypeSpec, allProperties))
        addType(codingKeysType(allProperties, envelope.discriminatorProperty))
      }.build()
  }

  private fun TypedEventEnvelope.caseDataProperty(case: TypedEventEnvelopeCase): GeneratedModelProperty =
    dataProperty.copy(
      type =
        GeneratedTypeRef.named(
          case.model.name,
          nullable = dataProperty.type.nullable,
          scope = case.model.scope,
          source = case.model.source,
        ),
    )

  private fun TypedEventEnvelopeCase.discriminatorValueCode(discriminatorProperty: GeneratedModelProperty): CodeBlock =
    value.swiftValueCode(discriminatorProperty.swiftTypeName().makeNonOptional(), discriminatorProperty.type)

  private fun TypedEventEnvelope.caseConstructor(properties: List<GeneratedModelProperty>): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .apply {
        properties.forEach { property ->
          addParameter(
            ParameterSpec
              .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false))
              .apply {
                if (property.swiftTypeName().optional) {
                  defaultValue("nil")
                }
              }.build(),
          )
        }
        properties.forEach { property ->
          addStatement("self.%N = %N", property.name.swiftIdentifierName, property.name.swiftIdentifierName)
        }
      }.build()

  private fun TypedEventEnvelope.caseDecoderConstructor(properties: List<GeneratedModelProperty>): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
      .apply {
        properties.forEach { property ->
          addStatement(
            "self.%N = try container.decode%L(%T.self, forKey: .%N)",
            property.name.swiftIdentifierName,
            if (property.swiftTypeName().optional) "IfPresent" else "",
            property.swiftTypeName().makeNonOptional(),
            property.name.swiftIdentifierName,
          )
        }
      }.build()

  private fun TypedEventEnvelope.caseEncoderFunction(
    case: TypedEventEnvelopeCase,
    properties: List<GeneratedModelProperty>,
  ): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .addStatement("var container = encoder.container(keyedBy: CodingKeys.self)")
      .addStatement(
        "try container.encode(%L, forKey: .%N)",
        case.discriminatorValueCode(discriminatorProperty),
        discriminatorProperty.name.swiftIdentifierName,
      ).apply {
        properties.forEach { property ->
          addStatement(
            "try container.encode%L(self.%N, forKey: .%N)",
            if (property.swiftTypeName().optional) "IfPresent" else "",
            property.name.swiftIdentifierName,
            property.name.swiftIdentifierName,
          )
        }
      }.build()

  private fun TypedEventEnvelopeCase.typeName(envelopeTypeName: DeclaredTypeName): DeclaredTypeName =
    envelopeTypeName.nestedType(model.name.removeSuffix("Data").toUpperCamelCase() + "Event")

  private val TypedEventEnvelopeCase.caseName: String
    get() =
      model
        .name
        .removeSuffix("Data")
        .toLowerCamelCase()
        .swiftIdentifierName

  private val GeneratedModel.isExternalDiscriminatorBaseProtocolModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        properties.isEmpty() &&
        discriminatorMappings.isNotEmpty()

  private val GeneratedModel.isExternalDiscriminatorCaseValueModel: Boolean
    get() =
      inherits
        .mapNotNull { inherited -> inherited.modelOrNull(apiIndex) }
        .any { inherited -> inherited.isExternalDiscriminatorBaseProtocolModel }

  private val GeneratedModel.isSimpleObjectValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !isRecursiveSwiftObjectModel &&
        !patchable &&
        inherits.isEmpty() &&
        discriminator == null &&
        discriminatorMappings.isEmpty() &&
        !externallyDiscriminated &&
        properties.none { property -> property.externalDiscriminator != null } &&
        !isProblemModel &&
        !hasInheritingModels

  private val GeneratedModel.isPatchableObjectValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !isRecursiveSwiftObjectModel &&
        patchable &&
        inherits.isEmpty() &&
        discriminator == null &&
        discriminatorMappings.isEmpty() &&
        !externallyDiscriminated &&
        properties.none { property -> property.externalDiscriminator != null } &&
        !isProblemModel &&
        !hasInheritingModels

  private val GeneratedModel.isInheritedObjectValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        inherits.isNotEmpty() &&
        !isRecursiveSwiftObjectModel &&
        !patchable &&
        discriminator == null &&
        discriminatorMappings.isEmpty() &&
        !externallyDiscriminated &&
        properties.none { property -> property.externalDiscriminator != null } &&
        !isProblemModel &&
        !isProtocolHierarchyValueModel

  private val GeneratedModel.isExternalDiscriminatorEnvelopeValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !patchable &&
        !isProblemModel &&
        properties.any { property -> property.externalDiscriminator != null }

  private val GeneratedModel.isRecursiveSwiftObjectModel: Boolean
    get() = swiftModelKey() in recursiveSwiftObjectModelKeys

  private val GeneratedModel.isProtocolHierarchyRootModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !patchable &&
        discriminator != null &&
        hasInheritingModels &&
        !isProblemModel

  private val GeneratedModel.isProtocolHierarchyValueModel: Boolean
    get() =
      !patchable &&
        !isProblemModel &&
        inherits
          .mapNotNull { inherited -> inherited.modelOrNull(apiIndex) }
          .any { inherited -> inherited.isProtocolHierarchyRootModel || inherited.isProtocolHierarchyValueModel }

  private val GeneratedModel.isProblemHierarchyProtocolModel: Boolean
    get() =
      isProblemModel &&
        hasInheritingModels

  private val GeneratedModel.isProblemHierarchyValueModel: Boolean
    get() =
      isProblemModel &&
        !hasInheritingModels

  private fun GeneratedModel.swiftExternalDiscriminatorBaseProtocolTypeSpec(): TypeSpec.Builder =
    TypeSpec
      .protocolBuilder(swiftDeclaredTypeName())
      .addModifiers(PUBLIC)
      .addSwiftDoc(documentation)
      .addSuperTypes(listOf(CODABLE, CUSTOM_STRING_CONVERTIBLE, SENDABLE))

  private fun GeneratedModel.swiftExternalDiscriminatorCaseValueTypeSpec(): TypeSpec.Builder {
    val typeName = swiftDeclaredTypeName()
    val localProperties = constructorProperties()
    val identifiableProperty = identifiablePropertyOrNull()
    val typeBuilder =
      TypeSpec
        .structBuilder(typeName)
        .addModifiers(PUBLIC)
        .addSwiftDoc(documentation)
        .addSuperTypes(
          inherits
            .mapNotNull { inherited -> inherited.modelOrNull(apiIndex) }
            .filter { inherited -> inherited.isExternalDiscriminatorBaseProtocolModel }
            .map { inherited -> inherited.swiftDeclaredTypeName() },
        ).apply {
          if (identifiableProperty != null) {
            addSuperType(IDENTIFIABLE)
          }
        }

    localProperties.forEach { property ->
      typeBuilder.addProperty(
        PropertySpec
          .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false), PUBLIC)
          .addSwiftDoc(property.documentation)
          .build(),
      )
    }

    if (identifiableProperty != null && identifiableProperty.name != "id") {
      typeBuilder.addProperty(swiftIdentifiableIdProperty(identifiableProperty, false))
    }

    typeBuilder.addProperty(debugDescriptionProperty(typeName, localProperties))
    typeBuilder.addFunction(modelConstructor(emptyList(), localProperties, null, false, false))
    typeBuilder.addFunction(modelDecoderConstructor(localProperties, null, false, false, true))
    typeBuilder.addFunction(modelEncoderFunction(localProperties, null, null, false, false))
    localProperties.forEach { property ->
      typeBuilder.addFunction(modelWithFunction(typeName, property, localProperties, patchable = false))
    }
    typeBuilder.addType(codingKeysType(localProperties))

    return typeBuilder
  }

  private fun GeneratedModel.swiftUnionTypeSpecOrNull(): TypeSpec.Builder? {
    if (!isObjectUnionEnum) {
      return null
    }

    val typeName = swiftDeclaredTypeName()
    val cases = unionCaseModels()
    val propertyBranches = cases.map { model -> UnionPropertyBranch(model, model.uniqueRequiredWireNames(cases)) }

    return TypeSpec
      .enumBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSwiftDoc(documentation)
      .addSuperTypes(listOf(CODABLE, CUSTOM_STRING_CONVERTIBLE, SENDABLE))
      .apply {
        cases.forEach { model ->
          addEnumCase(model.unionCaseName, model.swiftDeclaredTypeName())
        }
      }.addProperty(
        PropertySpec
          .builder("debugDescription", STRING, PUBLIC)
          .getter(
            FunctionSpec
              .getterBuilder()
              .apply {
                beginControlFlow("switch", "self")
                cases.forEach { model ->
                  addStatement("case .%N(let value):%Wreturn value.debugDescription", model.unionCaseName)
                }
                endControlFlow("switch")
              }.build(),
          ).build(),
      ).apply {
        if (unionDiscriminator(cases) == null) {
          addType(unionCodingKeysType(propertyBranches.structuralWireNames()))
        }
      }.addFunction(unionDecoderConstructor(cases, propertyBranches))
      .addFunction(unionEncoderFunction(cases))
  }

  private fun GeneratedModel.unionDecoderConstructor(
    cases: List<GeneratedModel>,
    propertyBranches: List<UnionPropertyBranch>,
  ): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .apply {
        val discriminator = unionDiscriminator(cases)
        if (discriminator != null) {
          addStatement("let container = try decoder.singleValueContainer()")
          addStatement("let value = try container.decode(%T.self)", ANY_VALUE)
          addStatement(
            "let object = try %T.default.decode([String : %T].self, from: value)",
            ANY_VALUE_DECODER,
            ANY_VALUE,
          )
          addStatement("let discriminatorValue = object[%S]?.unwrapped as? String", discriminator.wireName)
          discriminator.cases.forEach { case ->
            beginControlFlow("if", "discriminatorValue == %S", case.value)
            addStatement(
              "self = .%N(try %T.default.decode(%T.self, from: value))",
              case.model.unionCaseName,
              ANY_VALUE_DECODER,
              case.model.swiftDeclaredTypeName(),
            )
            addStatement("return")
            endControlFlow("if")
          }
          addStatement(
            "throw %T.dataCorruptedError(in: container, debugDescription: %S)",
            DECODING_ERROR,
            "unsupported value for \"${discriminator.wireName}\"",
          )
        } else {
          addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
          addStatement("let keys = container.allKeys")
          propertyBranches
            .filter { branch -> branch.uniqueRequiredWireNames.isNotEmpty() }
            .forEach { branch ->
              beginControlFlow(
                "if",
                "%L",
                branch.uniqueRequiredWireNames.keyPresenceExpression("||"),
              )
              addStatement(
                "self = .%N(try %T(from: decoder))",
                branch.model.unionCaseName,
                branch.model.swiftDeclaredTypeName(),
              )
              addStatement("return")
              endControlFlow("if")
            }

          propertyBranches
            .filter { branch -> branch.uniqueRequiredWireNames.isEmpty() }
            .forEach { branch ->
              beginControlFlow("if", "%L", branch.model.requiredWireNames().keyPresenceExpression("&&"))
              addStatement(
                "self = .%N(try %T(from: decoder))",
                branch.model.unionCaseName,
                branch.model.swiftDeclaredTypeName(),
              )
              addStatement("return")
              endControlFlow("if")
            }

          addStatement(
            "throw %T.typeMismatch(Self.self, .init(codingPath: decoder.codingPath, debugDescription: %S))",
            DECODING_ERROR,
            "Could not decode $name.self",
          )
        }
      }.build()

  private fun List<UnionPropertyBranch>.structuralWireNames(): List<String> =
    flatMap { branch ->
      if (branch.uniqueRequiredWireNames.isNotEmpty()) {
        branch.uniqueRequiredWireNames
      } else {
        branch.model.requiredWireNames()
      }
    }.distinct()

  private fun unionCodingKeysType(wireNames: List<String>): TypeSpec =
    TypeSpec
      .enumBuilder("CodingKeys")
      .addModifiers(FILEPRIVATE)
      .addSuperType(STRING)
      .addSuperType(CODING_KEY)
      .apply {
        wireNames.forEach { wireName ->
          addEnumCase(wireName.swiftIdentifierName, wireName)
        }
      }.build()

  private fun GeneratedModel.unionEncoderFunction(cases: List<GeneratedModel>): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .addStatement("var container = encoder.singleValueContainer()")
      .apply {
        beginControlFlow("switch", "self")
        cases.forEach { model ->
          addStatement("case .%N(let value):%Wtry container.encode(value)", model.unionCaseName)
        }
        endControlFlow("switch")
      }.build()

  private fun GeneratedProblem.swiftProblemTypeSpec(): TypeSpec.Builder {
    val typeName = swiftProblemTypeName()
    val codingKeysTypeName = typeName.nestedType("CodingKeys")
    val customFields = fields

    return TypeSpec
      .structBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSwiftDoc(documentation)
      .addSuperType(runtimeProblemTypeName)
      .addProperty(
        PropertySpec
          .builder("type", URL, PUBLIC, STATIC)
          .initializer("%T(string: %S)!", URL, resolvedTypeUri(options.defaultProblemBaseUri))
          .build(),
      ).addProperty(
        PropertySpec
          .builder("type", URL, PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("title", STRING, PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("status", INT, PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("detail", STRING.makeOptional(), PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("instance", URL.makeOptional(), PUBLIC)
          .build(),
      ).addProperty(
        PropertySpec
          .builder("parameters", DICTIONARY.parameterizedBy(STRING, ANY_VALUE).makeOptional(), PUBLIC)
          .build(),
      ).apply {
        customFields.forEach { field ->
          addProperty(
            PropertySpec
              .builder(field.name.swiftIdentifierName, field.swiftProblemFieldTypeName(), PUBLIC)
              .addSwiftDoc(field.documentation)
              .build(),
          )
        }
      }.addProperty(problemDescriptionProperty(customFields))
      .addFunction(problemConstructor(customFields))
      .addFunction(problemDecoderConstructor(customFields, codingKeysTypeName))
      .addFunction(problemEncoderFunction(customFields, codingKeysTypeName))
      .apply {
        problemCodingKeysTypeOrNull(customFields)?.let(::addType)
      }
  }

  private fun GeneratedProblem.problemConstructor(customFields: List<GeneratedModelProperty>): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .apply {
        customFields.forEach { field ->
          addParameter(
            ParameterSpec
              .builder(field.name.swiftIdentifierName, field.swiftProblemFieldTypeName())
              .apply {
                if (field.swiftProblemFieldTypeName().optional) {
                  defaultValue("nil")
                }
              }.build(),
          )
        }
        addParameter(
          ParameterSpec
            .builder("instance", URL.makeOptional())
            .defaultValue("nil")
            .build(),
        )
        addStatement("self.type = %T.type", SelfTypeName.INSTANCE)
        addStatement("self.title = %S", title ?: "")
        addStatement("self.status = %L", status ?: 0)
        addStatement(
          "self.detail = %L",
          detail?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("nil"),
        )
        addStatement("self.instance = instance")
        addStatement("self.parameters = nil")
        customFields.forEach { field ->
          addStatement("self.%N = %N", field.name.swiftIdentifierName, field.name.swiftIdentifierName)
        }
      }.build()

  private fun problemDescriptionProperty(customFields: List<GeneratedModelProperty>): PropertySpec =
    PropertySpec
      .builder("description", STRING, PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .addStatement(
            "return %T(%T.self)\n" +
              ".add(type, named: %S)\n" +
              ".add(title, named: %S)\n" +
              ".add(status, named: %S)\n" +
              ".add(detail, named: %S)\n" +
              ".add(instance, named: %S)\n" +
              customFields.map { ".add(%N, named: %S)\n" }.joinToString("") +
              ".build()",
            DESCRIPTION_BUILDER,
            SelfTypeName.INSTANCE,
            "type",
            "title",
            "status",
            "detail",
            "instance",
            *customFields
              .flatMap { field -> listOf(field.name.swiftIdentifierName, field.name.swiftIdentifierName) }
              .toTypedArray(),
          ).build(),
      ).build()

  private fun problemDecoderConstructor(
    customFields: List<GeneratedModelProperty>,
    codingKeysTypeName: DeclaredTypeName,
  ): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .apply {
        addStatement("let problem = try %T(from: decoder)", GENERIC_PROBLEM)
        if (customFields.isNotEmpty()) {
          addStatement("let container = try decoder.container(keyedBy: %T.self)", codingKeysTypeName)
        }
        customFields.forEach { field ->
          addStatement(
            "self.%N = try container.decode%L(%T.self, forKey: %T.%N)",
            field.name.swiftIdentifierName,
            if (field.swiftProblemFieldTypeName().optional) "IfPresent" else "",
            field.swiftProblemFieldTypeName().makeNonOptional(),
            codingKeysTypeName,
            field.name.swiftIdentifierName,
          )
        }
        addStatement("self.type = problem.type")
        addStatement("self.title = problem.title")
        addStatement("self.status = problem.status")
        addStatement("self.detail = problem.detail")
        addStatement("self.instance = problem.instance")
        addStatement("self.parameters = nil")
      }.build()

  private fun problemEncoderFunction(
    customFields: List<GeneratedModelProperty>,
    codingKeysTypeName: DeclaredTypeName,
  ): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(PUBLIC)
      .addParameter("to", "encoder", ENCODER)
      .throws(true)
      .apply {
        addStatement(
          "let problem = %T(type: type,%Wtitle: title,%Wstatus: status,%Wdetail: detail,%Winstance: instance,%Wparameters: parameters)",
          GENERIC_PROBLEM,
        )
        addStatement("try problem.encode(to: encoder)")
        if (customFields.isNotEmpty()) {
          addStatement("var container = encoder.container(keyedBy: %T.self)", codingKeysTypeName)
        }
        customFields.forEach { field ->
          addStatement(
            "try container.encode(self.%N, forKey: %T.%N)",
            field.name.swiftIdentifierName,
            codingKeysTypeName,
            field.name.swiftIdentifierName,
          )
        }
      }.build()

  private fun GeneratedModelProperty.swiftProblemFieldTypeName(): TypeName =
    type
      .swiftTypeName()
      .run {
        if (type.nullable) {
          makeOptional()
        } else {
          makeNonOptional()
        }
      }

  private fun problemCodingKeysTypeOrNull(customFields: List<GeneratedModelProperty>): TypeSpec? {
    if (customFields.isEmpty()) {
      return null
    }

    return TypeSpec
      .enumBuilder("CodingKeys")
      .addModifiers(FILEPRIVATE)
      .addSuperType(STRING)
      .addSuperType(CODING_KEY)
      .apply {
        customFields.forEach { field ->
          addEnumCase(field.name.swiftIdentifierName, field.serializationName ?: field.name)
        }
      }.build()
  }

  private fun GeneratedModel.swiftObjectTypeSpec(
    outputDirectory: OutputDirectory = OutputDirectory.Models,
    outputGroup: String? = null,
  ): TypeSpec.Builder {
    val typeName = swiftDeclaredTypeName()
    val inheritedModel = inherits.firstOrNull()?.modelOrNull(apiIndex)
    val inheritedTypeName = inheritedModel?.swiftDeclaredTypeName()
    val inheritedProperties = inheritedModel?.allConstructorProperties().orEmpty()
    val isRootProblemModel = isProblemModel && inheritedTypeName == null
    val isProtocolHierarchyRoot = isProtocolHierarchyRootModel
    val isProtocolHierarchyValueModel = isProtocolHierarchyValueModel
    val isProblemHierarchyProtocolModel = isProblemHierarchyProtocolModel
    val isProblemHierarchyValueModel = isProblemHierarchyValueModel
    val isInheritedObjectValueModel = isInheritedObjectValueModel
    val isProtocolModel = isProtocolHierarchyRoot || isProblemHierarchyProtocolModel
    val isRecursiveReferenceModel = isRecursiveSwiftObjectModel && !isProtocolModel
    val flattensInheritedProperties =
      isProtocolHierarchyValueModel ||
        isProblemHierarchyValueModel ||
        isInheritedObjectValueModel
    val localProperties =
      localConstructorProperties(inheritedProperties)
        .map { property ->
          if (isRootProblemModel) {
            property.normalizedSwiftBaseProblemProperty()
          } else {
            property
          }
        }
    val storedLocalProperties =
      if (isRootProblemModel || isProblemHierarchyProtocolModel) {
        localProperties.filterNot { property -> property.isSatisfiedByBaseProblemClass() }
      } else {
        localProperties
      }
    val identifiableProperty = identifiablePropertyOrNull()
    val discriminatorProperty = discriminatorPropertyOrNull()
    val isValueModel =
      !isRecursiveReferenceModel &&
        (
          isSimpleObjectValueModel ||
            isPatchableObjectValueModel ||
            isProtocolHierarchyValueModel ||
            isProblemHierarchyValueModel ||
            isInheritedObjectValueModel ||
            isExternalDiscriminatorEnvelopeValueModel
        )
    val isImmutableModel = isValueModel || isRecursiveReferenceModel
    val storedProperties =
      if (flattensInheritedProperties) {
        inheritedProperties + localProperties
      } else {
        storedLocalProperties
      }
    val baseTypeBuilder =
      if (isProtocolModel) {
        TypeSpec.protocolBuilder(typeName)
      } else if (isValueModel) {
        TypeSpec.structBuilder(typeName)
      } else {
        TypeSpec.classBuilder(typeName)
      }
    if (isRecursiveReferenceModel && !hasInheritingModels) {
      baseTypeBuilder.addModifiers(FINAL)
    }
    val typeBuilder =
      baseTypeBuilder
        .addModifiers(PUBLIC)
        .addSwiftDoc(documentation)
        .apply {
          if (isProblemHierarchyProtocolModel) {
            addSuperType(inheritedTypeName ?: runtimeProblemTypeName)
          } else if (isProtocolHierarchyRoot) {
            addSuperTypes(listOf(CODABLE, CUSTOM_STRING_CONVERTIBLE, SENDABLE))
          } else if (inheritedTypeName != null && !flattensInheritedProperties) {
            addSuperType(inheritedTypeName)
          } else if (isProblemHierarchyValueModel) {
            addSuperType(inheritedTypeName ?: runtimeProblemTypeName)
          } else if (isProtocolHierarchyValueModel) {
            addSuperType(inheritedTypeName ?: CODABLE)
          } else {
            addSuperTypes(
              buildList {
                add(CODABLE)
                add(CUSTOM_STRING_CONVERTIBLE)
                if (isValueModel || isRecursiveReferenceModel && !hasInheritingModels) {
                  add(SENDABLE)
                }
              },
            )
          }
          if (identifiableProperty != null) {
            addSuperType(IDENTIFIABLE)
          }
        }

    discriminatorProperty?.let { property ->
      typeBuilder.addProperty(
        swiftDiscriminatorProperty(
          property,
          isProtocolHierarchyRoot || isProblemHierarchyProtocolModel,
          isProtocolHierarchyValueModel || isProblemHierarchyValueModel,
        ),
      )
    }

    storedProperties.forEach { property ->
      val propertyTypeName = property.swiftModelPropertyTypeName(patchable)
      val propertyBuilder =
        if (isProtocolModel) {
          PropertySpec
            .abstractBuilder(property.name.swiftIdentifierName, propertyTypeName)
            .getter(FunctionSpec.getterBuilder().build())
        } else if (isProblemModel || isImmutableModel) {
          PropertySpec.builder(property.name.swiftIdentifierName, propertyTypeName, PUBLIC)
        } else {
          PropertySpec.varBuilder(property.name.swiftIdentifierName, propertyTypeName, PUBLIC)
        }
      typeBuilder.addProperty(
        propertyBuilder
          .addSwiftDoc(property.documentation)
          .build(),
      )
    }

    if (!isProtocolModel && identifiableProperty != null && identifiableProperty.name != "id") {
      typeBuilder.addProperty(swiftIdentifiableIdProperty(identifiableProperty, patchable))
    }

    if (isProblemHierarchyValueModel && storedProperties.none { property -> property.name == "parameters" }) {
      typeBuilder.addProperty(
        PropertySpec
          .builder("parameters", DICTIONARY.parameterizedBy(STRING, ANY_VALUE).makeOptional(), PUBLIC)
          .build(),
      )
    }

    if (!isProtocolModel) {
      typeBuilder.addProperty(
        debugDescriptionProperty(
          typeName,
          if (inheritedTypeName != null && discriminatorProperty != null) {
            listOf(discriminatorProperty) + inheritedProperties + localProperties
          } else {
            inheritedProperties + localProperties
          },
          inheritedTypeName != null && !flattensInheritedProperties,
        ),
      )
      typeBuilder.addFunction(
        modelConstructor(
          if (flattensInheritedProperties) emptyList() else inheritedProperties,
          if (flattensInheritedProperties) {
            inheritedProperties + localProperties
          } else {
            localProperties
          },
          inheritedTypeName.takeUnless { flattensInheritedProperties },
          patchable,
          false,
          isProblemHierarchyValueModel && storedProperties.none { property -> property.name == "parameters" },
        ),
      )
      typeBuilder.addFunction(
        modelDecoderConstructor(
          storedProperties,
          inheritedTypeName.takeUnless { flattensInheritedProperties },
          patchable,
          false,
          isValueModel,
          isProblemHierarchyValueModel && storedProperties.none { property -> property.name == "parameters" },
        ),
      )
      typeBuilder.addFunction(
        modelEncoderFunction(
          storedProperties,
          inheritedTypeName.takeUnless { flattensInheritedProperties },
          discriminatorProperty,
          patchable,
          false,
        ),
      )
      (inheritedProperties + localProperties).forEach { property ->
        typeBuilder.addFunction(
          modelWithFunction(
            typeName,
            property,
            inheritedProperties + localProperties,
            property in inheritedProperties && !flattensInheritedProperties,
            patchable,
          ),
        )
      }
    }
    referenceTypeOrNull(typeName, outputDirectory, outputGroup)?.let { referenceType ->
      if (!isProtocolModel) {
        typeBuilder.addType(referenceType)
      }
    }
    patchOpExtensionOrNull(typeName, inheritedProperties + localProperties, patchable)?.let { extension ->
      typeBuilder.associatedExtensions.add(extension)
    }
    if (!isProtocolModel) {
      typeBuilder.addType(
        codingKeysType(
          storedProperties,
          if (inherits.isEmpty() || isProtocolHierarchyValueModel || isProblemHierarchyValueModel) {
            discriminatorProperty
          } else {
            null
          },
        ),
      )
    }

    return typeBuilder
  }

  private fun GeneratedModel.constructorProperties(): List<GeneratedModelProperty> =
    properties.filterNot { property -> property.name == discriminatorNameOrNull() }

  private fun GeneratedModel.identifiablePropertyOrNull(): GeneratedModelProperty? {
    if (inherits.isNotEmpty() || !typeRegistry.options.contains(SwiftTypeRegistry.Option.DefaultIdentifiableTypes)) {
      return null
    }

    val properties = constructorProperties()
    properties.firstOrNull { property -> property.name == "id" }?.let { property -> return property }

    val suffixProperties =
      properties.filter { property ->
        property.name.endsWith("Id") || property.name.endsWith("ID")
      }

    return suffixProperties.singleOrNull()
  }

  private fun swiftIdentifiableIdProperty(
    property: GeneratedModelProperty,
    patchable: Boolean,
  ): PropertySpec =
    PropertySpec
      .builder("id", property.swiftModelPropertyTypeName(patchable), PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .addStatement("return self.%N", property.name.swiftIdentifierName)
          .build(),
      ).build()

  private fun GeneratedModel.localConstructorProperties(
    inheritedProperties: List<GeneratedModelProperty>,
  ): List<GeneratedModelProperty> {
    val inheritedWireNames = inheritedProperties.map { property -> property.wireName }.toSet()
    return constructorProperties().filterNot { property -> property.wireName in inheritedWireNames }
  }

  private fun GeneratedModel.allConstructorProperties(): List<GeneratedModelProperty> {
    val inheritedProperties =
      inherits.flatMap { inherited ->
        inherited.modelOrNull(apiIndex)?.allConstructorProperties().orEmpty()
      }
    val localProperties =
      localConstructorProperties(inheritedProperties)
        .map { property ->
          if (isProblemModel && inherits.isEmpty()) {
            property.normalizedSwiftBaseProblemProperty()
          } else {
            property
          }
        }
    return inheritedProperties + localProperties
  }

  private val GeneratedModel.isProblemModel: Boolean
    get() =
      inheritanceRootModel().let { root ->
        root.name.endsWith("Problem") &&
          root.properties.any { property -> property.name == "type" } &&
          root.properties.any { property -> property.name == "title" } &&
          root.properties.any { property -> property.name == "status" }
      }

  private fun GeneratedModel.inheritanceRootModel(): GeneratedModel {
    val parent = inherits.firstOrNull()?.modelOrNull(apiIndex)
    return parent?.inheritanceRootModel() ?: this
  }

  private val GeneratedModel.hasInheritingModels: Boolean
    get() =
      api.models.any { model ->
        model.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == this }
      }

  private fun GeneratedModelProperty.isSatisfiedByBaseProblemClass(): Boolean =
    serializationName == null && name in baseProblemProperties

  private fun GeneratedModelProperty.normalizedSwiftBaseProblemProperty(): GeneratedModelProperty =
    if (isSatisfiedByBaseProblemClass()) {
      when (name) {
        in optionalBaseProblemProperties -> copy(required = false, type = type.copy(nullable = true))
        else -> copy(required = true, type = type.copy(nullable = false))
      }
    } else {
      this
    }

  private val GeneratedModel.isObjectUnionEnum: Boolean
    get() =
      kind == GeneratedModel.Kind.UNION &&
        aliases.size >= 2 &&
        unionCaseModels().size == aliases.size

  private val GeneratedModel.hasSwiftReferenceType: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        discriminator != null &&
        !externallyDiscriminated &&
        inherits.isEmpty() &&
        hasInheritingModels

  private val GeneratedModel.isSwiftSendableModel: Boolean
    get() =
      kind == GeneratedModel.Kind.ENUM ||
        kind == GeneratedModel.Kind.SCALAR_ALIAS ||
        kind == GeneratedModel.Kind.ARRAY ||
        kind == GeneratedModel.Kind.MAP ||
        isSimpleObjectValueModel ||
        isPatchableObjectValueModel ||
        isInheritedObjectValueModel ||
        (isProtocolHierarchyValueModel && !isRecursiveSwiftObjectModel) ||
        isProblemHierarchyValueModel ||
        isExternalDiscriminatorEnvelopeValueModel ||
        (isRecursiveSwiftObjectModel && !hasInheritingModels)

  private fun GeneratedModel.unionCaseModels(): List<GeneratedModel> =
    aliases
      .mapNotNull { alias -> alias.modelOrNull(apiIndex) }
      .filter { model -> model.kind == GeneratedModel.Kind.OBJECT }

  private val GeneratedModel.unionCaseName: String
    get() = name.toLowerCamelCase().swiftIdentifierName

  private fun GeneratedModel.allModelProperties(): List<GeneratedModelProperty> =
    inherits.flatMap { inherited -> inherited.modelOrNull(apiIndex)?.allModelProperties().orEmpty() } +
      properties

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

  private data class UnionDiscriminator(
    val wireName: String,
    val cases: List<UnionDiscriminatorCase>,
  )

  private data class UnionDiscriminatorCase(
    val value: String,
    val model: GeneratedModel,
  )

  private data class TypedEventEnvelope(
    val model: GeneratedModel,
    val discriminatorProperty: GeneratedModelProperty,
    val dataProperty: GeneratedModelProperty,
    val cases: List<TypedEventEnvelopeCase>,
  )

  private data class TypedEventEnvelopeCase(
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

  private val GeneratedModelProperty.wireName: String
    get() = serializationName ?: name

  private fun List<String>.presenceExpression(operator: String): CodeBlock =
    if (isEmpty()) {
      CodeBlock.of("true")
    } else {
      map { wireName -> CodeBlock.of("object[%S] != nil", wireName) }.joinToCode(" $operator ")
    }

  private fun List<String>.keyPresenceExpression(operator: String): CodeBlock =
    if (isEmpty()) {
      CodeBlock.of("true")
    } else {
      map { wireName -> CodeBlock.of("keys.contains(.%N)", wireName.swiftIdentifierName) }.joinToCode(" $operator ")
    }

  private fun GeneratedModel.discriminatorPropertyOrNull(
    discriminator: String? = discriminatorNameOrNull(),
  ): GeneratedModelProperty? {
    discriminator ?: return null
    val discriminatorProperty =
      properties.firstOrNull { property -> property.name == discriminator }
        ?: inherits.firstNotNullOfOrNull { inherited ->
          inherited
            .modelOrNull(apiIndex)
            ?.discriminatorPropertyOrNull(discriminator)
        }
        ?: return null

    return discriminatorProperty
  }

  private fun GeneratedModel.discriminatorNameOrNull(): String? =
    discriminator
      ?: inherits.firstNotNullOfOrNull { inherited -> inherited.modelOrNull(apiIndex)?.discriminatorNameOrNull() }

  private fun GeneratedModel.swiftDiscriminatorProperty(
    property: GeneratedModelProperty,
    protocolRequirement: Boolean = false,
    valueModel: Boolean = false,
  ): PropertySpec {
    val propertyType = property.swiftTypeName().makeNonOptional()
    val discriminatorValue = discriminatorValue ?: name.takeIf { inherits.isNotEmpty() }

    val propertyBuilder =
      if (protocolRequirement) {
        PropertySpec.abstractBuilder(property.name.swiftIdentifierName, propertyType)
      } else {
        PropertySpec.builder(property.name.swiftIdentifierName, propertyType, PUBLIC)
      }

    return propertyBuilder
      .addSwiftDoc(property.documentation)
      .apply {
        if (protocolRequirement) {
          getter(FunctionSpec.getterBuilder().build())
          return@apply
        }
        if (discriminatorValue == null) {
          getter(
            FunctionSpec
              .getterBuilder()
              .addStatement("fatalError(\"abstract type method\")")
              .build(),
          )
        } else {
          val resolvedDiscriminatorValue = discriminatorValue
          if (!valueModel) {
            addModifiers(OVERRIDE)
          }
          getter(
            FunctionSpec
              .getterBuilder()
              .addStatement("return %L", resolvedDiscriminatorValue.swiftValueCode(propertyType, property.type))
              .build(),
          )
        }
      }.build()
  }

  private fun GeneratedModel.referenceTypeOrNull(
    typeName: DeclaredTypeName,
    outputDirectory: OutputDirectory,
    outputGroup: String?,
  ): TypeSpec? {
    if (discriminator == null || externallyDiscriminated || inherits.isNotEmpty()) {
      return null
    }

    val discriminatorProperty = discriminatorPropertyOrNull() ?: return null
    val inheritingModels =
      api
        .models
        .filter { model -> model.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == this } }

    if (inheritingModels.isEmpty()) {
      return null
    }

    val anyRefTypeName =
      swiftReferenceTypeName(typeName)
    typeRegistry.addReferenceType(typeName, anyRefTypeName)
    val referenceValueTypeName =
      if (isProtocolHierarchyRootModel || isProblemHierarchyProtocolModel) {
        typeName.swiftExistentialTypeName()
      } else {
        typeName
      }

    val referenceType =
      TypeSpec
        .enumBuilder(anyRefTypeName)
        .addModifiers(PUBLIC)
        .addSuperTypes(
          buildList {
            add(CODABLE)
            add(CUSTOM_STRING_CONVERTIBLE)
            if (inheritingModels.all { model -> model.isSwiftSendableModel }) {
              add(SENDABLE)
            }
          },
        ).apply {
          inheritingModels.forEach { model ->
            addEnumCase(model.discriminatorCaseName, model.swiftDeclaredTypeName())
          }

          addProperty(
            PropertySpec
              .builder("value", referenceValueTypeName, PUBLIC)
              .getter(
                FunctionSpec
                  .getterBuilder()
                  .apply {
                    beginControlFlow("switch", "self")
                    inheritingModels.forEach { model ->
                      addStatement("case .%N(let value):%Wreturn value", model.discriminatorCaseName)
                    }
                    endControlFlow("switch")
                  }.build(),
              ).build(),
          )
          addProperty(
            PropertySpec
              .builder("debugDescription", STRING, PUBLIC)
              .getter(
                FunctionSpec
                  .getterBuilder()
                  .apply {
                    beginControlFlow("switch", "self")
                    inheritingModels.forEach { model ->
                      addStatement("case .%N(let value):%Wreturn value.debugDescription", model.discriminatorCaseName)
                    }
                    endControlFlow("switch")
                  }.build(),
              ).build(),
          )
          addFunction(
            FunctionSpec
              .constructorBuilder()
              .addModifiers(PUBLIC)
              .addParameter("value", referenceValueTypeName)
              .apply {
                beginControlFlow("switch", "value")
                inheritingModels.forEach { model ->
                  addStatement(
                    "case let value as %T:%Wself = .%N(value)",
                    model.swiftDeclaredTypeName(),
                    model.discriminatorCaseName,
                  )
                }
                addStatement("default:%WfatalError(\"Invalid value type\")")
                endControlFlow("switch")
              }.build(),
          )
          addFunction(
            FunctionSpec
              .constructorBuilder()
              .addModifiers(PUBLIC)
              .addParameter("from", "decoder", DECODER)
              .throws(true)
              .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
              .addStatement(
                "let type = try container.decode(%T.self, forKey: CodingKeys.%N)",
                STRING,
                discriminatorProperty.name.swiftIdentifierName,
              ).apply {
                beginControlFlow("switch", "type")
                inheritingModels.forEach { model ->
                  addStatement(
                    "case %S:%Wself = .%N(try %T(from: decoder))",
                    model.discriminatorValue ?: model.name,
                    model.discriminatorCaseName,
                    model.swiftDeclaredTypeName(),
                  )
                }
                addStatement(
                  "default:\nthrow %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
                  DECODING_ERROR,
                  discriminatorProperty.name.swiftIdentifierName,
                  "unsupported value for \"${discriminatorProperty.name}\"",
                )
                endControlFlow("switch")
              }.build(),
          )
          addFunction(
            FunctionSpec
              .builder("encode")
              .addModifiers(PUBLIC)
              .addParameter("to", "encoder", ENCODER)
              .throws(true)
              .addStatement("var container = encoder.singleValueContainer()")
              .apply {
                beginControlFlow("switch", "self")
                inheritingModels.forEach { model ->
                  addStatement("case .%N(let value):%Wtry container.encode(value)", model.discriminatorCaseName)
                }
                endControlFlow("switch")
              }.build(),
          )
          addType(codingKeysType(listOf(discriminatorProperty)))
        }

    if (isProtocolHierarchyRootModel || isProblemHierarchyProtocolModel) {
      typeRegistry.addModelType(
        anyRefTypeName,
        referenceType,
        outputDirectory = outputDirectory,
        outputGroup = outputGroup,
      )
      return null
    }

    return referenceType.build()
  }

  private fun patchOpExtensionOrNull(
    typeName: DeclaredTypeName,
    properties: List<GeneratedModelProperty>,
    patchable: Boolean,
  ): ExtensionSpec? {
    if (!patchable) {
      return null
    }

    val mergeParameters =
      properties
        .map { property ->
          CodeBlock.of(
            "%N: %N",
            property.name.swiftIdentifierName,
            property.name.swiftIdentifierName,
          )
        }.joinToCode(",%W")

    return ExtensionSpec
      .builder(ANY_PATCH_OP)
      .addConditionalConstraint(typeVariable("Value", bound(SAME_TYPE, typeName)))
      .addFunction(
        FunctionSpec
          .builder("merge")
          .addModifiers(PUBLIC, STATIC)
          .returns(SelfTypeName.INSTANCE)
          .apply {
            properties.forEach { property ->
              addParameter(
                ParameterSpec
                  .builder(property.name.swiftIdentifierName, property.swiftPatchOpTypeName().makeOptional())
                  .defaultValue(".none")
                  .build(),
              )
            }
          }.addStatement(
            "%T.merge(%T(%L))",
            SelfTypeName.INSTANCE,
            typeName,
            mergeParameters,
          ).build(),
      ).build()
  }

  private val GeneratedModel.discriminatorCaseName: String
    get() = (discriminatorValue ?: name).swiftEnumCaseName

  private fun GeneratedModel.discriminatorWireValueCode(discriminatorProperty: GeneratedModelProperty): CodeBlock =
    CodeBlock.of(
      "%L",
      (discriminatorValue ?: name).swiftValueCode(
        discriminatorProperty.swiftTypeName().makeNonOptional(),
        discriminatorProperty.type,
      ),
    )

  private fun GeneratedModel.swiftReferenceTypeName(
    typeName: DeclaredTypeName = swiftDeclaredTypeName(),
  ): DeclaredTypeName =
    if (isProtocolHierarchyRootModel || isProblemHierarchyProtocolModel) {
      DeclaredTypeName.typeName(
        "${typeName.moduleName}.${typeName.simpleNames.joinToString("")}Ref",
      )
    } else {
      typeName.nestedType("AnyRef")
    }

  private fun TypeName.swiftExistentialTypeName(): TypeName {
    val optional = optional
    val baseType = makeNonOptional()
    val existentialType =
      when (baseType) {
        is DeclaredTypeName -> DeclaredTypeName.typeName(".any ${baseType.canonicalName}")
        else -> baseType
      }
    return if (optional) {
      existentialType.makeOptional()
    } else {
      existentialType
    }
  }

  private fun debugDescriptionProperty(
    typeName: DeclaredTypeName,
    properties: List<GeneratedModelProperty>,
    override: Boolean = false,
  ): PropertySpec =
    PropertySpec
      .builder("debugDescription", STRING, PUBLIC)
      .apply {
        if (override) {
          addModifiers(OVERRIDE)
        }
      }.getter(
        FunctionSpec
          .getterBuilder()
          .addCode(
            CodeBlock
              .builder()
              .add("%[return %T(%T.self)\n", DESCRIPTION_BUILDER, typeName)
              .apply {
                properties.forEach { property ->
                  add(".add(%N, named: %S)\n", property.name.swiftIdentifierName, property.name.swiftIdentifierName)
                }
              }.add(".build()%]\n")
              .build(),
          ).build(),
      ).build()

  private fun modelConstructor(
    inheritedProperties: List<GeneratedModelProperty>,
    localProperties: List<GeneratedModelProperty>,
    inheritedTypeName: DeclaredTypeName?,
    patchable: Boolean,
    isRootProblemModel: Boolean,
    addNilProblemParameters: Boolean = false,
  ): FunctionSpec {
    val modifiers =
      if (inheritedTypeName != null && localProperties.isEmpty()) {
        arrayOf(PUBLIC, OVERRIDE)
      } else {
        arrayOf(PUBLIC)
      }

    return FunctionSpec
      .constructorBuilder()
      .addModifiers(*modifiers)
      .apply {
        (inheritedProperties + localProperties).forEach { property ->
          addParameter(
            ParameterSpec
              .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(patchable))
              .apply {
                if (patchable) {
                  defaultValue(".none")
                } else if (property.swiftTypeName().optional) {
                  defaultValue("nil")
                }
              }.build(),
          )
        }
        localProperties
          .filterNot { property -> isRootProblemModel && property.isSatisfiedByBaseProblemClass() }
          .forEach { property ->
            addStatement("self.%N = %N", property.name.swiftIdentifierName, property.name.swiftIdentifierName)
          }
        if (addNilProblemParameters) {
          addStatement("self.parameters = nil")
        }
        if (isRootProblemModel) {
          addStatement(
            "super.init(type: %L,%Wtitle: %L,%Wstatus: %L,%Wdetail: %L,%Winstance: %L,%Wparameters: nil)",
            problemModelBaseArgument(localProperties, "type", "%T(string: %S)!", URL, "about:blank"),
            problemModelBaseArgument(localProperties, "title", "%S", ""),
            problemModelBaseArgument(localProperties, "status", "%L", 0),
            problemModelBaseArgument(localProperties, "detail", "%L", "nil"),
            problemModelBaseArgument(localProperties, "instance", "%L", "nil"),
          )
        } else if (inheritedTypeName != null) {
          val inheritedConstructorParameters =
            inheritedProperties
              .map { property ->
                CodeBlock.of(
                  "%N: %N",
                  property.name.swiftIdentifierName,
                  property.name.swiftIdentifierName,
                )
              }.joinToCode(",%W")

          addStatement(
            "super.init(%L)",
            inheritedConstructorParameters,
          )
        }
      }.build()
  }

  private fun problemModelBaseArgument(
    properties: List<GeneratedModelProperty>,
    propertyName: String,
    defaultFormat: String,
    vararg defaultArguments: Any,
  ): CodeBlock {
    val property =
      properties.firstOrNull { candidate -> candidate.name == propertyName }
        ?: return CodeBlock.of(defaultFormat, *defaultArguments)
    val name = property.name.swiftIdentifierName
    return if (property.swiftModelPropertyTypeName(false).optional) {
      CodeBlock.of("%N ?? %L", name, CodeBlock.of(defaultFormat, *defaultArguments))
    } else {
      CodeBlock.of("%N", name)
    }
  }

  private fun modelDecoderConstructor(
    localProperties: List<GeneratedModelProperty>,
    inheritedTypeName: DeclaredTypeName?,
    patchable: Boolean,
    isRootProblemModel: Boolean,
    isValueModel: Boolean = false,
    addNilProblemParameters: Boolean = false,
  ): FunctionSpec {
    val modifiers =
      if (isValueModel) {
        arrayOf(PUBLIC)
      } else {
        arrayOf(PUBLIC, REQUIRED)
      }

    return FunctionSpec
      .constructorBuilder()
      .addModifiers(*modifiers)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addStatement(
        "let %L = try decoder.container(keyedBy: CodingKeys.self)",
        if (localProperties.isEmpty()) "_" else "container",
      ).apply {
        localProperties.filter { property -> property.externalDiscriminator == null }.forEach { property ->
          val coderSuffix =
            when {
              patchable -> "IfExists"
              property.swiftTypeName().optional -> "IfPresent"
              else -> ""
            }
          val codingTypeName =
            if (patchable) {
              property.type.swiftTypeName().makeNonOptional()
            } else {
              property.swiftTypeName().makeNonOptional()
            }
          addStatement(
            "self.%N = try container.decode%L(%T.self, forKey: .%N)",
            property.name.swiftIdentifierName,
            coderSuffix,
            codingTypeName,
            property.name.swiftIdentifierName,
          )
        }
        localProperties.filter { property -> property.externalDiscriminator != null }.forEach { property ->
          addExternalDiscriminatorDecoder(property, localProperties)
        }
        if (addNilProblemParameters) {
          addStatement("self.parameters = nil")
        }
        if (inheritedTypeName != null || isRootProblemModel) {
          addStatement("try super.init(from: decoder)")
        }
      }.build()
  }

  private fun modelEncoderFunction(
    localProperties: List<GeneratedModelProperty>,
    inheritedTypeName: DeclaredTypeName?,
    discriminatorProperty: GeneratedModelProperty?,
    patchable: Boolean,
    isRootProblemModel: Boolean,
  ): FunctionSpec =
    FunctionSpec
      .builder("encode")
      .addModifiers(
        *if (inheritedTypeName == null && !isRootProblemModel) {
          arrayOf(PUBLIC)
        } else {
          arrayOf(PUBLIC, OVERRIDE)
        },
      ).addParameter("to", "encoder", ENCODER)
      .throws(true)
      .apply {
        if (inheritedTypeName != null || isRootProblemModel) {
          addStatement("try super.encode(to: encoder)")
        }
        if (localProperties.isNotEmpty() || discriminatorProperty != null && inheritedTypeName == null) {
          addStatement("var container = encoder.container(keyedBy: CodingKeys.self)")
        }
        discriminatorProperty?.takeIf { inheritedTypeName == null }?.let { discriminatorProperty ->
          addStatement(
            "try container.encode(self.%N, forKey: .%N)",
            discriminatorProperty.name.swiftIdentifierName,
            discriminatorProperty.name.swiftIdentifierName,
          )
        }
        localProperties.filter { property -> property.externalDiscriminator == null }.forEach { property ->
          addStatement(
            "try container.encode%L(self.%N, forKey: .%N)",
            when {
              patchable -> "IfExists"
              property.swiftTypeName().optional -> "IfPresent"
              else -> ""
            },
            property.name.swiftIdentifierName,
            property.name.swiftIdentifierName,
          )
        }
        localProperties.filter { property -> property.externalDiscriminator != null }.forEach { property ->
          addExternalDiscriminatorEncoder(property, localProperties)
        }
      }.build()

  private fun FunctionSpec.Builder.addExternalDiscriminatorDecoder(
    property: GeneratedModelProperty,
    properties: List<GeneratedModelProperty>,
  ) {
    val discriminatorProperty = property.externalDiscriminatorProperty(properties)
    val propertyTypeName = property.swiftTypeName()
    val coderSuffix = if (propertyTypeName.optional) "IfPresent" else ""

    beginControlFlow("switch", "self.%N", discriminatorProperty.name.swiftIdentifierName)
    property.externalDiscriminatorModels().forEach { model ->
      addStatement(
        "case %L:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
        model.discriminatorWireValueCode(discriminatorProperty),
        property.name.swiftIdentifierName,
        coderSuffix,
        model.swiftDeclaredTypeName(),
        property.name.swiftIdentifierName,
      )
    }
    addStatement(
      "default:\nthrow %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
      DECODING_ERROR,
      discriminatorProperty.name.swiftIdentifierName,
      "unsupported value for \"${discriminatorProperty.name}\"",
    )
    endControlFlow("switch")
  }

  private fun FunctionSpec.Builder.addExternalDiscriminatorEncoder(
    property: GeneratedModelProperty,
    properties: List<GeneratedModelProperty>,
  ) {
    val discriminatorProperty = property.externalDiscriminatorProperty(properties)
    val propertyTypeName = property.swiftTypeName()
    val coderSuffix = if (propertyTypeName.optional) "IfPresent" else ""
    val propertyTypeSuffix = if (propertyTypeName.optional) "?" else ""

    beginControlFlow("switch", "self.%N", discriminatorProperty.name.swiftIdentifierName)
    property.externalDiscriminatorModels().forEach { model ->
      addStatement(
        "case %L:%Wtry container.encode%L(self.%N as! %T%L, forKey: .%N)",
        model.discriminatorWireValueCode(discriminatorProperty),
        coderSuffix,
        property.name.swiftIdentifierName,
        model.swiftDeclaredTypeName(),
        propertyTypeSuffix,
        property.name.swiftIdentifierName,
      )
    }
    addStatement(
      "default:\n" +
        "throw %T.invalidValue(%>\nself.%N,\n%T(%>\ncodingPath: encoder.codingPath + [CodingKeys.%N],\ndebugDescription: %S%<\n)%<\n)",
      ENCODING_ERROR,
      discriminatorProperty.name.swiftIdentifierName,
      ENCODING_ERROR.nestedType("Context"),
      discriminatorProperty.name.swiftIdentifierName,
      "unsupported value for \"${discriminatorProperty.name}\"",
    )
    endControlFlow("switch")
  }

  private fun GeneratedModelProperty.externalDiscriminatorProperty(
    properties: List<GeneratedModelProperty>,
  ): GeneratedModelProperty {
    val externalDiscriminator = externalDiscriminator ?: genError("Property '$name' is missing external discriminator")
    return properties.firstOrNull { property -> property.name == externalDiscriminator }
      ?: genError("External discriminator '$externalDiscriminator' not found for property '$name'")
  }

  private fun GeneratedModelProperty.externalDiscriminatorModels(): List<GeneratedModel> {
    val baseModel = type.modelOrNull(apiIndex) ?: genError("External discriminator property '$name' has no model type")
    val mappedModels =
      baseModel.discriminatorMappings.values
        .mapNotNull { type -> type.modelOrNull(apiIndex) }

    return mappedModels.ifEmpty {
      api.models.filter { model -> model.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == baseModel } }
    }
  }

  private fun modelWithFunction(
    typeName: DeclaredTypeName,
    property: GeneratedModelProperty,
    properties: List<GeneratedModelProperty>,
    override: Boolean = false,
    patchable: Boolean = false,
  ): FunctionSpec =
    FunctionSpec
      .builder("with${property.name.toUpperCamelCase()}")
      .addModifiers(PUBLIC)
      .apply {
        if (override) {
          addModifiers(OVERRIDE)
        }
      }.addParameter(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(patchable))
      .returns(typeName)
      .addStatement(
        "return %T(%L)",
        typeName,
        properties
          .map { current ->
            val valueCode =
              if (current == property) {
                CodeBlock.of("%N", property.name.swiftIdentifierName)
              } else {
                CodeBlock.of("%N", current.name.swiftIdentifierName)
              }
            CodeBlock.of("%N: %L", current.name.swiftIdentifierName, valueCode)
          }.joinToCode(",%W"),
      ).build()

  private fun codingKeysType(
    properties: List<GeneratedModelProperty>,
    discriminatorProperty: GeneratedModelProperty? = null,
  ): TypeSpec =
    TypeSpec
      .enumBuilder("CodingKeys")
      .addModifiers(FILEPRIVATE)
      .apply {
        val codingKeyProperties = listOfNotNull(discriminatorProperty) + properties
        if (codingKeyProperties.isNotEmpty()) {
          addSuperType(STRING)
        }
        addSuperType(CODING_KEY)
        discriminatorProperty?.let { property ->
          addEnumCase(property.name.swiftIdentifierName, property.serializationName ?: property.name)
        }
        properties.forEach { property ->
          addEnumCase(property.name.swiftIdentifierName, property.serializationName ?: property.name)
        }
      }.build()

  private fun GeneratedModelProperty.swiftTypeName(): TypeName =
    type
      .swiftStoredTypeName(externalDiscriminator == null)
      .run {
        if (required && !type.nullable) {
          makeNonOptional()
        } else {
          makeOptional()
        }
      }

  private fun GeneratedModelProperty.swiftModelPropertyTypeName(patchable: Boolean): TypeName =
    if (patchable) {
      swiftPatchOpTypeName().makeOptional()
    } else {
      swiftTypeName()
    }

  private fun GeneratedModelProperty.swiftPatchOpTypeName(): TypeName {
    val base = if (type.nullable) PATCH_OP else UPDATE_OP
    return base.parameterizedBy(type.swiftTypeName().makeNonOptional())
  }

  private fun GeneratedOperation.operationFunction(service: GeneratedService): FunctionSpec {
    val response = primarySuccessResponse()
    val returnType = returnTypeName(response)
    val parameters = swiftParameterViews(service)
    val functionBuilder =
      FunctionSpec
        .builder(id)
        .addModifiers(PUBLIC)
        .addSwiftDoc(documentation)

    parameters
      .filterNot { parameter -> parameter.isConstant }
      .forEach { parameter ->
        functionBuilder.addParameter(parameter.swiftParameterSpec())
      }

    requestBody?.let { body ->
      functionBuilder.addParameter("body", body.swiftRequestBodyTypeName())
    }

    if (returnType != VOID) {
      functionBuilder.returns(returnType)
    }

    if (streaming != null) {
      functionBuilder.addCode(streamingCode(response, parameters))
      return functionBuilder.build()
    }

    if (exchange == null) {
      functionBuilder
        .throws(true)
        .addCode(operationCode(response, parameters))
    } else {
      functionBuilder
        .async(true)
        .throws(true)

      val factoryMethod =
        when (exchange) {
          GeneratedExchange.REQUEST -> "transportRequest"
          GeneratedExchange.RESPONSE -> "transportResponse"
        }

      functionBuilder.addCode(
        CodeBlock
          .builder()
          .add("return try await self.transport.%L(%>\n", factoryMethod)
          .add(requestCode(response, parameters))
          .add("%<\n)\n")
          .build(),
      )
    }

    return functionBuilder.build()
  }

  private fun GeneratedOperation.operationCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add("return %T(%>\n", if (isNilableOperation) NILABLE_OPERATION else OPERATION)
      .add("transport: self.transport,\n")
      .add("spec: %T%L(%>\n", OPERATION_SPEC, if (requestBody.isSwiftStreamingRequestBody) ".streaming" else "")
      .add(requestCode(response, parameters))
      .add("%<\n)")
      .apply {
        nullify?.takeIf { isNilableOperation }?.let { nullify ->
          add(",\n")
          add(nilifySpecCode(nullify))
        }
      }.add("%<\n)\n")
      .build()

  private fun nilifySpecCode(nullify: GeneratedNullify): CodeBlock {
    val problemTypeNames =
      nullify.problems
        .mapNotNull { problem -> problem.problemOrNull(apiIndex) }
        .map { problem -> problem.swiftProblemTypeName() }

    return CodeBlock
      .builder()
      .add("nilify: %T(%>\n", NILIFY_SPEC)
      .add("statuses: [${nullify.statuses.joinToString { "$it" }}],\n")
      .add("problemTypes: [")
      .add(problemTypeNames.map { typeName -> CodeBlock.of("%T.self", typeName) }.joinToCode(", "))
      .add("]")
      .add("%<\n)")
      .build()
  }

  private fun GeneratedOperation.streamingCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
  ): CodeBlock =
    when (streaming?.kind) {
      GeneratedStreaming.Kind.EVENT_STREAM ->
        eventStreamCode(response, parameters)

      else ->
        CodeBlock
          .builder()
          .add("return self.transport.eventSource(%>\n")
          .add(requestCode(response, parameters, eventStream = true))
          .add("%<\n)")
          .build()
    }

  private fun GeneratedOperation.eventStreamCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    val responseType = response?.type
    val originalReturnType = responseType?.swiftTypeName() ?: ANY

    builder.add("return self.transport.eventStream(%>\n")
    builder.add(requestCode(response, parameters, eventStream = true))

    when (streaming?.eventMode) {
      GeneratedStreaming.EventMode.DISCRIMINATED -> {
        val eventTypes =
          responseType
            ?.flattenedUnionTypes()
            .orEmpty()
            .filter { type -> type.kind == GeneratedTypeRef.Kind.NAMED }

        val decoderCases =
          eventTypes.joinToString("\n  ") { "case %S: return try decoder.decode(%T.self, from: data)" }
        val decoderParams =
          eventTypes.flatMap { type ->
            val typeName = type.swiftTypeName()
            val discriminatorValue =
              type
                .modelOrNull(apiIndex)
                ?.discriminatorValue
                ?: (typeName as? DeclaredTypeName)?.simpleName
                ?: "$typeName"
            listOf(discriminatorValue, typeName)
          }

        builder.add(",\n")
        builder.add(
          """
          |decoder: { decoder, event, _, data, log in
          |  switch event {
          |  $decoderCases
          |  default:
          |    log.error("Unknown event type, ignoring event: event=\(event ?? "<none>", privacy: .public)")
          |    return nil
          |  }
          |}
          """.trimMargin(),
          *decoderParams.toTypedArray(),
        )
      }

      else -> {
        val decodeType = typeRegistry.getReferenceType(originalReturnType) ?: originalReturnType
        val decodeUnwrap = if (decodeType != originalReturnType) ".value" else ""
        builder.add(
          ",\ndecoder: { decoder, _, _, data, _ in try decoder.decode(%T.self, from: data)%L }",
          decodeType,
          decodeUnwrap,
        )
      }
    }

    builder.add("%<\n)\n")
    return builder.build()
  }

  private fun GeneratedOperation.requestCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
    eventStream: Boolean = false,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    val pathParameters = parameters.withLocation(GeneratedParameter.Location.PATH)
    val queryParameters = parameters.withLocation(GeneratedParameter.Location.QUERY)
    val headerParameters = parameters.withLocation(GeneratedParameter.Location.HEADER)
    val contentTypeParameter = headerParameters.contentTypeParameterOrNull()

    builder.add("method: .%L", swiftRequestMethod())
    builder.add(",\npathTemplate: %S", path)
    builder.add(",\n%L", pathParameters.requestParametersCode("pathParameters", throwing = !eventStream))
    builder.add(",\n%L", queryParameters.requestParametersCode("queryParameters", throwing = !eventStream))

    requestBody?.let { body ->
      builder.add(",\nbody: body")
      builder.add(",\ncontentTypes: %L", body.contentTypesCode(contentTypeParameter, throwing = !eventStream))
    } ?: builder.add(",\nbody: %T.none,\ncontentTypes: nil", EMPTY)

    builder.add(
      ",\nacceptTypes: %L",
      if (eventStream) mediaTypesArray("text/event-stream") else response.acceptTypesCode(throwing = true),
    )
    builder.add(
      ",\n%L",
      headerParameters
        .filterNot { parameter -> parameter == contentTypeParameter }
        .requestParametersCode("headers", throwing = !eventStream),
    )

    return builder.build()
  }

  private fun GeneratedOperation.swiftRequestMethod(): String =
    when (method.uppercase()) {
      "SUBSCRIBE" -> "get"
      "PUBLISH" -> "post"
      else -> method.lowercase()
    }

  private fun GeneratedOperation.swiftParameterViews(service: GeneratedService): List<GeneratedOperationParameter> {
    val names = NameAllocator()
    val operationParameters =
      operationParameterViews(
        identifierName = { parameter -> parameter.name.swiftIdentifierName },
        allocateName = { parameter, proposedName -> names.newName(proposedName, parameter) },
      )
    val securityParameters =
      api
        .effectiveAuth(service, this)
        ?.securitySchemes
        .orEmpty()
        .flatMap { scheme -> scheme.swiftSecurityParameterViews(names) }

    return securityParameters + operationParameters
  }

  private fun GeneratedSecurityScheme.swiftSecurityParameterViews(
    names: NameAllocator,
  ): List<GeneratedOperationParameter> =
    headers.map { parameter -> parameter.swiftSecurityParameterView(this, names, GeneratedParameter.Location.HEADER) } +
      queryParameters.map { parameter ->
        parameter.swiftSecurityParameterView(this, names, GeneratedParameter.Location.QUERY)
      }

  private fun GeneratedParameter.swiftSecurityParameterView(
    scheme: GeneratedSecurityScheme,
    names: NameAllocator,
    location: GeneratedParameter.Location,
  ): GeneratedOperationParameter {
    val wireName = serializationName ?: name
    val proposedName = "${scheme.name.toLowerCamelCase()}${wireName.toUpperCamelCase()}"
    return GeneratedOperationParameter(
      source = this,
      name = names.newName(proposedName.swiftIdentifierName, this),
      wireName = wireName,
      location = location,
      type = type,
      required = required,
      defaultValue = defaultValue,
      constantValue = constantValue,
      isNullable = type.nullable || !required,
    )
  }

  private fun GeneratedOperationParameter.swiftParameterSpec(): ParameterSpec {
    val typeName = swiftParameterTypeName()
    val builder = ParameterSpec.builder(name, typeName)

    if (defaultValue != null) {
      builder.defaultValue(defaultValue.swiftValueCode(typeName.makeNonOptional(), type))
    } else if (typeName.optional) {
      builder.defaultValue("nil")
    }

    return builder.build()
  }

  private fun GeneratedOperationParameter.swiftParameterTypeName(): TypeName {
    val typeName = type.swiftParameterTypeName()
    return if (isNullable) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun GeneratedTypeRef.swiftParameterTypeName(): TypeName =
    when {
      kind == GeneratedTypeRef.Kind.MAP -> DICTIONARY.parameterizedBy(STRING, ANY)
      kind == GeneratedTypeRef.Kind.NAMED && modelOrNull(apiIndex)?.isFreeformObject == true ->
        DICTIONARY.parameterizedBy(STRING, ANY)

      else -> swiftTypeName().makeNonOptional()
    }

  private fun List<GeneratedOperationParameter>.requestParametersCode(
    fieldName: String,
    throwing: Boolean = true,
  ): CodeBlock {
    if (isEmpty()) {
      return CodeBlock.of("%L: nil", fieldName)
    }

    val filtersNullValues = any { parameter -> parameter.shouldFilterNullValue }
    val builder = CodeBlock.builder().add("%L: [%>\n", fieldName)

    forEachIndexed { idx, parameter ->
      builder.add("%S: ", parameter.wireName)
      if (parameter.isConstant) {
        builder.add(
          "%L %T.encode(%L)",
          if (throwing) "try" else "try!",
          PARAMETER_VALUES,
          parameter.constantValue!!.swiftValueCode(parameter.type.swiftTypeName(), null),
        )
      } else {
        builder.add("%L %T.encode(%N)", if (throwing) "try" else "try!", PARAMETER_VALUES, parameter.name)
      }
      if (idx < size - 1) {
        builder.add(",\n")
      }
    }

    builder.add("%<\n]%L", if (filtersNullValues) ".filter { \$0.value != nil }" else "")

    return builder.build()
  }

  private fun Iterable<GeneratedOperationParameter>.contentTypeParameterOrNull(): GeneratedOperationParameter? =
    firstOrNull { parameter ->
      parameter.location == GeneratedParameter.Location.HEADER &&
        parameter.wireName.equals("Content-Type", ignoreCase = true)
    }

  private fun GeneratedPayload.contentTypesCode(
    contentTypeParameter: GeneratedOperationParameter?,
    throwing: Boolean = false,
  ): CodeBlock =
    contentTypeParameter?.selectedContentTypesCode()
      ?: explicitContentTypes(defaultMediaTypes)
        ?.let { mediaTypes -> mediaTypesArray(mediaTypes, throwing = throwing) }
      ?: CodeBlock.of("self.defaultContentTypes")

  private fun GeneratedOperationParameter.selectedContentTypesCode(): CodeBlock? {
    val enumModel =
      type
        .modelOrNull(apiIndex)
        ?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
        ?: return null
    if (enumModel.values.filterIsInstance<String>().isEmpty()) {
      return null
    }
    return CodeBlock.of("[try .init(valid: %N.rawValue)]", name)
  }

  private fun GeneratedResponse?.acceptTypesCode(throwing: Boolean = false): CodeBlock =
    if (this?.type == null || isNoContent()) {
      CodeBlock.of("self.defaultAcceptTypes")
    } else {
      explicitAcceptTypes(defaultMediaTypes)
        ?.let { mediaTypes -> mediaTypesArray(mediaTypes, throwing = throwing) }
        ?: CodeBlock.of("self.defaultAcceptTypes")
    }

  private fun GeneratedOperation.returnTypeName(response: GeneratedResponse?): TypeName {
    if (exchange == GeneratedExchange.REQUEST) {
      return TRANSPORT_REQUEST
    }
    if (exchange == GeneratedExchange.RESPONSE) {
      return TRANSPORT_RESPONSE
    }

    val responseType = response?.type?.swiftPublicTypeName() ?: VOID
    if (streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE) {
      return EVENT_SOURCE
    }
    if (streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM) {
      return ASYNC_STREAM.parameterizedBy(
        response?.type?.swiftEventStreamTypeName(publicExistential = true)
          ?: responseType,
      )
    }
    if (!isNilableOperation && requestBody.isSwiftStreamingRequestBody) {
      return STREAMING_OPERATION.parameterizedBy(
        if (responseType == VOID || response?.status == 204) VOID else responseType,
        transportTypeVariable,
      )
    }
    return (if (isNilableOperation) NILABLE_OPERATION else OPERATION).parameterizedBy(
      requestBody?.swiftRequestBodyTypeName() ?: EMPTY,
      if (responseType == VOID || response?.status == 204) VOID else responseType,
      transportTypeVariable,
    )
  }

  private val GeneratedOperation.isNilableOperation: Boolean
    get() = nullify != null && exchange == null && streaming == null

  private fun GeneratedResponse.isNoContent(): Boolean = status == 204

  private fun GeneratedProblem.swiftProblemTypeName(): DeclaredTypeName =
    DeclaredTypeName
      .typeName(".${name.toUpperCamelCase()}")

  private fun GeneratedProblem.hasDuplicateSwiftProblemTypeName(): Boolean =
    api.problems
      .filter { problem -> problem.swiftProblemTypeName() == swiftProblemTypeName() }
      .map { problem -> problem.typeUri }
      .distinct()
      .size > 1

  private fun GeneratedTypeRef.swiftEventStreamTypeName(publicExistential: Boolean = false): TypeName =
    commonInheritedTypeOrNull()?.let { type ->
      if (publicExistential) {
        type.swiftPublicTypeName()
      } else {
        type.swiftTypeName()
      }
    } ?: if (publicExistential) {
      swiftPublicTypeName()
    } else {
      swiftTypeName()
    }

  private fun GeneratedTypeRef.commonInheritedTypeOrNull(): GeneratedTypeRef? {
    if (kind != GeneratedTypeRef.Kind.UNION) {
      return null
    }

    val inheritedTypes =
      arguments.map { argument ->
        argument
          .modelOrNull(apiIndex)
          ?.inherits
          ?.firstOrNull()
      }

    if (inheritedTypes.any { it == null }) {
      return null
    }

    return inheritedTypes
      .filterNotNull()
      .distinctBy { type -> Triple(type.name, type.scope, type.source) }
      .singleOrNull()
  }

  private fun GeneratedPayload.swiftTypeName(): TypeName =
    if (mediaTypes.firstOrNull() == "application/octet-stream") {
      DATA
    } else {
      type.swiftTypeName()
    }

  private fun GeneratedPayload.swiftRequestBodyTypeName(): TypeName =
    if (isSwiftStreamingRequestBody) {
      STREAMING_BODY
    } else if (mediaTypes.firstOrNull() == "application/octet-stream") {
      DATA
    } else {
      type.swiftStoredTypeName()
    }

  private val GeneratedPayload?.isSwiftStreamingRequestBody: Boolean
    get() = this?.streaming?.enabledFor(GenerationMode.Client) == true

  private fun GeneratedModel.swiftDeclaredTypeName(): DeclaredTypeName {
    if (scope != null) {
      return DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
    }

    val target = target("swift", "swift")
    val explicitTypeName = target?.typeName
    if (explicitTypeName != null && "." in explicitTypeName) {
      return DeclaredTypeName.typeName(explicitTypeName)
    }

    val nested = nested
    if (nested != null) {
      if (isProtocolHierarchyRootModel || isProblemHierarchyProtocolModel) {
        return DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
      }
      val enclosingModel =
        nested.enclosedIn
          ?.modelOrNull(apiIndex)
          ?: genError("Nested model '$name' references unknown enclosing type '${nested.enclosedIn?.name}'")
      if (enclosingModel.isProtocolHierarchyRootModel || enclosingModel.isProblemHierarchyProtocolModel) {
        return DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
      }
      val enclosingType = enclosingModel.swiftDeclaredTypeName()
      val nestedName = nested.name ?: genError("Nested model '$name' is missing a nested type name")
      return enclosingType.nestedType(nestedName)
    }

    val moduleName =
      target?.modelModuleName
        ?: api.target("swift", "swift")?.modelModuleName
        ?: ""
    val simpleName = explicitTypeName ?: name.toUpperCamelCase()
    return DeclaredTypeName.typeName("${if (moduleName.isBlank()) "" else moduleName}.$simpleName")
  }

  private fun GeneratedTypeRef.swiftTypeName(): TypeName {
    val typeName =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR -> scalarTypeName()
        GeneratedTypeRef.Kind.NAMED -> namedSwiftTypeName()
        GeneratedTypeRef.Kind.ARRAY -> ARRAY.parameterizedBy(arguments.firstOrNull()?.swiftTypeName() ?: STRING)
        GeneratedTypeRef.Kind.MAP -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
        GeneratedTypeRef.Kind.UNION -> ANY
      }

    return if (nullable) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun GeneratedTypeRef.swiftPublicTypeName(): TypeName {
    val typeName =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR -> scalarTypeName()
        GeneratedTypeRef.Kind.NAMED -> namedSwiftPublicTypeName()
        GeneratedTypeRef.Kind.ARRAY -> ARRAY.parameterizedBy(arguments.firstOrNull()?.swiftPublicTypeName() ?: STRING)
        GeneratedTypeRef.Kind.MAP -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
        GeneratedTypeRef.Kind.UNION -> ANY
      }

    return if (nullable) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun GeneratedTypeRef.swiftStoredTypeName(useReferenceTypes: Boolean = true): TypeName {
    val typeName =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR -> scalarTypeName()
        GeneratedTypeRef.Kind.NAMED -> namedSwiftStoredTypeName(useReferenceTypes)
        GeneratedTypeRef.Kind.ARRAY ->
          ARRAY.parameterizedBy(arguments.firstOrNull()?.swiftStoredTypeName(useReferenceTypes) ?: STRING)
        GeneratedTypeRef.Kind.MAP ->
          DICTIONARY.parameterizedBy(
            STRING,
            arguments.firstOrNull()?.swiftStoredTypeName(useReferenceTypes) ?: ANY_VALUE,
          )
        GeneratedTypeRef.Kind.UNION -> ANY
      }

    return if (nullable) {
      typeName.makeOptional()
    } else {
      typeName
    }
  }

  private fun GeneratedTypeRef.namedSwiftTypeName(): TypeName =
    when (val model = modelOrNull(apiIndex)) {
      null -> DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
      else ->
        when {
          model.isFreeformObject -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
          model.isAliasLike -> model.aliasTypeName()
          else -> model.swiftDeclaredTypeName()
        }
    }

  private fun GeneratedTypeRef.namedSwiftPublicTypeName(): TypeName =
    when (val model = modelOrNull(apiIndex)) {
      null -> DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
      else ->
        when {
          model.isFreeformObject -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
          model.isAliasLike -> model.aliasPublicTypeName()
          model.hasSwiftReferenceType -> model.swiftDeclaredTypeName().swiftExistentialTypeName()
          else -> model.swiftDeclaredTypeName()
        }
    }

  private fun GeneratedTypeRef.namedSwiftStoredTypeName(useReferenceTypes: Boolean): TypeName =
    when (val model = modelOrNull(apiIndex)) {
      null -> DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
      else ->
        when {
          model.isFreeformObject -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
          model.isAliasLike -> model.aliasStoredTypeName(useReferenceTypes)
          useReferenceTypes && model.hasSwiftReferenceType -> model.swiftReferenceTypeName()
          else -> model.swiftDeclaredTypeName()
        }
    }

  private fun GeneratedTypeRef.swiftReferenceLookupTypeName(): TypeName? =
    when (kind) {
      GeneratedTypeRef.Kind.NAMED -> namedSwiftTypeName().makeNonOptional()
      else -> null
    }

  private fun GeneratedTypeRef.directNamedModelOrNull(): GeneratedModel? =
    if (kind == GeneratedTypeRef.Kind.NAMED) {
      modelOrNull(apiIndex)
    } else {
      null
    }

  private fun SwiftModelKey.reaches(
    target: SwiftModelKey,
    edges: Map<SwiftModelKey, Set<SwiftModelKey>>,
    visited: MutableSet<SwiftModelKey>,
  ): Boolean {
    if (!visited.add(this)) {
      return false
    }

    return edges[this].orEmpty().any { next -> next == target || next.reaches(target, edges, visited) }
  }

  private fun GeneratedModel.aliasTypeName(): TypeName =
    when (kind) {
      GeneratedModel.Kind.SCALAR_ALIAS ->
        aliases.singleOrNull()?.swiftTypeName() ?: ANY_VALUE

      GeneratedModel.Kind.ARRAY -> {
        val itemType = aliases.singleOrNull()?.swiftTypeName() ?: STRING
        if (collection == GeneratedCollectionKind.SET) {
          SET.parameterizedBy(itemType)
        } else {
          ARRAY.parameterizedBy(itemType)
        }
      }

      GeneratedModel.Kind.MAP ->
        DICTIONARY.parameterizedBy(STRING, aliases.singleOrNull()?.swiftTypeName() ?: STRING)

      GeneratedModel.Kind.UNION -> {
        val options = aliases.map { alias -> alias.swiftTypeName() }.distinct()
        options.singleOrNull() ?: ANY_VALUE
      }

      GeneratedModel.Kind.OBJECT,
      GeneratedModel.Kind.ENUM,
      -> swiftDeclaredTypeName()
    }

  private fun GeneratedModel.aliasPublicTypeName(): TypeName =
    when (kind) {
      GeneratedModel.Kind.SCALAR_ALIAS ->
        aliases.singleOrNull()?.swiftPublicTypeName() ?: ANY_VALUE

      GeneratedModel.Kind.ARRAY -> {
        val itemType = aliases.singleOrNull()?.swiftPublicTypeName() ?: STRING
        if (collection == GeneratedCollectionKind.SET) {
          SET.parameterizedBy(itemType)
        } else {
          ARRAY.parameterizedBy(itemType)
        }
      }

      GeneratedModel.Kind.MAP ->
        DICTIONARY.parameterizedBy(STRING, aliases.singleOrNull()?.swiftPublicTypeName() ?: STRING)

      GeneratedModel.Kind.UNION -> {
        val options = aliases.map { alias -> alias.swiftPublicTypeName() }.distinct()
        options.singleOrNull() ?: ANY_VALUE
      }

      GeneratedModel.Kind.OBJECT,
      GeneratedModel.Kind.ENUM,
      -> swiftDeclaredTypeName()
    }

  private fun GeneratedModel.aliasStoredTypeName(useReferenceTypes: Boolean): TypeName =
    when (kind) {
      GeneratedModel.Kind.SCALAR_ALIAS ->
        aliases.singleOrNull()?.swiftStoredTypeName(useReferenceTypes) ?: ANY_VALUE

      GeneratedModel.Kind.ARRAY -> {
        val itemType = aliases.singleOrNull()?.swiftStoredTypeName(useReferenceTypes) ?: STRING
        if (collection == GeneratedCollectionKind.SET) {
          SET.parameterizedBy(itemType)
        } else {
          ARRAY.parameterizedBy(itemType)
        }
      }

      GeneratedModel.Kind.MAP ->
        DICTIONARY.parameterizedBy(STRING, aliases.singleOrNull()?.swiftStoredTypeName(useReferenceTypes) ?: STRING)

      GeneratedModel.Kind.UNION -> {
        val options = aliases.map { alias -> alias.swiftStoredTypeName(useReferenceTypes) }.distinct()
        options.singleOrNull() ?: ANY_VALUE
      }

      GeneratedModel.Kind.OBJECT,
      GeneratedModel.Kind.ENUM,
      -> swiftDeclaredTypeName()
    }

  private val GeneratedModel.isFreeformObject: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        properties.isEmpty() &&
        patternProperties.isEmpty() &&
        discriminatorMappings.isEmpty()

  private val GeneratedModel.isAliasLike: Boolean
    get() =
      kind == GeneratedModel.Kind.SCALAR_ALIAS ||
        kind == GeneratedModel.Kind.ARRAY ||
        kind == GeneratedModel.Kind.MAP ||
        (kind == GeneratedModel.Kind.UNION && !isObjectUnionEnum)

  private fun GeneratedTypeRef.scalarTypeName(): TypeName =
    swiftStringFormatTypeName(format) ?: when (name) {
      "any" -> ANY_VALUE
      "string" -> STRING
      "boolean" -> BOOL
      "date", "time", "datetime-only", "datetime" -> DATE
      "integer", "int32" -> INT
      "int64", "long" -> INT
      "number", "double" -> DOUBLE
      "object" -> DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
      "file" -> DATA
      "nil" -> VOID
      else -> STRING
    }

  private val TypeSpec.Builder.associatedExtensions: AssociatedExtensions
    get() {
      var value = tags[AssociatedExtensions::class] as AssociatedExtensions?
      if (value == null) {
        value = AssociatedExtensions()
        tag(value)
      }
      return value
    }

  private fun TypeSpec.Builder.addSwiftDoc(documentation: GeneratedDocumentation?): TypeSpec.Builder =
    apply {
      documentation?.swiftDoc?.let { doc ->
        addDoc(CodeBlock.of("%L", doc))
      }
    }

  private fun FunctionSpec.Builder.addSwiftDoc(documentation: GeneratedDocumentation?): FunctionSpec.Builder =
    apply {
      documentation?.swiftDoc?.let { doc ->
        addDoc(CodeBlock.of("%L", doc))
      }
    }

  private fun PropertySpec.Builder.addSwiftDoc(documentation: GeneratedDocumentation?): PropertySpec.Builder =
    apply {
      documentation?.swiftDoc?.let { doc ->
        addDoc(CodeBlock.of("%L", doc))
      }
    }

  private val GeneratedDocumentation.swiftDoc: String?
    get() =
      listOfNotNull(summary, description)
        .map { doc -> doc.trim().sanitizeSwiftDocComment() }
        .filter { doc -> doc.isNotEmpty() }
        .distinct()
        .joinToString("\n\n")
        .takeIf { doc -> doc.isNotEmpty() }
        ?.let { doc -> "$doc\n" }

  private fun String.sanitizeSwiftDocComment(): String =
    replace("/*", "/ *")
      .replace("*/", "* /")

  companion object {

    private val ANY_PATCH_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.AnyPatchOp")
    private val UPDATE_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.UpdateOp")
    private val PATCH_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.PatchOp")
    private val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
    private val baseProblemProperties = setOf("type", "title", "status", "detail", "instance")
    private val optionalBaseProblemProperties = setOf("detail", "instance")
    private val requestModelUsages =
      setOf(
        GeneratedModelScope.Usage.PARAMETER,
        GeneratedModelScope.Usage.QUERY_STRING,
        GeneratedModelScope.Usage.REQUEST_BODY,
        GeneratedModelScope.Usage.SECURITY_QUERY_STRING,
      )
  }

  private fun Any.swiftValueCode(
    typeName: TypeName,
    typeRef: GeneratedTypeRef?,
  ): CodeBlock =
    when (this) {
      is String ->
        if (typeRef?.modelOrNull(apiIndex)?.kind == GeneratedModel.Kind.ENUM) {
          CodeBlock.of("%T.%N", typeName, swiftEnumCaseName)
        } else {
          CodeBlock.of("%S", this)
        }

      is Number -> CodeBlock.of("%L", this)
      is Boolean -> CodeBlock.of("%L", this)
      is List<*> ->
        map { value ->
          value?.swiftValueCode(typeName, null) ?: CodeBlock.of("nil")
        }.joinToCode(prefix = "[", suffix = "]")
      is Map<*, *> ->
        entries
          .map { (key, value) ->
            CodeBlock.of("%S: %L", key.toString(), value?.swiftValueCode(typeName, null) ?: CodeBlock.of("nil"))
          }.joinToCode(prefix = "[", suffix = "]")

      else -> CodeBlock.of("%S", toString())
    }

  private fun mediaTypesArray(
    mimeTypes: List<String>,
    throwing: Boolean = false,
  ): CodeBlock = mediaTypesArray(*mimeTypes.toTypedArray(), throwing = throwing)

  private fun mediaTypesArray(
    vararg mimeTypes: String,
    throwing: Boolean = false,
  ): CodeBlock =
    mimeTypes
      .distinct()
      .map { mimeType -> mediaType(mimeType, throwing) }
      .joinToCode(prefix = "[", suffix = "]")

  private fun mediaType(
    value: String,
    throwing: Boolean,
  ): CodeBlock =
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
      "application/json-patch+json" -> CodeBlock.of(".jsonPatch")
      "application/merge-patch+json" -> CodeBlock.of(".mergePatch")
      else -> CodeBlock.of("%Linit(valid: %S)", if (throwing) "try ." else ".", value)
    }
}
