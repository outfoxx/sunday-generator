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
import io.outfoxx.sunday.generator.PayloadUse
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
import io.outfoxx.sunday.generator.ir.GeneratedPatchModels
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedSecurityScheme
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.allowsUnknown
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedDiscriminatorFallback
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.GeneratedModelProperties
import io.outfoxx.sunday.generator.ir.emit.GeneratedNominalTypes
import io.outfoxx.sunday.generator.ir.emit.GeneratedNumericBounds
import io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter
import io.outfoxx.sunday.generator.ir.emit.ancestorModels
import io.outfoxx.sunday.generator.ir.emit.clientSecurity
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.discriminatorChildren
import io.outfoxx.sunday.generator.ir.emit.discriminatorFallbackOrNull
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.ir.emit.enabledFor
import io.outfoxx.sunday.generator.ir.emit.explicitAcceptTypes
import io.outfoxx.sunday.generator.ir.emit.explicitContentTypes
import io.outfoxx.sunday.generator.ir.emit.externalDiscriminatorFallbackOrNull
import io.outfoxx.sunday.generator.ir.emit.flattenedUnionTypes
import io.outfoxx.sunday.generator.ir.emit.isNullableParameter
import io.outfoxx.sunday.generator.ir.emit.modelOrNull
import io.outfoxx.sunday.generator.ir.emit.operationParameterViews
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.ir.emit.primarySuccessResponse
import io.outfoxx.sunday.generator.ir.emit.problemOrNull
import io.outfoxx.sunday.generator.ir.emit.referencedProblems
import io.outfoxx.sunday.generator.ir.emit.requireNoUnsupportedPolicies
import io.outfoxx.sunday.generator.ir.emit.resolvedTypeUri
import io.outfoxx.sunday.generator.ir.emit.target
import io.outfoxx.sunday.generator.ir.emit.withLocation
import io.outfoxx.sunday.generator.requireBrokerServicesSupported
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry.OutputDirectory
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE
import io.outfoxx.sunday.generator.swift.utils.ANY_VALUE_DECODER
import io.outfoxx.sunday.generator.swift.utils.ASYNC_STREAM
import io.outfoxx.sunday.generator.swift.utils.CODABLE
import io.outfoxx.sunday.generator.swift.utils.CODING_KEY
import io.outfoxx.sunday.generator.swift.utils.CUSTOM_DEBUG_STRING_CONVERTIBLE
import io.outfoxx.sunday.generator.swift.utils.CUSTOM_STRING_CONVERTIBLE
import io.outfoxx.sunday.generator.swift.utils.DATE
import io.outfoxx.sunday.generator.swift.utils.DECODER
import io.outfoxx.sunday.generator.swift.utils.DECODING_ERROR
import io.outfoxx.sunday.generator.swift.utils.DESCRIPTION_BUILDER
import io.outfoxx.sunday.generator.swift.utils.EMPTY
import io.outfoxx.sunday.generator.swift.utils.ENCODER
import io.outfoxx.sunday.generator.swift.utils.EQUATABLE
import io.outfoxx.sunday.generator.swift.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.swift.utils.GENERIC_PROBLEM
import io.outfoxx.sunday.generator.swift.utils.HASHABLE
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
import io.outfoxx.sunday.generator.swift.utils.SwiftModelConstraints
import io.outfoxx.sunday.generator.swift.utils.SwiftModelDefaults
import io.outfoxx.sunday.generator.swift.utils.SwiftModelValidation
import io.outfoxx.sunday.generator.swift.utils.SwiftNominalTypes
import io.outfoxx.sunday.generator.swift.utils.SwiftPatchHelpers
import io.outfoxx.sunday.generator.swift.utils.SwiftValidationViews
import io.outfoxx.sunday.generator.swift.utils.SwiftValueConstraints
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT_REQUEST
import io.outfoxx.sunday.generator.swift.utils.TRANSPORT_RESPONSE
import io.outfoxx.sunday.generator.swift.utils.UNCHECKED_SENDABLE
import io.outfoxx.sunday.generator.swift.utils.URI_TEMPLATE
import io.outfoxx.sunday.generator.swift.utils.URL
import io.outfoxx.sunday.generator.swift.utils.swiftBindings
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
import io.outfoxx.swiftpoet.Modifier.CLASS
import io.outfoxx.swiftpoet.Modifier.FILEPRIVATE
import io.outfoxx.swiftpoet.Modifier.FINAL
import io.outfoxx.swiftpoet.Modifier.OVERRIDE
import io.outfoxx.swiftpoet.Modifier.PRIVATE
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
  api: GeneratedApi,
  private val typeRegistry: SwiftTypeOutputRegistry,
  private val options: SwiftSundayOptions,
) {

  private val api = api.copy(models = GeneratedPatchModels.normalizeFields(api.models))

  private val transportTypeVariable = typeVariable("TransportType", bound(TRANSPORT))

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(options.defaultMediaTypes)
  private val apiIndex = GeneratedApiIndex(this.api)
  private val modelProperties = GeneratedModelProperties(apiIndex::modelOrNull)
  private val patchHelpers by lazy { SwiftPatchHelpers(api.models) { it.swiftDeclaredTypeName() } }

  private val nominalTypes = GeneratedNominalTypes(apiIndex::modelOrNull)
  private val nominalGenerator =
    SwiftNominalTypes(nominalTypes, { validationViews }, { it.swiftDeclaredTypeName() }, { it.swiftTypeName() })
  private val validationViews =
    SwiftValidationViews(modelProperties, apiIndex::modelOrNull, {
      it.swiftDeclaredTypeName()
    }, { it.swiftTypeName() }, { it.isFreeformObject }, {
      if (it.hasSwiftReferenceType) it.swiftReferenceTypeName() else it.swiftDeclaredTypeName()
    })
  private val normalizedValidationModels by lazy { api.models.toSet() }
  private val decodingDefaultNames by lazy { inheritedDecodingDefaultNames() }
  private val discriminatorFallbacks: Map<GeneratedModel, GeneratedDiscriminatorFallback> by lazy {
    buildList {
      api.models.mapNotNullTo(this) { model -> model.discriminatorFallbackOrNull(apiIndex) }
      api.models.forEach { owner ->
        owner.properties.mapNotNullTo(this) { property ->
          property.externalDiscriminatorFallbackOrNull(owner, apiIndex)
        }
      }
    }.associateBy { fallback -> fallback.hierarchy }
  }
  private val scopedModelNames = mutableMapOf<GeneratedModel, DeclaredTypeName>()
  private val swiftEnumEntriesByModel = mutableMapOf<GeneratedModel, List<SwiftEnumEntry>>()

  /** Generates Swift/Sunday service types from IR and registers them in the type registry. */
  fun generateServiceTypes() {
    options.requireBrokerServicesSupported("Swift/Sunday")
    val services = api.swiftSundayServices()
    services.requireNoUnsupportedPolicies(options.generationContext(GenerationMode.Client), "Swift/Sunday")
    services.forEach { service ->
      val serviceName = DeclaredTypeName.typeName(".${service.typeSimpleName()}")
      apiIndex.referencedScopedModels(service).forEach { model ->
        scopedModelNames[model] = serviceName.nestedType(model.name.toUpperCamelCase())
      }
    }
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
        patchHelpers.add(model, typeBuilder, model.isSwiftValueModel)
        typeRegistry.addModelType(
          typeName,
          typeBuilder,
          outputDirectory = outputDirectory,
          outputGroup = outputGroup,
        )
        discriminatorFallbacks[model]?.let { fallback ->
          typeRegistry.addModelType(
            model.swiftFallbackTypeName(fallback),
            model.swiftFallbackTypeSpec(fallback, outputDirectory, outputGroup),
            outputDirectory = outputDirectory,
            outputGroup = outputGroup,
          )
        }
      }
    if (patchHelpers.enabled) typeRegistry.addModelType(patchHelpers.supportName, patchHelpers.support())
  }

  private fun GeneratedModel.swiftFallbackTypeName(fallback: GeneratedDiscriminatorFallback): DeclaredTypeName {
    val moduleName =
      target("swift", "swift")?.modelModuleName
        ?: api.target("swift", "swift")?.modelModuleName
        ?: ""
    return DeclaredTypeName.typeName("${if (moduleName.isBlank()) "" else moduleName}.${fallback.modelName}")
  }

  private fun GeneratedModel.swiftFallbackTypeSpec(
    fallback: GeneratedDiscriminatorFallback,
    outputDirectory: OutputDirectory,
    outputGroup: String?,
  ): TypeSpec.Builder {
    val typeName = swiftFallbackTypeName(fallback)
    val exposedProperties =
      buildList {
        if (!fallback.externallyDiscriminated) {
          add(fallback.discriminatorProperty)
        }
        addAll(fallback.baseProperties)
      }
    val rawBodyType = DICTIONARY.parameterizedBy(STRING, ANY_VALUE)
    val normalized = this in normalizedValidationModels
    val fallbackSchema =
      GeneratedModel(
        fallback.modelName,
        GeneratedModel.Kind.OBJECT,
        properties = exposedProperties,
        patternProperties = modelProperties.patternProperties(this),
        additionalProperties = additionalProperties,
      )
    return TypeSpec
      .structBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSuperTypes(
        buildList {
          if (isProtocolHierarchyRootModel ||
            isProblemHierarchyProtocolModel ||
            isExternalDiscriminatorBaseProtocolModel
          ) {
            add(swiftDeclaredTypeName())
          } else {
            add(CODABLE)
            add(CUSTOM_DEBUG_STRING_CONVERTIBLE)
            add(SENDABLE)
          }
        },
      ).apply {
        addSuperType(SwiftModelValidation.validatable)
        val validatorName = SwiftModelValidation.name(typeName)
        addFunction(SwiftModelValidation.instance(validatorName))
        val body =
          CodeBlock
            .builder()
            .apply {
              val effectiveTolerance = tolerance ?: fallback.enumModel?.tolerance
              if (!effectiveTolerance.allowsUnknown(
                  options.generationContext(GenerationMode.Client, PayloadUse.Request),
                )
              ) {
                beginControlFlow("if", "mode == .request")
                addStatement("return context.reject(.unknownUnion)")
                endControlFlow("if")
              }
              if (normalized) {
                add(normalizedObjectValidation(fallbackSchema))
              } else {
                add(
                  SwiftModelValidation.fields(
                    exposedProperties.map {
                      GeneratedModelProperties.Field(it, it, false)
                    },
                    modelProperties,
                    false,
                    swiftClosedPropertyValidation(CodeBlock.of("Set(value.rawBody.keys)")),
                  ) { property ->
                    property.type.copy(nullable = false).swiftNestedValidation(CodeBlock.of("fieldValue"))
                  },
                )
              }
            }.build()
        if (normalized) {
          addFunction(
            FunctionSpec
              .builder("_sundayValidationValue")
              .addDoc("Retains fallback identity and current declared fields without invoking a codec.\n")
              .returns(SwiftValueConstraints.valueType)
              .addCode(
                validationViews.objectView(
                  fallbackSchema,
                  "rawBody",
                  false,
                  unknown = true,
                  validatorName = validatorName,
                ),
              ).build(),
          )
        }
        typeRegistry.addModelType(
          validatorName,
          if (normalized) {
            normalizedValidatorType(validatorName, typeName, CodeBlock.of("value._sundayValidationValue()"), body)
          } else {
            SwiftModelValidation.type(validatorName, typeName, body)
          },
          outputDirectory = outputDirectory,
          outputGroup = outputGroup,
        )
        addType(unknownPropertyCodingKeyType())
        addType(extensionValueEncoderType())
        exposedProperties.forEach { property ->
          addProperty(
            PropertySpec
              .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false), PUBLIC)
              .build(),
          )
        }
        addProperty(PropertySpec.builder("rawBody", rawBodyType, PUBLIC).build())
        addProperty(debugDescriptionProperty(typeName, exposedProperties))
        addFunction(
          FunctionSpec
            .constructorBuilder()
            .addModifiers(PUBLIC)
            .throws(true)
            .apply {
              exposedProperties.forEach { property ->
                addParameter(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false))
              }
              addParameter("rawBody", rawBodyType)
              exposedProperties.forEach { property ->
                addStatement("self.%N = %N", property.name.swiftIdentifierName, property.name.swiftIdentifierName)
              }
              addStatement("self.rawBody = rawBody")
              addStatement("try %T.validate(self, .response)", validatorName)
            }.build(),
        )
        addFunction(
          FunctionSpec
            .constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter("from", "decoder", DECODER)
            .throws(true)
            .apply {
              if (normalized) {
                addStatement("var validationContext = try %T.decodingValue(decoder)", SwiftModelValidation.context)
                beginControlFlow(
                  "guard",
                  "%T.isValid(normalized: validationContext.originalValue!, .response, context: &validationContext) else",
                  validatorName,
                )
                addStatement("throw validationContext.decodingError")
                endControlFlow("guard")
              } else {
                addCode(closedModelDecodeValidation(this@swiftFallbackTypeSpec))
              }
              if (exposedProperties.isNotEmpty()) {
                addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
              }
              exposedProperties.forEach { property ->
                addStatement(
                  "self.%N = try container.decode%L(%T.self, forKey: .%N)",
                  property.name.swiftIdentifierName,
                  if (property.swiftModelPropertyTypeName(false).optional) "IfPresent" else "",
                  property.swiftModelPropertyTypeName(false).makeNonOptional(),
                  property.name.swiftIdentifierName,
                )
              }
            }.addStatement("self.rawBody = try decoder.singleValueContainer().decode(%T.self)", rawBodyType)
            .apply {
              if (!normalized) {
                addStatement(
                  "var validationContext = try %T.decoding(decoder, numericFields: %L, dynamicFields: %L)",
                  SwiftModelValidation.context,
                  swiftNumericFieldNames(),
                  swiftDynamicFieldNames(),
                )
                beginControlFlow("if", "!%T.isValid(self, .response, context: &validationContext)", validatorName)
                addStatement("throw validationContext.decodingError")
                endControlFlow("if")
              }
            }.build(),
        )
        addFunction(
          FunctionSpec
            .builder("encode")
            .addModifiers(PUBLIC)
            .addParameter("to", "encoder", ENCODER)
            .throws(true)
            .addStatement("try %T.validate(self, .response)", validatorName)
            .addStatement("var container = encoder.container(keyedBy: UnknownPropertyCodingKey.self)")
            .addStatement(
              "let declared: %T = [%L]",
              SET.parameterizedBy(STRING),
              exposedProperties.map { CodeBlock.of("%S", it.serializationName ?: it.name) }.joinToCode(", "),
            ).beginControlFlow("for", "(key, value) in rawBody where !declared.contains(key)")
            .addStatement(
              "try container.encode(AdditionalPropertyValue(value: value), forKey: UnknownPropertyCodingKey(stringValue: key))",
            ).endControlFlow("for")
            .apply {
              exposedProperties.forEach { property ->
                val wireName = property.serializationName ?: property.name
                if (!property.required) {
                  if (property.type.nullable && property.allowedValues?.contains(null) != false) {
                    beginControlFlow(
                      "if",
                      "self.%N != nil || rawBody[%S] != nil",
                      property.name.swiftIdentifierName,
                      wireName,
                    )
                  } else {
                    beginControlFlow("if", "self.%N != nil", property.name.swiftIdentifierName)
                  }
                }
                addStatement(
                  "try container.encode(self.%N, forKey: UnknownPropertyCodingKey(stringValue: %S))",
                  property.name.swiftIdentifierName,
                  wireName,
                )
                if (!property.required) endControlFlow("if")
              }
            }.build(),
        )
        if (exposedProperties.isNotEmpty()) {
          addType(codingKeysType(exposedProperties))
        }
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
      api.models.any { model ->
        model.name == "EventData" &&
          model.discriminatorMappings.values.any { type -> type.modelOrNull(apiIndex) == this }
      }

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

  private val swiftObjectModelsByKey: Map<SwiftModelKey, GeneratedModel> by lazy {
    api.models
      .filter { model -> model.kind == GeneratedModel.Kind.OBJECT }
      .associateBy { model -> model.swiftModelKey() }
  }

  private val swiftInheritingModelsByParentKey: Map<SwiftModelKey, List<GeneratedModel>> by lazy {
    val inheritingModels = mutableMapOf<SwiftModelKey, MutableList<GeneratedModel>>()
    swiftObjectModelsByKey.values.forEach { model ->
      model.inherits
        .mapNotNull { inherited -> inherited.modelOrNull(apiIndex)?.swiftModelKey() }
        .forEach { inheritedKey ->
          inheritingModels.getOrPut(inheritedKey) { mutableListOf() }.add(model)
        }
    }
    inheritingModels
  }

  private val swiftHierarchyCaseModelsByRootKey: Map<SwiftModelKey, List<GeneratedModel>> by lazy {
    swiftObjectModelsByKey.mapValues { (_, model) ->
      val mapped =
        model.discriminatorMappings.values.mapNotNull { type ->
          type.modelOrNull(apiIndex)?.takeIf { it.kind == GeneratedModel.Kind.OBJECT }
        }
      val children =
        model.discriminatorChildren(apiIndex) { swiftInheritingModelsByParentKey[it.swiftModelKey()].orEmpty() }
      (children + mapped).distinct()
    }
  }

  private val protocolHierarchyRootModelKeys: Set<SwiftModelKey> by lazy {
    val rootModels =
      swiftObjectModelsByKey.values.filter { model ->
        !model.patchable &&
          model.discriminator != null &&
          swiftHierarchyCaseModelsByRootKey[model.swiftModelKey()].orEmpty().isNotEmpty() &&
          !model.isProblemModel
      }
    // A mapped leaf may inherit through another declaration; that declaration must also be a protocol.
    val intermediates =
      rootModels.flatMap { root ->
        root.discriminatorMappings.values.mapNotNull(apiIndex::modelOrNull).flatMap { variant ->
          variant.ancestorModels(apiIndex).filter { ancestor -> root in ancestor.ancestorModels(apiIndex) }
        }
      }
    (rootModels + intermediates).mapTo(mutableSetOf()) { model -> model.swiftModelKey() }
  }

  private val protocolHierarchyValueModelKeys: Set<SwiftModelKey> by lazy {
    val valueKeys = mutableSetOf<SwiftModelKey>()
    val pendingModels =
      protocolHierarchyRootModelKeys
        .flatMap { rootKey -> swiftHierarchyCaseModelsByRootKey[rootKey].orEmpty() }
        .toMutableList()
    var index = 0
    while (index < pendingModels.size) {
      val model = pendingModels[index++]
      if (model.patchable || model.isProblemModel) continue
      val key = model.swiftModelKey()
      if (valueKeys.add(key)) {
        pendingModels.addAll(swiftInheritingModelsByParentKey[key].orEmpty())
      }
    }
    valueKeys
  }

  private val recursiveSwiftObjectModelKeys: Set<SwiftModelKey> by lazy {
    val edges =
      swiftObjectModelsByKey.mapValues { (_, model) ->
        buildSet {
          model
            .properties
            .mapNotNull { property -> property.type.directNamedModelOrNull() }
            .filter { referenced -> referenced.kind == GeneratedModel.Kind.OBJECT }
            .mapTo(this) { referenced -> referenced.swiftModelKey() }

          // Inherited properties are flattened into value models, so inheritance participates in stored-value cycles.
          model
            .inherits
            .mapNotNull { inherited -> inherited.modelOrNull(apiIndex) }
            .filter { inherited -> inherited.kind == GeneratedModel.Kind.OBJECT }
            .mapTo(this) { inherited -> inherited.swiftModelKey() }
        }
      }

    swiftObjectModelsByKey.keys.filterTo(mutableSetOf()) { key -> key.reaches(key, edges, mutableSetOf()) }
  }

  private val runtimeProblemTypeName: DeclaredTypeName =
    if (api.hasGeneratedSwiftTypeNamed(PROBLEM.simpleName)) {
      QUALIFIED_PROBLEM
    } else {
      PROBLEM
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
      if (isNullableParameter(GenerationMode.Client)) {
        type.swiftTypeName().makeOptional()
      } else {
        type.swiftTypeName()
      }

    return ParameterSpec
      .builder(name.swiftIdentifierName, typeName)
      .apply {
        defaultValue?.let { defaultValue ->
          defaultValue(defaultValue.swiftValueCode(typeName.makeNonOptional(), type))
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
  ): TypeSpec.Builder? {
    val builder =
      (
        nominalGenerator.generate(this) ?: when (kind) {
          GeneratedModel.Kind.ENUM ->
            if (unknownValue != null) {
              swiftTolerantEnumTypeSpec()
            } else {
              TypeSpec
                .enumBuilder(swiftDeclaredTypeName())
                .addModifiers(PUBLIC)
                .addSwiftDoc(documentation)
                .addSuperTypes(listOf(STRING, CASE_ITERABLE, CODABLE, CUSTOM_STRING_CONVERTIBLE, SENDABLE))
                .apply {
                  swiftEnumEntries().forEach { entry ->
                    addEnumCase(entry.name, entry.value)
                  }
                }.addProperty(swiftEnumDescriptionProperty())
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
      )?.also {
        it.addClosedModelSupport(this, typedEventEnvelopeOrNull() == null)
        it.addModelValidation(this, outputDirectory, outputGroup)
      }
    if (builder == null && isAliasLike) addAliasValidation(outputDirectory, outputGroup)
    return builder
  }

  private fun GeneratedModel.addAliasValidation(
    outputDirectory: OutputDirectory,
    outputGroup: String?,
  ) {
    if (this in normalizedValidationModels) {
      typeRegistry.addModelType(
        SwiftModelValidation.name(swiftDeclaredTypeName()),
        normalizedValidator(),
        outputDirectory = outputDirectory,
        outputGroup = outputGroup,
      )
      return
    }
    val valueType = aliasStoredTypeName(true)
    val name = SwiftModelValidation.name(swiftDeclaredTypeName())
    val reference =
      when (kind) {
        GeneratedModel.Kind.ARRAY -> GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = aliases)
        GeneratedModel.Kind.MAP -> GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = aliases)
        else -> aliases.singleOrNull()
      }
    val property = GeneratedModelProperty("value", reference ?: GeneratedTypeRef.scalar("any"), validation = validation)
    val body =
      SwiftModelValidation.scalar(
        listOf(property),
        modelProperties,
        nested = reference?.swiftNestedValidation(CodeBlock.of("value")),
      )
    typeRegistry.addModelType(
      name,
      SwiftModelValidation.type(name, valueType, body),
      outputDirectory = outputDirectory,
      outputGroup = outputGroup,
    )
  }

  private fun TypeSpec.Builder.addModelValidation(
    model: GeneratedModel,
    outputDirectory: OutputDirectory,
    outputGroup: String?,
  ) {
    if (model.nominal || nominalTypes.branches(model).isNotEmpty()) {
      val name = SwiftModelValidation.name(model.swiftDeclaredTypeName())
      typeRegistry.addModelType(
        name,
        nominalGenerator.validator(model),
        outputDirectory = outputDirectory,
        outputGroup = outputGroup,
      )
      return
    }
    val protocolModel =
      model.isProtocolHierarchyRootModel ||
        model.isProblemHierarchyProtocolModel ||
        model.isExternalDiscriminatorBaseProtocolModel
    val inherited =
      model.isSwiftClassModel &&
        model.inherits.any {
          it.modelOrNull(apiIndex)?.isSwiftClassModel == true
        }
    if (!inherited) addSuperType(SwiftModelValidation.validatable)
    val name = SwiftModelValidation.name(model.swiftDeclaredTypeName())
    if (protocolModel) {
      typeRegistry.addModelType(
        name,
        if (model in normalizedValidationModels) {
          model.normalizedProtocolValidator()
        } else {
          SwiftModelValidation.type(
            name,
            model.swiftDeclaredTypeName().swiftExistentialTypeName(),
            CodeBlock.of("return value.isValid(mode, context: &context)\n"),
          )
        },
        outputDirectory = outputDirectory,
        outputGroup = outputGroup,
      )
      return
    }
    addFunction(SwiftModelValidation.instance(name, inherited))
    if (model in normalizedValidationModels) {
      if (model.kind == GeneratedModel.Kind.OBJECT) {
        addFunction(
          FunctionSpec
            .builder("_sundayValidationValue")
            .addDoc("Supplies current wire fields without serialization or model construction.\n")
            .apply { if (inherited) addModifiers(OVERRIDE) }
            .returns(SwiftValueConstraints.valueType)
            .addCode(
              validationViews.objectView(
                model,
                extensionFieldName.takeIf {
                  model.preservesExtensions()
                },
                model.isSwiftClassModel,
                patchField = { model.patchable && it.name != model.discriminatorNameOrNull() },
                storageProperty = { property ->
                  when {
                    property.name == model.discriminatorNameOrNull() ->
                      property.copy(required = true, type = property.type.copy(nullable = false))
                    model.isProblemModel -> property.normalizedSwiftBaseProblemProperty()
                    else -> property
                  }
                },
              ),
            ).build(),
        )
      }
      typeRegistry.addModelType(
        name,
        model.normalizedValidator(),
        outputDirectory = outputDirectory,
        outputGroup = outputGroup,
      )
      return
    }
    val body =
      when (model.kind) {
        GeneratedModel.Kind.ENUM ->
          CodeBlock
            .builder()
            .apply {
              if (model.unknownValue != null &&
                !model.tolerance.allowsUnknown(options.generationContext(GenerationMode.Client, PayloadUse.Request))
              ) {
                val entry = model.swiftEnumEntries().single { it.value == model.unknownValue }
                beginControlFlow("if", "mode == .request, case .%N = value", entry.name)
                addStatement("return context.reject(.unknownEnum)")
                endControlFlow("if")
              }
              addStatement("return true")
            }.build()
        GeneratedModel.Kind.UNION -> model.swiftUnionValidation()
        GeneratedModel.Kind.OBJECT -> {
          val fields =
            SwiftModelValidation.fields(
              modelProperties.fields(model).map { field ->
                if (model.isProblemModel) {
                  GeneratedModelProperties.Field(
                    field.declaration.normalizedSwiftBaseProblemProperty(),
                    field.effective,
                    field.inherited,
                  )
                } else {
                  field
                }
              },
              modelProperties,
              model.patchable,
              model.swiftClosedPropertyValidation(),
            ) { property ->
              if (model.typedEventEnvelopeOrNull() != null) {
                // Event enum cases already pair the discriminator and payload in their storage type.
                property.type.copy(nullable = false).swiftNestedValidation(CodeBlock.of("fieldValue"))
              } else {
                property.swiftFieldValidation(modelProperties.fields(model).map { it.storage })
              }
            }
          if (model.isSwiftClassModel) {
            CodeBlock
              .builder()
              .add("return context.withObject(value) { context in\n")
              .indent()
              .add(fields)
              .unindent()
              .add("}\n")
              .build()
          } else {
            fields
          }
        }
        else -> CodeBlock.of("return true\n")
      }
    typeRegistry.addModelType(
      name,
      SwiftModelValidation.type(name, model.swiftDeclaredTypeName(), body),
      outputDirectory = outputDirectory,
      outputGroup = outputGroup,
    )
  }

  private fun normalizedObjectValidation(model: GeneratedModel): CodeBlock =
    validationViews.objectValidation(model) { property ->
      property.externalDiscriminator?.let {
        val discriminator = property.externalDiscriminatorProperty(modelProperties.fields(model).map { it.storage })
        val fallback = property.type.modelOrNull(apiIndex)?.let { discriminatorFallbacks[it] }
        CodeBlock
          .builder()
          .add("{ () -> Bool in\n")
          .indent()
          .addStatement(
            "guard let tag = objectFields[%S]?.string else { return context.reject(.discriminator) }",
            discriminator.serializationName ?: discriminator.name,
          ).beginControlFlow("switch", "tag")
          .apply {
            property.externalDiscriminatorModels().forEach { branch ->
              val validator = SwiftModelValidation.name(branch.swiftDeclaredTypeName())
              addStatement("case %S:", branch.discriminatorValue ?: branch.name)
              indent()
              addStatement(
                "guard !value.isUnknown && (!value.hasProjectedSchema || value.represents(%T.self)) else { return context.reject(.discriminator) }",
                validator,
              )
              addStatement(
                "return value.validateNested(mode, schema: %T.self, context: &context) { value, mode, context in %T.isValid(normalized: value, mode, context: &context) }",
                validator,
                validator,
              )
              unindent()
            }
            addStatement("default:")
            indent()
            if (fallback == null) {
              addStatement("return context.reject(.discriminator)")
            } else {
              val validator = SwiftModelValidation.name(fallback.hierarchy.swiftFallbackTypeName(fallback))
              addStatement(
                "guard !value.hasProjectedSchema || value.represents(%T.self) else { return context.reject(.discriminator) }",
                validator,
              )
              addStatement(
                "return value.validateNested(mode, schema: %T.self, context: &context) { value, mode, context in %T.isValid(normalized: value, mode, context: &context) }",
                validator,
                validator,
              )
            }
            unindent()
          }.endControlFlow("switch")
          .unindent()
          .add("}()")
          .build()
      }
    }

  private fun GeneratedModel.normalizedValidator(): TypeSpec.Builder {
    val name = SwiftModelValidation.name(swiftDeclaredTypeName())
    val valueType = if (isAliasLike) aliasStoredTypeName(true) else swiftDeclaredTypeName()
    val reference =
      when (kind) {
        GeneratedModel.Kind.ARRAY -> GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = aliases)
        GeneratedModel.Kind.MAP -> GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = aliases)
        else -> aliases.singleOrNull()
      }
    val projection =
      when (kind) {
        GeneratedModel.Kind.OBJECT -> CodeBlock.of("value._sundayValidationValue()")
        GeneratedModel.Kind.ENUM -> {
          val unknown = unknownValue?.let { raw -> swiftEnumEntries().single { it.value == raw }.name }
          if (unknown == null) {
            CodeBlock.of("%T.string(value.rawValue)", SwiftValueConstraints.valueType)
          } else {
            CodeBlock.of(
              "%T.string(value.rawValue, isUnknown: { if case .%N = value { return true }; return false }())",
              SwiftValueConstraints.valueType,
              unknown,
            )
          }
        }
        GeneratedModel.Kind.UNION ->
          if (isAliasLike) {
            if (valueType == ANY_VALUE) {
              CodeBlock.of("%T(value)", SwiftValueConstraints.valueType)
            } else {
              validationViews.project(aliases.first(), CodeBlock.of("value"))
            }
          } else {
            CodeBlock
              .builder()
              .add("{ () -> %T in\n", SwiftValueConstraints.valueType)
              .indent()
              .beginControlFlow("switch", "value")
              .apply {
                unionCaseModels().forEach { branch ->
                  addStatement(
                    "case .%N(let value): return %T.view(value)",
                    branch.unionCaseName,
                    SwiftModelValidation.name(branch.swiftDeclaredTypeName()),
                  )
                }
                discriminatorFallbacks[this@normalizedValidator]?.let { fallback ->
                  addStatement(
                    "case .%N(let value): return %T.view(value)",
                    fallback.fallbackName.swiftEnumCaseName,
                    SwiftModelValidation.name(swiftFallbackTypeName(fallback)),
                  )
                }
              }.endControlFlow("switch")
              .unindent()
              .add("}()")
              .build()
          }
        else -> validationViews.project(requireNotNull(reference), CodeBlock.of("value"))
      }
    val body =
      when (kind) {
        GeneratedModel.Kind.OBJECT -> normalizedObjectValidation(this)
        GeneratedModel.Kind.UNION ->
          if (isAliasLike) {
            CodeBlock.of(
              "return %L\n",
              validationViews.unionCheck(aliases, unionMode == GeneratedModel.UnionMode.ONE_OF),
            )
          } else {
            normalizedUnionValidation()
          }
        GeneratedModel.Kind.ENUM ->
          CodeBlock
            .builder()
            .apply {
              addStatement("guard let rawValue = value.string else { return context.reject(.invalidValue) }")
              val known =
                swiftEnumEntries()
                  .filterNot {
                    it.value == unknownValue
                  }.map { CodeBlock.of("%S", it.value) }
                  .joinToCode(", ", "[", "]")
              if (unknownValue == null) {
                addStatement("return %L.contains(rawValue) || context.reject(.allowedValue)", known)
              } else if (!tolerance.allowsUnknown(
                  options.generationContext(GenerationMode.Client, PayloadUse.Request),
                )
              ) {
                addStatement(
                  "if mode == .request && (value.isUnknown || !%L.contains(rawValue)) { " +
                    "return context.reject(.unknownEnum) }",
                  known,
                )
                addStatement("return true")
              } else {
                addStatement("_ = rawValue")
                addStatement("return true")
              }
            }.build()
        else ->
          validationViews.validation(
            GeneratedModelProperty("value", requireNotNull(reference), validation = validation),
          )
      }
    return normalizedValidatorType(name, valueType, projection, body)
  }

  private fun normalizedValidatorType(
    name: DeclaredTypeName,
    valueType: TypeName,
    projection: CodeBlock,
    body: CodeBlock,
  ): TypeSpec.Builder =
    SwiftModelValidation
      .type(
        name,
        valueType,
        CodeBlock.of("return isValid(normalized: view(value), mode, context: &context)\n"),
      ).addFunction(
        FunctionSpec
          .builder("view")
          .addModifiers(STATIC)
          .addDoc("Projects storage without constructing or encoding application models.\n")
          .addParameter("_", "value", valueType)
          .returns(SwiftValueConstraints.valueType)
          .addCode("return %L\n", projection)
          .build(),
      ).addFunction(
        SwiftModelValidation
          .function(SwiftValueConstraints.valueType, "value", "normalized")
          .addModifiers(STATIC)
          .addCode(body)
          .build(),
      )

  private fun GeneratedModel.normalizedDiscriminatorValidation(
    cases: List<GeneratedModel>,
    discriminator: UnionDiscriminator,
  ): CodeBlock {
    val fallback = discriminatorFallbacks[this]

    fun CodeBlock.Builder.validateFallback() {
      val resolved = requireNotNull(fallback)
      addStatement(
        "let valid = %T.isValid(normalized: value, mode, context: &context)",
        SwiftModelValidation.name(swiftFallbackTypeName(resolved)),
      )
      addStatement("if valid { context.selectAlternative(%L) }", cases.size)
      addStatement("return valid")
    }
    return CodeBlock
      .builder()
      .apply {
        if (fallback != null) {
          beginControlFlow("if", "value.isUnknown")
          validateFallback()
          endControlFlow("if")
        }
        addStatement(
          "guard let discriminator = value.fields?[%S]?.string else { " +
            "return context.at(.property(%S)) { $0.reject(.discriminator) } }",
          discriminator.wireName,
          discriminator.wireName,
        )
        beginControlFlow("switch", "discriminator")
        discriminator.cases.forEach { branch ->
          addStatement("case %S:", branch.value)
          indent()
          addStatement(
            "let valid = %T.isValid(normalized: value, mode, context: &context)",
            SwiftModelValidation.name(branch.model.swiftDeclaredTypeName()),
          )
          addStatement("if valid { context.selectAlternative(%L) }", cases.indexOf(branch.model))
          addStatement("return valid")
          unindent()
        }
        if (fallback != null) {
          addStatement("default:")
          indent()
          validateFallback()
          unindent()
        } else {
          addStatement(
            "default: return context.at(.property(%S)) { $0.reject(.discriminator) }",
            discriminator.wireName,
          )
        }
        endControlFlow("switch")
      }.build()
  }

  private fun GeneratedModel.normalizedProtocolValidator(): TypeSpec.Builder {
    val cases = swiftHierarchyCaseModels()
    val discriminator = discriminatorPropertyOrNull()
    val fallback = discriminatorFallbacks[this]
    val view =
      CodeBlock
        .builder()
        .add("{ () -> %T in\n", SwiftValueConstraints.valueType)
        .indent()
        .beginControlFlow("switch", "value")
        .apply {
          cases.forEach { branch ->
            addStatement(
              "case let value as %T: return %T.view(value)",
              branch.swiftDeclaredTypeName(),
              SwiftModelValidation.name(branch.swiftDeclaredTypeName()),
            )
          }
          if (fallback != null) {
            val name = swiftFallbackTypeName(fallback)
            addStatement("case let value as %T: return %T.view(value)", name, SwiftModelValidation.name(name))
          }
        }.addStatement("default: return .invalid")
        .endControlFlow("switch")
        .unindent()
        .add("}()")
        .build()
    val body =
      if (isExternalDiscriminatorBaseProtocolModel || discriminator == null) {
        CodeBlock
          .builder()
          .apply {
            cases.forEach { branch ->
              val validator = SwiftModelValidation.name(branch.swiftDeclaredTypeName())
              beginControlFlow("if", "value.represents(%T.self)", validator)
              addStatement("return %T.isValid(normalized: value, mode, context: &context)", validator)
              endControlFlow("if")
            }
            if (fallback != null) {
              beginControlFlow("if", "value.isUnknown")
              addStatement(
                "return %T.isValid(normalized: value, mode, context: &context)",
                SwiftModelValidation.name(swiftFallbackTypeName(fallback)),
              )
              endControlFlow("if")
            }
            add(normalizedObjectValidation(this@normalizedProtocolValidator))
          }.build()
      } else {
        normalizedDiscriminatorValidation(
          cases,
          UnionDiscriminator(
            requireNotNull(discriminator).wireName,
            cases.map { branch ->
              val wireValue =
                discriminatorMappings.entries
                  .firstOrNull {
                    it.value.modelOrNull(
                      apiIndex,
                    ) == branch
                  }?.key
              UnionDiscriminatorCase(wireValue ?: branch.discriminatorValue ?: branch.name, branch)
            },
          ),
        )
      }
    return normalizedValidatorType(
      SwiftModelValidation.name(swiftDeclaredTypeName()),
      if (isDiscriminatorMappingUnionModel && hasSwiftReferenceType) {
        swiftReferenceTypeName()
      } else {
        swiftDeclaredTypeName().swiftExistentialTypeName()
      },
      if (isDiscriminatorMappingUnionModel && hasSwiftReferenceType) {
        CodeBlock.of("%T.view(value)", SwiftModelValidation.name(swiftReferenceTypeName()))
      } else {
        view
      },
      if (modelProperties.hasUnionCommonRules(this)) normalizedCommonValidation(body) else body,
    )
  }

  private fun GeneratedModel.normalizedUnionValidation(): CodeBlock =
    normalizedCommonValidation(normalizedUnionAlternatives())

  private fun GeneratedModel.normalizedCommonValidation(alternatives: CodeBlock): CodeBlock {
    if (modelProperties.fields(this).isEmpty() &&
      modelProperties.patternProperties(this).isEmpty() &&
      modelProperties.additionalProperties(this).isEmpty() &&
      !modelProperties.isClosed(this)
    ) {
      return alternatives
    }
    return CodeBlock
      .builder()
      .apply {
        val fallback = discriminatorFallbacks[this@normalizedCommonValidation]
        if (fallback != null) {
          // The fallback owns the same base fields; recognized branches reuse independent payload schemas.
          addStatement(
            "let checksCommon = !value.isUnknown && (%L).contains(value.fields?[%S]?.string ?? %S)",
            fallback.mappedValues
              .sorted()
              .map { CodeBlock.of("%S", it) }
              .joinToCode(", ", "[", "]"),
            fallback.discriminatorWireName,
            "",
          )
        }
        add("let commonValid = %L{ () -> Bool in\n", if (fallback == null) "" else "!checksCommon || ")
        indent().add(normalizedObjectValidation(this@normalizedCommonValidation)).unindent().add("}()\n")
        addStatement("if !commonValid && !context.collectsDiagnostics { return false }")
        add("let branchValid = { () -> Bool in\n")
          .indent()
          .add(alternatives)
          .unindent()
          .add("}()\n")
        addStatement("return commonValid && branchValid")
      }.build()
  }

  private fun GeneratedModel.normalizedUnionAlternatives(): CodeBlock {
    val cases = unionCaseModels()
    unionDiscriminator(cases)?.let { return normalizedDiscriminatorValidation(cases, it) }
    return CodeBlock
      .builder()
      .addStatement("var matches: [Int] = []")
      .apply {
        unionCaseModels().forEachIndexed { index, branch ->
          beginControlFlow(
            "if",
            "context.matches({ context in %T.isValid(normalized: value, mode, context: &context) })",
            SwiftModelValidation.name(branch.swiftDeclaredTypeName()),
          )
          addStatement("matches.append(%L)", index)
          endControlFlow("if")
        }
      }.beginControlFlow("guard", "!matches.isEmpty else")
      .addStatement("return context.reject(.allowedValue)")
      .endControlFlow("guard")
      .apply {
        if (unionMode == GeneratedModel.UnionMode.ONE_OF) {
          beginControlFlow("guard", "matches.count == 1 else")
          addStatement("return context.reject(.allowedValue)")
          endControlFlow("guard")
        }
      }.addStatement("context.selectAlternative(matches[0])")
      .addStatement("return true")
      .build()
  }

  private fun GeneratedModel.swiftClosedPropertyValidation(storedValue: CodeBlock? = null): CodeBlock? {
    if (!modelProperties.isClosed(this) || modelProperties.patternProperties(this).isNotEmpty()) return null
    val stored =
      storedValue
        ?: if (preservesExtensions()) CodeBlock.of("Set(value.%N.keys)", extensionFieldName) else CodeBlock.of("[]")
    return CodeBlock
      .builder()
      .addStatement("let allowedProperties: %T = %L", SET.parameterizedBy(STRING), allowedPropertyNames(this))
      .addStatement("let suppliedProperties = (context.propertyNames ?? []).union(%L)", stored)
      .beginControlFlow("for", "key in suppliedProperties.sorted() where !allowedProperties.contains(key)")
      .addStatement("valid = context.at(.property(key)) { $0.reject(.additionalProperty) }")
      .addStatement("if !context.collectsDiagnostics { return false }")
      .endControlFlow("for")
      .build()
  }

  private fun GeneratedModelProperty.swiftFieldValidation(properties: List<GeneratedModelProperty>): CodeBlock? {
    val nested = type.copy(nullable = false).swiftNestedValidation(CodeBlock.of("fieldValue"))
    if (externalDiscriminator == null) return nested
    val discriminatorProperty = externalDiscriminatorProperty(properties)
    return CodeBlock
      .builder()
      .add("{ () -> Bool in\n")
      .indent()
      .beginControlFlow("switch", "value.%N", discriminatorProperty.name.swiftIdentifierName)
      .apply {
        externalDiscriminatorModels().forEach { model ->
          addStatement("case %L:", model.discriminatorWireValueCode(discriminatorProperty))
          indent()
          addStatement(
            "guard fieldValue is %T else { return context.reject(.discriminator) }",
            model.swiftDeclaredTypeName(),
          )
          unindent()
        }
        val fallback =
          type
            .modelOrNull(
              apiIndex,
            )?.let { discriminatorFallbacks[it] }
            ?.takeIf { it.externallyDiscriminated }
        addStatement("default:")
        indent()
        if (fallback == null) {
          addStatement("return context.reject(.discriminator)")
        } else {
          addStatement(
            "guard fieldValue is %T else { return context.reject(.discriminator) }",
            fallback.hierarchy.swiftFallbackTypeName(fallback),
          )
        }
        unindent()
      }.endControlFlow("switch")
      .add("return %L\n", nested ?: CodeBlock.of("true"))
      .unindent()
      .add("}()")
      .build()
  }

  private fun GeneratedModel.swiftUnionValidation(): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        beginControlFlow("switch", "value")
        unionCaseModels().forEach { model ->
          addStatement("case .%N(let value):%Wreturn value.isValid(mode, context: &context)", model.unionCaseName)
        }
        discriminatorFallbacks[this@swiftUnionValidation]?.takeUnless { it.externallyDiscriminated }?.let { fallback ->
          addStatement(
            "case .%N(let value):%Wreturn value.isValid(mode, context: &context)",
            fallback.fallbackName.swiftEnumCaseName,
          )
        }
        endControlFlow("switch")
      }.build()

  private fun GeneratedTypeRef.swiftNestedValidation(value: CodeBlock): CodeBlock? {
    if (nullable) {
      val check = copy(nullable = false).swiftNestedValidation(CodeBlock.of("value")) ?: return null
      return CodeBlock.of("%L.map { value in %L } ?? true", value, check)
    }
    if (kind == GeneratedTypeRef.Kind.NAMED) {
      val model = modelOrNull(apiIndex) ?: return null
      if (model.isFreeformObject) return null
      if (model.kind in setOf(GeneratedModel.Kind.ARRAY, GeneratedModel.Kind.MAP)) {
        return CodeBlock.of(
          "%T.isValid(%L, mode, context: &context)",
          SwiftModelValidation.name(model.swiftDeclaredTypeName()),
          value,
        )
      }
      if (model.isAliasLike) {
        return CodeBlock.of(
          "%T.isValid(%L, mode, context: &context)",
          SwiftModelValidation.name(model.swiftDeclaredTypeName()),
          value,
        )
      }
      return CodeBlock.of("!context.validatesNestedModels || %L.isValid(mode, context: &context)", value)
    }
    if (kind !in setOf(GeneratedTypeRef.Kind.ARRAY, GeneratedTypeRef.Kind.MAP)) return null
    val item = arguments.firstOrNull() ?: return null
    val check = item.swiftNestedValidation(CodeBlock.of("element")) ?: return null
    return CodeBlock
      .builder()
      .apply {
        add("{ () -> Bool in\n").indent()
        addStatement("var valid = true")
        if (kind == GeneratedTypeRef.Kind.ARRAY) {
          beginControlFlow("for", "(index, element) in %L.enumerated()", value)
        } else {
          beginControlFlow("for", "key in %L.keys.sorted()", value)
          addStatement("let element = %L[key]!", value)
        }
        add(
          "if !context.at(.%L, { context in %L }) {\n",
          if (kind ==
            GeneratedTypeRef.Kind.ARRAY
          ) {
            "index(index)"
          } else {
            "key(key)"
          },
          check,
        )
        indent()
        addStatement("valid = false")
        addStatement("if !context.collectsDiagnostics { return false }")
        endControlFlow("if")
        endControlFlow("for")
        addStatement("return valid")
        unindent().add("}()")
      }.build()
  }

  private fun GeneratedModel.swiftTolerantEnumTypeSpec(): TypeSpec.Builder {
    val typeName = swiftDeclaredTypeName()
    val entries = swiftEnumEntries()
    val fallbackValue = requireNotNull(unknownValue)
    val fallbackEntry =
      entries.singleOrNull { entry -> entry.value == fallbackValue }
        ?: genError("Swift tolerant enum '$name' unknown value '$fallbackValue' does not match any enum value")
    val knownEntries = entries.filterNot { entry -> entry == fallbackEntry }

    return TypeSpec
      .enumBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSwiftDoc(documentation)
      .addSuperTypes(listOf(CASE_ITERABLE, CODABLE, CUSTOM_STRING_CONVERTIBLE, EQUATABLE, HASHABLE, SENDABLE))
      .apply {
        knownEntries.forEach { entry -> addEnumCase(entry.name) }
        addEnumCase(fallbackEntry.name, STRING)
      }.addProperty(
        PropertySpec
          .builder("allCases", ARRAY.parameterizedBy(typeName), PUBLIC, STATIC)
          .getter(
            FunctionSpec
              .getterBuilder()
              .addCode(
                "return %L\n",
                knownEntries
                  .map { entry -> CodeBlock.of(".%N", entry.name) }
                  .joinToCode(prefix = "[", suffix = "]"),
              ).build(),
          ).build(),
      ).addProperty(
        PropertySpec
          .builder("rawValue", STRING, PUBLIC)
          .getter(
            FunctionSpec
              .getterBuilder()
              .apply {
                beginControlFlow("switch", "self")
                knownEntries.forEach { entry -> addStatement("case .%N:%Wreturn %S", entry.name, entry.value) }
                addStatement("case .%N(let rawValue):%Wreturn rawValue", fallbackEntry.name)
                endControlFlow("switch")
              }.build(),
          ).build(),
      ).addProperty(swiftEnumDescriptionProperty())
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addModifiers(PUBLIC)
          .addParameter("from", "decoder", DECODER)
          .throws(true)
          .addStatement("let rawValue = try decoder.singleValueContainer().decode(%T.self)", STRING)
          .apply {
            beginControlFlow("switch", "rawValue")
            knownEntries.forEach { entry -> addStatement("case %S:%Wself = .%N", entry.value, entry.name) }
            addStatement("default:%Wself = .%N(rawValue)", fallbackEntry.name)
            endControlFlow("switch")
          }.build(),
      ).addFunction(
        FunctionSpec
          .builder("encode")
          .addModifiers(PUBLIC)
          .addParameter("to", "encoder", ENCODER)
          .throws(true)
          .addStatement("var container = encoder.singleValueContainer()")
          .addStatement("try container.encode(rawValue)")
          .build(),
      )
  }

  private fun swiftEnumDescriptionProperty(): PropertySpec =
    PropertySpec
      .builder("description", STRING, PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .addStatement("return rawValue")
          .build(),
      ).build()

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
    return TypedEventEnvelope(
      this,
      discriminatorProperty,
      dataProperty,
      cases,
      dataProperty.externalDiscriminatorFallbackOrNull(this, apiIndex),
    )
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
          add(CUSTOM_DEBUG_STRING_CONVERTIBLE)
          add(SENDABLE)
          if (identifiableProperty != null) {
            add(IDENTIFIABLE)
          }
        },
      ).apply {
        cases.forEach { case ->
          addEnumCase(case.caseName, case.typeName(typeName))
        }
        fallback?.let { resolved ->
          addEnumCase(resolved.fallbackName.swiftEnumCaseName, fallbackEventTypeName(typeName))
        }
        model.properties.forEach { property ->
          addProperty(swiftEventEnvelopeProperty(property))
        }
        if (model.preservesExtensions()) {
          addProperty(
            PropertySpec
              .builder(extensionFieldName, DICTIONARY.parameterizedBy(STRING, ANY_VALUE), PUBLIC)
              .addDoc(CodeBlock.of("Schema-permitted fields retained by the selected event case."))
              .getter(
                FunctionSpec
                  .getterBuilder()
                  .apply {
                    beginControlFlow("switch", "self")
                    cases.forEach {
                      addStatement(
                        "case .%N(let value): return value.%N",
                        it.caseName,
                        extensionFieldName,
                      )
                    }
                    fallback?.let {
                      addStatement(
                        "case .%N(let value): return value.%N",
                        it.fallbackName.swiftEnumCaseName,
                        extensionFieldName,
                      )
                    }
                    endControlFlow("switch")
                  }.build(),
              ).build(),
          )
        }
        addProperty(swiftEventEnvelopeDescriptionProperty())
        addFunction(swiftEventEnvelopeDecoder(typeName))
        addFunction(swiftEventEnvelopeEncoder())
        addType(codingKeysType(listOf(discriminatorProperty)))
        cases.forEach { case ->
          addType(case.swiftTypeSpec(typeName, this@swiftTypeSpec))
        }
        fallback?.let { addType(swiftFallbackEventTypeSpec(typeName)) }
      }
  }

  private fun TypeSpec.Builder.addEventCaseValidation(
    envelope: TypedEventEnvelope,
    caseType: DeclaredTypeName,
    caseName: String,
  ) {
    val name = caseType.nestedType("Validation")
    addSuperType(SwiftModelValidation.validatable)
    addFunction(SwiftModelValidation.instance(name))
    addType(
      SwiftModelValidation
        .type(
          name,
          caseType,
          CodeBlock.of(
            "return %T.isValid(.%N(value), mode, context: &context)\n",
            SwiftModelValidation.name(envelope.model.swiftDeclaredTypeName()),
            caseName,
          ),
        ).build(),
    )
  }

  private fun TypedEventEnvelope.caseDecodeValidation(): CodeBlock =
    CodeBlock
      .builder()
      .addStatement(
        "var validationContext = try %T.decoding(decoder, numericFields: %L, dynamicFields: %L, retainValues: %L)",
        SwiftModelValidation.context,
        model.swiftNumericFieldNames(),
        model.swiftDynamicFieldNames(),
        model in normalizedValidationModels,
      ).beginControlFlow("if", "!isValid(.response, context: &validationContext)")
      .addStatement("throw validationContext.decodingError")
      .endControlFlow("if")
      .build()

  private fun TypedEventEnvelope.swiftEventEnvelopeProperty(property: GeneratedModelProperty): PropertySpec =
    PropertySpec
      .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false), PUBLIC)
      .getter(
        FunctionSpec
          .getterBuilder()
          .apply {
            beginControlFlow("switch", "self")
            cases.forEach { case ->
              if (property == dataProperty && property.type.modelOrNull(apiIndex)?.kind == GeneratedModel.Kind.UNION) {
                addStatement(
                  "case .%N(let value):%Wreturn .%N(value.%N)",
                  case.caseName,
                  case.model.unionCaseName,
                  property.name.swiftIdentifierName,
                )
              } else {
                addStatement("case .%N(let value):%Wreturn value.%N", case.caseName, property.name.swiftIdentifierName)
              }
            }
            fallback?.let { resolved ->
              addStatement(
                "case .%N(let value):%Wreturn value.%N",
                resolved.fallbackName.swiftEnumCaseName,
                property.name.swiftIdentifierName,
              )
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
            fallback?.let { resolved ->
              addStatement(
                "case .%N(let value):%Wreturn value.debugDescription",
                resolved.fallbackName.swiftEnumCaseName,
              )
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
      .addCode(closedModelDecodeValidation(model))
      .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
      .addStatement(
        "let discriminatorValue = try container.decode(%T.self, forKey: .%N)",
        discriminatorProperty.swiftTypeName().makeNonOptional(),
        discriminatorProperty.name.swiftIdentifierName,
      ).apply {
        cases.forEach { case ->
          beginControlFlow("if", "%L", case.discriminatorValueCondition(discriminatorProperty))
          addStatement("self = .%N(try %T(from: decoder))", case.caseName, case.typeName(typeName))
          addStatement("return")
          endControlFlow("if")
        }
        if (fallback != null) {
          addStatement(
            "self = .%N(try %T(from: decoder))",
            fallback.fallbackName.swiftEnumCaseName,
            fallbackEventTypeName(typeName),
          )
        } else {
          addStatement(
            "throw %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
            DECODING_ERROR,
            discriminatorProperty.name.swiftIdentifierName,
            "unsupported value for \"${discriminatorProperty.name}\"",
          )
        }
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
        fallback?.let { resolved ->
          addStatement("case .%N(let value):%Wtry value.encode(to: encoder)", resolved.fallbackName.swiftEnumCaseName)
        }
        endControlFlow("switch")
      }.build()

  private fun TypedEventEnvelope.fallbackEventTypeName(envelopeTypeName: DeclaredTypeName): DeclaredTypeName =
    envelopeTypeName.nestedType((fallback?.fallbackName ?: "Unknown") + "Event")

  private fun TypedEventEnvelope.swiftFallbackEventTypeSpec(envelopeTypeName: DeclaredTypeName): TypeSpec {
    val resolvedFallback = fallback ?: error("Fallback event type requires a discriminator fallback")
    val typeName = fallbackEventTypeName(envelopeTypeName)
    val dataTypeName = resolvedFallback.hierarchy.swiftFallbackTypeName(resolvedFallback)
    return TypeSpec
      .structBuilder(typeName.simpleName)
      .addModifiers(PUBLIC)
      .addSuperTypes(listOf(CODABLE, CUSTOM_DEBUG_STRING_CONVERTIBLE, SENDABLE))
      .apply {
        addEventCaseValidation(
          this@swiftFallbackEventTypeSpec,
          typeName,
          resolvedFallback.fallbackName.swiftEnumCaseName,
        )
        model.properties.forEach { property ->
          addProperty(
            PropertySpec
              .builder(
                property.name.swiftIdentifierName,
                if (property == dataProperty) dataTypeName else property.swiftModelPropertyTypeName(false),
                PUBLIC,
              ).build(),
          )
        }
        addClosedModelSupport(model)
        addProperty(debugDescriptionProperty(typeName, model.properties))
        addFunction(
          FunctionSpec
            .constructorBuilder()
            .addModifiers(PUBLIC)
            .throws(true)
            .apply {
              addExtensionParameter(model)
              model.properties.forEach { property ->
                addParameter(
                  property.name.swiftIdentifierName,
                  if (property == dataProperty) dataTypeName else property.swiftModelPropertyTypeName(false),
                )
                addStatement("self.%N = %N", property.name.swiftIdentifierName, property.name.swiftIdentifierName)
              }
              addStatement("try validate(.response)")
            }.build(),
        )
        addFunction(
          FunctionSpec
            .constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter("from", "decoder", DECODER)
            .throws(true)
            .addCode(closedModelDecodeValidation(model))
            .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
            .apply {
              if (model.preservesExtensions()) addCode(extensionDecode(model))
              model.properties.forEach { property ->
                val propertyType =
                  if (property ==
                    dataProperty
                  ) {
                    dataTypeName
                  } else {
                    property.swiftModelPropertyTypeName(false)
                  }
                addStatement(
                  "self.%N = try container.decode%L(%T.self, forKey: .%N)",
                  property.name.swiftIdentifierName,
                  if (propertyType.optional) "IfPresent" else "",
                  propertyType.makeNonOptional(),
                  property.name.swiftIdentifierName,
                )
              }
              addCode(caseDecodeValidation())
            }.build(),
        )
        addFunction(
          FunctionSpec
            .builder("encode")
            .addModifiers(PUBLIC)
            .addParameter("to", "encoder", ENCODER)
            .throws(true)
            .addStatement("try validate(.response)")
            .addStatement("var container = encoder.container(keyedBy: CodingKeys.self)")
            .apply {
              if (model.preservesExtensions()) addCode(extensionEncode(model))
              model.properties.forEach { property ->
                addStatement(
                  "try container.encode%L(self.%N, forKey: .%N)",
                  property.swiftEncodingSuffix(),
                  property.name.swiftIdentifierName,
                  property.name.swiftIdentifierName,
                )
              }
            }.build(),
        )
        addType(codingKeysType(model.properties))
      }.build()
  }

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
          add(CUSTOM_DEBUG_STRING_CONVERTIBLE)
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
        addClosedModelSupport(envelope.model)
        addEventCaseValidation(envelope, caseTypeName, caseName)
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

  private fun TypedEventEnvelopeCase.discriminatorValueCondition(
    discriminatorProperty: GeneratedModelProperty,
  ): CodeBlock {
    val enumModel = discriminatorProperty.type.modelOrNull(apiIndex)
    return if (enumModel?.unknownValue != null) {
      CodeBlock.of("discriminatorValue.rawValue == %S", value)
    } else {
      CodeBlock.of("discriminatorValue == %L", discriminatorValueCode(discriminatorProperty))
    }
  }

  private fun TypedEventEnvelope.caseConstructor(properties: List<GeneratedModelProperty>): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .throws(true)
      .apply {
        addExtensionParameter(model)
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
        addStatement("try validate(.response)")
      }.build()

  private fun TypedEventEnvelope.caseDecoderConstructor(properties: List<GeneratedModelProperty>): FunctionSpec =
    FunctionSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addParameter("from", "decoder", DECODER)
      .throws(true)
      .addCode(closedModelDecodeValidation(model))
      .addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
      .apply {
        if (model.preservesExtensions()) addCode(extensionDecode(model))
        properties.forEach { property ->
          addStatement(
            "self.%N = try container.decode%L(%T.self, forKey: .%N)",
            property.name.swiftIdentifierName,
            if (property.swiftTypeName().optional) "IfPresent" else "",
            property.swiftTypeName().makeNonOptional(),
            property.name.swiftIdentifierName,
          )
        }
        addCode(caseDecodeValidation())
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
      .addStatement("try validate(.response)")
      .addStatement("var container = encoder.container(keyedBy: CodingKeys.self)")
      .addStatement(
        "try container.encode(%L, forKey: .%N)",
        case.discriminatorValueCode(discriminatorProperty),
        discriminatorProperty.name.swiftIdentifierName,
      ).apply {
        if (model.preservesExtensions()) addCode(extensionEncode(model))
        properties.forEach { property ->
          addStatement(
            "try container.encode%L(self.%N, forKey: .%N)",
            property.swiftEncodingSuffix(),
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
        externallyDiscriminated &&
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
        !isProblemModel

  private val GeneratedModel.isPatchableObjectValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !isRecursiveSwiftObjectModel &&
        patchable &&
        discriminator == null &&
        discriminatorMappings.isEmpty() &&
        !externallyDiscriminated &&
        properties.none { property -> property.externalDiscriminator != null } &&
        !isProblemModel

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

  private val GeneratedModel.isSwiftValueModel: Boolean
    get() =
      !isRecursiveSwiftObjectModel &&
        (
          isSimpleObjectValueModel ||
            isPatchableObjectValueModel ||
            isProtocolHierarchyValueModel ||
            isProblemHierarchyValueModel ||
            isInheritedObjectValueModel ||
            isExternalDiscriminatorEnvelopeValueModel
        )

  private val GeneratedModel.isSwiftClassModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !isSwiftValueModel &&
        !isProtocolHierarchyRootModel &&
        !isProblemHierarchyProtocolModel &&
        !isExternalDiscriminatorBaseProtocolModel &&
        !isExternalDiscriminatorCaseValueModel &&
        typedEventEnvelopeOrNull() == null

  private val GeneratedModel.isExternalDiscriminatorEnvelopeValueModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        !patchable &&
        !isProblemModel &&
        properties.any { property -> property.externalDiscriminator != null }

  private val GeneratedModel.isRecursiveSwiftObjectModel: Boolean
    get() = swiftModelKey() in recursiveSwiftObjectModelKeys

  private val GeneratedModel.isProtocolHierarchyRootModel: Boolean
    get() = swiftModelKey() in protocolHierarchyRootModelKeys

  private val GeneratedModel.isProtocolHierarchyValueModel: Boolean
    get() = !patchable && !isProblemModel && swiftModelKey() in protocolHierarchyValueModelKeys

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
      .addSuperTypes(listOf(CODABLE, CUSTOM_DEBUG_STRING_CONVERTIBLE, SENDABLE))
      .apply {
        constructorProperties().forEach { property ->
          addProperty(
            PropertySpec
              .abstractBuilder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(false))
              .addSwiftDoc(property.documentation)
              .getter(FunctionSpec.getterBuilder().build())
              .build(),
          )
        }
      }

  private fun GeneratedModel.swiftExternalDiscriminatorCaseValueTypeSpec(): TypeSpec.Builder {
    val typeName = swiftDeclaredTypeName()
    val localProperties = allConstructorProperties()
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
    typeBuilder.addFunction(modelConstructor(this, emptyList(), localProperties, null, false, false))
    typeBuilder.addFunction(
      modelDecoderConstructor(
        this,
        localProperties,
        null,
        false,
        false,
        true,
      ),
    )
    typeBuilder.addFunction(modelEncoderFunction(this, localProperties, null, null, false, false))
    localProperties.forEach { property ->
      typeBuilder.addFunction(
        modelWithFunction(
          this,
          typeName,
          property,
          localProperties,
          patchable = false,
          validates = constructorThrows(),
        ),
      )
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
    val discriminator = unionDiscriminator(cases)
    val fallback = discriminatorFallbacks[this]?.takeUnless { candidate -> candidate.externallyDiscriminated }
    val fallbackTypeName = fallback?.let { resolved -> swiftFallbackTypeName(resolved) }
    val propertyBranches = cases.map { model -> UnionPropertyBranch(model, model.uniqueRequiredWireNames(cases)) }

    return TypeSpec
      .enumBuilder(typeName)
      .addModifiers(PUBLIC)
      .addSwiftDoc(documentation)
      .addSuperTypes(listOf(CODABLE, CUSTOM_DEBUG_STRING_CONVERTIBLE, SENDABLE))
      .apply {
        cases.forEach { model ->
          addEnumCase(model.unionCaseName, model.swiftDeclaredTypeName())
        }
        if (fallback != null && fallbackTypeName != null) {
          addEnumCase(fallback.fallbackName.swiftEnumCaseName, fallbackTypeName)
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
                if (fallback != null) {
                  addStatement(
                    "case .%N(let value):%Wreturn value.debugDescription",
                    fallback.fallbackName.swiftEnumCaseName,
                  )
                }
                endControlFlow("switch")
              }.build(),
          ).build(),
      ).apply {
        if (this@swiftUnionTypeSpecOrNull in normalizedValidationModels) return@apply
        if (discriminator != null) {
          addType(unionCodingKeysType(listOf(discriminator.wireName)))
        } else {
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
        if (this@unionDecoderConstructor in normalizedValidationModels) {
          addStatement("var context = try %T.decodingValue(decoder)", SwiftModelValidation.context)
          beginControlFlow(
            "guard",
            "%T.isValid(normalized: context.originalValue!, .response, context: &context) else",
            SwiftModelValidation.name(swiftDeclaredTypeName()),
          )
          addStatement("throw context.decodingError")
          endControlFlow("guard")
          beginControlFlow("switch", "context.selectedAlternative")
          cases.forEachIndexed { index, branch ->
            addStatement(
              "case %L: self = .%N(try %T(from: decoder))",
              index,
              branch.unionCaseName,
              branch.swiftDeclaredTypeName(),
            )
          }
          discriminatorFallbacks[this@unionDecoderConstructor]?.let { fallback ->
            addStatement(
              "case %L: self = .%N(try %T(from: decoder))",
              cases.size,
              fallback.fallbackName.swiftEnumCaseName,
              swiftFallbackTypeName(fallback),
            )
          }
          addStatement("default: preconditionFailure(%S)", "Canonical union validation did not select an alternative")
          endControlFlow("switch")
        } else if (discriminator != null) {
          addStatement(
            "let container = try decoder.container(keyedBy: CodingKeys.self)",
          )
          addStatement(
            "let discriminatorValue = try container.decode(%T.self, forKey: .%N)",
            STRING,
            discriminator.wireName.swiftIdentifierName,
          )
          discriminator.cases.forEach { case ->
            beginControlFlow("if", "discriminatorValue == %S", case.value)
            addStatement(
              "self = .%N(try %T(from: decoder))",
              case.model.unionCaseName,
              case.model.swiftDeclaredTypeName(),
            )
            addStatement("return")
            endControlFlow("if")
          }
          val fallback = discriminatorFallbacks[this@unionDecoderConstructor]?.takeUnless { it.externallyDiscriminated }
          if (fallback != null) {
            addStatement(
              "self = .%N(try %T(from: decoder))",
              fallback.fallbackName.swiftEnumCaseName,
              swiftFallbackTypeName(fallback),
            )
          } else {
            addStatement(
              "throw %T.dataCorruptedError(forKey: .%N, in: container, debugDescription: %S)",
              DECODING_ERROR,
              discriminator.wireName.swiftIdentifierName,
              "unsupported value for \"${discriminator.wireName}\"",
            )
          }
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
      .apply {
        if (this@unionEncoderFunction in normalizedValidationModels) {
          addStatement("try validate(.response)")
        }
      }.addStatement("var container = encoder.singleValueContainer()")
      .apply {
        beginControlFlow("switch", "self")
        cases.forEach { model ->
          addStatement("case .%N(let value):%Wtry container.encode(value)", model.unionCaseName)
        }
        discriminatorFallbacks[this@unionEncoderFunction]
          ?.takeUnless { fallback -> fallback.externallyDiscriminated }
          ?.let { fallback ->
            addStatement(
              "case .%N(let value):%Wtry container.encode(value)",
              fallback.fallbackName.swiftEnumCaseName,
            )
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
    val constrainedFields = SwiftModelConstraints.fields(modelProperties.fields(this), modelProperties, patchable)
    val inheritedModel = inherits.firstOrNull()?.modelOrNull(apiIndex)
    val inheritedTypeName = inheritedModel?.swiftDeclaredTypeName()
    val inheritedProperties =
      modelProperties
        .fields(this)
        .filter { it.inherited }
        .map { if (isProblemModel) it.storage.normalizedSwiftBaseProblemProperty() else it.storage }
        .filterNot { it.name == discriminatorPropertyOrNull()?.name }
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
        isInheritedObjectValueModel ||
        isPatchableObjectValueModel ||
        // Recursive children need reference storage, but a value parent cannot be a Swift superclass.
        (isRecursiveReferenceModel && inheritedModel?.isSwiftValueModel == true)
    val allowsInheritedPropertyOverrides = flattensInheritedProperties && !isProblemHierarchyValueModel
    val localProperties =
      localConstructorProperties(inheritedProperties, allowOverrides = allowsInheritedPropertyOverrides)
        .map { property ->
          if (isProblemModel) {
            property.normalizedSwiftBaseProblemProperty()
          } else {
            property
          }
        }
    val effectiveInheritedProperties =
      inheritedProperties
        .withoutOverridesFrom(localProperties)
        .takeIf { allowsInheritedPropertyOverrides }
        ?: inheritedProperties
    val storedLocalProperties =
      if (isRootProblemModel || isProblemHierarchyProtocolModel) {
        localProperties.filterNot { property -> property.isSatisfiedByBaseProblemClass() }
      } else {
        localProperties
      }
    val identifiableProperty = identifiablePropertyOrNull()
    val discriminatorProperty = discriminatorPropertyOrNull()
    val isValueModel = isSwiftValueModel
    val isImmutableModel = isValueModel && !patchable || isRecursiveReferenceModel
    val storedProperties =
      if (flattensInheritedProperties) {
        effectiveInheritedProperties + localProperties
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
            if (inheritedModel?.isProtocolHierarchyRootModel == true) {
              addSuperType(requireNotNull(inheritedTypeName))
            } else {
              addSuperTypes(listOf(CODABLE, CUSTOM_DEBUG_STRING_CONVERTIBLE, SENDABLE))
            }
          } else if (inheritedTypeName != null && !flattensInheritedProperties) {
            addSuperType(inheritedTypeName)
          } else if (isProblemHierarchyValueModel) {
            addSuperType(inheritedTypeName ?: runtimeProblemTypeName)
          } else if (isProtocolHierarchyValueModel) {
            if (inheritedTypeName != null) {
              addSuperType(inheritedTypeName)
            } else {
              addSuperTypes(listOf(CODABLE, CUSTOM_DEBUG_STRING_CONVERTIBLE, SENDABLE))
            }
          } else {
            addSuperTypes(
              buildList {
                add(CODABLE)
                add(CUSTOM_DEBUG_STRING_CONVERTIBLE)
                if (isValueModel || isRecursiveReferenceModel && !hasInheritingModels) {
                  add(SENDABLE)
                }
              },
            )
          }
          if (isRecursiveReferenceModel &&
            (hasInheritingModels || inheritedTypeName != null && !flattensInheritedProperties)
          ) {
            // Recursive models only emit immutable storage; Swift requires unchecked conformance for class inheritance.
            addSuperType(UNCHECKED_SENDABLE)
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

    if (isProblemHierarchyValueModel) {
      // The runtime Problem protocol includes optional standard fields even when the source omits them.
      listOf("detail" to STRING, "instance" to URL).forEach { (name, type) ->
        if (storedProperties.none { it.name == name }) {
          typeBuilder.addProperty(
            PropertySpec
              .builder(name, type.makeOptional(), PUBLIC)
              .addDoc("The optional standard problem field, absent from this schema.\n")
              .getter(FunctionSpec.getterBuilder().addStatement("return nil").build())
              .build(),
          )
        }
      }
    }

    if (!isProtocolModel) {
      if (!isValueModel && !patchable) {
        decodingDefaultProperties().forEach(typeBuilder::addProperty)
      }
      typeBuilder.addProperty(
        debugDescriptionProperty(
          typeName,
          if (inheritedTypeName != null && discriminatorProperty != null) {
            listOf(discriminatorProperty) + effectiveInheritedProperties + localProperties
          } else {
            effectiveInheritedProperties + localProperties
          },
          inheritedTypeName != null && !flattensInheritedProperties,
        ),
      )
      typeBuilder.addFunction(
        modelConstructor(
          this,
          if (flattensInheritedProperties) emptyList() else effectiveInheritedProperties,
          if (flattensInheritedProperties) {
            effectiveInheritedProperties + localProperties
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
          this,
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
          this,
          storedProperties,
          inheritedTypeName.takeUnless { flattensInheritedProperties },
          discriminatorProperty,
          patchable,
          false,
        ),
      )
      (effectiveInheritedProperties + localProperties).forEach { property ->
        typeBuilder.addFunction(
          modelWithFunction(
            this,
            typeName,
            property,
            effectiveInheritedProperties + localProperties,
            property in effectiveInheritedProperties && !flattensInheritedProperties,
            patchable,
            constructorThrows(),
          ),
        )
      }
    }
    referenceTypeOrNull(typeName, outputDirectory, outputGroup)?.let { referenceType ->
      if (!isProtocolModel) {
        typeBuilder.addType(referenceType)
      }
    }
    patchOpExtensionOrNull(
      typeName,
      effectiveInheritedProperties + localProperties,
      patchable,
      constructorThrows(),
    )?.let { extension ->
      typeBuilder.associatedExtensions.add(extension)
    }
    if (!isProtocolModel) {
      typeBuilder.addType(
        codingKeysType(
          (storedProperties + constrainedFields.map { it.storage }).distinctBy { it.wireName },
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
    allowOverrides: Boolean = false,
  ): List<GeneratedModelProperty> {
    if (allowOverrides) {
      val storage = modelProperties.fields(this).associateBy { it.wireName }
      return constructorProperties().map { storage[it.wireName]?.storage ?: it }
    }
    val inheritedWireNames = inheritedProperties.map { property -> property.wireName }.toSet()
    return constructorProperties().filterNot { property -> property.wireName in inheritedWireNames }
  }

  private fun List<GeneratedModelProperty>.withoutOverridesFrom(
    properties: List<GeneratedModelProperty>,
  ): List<GeneratedModelProperty> {
    val overrideWireNames = properties.map { property -> property.wireName }.toSet()
    return filterNot { property -> property.wireName in overrideWireNames }
  }

  private fun GeneratedModel.allConstructorProperties(): List<GeneratedModelProperty> {
    val inheritedProperties =
      inherits.flatMap { inherited ->
        inherited.modelOrNull(apiIndex)?.allConstructorProperties().orEmpty()
      }
    val allowsInheritedPropertyOverrides =
      isProtocolHierarchyValueModel ||
        isInheritedObjectValueModel ||
        isExternalDiscriminatorCaseValueModel ||
        isExternalDiscriminatorEnvelopeValueModel
    val localProperties =
      localConstructorProperties(inheritedProperties, allowOverrides = allowsInheritedPropertyOverrides)
        .map { property ->
          if (isProblemModel) {
            property.normalizedSwiftBaseProblemProperty()
          } else {
            property
          }
        }
    val effectiveInheritedProperties =
      if (allowsInheritedPropertyOverrides) {
        inheritedProperties.withoutOverridesFrom(localProperties)
      } else {
        inheritedProperties
      }
    return effectiveInheritedProperties + localProperties
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
        (aliases.size >= 2 || (aliases.isNotEmpty() && discriminatorFallbacks.containsKey(this))) &&
        unionCaseModels().size == aliases.size

  private val GeneratedModel.hasSwiftReferenceType: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        discriminator != null &&
        !externallyDiscriminated &&
        (inherits.isEmpty() || isDiscriminatorMappingUnionModel) &&
        swiftHierarchyCaseModels().isNotEmpty()

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
        isRecursiveSwiftObjectModel

  private fun GeneratedModel.swiftHierarchyCaseModels(): List<GeneratedModel> =
    swiftHierarchyCaseModelsByRootKey[swiftModelKey()].orEmpty()

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
    val fallback: GeneratedDiscriminatorFallback?,
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
    if (
      discriminator == null ||
      externallyDiscriminated ||
      inherits.isNotEmpty() &&
      !isDiscriminatorMappingUnionModel
    ) {
      return null
    }

    val discriminatorProperty = discriminatorPropertyOrNull() ?: return null
    val inheritingModels = swiftHierarchyCaseModels()
    val fallback = discriminatorFallbacks[this]?.takeUnless { candidate -> candidate.externallyDiscriminated }
    val fallbackTypeName = fallback?.let { resolved -> swiftFallbackTypeName(resolved) }

    if (inheritingModels.isEmpty()) {
      return null
    }

    val anyRefTypeName =
      swiftReferenceTypeName(typeName)
    typeRegistry.addReferenceType(typeName, anyRefTypeName)
    val referenceValueTypeName =
      if (!isDiscriminatorMappingUnionModel) {
        if (isProtocolHierarchyRootModel || isProblemHierarchyProtocolModel) {
          typeName.swiftExistentialTypeName()
        } else {
          typeName
        }
      } else {
        null
      }

    val referenceType =
      TypeSpec
        .enumBuilder(anyRefTypeName)
        .addModifiers(PUBLIC)
        .addSuperTypes(
          buildList {
            add(CODABLE)
            add(CUSTOM_DEBUG_STRING_CONVERTIBLE)
            if (inheritingModels.all { model -> model.isSwiftSendableModel }) {
              add(SENDABLE)
            }
          },
        ).apply {
          addSuperType(SwiftModelValidation.validatable)
          val validatorName = SwiftModelValidation.name(anyRefTypeName)
          addFunction(SwiftModelValidation.instance(validatorName))
          val validation =
            CodeBlock
              .builder()
              .apply {
                beginControlFlow("switch", "value")
                inheritingModels.forEach { model ->
                  addStatement(
                    "case .%N(let value):%Wreturn value.isValid(mode, context: &context)",
                    model.discriminatorCaseName,
                  )
                }
                if (fallback != null) {
                  addStatement(
                    "case .%N(let value):%Wreturn value.isValid(mode, context: &context)",
                    fallback.fallbackName.swiftEnumCaseName,
                  )
                }
                endControlFlow("switch")
              }.build()
          typeRegistry.addModelType(
            validatorName,
            if (this@referenceTypeOrNull in normalizedValidationModels) {
              val projection =
                CodeBlock
                  .builder()
                  .add("{ () -> %T in\n", SwiftValueConstraints.valueType)
                  .indent()
                  .beginControlFlow("switch", "value")
                  .apply {
                    inheritingModels.forEach { model ->
                      addStatement(
                        "case .%N(let value): return %T.view(value)",
                        model.discriminatorCaseName,
                        SwiftModelValidation.name(model.swiftDeclaredTypeName()),
                      )
                    }
                    if (fallback != null && fallbackTypeName != null) {
                      addStatement(
                        "case .%N(let value): return %T.view(value)",
                        fallback.fallbackName.swiftEnumCaseName,
                        SwiftModelValidation.name(fallbackTypeName),
                      )
                    }
                  }.endControlFlow("switch")
                  .unindent()
                  .add("}()")
                  .build()
              normalizedValidatorType(
                validatorName,
                anyRefTypeName,
                projection,
                CodeBlock.of(
                  "return %T.isValid(normalized: value, mode, context: &context)\n",
                  SwiftModelValidation.name(typeName),
                ),
              )
            } else {
              SwiftModelValidation.type(validatorName, anyRefTypeName, validation)
            },
            outputDirectory = outputDirectory,
            outputGroup = outputGroup,
          )
          inheritingModels.forEach { model ->
            addEnumCase(model.discriminatorCaseName, model.swiftDeclaredTypeName())
          }
          if (fallback != null && fallbackTypeName != null) {
            addEnumCase(fallback.fallbackName.swiftEnumCaseName, fallbackTypeName)
          }

          referenceValueTypeName?.let { valueTypeName ->
            addProperty(
              PropertySpec
                .builder("value", valueTypeName, PUBLIC)
                .getter(
                  FunctionSpec
                    .getterBuilder()
                    .apply {
                      beginControlFlow("switch", "self")
                      inheritingModels.forEach { model ->
                        addStatement("case .%N(let value):%Wreturn value", model.discriminatorCaseName)
                      }
                      if (fallback != null) {
                        addStatement("case .%N(let value):%Wreturn value", fallback.fallbackName.swiftEnumCaseName)
                      }
                      endControlFlow("switch")
                    }.build(),
                ).build(),
            )
          }
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
                    if (fallback != null) {
                      addStatement(
                        "case .%N(let value):%Wreturn value.debugDescription",
                        fallback.fallbackName.swiftEnumCaseName,
                      )
                    }
                    endControlFlow("switch")
                  }.build(),
              ).build(),
          )
          referenceValueTypeName?.let { valueTypeName ->
            addFunction(
              FunctionSpec
                .constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter("value", valueTypeName)
                .apply {
                  beginControlFlow("switch", "value")
                  inheritingModels.forEach { model ->
                    addStatement(
                      "case let value as %T:%Wself = .%N(value)",
                      model.swiftDeclaredTypeName(),
                      model.discriminatorCaseName,
                    )
                  }
                  if (fallback != null && fallbackTypeName != null) {
                    addStatement(
                      "case let value as %T:%Wself = .%N(value)",
                      fallbackTypeName,
                      fallback.fallbackName.swiftEnumCaseName,
                    )
                  }
                  addStatement("default:%WfatalError(\"Invalid value type\")")
                  endControlFlow("switch")
                }.build(),
            )
          }
          addFunction(
            FunctionSpec
              .constructorBuilder()
              .addModifiers(PUBLIC)
              .addParameter("from", "decoder", DECODER)
              .throws(true)
              .apply {
                if (this@referenceTypeOrNull in normalizedValidationModels) {
                  addStatement("var context = try %T.decodingValue(decoder)", SwiftModelValidation.context)
                  beginControlFlow(
                    "guard",
                    "%T.isValid(normalized: context.originalValue!, .response, context: &context) else",
                    SwiftModelValidation.name(typeName),
                  )
                  addStatement("throw context.decodingError")
                  endControlFlow("guard")
                  beginControlFlow("switch", "context.selectedAlternative")
                  inheritingModels.forEachIndexed { index, model ->
                    addStatement(
                      "case %L: self = .%N(try %T(from: decoder))",
                      index,
                      model.discriminatorCaseName,
                      model.swiftDeclaredTypeName(),
                    )
                  }
                  if (fallback != null && fallbackTypeName != null) {
                    addStatement(
                      "case %L: self = .%N(try %T(from: decoder))",
                      inheritingModels.size,
                      fallback.fallbackName.swiftEnumCaseName,
                      fallbackTypeName,
                    )
                  }
                  addStatement(
                    "default: preconditionFailure(%S)",
                    "Canonical discriminator validation did not select an alternative",
                  )
                  endControlFlow("switch")
                } else {
                  addStatement("let container = try decoder.container(keyedBy: CodingKeys.self)")
                  addStatement(
                    "let type = try container.decode(%T.self, forKey: CodingKeys.%N)",
                    STRING,
                    discriminatorProperty.name.swiftIdentifierName,
                  )
                  beginControlFlow("switch", "type")
                  inheritingModels.forEach { model ->
                    addStatement(
                      "case %S:%Wself = .%N(try %T(from: decoder))",
                      model.discriminatorValue ?: model.name,
                      model.discriminatorCaseName,
                      model.swiftDeclaredTypeName(),
                    )
                  }
                  if (fallback != null && fallbackTypeName != null) {
                    addStatement(
                      "default:%Wself = .%N(try %T(from: decoder))",
                      fallback.fallbackName.swiftEnumCaseName,
                      fallbackTypeName,
                    )
                  } else {
                    addStatement(
                      "default:\nthrow %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
                      DECODING_ERROR,
                      discriminatorProperty.name.swiftIdentifierName,
                      "unsupported value for \"${discriminatorProperty.name}\"",
                    )
                  }
                  endControlFlow("switch")
                }
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
                if (fallback != null) {
                  addStatement(
                    "case .%N(let value):%Wtry container.encode(value)",
                    fallback.fallbackName.swiftEnumCaseName,
                  )
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
    validates: Boolean,
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
          .throws(validates)
          .returns(SelfTypeName.INSTANCE)
          .apply {
            properties.forEach { property ->
              addParameter(
                ParameterSpec
                  .builder(property.name.swiftIdentifierName, property.swiftPatchOpTypeName())
                  .defaultValue(".unchanged")
                  .build(),
              )
            }
          }.addStatement(
            if (validates) "try %T.merge(%T(%L))" else "%T.merge(%T(%L))",
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

  // Swift overrides cannot add throws, so a class family shares the initializer and with-method contract.
  private fun GeneratedModel.constructorThrows(): Boolean {
    fun constrained(
      model: GeneratedModel,
      visited: Set<GeneratedModel> = emptySet(),
    ): Boolean {
      if (model in visited) return false
      val next = visited + model

      fun referenceConstrained(type: GeneratedTypeRef): Boolean =
        type.modelOrNull(apiIndex)?.let { constrained(it, next) } == true ||
          type.arguments.any { referenceConstrained(it) }
      return model.validation.isNotEmpty() ||
        modelProperties.fields(model).any { it.effective.externalDiscriminator != null } ||
        model.preservesExtensions() &&
        (
          modelProperties.isClosed(model) ||
            modelProperties.patternProperties(model).isNotEmpty() ||
            modelProperties.additionalProperties(model).isNotEmpty()
        ) ||
        modelProperties.fields(model).any {
          it.effective.name != model.discriminatorNameOrNull() &&
            (
              SwiftModelConstraints.hasValueConstraints(it.effective) ||
                it.effective.allowedValues != null ||
                referenceConstrained(it.effective.type)
            )
        } ||
        model.aliases.any { referenceConstrained(it) } ||
        model.discriminatorMappings.values.any { referenceConstrained(it) }
    }
    if (isSwiftValueModel || patchable) return constrained(this)
    val family = mutableSetOf<GeneratedModel>()
    val pending = ArrayDeque<GeneratedModel>()
    pending.add(this)
    while (pending.isNotEmpty()) {
      val model = pending.removeFirst()
      if (model.isSwiftValueModel || !family.add(model)) continue
      pending.addAll(model.inherits.mapNotNull { it.modelOrNull(apiIndex) })
      pending.addAll(api.models.filter { candidate -> candidate.inherits.any { it.modelOrNull(apiIndex) == model } })
    }
    return family.any { model ->
      !model.patchable && constrained(model)
    }
  }

  private val extensionFieldName by lazy {
    val names = api.models.flatMap { it.properties }.mapTo(mutableSetOf()) { it.name.swiftIdentifierName }
    generateSequence("additionalProperties") { "${it}_" }.first { it !in names }
  }

  private fun GeneratedModel.preservesExtensions(): Boolean =
    options.preserveUnknownFields &&
      kind == GeneratedModel.Kind.OBJECT &&
      !isProtocolHierarchyRootModel &&
      !isProblemHierarchyProtocolModel &&
      !isExternalDiscriminatorBaseProtocolModel

  private fun GeneratedModel.inheritsExtensionStorage(): Boolean =
    isSwiftClassModel &&
      inherits.mapNotNull { it.modelOrNull(apiIndex) }.any { it.isSwiftClassModel && it.preservesExtensions() }

  private fun extensionValue(model: GeneratedModel): CodeBlock =
    CodeBlock.of("%N.filter { !%L.contains($0.key) }", extensionFieldName, allowedPropertyNames(model))

  private fun modelConstructor(
    model: GeneratedModel,
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
      .throws(model.constructorThrows())
      .apply {
        (inheritedProperties + localProperties).forEach { property ->
          addParameter(
            ParameterSpec
              .builder(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(patchable))
              .apply {
                if (patchable) {
                  defaultValue(".unchanged")
                } else if (property.swiftTypeName().optional) {
                  defaultValue("nil")
                }
              }.build(),
          )
        }
        if (model.preservesExtensions()) {
          addParameter(
            ParameterSpec
              .builder(
                extensionFieldName,
                DICTIONARY.parameterizedBy(STRING, ANY_VALUE),
              ).defaultValue("[:]")
              .build(),
          )
          if (!model.inheritsExtensionStorage()) addStatement("self.%N = %L", extensionFieldName, extensionValue(model))
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
              }.let { parameters ->
                if (model.inheritsExtensionStorage()) {
                  parameters +
                    CodeBlock.of("%N: %L", extensionFieldName, extensionValue(model))
                } else {
                  parameters
                }
              }.joinToCode(",%W")

          addStatement(
            if (model.inherits.any {
                it.modelOrNull(apiIndex)?.constructorThrows() == true
              }
            ) {
              "try super.init(%L)"
            } else {
              "super.init(%L)"
            },
            inheritedConstructorParameters,
          )
        }
        if (model.constructorThrows()) addCode(model.swiftConstructorValidation())
      }.build()
  }

  private fun GeneratedModel.swiftDynamicFieldNames(): CodeBlock =
    modelProperties
      .fields(this)
      .filter { it.effective.allowedValues != null && modelProperties.declarationType(it.effective.type).name == "any" }
      .map { CodeBlock.of("%S", it.wireName) }
      .joinToCode(", ", "[", "]")

  private fun GeneratedModel.swiftNumericFieldNames(): CodeBlock =
    modelProperties
      .fields(this)
      .filter { field ->
        GeneratedNumericBounds.parse(field.effective.validation, field.wireName).isNotEmpty() ||
          "multipleOf" in field.effective.validation ||
          field.effective.allowedValues?.any { it is Number } == true &&
          modelProperties.declarationType(field.effective.type).name != "any"
      }.map { CodeBlock.of("%S", it.wireName) }
      .joinToCode(", ", "[", "]")

  private fun GeneratedModel.swiftConstructorValidation(decoding: Boolean = false): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        // Base initializers finish their storage before the most-derived initializer validates the graph.
        if (isSwiftClassModel) beginControlFlow("if", "Swift.type(of: self) == %T.self", swiftDeclaredTypeName())
        if (decoding) {
          addStatement(
            "var validationContext = try %T.decoding(decoder, numericFields: %L, dynamicFields: %L, retainValues: %L)",
            SwiftModelValidation.context,
            swiftNumericFieldNames(),
            swiftDynamicFieldNames(),
            this@swiftConstructorValidation in normalizedValidationModels,
          )
          beginControlFlow(
            "if",
            "!%T.isValid(self, .response, context: &validationContext)",
            SwiftModelValidation.name(swiftDeclaredTypeName()),
          )
          addStatement("throw validationContext.decodingError")
          endControlFlow("if")
        } else {
          addStatement("try %T.validate(self, .response)", SwiftModelValidation.name(swiftDeclaredTypeName()))
        }
        if (isSwiftClassModel) endControlFlow("if")
      }.build()

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

  private fun TypeSpec.Builder.addClosedModelSupport(
    model: GeneratedModel,
    storage: Boolean = true,
  ) {
    if (model.kind != GeneratedModel.Kind.OBJECT ||
      (
        !modelProperties.isClosed(model) &&
          modelProperties.patternProperties(model).isEmpty() &&
          !model.preservesExtensions() &&
          model.additionalProperties?.type == null
      ) ||
      model.isProtocolHierarchyRootModel ||
      model.isProblemHierarchyProtocolModel ||
      model.isExternalDiscriminatorBaseProtocolModel
    ) {
      return
    }
    addType(unknownPropertyCodingKeyType())
    if (model.preservesExtensions() && storage) {
      addType(extensionValueEncoderType())
      if (!model.inheritsExtensionStorage()) {
        addProperty(
          PropertySpec
            .builder(extensionFieldName, DICTIONARY.parameterizedBy(STRING, ANY_VALUE), PUBLIC)
            .addDoc(CodeBlock.of("Schema-permitted dynamic fields, serialized at their original JSON level."))
            .build(),
        )
      }
      if (model !in normalizedValidationModels) {
        addType(
          TypeSpec
            .structBuilder(
              "AdditionalPropertiesValidator",
            ).addModifiers(PRIVATE)
            .addSuperType(DeclaredTypeName.typeName("Swift.Decodable"))
            .addFunction(
              FunctionSpec
                .constructorBuilder()
                .addParameter("from", "decoder", DECODER)
                .throws(true)
                .addCode(closedModelDecodeValidation(model))
                .build(),
            ).build(),
        )
      }
    }
    if (model.isSwiftClassModel && (modelProperties.isClosed(model) || model.preservesExtensions())) {
      addProperty(
        PropertySpec
          .builder("_sundayAllowedPropertyNames", SET.parameterizedBy(STRING), CLASS)
          .apply {
            if (model.inherits.mapNotNull { it.modelOrNull(apiIndex) }.any {
                it.isSwiftClassModel && (modelProperties.isClosed(it) || it.preservesExtensions())
              }
            ) {
              addModifiers(OVERRIDE)
            }
          }.getter(FunctionSpec.getterBuilder().addStatement("return %L", allowedPropertyNames(model)).build())
          .build(),
      )
    }
  }

  // AnyValue's ordered dictionary uses an unkeyed representation with Foundation's JSONEncoder.
  // Encode containers recursively so preserved JSON objects remain objects with any supported encoder.
  private fun extensionValueEncoderType(): TypeSpec =
    TypeSpec
      .structBuilder("AdditionalPropertyValue")
      .addModifiers(PRIVATE)
      .addSuperType(DeclaredTypeName.typeName("Swift.Encodable"))
      .addProperty(PropertySpec.builder("value", ANY_VALUE).build())
      .addFunction(
        FunctionSpec
          .builder("encode")
          .addParameter("to", "encoder", ENCODER)
          .throws(true)
          .addCode(
            CodeBlock.of(
              """
              switch value {
              case .dictionary(let values):
                var container = encoder.container(keyedBy: UnknownPropertyCodingKey.self)
                for (key, item) in values {
                  guard case .string(let name) = key else {
                    throw %T.invalidValue(key, .init(codingPath: encoder.codingPath, debugDescription: "JSON object keys must be strings"))
                  }
                  try container.encode(AdditionalPropertyValue(value: item), forKey: UnknownPropertyCodingKey(stringValue: name))
                }
              case .array(let values):
                var container = encoder.unkeyedContainer()
                for item in values { try container.encode(AdditionalPropertyValue(value: item)) }
              default:
                try value.encode(to: encoder)
              }

              """.trimIndent(),
              DeclaredTypeName.typeName("Swift.EncodingError"),
            ),
          ).build(),
      ).build()

  private fun closedModelDecodeValidation(
    model: GeneratedModel,
    dynamic: Boolean = false,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        if (model in normalizedValidationModels) return@apply
        val patterns = modelProperties.patternProperties(model)
        if (patterns.isEmpty() && model.additionalProperties?.type == null) {
          return@apply
        }
        if (modelProperties.isClosed(model) || patterns.isNotEmpty() || model.additionalProperties?.type != null) {
          addStatement("let allProperties = try decoder.container(keyedBy: UnknownPropertyCodingKey.self)")
          addStatement(
            "let allowedProperties: %T = %L",
            SET.parameterizedBy(STRING),
            if (dynamic && (modelProperties.isClosed(model) || model.preservesExtensions())) {
              CodeBlock.of("Self._sundayAllowedPropertyNames")
            } else {
              allowedPropertyNames(model)
            },
          )
          beginControlFlow("for", "key in allProperties.allKeys")
          addStatement(
            "%L matched = allowedProperties.contains(key.stringValue)",
            if (patterns.isEmpty()) "let" else "var",
          )
          patterns.forEach { pattern ->
            beginControlFlow(
              "if",
              "key.stringValue.range(of: %L, options: .regularExpression) != nil",
              SwiftModelConstraints.regexLiteral(pattern.pattern),
            )
            addStatement("matched = true")
            addStatement("_ = try allProperties.decode(%T.self, forKey: key)", pattern.type.swiftStoredTypeName())
            val property =
              GeneratedModelProperty(
                "value",
                pattern.type,
                validation = pattern.validation,
                allowedValues = pattern.allowedValues,
              )
            val field = GeneratedModelProperties.Field(property, property, false)
            if (pattern.validation.isNotEmpty() || pattern.allowedValues != null) {
              addStatement("let container = allProperties")
              add(SwiftModelConstraints.decode(listOf(field), false, modelProperties) { CodeBlock.of("key") })
            }
            endControlFlow("if")
          }
          if (modelProperties.isClosed(model)) {
            beginControlFlow("if", "!matched")
            addStatement(
              "throw %T.dataCorruptedError(forKey: key, in: allProperties, debugDescription: %S + key.stringValue)",
              DECODING_ERROR,
              "Additional properties are not allowed: ",
            )
            endControlFlow("if")
          } else if (model.additionalProperties?.type != null) {
            beginControlFlow("if", "!matched")
            val additional = model.additionalProperties
            addStatement("_ = try allProperties.decode(%T.self, forKey: key)", additional.type.swiftStoredTypeName())
            if (additional.validation.isNotEmpty() || additional.allowedValues != null) {
              val property =
                GeneratedModelProperty(
                  "value",
                  additional.type,
                  validation = additional.validation,
                  allowedValues = additional.allowedValues,
                )
              addStatement("let container = allProperties")
              add(
                SwiftModelConstraints.decode(
                  listOf(GeneratedModelProperties.Field(property, property, false)),
                  false,
                  modelProperties,
                ) {
                  CodeBlock.of("key")
                },
              )
            }
            endControlFlow("if")
          } else {
            addStatement("_ = matched")
          }
          endControlFlow("for")
        }
      }.build()

  private fun allowedPropertyNames(model: GeneratedModel): CodeBlock =
    CodeBlock.of("[%L]", modelProperties.fields(model).map { CodeBlock.of("%S", it.wireName) }.joinToCode(", "))

  // Unlike the model's finite CodingKeys enum, this key accepts every input property name.
  private fun unknownPropertyCodingKeyType(): TypeSpec =
    TypeSpec
      .structBuilder("UnknownPropertyCodingKey")
      .addModifiers(FILEPRIVATE)
      .addSuperType(CODING_KEY)
      .addProperty(PropertySpec.builder("stringValue", STRING).build())
      .addProperty(PropertySpec.builder("intValue", INT.makeOptional()).initializer("nil").build())
      .addFunction(
        FunctionSpec
          .constructorBuilder()
          .addParameter("stringValue", STRING)
          .addStatement("self.stringValue = stringValue")
          .build(),
      ).addFunction(
        FunctionSpec
          .constructorBuilder()
          .addParameter("intValue", INT)
          .addStatement("self.stringValue = String(intValue)")
          .build(),
      ).build()

  private fun extensionDecode(
    model: GeneratedModel,
    dynamic: Boolean = false,
  ): CodeBlock =
    CodeBlock
      .builder()
      .addStatement("let extensionContainer = try decoder.container(keyedBy: UnknownPropertyCodingKey.self)")
      .addStatement(
        "let declaredFields: %T = %L",
        SET.parameterizedBy(STRING),
        if (dynamic) CodeBlock.of("Self._sundayAllowedPropertyNames") else allowedPropertyNames(model),
      ).addStatement(
        "self.%N = try %T(uniqueKeysWithValues: extensionContainer.allKeys.filter { !declaredFields.contains($0.stringValue) }.map { key in",
        extensionFieldName,
        DICTIONARY.parameterizedBy(STRING, ANY_VALUE),
      ).addStatement("  (key.stringValue, try extensionContainer.decode(%T.self, forKey: key))", ANY_VALUE)
      .addStatement("})")
      .build()

  private fun extensionEncode(
    model: GeneratedModel,
    inherited: Boolean = false,
  ): CodeBlock =
    CodeBlock
      .builder()
      .apply {
        if (model !in normalizedValidationModels) {
          addStatement("let extensionDecoder = %T()", ANY_VALUE_DECODER)
          addStatement("extensionDecoder.userInfo = encoder.userInfo")
          addStatement(
            "_ = try extensionDecoder.decode(AdditionalPropertiesValidator.self, from: %T.dictionary(.init(uniqueKeysWithValues: %N.map { (.string($0.key), $0.value) })))",
            ANY_VALUE,
            extensionFieldName,
          )
        }
        if (!inherited) {
          addStatement("var extensionContainer = encoder.container(keyedBy: UnknownPropertyCodingKey.self)")
          beginControlFlow("for", "(key, value) in %N", extensionFieldName)
          addStatement(
            "try extensionContainer.encode(AdditionalPropertyValue(value: value), forKey: UnknownPropertyCodingKey(stringValue: key))",
          )
          endControlFlow("for")
        }
      }.build()

  private fun FunctionSpec.Builder.addExtensionParameter(model: GeneratedModel) {
    if (!model.preservesExtensions()) return
    addParameter(
      ParameterSpec
        .builder(
          extensionFieldName,
          DICTIONARY.parameterizedBy(STRING, ANY_VALUE),
        ).defaultValue("[:]")
        .build(),
    )
    addStatement("self.%N = %L", extensionFieldName, extensionValue(model))
  }

  private fun modelDecoderConstructor(
    model: GeneratedModel,
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
        addCode(closedModelDecodeValidation(model, model.isSwiftClassModel))
        localProperties.filter { property -> property.externalDiscriminator == null }.forEach { property ->
          val coderSuffix =
            when {
              patchable -> ""
              property.swiftTypeName().optional -> "IfPresent"
              else -> ""
            }
          val codingTypeName =
            if (patchable) {
              property.swiftPatchOpTypeName()
            } else {
              property.swiftTypeName().makeNonOptional()
            }
          val default =
            if (!patchable && !property.required) {
              decodingDefaultNames[model to property.wireName]?.let { CodeBlock.of("Self.%N", it) }
                ?: property.swiftDefault(model)
            } else {
              null
            }
          val decode =
            CodeBlock.of(
              "container.decode%L(%T.self, forKey: .%N)",
              coderSuffix,
              codingTypeName,
              property.name.swiftIdentifierName,
            )
          addStatement(
            "self.%N = try %L",
            property.name.swiftIdentifierName,
            default?.let {
              CodeBlock.of(
                "container.contains(.%N) ? %L : %L",
                property.name.swiftIdentifierName,
                decode,
                patchHelpers.decodingDefault(it),
              )
            }
              ?: decode,
          )
        }
        localProperties.filter { property -> property.externalDiscriminator != null }.forEach { property ->
          addExternalDiscriminatorDecoder(property, localProperties)
        }
        if (addNilProblemParameters) {
          addStatement("self.parameters = nil")
        }
        if (model.preservesExtensions() &&
          !model.inheritsExtensionStorage()
        ) {
          addCode(extensionDecode(model, model.isSwiftClassModel))
        }
        if (inheritedTypeName != null || isRootProblemModel) {
          addStatement("try super.init(from: decoder)")
        }
        addCode(model.swiftConstructorValidation(decoding = true))
      }.build()
  }

  private fun modelEncoderFunction(
    model: GeneratedModel,
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
        addCode(model.swiftConstructorValidation())
        if (model.preservesExtensions()) addCode(extensionEncode(model, model.inheritsExtensionStorage()))
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
            if (patchable) "" else property.swiftEncodingSuffix(),
            property.name.swiftIdentifierName,
            property.name.swiftIdentifierName,
          )
        }
        localProperties.filter { property -> property.externalDiscriminator != null }.forEach { property ->
          addExternalDiscriminatorEncoder(property)
        }
      }.build()

  private fun FunctionSpec.Builder.addExternalDiscriminatorDecoder(
    property: GeneratedModelProperty,
    properties: List<GeneratedModelProperty>,
  ) {
    val discriminatorProperty = property.externalDiscriminatorProperty(properties)
    val propertyTypeName = property.swiftTypeName()
    val coderSuffix = if (propertyTypeName.optional) "IfPresent" else ""

    if (propertyTypeName.optional) {
      beginControlFlow(
        "if",
        "try !container.contains(.%N) || container.decodeNil(forKey: .%N)",
        property.name.swiftIdentifierName,
        property.name.swiftIdentifierName,
      )
      addStatement("self.%N = nil", property.name.swiftIdentifierName)
      nextControlFlow("else", "")
    }
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
    val fallback =
      property.type
        .modelOrNull(apiIndex)
        ?.let { model -> discriminatorFallbacks[model] }
        ?.takeIf { candidate -> candidate.externallyDiscriminated }
    if (fallback != null) {
      addStatement(
        "default:%Wself.%N = try container.decode%L(%T.self, forKey: .%N)",
        property.name.swiftIdentifierName,
        coderSuffix,
        fallback.hierarchy.swiftFallbackTypeName(fallback),
        property.name.swiftIdentifierName,
      )
    } else {
      addStatement(
        "default:\nthrow %T.dataCorruptedError(%>\nforKey: CodingKeys.%N,\nin: container,\ndebugDescription: %S%<\n)",
        DECODING_ERROR,
        discriminatorProperty.name.swiftIdentifierName,
        "unsupported value for \"${discriminatorProperty.name}\"",
      )
    }
    endControlFlow("switch")
    if (propertyTypeName.optional) endControlFlow("if")
  }

  private fun FunctionSpec.Builder.addExternalDiscriminatorEncoder(property: GeneratedModelProperty) {
    // Canonical validation already selected the branch; opening the Codable existential preserves its concrete encoder.
    val identifier = property.name.swiftIdentifierName
    if (property.swiftTypeName().optional) {
      beginControlFlow("if", "let payload = self.%N", identifier)
      addStatement("try container.encode(payload, forKey: .%N)", identifier)
      if (property.swiftEncodingSuffix().isEmpty()) {
        nextControlFlow("else", "")
        addStatement("try container.encodeNil(forKey: .%N)", identifier)
      }
      endControlFlow("if")
    } else {
      addStatement("try container.encode(self.%N, forKey: .%N)", identifier, identifier)
    }
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
    model: GeneratedModel,
    typeName: DeclaredTypeName,
    property: GeneratedModelProperty,
    properties: List<GeneratedModelProperty>,
    override: Boolean = false,
    patchable: Boolean = false,
    validates: Boolean = false,
  ): FunctionSpec =
    FunctionSpec
      .builder("with${property.name.toUpperCamelCase()}")
      .addModifiers(PUBLIC)
      .throws(validates)
      .apply {
        if (override) {
          addModifiers(OVERRIDE)
        }
      }.addParameter(property.name.swiftIdentifierName, property.swiftModelPropertyTypeName(patchable))
      .returns(typeName)
      .addStatement(
        if (validates) "return try %T(%L)" else "return %T(%L)",
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
          }.let { parameters ->
            if (model.preservesExtensions()) {
              parameters +
                CodeBlock.of(
                  "%N: %N",
                  extensionFieldName,
                  extensionFieldName,
                )
            } else {
              parameters
            }
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
        codingKeyProperties.distinctBy { it.serializationName ?: it.name }.forEach { property ->
          addEnumCase(property.name.swiftIdentifierName, property.serializationName ?: property.name)
        }
      }.build()

  private fun GeneratedModelProperty.swiftEncodingSuffix(): String =
    if (!required && !modelProperties.acceptsNull(type)) "IfPresent" else ""

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
      swiftPatchOpTypeName()
    } else {
      swiftTypeName()
    }

  private fun GeneratedModelProperty.swiftPatchOpTypeName(): TypeName {
    val base = if (patchDeletionAllowed == true) PATCH_OP else UPDATE_OP
    return base.parameterizedBy(type.swiftTypeName().makeNonOptional())
  }

  private fun GeneratedOperation.operationFunction(service: GeneratedService): FunctionSpec {
    val response = primarySuccessResponse()
    val returnType = returnTypeName(response)
    val parameters = swiftParameterViews(service)
    val security =
      api
        .clientSecurity(service, this, options.generationContext(GenerationMode.Client))
        ?.swiftBindings(options.profile)
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
      functionBuilder.addCode(streamingCode(response, parameters, security))
      return functionBuilder.build()
    }

    if (exchange == null) {
      functionBuilder
        .throws(true)
        .addCode(operationCode(response, parameters, security))
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
          .add(requestCode(response, parameters, security = security, asSpec = true))
          .add("%<\n)\n")
          .build(),
      )
    }

    return functionBuilder.build()
  }

  private fun GeneratedOperation.operationCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
    security: CodeBlock?,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add("return %T(%>\n", if (isNilableOperation) NILABLE_OPERATION else OPERATION)
      .add("transport: self.transport,\n")
      .add("spec: %T%L(%>\n", OPERATION_SPEC, if (requestBody.isSwiftStreamingRequestBody) ".streaming" else "")
      .add(requestCode(response, parameters, security = security))
      .add("%<\n)")
      .apply {
        nullify?.takeIf { isNilableOperation }?.let { nullify ->
          add(",\n")
          add(nilifySpecCode(nullify))
        }
        response?.type?.swiftPayloadValidation("response")?.let { validation ->
          add(",\nresponseValidation: %L", validation)
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
    security: CodeBlock?,
  ): CodeBlock =
    when (streaming?.kind) {
      GeneratedStreaming.Kind.EVENT_STREAM ->
        eventStreamCode(response, parameters, security)

      else ->
        CodeBlock
          .builder()
          .add("return self.transport.eventSource(%>\n")
          .add(requestCode(response, parameters, eventStream = true, security = security, asSpec = true))
          .add("%<\n)")
          .build()
    }

  private fun GeneratedOperation.eventStreamCode(
    response: GeneratedResponse?,
    parameters: List<GeneratedOperationParameter>,
    security: CodeBlock?,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    val responseType = response?.type
    val originalReturnType = responseType?.swiftTypeName() ?: ANY

    builder.add("return self.transport.eventStream(%>\n")
    builder.add(requestCode(response, parameters, eventStream = true, security = security, asSpec = true))

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
        val decodeUnwrap =
          if (decodeType != originalReturnType && responseType?.isDiscriminatorMappingUnionType != true) {
            ".value"
          } else {
            ""
          }
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
    security: CodeBlock? = null,
    asSpec: Boolean = false,
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

    security?.let { builder.add(",\nsecurity: %L", it) }
    val names = NameAllocator()
    parameters.forEach { names.newName(it.name, it) }
    val parameterChecks =
      parameters.filterNot { it.isConstant }.mapNotNull { parameter ->
        val capture = names.newName("parameter")
        parameter.type
          .copy(nullable = parameter.isNullable)
          .swiftNestedValidation(CodeBlock.of("%N", capture))
          ?.let { check -> CodeBlock.of("%N = %N", capture, parameter.name) to check }
      }
    if (parameterChecks.isNotEmpty()) {
      // Capture arguments outside the callback scope shared by the canonical validation helpers.
      builder.add(",\nparameterValidation: { [%L] in\n%>", parameterChecks.map { it.first }.joinToCode(", "))
      builder.add("let mode = %T.request\n", SwiftModelValidation.mode)
      builder.add("var context = %T(collectsDiagnostics: true)\n", SwiftModelValidation.context)
      parameterChecks.forEach { (_, check) -> builder.add("_ = %L\n", check) }
      builder.add("if !context.diagnostics.isEmpty { throw context.validationError }\n%<}")
    }
    if (!eventStream && !requestBody.isSwiftStreamingRequestBody) {
      requestBody?.type?.swiftPayloadValidation("request")?.let { validation ->
        builder.add(",\nrequestValidation: %L", validation)
      }
    }
    val arguments = builder.build()
    return if (asSpec && (security != null || parameterChecks.isNotEmpty())) {
      CodeBlock.of(
        "spec: %T%L(%>\n%L%<\n)",
        OPERATION_SPEC,
        if (requestBody.isSwiftStreamingRequestBody) ".streaming" else "",
        arguments,
      )
    } else {
      arguments
    }
  }

  private fun GeneratedTypeRef.swiftPayloadValidation(mode: String): CodeBlock? {
    val model = modelOrNull(apiIndex)
    if (model?.isAliasLike == true && !nullable) {
      return CodeBlock.of(
        "{ try %T.validate($0, .%L) }",
        SwiftModelValidation.name(model.swiftDeclaredTypeName()),
        mode,
      )
    }
    val validation = swiftNestedValidation(CodeBlock.of("value")) ?: return null
    return CodeBlock
      .builder()
      .add("{ value in\n")
      .indent()
      .addStatement("let mode = %T.%L", SwiftModelValidation.mode, mode)
      .addStatement("var context = %T(collectsDiagnostics: true)", SwiftModelValidation.context)
      .beginControlFlow("if", "!(%L)", validation)
      .addStatement("throw context.validationError")
      .endControlFlow("if")
      .unindent()
      .add("}")
      .build()
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
        ?.takeIf { api.clientSecurity(service, this, options.generationContext(GenerationMode.Client)) == null }
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
      isNullable = isNullableParameter(GenerationMode.Client),
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
      kind == GeneratedTypeRef.Kind.MAP ->
        DICTIONARY.parameterizedBy(STRING, arguments.firstOrNull()?.swiftTypeName() ?: ANY)
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
    } ?: if (kind == GeneratedTypeRef.Kind.UNION) {
      ANY
    } else if (publicExistential) {
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
      return scopedModelNames[this] ?: DeclaredTypeName.typeName(".${name.toUpperCamelCase()}")
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
        GeneratedTypeRef.Kind.MAP ->
          DICTIONARY.parameterizedBy(STRING, arguments.firstOrNull()?.swiftTypeName() ?: ANY_VALUE)
        GeneratedTypeRef.Kind.UNION -> ANY_VALUE
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
        GeneratedTypeRef.Kind.MAP ->
          DICTIONARY.parameterizedBy(STRING, arguments.firstOrNull()?.swiftPublicTypeName() ?: ANY_VALUE)
        GeneratedTypeRef.Kind.UNION -> ANY_VALUE
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
        GeneratedTypeRef.Kind.UNION -> ANY_VALUE
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
          model.isDiscriminatorMappingUnionModel -> model.swiftReferenceTypeName()
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

  private val GeneratedTypeRef.isDiscriminatorMappingUnionType: Boolean
    get() = directNamedModelOrNull()?.isDiscriminatorMappingUnionModel == true

  private val GeneratedModel.isDiscriminatorMappingUnionModel: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
        discriminatorMappings.isNotEmpty() &&
        discriminatorMappings.values.any { type ->
          type.modelOrNull(apiIndex)?.structurallyInherits(this) == false
        }

  private fun GeneratedModel.structurallyInherits(base: GeneratedModel): Boolean =
    inherits.any { inherited ->
      val inheritedModel = inherited.modelOrNull(apiIndex) ?: return@any false
      inheritedModel == base || inheritedModel.structurallyInherits(base)
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
        !modelProperties.isClosed(this) &&
        modelProperties.fields(this).isEmpty() &&
        modelProperties.patternProperties(this).isEmpty() &&
        modelProperties.additionalProperties(this).isEmpty() &&
        discriminator == null &&
        discriminatorMappings.isEmpty()

  private val GeneratedModel.isAliasLike: Boolean
    get() =
      (kind == GeneratedModel.Kind.SCALAR_ALIAS && !nominal) ||
        kind == GeneratedModel.Kind.ARRAY ||
        kind == GeneratedModel.Kind.MAP ||
        (kind == GeneratedModel.Kind.UNION && !isObjectUnionEnum && nominalTypes.branches(this).isEmpty())

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

  private fun GeneratedModelProperty.swiftDefault(model: GeneratedModel): CodeBlock? {
    val field = modelProperties.fields(model).firstOrNull { it.wireName == (serializationName ?: name) }
    if (field?.effective?.required == true) return null
    val nominal = modelProperties.declarationModel(type)?.takeIf { it.nominal }
    if (nominal != null) {
      val scalar = nominalTypes.scalar(nominal)
      val effective = field?.effective ?: this
      val rawProperty =
        effective.copy(
          type = scalar.type,
          validation =
            scalar.property.validation + effective.validation,
        )
      val rawDefault =
        SwiftModelDefaults.render(model.name, rawProperty, modelProperties) { value ->
          value.swiftValueCode(scalar.type.swiftTypeName(), scalar.type)
        } ?: return null
      scalar.patterns.forEach { pattern ->
        SwiftModelDefaults.render(
          model.name,
          rawProperty.copy(validation = mapOf("pattern" to pattern)),
          modelProperties,
        ) {
          rawDefault
        }
      }
      return CodeBlock.of("%T(rawValue: %L)!", swiftTypeName().makeNonOptional(), rawDefault)
    }
    return SwiftModelDefaults.render(model.name, field?.effective ?: this, modelProperties) { value ->
      value.swiftValueCode(swiftTypeName().makeNonOptional(), type)
    }
  }

  private fun GeneratedModel.swiftClassChain(): List<GeneratedModel> =
    generateSequence(this) { it.inherits.firstOrNull()?.modelOrNull(apiIndex) }
      .takeWhile { it.isSwiftClassModel && !it.patchable }
      .toList()

  // Dynamic type defaults keep superclass decoding intact and never leak into nested decoded objects.
  private fun inheritedDecodingDefaultNames(): Map<Pair<GeneratedModel, String>, String> {
    val declarations = linkedSetOf<Pair<GeneratedModel, String>>()
    api.models.filter { it.isSwiftClassModel && !it.patchable }.forEach { model ->
      val chain = model.swiftClassChain()
      val parent = chain.getOrNull(1) ?: return@forEach
      val parentFields = modelProperties.fields(parent).associateBy { it.wireName }
      modelProperties.fields(model).filter { it.inherited && !it.declaration.required }.forEach field@{ field ->
        val parentField = parentFields[field.wireName] ?: return@field
        if (field.storage.externalDiscriminator == null &&
          field.storage.swiftDefault(model) != parentField.storage.swiftDefault(parent)
        ) {
          val owner = chain.last { ancestor -> modelProperties.fields(ancestor).any { it.wireName == field.wireName } }
          declarations += owner to field.wireName
        }
      }
    }
    val names = NameAllocator()
    api.models
      .flatMap { it.properties }
      .map { it.name.swiftIdentifierName }
      .distinct()
      .forEach { names.newName(it) }
    return declarations
      .sortedBy { (owner, wireName) ->
        "${owner.scope}:${owner.name}:$wireName"
      }.associateWith { declaration ->
        names.newName("_sundayDefault${declaration.second.toUpperCamelCase()}", declaration)
      }
  }

  private fun GeneratedModel.decodingDefaultProperties(): List<PropertySpec> {
    val chain = swiftClassChain()
    val parent = chain.getOrNull(1)
    val fields = modelProperties.fields(this).associateBy { it.wireName }
    val parentFields = parent?.let(modelProperties::fields).orEmpty().associateBy { it.wireName }
    return decodingDefaultNames.mapNotNull { (declaration, name) ->
      val (owner, wireName) = declaration
      if (owner !in chain) return@mapNotNull null
      val field = fields.getValue(wireName)
      val default = field.storage.swiftDefault(this)
      if (owner != this && default == parentFields.getValue(wireName).storage.swiftDefault(requireNotNull(parent))) {
        return@mapNotNull null
      }
      PropertySpec
        .builder(name, field.declaration.swiftTypeName().makeOptional(), CLASS)
        .apply { if (owner != this@decodingDefaultProperties) addModifiers(OVERRIDE) }
        .getter(FunctionSpec.getterBuilder().addStatement("return %L", default ?: CodeBlock.of("nil")).build())
        .build()
    }
  }

  private fun Any.swiftValueCode(
    typeName: TypeName,
    typeRef: GeneratedTypeRef?,
  ): CodeBlock {
    val nominal = typeRef?.let(modelProperties::declarationModel)?.takeIf { it.nominal }
    if (nominal != null) {
      val rawType = nominalTypes.scalar(nominal).type.swiftTypeName()
      return CodeBlock.of("%T(rawValue: %L)!", typeName.makeNonOptional(), swiftValueCode(rawType, null))
    }
    return when (this) {
      is String -> {
        val enumModel =
          typeRef
            ?.let(modelProperties::declarationModel)
            ?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
        if (enumModel != null) {
          val caseName = enumModel.requireSwiftEnumCaseNameForValue(this)
          if (enumModel.unknownValue == this) {
            CodeBlock.of("%T.%N(%S)", typeName, caseName, this)
          } else {
            CodeBlock.of("%T.%N", typeName, caseName)
          }
        } else if (typeName == URL) {
          CodeBlock.of("%T(string: %S)!", URL, this)
        } else {
          CodeBlock.of("%S", this)
        }
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
  }

  private fun GeneratedModel.swiftEnumCaseNameForValue(value: String): String? =
    swiftEnumEntries().singleOrNull { entry -> entry.value == value }?.name

  private fun GeneratedModel.requireSwiftEnumCaseNameForValue(value: String): String =
    swiftEnumCaseNameForValue(value)
      ?: genError(
        "Swift enum '$name' value '$value' does not match any enum value. " +
          "Fix the value or the enum definition.",
      )

  private fun GeneratedModel.swiftEnumEntries(): List<SwiftEnumEntry> =
    swiftEnumEntriesByModel.getOrPut(this) {
      createSwiftEnumEntries()
    }

  private fun GeneratedModel.createSwiftEnumEntries(): List<SwiftEnumEntry> {
    if (enumValueNames.isNotEmpty() && enumValueNames.size != values.size) {
      genError(
        "Swift enum '$name' has ${enumValueNames.size} enum value names for ${values.size} enum values. " +
          "Fix x-enum-varnames so it has one entry per enum value.",
      )
    }

    val entries =
      values.mapIndexed { index, value ->
        val caseName =
          if (enumValueNames.isNotEmpty()) {
            enumValueNames[index].trim()
          } else {
            value.swiftEnumCaseName
          }
        validateSwiftEnumCaseName(
          caseName,
          value,
          enumValueNames.getOrNull(index),
        )
        SwiftEnumEntry(caseName, value)
      }

    entries
      .groupBy { entry -> entry.name }
      .filterValues { duplicates -> duplicates.size > 1 }
      .forEach { (caseName, duplicates) ->
        genError(
          "Swift enum '$name' case name '$caseName' is used for multiple values " +
            duplicates.joinToString(", ") { entry -> "'${entry.value}'" } +
            ". Add x-enum-varnames to disambiguate them.",
        )
      }

    return entries
  }

  private fun GeneratedModel.validateSwiftEnumCaseName(
    caseName: String,
    value: String,
    explicitName: String?,
  ) {
    if (!swiftEnumCaseIdentifierRegex.matches(caseName)) {
      if (explicitName != null) {
        genError(
          "Swift enum '$name' x-enum-varnames entry '$explicitName' for value '$value' " +
            "maps to invalid case name '$caseName'. Fix x-enum-varnames with a valid Swift case name.",
        )
      }
      genError(
        "Swift enum '$name' value '$value' maps to invalid case name '$caseName'. " +
          "Add x-enum-varnames with a valid Swift case name.",
      )
    }
  }

  private data class SwiftEnumEntry(
    val name: String,
    val value: String,
  )

  companion object {

    private val ANY_PATCH_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.AnyPatchOp")
    private val UPDATE_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.UpdateOp")
    private val PATCH_OP = DeclaredTypeName.typeName("$SUNDAY_MODULE.PatchOp")
    private val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
    private val baseProblemProperties = setOf("type", "title", "status", "detail", "instance")
    private val optionalBaseProblemProperties = setOf("detail", "instance")
    private val swiftEnumCaseIdentifierRegex = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val requestModelUsages =
      setOf(
        GeneratedModelScope.Usage.PARAMETER,
        GeneratedModelScope.Usage.QUERY_STRING,
        GeneratedModelScope.Usage.REQUEST_BODY,
        GeneratedModelScope.Usage.SECURITY_QUERY_STRING,
      )
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
