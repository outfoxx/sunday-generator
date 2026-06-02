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
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NameAllocator
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.joinToCode
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.common.HttpStatus
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
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
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTargetImplementationParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.enabledFor
import io.outfoxx.sunday.generator.ir.emit.explicitAcceptTypes
import io.outfoxx.sunday.generator.ir.emit.explicitContentTypes
import io.outfoxx.sunday.generator.ir.emit.flattenedUnionTypes
import io.outfoxx.sunday.generator.ir.emit.isNoContent
import io.outfoxx.sunday.generator.ir.emit.modelOrNull
import io.outfoxx.sunday.generator.ir.emit.operationParameterViews
import io.outfoxx.sunday.generator.ir.emit.orderedDefaultMediaTypes
import io.outfoxx.sunday.generator.ir.emit.primarySuccessResponse
import io.outfoxx.sunday.generator.ir.emit.problemOrNull
import io.outfoxx.sunday.generator.ir.emit.referencedProblems
import io.outfoxx.sunday.generator.ir.emit.resolvedTypeUri
import io.outfoxx.sunday.generator.ir.emit.target
import io.outfoxx.sunday.generator.ir.emit.withLocation
import io.outfoxx.sunday.generator.kotlin.utils.FLOW
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_DESERIALIZATION_CONTEXT
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_CREATOR
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZER
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_DESERIALIZER_NONE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_IGNORE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE_INCLUDE
import io.outfoxx.sunday.generator.kotlin.utils.JACKSON_JSON_INCLUDE_NON_EMPTY
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
import io.outfoxx.sunday.generator.kotlin.utils.KotlinEnumEntriesResolver
import io.outfoxx.sunday.generator.kotlin.utils.KotlinProblemLibrary
import io.outfoxx.sunday.generator.kotlin.utils.MEDIA_TYPE
import io.outfoxx.sunday.generator.kotlin.utils.PATCH
import io.outfoxx.sunday.generator.kotlin.utils.PATCH_OP
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_EVENT_SOURCE
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_HTTP_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_METHOD
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_NULLABLE_OPERATION
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_NULLIFY_SPEC
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_OPERATION
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_OPERATION_SPEC
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_REQUEST
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_RESPONSE
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_STREAMING_BODY
import io.outfoxx.sunday.generator.kotlin.utils.SUNDAY_STREAMING_OPERATION
import io.outfoxx.sunday.generator.kotlin.utils.TRANSPORT
import io.outfoxx.sunday.generator.kotlin.utils.UPDATE_OP
import io.outfoxx.sunday.generator.kotlin.utils.URI_TEMPLATE
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_ABSTRACT_THROWABLE_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_EXCEPTIONAL
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_STATUS
import io.outfoxx.sunday.generator.kotlin.utils.ZALANDO_THROWABLE_PROBLEM
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIdentifierName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinIntegerScalarTypeName
import io.outfoxx.sunday.generator.kotlin.utils.kotlinTypeName
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Kotlin/Sunday service generator that reads the durable Sunday IR contract.
 */
