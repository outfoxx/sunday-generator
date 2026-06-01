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

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.genError
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedExchange
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNullify
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedStreaming
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.emit.GeneratedApiIndex
import io.outfoxx.sunday.generator.ir.emit.GeneratedMediaSelection
import io.outfoxx.sunday.generator.ir.emit.defaultMediaSelection
import io.outfoxx.sunday.generator.ir.emit.enabledFor
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
import io.outfoxx.sunday.generator.typescript.utils.ABORT_SIGNAL
import io.outfoxx.sunday.generator.typescript.utils.ASYNC_ITERABLE
import io.outfoxx.sunday.generator.typescript.utils.CREATE_NULLABLE_OPERATION
import io.outfoxx.sunday.generator.typescript.utils.CREATE_OPERATION
import io.outfoxx.sunday.generator.typescript.utils.CREATE_PROBLEM_CODEC
import io.outfoxx.sunday.generator.typescript.utils.CREATE_STREAMING_OPERATION
import io.outfoxx.sunday.generator.typescript.utils.DEFINE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.EVENT_SOURCE
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATE
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.LOCAL_TIME
import io.outfoxx.sunday.generator.typescript.utils.MEDIA_TYPE
import io.outfoxx.sunday.generator.typescript.utils.NULLABLE_OPERATION
import io.outfoxx.sunday.generator.typescript.utils.OFFSET_DATETIME
import io.outfoxx.sunday.generator.typescript.utils.OPERATION
import io.outfoxx.sunday.generator.typescript.utils.PARTIAL
import io.outfoxx.sunday.generator.typescript.utils.PROBLEM
import io.outfoxx.sunday.generator.typescript.utils.PROBLEM_WIRE_SCHEMA
import io.outfoxx.sunday.generator.typescript.utils.RESPONSE
import io.outfoxx.sunday.generator.typescript.utils.SCHEMA_LIKE
import io.outfoxx.sunday.generator.typescript.utils.SCHEMA_OUTPUT
import io.outfoxx.sunday.generator.typescript.utils.SCHEMA_RUNTIME
import io.outfoxx.sunday.generator.typescript.utils.STREAMING_BODY
import io.outfoxx.sunday.generator.typescript.utils.STREAMING_OPERATION
import io.outfoxx.sunday.generator.typescript.utils.TRANSPORT
import io.outfoxx.sunday.generator.typescript.utils.TRANSPORT_REQUEST
import io.outfoxx.sunday.generator.typescript.utils.URL_TEMPLATE
import io.outfoxx.sunday.generator.typescript.utils.URL_TYPE
import io.outfoxx.sunday.generator.typescript.utils.Z
import io.outfoxx.sunday.generator.typescript.utils.isNullable
import io.outfoxx.sunday.generator.typescript.utils.isOptional
import io.outfoxx.sunday.generator.typescript.utils.isUndefinable
import io.outfoxx.sunday.generator.typescript.utils.nonUndefinable
import io.outfoxx.sunday.generator.typescript.utils.nullable
import io.outfoxx.sunday.generator.typescript.utils.quotedIfNotTypeScriptIdentifier
import io.outfoxx.sunday.generator.typescript.utils.recordType
import io.outfoxx.sunday.generator.typescript.utils.typeScriptIdentifierName
import io.outfoxx.sunday.generator.typescript.utils.undefinable
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import io.outfoxx.typescriptpoet.AnyTypeSpecBuilder
import io.outfoxx.typescriptpoet.ClassSpec
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.EnumSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.NameAllocator
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeAliasSpec
import io.outfoxx.typescriptpoet.TypeName
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY
import io.outfoxx.typescriptpoet.TypeName.Companion.ARRAY_BUFFER
import io.outfoxx.typescriptpoet.TypeName.Companion.BOOLEAN
import io.outfoxx.typescriptpoet.TypeName.Companion.NUMBER
import io.outfoxx.typescriptpoet.TypeName.Companion.PROMISE
import io.outfoxx.typescriptpoet.TypeName.Companion.SET
import io.outfoxx.typescriptpoet.TypeName.Companion.STRING
import io.outfoxx.typescriptpoet.TypeName.Companion.VOID
import io.outfoxx.typescriptpoet.TypeName.Companion.bound
import io.outfoxx.typescriptpoet.TypeName.Companion.parameterizedType
import io.outfoxx.typescriptpoet.TypeName.Companion.typeVariable
import io.outfoxx.typescriptpoet.tag

/**
 * TypeScript/Sunday service generator that renders service declarations from Sunday IR.
 */
