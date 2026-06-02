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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.net.URI

/**
 * Converts AsyncAPI documents into Sunday generated API IR fragments without using AMF.
 */
class AsyncApiToGeneratedApi(
  private val options: GeneratedApiIrOptions = GeneratedApiIrOptions(),
) {

  /** Converts an AsyncAPI source document into a generated API IR composition fragment. */
  fun convertFragment(sourceUri: URI): GeneratedApiFragment {
    val sourceDocument = AsyncApiSourceDocument.read(sourceUri.toString())
    activeSourceDocument = sourceDocument
    try {
      val localModels = linkedMapOf<String, GeneratedModel>()
      val servers = sourceDocument.servers().map { server -> server.generatedServer(sourceDocument) }
      val serviceFragments = sourceDocument.serviceFragments(sourceUri.toString(), localModels)
      val services = serviceFragments.map { fragment -> fragment.service }
      val generatedApi =
        GeneratedApi(
          name = sourceDocument.title() ?: "API",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, sourceUri.toString()),
          services = services,
          models = localModels.values.toList(),
          auth = sourceDocument.auth(),
          protocol = GeneratedProtocol(servers = servers).takeUnless { it == GeneratedProtocol() },
        )

      return GeneratedApiFragment(
        api = generatedApi,
        apiId = sourceDocument.compositionApiIdentity(sourceUri.toString()),
        serviceIdentities = serviceFragments.associate { fragment -> fragment.service.name to fragment.identity },
        operationIdentities =
          serviceFragments
            .flatMap { fragment -> fragment.operationIdentities }
            .toMap(),
        modelIdentities =
          localModels.values.associate { model ->
            model.name to GeneratedIdentity.native(model.name.replaceFirstChar { char -> char.lowercase() })
          },
      )
    } finally {
      activeSourceDocument = null
    }
  }

  private fun serviceName(apiName: String): String =
    apiName
      .removeSuffix(" API")
      .split(Regex("\\s+"))
      .joinToString("") { it.toUpperCamelCase() } + "Service"

  private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

  private fun String.toServiceLabel(): String =
    Regex("[A-Za-z0-9]+")
      .findAll(this)
      .map { match -> match.value }
      .firstOrNull()
      ?.toUpperCamelCase()
      ?: "API"

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

  private fun AsyncApiSourceDocument.serviceFragments(
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<ServiceFragment> =
    operationFragments(location, localModels)
      .groupBy { fragment -> fragment.seed }
      .mapNotNull { (seed, fragments) ->
        val operations = fragments.map { fragment -> fragment.operation.operation }
        operations
          .takeIf { it.isNotEmpty() }
          ?.let {
            val serviceName = serviceName(seed.serviceLabel)
            ServiceFragment(
              service =
                GeneratedService(
                  name = serviceName,
                  baseUri = fragments.firstNotNullOfOrNull { fragment -> fragment.channel.serverUrl(this) },
                  operations = operations,
                  media =
                    GeneratedMedia(
                      response =
                        operations
                          .flatMap { operation -> operation.responses.flatMap { response -> response.mediaTypes } }
                          .distinct(),
                    ),
                ),
              identity = seed.identity,
              operationIdentities =
                fragments.map { fragment ->
                  GeneratedOperationIdentityKey(serviceName, fragment.operation.operation.id) to
                    fragment.operation.identity
                },
            )
          }
      }

  private fun AsyncApiSourceDocument.operationFragments(
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<ServiceOperationFragment> =
    channels().flatMap { channel ->
      listOfNotNull(
        channel.operation("publish", location, localModels)?.let { operation ->
          ServiceOperationFragment(channel.serviceIdentitySeed(null), channel, operation)
        },
        channel.operation("subscribe", location, localModels)?.let { operation ->
          ServiceOperationFragment(channel.serviceIdentitySeed(null), channel, operation)
        },
      )
    } +
      operations().mapNotNull { operation ->
        operation.operation(location, localModels)?.let { generatedOperation ->
          ServiceOperationFragment(operation.serviceIdentitySeed(), operation.channel, generatedOperation)
        }
      }

  private fun AsyncApiChannel.operation(
    method: String,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): OperationFragment? {
    val operation = operation(method) ?: return null
    val message = operation.message?.resolvedMessage() ?: return null
    val payload = message.payload ?: return null
    val publish = method == "publish"
    val payloadType = schemaTypeRef(payload, message.modelName(), location, localModels)
    val mediaTypes = listOfNotNull(message.contentType)
    val operationId = operation.generatedOperationId()
    val examples = message.examples.mapNotNull { example -> example.generatedExample() }
    val protocol =
      GeneratedProtocol(
        bindings =
          bindings(GeneratedProtocolBinding.Kind.CHANNEL) +
            operation.bindings(GeneratedProtocolBinding.Kind.OPERATION),
      ).takeUnless { it == GeneratedProtocol() }

    return OperationFragment(
      operation =
        GeneratedOperation(
          id = operationId,
          method = method.uppercase(),
          path = address,
          parameters =
            pathParameters(location, localModels) +
              if (publish) {
                message.headers(location, localModels)
              } else {
                listOf()
              },
          requestBody =
            if (publish) {
              GeneratedPayload(type = payloadType, mediaTypes = mediaTypes, examples = examples)
            } else {
              null
            },
          responses =
            if (publish) {
              listOf()
            } else {
              listOf(
                GeneratedResponse(
                  type = payloadType,
                  mediaTypes = mediaTypes,
                  headers = message.headers(location, localModels),
                  examples = examples,
                ),
              )
            },
          exchange = GeneratedExchange.REQUEST.takeIf { publish },
          streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM).takeUnless { publish },
          auth = sourceAuth(security + operation.security),
          protocol = protocol,
          documentation = GeneratedDocumentation(summary = operation.summary, description = operation.description),
        ),
      identity = operation.compositionOperationIdentity(operationId),
    )
  }

  private fun AsyncApiDocumentOperation.operation(
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): OperationFragment? {
    val message = message ?: return null
    val payload = message.payload ?: return null
    val method = method ?: return null
    val publish = method == "publish"
    val payloadType = schemaTypeRef(payload, message.modelName(), location, localModels)
    val mediaTypes = listOfNotNull(message.contentType)
    val operationId = generatedOperationId()
    val examples = message.examples.mapNotNull { example -> example.generatedExample() }
    val protocol =
      GeneratedProtocol(
        bindings =
          channel.bindings(GeneratedProtocolBinding.Kind.CHANNEL) +
            bindings(GeneratedProtocolBinding.Kind.OPERATION),
      ).takeUnless { it == GeneratedProtocol() }

    return OperationFragment(
      operation =
        GeneratedOperation(
          id = operationId,
          method = method.uppercase(),
          path = channel.address,
          parameters =
            channel.pathParameters(location, localModels) +
              if (publish) {
                message.headers(location, localModels)
              } else {
                listOf()
              },
          requestBody =
            if (publish) {
              GeneratedPayload(type = payloadType, mediaTypes = mediaTypes, examples = examples)
            } else {
              null
            },
          responses =
            if (publish) {
              listOf()
            } else {
              listOf(
                GeneratedResponse(
                  type = payloadType,
                  mediaTypes = mediaTypes,
                  headers = message.headers(location, localModels),
                  examples = examples,
                ),
              )
            },
          exchange = GeneratedExchange.REQUEST.takeIf { publish },
          streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM).takeUnless { publish },
          auth = sourceAuth(channel.security + security),
          protocol = protocol,
          documentation = GeneratedDocumentation(summary = summary, description = description),
        ),
      identity = compositionOperationIdentity(operationId),
    )
  }

  private fun AsyncApiChannel.pathParameters(
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<GeneratedParameter> =
    source
      .mapValue("parameters")
      .orEmpty()
      .mapNotNull { (name, value) ->
        val wireName = name as? String ?: return@mapNotNull null
        val parameter = value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val generatedName = wireName.toLowerCamelCase()
        val schema = parameter.mapValue("schema") ?: parameter.schemaValue()
        GeneratedParameter(
          name = generatedName,
          location = GeneratedParameter.Location.PATH,
          type =
            schema
              ?.let { schemaTypeRef(it, generatedName.toUpperCamelCase(), location, localModels) }
              ?: GeneratedTypeRef.scalar("string"),
          required = true,
          serializationName = wireName.takeUnless { it == generatedName },
          encoding = GeneratedParameterEncoding(style = "simple"),
          validation = schema?.let(::validation).orEmpty(),
          documentation =
            GeneratedDocumentation(description = parameter["description"] as? String)
              .takeUnless { it == GeneratedDocumentation() },
        )
      }

  private fun AsyncApiMessage.headers(
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<GeneratedParameter> =
    headers
      ?.mapValue("properties")
      .orEmpty()
      .mapNotNull { (name, schema) ->
        val wireName = name as? String ?: return@mapNotNull null
        val schemaMap = schema as? Map<*, *> ?: return@mapNotNull null
        val generatedName = wireName.toLowerCamelCase()
        GeneratedParameter(
          name = generatedName,
          location = GeneratedParameter.Location.HEADER,
          type = schemaTypeRef(schemaMap, generatedName.toUpperCamelCase(), location, localModels),
          required = wireName in headers.requiredNames,
          serializationName = wireName.takeUnless { it == generatedName },
          validation = validation(schemaMap),
        )
      }

  private fun schemaTypeRef(
    schema: Map<*, *>,
    nameHint: String?,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedTypeRef {
    schema.refName()?.let { name -> return materializedNamedTypeRef(name, location, localModels) }

    val oneOf = schema.schemaList("oneOf").ifEmpty { schema.schemaList("anyOf") }
    if (oneOf.isNotEmpty()) {
      val nonNullBranches = oneOf.filterNot { branch -> branch.scalarTypeName() == "nil" }
      if (nonNullBranches.size == 1 && nonNullBranches.size != oneOf.size) {
        return schemaTypeRef(nonNullBranches.single(), nameHint, location, localModels).copy(nullable = true)
      }
      return GeneratedTypeRef(
        kind = GeneratedTypeRef.Kind.UNION,
        name = "union",
        arguments = nonNullBranches.map { branch -> schemaTypeRef(branch, null, location, localModels) },
      )
    }

    return when (schema.scalarTypeName()) {
      "object" ->
        mapTypeRef(schema, location, localModels)
          ?: nameHint?.let { name -> materializedInlineModel(name, schema, location, localModels) }
          ?: GeneratedTypeRef.scalar("object")

      "array" ->
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.ARRAY,
          name = "array",
          arguments =
            listOf(
              schema
                .mapValue("items")
                ?.let { items -> schemaTypeRef(items, null, location, localModels) }
                ?: GeneratedTypeRef.scalar("any"),
            ),
          collection = GeneratedCollectionKind.SET.takeIf { schema["uniqueItems"] == true },
        )

      else -> GeneratedTypeRef.scalar(schema.scalarTypeName(), format = schema["format"] as? String)
    }
  }

  private fun materializedNamedTypeRef(
    name: String,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedTypeRef {
    if (!localModels.containsKey(name)) {
      val schema = currentSourceDocument.schemas()[name]
      if (schema != null) {
        localModels[name] =
          GeneratedModel(
            name = name,
            kind = GeneratedModel.Kind.OBJECT,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, location),
          )
        localModels[name] = generatedModel(name, schema, location, localModels)
      }
    }
    return GeneratedTypeRef.named(name)
  }

  private fun materializedInlineModel(
    name: String,
    schema: Map<*, *>,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedTypeRef {
    if (!localModels.containsKey(name)) {
      localModels[name] = generatedModel(name, schema, location, localModels)
    }
    return GeneratedTypeRef.named(name)
  }

  private fun generatedModel(
    name: String,
    schema: Map<*, *>,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedModel {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, location)
    val oneOf = schema.schemaList("oneOf").ifEmpty { schema.schemaList("anyOf") }
    if (oneOf.isNotEmpty()) {
      val discriminator = schema.discriminatorName()
      return GeneratedModel(
        name = name,
        kind = GeneratedModel.Kind.UNION,
        source = source,
        aliases =
          oneOf
            .filterNot { branch ->
              branch.scalarTypeName() == "nil"
            }.map { branch ->
              schemaTypeRef(branch, null, location, localModels)
            },
        discriminator = discriminator,
        discriminatorMappings = schema.discriminatorMappings(discriminator, oneOf),
        documentation = documentation(description = schema["description"] as? String),
      )
    }

    if (schema.enumValues().isNotEmpty()) {
      return GeneratedModel(
        name = name,
        kind = GeneratedModel.Kind.ENUM,
        source = source,
        values = schema.enumValues().map { value -> value.toString() },
        documentation = documentation(description = schema["description"] as? String),
      )
    }

    return GeneratedModel(
      name = name,
      kind = GeneratedModel.Kind.OBJECT,
      source = source,
      properties =
        schema
          .mapValue("properties")
          .orEmpty()
          .mapNotNull { (propertyName, propertySchema) ->
            val wireName = propertyName as? String ?: return@mapNotNull null
            val propertySchemaMap = propertySchema as? Map<*, *> ?: return@mapNotNull null
            val generatedName = wireName.toLowerCamelCase()
            val propertyType = schemaTypeRef(propertySchemaMap, generatedName.toUpperCamelCase(), location, localModels)
            GeneratedModelProperty(
              name = generatedName,
              type = propertyType,
              required = wireName in schema.requiredNames,
              serializationName = wireName.takeUnless { it == generatedName },
              externalDiscriminator = schema.externalDiscriminatorName(wireName, propertyType, localModels),
              validation = validation(propertySchemaMap),
              documentation = documentation(description = propertySchemaMap["description"] as? String),
            )
          },
      documentation = documentation(description = schema["description"] as? String),
    )
  }

  private fun documentation(
    summary: String? = null,
    description: String? = null,
  ): GeneratedDocumentation? =
    GeneratedDocumentation(summary = summary, description = description).takeUnless { it == GeneratedDocumentation() }

  private fun Map<*, *>.externalDiscriminatorName(
    propertyWireName: String,
    propertyType: GeneratedTypeRef,
    localModels: Map<String, GeneratedModel>,
  ): String? {
    if (propertyType.kind != GeneratedTypeRef.Kind.NAMED) {
      return null
    }

    val model = localModels[propertyType.name] ?: return null
    if (model.kind != GeneratedModel.Kind.UNION || model.discriminatorMappings.isEmpty()) {
      return null
    }

    val discriminator = model.discriminator ?: return null
    if (discriminator == propertyWireName) {
      return null
    }

    return discriminator
      .takeIf { discriminatorName ->
        mapValue("properties")
          ?.containsKey(discriminatorName) == true
      }?.toLowerCamelCase()
  }

  private val currentSourceDocument: AsyncApiSourceDocument
    get() = activeSourceDocument ?: error("No active AsyncAPI source document")

  private var activeSourceDocument: AsyncApiSourceDocument? = null

  private fun AsyncApiServer.generatedServer(sourceDocument: AsyncApiSourceDocument): GeneratedServer =
    GeneratedServer(
      name = name,
      url = url ?: "",
      protocol = protocol,
      protocolVersion = protocolVersion,
      auth = sourceAuth(security),
      bindings = sourceDocument.serverBindings(name),
      documentation = GeneratedDocumentation(description = description).takeUnless { it == GeneratedDocumentation() },
    )

  private fun AsyncApiSourceDocument.auth(): GeneratedAuth? =
    sourceAuth(security() + servers().flatMap { server -> server.security })

  private fun sourceAuth(securityRequirements: List<Map<*, *>>): GeneratedAuth? {
    val requirements =
      securityRequirements
        .map { requirement -> GeneratedSecurityRequirement(schemes = requirement.keys.filterIsInstance<String>()) }
        .filter { requirement -> requirement.schemes.isNotEmpty() }
    val schemes = requirements.flatMap { requirement -> requirement.schemes }.distinct()
    val securitySchemes =
      currentSourceDocument
        .securitySchemes()
        .mapNotNull { (name, value) ->
          val scheme = value
          GeneratedSecurityScheme(
            name = name,
            type = scheme["type"] as? String,
            scheme = scheme["scheme"] as? String,
            bearerFormat = scheme["bearerFormat"] as? String,
            documentation =
              GeneratedDocumentation(
                summary = scheme["summary"] as? String,
                description = scheme["description"] as? String,
              ).takeUnless { documentation -> documentation == GeneratedDocumentation() },
          )
        }.filter { scheme -> scheme.name in schemes }
    return GeneratedAuth(
      schemes = schemes,
      requirements = requirements,
      securitySchemes = securitySchemes,
    ).takeUnless { it == GeneratedAuth() }
  }

  private fun AsyncApiChannel.serviceIdentitySeed(operation: AsyncApiOperation?): ServiceIdentitySeed {
    val explicitService =
      operation
        ?.extension("x-sunday-service")
        ?.trimToNull()
        ?: extension("x-sunday-service")?.trimToNull()
    if (explicitService != null) {
      return ServiceIdentitySeed(explicitService, GeneratedIdentity.explicit(normalizedCompositionId(explicitService)))
    }

    if (options.deriveServicesFromTags) {
      val taggedService = operation?.serviceTagName() ?: taggedServiceLabel()
      if (taggedService != null) {
        return ServiceIdentitySeed(taggedService, GeneratedIdentity.native(normalizedCompositionId(taggedService)))
      }
    }

    val serviceLabel = name.substringBefore('.').toServiceLabel()
    return ServiceIdentitySeed(serviceLabel, GeneratedIdentity.native(normalizedCompositionId(serviceLabel)))
  }

  private fun AsyncApiChannel.taggedServiceLabel(): String? {
    val tagNames =
      listOfNotNull(operation("publish"), operation("subscribe"))
        .mapNotNull { operation -> operation.serviceTagName() }
        .distinct()

    require(tagNames.size <= 1) {
      "AsyncAPI channel '$name' has operations with different service tags (${tagNames.joinToString()}). " +
        "Add x-sunday-service to select one generated service explicitly."
    }

    return tagNames.singleOrNull()
  }

  private fun AsyncApiOperation.serviceTagName(): String? = tags.firstOrNull()?.trimToNull()

  private fun AsyncApiDocumentOperation.serviceIdentitySeed(): ServiceIdentitySeed {
    val explicitService =
      extension("x-sunday-service")
        ?.trimToNull()
        ?: channel.extension("x-sunday-service")?.trimToNull()
    if (explicitService != null) {
      return ServiceIdentitySeed(explicitService, GeneratedIdentity.explicit(normalizedCompositionId(explicitService)))
    }

    if (options.deriveServicesFromTags) {
      val taggedService = serviceTagName() ?: channel.taggedServiceLabel()
      if (taggedService != null) {
        return ServiceIdentitySeed(taggedService, GeneratedIdentity.native(normalizedCompositionId(taggedService)))
      }
    }

    val serviceLabel = channel.name.substringBefore('.').toServiceLabel()
    return ServiceIdentitySeed(serviceLabel, GeneratedIdentity.native(normalizedCompositionId(serviceLabel)))
  }

  private fun AsyncApiDocumentOperation.serviceTagName(): String? = tags.firstOrNull()?.trimToNull()

  private fun AsyncApiOperation.generatedOperationId(): String =
    extension("x-sunday-operationId")?.trimToNull() ?: operationId

  private fun AsyncApiOperation.compositionOperationIdentity(operationId: String): GeneratedIdentity =
    extension("x-sunday-operationId")
      ?.trimToNull()
      ?.let { GeneratedIdentity.explicit(it) }
      ?: GeneratedIdentity.native(operationId)

  private fun AsyncApiDocumentOperation.generatedOperationId(): String =
    extension("x-sunday-operationId")?.trimToNull() ?: operationId

  private fun AsyncApiDocumentOperation.compositionOperationIdentity(operationId: String): GeneratedIdentity =
    extension("x-sunday-operationId")
      ?.trimToNull()
      ?.let { GeneratedIdentity.explicit(it) }
      ?: GeneratedIdentity.native(operationId)

  private fun AsyncApiChannel.serverUrl(sourceDocument: AsyncApiSourceDocument): String? =
    serverNames.firstOrNull()?.let { name ->
      sourceDocument.servers().firstOrNull { server -> server.name == name }?.url
    }

  private fun AsyncApiMessage.modelName(): String = name ?: "Message"

  private fun AsyncApiMessage.resolvedMessage(): AsyncApiMessage? {
    val refName = source.refName() ?: return this
    return currentSourceDocument.messages()[refName]?.let(::AsyncApiMessage)
  }

  private fun mapTypeRef(
    schema: Map<*, *>,
    location: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedTypeRef? {
    val additionalProperties = schema["additionalProperties"] ?: return null
    if (schema.mapValue("properties")?.isNotEmpty() == true) {
      return null
    }
    val valueType =
      if (additionalProperties == true) {
        GeneratedTypeRef.scalar("any")
      } else {
        (additionalProperties as? Map<*, *>)?.let { schemaTypeRef(it, null, location, localModels) }
          ?: GeneratedTypeRef.scalar("any")
      }
    return GeneratedTypeRef(
      kind = GeneratedTypeRef.Kind.MAP,
      name = "map",
      arguments = listOf(valueType),
    )
  }

  private fun validation(schema: Map<*, *>): Map<String, String> =
    linkedMapOf<String, String>().apply {
      listOf("minLength", "maxLength", "pattern", "minimum", "maximum", "minItems", "maxItems")
        .forEach { key -> schema[key]?.let { value -> put(key, value.toString()) } }
      schema["uniqueItems"]?.takeIf { it == true }?.let { put("uniqueItems", it.toString()) }
    }

  private val Map<*, *>?.requiredNames: List<String>
    get() =
      this
        ?.get("required")
        .let { required -> required as? List<*> }
        .orEmpty()
        .filterIsInstance<String>()

  private fun Map<*, *>.refName(): String? =
    (this["\$ref"] as? String)
      ?.substringAfterLast('/')
      ?.takeIf { it.isNotBlank() }

  private fun Map<*, *>.schemaList(name: String): List<Map<*, *>> =
    (this[name] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

  private fun Map<*, *>.enumValues(): List<Any> = (this["enum"] as? List<*>).orEmpty().filterNotNull()

  private fun Map<*, *>.scalarTypeName(): String =
    when (val type = this["type"]) {
      "string" -> "string"
      "boolean" -> "boolean"
      "integer" -> "integer"
      "number" -> "number"
      "array" -> "array"
      "object" -> "object"
      "null" -> "nil"
      is List<*> ->
        type
          .filterIsInstance<String>()
          .filterNot { it == "null" }
          .singleOrNull()
          ?: "any"
      else ->
        when {
          containsKey("properties") || containsKey("additionalProperties") -> "object"
          containsKey("items") -> "array"
          containsKey("enum") || (this["format"] as? String)?.isNotBlank() == true -> "string"
          else -> "any"
        }
    }

  private fun Map<*, *>.discriminatorName(): String? =
    when (val discriminator = this["discriminator"]) {
      is String -> discriminator
      is Map<*, *> -> discriminator["propertyName"] as? String
      else -> null
    }

  private fun Map<*, *>.discriminatorMappings(
    discriminatorName: String?,
    branches: List<Map<*, *>>,
  ): Map<String, GeneratedTypeRef> {
    val explicitMappings =
      (this["discriminator"] as? Map<*, *>)
        ?.mapValue("mapping")
        .orEmpty()
        .mapNotNull { (value, ref) ->
          val discriminatorValue = value as? String ?: return@mapNotNull null
          val modelName = (ref as? String)?.substringAfterLast('/') ?: return@mapNotNull null
          discriminatorValue to GeneratedTypeRef.named(modelName)
        }.toMap()
    if (explicitMappings.isNotEmpty()) {
      return explicitMappings
    }

    discriminatorName ?: return mapOf()
    return branches
      .mapNotNull { branch ->
        val modelName = branch.refName() ?: return@mapNotNull null
        val schema = currentSourceDocument.schemas()[modelName] ?: return@mapNotNull null
        val discriminatorSchema = schema.mapValue("properties")?.mapValue(discriminatorName) ?: return@mapNotNull null
        val value =
          discriminatorSchema["const"] as? String
            ?: discriminatorSchema.enumValues().singleOrNull() as? String
            ?: return@mapNotNull null
        value to GeneratedTypeRef.named(modelName)
      }.toMap()
  }

  private fun GeneratedExampleSource.generatedExample(): GeneratedExample? =
    GeneratedExample(
      name = name,
      value = payload,
      documentation = GeneratedDocumentation(summary = summary, description = description),
    ).takeUnless { example ->
      example.name == null &&
        example.value == null &&
        example.documentation == null
    }

  private fun Map<*, *>.listValue(name: String): List<Map<*, *>>? =
    when (val value = this[name]) {
      is List<*> -> value.filterIsInstance<Map<*, *>>()
      else -> null
    }

  private fun Map<*, *>.mapValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

  private fun Map<*, *>.schemaValue(): Map<*, *>? =
    takeIf { map ->
      map.containsKey("\$ref") ||
        map.containsKey("type") ||
        map.containsKey("oneOf") ||
        map.containsKey("anyOf") ||
        map.containsKey("allOf") ||
        map.containsKey("properties") ||
        map.containsKey("items") ||
        map.containsKey("additionalProperties")
    }

  private fun Map<*, *>.protocolBindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
    entries.mapNotNull { (key, value) ->
      val protocol = key as? String ?: return@mapNotNull null
      val values = value as? Map<*, *> ?: return@mapNotNull null
      GeneratedProtocolBinding(
        kind = kind,
        protocol = protocol,
        values = values.stringKeyMap(),
      )
    }

  private fun Map<*, *>.stringKeyMap(): Map<String, Any?> =
    entries
      .mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        name to value.generatedValue()
      }.toMap()

  private fun Any?.generatedValue(): Any? =
    when (this) {
      is Map<*, *> -> stringKeyMap()
      is List<*> -> map { value -> value.generatedValue() }
      else -> this
    }

  private data class ServiceIdentitySeed(
    val serviceLabel: String,
    val identity: GeneratedIdentity,
  )

  private data class ServiceFragment(
    val service: GeneratedService,
    val identity: GeneratedIdentity,
    val operationIdentities: List<Pair<GeneratedOperationIdentityKey, GeneratedIdentity>>,
  )

  private data class OperationFragment(
    val operation: GeneratedOperation,
    val identity: GeneratedIdentity,
  )

  private data class ServiceOperationFragment(
    val seed: ServiceIdentitySeed,
    val channel: AsyncApiChannel,
    val operation: OperationFragment,
  )

  private class AsyncApiSourceDocument(
    private val source: Map<*, *>,
  ) {

    fun title(): String? = source.mapValue("info")?.get("title") as? String

    fun security(): List<Map<*, *>> = source.listValue("security").orEmpty()

    fun schemas(): Map<String, Map<*, *>> =
      source
        .mapValue("components")
        ?.mapValue("schemas")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val schemaName = name as? String ?: return@mapNotNull null
          val schema = value as? Map<*, *> ?: return@mapNotNull null
          schemaName to schema
        }.toMap()

    fun securitySchemes(): Map<String, Map<*, *>> =
      source
        .mapValue("components")
        ?.mapValue("securitySchemes")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val schemeName = name as? String ?: return@mapNotNull null
          val scheme = value as? Map<*, *> ?: return@mapNotNull null
          schemeName to scheme
        }.toMap()

    fun messages(): Map<String, Map<*, *>> =
      source
        .mapValue("components")
        ?.mapValue("messages")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val messageName = name as? String ?: return@mapNotNull null
          val message = value as? Map<*, *> ?: return@mapNotNull null
          messageName to message
        }.toMap()

    fun servers(): List<AsyncApiServer> =
      source
        .mapValue("servers")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val serverName = name as? String ?: return@mapNotNull null
          val server = value as? Map<*, *> ?: return@mapNotNull null
          AsyncApiServer(
            name = serverName,
            url = server["url"] as? String,
            protocol = server["protocol"] as? String,
            protocolVersion = server["protocolVersion"] as? String,
            description = server["description"] as? String,
            security = server.listValue("security").orEmpty(),
          )
        }

    fun channels(): List<AsyncApiChannel> =
      source
        .mapValue("channels")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val channelName = name as? String ?: return@mapNotNull null
          val channel = value as? Map<*, *> ?: return@mapNotNull null
          AsyncApiChannel(
            name = channelName,
            address = channel["address"] as? String ?: channelName,
            source = channel,
            serverNames =
              channel["servers"]
                .let { servers ->
                  servers as? List<*>
                }.orEmpty()
                .filterIsInstance<String>(),
            security = channel.listValue("security").orEmpty(),
          )
        }

    fun operations(): List<AsyncApiDocumentOperation> {
      val channels = channels().associateBy { channel -> channel.name }
      return source
        .mapValue("operations")
        .orEmpty()
        .mapNotNull { (name, value) ->
          val operationId = name as? String ?: return@mapNotNull null
          val operation = value as? Map<*, *> ?: return@mapNotNull null
          val channelName =
            operation
              .mapValue("channel")
              ?.refName()
              ?: return@mapNotNull null
          val channel = channels[channelName] ?: return@mapNotNull null
          AsyncApiDocumentOperation(
            operationId = operation["operationId"] as? String ?: operationId,
            action = operation["action"] as? String,
            source = operation,
            channel = channel,
            summary = operation["summary"] as? String,
            description = operation["description"] as? String,
            tags = operation.tags(),
            message =
              operation
                .listValue("messages")
                .orEmpty()
                .firstNotNullOfOrNull { message -> resolveMessage(message) }
                ?: operation.mapValue("message")?.let(::resolveMessage),
            security = operation.listValue("security").orEmpty(),
          )
        }
    }

    fun resolveMessage(source: Map<*, *>): AsyncApiMessage? {
      val ref = source["\$ref"] as? String
      if (ref != null) {
        val parts = ref.removePrefix("#/").split("/")
        val resolved =
          when {
            parts.size == 3 && parts[0] == "components" && parts[1] == "messages" ->
              messages()[parts[2]]

            parts.size == 4 && parts[0] == "channels" && parts[2] == "messages" ->
              this.source
                .mapValue("channels")
                ?.mapValue(parts[1])
                ?.mapValue("messages")
                ?.mapValue(parts[3])

            else -> null
          }
        return resolved?.let(::resolveMessage)
      }
      return AsyncApiMessage(source)
    }

    fun compositionApiIdentity(location: String): GeneratedIdentity =
      extension("x-sunday-apiId")
        ?.trimToNull()
        ?.let { GeneratedIdentity.explicit(it) }
        ?: title()
          ?.trimToNull()
          ?.let { GeneratedIdentity.native(normalizedCompositionId(it)) }
        ?: GeneratedIdentity.generated(
          normalizedCompositionId(location.substringAfterLast('/').substringBeforeLast('.')),
        )

    private fun extension(name: String): String? = source[name] as? String

    fun messageExamples(
      channelName: String,
      method: String,
    ): List<GeneratedExample> =
      source
        .mapValue("channels")
        ?.mapValue(channelName)
        ?.mapValue(method)
        ?.mapValue("message")
        ?.listValue("examples")
        ?.mapNotNull { example -> example.generatedExample() }
        .orEmpty()

    fun serverBindings(serverName: String?): List<GeneratedProtocolBinding> =
      serverName
        ?.let { name ->
          source
            .mapValue("servers")
            ?.mapValue(name)
            ?.mapValue("bindings")
            ?.protocolBindings(GeneratedProtocolBinding.Kind.SERVER)
        }.orEmpty()

    fun channelBindings(channelName: String): List<GeneratedProtocolBinding> =
      source
        .mapValue("channels")
        ?.mapValue(channelName)
        ?.mapValue("bindings")
        ?.protocolBindings(GeneratedProtocolBinding.Kind.CHANNEL)
        .orEmpty()

    fun operationBindings(
      channelName: String,
      method: String,
    ): List<GeneratedProtocolBinding> =
      source
        .mapValue("channels")
        ?.mapValue(channelName)
        ?.mapValue(method)
        ?.mapValue("bindings")
        ?.protocolBindings(GeneratedProtocolBinding.Kind.OPERATION)
        .orEmpty()

    private fun Map<*, *>.listValue(name: String): List<Map<*, *>>? =
      when (val value = this[name]) {
        is List<*> -> value.filterIsInstance<Map<*, *>>()
        else -> null
      }

    private fun Map<*, *>.mapValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

    private fun Map<*, *>.refName(): String? =
      (this["\$ref"] as? String)
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.tags(): List<String> =
      (this["tags"] as? List<*>)
        .orEmpty()
        .mapNotNull { tag ->
          when (tag) {
            is String -> tag
            is Map<*, *> -> tag["name"] as? String
            else -> null
          }
        }

    private fun Map<*, *>.protocolBindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
      entries.mapNotNull { (key, value) ->
        val protocol = key as? String ?: return@mapNotNull null
        val values = value as? Map<*, *> ?: return@mapNotNull null
        GeneratedProtocolBinding(
          kind = kind,
          protocol = protocol,
          values = values.stringKeyMap(),
        )
      }

    private fun Map<*, *>.stringKeyMap(): Map<String, Any?> =
      entries
        .mapNotNull { (key, value) ->
          val name = key as? String ?: return@mapNotNull null
          name to value.generatedValue()
        }.toMap()

    private fun Any?.generatedValue(): Any? =
      when (this) {
        is Map<*, *> -> stringKeyMap()
        is List<*> -> map { value -> value.generatedValue() }
        else -> this
      }

    private fun Map<*, *>.generatedExample(): GeneratedExample? =
      GeneratedExample(
        name = this["name"] as? String,
        value = this["payload"],
        documentation =
          GeneratedDocumentation(
            summary = this["summary"] as? String,
            description = this["description"] as? String,
          ),
      ).takeUnless { example ->
        example.name == null &&
          example.value == null &&
          example.documentation == null
      }

    companion object {

      private val yamlMapper = ObjectMapper(YAMLFactory())

      fun read(location: String): AsyncApiSourceDocument =
        AsyncApiSourceDocument(
          URI(location).toURL().openStream().use { input ->
            yamlMapper.readValue(input, object : TypeReference<Map<String, Any?>>() {})
          },
        )
    }
  }

  private data class AsyncApiServer(
    val name: String,
    val url: String?,
    val protocol: String?,
    val protocolVersion: String?,
    val description: String?,
    val security: List<Map<*, *>>,
  )

  private data class AsyncApiChannel(
    val name: String,
    val address: String,
    val source: Map<*, *>,
    val serverNames: List<String>,
    val security: List<Map<*, *>>,
  ) {

    fun operation(method: String): AsyncApiOperation? =
      source.mapValue(method)?.let { operation ->
        AsyncApiOperation(
          operationId = operation["operationId"] as? String ?: method,
          source = operation,
          summary = operation["summary"] as? String,
          description = operation["description"] as? String,
          tags =
            (operation["tags"] as? List<*>)
              .orEmpty()
              .mapNotNull { tag ->
                when (tag) {
                  is String -> tag
                  is Map<*, *> -> tag["name"] as? String
                  else -> null
                }
              },
          message = operation.mapValue("message")?.let(::AsyncApiMessage),
          security = operation.listValue("security").orEmpty(),
        )
      }

    fun bindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
      source.mapValue("bindings").orEmpty().protocolBindings(kind)

    fun extension(name: String): String? = source[name] as? String
  }

  private data class AsyncApiOperation(
    val operationId: String,
    val source: Map<*, *>,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val message: AsyncApiMessage?,
    val security: List<Map<*, *>>,
  ) {

    fun bindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
      source.mapValue("bindings").orEmpty().protocolBindings(kind)

    fun extension(name: String): String? = source[name] as? String
  }

  private data class AsyncApiDocumentOperation(
    val operationId: String,
    val action: String?,
    val source: Map<*, *>,
    val channel: AsyncApiChannel,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val message: AsyncApiMessage?,
    val security: List<Map<*, *>>,
  ) {

    val method: String?
      get() =
        when (action?.lowercase()) {
          "send" -> "publish"
          "receive" -> "subscribe"
          else -> null
        }

    fun bindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
      source.mapValue("bindings").orEmpty().protocolBindings(kind)

    fun extension(name: String): String? = source[name] as? String
  }

  private data class AsyncApiMessage(
    val source: Map<*, *>,
  ) {

    val name: String?
      get() = source["name"] as? String

    val contentType: String?
      get() = source["contentType"] as? String

    val headers: Map<*, *>?
      get() = source.mapValue("headers")

    val payload: Map<*, *>?
      get() = source.mapValue("payload")

    val examples: List<GeneratedExampleSource>
      get() =
        source
          .listValue("examples")
          .orEmpty()
          .map { example ->
            GeneratedExampleSource(
              name = example["name"] as? String,
              summary = example["summary"] as? String,
              description = example["description"] as? String,
              payload = example["payload"],
            )
          }
  }

  private data class GeneratedExampleSource(
    val name: String?,
    val summary: String?,
    val description: String?,
    val payload: Any?,
  )
}

private fun Map<*, *>.listValue(name: String): List<Map<*, *>>? =
  when (val value = this[name]) {
    is List<*> -> value.filterIsInstance<Map<*, *>>()
    else -> null
  }

private fun Map<*, *>.mapValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

private fun Map<*, *>.protocolBindings(kind: GeneratedProtocolBinding.Kind): List<GeneratedProtocolBinding> =
  entries.mapNotNull { (key, value) ->
    val protocol = key as? String ?: return@mapNotNull null
    val values = value as? Map<*, *> ?: return@mapNotNull null
    GeneratedProtocolBinding(
      kind = kind,
      protocol = protocol,
      values = values.stringKeyMap(),
    )
  }

private fun Map<*, *>.stringKeyMap(): Map<String, Any?> =
  entries
    .mapNotNull { (key, value) ->
      val name = key as? String ?: return@mapNotNull null
      name to value.generatedValue()
    }.toMap()

private fun Any?.generatedValue(): Any? =
  when (this) {
    is Map<*, *> -> stringKeyMap()
    is List<*> -> map { value -> value.generatedValue() }
    else -> this
  }

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