class KotlinSundayIrGenerator(
  private val api: GeneratedApi,
  private val typeRegistry: KotlinTypeOutputRegistry,
  private val options: KotlinSundayOptions,
) {

  private val requestTypeVariable = TypeVariableName("Req", SUNDAY_REQUEST)
  private val transportType = TRANSPORT.parameterizedBy(requestTypeVariable)
  private val kotlinEnumEntries = KotlinEnumEntriesResolver()

  init {
    require(typeRegistry.problemLibrary != KotlinProblemLibrary.QUARKUS) {
      "Kotlin Sunday generator does not support Quarkus HttpProblem. Use -problem-library sunday or zalando."
    }
    require(typeRegistry.generationMode == GenerationMode.Client) {
      "Kotlin/Sunday requires only supports 'Client' generation mode"
    }
  }

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(options.defaultMediaTypes)
  private val apiIndex = GeneratedApiIndex(api)
  private val requestBodyModels by lazy {
    api
      .services
      .flatMap { service -> service.operations }
      .flatMap { operation ->
        listOfNotNull(operation.requestBody?.type) +
          operation
            .requestBody
            ?.payloads
            .orEmpty()
            .map { payload -> payload.type }
      }.mapNotNull { type -> type.modelOrNull(apiIndex) }
      .toSet()
  }

  /**
   * Generates Kotlin/Sunday service types from IR and registers them in the type registry.
   */
  fun generateServiceTypes() {
    val services = api.kotlinSundayServices()

    generateModelTypes(services)
    generateProblemTypes(services)

    val serviceTypes =
      services.map { service ->
        val servicePackageName = servicePackageName()
        val serviceTypeName = ClassName.bestGuess("$servicePackageName.${service.typeSimpleName()}")
        val serviceTypeBuilder = generateServiceType(serviceTypeName, service)

        typeRegistry.addServiceType(serviceTypeName, serviceTypeBuilder)
        GeneratedKotlinService(service, serviceTypeName)
      }

    if (options.aggregateServices && serviceTypes.size > 1) {
      val aggregateTypeName = aggregateServiceTypeName()
      if (serviceTypes.any { serviceType -> serviceType.typeName == aggregateTypeName }) {
        genError(
          "Cannot generate Kotlin/Sunday aggregate service '$aggregateTypeName' because it matches a generated service",
        )
      }
      typeRegistry.addServiceType(aggregateTypeName, generateAggregateServiceType(aggregateTypeName, serviceTypes))
    }
  }

  private fun GeneratedApi.kotlinSundayServices(): List<GeneratedService> =
    services
      .mapNotNull { service ->
        service
          .copy(operations = service.operations.filter { operation -> operation.isKotlinSundayOperation() })
          .withKotlinSundayBaseUri()
          .takeIf { filtered -> filtered.operations.isNotEmpty() }
      }

  private fun GeneratedOperation.isKotlinSundayOperation(): Boolean =
    method !in asyncApiOperationMethods ||
      (path.startsWith("/") && !hasNonHttpProtocolBinding())

  private fun GeneratedService.withKotlinSundayBaseUri(): GeneratedService =
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

  private fun generateModelTypes(services: List<GeneratedService>) {
    modelsForGeneration(services)
      .mapNotNull { model -> model.modelType()?.let { model.kotlinClassName() to it } }
      .forEach { (className, typeBuilder) -> typeRegistry.addModelType(className, typeBuilder) }
  }

  private fun modelsForGeneration(services: List<GeneratedService>): List<GeneratedModel> =
    api.models.filter { model -> model.scope == null } +
      services.flatMap { service -> apiIndex.referencedScopedModels(service) }

  private fun generateProblemTypes(services: List<GeneratedService>) {
    val referencedProblems =
      services
        .flatMap { service -> service.referencedProblems(apiIndex) }
        .distinctBy { problem -> problem.typeUri }

    referencedProblems.forEach { problem ->
      typeRegistry.addModelType(problem.typeName(), problem.problemType())
    }
  }

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

  private fun servicePackageName(): String =
    api.target("kotlinClient", "kotlin")?.packageName
      ?: options.defaultServicePackageName
      ?: genError("No service package specified, one must be specified via options or in source IR")

  private fun aggregateServiceTypeName(): ClassName {
    val servicePackageName = servicePackageName()
    val serviceName = (options.aggregateServiceName ?: options.serviceSuffix).ifBlank { "API" }
    return ClassName.bestGuess("$servicePackageName.$serviceName")
  }

  private fun generateAggregateServiceType(
    aggregateTypeName: ClassName,
    services: List<GeneratedKotlinService>,
  ): TypeSpec.Builder {
    val mediaSelection = services.aggregateMediaSelection()
    val names = NameAllocator()
    val serviceProperties =
      services.map { service ->
        val proposedName = service.service.aggregatePropertyName()
        AggregateServiceProperty(service.typeName, names.newName(proposedName, service))
      }

    val constructorBuilder =
      FunSpec
        .constructorBuilder()
        .addParameter("transport", transportType)
        .addParameter(
          ParameterSpec
            .builder("defaultContentTypes", LIST.parameterizedBy(MEDIA_TYPE))
            .defaultValue("%L", mediaTypesArray(mediaSelection.contentTypes))
            .build(),
        ).addParameter(
          ParameterSpec
            .builder("defaultAcceptTypes", LIST.parameterizedBy(MEDIA_TYPE))
            .defaultValue("%L", mediaTypesArray(mediaSelection.acceptTypes))
            .build(),
        )

    val initBlock =
      CodeBlock
        .builder()
        .addStatement("this.transport = transport")
        .addStatement("this.defaultContentTypes = defaultContentTypes")
        .addStatement("this.defaultAcceptTypes = defaultAcceptTypes")

    serviceProperties.forEach { serviceProperty ->
      initBlock.addStatement(
        "this.%N = %T(transport = transport, defaultContentTypes = defaultContentTypes, " +
          "defaultAcceptTypes = defaultAcceptTypes)",
        serviceProperty.name,
        serviceProperty.typeName,
      )
    }

    return TypeSpec
      .classBuilder(aggregateTypeName)
      .addTypeVariable(requestTypeVariable)
      .primaryConstructor(constructorBuilder.build())
      .addProperty(PropertySpec.builder("transport", transportType, KModifier.PUBLIC).build())
      .addProperty(
        PropertySpec
          .builder(
            "defaultContentTypes",
            LIST.parameterizedBy(MEDIA_TYPE),
            KModifier.PUBLIC,
          ).build(),
      ).addProperty(
        PropertySpec
          .builder(
            "defaultAcceptTypes",
            LIST.parameterizedBy(MEDIA_TYPE),
            KModifier.PUBLIC,
          ).build(),
      ).apply {
        serviceProperties.forEach { serviceProperty ->
          addProperty(
            PropertySpec
              .builder(
                serviceProperty.name,
                serviceProperty.typeName.parameterizedBy(requestTypeVariable),
                KModifier.PUBLIC,
              ).build(),
          )
        }
      }.addInitializerBlock(initBlock.build())
  }

  private fun GeneratedService.aggregatePropertyName(): String =
    typeSimpleName()
      .removeSuffix(options.serviceSuffix)
      .ifBlank { name.removeSuffix("Service") }
      .toLowerCamelCase()
      .kotlinIdentifierName

  private fun List<GeneratedKotlinService>.aggregateMediaSelection(): GeneratedMediaSelection {
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

  private data class GeneratedKotlinService(
    val service: GeneratedService,
    val typeName: ClassName,
  )

  private data class AggregateServiceProperty(
    val typeName: ClassName,
    val name: String,
  )

  private data class UnionPropertyBranch(
    val model: GeneratedModel,
    val uniqueRequiredWireNames: List<String>,
  )

  private fun generateServiceType(
    serviceTypeName: ClassName,
    service: GeneratedService,
  ): TypeSpec.Builder {
    val mediaSelection = service.defaultMediaSelection(defaultMediaTypes)
    val serviceTypeBuilder = TypeSpec.classBuilder(serviceTypeName)
    val constructorBuilder =
      FunSpec
        .constructorBuilder()
        .addParameter("transport", transportType)

    constructorBuilder
      .addParameter(
        ParameterSpec
          .builder("defaultContentTypes", LIST.parameterizedBy(MEDIA_TYPE))
          .defaultValue("%L", mediaTypesArray(mediaSelection.contentTypes))
          .build(),
      ).addParameter(
        ParameterSpec
          .builder("defaultAcceptTypes", LIST.parameterizedBy(MEDIA_TYPE))
          .defaultValue("%L", mediaTypesArray(mediaSelection.acceptTypes))
          .build(),
      )

    val referencedProblems = service.referencedProblems(apiIndex)
    referencedProblems.forEach { problem ->
      constructorBuilder.addStatement(
        "transport.registerProblem(%S, %T::class)",
        problem.resolvedTypeUri(options.defaultProblemBaseUri),
        problem.typeName(),
      )
    }

    serviceTypeBuilder
      .addTypeVariable(requestTypeVariable)
      .primaryConstructor(
        constructorBuilder.build(),
      ).addProperty(
        PropertySpec
          .builder("transport", transportType, KModifier.PUBLIC)
          .initializer("transport")
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultContentTypes", LIST.parameterizedBy(MEDIA_TYPE), KModifier.PUBLIC)
          .initializer("defaultContentTypes")
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", LIST.parameterizedBy(MEDIA_TYPE), KModifier.PUBLIC)
          .initializer("defaultAcceptTypes")
          .build(),
      )

    service.baseURLCompanionType()?.let(serviceTypeBuilder::addType)

    service.operations
      .forEach { operation ->
        val operationParameters = operation.operationParameters()
        serviceTypeBuilder.addFunction(generateOperation(operation, operationParameters))
      }

    return serviceTypeBuilder
  }

  private fun GeneratedModel.modelType(): TypeSpec.Builder? =
    when (kind) {
      GeneratedModel.Kind.ENUM ->
        enumTypeSpec()

      GeneratedModel.Kind.OBJECT -> objectTypeSpec()

      GeneratedModel.Kind.UNION -> objectUnionTypeSpecOrNull()

      else -> null
    }

  private fun GeneratedModel.enumTypeSpec(): TypeSpec.Builder {
    val entries = kotlinEnumEntries.entries(this)
    val customWireValues = entries.any { entry -> entry.name != entry.value }
    val jacksonAnnotations = typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)

    return TypeSpec
      .enumBuilder(kotlinClassName())
      .addModifiers(KModifier.PUBLIC)
      .apply {
        if (customWireValues) {
          primaryConstructor(
            FunSpec
              .constructorBuilder()
              .addParameter("wireValue", STRING)
              .build(),
          )
          addProperty(
            PropertySpec
              .builder("wireValue", STRING, KModifier.PRIVATE)
              .initializer("wireValue")
              .build(),
          )
          val toStringFunction =
            FunSpec
              .builder("toString")
              .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
              .returns(STRING)
              .addStatement("return wireValue")
          if (jacksonAnnotations) {
            toStringFunction.addAnnotation(JACKSON_JSON_VALUE)
          }
          addFunction(toStringFunction.build())
          if (jacksonAnnotations) {
            addType(jsonCreatorCompanionType())
          }
        }

        entries.forEach { entry ->
          if (customWireValues) {
            addEnumConstant(
              entry.name,
              TypeSpec
                .anonymousClassBuilder()
                .addSuperclassConstructorParameter("%S", entry.value)
                .build(),
            )
          } else {
            addEnumConstant(entry.name)
          }
        }
      }
  }

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

  private fun GeneratedModel.objectTypeSpec(): TypeSpec.Builder {
    val inheritedProperties = inheritedModelProperties()
    val localProperties = localModelProperties(inheritedProperties)

    if (isProblemModel()) {
      return classTypeSpec(inheritedProperties, localProperties)
    }
    if (shouldGenerateDataClassModel && localProperties.isNotEmpty()) {
      return dataClassTypeSpec(localProperties)
    }
    if (shouldGenerateClassModel) {
      return classTypeSpec(inheritedProperties, localProperties)
    }

    return TypeSpec
      .interfaceBuilder(kotlinClassName())
      .addModifiers(KModifier.PUBLIC)
      .apply {
        addJacksonPolymorphism(this@objectTypeSpec)
        if (patchable) {
          addSuperinterface(PATCH)
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_INCLUDE)
              .addMember("%T.%L", JACKSON_JSON_INCLUDE_INCLUDE, JACKSON_JSON_INCLUDE_NON_EMPTY)
              .build(),
          )
        }
        inherits.forEach { inherited ->
          addSuperinterface(inherited.kotlinTypeName())
        }
        directUnionSupertypes().forEach { union ->
          addSuperinterface(union.kotlinClassName())
        }
        localProperties.forEach { property ->
          addProperty(propertySpec(property))
        }
      }
  }

  private val GeneratedModel.shouldGenerateClassModel: Boolean
    get() = typeRegistry.options.contains(KotlinTypeRegistry.Option.ImplementModel)

  private val GeneratedModel.shouldGenerateDataClassModel: Boolean
    get() =
      typeRegistry.options.contains(KotlinTypeRegistry.Option.ImplementModel) &&
        this in requestBodyModels &&
        inherits.isEmpty() &&
        !hasInheritors &&
        !patchable

  private fun GeneratedModel.classTypeSpec(
    inheritedProperties: List<GeneratedModelProperty>,
    localProperties: List<GeneratedModelProperty>,
  ): TypeSpec.Builder {
    val constructor =
      FunSpec
        .constructorBuilder()
        .apply {
          inheritedProperties.forEach { property ->
            addParameter(property.constructorParameterSpec())
          }
          localProperties.forEach { property ->
            addParameter(property.constructorParameterSpec(patchable))
          }
        }.build()
    val isProblemRootModel = isProblemRootModel()

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
        if (isProblemRootModel) {
          configureSourceProblemRootModel(inheritedProperties + localProperties)
        } else {
          inherits.firstOrNull()?.let { inherited ->
            superclass(inherited.kotlinTypeName())
            inheritedProperties.forEach { property ->
              addSuperclassConstructorParameter("%N", property.name.kotlinIdentifierName)
            }
          }
        }
        if (patchable) {
          addSuperinterface(PATCH)
          addAnnotation(
            AnnotationSpec
              .builder(JACKSON_JSON_INCLUDE)
              .addMember("%T.%L", JACKSON_JSON_INCLUDE_INCLUDE, JACKSON_JSON_INCLUDE_NON_EMPTY)
              .build(),
          )
        }
        directUnionSupertypes().forEach { union ->
          addSuperinterface(union.kotlinClassName())
        }
        localProperties
          .filterNot { property ->
            isProblemRootModel && property.isBaseProblemProperty()
          }.forEach { property ->
            addProperty(
              PropertySpec
                .builder(property.name.kotlinIdentifierName, property.modelPropertyTypeName(patchable))
                .apply {
                  addAnnotations(property.jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.GET))
                  addAnnotations(
                    property.validation.validationAnnotations(
                      property.type,
                      AnnotationSpec.UseSiteTarget.FIELD,
                    ),
                  )
                  if (patchable) {
                    mutable(true)
                  }
                  initializer(property.name.kotlinIdentifierName)
                }.build(),
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

  private fun TypeSpec.Builder.configureSourceProblemRootModel(properties: List<GeneratedModelProperty>) {
    when (typeRegistry.problemLibrary) {
      KotlinProblemLibrary.SUNDAY -> configureSundaySourceProblemRootModel(properties)
      KotlinProblemLibrary.ZALANDO -> configureZalandoSourceProblemRootModel(properties)
      KotlinProblemLibrary.QUARKUS ->
        genError("Kotlin/Sunday does not support Quarkus HttpProblem. Use -problem-library sunday or zalando.")
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

  private fun GeneratedModel.dataClassTypeSpec(properties: List<GeneratedModelProperty>): TypeSpec.Builder {
    val constructor =
      FunSpec
        .constructorBuilder()
        .apply {
          properties.forEach { property ->
            addParameter(property.constructorParameterSpec())
          }
        }.build()

    return TypeSpec
      .classBuilder(kotlinClassName())
      .addModifiers(KModifier.PUBLIC, KModifier.DATA)
      .primaryConstructor(constructor)
      .apply {
        addJacksonPolymorphism(this@dataClassTypeSpec)
        addJacksonUnionMemberDeserializerOverride(this@dataClassTypeSpec)
        directUnionSupertypes().forEach { union ->
          addSuperinterface(union.kotlinClassName())
        }
        properties.forEach { property ->
          addProperty(
            PropertySpec
              .builder(property.name.kotlinIdentifierName, property.modelPropertyTypeName())
              .addAnnotations(property.jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.GET))
              .addAnnotations(
                property.validation.validationAnnotations(
                  property.type,
                  AnnotationSpec.UseSiteTarget.FIELD,
                ),
              ).initializer(property.name.kotlinIdentifierName)
              .build(),
          )
        }
      }
  }

  private fun GeneratedModelProperty.constructorParameterSpec(patchable: Boolean = false): ParameterSpec =
    ParameterSpec
      .builder(name.kotlinIdentifierName, modelPropertyTypeName(patchable))
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
        if (typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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
        if (typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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
              addStatement(
                "return %L",
                case.model.unionCaseDecodeCode(unionTypeName, directCases),
              )
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
                addStatement(
                  "return %L",
                  branch.model.unionCaseDecodeCode(unionTypeName, directCases),
                )
                endControlFlow()
              }

            propertyBranches
              .filter { branch -> branch.uniqueRequiredWireNames.isEmpty() }
              .forEach { branch ->
                beginControlFlow("if (%L)", branch.model.requiredWireNames().presenceExpression("&&"))
                addStatement(
                  "return %L",
                  branch.model.unionCaseDecodeCode(unionTypeName, directCases),
                )
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

  private fun GeneratedModel.propertySpec(property: GeneratedModelProperty): PropertySpec {
    val propertyType = property.modelPropertyTypeName(patchable)
    val implementation = property.target("kotlinClient", "kotlin")?.implementation

    return PropertySpec
      .builder("`${property.name.kotlinIdentifierName}`", propertyType)
      .apply {
        addAnnotations(property.jacksonExternalDiscriminatorAnnotations(AnnotationSpec.UseSiteTarget.GET))
        addAnnotations(
          property.validation.validationAnnotations(
            property.type,
            AnnotationSpec.UseSiteTarget.GET,
          ),
        )
        if (patchable) {
          mutable(true)
        }
        if (implementation != null) {
          getter(
            FunSpec
              .getterBuilder()
              .addStatement(implementation.code, *implementation.parameters.toCodeParameters())
              .build(),
          )
        }
      }.build()
  }

  private fun GeneratedModelProperty.jacksonExternalDiscriminatorAnnotations(
    useSiteTarget: AnnotationSpec.UseSiteTarget,
  ): List<AnnotationSpec> {
    if (!typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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

  private fun TypeSpec.Builder.addJacksonPolymorphism(model: GeneratedModel) {
    if (!typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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
    if (!typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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

  private fun GeneratedModelProperty.modelPropertyTypeName(patchable: Boolean = false): TypeName =
    if (patchable) {
      val base = if (type.nullable) PATCH_OP else UPDATE_OP
      base.parameterizedBy(type.kotlinTypeName().copy(nullable = false))
    } else {
      type.kotlinTypeName().copy(nullable = type.nullable || !required)
    }

  private fun Map<String, String>.validationAnnotations(
    type: GeneratedTypeRef,
    useSiteTarget: AnnotationSpec.UseSiteTarget? = null,
  ): List<AnnotationSpec> {
    if (!typeRegistry.options.contains(KotlinTypeRegistry.Option.ValidationConstraints)) {
      return emptyList()
    }

    return buildList {
      sizeAnnotationBuilder()?.let { sizeBuilder ->
        add(sizeBuilder.withUseSiteTarget(useSiteTarget).build())
      }

      if (type.format.equals("email", ignoreCase = true)) {
        add(AnnotationSpec.builder(typeRegistry.beanValidationTypes.email).withUseSiteTarget(useSiteTarget).build())
      }

      this@validationAnnotations["pattern"]?.let { pattern ->
        add(
          AnnotationSpec
            .builder(typeRegistry.beanValidationTypes.pattern)
            .withUseSiteTarget(useSiteTarget)
            .addMember("regexp = %P", pattern)
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
      sizeBuilder = AnnotationSpec.builder(typeRegistry.beanValidationTypes.size).addMember("max = %L", maxLength)
    }
    this["minLength"]?.let { minLength ->
      sizeBuilder =
        (sizeBuilder ?: AnnotationSpec.builder(typeRegistry.beanValidationTypes.size)).addMember("min = %L", minLength)
    }
    this["maxItems"]?.let { maxItems ->
      sizeBuilder = AnnotationSpec.builder(typeRegistry.beanValidationTypes.size).addMember("max = %L", maxItems)
    }
    this["minItems"]?.let { minItems ->
      sizeBuilder =
        (sizeBuilder ?: AnnotationSpec.builder(typeRegistry.beanValidationTypes.size)).addMember("min = %L", minItems)
    }
    return sizeBuilder
  }

  private fun GeneratedProblem.problemType(): TypeSpec.Builder {
    val problemTypeName = typeName()
    val constructorBuilder =
      FunSpec
        .constructorBuilder()
        .apply {
          if (typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
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
          .builder(
            field.name.kotlinIdentifierName,
            field.problemTypeName(),
            KModifier.PUBLIC,
          ).initializer(field.name.kotlinIdentifierName)
          .build(),
      )
    }

    if (typeRegistry.options.contains(KotlinTypeRegistry.Option.JacksonAnnotations)) {
      typeBuilder.addAnnotation(
        AnnotationSpec
          .builder(JACKSON_JSON_TYPENAME)
          .addMember("%T.TYPE", problemTypeName)
          .build(),
      )
    }

    when (typeRegistry.problemLibrary) {
      KotlinProblemLibrary.SUNDAY ->
        configureSundayProblemType(typeBuilder, constructorBuilder)

      KotlinProblemLibrary.ZALANDO ->
        configureZalandoProblemType(typeBuilder, constructorBuilder)

      KotlinProblemLibrary.QUARKUS ->
        genError("Kotlin/Sunday does not support Quarkus HttpProblem. Use -problem-library sunday or zalando.")
    }

    return typeBuilder
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

  private fun GeneratedService.baseURLCompanionType(): TypeSpec? {
    val baseUri = baseUri ?: return null
    val companionTypeBuilder = TypeSpec.companionObjectBuilder()

    typeRegistry.addGeneratedTo(companionTypeBuilder, false)
    companionTypeBuilder.addFunction(
      FunSpec
        .builder("baseURL")
        .returns(URI_TEMPLATE)
        .apply {
          baseUriParameters.forEach { parameter ->
            addParameter(baseUriParameterSpec(parameter))
          }
        }.addCode("return %T(⇥\n", URI_TEMPLATE)
        .addCode("%S,\nmapOf(", baseUri)
        .apply {
          baseUriParameters.forEachIndexed { index, parameter ->
            addCode("%S to %N", parameter.serializationName ?: parameter.name, parameter.name.kotlinIdentifierName)
            if (index < baseUriParameters.size - 1) {
              addCode(", ")
            }
          }
        }.addCode(")⇤\n)\n")
        .build(),
    )

    return companionTypeBuilder.build()
  }

  private fun baseUriParameterSpec(parameter: GeneratedParameter): ParameterSpec {
    val typeName =
      parameter.type
        .kotlinTypeName()
        .copy(nullable = parameter.defaultValue == null && parameter.type.nullable)

    return ParameterSpec
      .builder(parameter.name.kotlinIdentifierName, typeName)
      .apply {
        parameter.defaultValue?.let { defaultValue ->
          defaultValue("%L", parameter.baseUriDefaultValueCode(defaultValue, typeName))
        }
      }.build()
  }

  private fun GeneratedParameter.baseUriDefaultValueCode(
    defaultValue: Any,
    typeName: TypeName,
  ): CodeBlock {
    val enumModel = type.modelOrNull(apiIndex)?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
    if (defaultValue is String && enumModel != null) {
      return CodeBlock.of(
        "%T.%L",
        typeName,
        kotlinEnumEntries.requireConstantNameForValue(enumModel, defaultValue, "default"),
      )
    }
    return valueCode(defaultValue)
  }

  private fun GeneratedOperation.operationParameters(): List<GeneratedOperationParameter> {
    val names = NameAllocator()
    return operationParameterViews(
      identifierName = { parameter -> parameter.name.kotlinIdentifierName },
      allocateName = { _, proposedName -> names.newName(proposedName) },
    )
  }

  private fun generateOperation(
    operation: GeneratedOperation,
    operationParameters: List<GeneratedOperationParameter>,
  ): FunSpec {
    val isDirectTransportCall = operation.streaming != null || operation.exchange != null
    val functionBuilder =
      FunSpec
        .builder(operation.id.kotlinIdentifierName)
        .returns(UNIT)

    if (isDirectTransportCall && operation.streaming?.kind != GeneratedStreaming.Kind.EVENT_STREAM) {
      functionBuilder.addModifiers(KModifier.SUSPEND)
    }

    operation.documentation?.description?.let { description ->
      functionBuilder.addKdoc("$description\n")
    }

    operationParameters
      .filterNot { parameter -> parameter.isConstant }
      .map { parameter -> parameterSpec(parameter) }
      .forEach(functionBuilder::addParameter)

    operation.requestBody?.let { requestBody ->
      functionBuilder.addParameter("body", requestBody.kotlinTypeName())
    }

    val response = operation.primarySuccessResponse()
    val responseTypeName = operation.returnTypeName(response)
    functionBuilder.returns(responseTypeName)

    functionBuilder.addCode(operation.transportCall(response, operationParameters))

    return functionBuilder.build()
  }

  private fun GeneratedOperation.transportCall(
    response: GeneratedResponse?,
    operationParameters: List<GeneratedOperationParameter>,
  ): CodeBlock {
    if (streaming == null && exchange == null) {
      return operationCall(response, operationParameters)
    }

    val factoryMethod =
      when {
        streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE -> "eventSource"
        streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM -> "eventStream"
        exchange == GeneratedExchange.REQUEST -> "transportRequest"
        exchange == GeneratedExchange.RESPONSE -> "transportResponse"
        else -> error("Operation exchange or streaming mode is required for direct transport calls")
      }

    val builder = CodeBlock.builder()
    val contentTypeParameter = operationParameters.contentTypeParameterOrNull()
    builder.add("return this.transport\n⇥.%L(⇥\n", factoryMethod)
    builder.add("method = %T.%L", SUNDAY_METHOD, sundayRequestMethod())
    builder.add(",\n")
    builder.add("pathTemplate = %S", path)

    operationParameters
      .withLocation(GeneratedParameter.Location.PATH)
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("pathParameters", parameters))
      }

    operationParameters
      .withLocation(GeneratedParameter.Location.QUERY)
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("queryParameters", parameters))
      }

    requestBody?.let { requestBody ->
      builder.add(",\n")
      builder.add("body = body")
      builder.add(",\n")
      builder.add("contentTypes = %L", contentTypesCode(requestBody, contentTypeParameter))
    }

    if (response?.takeUnless { it.isNoContent }?.type != null || streaming != null) {
      builder.add(",\n")
      if (streaming != null) {
        builder.add("acceptTypes = %L", mediaTypesArray(GeneratedMediaSelection.EVENT_STREAM))
      } else {
        builder.add("acceptTypes = %L", acceptTypesCode(response))
      }
    }

    if (streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM) {
      builder.add(",\n")
      builder.add("decoder = %L", eventStreamDecoder(response))
    }

    operationParameters
      .withLocation(GeneratedParameter.Location.HEADER)
      .filterNot { parameter -> parameter == contentTypeParameter }
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("headers", parameters))
      }

    builder.add("⇤\n)⇤\n⇤")

    return builder.build()
  }

  private fun GeneratedOperation.operationCall(
    response: GeneratedResponse?,
    operationParameters: List<GeneratedOperationParameter>,
  ): CodeBlock {
    val requestType = requestBody?.kotlinTypeName()?.copy(nullable = false) ?: UNIT
    val responseType = responseBodyTypeName(response)
    val contentTypeParameter = operationParameters.contentTypeParameterOrNull()
    val builder = CodeBlock.builder()
    val operationFunction = if (isNullifyingOperation) SUNDAY_NULLABLE_OPERATION_FUNCTION else SUNDAY_OPERATION_FUNCTION

    builder.add("return this.transport.%M<%T,·%T,·Req>(⇥\n", operationFunction, requestType, responseType)
    builder.add("%T(⇥\n", SUNDAY_OPERATION_SPEC)
    builder.add("method = %T.%L", SUNDAY_METHOD, sundayRequestMethod())
    builder.add(",\npathTemplate = %S", path)

    operationParameters
      .withLocation(GeneratedParameter.Location.PATH)
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("pathParameters", parameters))
      }

    operationParameters
      .withLocation(GeneratedParameter.Location.QUERY)
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("queryParameters", parameters))
      }

    requestBody?.let { requestBody ->
      builder.add(",\nbody = body")
      builder.add(",\ncontentTypes = %L", contentTypesCode(requestBody, contentTypeParameter))
    }

    if (response?.takeUnless { it.isNoContent }?.type != null) {
      builder.add(",\nacceptTypes = %L", acceptTypesCode(response))
    }

    operationParameters
      .withLocation(GeneratedParameter.Location.HEADER)
      .filterNot { parameter -> parameter == contentTypeParameter }
      .takeIf { parameters -> parameters.isNotEmpty() }
      ?.let { parameters ->
        builder.add(",\n")
        builder.add(parametersCode("headers", parameters))
      }

    builder.add("⇤\n)")
    nullify?.takeIf { isNullifyingOperation }?.let { nullify ->
      builder.add(",\n")
      builder.add(nullifySpecCode(nullify))
    }
    builder.add("⇤\n)\n")

    return builder.build()
  }

  private fun GeneratedOperation.nullifySpecCode(nullify: GeneratedNullify): CodeBlock {
    val problemTypeNames =
      nullify.problems
        .filter { problem -> problems.any { referenced -> referenced.name == problem.name } }
        .mapNotNull { problem -> problem.problemTypeNameOrNull() }
    val builder = CodeBlock.builder()

    builder.add("%T(⇥\n", SUNDAY_NULLIFY_SPEC)
    builder.add("statuses = listOf(%L)", nullify.statuses.joinToString(", "))
    builder.add(",\n")
    builder.add("problemTypes = listOf(")
    problemTypeNames.forEachIndexed { index, problemTypeName ->
      if (index > 0) {
        builder.add(", ")
      }
      builder.add("%T::class", problemTypeName)
    }
    builder.add(")")
    builder.add("⇤\n)")

    return builder.build()
  }

  private fun GeneratedOperation.sundayRequestMethod(): String =
    when (method.uppercase()) {
      "SUBSCRIBE" -> "Get"
      "PUBLISH" -> "Post"
      else -> method.lowercase().replaceFirstChar { it.titlecase() }
    }

  private fun parameterSpec(parameter: GeneratedOperationParameter): ParameterSpec =
    ParameterSpec
      .builder(parameter.name, parameter.typeName())
      .apply {
        when {
          parameter.defaultValue != null -> defaultValue("%L", valueCode(parameter.defaultValue))
          parameter.typeName().isNullable -> defaultValue("null")
        }
      }.build()

  private fun parametersCode(
    fieldName: String,
    parameters: List<GeneratedOperationParameter>,
  ): CodeBlock {
    val anyNullable = parameters.any { parameter -> parameter.shouldFilterNullValue }
    val builder = CodeBlock.builder().add("%L = mapOf(⇥\n", fieldName)

    parameters.forEachIndexed { index, parameter ->
      builder.add("%S to ", parameter.wireName)
      if (parameter.isConstant) {
        builder.add("%L", valueCode(parameter.constantValue))
      } else {
        builder.add("%N", parameter.name)
      }

      if (index < parameters.size - 1) {
        builder.add(",\n")
      }
    }

    builder.add("⇤\n)%L", if (anyNullable) ".filterValues { it != null }" else "")
    return builder.build()
  }

  private fun GeneratedOperation.returnTypeName(response: GeneratedResponse?): TypeName =
    when {
      streaming?.kind == GeneratedStreaming.Kind.EVENT_SOURCE -> SUNDAY_EVENT_SOURCE
      streaming?.kind == GeneratedStreaming.Kind.EVENT_STREAM -> {
        val eventType =
          if (streaming.eventMode == GeneratedStreaming.EventMode.DISCRIMINATED) {
            response?.type?.eventStreamCommonTypeName() ?: ANY
          } else {
            response?.type?.kotlinTypeName() ?: ANY
          }
        FLOW.parameterizedBy(eventType)
      }
      else ->
        when (exchange) {
          GeneratedExchange.REQUEST -> requestTypeVariable
          GeneratedExchange.RESPONSE -> SUNDAY_RESPONSE
          null ->
            when {
              isNullifyingOperation ->
                SUNDAY_NULLABLE_OPERATION.parameterizedBy(
                  requestBody?.kotlinTypeName()?.copy(nullable = false) ?: UNIT,
                  responseBodyTypeName(response),
                  requestTypeVariable,
                )
              requestBody.isKotlinStreamingRequestBody ->
                SUNDAY_STREAMING_OPERATION.parameterizedBy(
                  responseBodyTypeName(response),
                  requestTypeVariable,
                )
              else ->
                SUNDAY_OPERATION.parameterizedBy(
                  requestBody?.kotlinTypeName()?.copy(nullable = false) ?: UNIT,
                  responseBodyTypeName(response),
                  requestTypeVariable,
                )
            }
        }
    }

  private val GeneratedOperation.isNullifyingOperation: Boolean
    get() = nullify != null && exchange == null && streaming == null

  private fun responseBodyTypeName(response: GeneratedResponse?): TypeName =
    response
      ?.takeUnless { it.isNoContent }
      ?.type
      ?.kotlinTypeName()
      ?.copy(nullable = false)
      ?: UNIT

  private fun GeneratedTypeRef.problemTypeNameOrNull(): TypeName? = problemOrNull(apiIndex)?.typeName()

  private fun GeneratedOperation.eventStreamDecoder(response: GeneratedResponse?): CodeBlock =
    when (streaming?.eventMode) {
      GeneratedStreaming.EventMode.SIMPLE ->
        simpleEventStreamDecoder(response?.type?.kotlinTypeName() ?: ANY)
      GeneratedStreaming.EventMode.DISCRIMINATED ->
        discriminatedEventStreamDecoder(response?.type)
      null ->
        simpleEventStreamDecoder(response?.type?.kotlinTypeName() ?: ANY)
    }

  private fun simpleEventStreamDecoder(eventType: TypeName): CodeBlock =
    CodeBlock.of(
      "{ decoder, _, _, data, _ -> decoder.decode<%T>(data, %M<%T>()) }",
      eventType,
      TYPE_OF,
      eventType,
    )

  private fun discriminatedEventStreamDecoder(type: GeneratedTypeRef?): CodeBlock {
    val eventTypes =
      type
        ?.flattenedUnionTypes()
        .orEmpty()
        .filter { eventType ->
          eventType.kind == GeneratedTypeRef.Kind.NAMED
        }
    val builder = CodeBlock.builder()

    builder.add("{ decoder, event, _, data, logger ->\n")
    builder.indent()
    builder.add("when (event) {\n")
    builder.indent()

    eventTypes.forEach { eventType ->
      val eventTypeName = eventType.kotlinTypeName()
      builder.addStatement(
        "%S -> decoder.decode<%T>(data, %M<%T>())",
        eventType.discriminatorValue(),
        eventTypeName,
        TYPE_OF,
        eventTypeName,
      )
    }

    builder
      .add("else -> {\n")
      .indent()
      .addStatement("logger.error(\"Unknown event type, ignoring event: event=\$event\")")
      .addStatement("null")
      .unindent()
      .add("}\n")
      .unindent()
      .add("}\n")
      .unindent()
      .add("}")

    return builder.build()
  }

  private fun GeneratedTypeRef.eventStreamCommonTypeName(): TypeName {
    val eventTypes = flattenedUnionTypes().filter { eventType -> eventType.kind == GeneratedTypeRef.Kind.NAMED }
    val commonBase =
      eventTypes
        .mapNotNull { eventType -> eventType.modelOrNull(apiIndex)?.inherits?.firstOrNull() }
        .takeIf { inheritedTypes -> inheritedTypes.size == eventTypes.size }
        ?.distinctBy { inheritedType -> inheritedType.name to inheritedType.scope }
        ?.singleOrNull()

    return commonBase?.kotlinTypeName() ?: ANY
  }

  private fun GeneratedTypeRef.discriminatorValue(): String {
    val typeName = kotlinTypeName()
    return modelOrNull(apiIndex)?.discriminatorValue ?: (typeName as? ClassName)?.simpleName ?: typeName.toString()
  }

  private fun GeneratedProblem.typeName(): ClassName {
    val packageName =
      api.target("kotlinClient", "kotlin")?.modelPackageName
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in source IR")

    return ClassName(packageName, name.toUpperCamelCase())
  }

  private fun GeneratedOperationParameter.typeName(): TypeName = type.kotlinTypeName().copy(nullable = isNullable)

  private fun contentTypesCode(
    requestBody: GeneratedPayload,
    contentTypeParameter: GeneratedOperationParameter?,
  ): CodeBlock =
    contentTypeParameter?.selectedContentTypeCode()
      ?: requestBody
        .explicitContentTypes(defaultMediaTypes)
        ?.let(::mediaTypesArray)
      ?: CodeBlock.of("this.defaultContentTypes")

  private fun Iterable<GeneratedOperationParameter>.contentTypeParameterOrNull(): GeneratedOperationParameter? =
    firstOrNull { parameter ->
      parameter.location == GeneratedParameter.Location.HEADER &&
        parameter.wireName.equals("Content-Type", ignoreCase = true)
    }

  private fun GeneratedOperationParameter.selectedContentTypeCode(): CodeBlock? {
    val enumModel =
      type
        .modelOrNull(apiIndex)
        ?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
        ?: return null
    val entries = kotlinEnumEntries.entries(enumModel)
    if (entries.isEmpty()) {
      return null
    }

    return CodeBlock
      .builder()
      .add("listOf(when (%N) {\n", name)
      .indent()
      .apply {
        entries.forEach { entry ->
          addStatement("%T.%L -> %L", type.kotlinTypeName(), entry.name, mediaType(entry.value))
        }
      }.unindent()
      .add("})")
      .build()
  }

  private fun acceptTypesCode(response: GeneratedResponse?): CodeBlock =
    response
      .explicitAcceptTypes(defaultMediaTypes)
      ?.let(::mediaTypesArray)
      ?: CodeBlock.of("this.defaultAcceptTypes")

  private fun GeneratedPayload.kotlinTypeName(): TypeName =
    if (isKotlinStreamingRequestBody) {
      SUNDAY_STREAMING_BODY
    } else if (mediaTypes.firstOrNull() == "application/octet-stream") {
      BYTE_ARRAY
    } else {
      type.kotlinTypeName()
    }

  private val GeneratedPayload?.isKotlinStreamingRequestBody: Boolean
    get() = this?.streaming?.enabledFor(GenerationMode.Client) == true

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

  private fun valueCode(value: Any?): CodeBlock =
    when (value) {
      null -> CodeBlock.of("null")
      is String -> CodeBlock.of("%S", value)
      is Boolean -> CodeBlock.of("%L", value)
      is Number -> CodeBlock.of("%L", value)
      is List<*> ->
        value
          .map(::valueCode)
          .joinToCode(prefix = "listOf(", separator = ", ", suffix = ")")
      is Map<*, *> -> {
        val entries =
          value.entries.map { (key, entryValue) ->
            CodeBlock.of("%S to %L", key.toString(), valueCode(entryValue))
          }
        entries.joinToCode(prefix = "mapOf(", separator = ", ", suffix = ")")
      }
      else -> CodeBlock.of("%S", value.toString())
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
      api.target("kotlinClient", "kotlin")?.modelPackageName
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
    val nested = nested
    if (nested != null) {
      val enclosingType =
        nested.enclosedIn?.modelOrNull(apiIndex)?.kotlinClassName()
          ?: genError("Nested model '$name' references unknown enclosing type '${nested.enclosedIn?.name}'")
      val nestedName = nested.name ?: genError("Nested model '$name' is missing a nested type name")
      return enclosingType.nestedClass(nestedName)
    }

    val scope = scope
    if (scope?.service != null) {
      val service =
        api.services.firstOrNull { service -> service.name == scope.service }
          ?: genError("No service found for scoped model $name")
      val servicePackageName =
        api.target("kotlinClient", "kotlin")?.packageName
          ?: options.defaultServicePackageName
          ?: genError("No service package specified, one must be specified via options or in source IR")

      return ClassName(servicePackageName, service.typeSimpleName(), scopedSimpleName())
    }

    val target = target("kotlinClient", "kotlin")
    val explicitTypeName = target?.typeName
    if (explicitTypeName != null && "." in explicitTypeName) {
      return ClassName.bestGuess(explicitTypeName)
    }

    val packageName =
      target?.modelPackageName
        ?: api.target("kotlinClient", "kotlin")?.modelPackageName
        ?: typeRegistry.defaultModelPackageName
        ?: genError("No model package specified, one must be specified via options or in source IR")
    val simpleName = explicitTypeName ?: name.toUpperCamelCase()
    return ClassName(packageName, simpleName)
  }

  private fun GeneratedModel.scopedSimpleName(): String {
    val baseName = name.toUpperCamelCase()
    val responseStatus = scope?.status
    return if (requiresResponseStatusInScopedName() && responseStatus != null) {
      baseName.withResponseStatus(responseStatus)
    } else {
      baseName
    }
  }

  private fun GeneratedModel.requiresResponseStatusInScopedName(): Boolean {
    val modelScope = scope ?: return false
    return modelScope.usage == GeneratedModelScope.Usage.RESPONSE_BODY &&
      modelScope.status != null &&
      api.models.count { candidate ->
        val candidateScope = candidate.scope
        candidate.name == name &&
          candidateScope != null &&
          candidateScope.service == modelScope.service &&
          candidateScope.operation == modelScope.operation &&
          candidateScope.usage == GeneratedModelScope.Usage.RESPONSE_BODY
      } > 1
  }

  private fun String.withResponseStatus(status: Int): String =
    if (endsWith("ResponseBody")) {
      removeSuffix("ResponseBody") + status + "ResponseBody"
    } else {
      this + status
    }

  private fun List<GeneratedTargetImplementationParameter>.toCodeParameters(): Array<Any> =
    map { parameter ->
      when (parameter.type) {
        "Type" -> ClassName.bestGuess(parameter.value)
        else -> parameter.value
      }
    }.toTypedArray()

  private val GeneratedModel.isFreeformObject: Boolean
    get() =
      kind == GeneratedModel.Kind.OBJECT &&
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

  private val GeneratedModel.hasInheritors: Boolean
    get() =
      api.models.any { model ->
        model.inherits.any { inherited -> inherited.modelOrNull(apiIndex) == this }
      }

  private fun GeneratedModel.unionCaseTypeName(unionTypeName: ClassName): ClassName =
    unionTypeName.nestedClass("${name.toUpperCamelCase()}Value")

  private fun GeneratedModel.allModelProperties(): List<GeneratedModelProperty> =
    inherits.flatMap { inherited -> inherited.modelOrNull(apiIndex)?.allModelProperties().orEmpty() } +
      properties

  private fun GeneratedModel.inheritedModelProperties(): List<GeneratedModelProperty> =
    inherits.flatMap { inherited -> inherited.modelOrNull(apiIndex)?.allModelProperties().orEmpty() }

  private fun GeneratedModel.localModelProperties(
    inheritedProperties: List<GeneratedModelProperty>,
  ): List<GeneratedModelProperty> {
    val inheritedWireNames = inheritedProperties.map { property -> property.wireName }.toSet()
    return properties.filterNot { property -> property.wireName in inheritedWireNames }
  }

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

  private val GeneratedModelProperty.wireName: String
    get() = serializationName ?: name

  private fun List<String>.presenceExpression(operator: String): CodeBlock =
    if (isEmpty()) {
      CodeBlock.of("true")
    } else {
      map { wireName -> CodeBlock.of("tree.has(%S)", wireName) }.joinToCode(" $operator ")
    }

  private val GeneratedModel.isAliasLike: Boolean
    get() =
      kind == GeneratedModel.Kind.SCALAR_ALIAS ||
        kind == GeneratedModel.Kind.ARRAY ||
        kind == GeneratedModel.Kind.MAP ||
        (kind == GeneratedModel.Kind.UNION && !isObjectUnionSealedInterface)

  private fun mediaTypesArray(mimeTypes: List<String>): CodeBlock = mediaTypesArray(*mimeTypes.toTypedArray())

  private fun mediaTypesArray(vararg mimeTypes: String): CodeBlock =
    mimeTypes
      .distinct()
      .map(::mediaType)
      .joinToCode(prefix = "listOf(", separator = ", ", suffix = ")")

  private fun mediaType(value: String): CodeBlock =
    when (value) {
      "text/plain" -> CodeBlock.of("%T.Plain", MEDIA_TYPE)
      "text/html" -> CodeBlock.of("%T.HTML", MEDIA_TYPE)
      "application/json" -> CodeBlock.of("%T.JSON", MEDIA_TYPE)
      "application/yaml" -> CodeBlock.of("%T.YAML", MEDIA_TYPE)
      "application/cbor" -> CodeBlock.of("%T.CBOR", MEDIA_TYPE)
      "application/octet-stream" -> CodeBlock.of("%T.OctetStream", MEDIA_TYPE)
      "text/event-stream" -> CodeBlock.of("%T.EventStream", MEDIA_TYPE)
      "application/x-www-form-urlencoded" -> CodeBlock.of("%T.WWWFormUrlEncoded", MEDIA_TYPE)
      "application/problem+json" -> CodeBlock.of("%T.Problem", MEDIA_TYPE)
      "application/x-x509-ca-cert" -> CodeBlock.of("%T.X509CACert", MEDIA_TYPE)
      "application/x-x509-user-cert" -> CodeBlock.of("%T.X509UserCert", MEDIA_TYPE)
      "application/json-patch+json" -> CodeBlock.of("%T.JsonPatch", MEDIA_TYPE)
      "application/merge-patch+json" -> CodeBlock.of("%T.MergePatch", MEDIA_TYPE)
      else -> CodeBlock.of("MediaType.from(%S)", value)
    }

  private companion object {
    val SUNDAY_OPERATION_FUNCTION = MemberName("io.outfoxx.sunday", "operation")
    val SUNDAY_NULLABLE_OPERATION_FUNCTION = MemberName("io.outfoxx.sunday", "nullableOperation")
    val TYPE_OF = MemberName("kotlin.reflect", "typeOf")
    val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
    val baseProblemProperties = setOf("type", "title", "status", "detail", "instance")
  }
}
