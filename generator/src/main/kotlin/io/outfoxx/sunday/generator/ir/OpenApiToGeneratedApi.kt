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
import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.utils.toLowerCamelCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import java.math.BigDecimal
import java.net.URI

/**
 * Converts OpenAPI 3.1 documents into Sunday generated API IR without using AMF.
 */
class OpenApiToGeneratedApi(
  private val options: GeneratedApiIrOptions = GeneratedApiIrOptions(),
) {

  /** Converts an OpenAPI source document into generated API IR. */
  fun convert(sourceUri: URI): GeneratedApi = convertFragment(sourceUri).api

  /** Converts an OpenAPI source document into a generated API IR composition fragment. */
  fun convertFragment(sourceUri: URI): GeneratedApiFragment {
    val document = OpenApiSourceDocument.read(sourceUri.toString())
    activeDocument = document
    try {
      val localModels = linkedMapOf<String, GeneratedModel>()
      val discriminatorValues = document.discriminatorValues()
      document.schemas.forEach { (name, schema) ->
        localModels[name] = document.generatedModel(name, schema, localModels, discriminatorValues)
      }

      val serviceFragments = document.serviceFragments(localModels)
      val auth =
        document.auth(
          document.security,
          zanzibar = document.rootZanzibar(),
          zanzibarUserSource = document.rootZanzibarUserSource(),
        )
      val generatedApi =
        GeneratedApi(
          name = document.title,
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, document.location),
          services = serviceFragments.map { fragment -> fragment.service },
          models = localModels.values.sortedBy { model -> model.name },
          problems = document.problems(),
          auth = auth,
          jaxrs = document.restClientJaxrs(),
          media = GeneratedMedia(),
          tags = document.tags(),
          documentation = documentation(description = document.info["description"] as? String),
        )

      return GeneratedApiFragment(
        api = generatedApi,
        apiId = document.compositionApiIdentity(),
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
      activeDocument = null
    }
  }

  private fun OpenApiSourceDocument.serviceFragments(
    localModels: MutableMap<String, GeneratedModel>,
  ): List<ServiceFragment> =
    operationFragments(localModels)
      .groupBy { fragment -> fragment.seed }
      .mapNotNull { (seed, fragments) ->
        val operations = fragments.map { fragment -> fragment.operation }
        operations
          .takeIf { it.isNotEmpty() }
          ?.let {
            val serviceName = serviceName(seed.serviceLabel)
            val serviceJaxrsValues =
              fragments
                .mapNotNull { fragment -> fragment.serviceJaxrs }
                .distinct()
            require(serviceJaxrsValues.size <= 1) {
              "OpenAPI service '$serviceName' has conflicting x-sunday-jaxrs metadata. " +
                "Move REST client metadata to the API root, a single service tag, or align the operation metadata."
            }
            ServiceFragment(
              service =
                GeneratedService(
                  name = serviceName,
                  baseUri = servers.firstOrNull()?.get("url") as? String,
                  baseUriParameters = servers.firstOrNull()?.serverVariables().orEmpty(),
                  operations = operations,
                  auth = auth(zanzibar = rootZanzibar()),
                  jaxrs = serviceJaxrsValues.singleOrNull(),
                  media = GeneratedMedia(),
                ),
              identity = seed.identity,
              operationIdentities =
                fragments.map { fragment ->
                  GeneratedOperationIdentityKey(serviceName, fragment.operation.id) to fragment.identity
                },
            )
          }
      }

  private fun OpenApiSourceDocument.operationFragments(
    localModels: MutableMap<String, GeneratedModel>,
  ): List<OperationFragment> =
    paths.flatMap { (path, pathItem) ->
      val pathParameters = pathItem.listValue("parameters").mapNotNull { value -> parameter(value) }
      val pathService = pathItem.stringValue("x-sunday-service")
      pathItem.operationMethods().mapNotNull { method ->
        val operation = pathItem.mapValue(method) ?: return@mapNotNull null
        if (operation.excluded() || excludedByTag(operation)) {
          return@mapNotNull null
        }
        val operationId = operation.generatedOperationId(method, path)
        val seed = serviceIdentitySeed(path, pathService, operation)
        val serviceJaxrs = serviceJaxrs(seed.serviceTag, pathItem, operation)
        val operationPolicy = operationPolicy(operation, pathItem)
        val operationJaxrs = pathItem.operationJaxrs().mergeWith(operation.operationJaxrs())
        OperationFragment(
          operation =
            GeneratedOperation(
              id = operationId,
              method = method.uppercase(),
              path = path,
              parameters = pathParameters + operation.listValue("parameters").mapNotNull { value -> parameter(value) },
              requestBody = operation.requestBody(localModels),
              responses = operation.responses(operationId, seed.serviceLabel, localModels),
              problems = operation.problemRefs(),
              nullify = operation.nullify(),
              auth = operation.auth(operation.listValue("security")),
              media = GeneratedMedia(),
              policy = operationPolicy,
              jaxrs = operationJaxrs,
              streaming = operation.streaming(),
              deprecated = operation["deprecated"] == true,
              tags = operation.listValue("tags").mapNotNull { tag -> tag as? String },
              documentation = documentation(operation["summary"] as? String, operation["description"] as? String),
            ),
          identity = operation.compositionOperationIdentity(operationId),
          seed = seed,
          serviceJaxrs = serviceJaxrs,
        )
      }
    }

  private fun OpenApiSourceDocument.serviceJaxrs(
    serviceTag: String?,
    pathItem: Map<*, *>,
    operation: Map<*, *>,
  ): GeneratedJaxrs? =
    tagJaxrs(serviceTag)
      .mergeWith(pathItem.restClientJaxrs())
      .mergeWith(operation.restClientJaxrs())

  private fun OpenApiSourceDocument.tagJaxrs(serviceTag: String?): GeneratedJaxrs? =
    serviceTag
      ?.let { tagName ->
        listValue("tags")
          .mapNotNull { tag -> tag as? Map<*, *> }
          .firstOrNull { tag -> tag["name"] == tagName }
      }?.restClientJaxrs()

  private fun OpenApiSourceDocument.operationPolicy(
    operation: Map<*, *>,
    pathItem: Map<*, *>,
  ): GeneratedPolicy? =
    tagPolicy(operation)
      .mergeWith(pathItem.policy())
      .mergeWith(operation.policy())

  private fun OpenApiSourceDocument.tagPolicy(operation: Map<*, *>): GeneratedPolicy? {
    val tagPolicies =
      operation
        .listValue("tags")
        .mapNotNull { tag -> tag as? String }
        .mapNotNull { tagName ->
          listValue("tags")
            .mapNotNull { tag -> tag as? Map<*, *> }
            .firstOrNull { tag -> tag["name"] == tagName }
            ?.policy()
        }.distinct()

    require(tagPolicies.size <= 1) {
      "OpenAPI operation '${operation["operationId"] ?: "<unknown>"}' has multiple tag x-sunday-policy values. " +
        "Move policy metadata to the operation or align the tag policies."
    }

    return tagPolicies.singleOrNull()
  }

  private fun OpenApiSourceDocument.parameter(value: Any?): GeneratedParameter? {
    if ((value as? Map<*, *>)?.excluded() == true) {
      return null
    }
    val parameter = resolveParameter(value) ?: return null
    if (parameter.excluded()) {
      return null
    }
    val wireName = parameter["name"] as? String ?: return null
    val generatedName = wireName.toLowerCamelCase()
    val schema = parameter.mapValue("schema").orEmpty()
    val location =
      when (parameter["in"] as? String) {
        "path" -> GeneratedParameter.Location.PATH
        "query" -> GeneratedParameter.Location.QUERY
        "header" -> GeneratedParameter.Location.HEADER
        "cookie" -> GeneratedParameter.Location.COOKIE
        else -> return null
      }
    val examples = parameter.examples()
    return GeneratedParameter(
      name = generatedName,
      location = location,
      type = schemaTypeRef(schema, generatedName.toUpperCamelCase(), null),
      required = parameter["required"] == true,
      serializationName = wireName.takeUnless { it == generatedName },
      defaultValue = schema["default"],
      constantValue = schema.constantValue(),
      encoding = parameter.encoding(location),
      validation = validation(schema),
      examples = examples,
      deprecated = parameter["deprecated"] == true,
      documentation = documentation(description = parameter["description"] as? String),
    )
  }

  private fun Map<*, *>.requestBody(localModels: MutableMap<String, GeneratedModel>): GeneratedPayload? {
    val document = activeDocument ?: return null
    if ((this["requestBody"] as? Map<*, *>)?.excluded() == true) {
      return null
    }
    val requestBody = document.resolveRequestBody(this["requestBody"]) ?: return null
    if (requestBody.excluded()) {
      return null
    }
    val content = requestBody.content()
    val payloads =
      content.mapNotNull { (mediaType, media) ->
        val schema = media.mapValue("schema") ?: return@mapNotNull null
        GeneratedPayloadOption(
          type = document.schemaTypeRef(schema, null, null, localModels),
          mediaTypes = listOf(mediaType),
        )
      }
    val firstPayload = payloads.firstOrNull() ?: return null
    return GeneratedPayload(
      type = firstPayload.type,
      mediaTypes = payloads.mapNotNull { payload -> payload.mediaTypes.firstOrNull() },
      payloads = payloads.takeIf { it.size > 1 }.orEmpty(),
      examples = content.flatMap { (mediaType, media) -> media.examples(mediaType) },
      documentation = documentation(description = requestBody["description"] as? String),
    )
  }

  private fun Map<*, *>.responses(
    operationId: String,
    serviceLabel: String,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<GeneratedResponse> =
    mapValue("responses")
      .orEmpty()
      .mapNotNull { (statusValue, responseValue) ->
        val document = activeDocument ?: return@mapNotNull null
        val status = statusValue as? String ?: return@mapNotNull null
        val response = document.resolveResponse(responseValue) ?: return@mapNotNull null
        val content = response.content()
        val responseScope =
          GeneratedModelScope(
            service = serviceName(serviceLabel),
            operation = operationId,
            usage = GeneratedModelScope.Usage.RESPONSE_BODY,
            status = status.toIntOrNull(),
          )
        val payloads =
          content.mapNotNull { (mediaType, media) ->
            val schema = media.mapValue("schema") ?: return@mapNotNull null
            GeneratedPayloadOption(
              type =
                document.schemaTypeRef(
                  schema,
                  operationId.toUpperCamelCase() + "ResponseBody",
                  responseScope,
                  localModels,
                ),
              mediaTypes = listOf(mediaType),
            )
          }
        val groupedPayloads = payloads.groupByType()
        val firstPayload = groupedPayloads.firstOrNull()
        GeneratedResponse(
          status = status.toIntOrNull(),
          type = firstPayload?.type,
          mediaTypes = firstPayload?.mediaTypes.orEmpty(),
          payloads = groupedPayloads.takeIf { it.size > 1 }.orEmpty(),
          headers =
            response.mapValue("headers").orEmpty().mapNotNull { (name, header) ->
              headerParameter(name, header)
            },
          examples = content.flatMap { (mediaType, media) -> media.examples(mediaType) },
          documentation = documentation(description = response["description"] as? String),
        )
      }

  private fun OpenApiSourceDocument.schemaTypeRef(
    schema: Map<*, *>,
    nameHint: String?,
    scope: GeneratedModelScope?,
    localModels: MutableMap<String, GeneratedModel>? = null,
  ): GeneratedTypeRef {
    schema.refName()?.let { return GeneratedTypeRef.named(it) }
    schema.singleAllOfRefName()?.let { return GeneratedTypeRef.named(it) }

    val oneOf = schema.listValue("oneOf")
    val anyOf = schema.listValue("anyOf")
    if (oneOf.isNotEmpty() || anyOf.isNotEmpty()) {
      val branches = (oneOf.ifEmpty { anyOf }).mapNotNull { branch -> branch as? Map<*, *> }
      val nonNullBranches = branches.filterNot { branch -> branch.isNullSchema() }
      if (branches.size != nonNullBranches.size && nonNullBranches.size == 1) {
        return schemaTypeRef(nonNullBranches.single(), nameHint, scope, localModels).copy(nullable = true)
      }
      if (nameHint != null && localModels != null) {
        localModels[nameHint] = generatedUnionModel(nameHint, schema, branches, scope, localModels)
        return GeneratedTypeRef.named(nameHint, scope = scope)
      }
      return GeneratedTypeRef(
        kind = GeneratedTypeRef.Kind.UNION,
        name = "union",
        arguments = branches.map { branch -> schemaTypeRef(branch, null, null, localModels) },
      )
    }

    val nullable = schema.isNullable()
    if (schema.isUnconstrainedSchema()) {
      return scalar("any", nullable = nullable)
    }

    val type = schema.schemaType()
    return when (type) {
      "array" ->
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.ARRAY,
          name = "array",
          nullable = nullable,
          arguments =
            listOf(
              schema.mapValue("items")?.let { schemaTypeRef(it, null, null, localModels) } ?: scalar("any"),
            ),
          collection = GeneratedCollectionKind.SET.takeIf { schema["uniqueItems"] == true },
        )

      "object", null -> {
        val properties = schema.mapValue("properties").orEmpty()
        val additionalProperties = schema["additionalProperties"]
        when {
          properties.isEmpty() && additionalProperties == true -> scalar("object", nullable = nullable)
          properties.isEmpty() && additionalProperties is Map<*, *> ->
            GeneratedTypeRef(
              kind = GeneratedTypeRef.Kind.MAP,
              name = "object",
              nullable = nullable,
              arguments = listOf(schemaTypeRef(additionalProperties, null, null, localModels)),
            )

          nameHint != null && localModels != null -> {
            localModels[nameHint] = generatedModel(nameHint, schema, localModels, discriminatorValues(), scope)
            GeneratedTypeRef.named(nameHint, nullable = nullable, scope = scope)
          }

          else -> scalar("object", nullable = nullable)
        }
      }

      "string" ->
        if ((schema["format"] as? String) == "binary") {
          scalar("file", nullable = nullable, format = "binary")
        } else {
          scalar("string", nullable = nullable, format = schema["format"] as? String)
        }

      "integer" -> scalar("integer", nullable = nullable, format = schema["format"] as? String)
      "number" -> scalar("number", nullable = nullable, format = schema["format"] as? String)
      "boolean" -> scalar("boolean", nullable = nullable)
      "null" -> scalar("any", nullable = true)
      else -> scalar("any", nullable = nullable)
    }
  }

  private fun OpenApiSourceDocument.generatedModel(
    name: String,
    schema: Map<*, *>,
    localModels: MutableMap<String, GeneratedModel>,
    discriminatorValues: Map<String, String>,
    scope: GeneratedModelScope? = null,
  ): GeneratedModel {
    val resolved = schema.resolveSchema()
    val allOf = resolved.listValue("allOf").mapNotNull { it as? Map<*, *> }
    val oneOf = resolved.listValue("oneOf").mapNotNull { it as? Map<*, *> }
    val anyOf = resolved.listValue("anyOf").mapNotNull { it as? Map<*, *> }

    return when {
      oneOf.isNotEmpty() || anyOf.isNotEmpty() ->
        generatedUnionModel(
          name,
          resolved,
          oneOf.ifEmpty {
            anyOf
          },
          scope,
          localModels,
        )
      resolved["enum"] is List<*> ->
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.ENUM,
          scope = scope,
          values = resolved.listValue("enum").mapNotNull { it?.toString() },
          documentation = documentation(description = resolved["description"] as? String),
        )

      resolved.isUnconstrainedSchema() ->
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.SCALAR_ALIAS,
          scope = scope,
          aliases = listOf(scalar("any", nullable = resolved.isNullable())),
          documentation = documentation(description = resolved["description"] as? String),
          examples = resolved.examples(),
          deprecated = resolved["deprecated"] == true,
        )

      resolved.schemaType() == "array" ->
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.ARRAY,
          scope = scope,
          aliases =
            listOf(
              resolved.mapValue("items")?.let { schemaTypeRef(it, null, null, localModels) } ?: scalar("any"),
            ),
          collection = GeneratedCollectionKind.SET.takeIf { resolved["uniqueItems"] == true },
          validation = validation(resolved),
          documentation = documentation(description = resolved["description"] as? String),
        )

      resolved.isScalarAliasModel() ->
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.SCALAR_ALIAS,
          scope = scope,
          aliases = listOf(schemaTypeRef(resolved, null, null, localModels)),
          validation = validation(resolved),
          documentation = documentation(description = resolved["description"] as? String),
          examples = resolved.examples(),
          deprecated = resolved["deprecated"] == true,
        )

      resolved.isMapModel() ->
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.MAP,
          scope = scope,
          aliases =
            listOf(
              (resolved["additionalProperties"] as? Map<*, *>)?.let { schemaTypeRef(it, null, null, localModels) }
                ?: scalar("any"),
            ),
          additionalProperties = additionalProperties(resolved, localModels),
          documentation = documentation(description = resolved["description"] as? String),
        )

      else -> {
        val inlineObject = allOf.firstOrNull { part -> part.refName() == null } ?: resolved
        val required =
          (resolved.listValue("required") + inlineObject.listValue("required"))
            .mapNotNull {
              it as? String
            }.toSet()
        GeneratedModel(
          name = name,
          kind = GeneratedModel.Kind.OBJECT,
          scope = scope,
          properties = inlineObject.properties(name, required, localModels),
          closed = true.takeIf { resolved["additionalProperties"] == false },
          additionalProperties = additionalProperties(resolved, localModels),
          inherits = allOf.mapNotNull { it.refName()?.let(GeneratedTypeRef::named) },
          discriminator = resolved.discriminatorProperty(),
          discriminatorValue = discriminatorValues[name],
          documentation = documentation(description = resolved["description"] as? String),
          examples = resolved.examples(),
          deprecated = resolved["deprecated"] == true,
        )
      }
    }
  }

  private fun OpenApiSourceDocument.generatedUnionModel(
    name: String,
    schema: Map<*, *>,
    branches: List<Map<*, *>>,
    scope: GeneratedModelScope?,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedModel =
    GeneratedModel(
      name = name,
      kind = GeneratedModel.Kind.UNION,
      scope = scope,
      aliases = branches.map { branch -> schemaTypeRef(branch, null, null, localModels) },
      discriminator = schema.discriminatorProperty(),
      discriminatorMappings =
        schema
          .mapValue("discriminator")
          ?.mapValue("mapping")
          .orEmpty()
          .mapNotNull { (value, ref) ->
            (value as? String)?.let { it to GeneratedTypeRef.named((ref as String).substringAfterLast('/')) }
          }.toMap(),
      documentation = documentation(description = schema["description"] as? String),
    )

  private fun Map<*, *>.properties(
    modelName: String,
    required: Set<String>,
    localModels: MutableMap<String, GeneratedModel>,
  ): List<GeneratedModelProperty> =
    mapValue("properties")
      .orEmpty()
      .map { (wireNameValue, propertyValue) ->
        val document = activeDocument ?: error("No active OpenAPI document")
        val wireName = wireNameValue as String
        val propertySchema = (propertyValue as? Map<*, *>).orEmpty()
        val property = propertySchema.resolveSchema()
        val generatedName = wireName.toLowerCamelCase()
        val typeNameHint = modelName + generatedName.toUpperCamelCase()
        GeneratedModelProperty(
          name = generatedName,
          type = document.schemaTypeRef(propertySchema, typeNameHint, null, localModels),
          required = required.contains(wireName),
          serializationName = wireName.takeUnless { it == generatedName },
          defaultValue = property["default"]?.toString(),
          validation = validation(property),
          examples = property.examples(),
          readOnly = property["readOnly"] == true,
          writeOnly = property["writeOnly"] == true,
          deprecated = property["deprecated"] == true,
          documentation = documentation(description = property["description"] as? String),
        )
      }

  private fun OpenApiSourceDocument.additionalProperties(
    schema: Map<*, *>,
    localModels: MutableMap<String, GeneratedModel>,
  ): GeneratedAdditionalProperties? =
    when (val additionalProperties = schema["additionalProperties"]) {
      true -> GeneratedAdditionalProperties(allowed = true)
      false -> GeneratedAdditionalProperties(allowed = false)
      is Map<*, *> ->
        GeneratedAdditionalProperties(
          allowed = true,
          type = schemaTypeRef(additionalProperties, null, null, localModels),
          validation = validation(additionalProperties),
          documentation = documentation(description = additionalProperties["description"] as? String),
        )

      else -> null
    }

  private fun OpenApiSourceDocument.problems(): List<GeneratedProblem> {
    val problemBaseUri = stringValue("x-sunday-problemBaseUri") ?: return emptyList()
    val problemUriParams = mapValue("x-sunday-problemUriParams").orEmpty().mapValues { it.value.toString() }
    return mapValue("x-sunday-problemTypes")
      .orEmpty()
      .mapNotNull { (sourceNameValue, value) ->
        val sourceName = sourceNameValue as? String ?: return@mapNotNull null
        val problem = value as? Map<*, *> ?: return@mapNotNull null
        val name = sourceName.toUpperCamelCase() + "Problem"
        val typeUri =
          problemUriParams.entries.fold(problemBaseUri) { uri, (key, replacement) ->
            uri.replace("{$key}", replacement)
          } + sourceName
        val customFields =
          problem.mapValue("custom").orEmpty().map { (fieldName, typeName) ->
            GeneratedModelProperty(
              name = (fieldName as String).toLowerCamelCase(),
              type = scalar(typeName.toString()),
              serializationName = fieldName.takeUnless { it == fieldName.toLowerCamelCase() },
            )
          }
        val status = (problem["status"] as? Number)?.toInt()
        val title = problem["title"] as? String
        val detail = problem["detail"] as? String
        GeneratedProblem(
          name = name,
          sourceName = sourceName,
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, location),
          typeUri = typeUri,
          status = status,
          title = title,
          detail = detail,
          statusBindings =
            status
              ?.let {
                listOf(GeneratedProblemStatusBinding(status = it, typeUri = typeUri, title = title, detail = detail))
              }.orEmpty(),
          payload =
            GeneratedProblemPayload(
              type = GeneratedTypeRef.named(name),
              mediaTypes = listOf("application/problem+json"),
              fields =
                listOf(
                  GeneratedModelProperty("type", scalar("string"), required = true),
                  GeneratedModelProperty("title", scalar("string"), required = true),
                  GeneratedModelProperty("status", scalar("integer"), required = true),
                  GeneratedModelProperty("detail", scalar("string"), required = true),
                  GeneratedModelProperty("instance", scalar("string")),
                ) + customFields,
            ),
          fields = customFields,
          documentation = documentation(summary = title, description = detail),
        )
      }
  }

  private fun OpenApiSourceDocument.auth(
    security: List<Any?> = this.security,
    zanzibar: Map<String, String> = mapOf(),
    zanzibarUserSource: GeneratedZanzibarUserSource? = null,
  ): GeneratedAuth? {
    val requirements = security.mapNotNull { requirement -> (requirement as? Map<*, *>)?.securityRequirement() }
    val schemeNames = requirements.flatMap { requirement -> requirement.schemes }.distinct()
    val schemes = schemeNames.mapNotNull { name -> securityScheme(name) }
    return GeneratedAuth(
      schemes = schemeNames,
      requirements = requirements,
      securitySchemes = schemes,
      zanzibar = zanzibar,
      zanzibarUserSource = zanzibarUserSource,
    ).takeUnless { it == GeneratedAuth() }
  }

  private fun Map<*, *>.securityRequirement(): GeneratedSecurityRequirement =
    GeneratedSecurityRequirement(keys.mapNotNull { it as? String })

  private fun OpenApiSourceDocument.securityScheme(name: String): GeneratedSecurityScheme? {
    val scheme = securitySchemes[name] ?: return null
    val type = scheme["type"] as? String
    val parameter =
      if (type == "apiKey") {
        GeneratedParameter(
          name = (scheme["name"] as? String ?: name).toLowerCamelCase(),
          location =
            when (scheme["in"] as? String) {
              "query" -> GeneratedParameter.Location.QUERY
              "cookie" -> GeneratedParameter.Location.COOKIE
              else -> GeneratedParameter.Location.HEADER
            },
          type = scalar("string"),
          required = true,
          serializationName = (scheme["name"] as? String)?.takeUnless { it == it.toLowerCamelCase() },
        )
      } else {
        null
      }
    return GeneratedSecurityScheme(
      name = name,
      type = if (type == "apiKey") "Api Key" else type,
      scheme = scheme["scheme"] as? String,
      bearerFormat = scheme["bearerFormat"] as? String,
      headers = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.HEADER }),
      queryParameters = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.QUERY }),
      cookieParameters = listOfNotNull(parameter?.takeIf { it.location == GeneratedParameter.Location.COOKIE }),
      documentation = documentation(description = scheme["description"] as? String),
    )
  }

  private fun Map<*, *>.headerParameter(
    nameValue: Any?,
    value: Any?,
  ): GeneratedParameter? {
    val document = activeDocument ?: return null
    val wireName = nameValue as? String ?: return null
    val header = (value as? Map<*, *>).orEmpty()
    val generatedName = wireName.toLowerCamelCase()
    return GeneratedParameter(
      name = generatedName,
      location = GeneratedParameter.Location.HEADER,
      type = document.schemaTypeRef(header.mapValue("schema").orEmpty(), generatedName.toUpperCamelCase(), null),
      serializationName = wireName.takeUnless { it == generatedName },
      encoding = GeneratedParameterEncoding(style = "simple"),
      examples = header.examples(),
      documentation = documentation(description = header["description"] as? String),
    )
  }

  private fun OpenApiSourceDocument.serviceIdentitySeed(
    path: String,
    pathService: String?,
    operation: Map<*, *>,
  ): ServiceIdentitySeed {
    val explicitService = operation.stringValue("x-sunday-service") ?: pathService
    if (explicitService != null) {
      return ServiceIdentitySeed(explicitService, GeneratedIdentity.explicit(normalizedCompositionId(explicitService)))
    }

    if (operation.streaming() != null) {
      val serviceLabel = path.trim('/').substringBefore('/').toUpperCamelCase()
      return ServiceIdentitySeed(serviceLabel, GeneratedIdentity.native(normalizedCompositionId(serviceLabel)))
    }

    val taggedService = taggedServiceLabel(operation, path)
    if (taggedService != null) {
      return ServiceIdentitySeed(
        taggedService,
        GeneratedIdentity.native(normalizedCompositionId(taggedService)),
        serviceTag = taggedService,
      )
    }

    val serviceLabel = title.removeSuffix(" API")
    return ServiceIdentitySeed(serviceLabel, GeneratedIdentity.native(normalizedCompositionId(serviceLabel)))
  }

  private fun OpenApiSourceDocument.taggedServiceLabel(
    operation: Map<*, *>,
    path: String,
  ): String? {
    val tags =
      operation
        .listValue("tags")
        .mapNotNull { tag -> (tag as? String)?.trimToNull() }
        .distinct()
    val serviceTags =
      if (options.deriveServicesFromTags) {
        tags
      } else {
        val serviceGroupTags =
          listValue("tags")
            .mapNotNull { tag -> tag as? Map<*, *> }
            .filter { tag -> tag.serviceGroup() }
            .mapNotNull { tag -> (tag["name"] as? String)?.trimToNull() }
            .toSet()
        tags.filter { tag -> tag in serviceGroupTags }
      }

    require(serviceTags.size <= 1) {
      "OpenAPI path '$path' operation has multiple service tags (${serviceTags.joinToString()}). " +
        "Add x-sunday-service to select one generated service explicitly."
    }
    return serviceTags.singleOrNull()
  }

  private fun OpenApiSourceDocument.excludedByTag(operation: Map<*, *>): Boolean {
    val tags =
      operation
        .listValue("tags")
        .mapNotNull { tag -> (tag as? String)?.trimToNull() }
        .toSet()
    if (tags.isEmpty()) {
      return false
    }
    return listValue("tags")
      .mapNotNull { tag -> tag as? Map<*, *> }
      .any { tag -> tag["name"] in tags && tag.excluded() }
  }

  private fun Map<*, *>.generatedOperationId(
    method: String,
    path: String,
  ): String =
    stringValue("x-sunday-operationId")
      ?: stringValue("operationId")
      ?: method.toLowerCamelCase() + path.toUpperCamelCase()

  private fun Map<*, *>.compositionOperationIdentity(operationId: String): GeneratedIdentity =
    stringValue("x-sunday-operationId")
      ?.let(GeneratedIdentity::explicit)
      ?: GeneratedIdentity.native(operationId)

  private fun OpenApiSourceDocument.compositionApiIdentity(): GeneratedIdentity =
    stringValue("x-sunday-apiId")
      ?.let(GeneratedIdentity::explicit)
      ?: GeneratedIdentity.native(normalizedCompositionId(title))

  private fun Map<*, *>.problemRefs(): List<GeneratedTypeRef> =
    listValue("x-sunday-problems").mapNotNull { problemName ->
      (problemName as? String)?.toUpperCamelCase()?.let { GeneratedTypeRef.named(it + "Problem") }
    }

  private fun Map<*, *>.nullify(): GeneratedNullify? {
    val values = listValue("x-sunday-nullify")
    val problems =
      values.mapNotNull { value ->
        (value as? String)
          ?.takeUnless { it.toIntOrNull() != null }
          ?.toUpperCamelCase()
          ?.let { GeneratedTypeRef.named(it + "Problem") }
      }
    val statuses = values.mapNotNull { value -> (value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull() }
    return GeneratedNullify(problems = problems, statuses = statuses).takeUnless { it == GeneratedNullify() }
  }

  private fun Map<*, *>.policy(): GeneratedPolicy? {
    val policy = mapValue("x-sunday-policy") ?: return null
    return GeneratedPolicy(
      timeout = policy["timeout"] as? String,
      retry = policy.mapValue("retry").stringMap(),
      circuitBreaker = policy.mapValue("circuitBreaker").stringMap(),
      clientRateLimit = policy.mapValue("clientRateLimit").stringMap(),
      serverRateLimit = policy.mapValue("serverRateLimit").stringMap(),
      source = policy["source"] as? String,
    ).takeUnless { it == GeneratedPolicy() }
  }

  private fun GeneratedPolicy?.mergeWith(overrides: GeneratedPolicy?): GeneratedPolicy? {
    val base = this ?: GeneratedPolicy()
    if (overrides == null || overrides == GeneratedPolicy()) {
      return base.takeUnless { it == GeneratedPolicy() }
    }

    return GeneratedPolicy(
      timeout = overrides.timeout ?: base.timeout,
      retry = base.retry + overrides.retry,
      circuitBreaker = base.circuitBreaker + overrides.circuitBreaker,
      clientRateLimit = base.clientRateLimit + overrides.clientRateLimit,
      serverRateLimit = base.serverRateLimit + overrides.serverRateLimit,
      source = overrides.source ?: base.source,
    ).takeUnless { it == GeneratedPolicy() }
  }

  private fun Map<*, *>.streaming(): GeneratedStreaming? =
    responses()
      .values
      .flatMap { response -> response.content().entries }
      .any { (mediaType, media) ->
        mediaType == "text/event-stream" &&
          media
            .mapValue("schema")
            .orEmpty()
            .let { schema -> schema.schemaType() != "string" || schema.refName() != null }
      }.takeIf { it }
      ?.let { GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM) }

  private fun OpenApiSourceDocument.rootZanzibar(): Map<String, String> = mapValue("x-sunday-zanzibar").zanzibarMap()

  private fun OpenApiSourceDocument.rootZanzibarUserSource(): GeneratedZanzibarUserSource? =
    mapValue("x-sunday-zanzibar").zanzibarUserSource()

  private fun Map<*, *>.auth(security: List<Any?>): GeneratedAuth? =
    activeDocument?.let { document ->
      val zanzibar = mapValue("x-sunday-zanzibar")
      document.auth(
        security,
        zanzibar = document.rootZanzibar() + zanzibar.zanzibarMap(),
        zanzibarUserSource = zanzibar.zanzibarUserSource() ?: document.rootZanzibarUserSource(),
      )
    }

  private fun OpenApiSourceDocument.discriminatorValues(): Map<String, String> =
    schemas.values
      .flatMap { schema ->
        schema
          .mapValue("discriminator")
          ?.mapValue("mapping")
          .orEmpty()
          .mapNotNull { (value, ref) -> (ref as? String)?.substringAfterLast('/')?.let { it to value.toString() } }
      }.toMap()

  private fun OpenApiSourceDocument.resolveParameter(value: Any?): Map<*, *>? =
    when (value) {
      is Map<*, *> -> value.resolveComponentRef("parameters")
      else -> null
    }

  private fun OpenApiSourceDocument.resolveRequestBody(value: Any?): Map<*, *>? =
    when (value) {
      is Map<*, *> -> value.resolveComponentRef("requestBodies")
      else -> null
    }

  private fun OpenApiSourceDocument.resolveResponse(value: Any?): Map<*, *>? =
    when (value) {
      is Map<*, *> -> value.resolveComponentRef("responses")
      else -> null
    }

  private fun Map<*, *>.resolveSchema(): Map<*, *> = refName()?.let { activeDocument?.schemas?.get(it) } ?: this

  private fun Map<*, *>.resolveComponentRef(component: String): Map<*, *> =
    refName()?.let { activeDocument?.components?.mapValue(component)?.mapValue(it) } ?: this

  private fun Map<*, *>.refName(): String? =
    (this["\$ref"] as? String)
      ?.takeIf { ref -> ref.startsWith("#/components/") }
      ?.substringAfterLast('/')

  private fun Map<*, *>.singleAllOfRefName(): String? {
    val allOf = listValue("allOf")
    return allOf.singleOrNull()?.let { (it as? Map<*, *>)?.refName() }
  }

  private fun Map<*, *>.content(): Map<String, Map<*, *>> =
    mapValue("content")
      .orEmpty()
      .mapNotNull { (mediaType, media) ->
        (mediaType as? String)?.let { it to (media as? Map<*, *>).orEmpty() }
      }.toMap()

  private fun Map<*, *>.responses(): Map<String, Map<*, *>> =
    mapValue("responses")
      .orEmpty()
      .mapNotNull { (status, response) ->
        (status as? String)?.let { it to (response as? Map<*, *>).orEmpty() }
      }.toMap()

  private fun Map<*, *>.operationMethods(): List<String> =
    keys.mapNotNull { key -> (key as? String)?.takeIf(httpMethods::contains) }

  private fun List<GeneratedPayloadOption>.groupByType(): List<GeneratedPayloadOption> =
    groupBy { payload -> payload.type }
      .values
      .map { payloads ->
        payloads.first().copy(mediaTypes = payloads.flatMap { payload -> payload.mediaTypes }.distinct())
      }

  private fun Map<*, *>.encoding(location: GeneratedParameter.Location): GeneratedParameterEncoding? {
    val style =
      (this["style"] as? String)
        ?: when (location) {
          GeneratedParameter.Location.QUERY, GeneratedParameter.Location.COOKIE -> "form"
          GeneratedParameter.Location.PATH, GeneratedParameter.Location.HEADER -> "simple"
          else -> null
        }
    val explode =
      this["explode"] as? Boolean
        ?: when (location) {
          GeneratedParameter.Location.QUERY, GeneratedParameter.Location.COOKIE -> true
          GeneratedParameter.Location.PATH, GeneratedParameter.Location.HEADER -> false
          else -> null
        }
    return GeneratedParameterEncoding(
      style = style,
      explode = explode,
      allowReserved = this["allowReserved"] as? Boolean,
      allowEmptyValue = this["allowEmptyValue"] as? Boolean,
    ).takeUnless { it == GeneratedParameterEncoding() }
  }

  private fun Map<*, *>.examples(mediaType: String? = null): List<GeneratedExample> {
    val examples =
      mapValue("examples").orEmpty().mapNotNull { (name, exampleValue) ->
        val example = (exampleValue as? Map<*, *>).orEmpty()
        GeneratedExample(
          name = name as? String,
          mediaType = mediaType,
          value = example["value"],
          strict = true,
          documentation =
            documentation(
              summary = example["summary"] as? String,
              description = example["description"] as? String,
            ),
        )
      }
    val example = this["example"]
    return examples.ifEmpty {
      if (example != null) {
        listOf(GeneratedExample(mediaType = mediaType, value = example, strict = true))
      } else {
        emptyList()
      }
    }
  }

  private fun OpenApiSourceDocument.tags(): List<GeneratedTag> =
    listValue("tags").mapNotNull { value ->
      val tag = value as? Map<*, *> ?: return@mapNotNull null
      GeneratedTag(
        name = tag["name"] as? String ?: return@mapNotNull null,
        serviceGroup = tag.serviceGroup(),
        policy = tag.policy(),
        jaxrs = tag.restClientJaxrs(),
        documentation = documentation(description = tag["description"] as? String),
      )
    }

  private fun Map<*, *>.serviceGroup(): Boolean = booleanValue("x-sunday-service-group") == true

  private fun Map<*, *>.excluded(): Boolean =
    when (val value = this["x-sunday-exclude"]) {
      true -> true
      is String -> enabledForTarget(value)
      else -> false
    }

  private fun enabledForTarget(value: String?): Boolean =
    when (value?.trim()?.lowercase()) {
      null, "", "true", "all", "both" -> true
      "client" -> options.generationMode == GenerationMode.Client
      "server" -> options.generationMode == GenerationMode.Server
      else -> false
    }

  private fun Map<*, *>.restClientJaxrs(): GeneratedJaxrs? {
    val restClient = mapValue("x-sunday-jaxrs")?.mapValue("rest-client")?.restClient()
    return GeneratedJaxrs(restClient = restClient).takeUnless { it == GeneratedJaxrs() }
  }

  private fun Map<*, *>.operationJaxrs(): GeneratedJaxrs? {
    val jaxrs = mapValue("x-sunday-jaxrs") ?: return null
    return GeneratedJaxrs(
      context = jaxrs.contextParameters(),
    ).takeUnless { it == GeneratedJaxrs() }
  }

  private fun OpenApiSourceDocument.restClientJaxrs(): GeneratedJaxrs? = source.restClientJaxrs()

  private fun Map<*, *>.contextParameters(): List<String> =
    listValue("context")
      .flatMap(::contextParameterValues)
      .distinct()

  private fun contextParameterValues(value: Any?): List<String> =
    when (value) {
      is String -> listOf(value)
      is Map<*, *> -> contextParameterValues(value)
      else -> emptyList()
    }

  private fun contextParameterValues(value: Map<*, *>): List<String> {
    val target = value.stringValue("target")
    if (!enabledForTarget(target)) {
      return emptyList()
    }
    return listOfNotNull(
      value.stringValue("type"),
      value.stringValue("name"),
      value.stringValue("value"),
    ).take(1)
  }

  private fun Map<*, *>.restClient(): GeneratedJaxrsRestClient? =
    GeneratedJaxrsRestClient(
      configKey = stringValue("config-key") ?: stringValue("configKey"),
      oidcClient = stringValue("oidc-client") ?: stringValue("oidcClient"),
      providers = listValue("providers").mapNotNull { provider -> (provider as? String)?.trimToNull() }.distinct(),
    ).takeUnless { it == GeneratedJaxrsRestClient() }

  private fun Map<*, *>.serverVariables(): List<GeneratedParameter> =
    mapValue("variables").orEmpty().mapNotNull { (nameValue, value) ->
      val name = nameValue as? String ?: return@mapNotNull null
      val variable = value as? Map<*, *> ?: return@mapNotNull null
      GeneratedParameter(
        name = name.toLowerCamelCase(),
        location = GeneratedParameter.Location.PATH,
        type = scalar("string"),
        defaultValue = variable["default"],
        serializationName = name.takeUnless { it == name.toLowerCamelCase() },
        documentation = documentation(description = variable["description"] as? String),
      )
    }

  private fun validation(schema: Map<*, *>): Map<String, String> =
    buildMap {
      listOf("minLength", "maxLength", "pattern", "minimum", "maximum", "minItems", "maxItems")
        .forEach { key -> schema[key]?.let { put(key, it.formatConstraint()) } }
      schema["uniqueItems"]?.takeIf { it == true }?.let { put("uniqueItems", it.toString()) }
    }

  private fun Any.formatConstraint(): String =
    when (this) {
      is Double -> BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
      is Float -> BigDecimal.valueOf(toDouble()).stripTrailingZeros().toPlainString()
      else -> toString()
    }

  private fun Map<*, *>.schemaType(): String? =
    when (val type = this["type"]) {
      is String -> type
      is List<*> -> type.firstNotNullOfOrNull { it as? String }?.takeUnless { it == "null" }
      else ->
        when {
          containsKey("properties") || containsKey("additionalProperties") -> "object"
          containsKey("items") -> "array"
          containsKey("enum") || containsKey("const") -> "string"
          else -> null
        }
    }

  private fun Map<*, *>.isUnconstrainedSchema(): Boolean =
    keys.all { key ->
      key is String && (key in unconstrainedSchemaKeys || key.startsWith("x-"))
    }

  private fun Map<*, *>.isNullable(): Boolean =
    this["nullable"] == true || (this["type"] as? List<*>)?.contains("null") == true

  private fun Map<*, *>.isNullSchema(): Boolean = schemaType() == "null"

  private fun Map<*, *>.isMapModel(): Boolean =
    schemaType() == "object" &&
      mapValue("properties").orEmpty().isEmpty() &&
      this["additionalProperties"] is Map<*, *>

  private fun Map<*, *>.isScalarAliasModel(): Boolean = schemaType() in setOf("string", "integer", "number", "boolean")

  private fun Map<*, *>.constantValue(): Any? =
    this["const"]
      ?: (this["enum"] as? List<*>)?.singleOrNull()

  private fun Map<*, *>.discriminatorProperty(): String? = mapValue("discriminator")?.get("propertyName") as? String

  private fun Map<*, *>?.stringMap(): Map<String, String> =
    this
      .orEmpty()
      .mapNotNull { (key, value) -> (key as? String)?.let { it to value.toString() } }
      .toMap()

  private fun Map<*, *>?.zanzibarMap(): Map<String, String> =
    this
      .orEmpty()
      .filterKeys { key -> key !in listOf("user-source", "userSource") }
      .stringMap()

  private fun Map<*, *>?.zanzibarUserSource(): GeneratedZanzibarUserSource? {
    val userSource = this?.mapValue("user-source") ?: this?.mapValue("userSource") ?: return null
    val jwt = userSource.mapValue("jwt")?.zanzibarJwtUserSource()
    return GeneratedZanzibarUserSource(jwt = jwt).takeUnless { it == GeneratedZanzibarUserSource() }
  }

  private fun Map<*, *>.zanzibarJwtUserSource(): GeneratedZanzibarJwtUserSource? {
    val claims = listMember("claims").mapNotNull { value -> value as? String }
    val principalFallback = booleanValue("principal-fallback") ?: booleanValue("principalFallback") ?: false
    return GeneratedZanzibarJwtUserSource(
      claims = claims,
      principalFallback = principalFallback,
    ).takeUnless { it == GeneratedZanzibarJwtUserSource() }
  }

  private fun Map<*, *>?.listMember(name: String): List<*> = this?.get(name) as? List<*> ?: listOf<Any?>()

  private fun Map<*, *>?.booleanValue(name: String): Boolean? = this?.get(name) as? Boolean

  private fun scalar(
    name: String,
    nullable: Boolean = false,
    format: String? = null,
  ): GeneratedTypeRef = GeneratedTypeRef.scalar(name, nullable = nullable, format = format)

  private fun documentation(
    summary: String? = null,
    description: String? = null,
  ): GeneratedDocumentation? =
    GeneratedDocumentation(summary = summary, description = description).takeUnless { it == GeneratedDocumentation() }

  private fun serviceName(apiName: String): String =
    apiName
      .removeSuffix(" API")
      .split(Regex("\\s+"))
      .joinToString("") { it.toUpperCamelCase() } + "Service"

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

  private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }

  private fun Map<*, *>.mapValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

  private fun Map<*, *>.listValue(name: String): List<Any?> = (this[name] as? List<*>) ?: emptyList()

  private fun Map<*, *>.stringValue(name: String): String? = this[name] as? String

  private data class ServiceIdentitySeed(
    val serviceLabel: String,
    val identity: GeneratedIdentity,
    val serviceTag: String? = null,
  )

  private data class OperationFragment(
    val operation: GeneratedOperation,
    val identity: GeneratedIdentity,
    val seed: ServiceIdentitySeed,
    val serviceJaxrs: GeneratedJaxrs?,
  )

  private data class ServiceFragment(
    val service: GeneratedService,
    val identity: GeneratedIdentity,
    val operationIdentities: List<Pair<GeneratedOperationIdentityKey, GeneratedIdentity>>,
  )

  private class OpenApiSourceDocument(
    val location: String,
    val source: Map<*, *>,
  ) {

    private fun Map<*, *>.mapValue(name: String): Map<*, *>? = this[name] as? Map<*, *>

    private fun Map<*, *>.listValue(name: String): List<Any?> = (this[name] as? List<*>) ?: emptyList()

    private fun Map<*, *>.stringValue(name: String): String? = this[name] as? String

    val info: Map<*, *> = source.mapValue("info").orEmpty()
    val title: String = info["title"] as? String ?: "API"
    val paths: Map<String, Map<*, *>> =
      source
        .mapValue("paths")
        .orEmpty()
        .mapNotNull { (path, item) ->
          (path as? String)?.let { it to (item as? Map<*, *>).orEmpty() }
        }.toMap()
    val components: Map<*, *> = source.mapValue("components").orEmpty()
    val schemas: Map<String, Map<*, *>> =
      components
        .mapValue("schemas")
        .orEmpty()
        .mapNotNull { (name, schema) ->
          (name as? String)?.let { it to (schema as? Map<*, *>).orEmpty() }
        }.toMap()
    val servers: List<Map<*, *>> = source.listValue("servers").mapNotNull { it as? Map<*, *> }
    val security: List<Any?> = source.listValue("security")
    val securitySchemes: Map<String, Map<*, *>> =
      components
        .mapValue("securitySchemes")
        .orEmpty()
        .mapNotNull { (name, scheme) ->
          (name as? String)?.let { it to (scheme as? Map<*, *>).orEmpty() }
        }.toMap()

    fun mapValue(name: String): Map<*, *>? = source.mapValue(name)

    fun listValue(name: String): List<Any?> = source.listValue(name)

    fun stringValue(name: String): String? = source.stringValue(name)

    companion object {

      fun read(location: String): OpenApiSourceDocument =
        OpenApiSourceDocument(
          location,
          URI(location).toURL().openStream().use { input ->
            ObjectMapper(YAMLFactory()).readValue(input, object : TypeReference<Map<String, Any?>>() {})
          },
        )
    }
  }

  private companion object {

    val httpMethods = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
    val unconstrainedSchemaKeys =
      setOf(
        "\$anchor",
        "\$comment",
        "\$defs",
        "\$dynamicAnchor",
        "\$id",
        "\$schema",
        "default",
        "definitions",
        "deprecated",
        "description",
        "example",
        "examples",
        "externalDocs",
        "nullable",
        "readOnly",
        "title",
        "writeOnly",
        "xml",
      )
    val yamlMapper = ObjectMapper(YAMLFactory())
    var activeDocument: OpenApiSourceDocument? = null
  }
}