class TypeScriptSundayIrGenerator(
  private val api: GeneratedApi,
  private val typeRegistry: TypeScriptTypeOutputRegistry,
  private val options: TypeScriptSundayOptions,
) {

  private val transportTypeVariable = typeVariable("Factory", bound(TypeName.implicit("SundayTransport")))

  private val defaultMediaTypes = api.orderedDefaultMediaTypes(options.defaultMediaTypes)
  private val index = GeneratedApiIndex(api)
  private val typeScriptEnumEntriesByModel = mutableMapOf<GeneratedModel, List<TypeScriptEnumEntry>>()

  /** Generates TypeScript/Sunday service types from IR and registers them in the type registry. */
  fun generateServiceTypes() {
    val services = api.typeScriptSundayServices()

    registerCompanionSchemaTypes()
    generateModelTypes()
    generateProblemTypes(services)

    val serviceTypes =
      services.map { service ->
        val serviceSimpleName = service.typeSimpleName()
        val modulePath = resolveServiceModulePath(serviceSimpleName, api.targets["typescript"]?.moduleName)
        val serviceTypeName = typeRegistry.generatedTypeName(serviceSimpleName, modulePath)

        val serviceType = generateServiceType(serviceTypeName, service)
        typeRegistry.addServiceType(serviceTypeName, serviceType.interfaceBuilder, serviceType.extras)
        GeneratedTypeScriptService(service, serviceTypeName)
      }

    if (options.aggregateServices && serviceTypes.size > 1) {
      val aggregateTypeName = aggregateServiceTypeName()
      if (serviceTypes.any { serviceType -> serviceType.typeName == aggregateTypeName }) {
        error(
          "Cannot generate TypeScript/Sunday aggregate service '$aggregateTypeName' because it matches a generated service",
        )
      }
      val aggregateType = generateAggregateServiceType(aggregateTypeName, serviceTypes)
      typeRegistry.addServiceType(aggregateTypeName, aggregateType.interfaceBuilder, aggregateType.extras)
    }
  }

  private fun GeneratedApi.typeScriptSundayServices(): List<GeneratedService> =
    services
      .mapNotNull { service ->
        service
          .copy(operations = service.operations.filter { operation -> operation.isTypeScriptSundayOperation() })
          .withTypeScriptSundayBaseUri()
          .takeIf { filtered -> filtered.operations.isNotEmpty() }
      }

  private fun GeneratedOperation.isTypeScriptSundayOperation(): Boolean =
    method !in asyncApiOperationMethods ||
      (path.startsWith("/") && !hasNonHttpProtocolBinding())

  private fun GeneratedService.withTypeScriptSundayBaseUri(): GeneratedService =
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

  private fun generateProblemTypes(services: List<GeneratedService>) {
    services
      .flatMap { service -> service.referencedProblems(index) }
      .distinctBy { problem -> Triple(problem.name, problem.sourceName, problem.source) }
      .forEach { problem -> generateProblemType(problem) }
  }

  private fun registerCompanionSchemaTypes() {
    api.models
      .filter { model ->
        model.kind == GeneratedModel.Kind.OBJECT &&
          model.scope == null &&
          model.isProblemModel() &&
          model.isInDiscriminatedHierarchy()
      }.forEach { model ->
        typeRegistry.addCompanionSchemaType(model.typeName(model.name.toUpperCamelCase()))
      }
  }

  private fun GeneratedModel.isInDiscriminatedHierarchy(): Boolean = rootModel()?.discriminator != null

  private fun generateModelTypes() {
    api.models
      .filter { model ->
        model.scope == null
      }.forEach { model ->
        when (model.kind) {
          GeneratedModel.Kind.ENUM -> generateSharedEnumModelType(model)
          GeneratedModel.Kind.OBJECT -> generateSharedObjectModelType(model)
          GeneratedModel.Kind.UNION -> generateSharedUnionModelType(model)
          else -> Unit
        }
      }
  }

  private fun generateServiceType(
    serviceTypeName: TypeName.Standard,
    service: GeneratedService,
  ): TypeScriptServiceType {
    val mediaSelection = service.defaultMediaSelection(defaultMediaTypes)
    val interfaceBuilder =
      InterfaceSpec
        .builder(serviceTypeName.simpleName())
        .addTypeVariable(transportTypeVariable)
    val serviceClassName = TypeName.standard("${serviceTypeName.simpleName()}Client")
    val serviceClassBuilder =
      ClassSpec
        .builder(serviceClassName)
        .addTypeVariable(transportTypeVariable)
        .tag(CodeBlock.builder())

    serviceClassBuilder
      .addProperty(
        PropertySpec
          .builder("transport", transportTypeVariable, false, Modifier.PUBLIC)
          .initializer("transport")
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultContentTypes", parameterizedType(ARRAY, MEDIA_TYPE))
          .build(),
      ).addProperty(
        PropertySpec
          .builder("defaultAcceptTypes", parameterizedType(ARRAY, MEDIA_TYPE))
          .build(),
      )

    val constructorBuilder =
      FunctionSpec
        .constructorBuilder()
        .addParameter("transport", transportTypeVariable, false, Modifier.PUBLIC)
        .addParameter(
          ParameterSpec
            .builder("options", serviceOptionsType().undefinable)
            .defaultValue("undefined")
            .build(),
        ).addStatement(
          "this.defaultContentTypes =\noptions?.defaultContentTypes ?? %L",
          mediaTypesArray(mediaSelection.contentTypes),
        ).addStatement(
          "this.defaultAcceptTypes =\noptions?.defaultAcceptTypes ?? %L",
          mediaTypesArray(mediaSelection.acceptTypes),
        )

    service.referencedProblems(index).forEach { problem ->
      constructorBuilder.addCode(
        CodeBlock
          .builder()
          .add("transport.registerProblem(%S, ", problem.resolvedTypeUri(options.defaultProblemBaseUri))
          .add(typeRegistry.schemaInitializer(problem.typeName()))
          .add(");\n")
          .build(),
      )
    }

    serviceClassBuilder.constructor(constructorBuilder.build())

    generateLocalModelTypes(serviceTypeName, service)

    service.operations.forEach { operation ->
      interfaceBuilder.addFunction(
        operation
          .operationFunctionSignature(serviceTypeName, defaultOptionalParameters = false)
          .toBuilder()
          .addModifiers(Modifier.ABSTRACT)
          .build(),
      )
      serviceClassBuilder.addFunction(operation.operationFunction(serviceTypeName, serviceClassBuilder))
    }

    return TypeScriptServiceType(
      interfaceBuilder,
      listOfNotNull(
        transportAliasCode(),
        serviceClassBuilder.extraCode(),
        serviceClassBuilder.build(),
        service.factoryFunction(serviceTypeName, serviceClassName, mediaSelection),
        service.baseUrlNamespace(serviceTypeName),
      ),
    )
  }

  private fun GeneratedService.baseUrlFunction(
    serviceTypeName: TypeName.Standard,
    static: Boolean = false,
    export: Boolean = false,
  ): FunctionSpec? {
    val baseUri = baseUri ?: return null
    val functionBuilder = FunctionSpec.builder("baseURL")

    if (export) {
      functionBuilder.addModifiers(Modifier.EXPORT)
    }
    if (static) {
      functionBuilder.addModifiers(Modifier.STATIC)
    }

    return functionBuilder
      .returns(URL_TEMPLATE)
      .apply {
        baseUriParameters.forEach { parameter ->
          addParameter(
            parameter.name,
            parameter.type.typeName(serviceTypeName),
            optional = parameter.defaultValue != null,
          )
        }
      }.addCode("return new URLTemplate(%>\n")
      .addCode("%S,\n{", baseUri)
      .apply {
        baseUriParameters.forEachIndexed { idx, parameter ->
          addCode("%N", parameter.name)

          parameter.defaultValue?.let { defaultValue ->
            addCode(": %N ?? ", parameter.name)
            addCode(parameter.defaultValueCode(serviceTypeName, defaultValue))
          }

          if (idx < baseUriParameters.size - 1) {
            addCode(", ")
          }
        }
      }.addCode("}%<\n);\n")
      .build()
  }

  private fun GeneratedService.baseUrlNamespace(serviceTypeName: TypeName.Standard): ModuleSpec? {
    val baseUrlFunction = baseUrlFunction(serviceTypeName, export = true) ?: return null
    return ModuleSpec
      .builder(serviceTypeName.simpleName(), ModuleSpec.Kind.NAMESPACE)
      .addModifier(Modifier.EXPORT)
      .addFunction(baseUrlFunction)
      .build()
  }

  private fun GeneratedService.factoryFunction(
    serviceTypeName: TypeName.Standard,
    serviceClassName: TypeName.Standard,
    mediaSelection: GeneratedMediaSelection,
  ): FunctionSpec =
    FunctionSpec
      .builder(serviceTypeName.factoryFunctionName)
      .addModifiers(Modifier.EXPORT)
      .addTypeVariable(transportTypeVariable)
      .addParameter("transport", transportTypeVariable)
      .addParameter(
        ParameterSpec
          .builder("options", serviceOptionsType().undefinable)
          .defaultValue("undefined")
          .build(),
      ).returns(serviceTypeName.parameterized(transportTypeVariable))
      .addStatement("return new %T(transport, options)", serviceClassName)
      .build()

  private fun GeneratedParameter.defaultValueCode(
    serviceTypeName: TypeName.Standard,
    value: Any,
  ): CodeBlock {
    val typeName = type.typeName(serviceTypeName)
    val enumModel = type.modelOrNull(index)?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
    if (value is String && enumModel != null) {
      return CodeBlock.of(
        "%T.%L",
        typeName,
        enumModel.requireTypeScriptEnumMemberNameForValue(value, "default"),
      )
    }
    return literal(value)
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

    return "${servicePrefix.toUpperCamelCase()}${options.serviceSuffix}"
  }

  private fun aggregateServiceTypeName(): TypeName.Standard {
    val serviceName = (options.aggregateServiceName ?: options.serviceSuffix).ifBlank { "API" }
    return typeRegistry.generatedTypeName(serviceName, serviceName.camelCaseToKebabCase())
  }

  private fun generateAggregateServiceType(
    aggregateTypeName: TypeName.Standard,
    services: List<GeneratedTypeScriptService>,
  ): TypeScriptServiceType {
    val mediaSelection = services.aggregateMediaSelection()
    val names = NameAllocator()
    val serviceProperties =
      services.map { service ->
        val proposedName = service.service.aggregatePropertyName()
        AggregateServiceProperty(service.typeName, names.newName(proposedName, service))
      }

    val constructorBuilder =
      FunctionSpec
        .constructorBuilder()
        .addParameter("transport", transportTypeVariable, false, Modifier.PUBLIC)
        .addParameter(
          ParameterSpec
            .builder("options", serviceOptionsType().undefinable)
            .defaultValue("undefined")
            .build(),
        ).addStatement(
          "this.defaultContentTypes =\noptions?.defaultContentTypes ?? %L",
          mediaTypesArray(mediaSelection.contentTypes),
        ).addStatement(
          "this.defaultAcceptTypes =\noptions?.defaultAcceptTypes ?? %L",
          mediaTypesArray(mediaSelection.acceptTypes),
        )

    val aggregateInterfaceBuilder =
      InterfaceSpec
        .builder(aggregateTypeName.simpleName())
        .addTypeVariable(transportTypeVariable)
    val aggregateClassName = TypeName.standard("${aggregateTypeName.simpleName()}Client")
    val aggregateBuilder =
      ClassSpec
        .builder(aggregateClassName)
        .addTypeVariable(transportTypeVariable)
        .tag(CodeBlock.builder())
        .addProperty(
          PropertySpec
            .builder("defaultContentTypes", parameterizedType(ARRAY, MEDIA_TYPE))
            .build(),
        ).addProperty(
          PropertySpec
            .builder("defaultAcceptTypes", parameterizedType(ARRAY, MEDIA_TYPE))
            .build(),
        )

    serviceProperties.forEach { serviceProperty ->
      aggregateInterfaceBuilder.addProperty(
        PropertySpec
          .builder(serviceProperty.name, serviceProperty.typeName.parameterized(transportTypeVariable))
          .build(),
      )
      aggregateBuilder.addProperty(
        PropertySpec
          .builder(serviceProperty.name, serviceProperty.typeName.parameterized(transportTypeVariable))
          .build(),
      )
      constructorBuilder.addStatement(
        "this.%N = %T(transport, { defaultContentTypes: this.defaultContentTypes, " +
          "defaultAcceptTypes: this.defaultAcceptTypes })",
        serviceProperty.name,
        serviceProperty.typeName.factoryFunctionTypeName(),
      )
    }

    return TypeScriptServiceType(
      aggregateInterfaceBuilder,
      listOf(
        transportAliasCode(),
        aggregateBuilder.constructor(constructorBuilder.build()).build(),
        aggregateFactoryFunction(aggregateTypeName, aggregateClassName),
      ),
    )
  }

  private fun aggregateFactoryFunction(
    aggregateTypeName: TypeName.Standard,
    aggregateClassName: TypeName.Standard,
  ): FunctionSpec =
    FunctionSpec
      .builder(aggregateTypeName.factoryFunctionName)
      .addModifiers(Modifier.EXPORT)
      .addTypeVariable(transportTypeVariable)
      .addParameter("transport", transportTypeVariable)
      .addParameter(
        ParameterSpec
          .builder("options", serviceOptionsType().undefinable)
          .defaultValue("undefined")
          .build(),
      ).returns(aggregateTypeName.parameterized(transportTypeVariable))
      .addStatement("return new %T(transport, options)", aggregateClassName)
      .build()

  private fun GeneratedService.aggregatePropertyName(): String =
    typeSimpleName()
      .removeSuffix(options.serviceSuffix)
      .ifBlank { name.removeSuffix("Service") }
      .toLowerCamelCase()
      .typeScriptIdentifierName

  private fun transportAliasCode(): CodeBlock =
    CodeBlock.of(
      "type SundayTransport = %T<unknown>;\n",
      TRANSPORT,
    )

  private fun ClassSpec.Builder.extraCode(): CodeBlock? =
    (tags[CodeBlock.Builder::class] as? CodeBlock.Builder)
      ?.takeIf { it.isNotEmpty() }
      ?.build()

  private fun serviceOptionsType(): TypeName =
    TypeName.anonymousType(
      listOf(
        TypeName.Anonymous.Member("defaultContentTypes", parameterizedType(ARRAY, MEDIA_TYPE), true),
        TypeName.Anonymous.Member("defaultAcceptTypes", parameterizedType(ARRAY, MEDIA_TYPE), true),
      ),
    )

  private val TypeName.Standard.factoryFunctionName: String
    get() = "create${simpleName()}"

  private fun TypeName.Standard.factoryFunctionTypeName(): TypeName.Standard =
    TypeName.namedImport(factoryFunctionName, (base as SymbolSpec.Imported).source)

  private fun List<GeneratedTypeScriptService>.aggregateMediaSelection(): GeneratedMediaSelection {
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

  private data class GeneratedTypeScriptService(
    val service: GeneratedService,
    val typeName: TypeName.Standard,
  )

  private data class TypeScriptServiceType(
    val interfaceBuilder: InterfaceSpec.Builder,
    val extras: List<Any>,
  )

  private data class AggregateServiceProperty(
    val typeName: TypeName.Standard,
    val name: String,
  )

  private fun generateLocalModelTypes(
    serviceTypeName: TypeName.Standard,
    service: GeneratedService,
  ) {
    index
      .referencedScopedModels(service)
      .forEach { model ->
        when (model.kind) {
          GeneratedModel.Kind.ENUM -> generateLocalEnumModelType(serviceTypeName, model)
          GeneratedModel.Kind.OBJECT -> generateLocalObjectModelType(serviceTypeName, model)
          else -> Unit
        }
      }
  }

  private fun generateLocalEnumModelType(
    serviceTypeName: TypeName.Standard,
    model: GeneratedModel,
  ) {
    val typeName = serviceTypeName.nested(model.name)
    generateEnumModelType(typeName, model)
  }

  private fun generateProblemType(problem: GeneratedProblem) {
    val typeName = problem.typeName()
    val specName = typeName.sibling("Spec")
    val properties =
      problem.fields.map { field ->
        field to field.type.typeName(typeName).modelPropertyType(field)
      }
    val classBuilder =
      ClassSpec
        .builder(typeName)
        .addModifiers(Modifier.EXPORT)
        .superClass(PROBLEM)
        .addProperty(
          PropertySpec
            .builder("TYPE", STRING)
            .addModifiers(Modifier.STATIC)
            .initializer("%S", problem.typeUri)
            .build(),
        )

    properties.forEach { (field, fieldType) ->
      classBuilder.addProperty(
        PropertySpec
          .builder(field.name.typeScriptIdentifierName, fieldType)
          .build(),
      )
    }

    val constructorBuilder =
      FunctionSpec
        .constructorBuilder()
        .addParameter(
          ParameterSpec
            .builder("spec", specName)
            .apply {
              if (properties.isEmpty()) {
                defaultValue("{}")
              }
            }.build(),
        ).addCode(problemSuperCode(problem, typeName, properties.map { it.first }))

    properties.forEach { (field, _) ->
      val fieldName = field.name.typeScriptIdentifierName
      constructorBuilder.addStatement("this.%N = spec.%N", fieldName, fieldName)
    }
    classBuilder.constructor(constructorBuilder.build())

    typeRegistry.addModelType(
      typeName,
      classBuilder,
      listOf(
        problemSpecSchemaCode(typeName, properties),
        problemSpecType(specName, typeName),
        problemSchemaCode(typeName, properties),
      ),
    )
  }

  private fun problemSpecType(
    specName: TypeName.Standard,
    typeName: TypeName.Standard,
  ): TypeAliasSpec =
    TypeAliasSpec
      .builder(
        specName.simpleName(),
        schemaOutputTypeOf(typeName.sibling("SpecSchema")),
      ).addModifiers(Modifier.EXPORT)
      .build()

  private fun problemSpecSchemaCode(
    typeName: TypeName.Standard,
    properties: List<Pair<GeneratedModelProperty, TypeName>>,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add(
        "export const %L = %Q((runtime: %T) => {\n",
        typeName.sibling("SpecSchema").simpleName(),
        DEFINE_SCHEMA,
        SCHEMA_RUNTIME,
      ).add("  return %T.looseObject({\n", Z)
      .apply {
        properties.forEach { (field, _) ->
          add("    %S: ", field.name.typeScriptIdentifierName)
          add(field.type.zodSchema(typeName, field.required, field.validation))
          add(",\n")
        }
      }.add(
        "    'instance': %T.union([%T.string(), %T.instanceof(%T)]).optional()\n",
        Z,
        Z,
        Z,
        URL_TYPE,
      ).add("  });\n")
      .add("});\n")
      .build()

  private fun problemSuperCode(
    problem: GeneratedProblem,
    typeName: TypeName.Standard,
    fields: List<GeneratedModelProperty>,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add(
        """
        |super({
        |  type: %T.TYPE,
        |  title: %S,
        |  status: %L,
        |  detail: %S,
        |  instance: spec.instance
        """.trimMargin(),
        typeName,
        problem.title,
        problem.status,
        problem.detail,
      ).apply {
        if (fields.isNotEmpty()) {
          add(",\n")
          fields.forEachIndexed { idx, field ->
            val fieldName = field.name.typeScriptIdentifierName
            add("  %N: spec.%N", fieldName, fieldName)
            if (idx < fields.lastIndex) {
              add(",")
            }
            add("\n")
          }
        } else {
          add("\n")
        }
      }.add("});\n\n")
      .build()

  private fun problemSchemaCode(
    typeName: TypeName.Standard,
    properties: List<Pair<GeneratedModelProperty, TypeName>>,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add(
        "export const %L = %Q((runtime: %T) => {\n",
        typeName.sibling("WireSchema").simpleName(),
        DEFINE_SCHEMA,
        SCHEMA_RUNTIME,
      ).add("  return %T.extend({", PROBLEM_WIRE_SCHEMA)
      .apply {
        if (properties.isNotEmpty()) {
          add("\n")
          properties.forEachIndexed { idx, (field, _) ->
            add("    %S: ", field.serializationName ?: field.name)
            add(field.type.zodSchema(typeName, field.required, field.validation))
            if (idx < properties.lastIndex) {
              add(",")
            }
            add("\n")
          }
          add("  ")
        }
      }.add("});\n")
      .add("});\n\n")
      .add(
        "export const %L: %T = %Q((runtime: %T) => {\n",
        typeName.sibling("Schema").simpleName(),
        SCHEMA_LIKE.parameterized(typeName),
        DEFINE_SCHEMA,
        SCHEMA_RUNTIME,
      ).add(
        "  return %Q(%T, runtime.resolveSchema(%T));\n",
        CREATE_PROBLEM_CODEC,
        typeName,
        typeName.sibling("WireSchema"),
      ).add("});\n")
      .build()

  private fun generateSharedEnumModelType(model: GeneratedModel) {
    val simpleName = model.name.toUpperCamelCase()
    generateEnumModelType(model.typeName(simpleName), model)
  }

  private fun generateEnumModelType(
    typeName: TypeName.Standard,
    model: GeneratedModel,
  ) {
    val enumBuilder =
      EnumSpec
        .builder(typeName.simpleName())
        .addModifiers(Modifier.EXPORT)

    model.typeScriptEnumEntries().forEach { entry ->
      enumBuilder.addConstant(entry.name, CodeBlock.of("%S", entry.value))
    }

    val schemaCode =
      CodeBlock
        .builder()
        .add("export const %LSchema = ", typeName.simpleName())
        .add("%T.enum(%T)", Z, typeName)
        .add(";\n")
        .build()

    typeRegistry.addModelType(typeName, enumBuilder, listOf(schemaCode))
  }

  private fun generateSharedObjectModelType(model: GeneratedModel) {
    val simpleName = model.name.toUpperCamelCase()
    val typeName = model.typeName(simpleName)
    generateObjectModelType(typeName, model, typeName, typeName.sibling("Spec"))
  }

  private fun generateSharedUnionModelType(model: GeneratedModel) {
    val simpleName = model.name.toUpperCamelCase()
    val typeName = model.typeName(simpleName)
    val typeAliasBuilder =
      TypeAliasSpec
        .builder(
          typeName.simpleName(),
          if (model.isRecursiveModel()) {
            TypeName.unionType(*model.aliases.map { alias -> alias.typeName(typeName) }.toTypedArray())
          } else {
            schemaOutputType(typeName)
          },
        ).addModifiers(Modifier.EXPORT)

    typeRegistry.addModelType(
      typeName,
      typeAliasBuilder,
      listOf(
        unionSchemaCode(
          typeName,
          model,
          schemaTypeName = typeName.takeIf { model.isRecursiveModel() },
        ),
      ),
    )
  }

  private fun generateObjectModelType(
    ownerTypeName: TypeName.Standard,
    model: GeneratedModel,
    typeName: TypeName.Standard,
    specName: TypeName.Standard,
  ) {
    if (!model.isProblemModel()) {
      generatePlainObjectModelType(ownerTypeName, model, typeName)
      return
    }

    val rootDiscriminatorName = model.rootModel()?.discriminator
    val inheritedProperties = model.inheritedProperties(rootDiscriminatorName)
    val inheritedPropertyNames = inheritedProperties.map { property -> property.name }.toSet()
    val declaredProperties =
      model.properties
        .filterNot { property -> property.name == rootDiscriminatorName || property.name in inheritedPropertyNames }
    val allSerializableProperties = inheritedProperties + declaredProperties
    val childModels = model.childModels()
    val isDiscriminatorBase = rootDiscriminatorName != null && childModels.isNotEmpty()
    val isProblemModel = model.isProblemModel()
    val leafDiscriminatorValue =
      rootDiscriminatorName
        ?.takeIf { model.inherits.isNotEmpty() && childModels.isEmpty() }
        ?.let { model.discriminatorValue ?: model.name }
    val discriminatorProperty =
      rootDiscriminatorName
        ?.let { discriminatorName ->
          (model.inheritedProperties(null) + model.properties)
            .firstOrNull { property -> property.name == discriminatorName }
        }
    val discriminatorTypeName = discriminatorProperty?.type?.typeName(ownerTypeName)
    val superTypeName = model.inherits.firstOrNull()?.typeName(ownerTypeName) as? TypeName.Standard
    val isRootProblemModel = isProblemModel && superTypeName == null

    val interfaceBuilder =
      InterfaceSpec
        .builder(specName.simpleName())
        .addModifiers(Modifier.EXPORT)
    val classBuilder =
      ClassSpec
        .builder(typeName.simpleName())
        .addModifiers(Modifier.EXPORT)
        .addMixin(specName)
        .tag(TypeScriptTypeRegistry.SpecificationInterface(interfaceBuilder))

    when {
      superTypeName != null -> {
        classBuilder.superClass(superTypeName)
        interfaceBuilder.addSuperInterface(superTypeName.sibling("Spec"))
      }

      isRootProblemModel -> {
        classBuilder.superClass(PROBLEM)
      }
    }

    if (isDiscriminatorBase) {
      classBuilder.addModifiers(Modifier.ABSTRACT)
      discriminatorProperty?.let { property ->
        classBuilder.addProperty(
          PropertySpec
            .builder(property.name.typeScriptIdentifierName, requireNotNull(discriminatorTypeName))
            .addModifiers(Modifier.ABSTRACT, Modifier.READONLY)
            .build(),
        )
      }
    }

    declaredProperties.forEach { property ->
      val propertyName = property.name.typeScriptIdentifierName
      val propertyType = property.type.typeName(ownerTypeName).modelPropertyType(property)
      interfaceBuilder.addProperty(
        PropertySpec
          .builder(propertyName, propertyType.nonUndefinable)
          .optional(propertyType.isUndefinable)
          .build(),
      )
      if (!(isRootProblemModel && property.isSatisfiedByBaseProblemClass())) {
        classBuilder.addProperty(PropertySpec.builder(propertyName, propertyType).build())
      }
    }

    if (leafDiscriminatorValue != null && discriminatorProperty != null && discriminatorTypeName != null) {
      classBuilder.addFunction(
        FunctionSpec
          .builder(discriminatorProperty.name.typeScriptIdentifierName)
          .addModifiers(Modifier.GET)
          .returns(discriminatorTypeName)
          .addStatement(
            "return %L",
            discriminatorValueCode(
              discriminatorProperty,
              discriminatorTypeName,
              leafDiscriminatorValue,
            ),
          ).build(),
      )
    }

    if (allSerializableProperties.isNotEmpty()) {
      val constructorBuilder =
        FunctionSpec
          .constructorBuilder()
          .addParameter("init", specName)

      if (superTypeName != null) {
        if (inheritedProperties.isNotEmpty()) {
          constructorBuilder.addStatement("super(init)")
        } else {
          constructorBuilder.addStatement("super()")
        }
      } else if (isRootProblemModel) {
        constructorBuilder.addCode(problemModelSuperCode())
      }

      declaredProperties.forEach { property ->
        val propertyName = property.name.typeScriptIdentifierName
        if (!(isRootProblemModel && property.isSatisfiedByBaseProblemClass())) {
          constructorBuilder.addStatement("this.%N = init.%N", propertyName, propertyName)
        }
      }
      classBuilder.constructor(constructorBuilder.build())

      if (!isDiscriminatorBase) {
        classBuilder.addFunction(
          FunctionSpec
            .builder("copy")
            .returns(typeName)
            .addParameter("changes", PARTIAL.parameterized(specName))
            .addStatement("return new %T(Object.assign({}, this, changes))", typeName)
            .build(),
        )
      }
    }

    val toStringValues =
      allSerializableProperties.joinToString(", ") { property ->
        val propertyName = property.name.typeScriptIdentifierName
        "$propertyName='\${this.$propertyName}'"
      }
    val toStringTemplate =
      CodeBlock
        .of("%N(%L)", typeName, toStringValues)
        .toString()
    classBuilder.addFunction(
      FunctionSpec
        .builder("toString")
        .returns(STRING)
        .addStatement("return %P", toStringTemplate)
        .build(),
    )

    val schemaCode =
      if (isDiscriminatorBase) {
        if (model.externallyDiscriminated) {
          externallyDiscriminatedObjectSchemaCode(typeName, allSerializableProperties)
        } else {
          discriminatedObjectSchemaCode(typeName, requireNotNull(rootDiscriminatorName), childModels)
        }
      } else {
        localObjectSchemaCode(
          ownerTypeName,
          typeName,
          allSerializableProperties,
          leafDiscriminator =
            leafDiscriminatorValue?.let { value ->
              requireNotNull(discriminatorProperty) to value
            },
        )
      }
    val typedExternalDiscriminatorCode = model.externalDiscriminatorTypedCode(typeName, allSerializableProperties)

    if (model.isInDiscriminatedHierarchy()) {
      typeRegistry.addModelType(typeName, classBuilder)
      typeRegistry.addCompanionSchemaCode(typeName, schemaCode)
    } else {
      typeRegistry.addModelType(typeName, classBuilder, listOfNotNull(typedExternalDiscriminatorCode, schemaCode))
    }
  }

  private fun generatePlainObjectModelType(
    ownerTypeName: TypeName.Standard,
    model: GeneratedModel,
    typeName: TypeName.Standard,
  ) {
    val rootDiscriminatorName = model.rootModel()?.discriminator
    val inheritedProperties = model.inheritedProperties(rootDiscriminatorName)
    val inheritedPropertyNames = inheritedProperties.map { property -> property.name }.toSet()
    val declaredProperties =
      model.properties
        .filterNot { property -> property.name == rootDiscriminatorName || property.name in inheritedPropertyNames }
    val allSerializableProperties = inheritedProperties + declaredProperties
    val childModels = model.childModels()
    val isDiscriminatorBase = rootDiscriminatorName != null && childModels.isNotEmpty()
    val leafDiscriminatorValue =
      rootDiscriminatorName
        ?.takeIf { model.inherits.isNotEmpty() && childModels.isEmpty() }
        ?.let { model.discriminatorValue ?: model.name }
    val discriminatorProperty =
      rootDiscriminatorName
        ?.let { discriminatorName ->
          (model.inheritedProperties(null) + model.properties)
            .firstOrNull { property -> property.name == discriminatorName }
        }

    val recursiveModel = model.isRecursiveModel()
    val needsExplicitSchemaType =
      recursiveModel ||
        (isDiscriminatorBase && childModels.any { childModel -> childModel.isRecursiveModel() })
    val typeSpec: AnyTypeSpecBuilder =
      if (needsExplicitSchemaType && isDiscriminatorBase) {
        TypeAliasSpec
          .builder(
            typeName.simpleName(),
            TypeName.unionType(
              *childModels
                .map { childModel -> childModel.typeName(childModel.name.toUpperCamelCase()) }
                .toTypedArray(),
            ),
          ).addModifiers(Modifier.EXPORT)
      } else if (needsExplicitSchemaType) {
        plainObjectInterfaceType(
          typeName,
          allSerializableProperties,
          leafDiscriminatorValue?.let { value ->
            requireNotNull(discriminatorProperty) to value
          },
        )
      } else {
        TypeAliasSpec
          .builder(typeName.simpleName(), schemaOutputType(typeName))
          .addModifiers(Modifier.EXPORT)
      }

    val schemaCode =
      if (isDiscriminatorBase) {
        if (model.externallyDiscriminated) {
          plainExternallyDiscriminatedObjectSchemaCode(
            typeName,
            childModels,
            typeName.takeIf { needsExplicitSchemaType },
          )
        } else {
          plainDiscriminatedObjectSchemaCode(
            typeName,
            requireNotNull(rootDiscriminatorName),
            childModels,
            typeName.takeIf { needsExplicitSchemaType },
          )
        }
      } else {
        plainObjectSchemaCode(
          ownerTypeName,
          typeName,
          allSerializableProperties,
          schemaTypeName = typeName.takeIf { needsExplicitSchemaType },
          leafDiscriminator =
            leafDiscriminatorValue?.let { value ->
              requireNotNull(discriminatorProperty) to value
            },
        )
      }

    typeRegistry.addModelType(typeName, typeSpec, listOf(schemaCode))
  }

  private fun schemaOutputType(typeName: TypeName.Standard): TypeName = schemaOutputTypeOf(typeName.sibling("Schema"))

  private fun schemaOutputTypeOf(schemaTypeName: TypeName.Standard): TypeName =
    SCHEMA_OUTPUT.parameterized(TypeName.standard("typeof ${schemaTypeName.simpleName()}"))

  private fun plainObjectInterfaceType(
    typeName: TypeName.Standard,
    properties: List<GeneratedModelProperty>,
    leafDiscriminator: Pair<GeneratedModelProperty, String>? = null,
  ): AnyTypeSpecBuilder {
    val interfaceBuilder =
      InterfaceSpec
        .builder(typeName.simpleName())
        .addModifiers(Modifier.EXPORT)

    leafDiscriminator?.let { (property, _) ->
      interfaceBuilder.addProperty(
        PropertySpec
          .builder(property.name.typeScriptIdentifierName, property.type.typeName(typeName))
          .build(),
      )
    }

    properties.forEach { property ->
      val propertyType = property.type.typeName(typeName).modelPropertyType(property)
      interfaceBuilder.addProperty(
        PropertySpec
          .builder(property.name.typeScriptIdentifierName, propertyType.nonUndefinable)
          .optional(propertyType.isUndefinable)
          .build(),
      )
    }

    return interfaceBuilder
  }

  private fun generateLocalObjectModelType(
    serviceTypeName: TypeName.Standard,
    model: GeneratedModel,
  ) {
    val typeName = serviceTypeName.nested(model.name)
    val specName = serviceTypeName.nested("${model.name}Spec")
    generateObjectModelType(serviceTypeName, model, typeName, specName)
  }

  private fun GeneratedModel.rootModel(): GeneratedModel? {
    val parent = inherits.firstOrNull()?.modelOrNull(index)
    return parent?.rootModel() ?: this
  }

  private fun GeneratedModel.childModels(): List<GeneratedModel> =
    api.models.filter { candidate ->
      candidate.inherits.any { inherited -> inherited.modelOrNull(index) == this }
    }

  private fun GeneratedModel.discriminatorCaseModels(): List<Pair<String?, GeneratedModel>> =
    buildList {
      val mappedDiscriminators =
        discriminatorMappings
          .mapNotNull { (discriminatorValue, typeRef) ->
            val model = typeRef.modelOrNull(index) ?: return@mapNotNull null
            model to discriminatorValue
          }.toMap()
      val childModels = childModels()
      childModels.forEach { model -> add((mappedDiscriminators[model] ?: model.discriminatorValue) to model) }
      discriminatorMappings.forEach { (discriminatorValue, typeRef) ->
        val model = typeRef.modelOrNull(index) ?: return@forEach
        if (model in childModels) {
          return@forEach
        }
        add(discriminatorValue to model)
      }
    }

  private fun GeneratedModel.isRecursiveModel(): Boolean = referencesModel(this, mutableSetOf())

  private fun GeneratedModel.referencesModel(
    target: GeneratedModel,
    visited: MutableSet<GeneratedModel>,
  ): Boolean {
    if (!visited.add(this)) {
      return false
    }
    return referencedModels().any { referenced ->
      referenced == target || referenced.referencesModel(target, visited)
    }
  }

  private fun GeneratedModel.referencedModels(): List<GeneratedModel> =
    buildList {
      properties.forEach { property -> addAll(property.type.referencedModels()) }
      aliases.forEach { alias -> addAll(alias.referencedModels()) }
      childModels().forEach { childModel -> add(childModel) }
      additionalProperties?.type?.let { additionalPropertiesType ->
        addAll(additionalPropertiesType.referencedModels())
      }
      patternProperties.forEach { patternProperty -> addAll(patternProperty.type.referencedModels()) }
    }.distinct()

  private fun GeneratedTypeRef.referencedModels(): List<GeneratedModel> =
    buildList {
      modelOrNull(index)?.let(::add)
      arguments.forEach { argument -> addAll(argument.referencedModels()) }
    }

  private fun GeneratedModel.isProblemModel(): Boolean =
    rootModel()?.let { root ->
      root.name.endsWith("Problem") &&
        root.properties.any { property -> property.name == "type" } &&
        root.properties.any { property -> property.name == "title" } &&
        root.properties.any { property -> property.name == "status" }
    } == true

  private fun GeneratedModelProperty.isSatisfiedByBaseProblemClass(): Boolean =
    serializationName == null &&
      (name in requiredBaseProblemProperties || (name in optionalBaseProblemProperties && !required))

  private fun problemModelSuperCode(): CodeBlock =
    CodeBlock
      .builder()
      .add(
        """
        |super({
        |  ...init,
        |  type: init.type ?? %T.BLANK_URL,
        |  title: init.title ?? '',
        |  status: init.status ?? 0,
        |  detail: init.detail ?? undefined,
        |  instance: init.instance ?? undefined
        |});
        |
        """.trimMargin(),
        PROBLEM,
      ).build()

  private fun GeneratedModel.inheritedProperties(discriminatorName: String?): List<GeneratedModelProperty> {
    val parent = inherits.firstOrNull()?.modelOrNull(index) ?: return emptyList()
    return parent.inheritedProperties(discriminatorName) +
      parent.properties.filterNot { property -> property.name == discriminatorName }
  }

  private fun discriminatorValueCode(
    discriminatorProperty: GeneratedModelProperty,
    discriminatorTypeName: TypeName,
    discriminatorValue: String,
  ): CodeBlock {
    val enumModel =
      discriminatorProperty.type
        .modelOrNull(index)
        ?.takeIf { model ->
          model.kind == GeneratedModel.Kind.ENUM
        }
    if (enumModel != null) {
      return CodeBlock.of(
        "%T.%L",
        discriminatorTypeName,
        enumModel.requireTypeScriptEnumMemberNameForValue(discriminatorValue, "discriminator"),
      )
    }
    return CodeBlock.of("%S", discriminatorValue)
  }

  private fun discriminatorLiteralSchema(
    discriminatorProperty: GeneratedModelProperty,
    discriminatorTypeName: TypeName,
    discriminatorValue: String,
  ): CodeBlock {
    val enumModel =
      discriminatorProperty.type
        .modelOrNull(index)
        ?.takeIf { model ->
          model.kind == GeneratedModel.Kind.ENUM
        }
    if (enumModel != null) {
      return CodeBlock.of(
        "%T.literal(%T.%L)",
        Z,
        discriminatorTypeName,
        enumModel.requireTypeScriptEnumMemberNameForValue(discriminatorValue, "discriminator"),
      )
    }
    return CodeBlock.of("%T.literal(%S)", Z, discriminatorValue)
  }

  private fun plainDiscriminatedObjectSchemaCode(
    typeName: TypeName.Standard,
    discriminatorName: String,
    childModels: List<GeneratedModel>,
    schemaTypeName: TypeName.Standard? = null,
  ): CodeBlock {
    val variants =
      childModels.map { childModel ->
        val childTypeName = childModel.typeName(childModel.name.toUpperCamelCase())
        childTypeName.sibling("Schema") to (childModel.discriminatorValue ?: childModel.name)
      }
    val schemaFactory =
      if (schemaTypeName == null && variants.map { it.second }.toSet().size == variants.size) {
        "discriminatedUnion"
      } else {
        "union"
      }

    return CodeBlock
      .builder()
      .add("export const %LSchema", typeName.simpleName())
      .apply {
        schemaTypeName?.let { add(": %T", SCHEMA_LIKE.parameterized(it)) }
      }.add(" = %Q((runtime: %T) => {\n", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .apply {
        if (schemaFactory == "discriminatedUnion") {
          add("  return %T.discriminatedUnion(%S, [\n", Z, discriminatorName)
        } else if (schemaTypeName != null) {
          add("  const wireSchema = %T.union([\n", Z)
        } else {
          add("  return %T.union([\n", Z)
        }
      }.add(
        variants
          .map { (childSchemaTypeName, _) -> CodeBlock.of("    runtime.resolveSchema(%T)", childSchemaTypeName) }
          .joinToCode(",\n"),
      ).add("\n  ]);\n")
      .apply {
        if (schemaTypeName != null) {
          add("  return wireSchema as %T.ZodType<%T>;\n", Z, schemaTypeName)
        }
      }.add("});\n")
      .build()
  }

  private fun plainExternallyDiscriminatedObjectSchemaCode(
    typeName: TypeName.Standard,
    childModels: List<GeneratedModel>,
    schemaTypeName: TypeName.Standard? = null,
  ): CodeBlock {
    val variants =
      childModels.map { childModel ->
        val childTypeName = childModel.typeName(childModel.name.toUpperCamelCase())
        childTypeName.sibling("Schema")
      }

    return CodeBlock
      .builder()
      .add("export const %LSchema", typeName.simpleName())
      .apply {
        schemaTypeName?.let { add(": %T", SCHEMA_LIKE.parameterized(it)) }
      }.add(" = %Q((runtime: %T) => {\n", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .apply {
        if (variants.isEmpty()) {
          add("  return %T.looseObject({});\n", Z)
        } else {
          add("  return %T.union([\n", Z)
          add(
            variants
              .map { childSchemaTypeName -> CodeBlock.of("    runtime.resolveSchema(%T)", childSchemaTypeName) }
              .joinToCode(",\n"),
          )
          add("\n  ]);\n")
        }
      }.add("});\n")
      .build()
  }

  private fun discriminatedObjectSchemaCode(
    typeName: TypeName.Standard,
    discriminatorName: String,
    childModels: List<GeneratedModel>,
  ): CodeBlock {
    val variants =
      childModels.map { childModel ->
        val childTypeName = childModel.typeName(childModel.name.toUpperCamelCase())
        childTypeName.companionSchemaTypeName() to (childModel.discriminatorValue ?: childModel.name)
      }
    val schemaFactory =
      if (variants.map { it.second }.toSet().size == variants.size) {
        "discriminatedUnion"
      } else {
        "union"
      }

    return CodeBlock
      .builder()
      .add("export const %LSchema = %Q((runtime: %T) => {\n", typeName.simpleName(), DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .apply {
        if (schemaFactory == "discriminatedUnion") {
          add("  const wireSchema = %T.discriminatedUnion(%S, [\n", Z, discriminatorName)
        } else {
          add("  const wireSchema = %T.union([\n", Z)
        }
      }.add(
        variants
          .map { (childSchemaTypeName, _) -> CodeBlock.of("runtime.resolveSchema(%T)", childSchemaTypeName) }
          .joinToCode(",\n"),
      ).add("\n  ]);\n")
      .add("  return %T.codec(wireSchema, %T.instanceof(%T), {\n", Z, Z, typeName)
      .add("    decode: (value) => value as %T,\n", typeName)
      .add("    encode: (value) => value as z.infer<typeof wireSchema>,\n")
      .add("  });\n\n")
      .add("});\n")
      .build()
  }

  private fun unionSchemaCode(
    typeName: TypeName.Standard,
    model: GeneratedModel,
    schemaTypeName: TypeName.Standard? = null,
  ): CodeBlock {
    val aliasSchemas =
      model.aliases.map { alias ->
        CodeBlock
          .builder()
          .add("runtime.resolveSchema(")
          .add(typeRegistry.schemaInitializer(alias.typeName(typeName)))
          .add(")")
          .build()
      }

    return CodeBlock
      .builder()
      .add("export const %LSchema", typeName.simpleName())
      .apply {
        schemaTypeName?.let { add(": %T", SCHEMA_LIKE.parameterized(it)) }
      }.add(" = %Q((runtime: %T) => {\n", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add("  const wireSchema = %T.union([\n", Z)
      .add(aliasSchemas.joinToCode(",\n"))
      .add("\n  ]);\n")
      .apply {
        if (schemaTypeName != null) {
          add("  return wireSchema as %T.ZodType<%T>;\n", Z, schemaTypeName)
        } else {
          add("  return wireSchema;\n")
        }
      }.add("});\n")
      .build()
  }

  private fun externallyDiscriminatedObjectSchemaCode(
    typeName: TypeName.Standard,
    properties: List<GeneratedModelProperty>,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add("export const %LSchema = %Q((runtime: %T) => {\n", typeName.simpleName(), DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add("  const wireSchema = %T.looseObject({", Z)
      .apply {
        if (properties.isNotEmpty()) {
          add("\n")
          properties.forEachIndexed { idx, property ->
            add("    %S: ", property.serializationName ?: property.name)
            add(property.type.zodSchema(typeName, property.required, property.validation))
            if (idx < properties.lastIndex) {
              add(",")
            }
            add("\n")
          }
          add("  ")
        }
      }.add("});\n")
      .add("  return %T.codec(wireSchema, %T.instanceof(%T), {\n", Z, Z, typeName)
      .add(
        "    decode: () => { throw new TypeError(%P); },\n",
        "${typeName.simpleName()} requires external discriminator",
      ).add("    encode: (value) => value as unknown as Record<string, unknown>,\n")
      .add("  });\n")
      .add("});\n")
      .build()

  private fun plainObjectSchemaCode(
    serviceTypeName: TypeName.Standard,
    typeName: TypeName.Standard,
    properties: List<GeneratedModelProperty>,
    schemaTypeName: TypeName.Standard? = null,
    leafDiscriminator: Pair<GeneratedModelProperty, String>? = null,
  ): CodeBlock {
    val externalDiscriminatedPropertyPairs = properties.externalDiscriminatedPropertyPairs()
    var resolvedWireSchemaName = "wireSchema"

    return CodeBlock
      .builder()
      .add("export const %LSchema", typeName.simpleName())
      .apply {
        schemaTypeName?.let { add(": %T", SCHEMA_LIKE.parameterized(it)) }
      }.add(" = %Q((runtime: %T) => {\n", DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add("  const wireSchema = %T.looseObject({", Z)
      .apply {
        val wireProperties =
          buildList {
            leafDiscriminator?.let { (property, value) ->
              val discriminatorTypeName = property.type.typeName(serviceTypeName)
              add(property to discriminatorLiteralSchema(property, discriminatorTypeName, value))
            }
            addAll(
              properties.map { property ->
                property to
                  if (property.externalDiscriminator != null) {
                    externalDiscriminatedPropertySchema(property.type.typeName(serviceTypeName))
                  } else {
                    property.type.zodSchema(serviceTypeName, property.required, property.validation, typeName)
                  }
              },
            )
          }

        if (wireProperties.isNotEmpty()) {
          add("\n")
          wireProperties.forEachIndexed { idx, (property, propertySchema) ->
            add("    %S: ", property.serializationName ?: property.name)
            add(propertySchema)
            if (idx < wireProperties.lastIndex) {
              add(",")
            }
            add("\n")
          }
          add("  ")
        }
      }.add("});\n")
      .apply {
        resolvedWireSchemaName =
          applyExternalDiscriminatorConstraints(
            externalDiscriminatedPropertyPairs,
            "wireSchema",
            "wireSchema",
            serviceTypeName,
            this,
          )
      }.add("  return %L;\n", resolvedWireSchemaName)
      .add("});\n")
      .build()
  }

  private fun localObjectSchemaCode(
    serviceTypeName: TypeName.Standard,
    typeName: TypeName.Standard,
    properties: List<GeneratedModelProperty>,
    leafDiscriminator: Pair<GeneratedModelProperty, String>? = null,
  ): CodeBlock {
    val externalDiscriminatedPropertyPairs = properties.externalDiscriminatedPropertyPairs()
    var resolvedWireSchemaName = "wireSchema"

    return CodeBlock
      .builder()
      .add("export const %LSchema = %Q((runtime: %T) => {\n", typeName.simpleName(), DEFINE_SCHEMA, SCHEMA_RUNTIME)
      .add("  const wireSchema = %T.looseObject({", Z)
      .apply {
        val wireProperties =
          buildList {
            leafDiscriminator?.let { (property, value) ->
              val discriminatorTypeName = property.type.typeName(serviceTypeName)
              add(property to discriminatorLiteralSchema(property, discriminatorTypeName, value))
            }
            addAll(
              properties.map { property ->
                property to
                  if (property.externalDiscriminator != null) {
                    externalDiscriminatedPropertySchema(property.type.typeName(serviceTypeName))
                  } else {
                    property.type.zodSchema(serviceTypeName, property.required, property.validation)
                  }
              },
            )
          }

        if (wireProperties.isNotEmpty()) {
          add("\n")
          wireProperties.forEachIndexed { idx, (property, propertySchema) ->
            add("    %S: ", property.serializationName ?: property.name)
            add(propertySchema)
            if (idx < wireProperties.lastIndex) {
              add(",")
            }
            add("\n")
          }
          add("  ")
        }
      }.add("});\n")
      .apply {
        resolvedWireSchemaName =
          applyExternalDiscriminatorConstraints(
            externalDiscriminatedPropertyPairs,
            "wireSchema",
            "wireSchema",
            serviceTypeName,
            this,
          )
        add("  return %T.codec(%L, %T.instanceof(%T), {\n", Z, resolvedWireSchemaName, Z, typeName)
      }.apply {
        if (properties.isEmpty()) {
          add("    decode: () => new %T(),\n", typeName)
        } else {
          add("    decode: (value) => new %T({\n", typeName)
          properties.forEachIndexed { idx, property ->
            val propertyName = property.name.typeScriptIdentifierName
            val wireValue = CodeBlock.of("value[%S]", property.serializationName ?: property.name)
            val decodeValue =
              if (property.type.typeName(serviceTypeName).isSetType()) {
                CodeBlock.of("new %T(%L)", SET, wireValue)
              } else {
                wireValue
              }
            add(
              "      %N: %L,",
              propertyName,
              decodeValue,
            )
            if (idx < properties.lastIndex) {
              add("\n")
            }
          }
          add("\n    }),\n")
        }
      }.add("    encode: (value) => ({\n")
      .apply {
        val encodeProperties =
          buildList<Pair<String, CodeBlock>> {
            leafDiscriminator?.let { (property, value) ->
              val discriminatorTypeName = property.type.typeName(serviceTypeName)
              add(
                Pair(
                  property.serializationName ?: property.name,
                  discriminatorValueCode(property, discriminatorTypeName, value),
                ),
              )
            }
            addAll(
              properties.map { property ->
                val propertyName = property.name.typeScriptIdentifierName
                val propertyValue = CodeBlock.of("value.%N", propertyName)
                val encodeValue =
                  if (property.type.typeName(serviceTypeName).isSetType()) {
                    CodeBlock.of("%T.from(%L)", ARRAY, propertyValue)
                  } else {
                    propertyValue
                  }
                Pair(property.serializationName ?: property.name, encodeValue)
              },
            )
          }

        encodeProperties.forEachIndexed { idx, (wireName, encodeValue) ->
          add(
            "      %S: %L,",
            wireName,
            encodeValue,
          )
          if (idx < encodeProperties.lastIndex) {
            add("\n")
          }
        }
      }.add("\n    }) as unknown as z.infer<typeof %L>,\n", resolvedWireSchemaName)
      .add("  });\n")
      .add("});\n")
      .build()
  }

  private fun List<GeneratedModelProperty>.externalDiscriminatedPropertyPairs():
    List<Pair<GeneratedModelProperty, GeneratedModelProperty>> =
    mapNotNull { property ->
      val externalDiscriminator = property.externalDiscriminator ?: return@mapNotNull null
      val discriminatorProperty =
        firstOrNull { candidate ->
          candidate.name == externalDiscriminator ||
            candidate.serializationName == externalDiscriminator
        }
          ?: error("External discriminator '$externalDiscriminator' not found in object")
      property to discriminatorProperty
    }

  private fun GeneratedModel.externalDiscriminatorTypedTypeName(typeName: TypeName.Standard): TypeName.Standard? =
    typeName
      .nested("Typed")
      .takeIf { externalDiscriminatorTypedVariants(properties).isNotEmpty() }

  private fun GeneratedModel.externalDiscriminatorTypedCode(
    typeName: TypeName.Standard,
    properties: List<GeneratedModelProperty>,
  ): CodeBlock? {
    val variants = externalDiscriminatorTypedVariants(properties)
    if (variants.isEmpty()) {
      return null
    }

    return CodeBlock
      .builder()
      .add("export namespace %L {\n", typeName.simpleName())
      .add("  export type Typed =\n")
      .indent()
      .indent()
      .add(
        variants
          .map { variant ->
            CodeBlock.of(
              "%T & { %L: %S, %L: %T }",
              typeName,
              variant.discriminatorProperty.name.typeScriptIdentifierName,
              variant.discriminatorValue,
              variant.discriminatedProperty.name.typeScriptIdentifierName,
              variant.discriminatedTypeName,
            )
          }.joinToCode("\n| "),
      ).add(";\n")
      .unindent()
      .unindent()
      .add("}\n")
      .build()
  }

  private fun GeneratedModel.externalDiscriminatorTypedVariants(
    properties: List<GeneratedModelProperty>,
  ): List<ExternalDiscriminatorTypedVariant> {
    val (discriminatedProperty, discriminatorProperty) =
      properties
        .externalDiscriminatedPropertyPairs()
        .singleOrNull()
        ?: return listOf()
    val discriminatedModel = discriminatedProperty.type.modelOrNull(index) ?: return listOf()

    return discriminatedModel.discriminatorCaseModels().map { (mappedDiscriminator, childModel) ->
      val childTypeName = childModel.typeName(childModel.name.toUpperCamelCase())
      ExternalDiscriminatorTypedVariant(
        discriminatedProperty,
        discriminatorProperty,
        childModel.discriminatorValue ?: mappedDiscriminator ?: childModel.name,
        childTypeName,
      )
    }
  }

  private data class ExternalDiscriminatorTypedVariant(
    val discriminatedProperty: GeneratedModelProperty,
    val discriminatorProperty: GeneratedModelProperty,
    val discriminatorValue: String,
    val discriminatedTypeName: TypeName.Standard,
  )

  private fun applyExternalDiscriminatorConstraints(
    externalDiscriminatedProperties: List<Pair<GeneratedModelProperty, GeneratedModelProperty>>,
    baseWireSchemaName: String,
    wireSchemaName: String,
    serviceTypeName: TypeName.Standard,
    schemaCode: CodeBlock.Builder,
  ): String {
    if (externalDiscriminatedProperties.isEmpty()) {
      return wireSchemaName
    }

    if (externalDiscriminatedProperties.size == 1) {
      val constrainedSchemaName = "externallyConstrainedWireSchema1"
      val (discriminatedProperty, discriminatorProperty) = externalDiscriminatedProperties.first()
      schemaCode
        .add("  const %L = ", constrainedSchemaName)
        .add(
          externalDiscriminatorMergedSchema(
            discriminatedProperty,
            discriminatorProperty,
            serviceTypeName,
            baseWireSchemaName,
          ),
        ).add(";\n")
      return constrainedSchemaName
    }

    var constrainedSchemaName = wireSchemaName
    externalDiscriminatedProperties.forEachIndexed { idx, (discriminatedProperty, discriminatorProperty) ->
      val discriminatorSchemaName = "externalDiscriminatorSchema${idx + 1}"
      val nextConstrainedSchemaName = "externallyConstrainedWireSchema${idx + 1}"
      schemaCode
        .add("  const %L = ", discriminatorSchemaName)
        .add(externalDiscriminatorConstraintSchema(discriminatedProperty, discriminatorProperty, serviceTypeName))
        .add(";\n")
        .add(
          "  const %L = %T.intersection(%L, %L);\n",
          nextConstrainedSchemaName,
          Z,
          constrainedSchemaName,
          discriminatorSchemaName,
        )
      constrainedSchemaName = nextConstrainedSchemaName
    }
    return constrainedSchemaName
  }

  private fun externalDiscriminatorMergedSchema(
    discriminatedProperty: GeneratedModelProperty,
    discriminatorProperty: GeneratedModelProperty,
    serviceTypeName: TypeName.Standard,
    baseWireSchemaName: String,
  ): CodeBlock {
    val discriminatedPropertyTypeName = discriminatedProperty.type.typeName(serviceTypeName)
    val discriminatedModel =
      discriminatedProperty.type.modelOrNull(index)
        ?: error("External discriminator property must reference a model")
    val variants =
      discriminatedModel.externalDiscriminatorVariants(serviceTypeName) { childSchema, discriminatorValue ->
        CodeBlock
          .builder()
          .add("%T.looseObject({ ...%L.shape, %S: ", Z, baseWireSchemaName, discriminatorProperty.wireName)
          .add(
            discriminatorLiteralSchema(
              discriminatorProperty,
              discriminatorProperty.type.typeName(serviceTypeName),
              discriminatorValue,
            ),
          ).add(", %S: ", discriminatedProperty.wireName)
          .add("runtime.resolveSchema(")
          .add(childSchema)
          .add(")")
          .add(" })")
          .build()
      }

    if (variants.isEmpty()) {
      return CodeBlock.of("%L", baseWireSchemaName)
    }

    val optionalVariants =
      buildList {
        if (discriminatedPropertyTypeName.isUndefinable) {
          add(
            CodeBlock.of(
              "%T.looseObject({ ...%L.shape, %S: %T.undefined().optional() })",
              Z,
              baseWireSchemaName,
              discriminatedProperty.wireName,
              Z,
            ),
          )
        }
        if (discriminatedPropertyTypeName.isNullable) {
          add(
            CodeBlock.of(
              "%T.looseObject({ ...%L.shape, %S: %T.null() })",
              Z,
              baseWireSchemaName,
              discriminatedProperty.wireName,
              Z,
            ),
          )
        }
      }
    val discriminatorSchema =
      externalDiscriminatorSchema(discriminatorProperty.wireName, variants)

    return if (optionalVariants.isEmpty()) {
      discriminatorSchema
    } else {
      CodeBlock
        .builder()
        .add("%T.union([\n", Z)
        .add((listOf(discriminatorSchema) + optionalVariants).joinToCode(",\n"))
        .add("\n  ])")
        .build()
    }
  }

  private fun externalDiscriminatorConstraintSchema(
    discriminatedProperty: GeneratedModelProperty,
    discriminatorProperty: GeneratedModelProperty,
    serviceTypeName: TypeName.Standard,
  ): CodeBlock {
    val discriminatedPropertyTypeName = discriminatedProperty.type.typeName(serviceTypeName)
    val discriminatedModel =
      discriminatedProperty.type.modelOrNull(index)
        ?: error("External discriminator property must reference a model")
    val variants =
      discriminatedModel.externalDiscriminatorVariants(serviceTypeName) { childSchema, discriminatorValue ->
        CodeBlock
          .builder()
          .add("%T.object({ %S: ", Z, discriminatorProperty.wireName)
          .add(
            discriminatorLiteralSchema(
              discriminatorProperty,
              discriminatorProperty.type.typeName(serviceTypeName),
              discriminatorValue,
            ),
          ).add(", %S: ", discriminatedProperty.wireName)
          .add("runtime.resolveSchema(")
          .add(childSchema)
          .add(")")
          .add(" })")
          .build()
      }

    if (variants.isEmpty()) {
      return CodeBlock.of("%T.looseObject({})", Z)
    }

    val optionalVariants =
      buildList {
        if (discriminatedPropertyTypeName.isUndefinable) {
          add(CodeBlock.of("%T.object({ %S: %T.undefined().optional() })", Z, discriminatedProperty.wireName, Z))
        }
        if (discriminatedPropertyTypeName.isNullable) {
          add(CodeBlock.of("%T.object({ %S: %T.null() })", Z, discriminatedProperty.wireName, Z))
        }
      }
    val discriminatorSchema =
      externalDiscriminatorSchema(discriminatorProperty.wireName, variants)

    return if (optionalVariants.isEmpty()) {
      discriminatorSchema
    } else {
      CodeBlock
        .builder()
        .add("%T.union([\n", Z)
        .add((optionalVariants + discriminatorSchema).joinToCode(",\n"))
        .add("\n  ])")
        .build()
    }
  }

  private fun GeneratedModel.externalDiscriminatorVariants(
    serviceTypeName: TypeName.Standard,
    variantSchema: (CodeBlock, String) -> CodeBlock,
  ): List<Pair<String, CodeBlock>> =
    discriminatorCaseModels().map { (mappedDiscriminator, childModel) ->
      val childTypeName = childModel.typeName(childModel.name.toUpperCamelCase())
      val discriminatorValue = childModel.discriminatorValue ?: mappedDiscriminator ?: childModel.name
      discriminatorValue to variantSchema(typeRegistry.schemaInitializer(childTypeName), discriminatorValue)
    }

  private fun externalDiscriminatorSchema(
    discriminatorWireName: String,
    variants: List<Pair<String, CodeBlock>>,
  ): CodeBlock =
    if (variants.map { it.first }.toSet().size == variants.size) {
      CodeBlock
        .builder()
        .add("%T.discriminatedUnion(%S, [\n", Z, discriminatorWireName)
        .add(variants.map { it.second }.joinToCode(",\n"))
        .add("\n  ])")
        .build()
    } else {
      CodeBlock
        .builder()
        .add("%T.union([\n", Z)
        .add(variants.map { it.second }.joinToCode(",\n"))
        .add("\n  ])")
        .build()
    }

  private fun externalDiscriminatedPropertySchema(propertyTypeName: TypeName): CodeBlock {
    val schema =
      CodeBlock
        .builder()
        .add("%T.custom<%T>()", Z, propertyTypeName.nonUndefinable)
        .apply {
          if (propertyTypeName.isUndefinable) {
            add(".nullish()")
          } else if (propertyTypeName.isNullable) {
            add(".nullable()")
          }
        }
    return schema.build()
  }

  private fun GeneratedTypeRef.zodSchema(
    serviceTypeName: TypeName.Standard,
    required: Boolean,
    validation: Map<String, String> = mapOf(),
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock {
    val schema =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR ->
          formattedScalarTypeName()?.let { typeName -> runtimeResolvedSchema(typeName) } ?: when (name) {
            "boolean" -> CodeBlock.of("%T.boolean()", Z)
            "integer", "number" -> CodeBlock.of("%T.number()", Z)
            "nil" -> CodeBlock.of("%T.null()", Z)
            "string" -> CodeBlock.of("%T.string()", Z)
            else -> typeRegistry.schemaInitializer(typeName(serviceTypeName))
          }

        GeneratedTypeRef.Kind.ARRAY ->
          zodArraySchema(serviceTypeName)

        GeneratedTypeRef.Kind.MAP, GeneratedTypeRef.Kind.UNION ->
          runtimeResolvedSchema(typeName(serviceTypeName), lazyRefType)

        GeneratedTypeRef.Kind.NAMED ->
          modelOrNull(index)
            ?.aliasedTypeRef()
            ?.zodSchema(serviceTypeName, true)
            ?: runtimeResolvedSchema(typeName(serviceTypeName), lazyRefType)
      }

    val constrainedSchema = applyZodValidation(schema, validation)

    return when {
      !required ->
        CodeBlock
          .builder()
          .add(constrainedSchema)
          .add(".nullish()")
          .build()

      nullable ->
        CodeBlock
          .builder()
          .add(constrainedSchema)
          .add(".nullable()")
          .build()

      else -> constrainedSchema
    }
  }

  private fun GeneratedTypeRef.directZodSchema(
    required: Boolean,
    validation: Map<String, String>,
  ): CodeBlock? {
    val schema =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR ->
          when (name) {
            "boolean" -> CodeBlock.of("%T.boolean()", Z)
            "integer", "number" -> CodeBlock.of("%T.number()", Z)
            "nil" -> CodeBlock.of("%T.null()", Z)
            "string" -> CodeBlock.of("%T.string()", Z)
            else -> null
          }

        GeneratedTypeRef.Kind.ARRAY ->
          arguments.firstOrNull()?.directZodSchema(true, mapOf())?.let { elementSchema ->
            CodeBlock
              .builder()
              .add("%T.array(", Z)
              .add(elementSchema)
              .add(")")
              .build()
          }

        else -> null
      } ?: return null

    val constrainedSchema = applyZodValidation(schema, validation)

    return when {
      !required ->
        CodeBlock
          .builder()
          .add(constrainedSchema)
          .add(".nullish()")
          .build()

      nullable ->
        CodeBlock
          .builder()
          .add(constrainedSchema)
          .add(".nullable()")
          .build()

      else -> constrainedSchema
    }
  }

  private fun GeneratedTypeRef.applyZodValidation(
    schema: CodeBlock,
    validation: Map<String, String>,
  ): CodeBlock {
    var constrained = schema

    if (kind == GeneratedTypeRef.Kind.SCALAR && name == "string") {
      constrained =
        when (format?.lowercase()?.ifBlank { null }) {
          "email" -> constrained.appendSchemaCall("email()")
          "uuid" -> constrained.appendSchemaCall("uuid()")
          else -> constrained
        }
    }

    validation["minLength"]?.let { constrained = constrained.appendSchemaCall("min(%L)", it) }
    validation["maxLength"]?.let { constrained = constrained.appendSchemaCall("max(%L)", it) }
    validation["pattern"]
      ?.takeUnless { it.isBlank() || it == ".*" }
      ?.let { constrained = constrained.appendSchemaCall("regex(/%L/)", it) }

    validation["minimum"]?.let {
      constrained =
        if (validation["exclusiveMinimum"] == "true") {
          constrained.appendSchemaCall("gt(%L)", it)
        } else {
          constrained.appendSchemaCall("gte(%L)", it)
        }
    }
    validation["maximum"]?.let {
      constrained =
        if (validation["exclusiveMaximum"] == "true") {
          constrained.appendSchemaCall("lt(%L)", it)
        } else {
          constrained.appendSchemaCall("lte(%L)", it)
        }
    }
    validation["multipleOf"]?.let { constrained = constrained.appendSchemaCall("multipleOf(%L)", it) }
    validation["minItems"]?.let { constrained = constrained.appendSchemaCall("min(%L)", it) }
    validation["maxItems"]?.let { constrained = constrained.appendSchemaCall("max(%L)", it) }

    return constrained
  }

  private fun CodeBlock.appendSchemaCall(
    methodCall: String,
    vararg args: Any,
  ): CodeBlock =
    CodeBlock
      .builder()
      .add(this)
      .add(".")
      .add(methodCall, *args)
      .build()

  private fun runtimeResolvedSchema(
    typeName: TypeName,
    lazyRefType: TypeName.Standard? = null,
  ): CodeBlock =
    if (lazyRefType == null) {
      CodeBlock
        .builder()
        .add("runtime.resolveSchema(")
        .add(typeRegistry.schemaInitializer(typeName))
        .add(")")
        .build()
    } else {
      typeRegistry.runtimeSchemaForType(typeName, "runtime", lazyRefType)
    }

  private fun GeneratedTypeRef.zodArraySchema(serviceTypeName: TypeName.Standard): CodeBlock {
    val elementSchema =
      arguments.firstOrNull()?.zodSchema(serviceTypeName, true)
        ?: CodeBlock.of("%T.unknown()", Z)

    return CodeBlock
      .builder()
      .add("%T.array(", Z)
      .add(elementSchema)
      .add(")")
      .build()
  }

  private fun GeneratedOperation.operationFunction(
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): FunctionSpec {
    val signature = operationFunctionSignature(serviceTypeName, defaultOptionalParameters = true)
    val functionBuilder = signature.toBuilder()
    val parameterViews = typeScriptParameterViews()

    functionBuilder.addCode(
      transportCall(
        primarySuccessResponse(),
        responseBodyType(primarySuccessResponse(), serviceTypeName),
        parameterViews,
        serviceTypeName,
        typeBuilder,
      ),
    )

    return functionBuilder.build()
  }

  private fun GeneratedOperation.operationFunctionSignature(
    serviceTypeName: TypeName.Standard,
    defaultOptionalParameters: Boolean,
  ): FunctionSpec {
    val successResponse = primarySuccessResponse()
    val responseType = responseBodyType(successResponse, serviceTypeName)
    val wrappedReturnType =
      when (exchange) {
        GeneratedExchange.REQUEST -> TRANSPORT_REQUEST.parameterized(transportTypeVariable)
        GeneratedExchange.RESPONSE -> RESPONSE
        null ->
          if (streaming == null) {
            when {
              isNullableOperation ->
                NULLABLE_OPERATION.parameterized(requestBodyType(serviceTypeName), responseType, transportTypeVariable)
              requestBody.isTypeScriptStreamingRequestBody ->
                STREAMING_OPERATION.parameterized(responseType, transportTypeVariable)
              else ->
                OPERATION.parameterized(requestBodyType(serviceTypeName), responseType, transportTypeVariable)
            }
          } else {
            responseType
          }
      }
    val functionReturnType =
      when (streaming?.kind) {
        GeneratedStreaming.Kind.EVENT_SOURCE -> EVENT_SOURCE
        GeneratedStreaming.Kind.EVENT_STREAM -> parameterizedType(ASYNC_ITERABLE, responseType)
        else -> if (exchange == null) wrappedReturnType else parameterizedType(PROMISE, wrappedReturnType)
      }
    val functionBuilder =
      FunctionSpec
        .builder(id.typeScriptIdentifierName)
        .returns(functionReturnType)
    val parameterViews = typeScriptParameterViews()

    parameterViews
      .filterNot { parameter -> parameter.isConstant }
      .forEach { parameter ->
        val parameterSpec = parameter.parameterSpec(serviceTypeName)
        functionBuilder.addParameter(
          if (defaultOptionalParameters) {
            methodParameter(parameterSpec)
          } else {
            parameterSpec
          },
        )
      }

    requestBody?.let { body ->
      val bodyParameter =
        ParameterSpec
          .builder("body", requestBodyType(serviceTypeName))
          .build()
      functionBuilder.addParameter(
        if (defaultOptionalParameters) {
          methodParameter(bodyParameter)
        } else {
          bodyParameter
        },
      )
    }

    if (streaming != null || exchange != null) {
      functionBuilder.addParameter("signal", ABORT_SIGNAL, true)
    }

    return functionBuilder.build()
  }

  private fun GeneratedOperation.requestBodyType(serviceTypeName: TypeName.Standard): TypeName =
    if (requestBody.isTypeScriptStreamingRequestBody) {
      STREAMING_BODY
    } else {
      requestBody?.type?.typeName(serviceTypeName)?.nonUndefinable ?: VOID
    }

  private fun responseBodyType(
    response: GeneratedResponse?,
    serviceTypeName: TypeName.Standard,
  ): TypeName =
    response
      ?.takeUnless { it.isNoContent }
      ?.type
      ?.typeName(serviceTypeName)
      ?.nonUndefinable
      ?: VOID

  private val GeneratedOperation.isNullableOperation: Boolean
    get() = nullify != null && exchange == null && streaming == null

  private fun io.outfoxx.sunday.generator.ir.GeneratedProblem.typeName(): TypeName.Standard {
    val simpleName = name.toUpperCamelCase()
    return typeRegistry.generatedTypeName(simpleName, simpleName.camelCaseToKebabCase())
  }

  private fun GeneratedOperation.typeScriptParameterViews() =
    mutableSetOf<String>().let { allocatedNames ->
      operationParameterViews(
        identifierName = { parameter -> parameter.name.typeScriptIdentifierName },
        allocateName = { _, proposedName ->
          var name = proposedName
          while (!allocatedNames.add(name)) {
            name += "_"
          }
          name
        },
      )
    }

  private fun GeneratedOperation.transportCall(
    response: GeneratedResponse?,
    returnType: TypeName,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): CodeBlock {
    val parameterSchemaProperties = parameterSchemaProperties(parameters, serviceTypeName, typeBuilder)

    if (streaming != null) {
      return streamingTransportCall(
        response,
        returnType,
        parameters,
        parameterSchemaProperties,
        serviceTypeName,
        typeBuilder,
      )
    }

    if (exchange == null) {
      return operationCall(response, returnType, parameters, parameterSchemaProperties, serviceTypeName, typeBuilder)
    }

    val requestBodyTypeProperty =
      requestBody?.takeUnless { it.isTypeScriptStreamingRequestBody }?.type?.let { type ->
        val propertyName = "${id.typeScriptIdentifierName}BodyType"
        val typeName = type.typeName(serviceTypeName)
        addTypeProperty(
          typeBuilder,
          propertyName,
          typeName,
          initializer = type.schemaInitializer(serviceTypeName, typeName),
        )
        propertyName
      }
    val factoryMethod =
      when (exchange) {
        GeneratedExchange.REQUEST -> "transportRequest"
        GeneratedExchange.RESPONSE -> "transportResponse"
      }

    return CodeBlock
      .builder()
      .add("%[return this.transport.%L(\n", factoryMethod)
      .add(spec(returnType, requestBodyTypeProperty, response, parameters, parameterSchemaProperties))
      .add("%]\n)")
      .apply {
        if (exchange == GeneratedExchange.REQUEST) {
          add(" as %T<%T>", PROMISE, TRANSPORT_REQUEST.parameterized(transportTypeVariable))
        }
      }.add(";\n")
      .build()
  }

  private fun GeneratedOperation.operationCall(
    response: GeneratedResponse?,
    returnType: TypeName,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): CodeBlock {
    val requestBodyTypeProperty =
      requestBody?.takeUnless { it.isTypeScriptStreamingRequestBody }?.type?.let { type ->
        val propertyName = "${id.typeScriptIdentifierName}BodyType"
        val typeName = type.typeName(serviceTypeName)
        addTypeProperty(
          typeBuilder,
          propertyName,
          typeName,
          initializer = type.schemaInitializer(serviceTypeName, typeName),
        )
        propertyName
      }
    val returnTypeProperty =
      response?.takeUnless { it.isNoContent }?.type?.let { type ->
        val propertyName = "${id.typeScriptIdentifierName}ReturnType"
        val typeName = type.typeName(serviceTypeName)
        addTypeProperty(
          typeBuilder,
          propertyName,
          typeName,
          initializer = type.schemaInitializer(serviceTypeName, typeName),
        )
        propertyName
      }

    val createOperation =
      when {
        isNullableOperation -> CREATE_NULLABLE_OPERATION
        requestBody.isTypeScriptStreamingRequestBody -> CREATE_STREAMING_OPERATION
        else -> CREATE_OPERATION
      }

    val builder = CodeBlock.builder()
    builder.add("%[return %Q(this.transport, {\n", createOperation)
    builder.add("request: ")
    builder.add(
      spec(
        returnType,
        requestBodyTypeProperty,
        response,
        parameters,
        parameterSchemaProperties,
        includeSignal = false,
      ),
    )
    if (returnTypeProperty != null) {
      builder.add(",\nresponseType: %L", returnTypeProperty)
    }
    nullify?.takeIf { isNullableOperation }?.let { nullify ->
      builder.add("%]\n}, {\n")
      builder.indent()
      builder.add(nullifySpecCode(nullify))
      builder.unindent()
      builder.add(");\n")
    } ?: builder.add("%]\n});\n")

    return builder.build()
  }

  private fun nullifySpecCode(nullify: GeneratedNullify): CodeBlock {
    val problemMatchers =
      nullify.problems
        .mapNotNull { problem -> problem.problemOrNull(index) }
        .map { problem -> CodeBlock.of("(problem) => problem instanceof %T", problem.typeName()) }

    return CodeBlock
      .builder()
      .add("statuses: [")
      .add(nullify.statuses.map { CodeBlock.of("%L", it) }.joinToCode(", "))
      .add("],\n")
      .add("problemTypes: [")
      .add(problemMatchers.joinToCode(", "))
      .add("]\n}")
      .build()
  }

  private fun GeneratedOperation.streamingTransportCall(
    response: GeneratedResponse?,
    returnType: TypeName,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): CodeBlock =
    when (streaming?.kind) {
      GeneratedStreaming.Kind.EVENT_STREAM ->
        eventStreamTransportCall(
          response,
          returnType,
          parameters,
          parameterSchemaProperties,
          serviceTypeName,
          typeBuilder,
        )

      else ->
        CodeBlock
          .builder()
          .add("%[return this.transport.eventSource(\n")
          .add(spec(returnType, null, response, parameters, parameterSchemaProperties))
          .add("%]\n);\n")
          .build()
    }

  private fun GeneratedOperation.eventStreamTransportCall(
    response: GeneratedResponse?,
    returnType: TypeName,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): CodeBlock =
    when (streaming?.eventMode) {
      GeneratedStreaming.EventMode.DISCRIMINATED ->
        discriminatedEventStreamTransportCall(
          response,
          returnType,
          parameters,
          parameterSchemaProperties,
          serviceTypeName,
          typeBuilder,
        )

      else -> {
        val eventTypeProperty = "${id.typeScriptIdentifierName}EventType"
        response?.type?.let { type ->
          addTypeProperty(
            typeBuilder,
            eventTypeProperty,
            returnType,
            initializer = type.schemaInitializer(serviceTypeName, returnType),
          )
        } ?: addTypeProperty(typeBuilder, eventTypeProperty, returnType)
        CodeBlock
          .builder()
          .add("%[return this.transport.eventStream<%T>(\n", returnType)
          .add(spec(returnType, null, response, parameters, parameterSchemaProperties))
          .add(",\n")
          .add("(decoder, event, id, data) => decoder.decodeText(data, %L)%]\n);\n", eventTypeProperty)
          .build()
      }
    }

  private fun GeneratedOperation.discriminatedEventStreamTransportCall(
    response: GeneratedResponse?,
    returnType: TypeName,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): CodeBlock {
    val eventTypes =
      response
        ?.type
        ?.flattenedUnionTypes()
        .orEmpty()
        .filter { type -> type.kind == GeneratedTypeRef.Kind.NAMED }

    val eventTypeProperties =
      eventTypes.mapIndexed { idx, type ->
        val typeName = type.typeName(serviceTypeName)
        val propertyName = "${id.typeScriptIdentifierName}EventType${idx + 1}"
        addTypeProperty(typeBuilder, propertyName, typeName)
        type.eventDiscriminatorValue(typeName) to propertyName
      }

    return CodeBlock
      .builder()
      .add("%[return this.transport.eventStream<%T>(\n", returnType)
      .add(spec(returnType, null, response, parameters, parameterSchemaProperties))
      .add(",\n")
      .add(discriminatedEventDecoder(eventTypeProperties))
      .add("%]\n);\n")
      .build()
  }

  private fun discriminatedEventDecoder(eventTypeProperties: List<Pair<String, String>>): CodeBlock =
    CodeBlock
      .builder()
      .add("(decoder, event, id, data, logger) => {\n")
      .add("  switch (event) {\n")
      .apply {
        eventTypeProperties.forEach { (discriminatorValue, propertyName) ->
          add("  case %S: return decoder.decodeText(data, %L);\n", discriminatorValue, propertyName)
        }
      }.add(
        $$"""
        |  default:
        |    logger?.error?.(`Unknown event type, ignoring event: event=${event}`);
        |    return undefined;
        |  }
        |},
        """.trimMargin(),
      ).build()

  private fun GeneratedTypeRef.eventDiscriminatorValue(typeName: TypeName): String =
    modelOrNull(index)?.discriminatorValue
      ?: (typeName as? TypeName.Standard)?.simpleName()
      ?: typeName.toString()

  private fun GeneratedOperation.spec(
    returnType: TypeName,
    requestBodyTypeProperty: String?,
    response: GeneratedResponse?,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
    includeSignal: Boolean = true,
  ): CodeBlock {
    val builder = CodeBlock.builder()
    val headerParameters = parameters.withLocation(GeneratedParameter.Location.HEADER)
    val contentTypeParameter = headerParameters.contentTypeParameterOrNull()

    builder.add("{%>")
    builder.add("\nmethod: %S,", requestMethod())
    builder.add("\npathTemplate: %S,", path)

    parameters.withLocation(GeneratedParameter.Location.PATH).takeIf { it.isNotEmpty() }?.let { pathParameters ->
      builder.add("\n")
      builder.add(parametersBlock("pathParameters", pathParameters, parameterSchemaProperties))
      builder.add(",")
    }

    parameters.withLocation(GeneratedParameter.Location.QUERY).takeIf { it.isNotEmpty() }?.let { queryParameters ->
      builder.add("\n")
      builder.add(parametersBlock("queryParameters", queryParameters, parameterSchemaProperties))
      builder.add(",")
    }

    if (requestBody != null) {
      builder.add("\nbody: body,")
      requestBodyTypeProperty?.let { builder.add("\nbodyType: %L,", it) }
      builder.add("\ncontentTypes: %L,", requestBody.contentTypes(contentTypeParameter))
    }

    val acceptTypes = response?.mediaTypes?.ifEmpty { defaultMediaTypes } ?: emptyList()
    if (acceptTypes.isNotEmpty() && returnType != VOID) {
      builder.add("\nacceptTypes: %L,", acceptTypes(acceptTypes))
    }

    headerParameters
      .filterNot { parameter -> parameter == contentTypeParameter }
      .takeIf { it.isNotEmpty() }
      ?.let { filteredHeaderParameters ->
        builder.add("\n")
        builder.add(parametersBlock("headers", filteredHeaderParameters, parameterSchemaProperties))
        builder.add(",")
      }

    if (includeSignal) {
      builder.add("\nsignal: signal,")
    }
    builder.add("%<\n}")

    return builder.build()
  }

  private fun GeneratedOperation.requestMethod(): String =
    when (method.uppercase()) {
      "SUBSCRIBE" -> "GET"
      "PUBLISH" -> "POST"
      else -> method.uppercase()
    }

  private fun parametersBlock(
    fieldName: String,
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    parameterSchemaProperties: Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String>,
  ): CodeBlock {
    val builder = CodeBlock.builder().add("%L: {%>\n", fieldName)

    parameters.forEachIndexed { idx, parameter ->
      val value = parameter.requestValue(parameterSchemaProperties[parameter])
      if (
        parameter.wireName == parameter.name &&
        parameter.source.serializationName == null &&
        parameter.defaultValue == null &&
        !parameter.isConstant &&
        !parameter.source.hasDirectZodValidation()
      ) {
        builder.add("%L", value)
      } else {
        builder.add("%L: %L", parameter.wireName.quotedIfNotTypeScriptIdentifier, value)
      }

      if (idx < parameters.size - 1) {
        builder.add(",\n")
      }
    }

    builder.add("%<\n}")
    return builder.build()
  }

  private fun GeneratedOperation.parameterSchemaProperties(
    parameters: List<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>,
    serviceTypeName: TypeName.Standard,
    typeBuilder: ClassSpec.Builder,
  ): Map<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter, String> =
    parameters
      .filter { parameter -> !parameter.isConstant && parameter.source.hasDirectZodValidation() }
      .associateWith { parameter ->
        val propertyName = "${id.typeScriptIdentifierName}${parameter.name.toUpperCamelCase()}ParameterType"
        val parameterTypeName = parameter.source.type.typeName(serviceTypeName)
        val schema =
          parameter.source.type.directZodSchema(parameter.source.required, parameter.source.validation)
            ?: typeRegistry.schemaInitializer(parameterTypeName)
        addTypeProperty(typeBuilder, propertyName, parameterTypeName, schema, schemaLikeType = null)
        propertyName
      }

  private fun io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter.requestValue(
    schemaPropertyName: String?,
  ): CodeBlock {
    val value =
      when {
        isConstant -> literal(constantValue)
        defaultValue != null -> CodeBlock.of("%N ?? %L", name, literal(defaultValue))
        else -> CodeBlock.of("%N", name)
      }

    return schemaPropertyName?.let { CodeBlock.of("%N.parse(%L)", it, value) } ?: value
  }

  private fun GeneratedParameter.hasDirectZodValidation(): Boolean =
    validation.isNotEmpty() ||
      (
        type.kind == GeneratedTypeRef.Kind.SCALAR &&
          type.name == "string" &&
          type.format?.lowercase()?.ifBlank { null } in setOf("email", "uuid")
      )

  private fun Iterable<io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter>.contentTypeParameterOrNull():
    io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter? =
    firstOrNull { parameter ->
      parameter.location == GeneratedParameter.Location.HEADER &&
        parameter.wireName.equals("Content-Type", ignoreCase = true)
    }

  private fun io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter.parameterSpec(
    serviceTypeName: TypeName.Standard,
  ): ParameterSpec =
    ParameterSpec
      .builder(name, source.type.typeName(serviceTypeName).optionalParameterType(source))
      .build()

  private fun addTypeProperty(
    typeBuilder: ClassSpec.Builder,
    propertyName: String,
    typeName: TypeName,
    initializer: CodeBlock = typeRegistry.schemaInitializer(typeName),
    schemaLikeType: TypeName? = typeName,
  ) {
    val typeCodeBuilder = typeBuilder.tags[CodeBlock.Builder::class] as CodeBlock.Builder
    typeCodeBuilder.add("%[const %N", propertyName)
    schemaLikeType?.let { typeCodeBuilder.add(": %T", SCHEMA_LIKE.parameterized(it)) }
    typeCodeBuilder.add(" = ")
    typeCodeBuilder.add(initializer)
    typeCodeBuilder.add(";\n%]")
  }

  private fun GeneratedTypeRef.typeName(serviceTypeName: TypeName.Standard): TypeName {
    val model = modelOrNull(index)
    val typeName =
      when (kind) {
        GeneratedTypeRef.Kind.SCALAR ->
          formattedScalarTypeName() ?: when (name) {
            "boolean" -> BOOLEAN
            "integer", "number" -> NUMBER
            "nil" -> VOID
            "string" -> STRING
            "file" -> ARRAY_BUFFER
            else -> TypeName.implicit("unknown")
          }

        GeneratedTypeRef.Kind.NAMED -> {
          if (model?.isFreeformMapModel() == true) {
            return recordType(STRING, TypeName.implicit("unknown"))
          }

          val aliasedTypeRef = model?.aliasedTypeRef()
          val simpleName = name.toUpperCamelCase()
          if (aliasedTypeRef != null) {
            aliasedTypeRef.typeName(serviceTypeName)
          } else if (scope != null) {
            serviceTypeName.nested(simpleName)
          } else if (model != null) {
            val modelTypeName = model.typeName(simpleName)
            if (model.isProblemModel()) {
              model.externalDiscriminatorTypedTypeName(modelTypeName) ?: modelTypeName
            } else {
              modelTypeName
            }
          } else {
            typeRegistry.generatedTypeName(simpleName, simpleName.camelCaseToKebabCase())
          }
        }

        GeneratedTypeRef.Kind.ARRAY ->
          parameterizedType(
            if (collection == GeneratedCollectionKind.SET) SET else ARRAY,
            arguments.firstOrNull()?.typeName(serviceTypeName) ?: TypeName.implicit("unknown"),
          )

        GeneratedTypeRef.Kind.MAP ->
          recordType(STRING, arguments.firstOrNull()?.typeName(serviceTypeName) ?: TypeName.implicit("unknown"))

        GeneratedTypeRef.Kind.UNION ->
          TypeName.unionType(*arguments.map { it.typeName(serviceTypeName) }.toTypedArray())
      }

    return if (nullable) {
      TypeName.unionType(typeName, TypeName.NULL)
    } else {
      typeName
    }
  }

  private fun GeneratedTypeRef.schemaTypeName(serviceTypeName: TypeName.Standard): TypeName {
    val model = modelOrNull(index)
    return if (
      kind == GeneratedTypeRef.Kind.NAMED &&
      model != null &&
      scope == null &&
      model.aliasedTypeRef() == null
    ) {
      model.typeName(name.toUpperCamelCase())
    } else {
      typeName(serviceTypeName)
    }
  }

  private fun GeneratedTypeRef.schemaInitializer(
    serviceTypeName: TypeName.Standard,
    outputTypeName: TypeName,
  ): CodeBlock {
    val schemaTypeName = schemaTypeName(serviceTypeName)
    return if (schemaTypeName == outputTypeName) {
      typeRegistry.schemaInitializer(schemaTypeName)
    } else if (schemaTypeName is TypeName.Standard) {
      CodeBlock.of("%T as unknown as %T", schemaTypeName.sibling("Schema"), SCHEMA_LIKE.parameterized(outputTypeName))
    } else {
      val initializer = typeRegistry.schemaInitializer(schemaTypeName)
      CodeBlock.of("%L as unknown as %T", initializer, SCHEMA_LIKE.parameterized(outputTypeName))
    }
  }

  private fun GeneratedTypeRef.formattedScalarTypeName(): TypeName.Standard? =
    when (format?.lowercase()?.ifBlank { null } ?: name.lowercase()) {
      "date",
      "full-date",
      "date-only",
      -> LOCAL_DATE

      "time",
      "partial-time",
      "time-only",
      -> LOCAL_TIME

      "datetime-only",
      "date-time-only",
      -> LOCAL_DATETIME

      "datetime",
      "date-time",
      -> OFFSET_DATETIME

      "uri",
      "url",
      "uri-reference",
      "iri",
      "iri-reference",
      -> URL_TYPE

      "byte", "binary" -> ARRAY_BUFFER
      else -> null
    }

  private fun GeneratedModel.typeName(simpleName: String): TypeName.Standard {
    val target = target("typescript", "typescript")
    val typeSimpleName = target?.typeName ?: simpleName
    val moduleName =
      target?.modelModuleName
        ?: api.target("typescript", "typescript")?.modelModuleName
        ?: target?.moduleName
        ?: api.target("typescript", "typescript")?.moduleName
    if (moduleName == null) {
      nested
        ?.enclosedIn
        ?.modelOrNull(index)
        ?.let { enclosingModel ->
          val enclosingTypeName = enclosingModel.typeName(enclosingModel.name.toUpperCamelCase())
          return enclosingTypeName.nested(nested.name?.toUpperCamelCase() ?: simpleName)
        }
    }

    val modulePath =
      when {
        moduleName == null -> simpleName.camelCaseToKebabCase()
        moduleName.endsWith(".ts") -> moduleName.removeSuffix(".ts")
        else -> "$moduleName/${simpleName.camelCaseToKebabCase()}"
      }

    return typeRegistry.generatedTypeName(typeSimpleName, modulePath)
  }

  private fun GeneratedModel.isFreeformMapModel(): Boolean =
    kind == GeneratedModel.Kind.OBJECT &&
      properties.isEmpty() &&
      patternProperties.isEmpty() &&
      !externallyDiscriminated &&
      discriminatorMappings.isEmpty() &&
      childModels().isEmpty()

  private fun GeneratedModel.aliasedTypeRef(): GeneratedTypeRef? =
    when (kind) {
      GeneratedModel.Kind.SCALAR_ALIAS -> aliases.firstOrNull()
      GeneratedModel.Kind.ARRAY ->
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.ARRAY,
          name = name,
          arguments = aliases.take(1),
          collection = collection,
        )
      GeneratedModel.Kind.MAP ->
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.MAP,
          name = name,
          arguments = aliases.take(1),
        )
      else -> null
    }

  private fun TypeName.Standard.sibling(name: String): TypeName.Standard =
    when (val symbol = base) {
      is SymbolSpec.Imported ->
        TypeName.standard(SymbolSpec.importsName(symbol.value + name, symbol.source))

      is SymbolSpec.Implicit ->
        TypeName.standard(SymbolSpec.implicit(symbol.value + name))
    }

  private fun TypeName.Standard.companionSchemaTypeName(): TypeName.Standard =
    when (val symbol = base) {
      is SymbolSpec.Imported -> {
        val schemaSource =
          if (symbol.source.endsWith(".js")) {
            symbol.source.removeSuffix(".js") + "-schema.js"
          } else {
            symbol.source + "-schema"
          }
        TypeName.standard(SymbolSpec.importsName(symbol.value + "Schema", schemaSource))
      }

      is SymbolSpec.Implicit ->
        TypeName.standard(SymbolSpec.implicit(symbol.value + "Schema"))
    }

  private val GeneratedModelProperty.wireName: String
    get() = serializationName ?: name

  private fun TypeName.optionalParameterType(parameter: GeneratedParameter): TypeName =
    if (parameter.defaultValue != null || !parameter.required) {
      undefinable
    } else {
      this
    }

  private val GeneratedPayload?.isTypeScriptStreamingRequestBody: Boolean
    get() = this?.streaming?.enabledFor(GenerationMode.Client) == true

  private fun TypeName.modelPropertyType(property: io.outfoxx.sunday.generator.ir.GeneratedModelProperty): TypeName =
    if (!property.required) {
      nullable.undefinable
    } else {
      this
    }

  private fun TypeName.isSetType(): Boolean = (nonUndefinable as? TypeName.Parameterized)?.rawType == SET

  private fun methodParameter(parameter: ParameterSpec): ParameterSpec {
    val builder = parameter.toBuilder()

    val type = parameter.type
    if (type.isOptional) {
      builder.defaultValue(CodeBlock.of("%L", if (type.isUndefinable) TypeName.UNDEFINED else TypeName.NULL))
    }

    return builder.build()
  }

  private fun GeneratedPayload.contentTypes(
    contentTypeParameter: io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter?,
  ): CodeBlock =
    contentTypeParameter?.selectedContentTypes()
      ?: explicitContentTypes(defaultMediaTypes)
        ?.let(::mediaTypesArray)
      ?: CodeBlock.of("this.defaultContentTypes")

  private fun io.outfoxx.sunday.generator.ir.emit.GeneratedOperationParameter.selectedContentTypes(): CodeBlock? {
    val enumModel =
      type
        .modelOrNull(index)
        ?.takeIf { model -> model.kind == GeneratedModel.Kind.ENUM }
        ?: return null
    if (enumModel.values.filterIsInstance<String>().isEmpty()) {
      return null
    }
    return CodeBlock.of("[%T.from(%N)]", MEDIA_TYPE, name)
  }

  private fun acceptTypes(mediaTypes: List<String>): CodeBlock =
    if (mediaTypes == defaultMediaTypes) {
      CodeBlock.of("this.defaultAcceptTypes")
    } else {
      mediaTypesArray(mediaTypes)
    }

  private fun mediaTypesArray(mimeTypes: List<String>): CodeBlock = mediaTypesArray(*mimeTypes.toTypedArray())

  private fun mediaTypesArray(vararg mimeTypes: String): CodeBlock =
    mimeTypes
      .distinct()
      .map {
        mediaType(it)
      }.let { mediaTypeCodes ->
        mediaTypeCodes.joinToCode(prefix = "[", suffix = "]")
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
      "application/x-www-form-urlencoded" -> CodeBlock.of("%T.WWWFormURLEncoded", MEDIA_TYPE)
      "application/problem+json" -> CodeBlock.of("%T.Problem", MEDIA_TYPE)
      "application/x-x509-ca-cert" -> CodeBlock.of("%T.X509CACert", MEDIA_TYPE)
      "application/x-x509-user-cert" -> CodeBlock.of("%T.X509UserCert", MEDIA_TYPE)
      "application/json-patch+json" -> CodeBlock.of("%T.JsonPatch", MEDIA_TYPE)
      "application/merge-patch+json" -> CodeBlock.of("%T.MergePatch", MEDIA_TYPE)
      else -> CodeBlock.of("%T.from(%S)", MEDIA_TYPE, value)
    }

  private fun GeneratedModel.typeScriptEnumMemberNameForValue(value: String): String? =
    typeScriptEnumEntries().singleOrNull { entry -> entry.value == value }?.name

  private fun GeneratedModel.requireTypeScriptEnumMemberNameForValue(
    value: String,
    usage: String,
  ): String =
    typeScriptEnumMemberNameForValue(value)
      ?: genError(
        "TypeScript enum '$name' $usage value '$value' does not match any enum value. " +
          "Fix the $usage value or the enum definition.",
      )

  private fun GeneratedModel.typeScriptEnumEntries(): List<TypeScriptEnumEntry> =
    typeScriptEnumEntriesByModel.getOrPut(this) {
      createTypeScriptEnumEntries()
    }

  private fun GeneratedModel.createTypeScriptEnumEntries(): List<TypeScriptEnumEntry> {
    if (enumValueNames.isNotEmpty() && enumValueNames.size != values.size) {
      genError(
        "TypeScript enum '$name' has ${enumValueNames.size} enum value names for ${values.size} enum values. " +
          "Fix x-enum-varnames so it has one entry per enum value.",
      )
    }

    val entries =
      values.mapIndexed { index, value ->
        val memberName =
          if (enumValueNames.isNotEmpty()) {
            enumValueNames[index].trim()
          } else {
            typeScriptEnumMemberName(value)
          }
        validateTypeScriptEnumMemberName(
          memberName,
          value,
          enumValueNames.getOrNull(index),
        )
        TypeScriptEnumEntry(memberName, value)
      }

    entries
      .groupBy { entry -> entry.name }
      .filterValues { duplicates -> duplicates.size > 1 }
      .forEach { (memberName, duplicates) ->
        genError(
          "TypeScript enum '$name' member name '$memberName' is used for multiple values " +
            duplicates.joinToString(", ") { entry -> "'${entry.value}'" } +
            ". Add x-enum-varnames to disambiguate them.",
        )
      }

    return entries
  }

  private fun GeneratedModel.validateTypeScriptEnumMemberName(
    memberName: String,
    value: String,
    explicitName: String?,
  ) {
    if (!typeScriptEnumMemberIdentifierRegex.matches(memberName) || memberName in typeScriptReservedWords) {
      if (explicitName != null) {
        genError(
          "TypeScript enum '$name' x-enum-varnames entry '$explicitName' for value '$value' " +
            "maps to invalid member name '$memberName'. Fix x-enum-varnames with a valid " +
            "TypeScript enum member name.",
        )
      }
      genError(
        "TypeScript enum '$name' value '$value' maps to invalid member name '$memberName'. " +
          "Add x-enum-varnames with a valid TypeScript enum member name.",
      )
    }
  }

  private data class TypeScriptEnumEntry(
    val name: String,
    val value: String,
  )

  private fun GeneratedModel.typeScriptEnumMemberName(value: String): String =
    value.typeScriptEnumConstantName().ifBlank {
      genError(
        "TypeScript enum '$name' value '$value' contains no valid identifier characters. " +
          "Add x-enum-varnames with a valid TypeScript enum member name.",
      )
    }

  private fun String.typeScriptEnumConstantName(): String =
    split(enumNameDelimiter)
      .filter { part -> part.isNotBlank() }
      .joinToString("") { part ->
        part.normalizedEnumSegment().replaceFirstChar { char -> char.titlecase() }
      }

  private fun String.normalizedEnumSegment(): String =
    if (any { it.isLetter() } && all { !it.isLetter() || it.isUpperCase() }) {
      lowercase()
    } else {
      this
    }

  private fun literal(value: Any?): CodeBlock =
    when (value) {
      null -> CodeBlock.of("null")
      is String -> CodeBlock.of("%S", value)
      is Number -> CodeBlock.of("%L", value)
      is Boolean -> CodeBlock.of("%L", value)
      else -> CodeBlock.of("%S", value.toString())
    }

  private companion object {
    val asyncApiOperationMethods = setOf("PUBLISH", "SUBSCRIBE")
    val requiredBaseProblemProperties = setOf("type", "title", "status")
    val optionalBaseProblemProperties = setOf("detail", "instance")
    val enumNameDelimiter = Regex("[^A-Za-z0-9]+")
    val typeScriptEnumMemberIdentifierRegex = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
    val typeScriptReservedWords =
      setOf(
        "break",
        "case",
        "catch",
        "class",
        "const",
        "continue",
        "debugger",
        "default",
        "delete",
        "do",
        "else",
        "enum",
        "export",
        "extends",
        "false",
        "finally",
        "for",
        "function",
        "if",
        "import",
        "in",
        "instanceof",
        "new",
        "null",
        "return",
        "super",
        "switch",
        "this",
        "throw",
        "true",
        "try",
        "typeof",
        "var",
        "void",
        "while",
        "with",
        "as",
        "implements",
        "interface",
        "let",
        "package",
        "private",
        "protected",
        "public",
        "static",
        "yield",
      )
  }
}
