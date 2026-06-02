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

import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class AsyncApiToGeneratedApiTest {

  @Test
  fun `maps AsyncAPI subscribe channel to generated API fragment`(
    @ResourceUri("asyncapi/ir/project-events.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(fragment.apiId, equalTo(GeneratedIdentity.explicit("craft")))
    assertThat(fragment.api.source.kind, equalTo(GeneratedSourceSpec.Kind.ASYNCAPI))
    assertThat(
      fragment.serviceIdentities,
      equalTo(mapOf("ProjectsService" to GeneratedIdentity.explicit("projects"))),
    )
    assertThat(
      fragment.operationIdentities,
      equalTo(
        mapOf(
          GeneratedOperationIdentityKey("ProjectsService", "projectChanged") to
            GeneratedIdentity.native("projectChanged"),
        ),
      ),
    )

    assertThat(
      fragment.api.services.single(),
      equalTo(
        GeneratedService(
          name = "ProjectsService",
          operations =
            listOf(
              GeneratedOperation(
                id = "projectChanged",
                method = "SUBSCRIBE",
                path = "project.changed",
                responses =
                  listOf(
                    GeneratedResponse(
                      type = GeneratedTypeRef.named("ProjectChanged"),
                      mediaTypes = listOf("application/json"),
                    ),
                  ),
                streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
                documentation = GeneratedDocumentation(summary = "Project changed"),
              ),
            ),
          media = GeneratedMedia(response = listOf("application/json")),
        ),
      ),
    )
    assertThat(
      fragment.api.models,
      equalTo(
        listOf(
          GeneratedModel(
            name = "ProjectChanged",
            kind = GeneratedModel.Kind.OBJECT,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, fragment.api.source.location),
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "projectId",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
                GeneratedModelProperty(
                  name = "name",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `composes AsyncAPI events into matching OpenAPI service`(
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/project-events.yaml") asyncApiUri: URI,
  ) {
    val openApi = OpenApiToGeneratedApi().convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi().convertFragment(asyncApiUri)

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.services.map { it.name }, equalTo(listOf("ProjectsService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { operation -> operation.id },
      equalTo(listOf("getProject", "projectChanged")),
    )
  }

  @Test
  fun `maps path-like AsyncAPI channel names to valid service names`(
    @ResourceUri("asyncapi/ir/path-channel.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(
      fragment.serviceIdentities,
      equalTo(mapOf("EventsService" to GeneratedIdentity.native("events"))),
    )
    assertThat(
      fragment
        .api
        .services
        .single()
        .name,
      equalTo("EventsService"),
    )
  }

  @Test
  fun `maps AsyncAPI channel address parameters to operation path parameters`(
    @ResourceUri("asyncapi/ir/channel-parameters.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(
      fragment
        .api
        .services
        .single()
        .operations
        .single()
        .parameters,
      equalTo(
        listOf(
          GeneratedParameter(
            name = "repoId",
            location = GeneratedParameter.Location.PATH,
            type = GeneratedTypeRef.scalar("string"),
            required = true,
            encoding = GeneratedParameterEncoding(style = "simple"),
            documentation =
              GeneratedDocumentation(
                description = "Repository id whose narrative events are streamed.",
              ),
          ),
        ),
      ),
    )
  }

  @Test
  fun `derives AsyncAPI service names from operation tags when enabled`(
    @ResourceUri("asyncapi/ir/tag-services.yaml") testUri: URI,
  ) {
    val fragment =
      AsyncApiToGeneratedApi(GeneratedApiIrOptions(deriveServicesFromTags = true))
        .convertFragment(testUri)

    assertThat(
      fragment.serviceIdentities,
      equalTo(mapOf("ProjectsService" to GeneratedIdentity.native("projects"))),
    )
    assertThat(
      fragment
        .api
        .services
        .single()
        .name,
      equalTo("ProjectsService"),
    )
  }

  @Test
  fun `maps AsyncAPI operation and message metadata to generated API fragment`(
    @ResourceUri("asyncapi/ir/operation-surface.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(
      fragment.operationIdentities,
      equalTo(
        mapOf(
          GeneratedOperationIdentityKey("ProjectsService", "sendProjectCommand") to
            GeneratedIdentity.explicit("sendProjectCommand"),
          GeneratedOperationIdentityKey("ProjectsService", "projectCommandAccepted") to
            GeneratedIdentity.native("projectCommandAccepted"),
        ),
      ),
    )
    assertThat(
      fragment
        .api
        .services
        .single()
        .operations,
      equalTo(
        listOf(
          GeneratedOperation(
            id = "sendProjectCommand",
            method = "PUBLISH",
            path = "project.commands",
            parameters =
              listOf(
                GeneratedParameter(
                  name = "xRequestId",
                  location = GeneratedParameter.Location.HEADER,
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                  serializationName = "x-request-id",
                ),
              ),
            requestBody =
              GeneratedPayload(
                type = GeneratedTypeRef.named("ProjectCommand"),
                mediaTypes = listOf("application/json"),
                examples =
                  listOf(
                    GeneratedExample(
                      name = "rename",
                      value = mapOf("commandId" to "cmd-1", "projectId" to "project-1", "name" to "Renamed"),
                      documentation = GeneratedDocumentation(summary = "Rename project"),
                    ),
                  ),
              ),
            exchange = GeneratedExchange.REQUEST,
            documentation =
              GeneratedDocumentation(
                summary = "Send project command",
                description = "Publishes project command messages.",
              ),
          ),
          GeneratedOperation(
            id = "projectCommandAccepted",
            method = "SUBSCRIBE",
            path = "project.command.accepted",
            responses =
              listOf(
                GeneratedResponse(
                  type = GeneratedTypeRef.named("ProjectCommandAccepted"),
                  mediaTypes = listOf("application/json"),
                  headers =
                    listOf(
                      GeneratedParameter(
                        name = "xTraceId",
                        location = GeneratedParameter.Location.HEADER,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                        serializationName = "x-trace-id",
                      ),
                    ),
                ),
              ),
            streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
            documentation = GeneratedDocumentation(summary = "Project command accepted"),
          ),
        ),
      ),
    )
    assertThat(
      fragment.api.models.map { model -> model.name },
      equalTo(listOf("ProjectCommand", "ProjectCommandAccepted")),
    )
  }

  @Test
  fun `maps AsyncAPI schema breadth to generated API fragment`(
    @ResourceUri("asyncapi/ir/schema-breadth.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(
      fragment
        .api
        .services
        .single()
        .operations
        .single()
        .responses
        .single()
        .type,
      equalTo(GeneratedTypeRef.named("ProjectSnapshot")),
    )
    assertThat(
      fragment.api.models,
      equalTo(
        listOf(
          GeneratedModel(
            name = "ProjectSnapshot",
            kind = GeneratedModel.Kind.OBJECT,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, fragment.api.source.location),
            properties =
              listOf(
                GeneratedModelProperty(
                  name = "projectId",
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                  serializationName = "project-id",
                  validation =
                    mapOf(
                      "minLength" to "3",
                      "maxLength" to "64",
                      "pattern" to "^[a-z0-9-]+$",
                    ),
                ),
                GeneratedModelProperty(
                  name = "occurredAt",
                  type = GeneratedTypeRef.scalar("string", format = "date-time"),
                  required = true,
                ),
                GeneratedModelProperty(
                  name = "status",
                  type = GeneratedTypeRef.named("ProjectStatus"),
                  required = true,
                ),
                GeneratedModelProperty(
                  name = "tags",
                  type =
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                      collection = GeneratedCollectionKind.SET,
                    ),
                  required = true,
                  validation =
                    mapOf(
                      "minItems" to "1",
                      "uniqueItems" to "true",
                    ),
                ),
                GeneratedModelProperty(
                  name = "labels",
                  type =
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.MAP,
                      name = "map",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                ),
                GeneratedModelProperty(
                  name = "nickname",
                  type = GeneratedTypeRef.scalar("string", nullable = true),
                ),
              ),
          ),
          GeneratedModel(
            name = "ProjectStatus",
            kind = GeneratedModel.Kind.ENUM,
            source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, fragment.api.source.location),
            values = listOf("active", "archived"),
          ),
        ),
      ),
    )
  }

  @Test
  fun `maps AsyncAPI event envelope data payloads to externally discriminated models`(
    @ResourceUri("asyncapi/ir/typed-event-envelope.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)
    val models = fragment.api.models.associateBy { model -> model.name }

    assertThat(
      models["EventEnvelope"]
        ?.properties
        ?.firstOrNull { property -> property.name == "data" },
      equalTo(
        GeneratedModelProperty(
          name = "data",
          type = GeneratedTypeRef.named("EventData"),
          required = true,
          externalDiscriminator = "type",
        ),
      ),
    )
    assertThat(
      models["EventData"],
      equalTo(
        GeneratedModel(
          name = "EventData",
          kind = GeneratedModel.Kind.UNION,
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, fragment.api.source.location),
          aliases =
            listOf(
              GeneratedTypeRef.named("ProjectCreatedData"),
              GeneratedTypeRef.named("ProjectDeletedData"),
            ),
          discriminator = "type",
          discriminatorMappings =
            mapOf(
              "project.created" to GeneratedTypeRef.named("ProjectCreatedData"),
              "project.deleted" to GeneratedTypeRef.named("ProjectDeletedData"),
            ),
        ),
      ),
    )
    assertThat(
      models["ProjectCreatedData"]?.inherits,
      equalTo(emptyList()),
    )
    assertThat(models["ProjectCreatedData"]?.discriminatorValue, equalTo(null))
    assertThat(
      models["ProjectDeletedData"]?.inherits,
      equalTo(emptyList()),
    )
    assertThat(models["ProjectDeletedData"]?.discriminatorValue, equalTo(null))
  }

  @Test
  fun `maps AsyncAPI 3_1 top-level operations and event payload schemas`(
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)
    val models = fragment.api.models.associateBy { model -> model.name }
    val operation =
      fragment
        .api
        .services
        .single()
        .operations
        .single()
    val dataProperty =
      models["EventEnvelope"]
        ?.properties
        ?.firstOrNull { property -> property.name == "data" }

    assertThat(
      fragment.serviceIdentities,
      equalTo(mapOf("EventsService" to GeneratedIdentity.explicit("events"))),
    )
    assertThat(
      operation,
      equalTo(
        GeneratedOperation(
          id = "streamEvents",
          method = "SUBSCRIBE",
          path = "/events",
          responses =
            listOf(
              GeneratedResponse(
                type = GeneratedTypeRef.named("EventEnvelope"),
                mediaTypes = listOf("application/json"),
              ),
            ),
          streaming = GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_STREAM),
          documentation = GeneratedDocumentation(summary = "Stream project events"),
        ),
      ),
    )
    assertThat(
      dataProperty,
      equalTo(
        GeneratedModelProperty(
          name = "data",
          type = GeneratedTypeRef.named("EventData"),
          required = true,
          externalDiscriminator = "type",
        ),
      ),
    )
    assertThat(models["ProjectCreatedData"]?.inherits, equalTo(emptyList()))
    assertThat(models["ProjectCreatedData"]?.discriminatorValue, equalTo(null))
  }

  @Test
  fun `maps AsyncAPI protocol and security metadata to generated API fragment`(
    @ResourceUri("asyncapi/ir/protocol-security.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)

    assertThat(
      fragment.api.auth,
      equalTo(
        GeneratedAuth(
          schemes = listOf("userPassword"),
          requirements = listOf(GeneratedSecurityRequirement(schemes = listOf("userPassword"))),
          securitySchemes =
            listOf(
              GeneratedSecurityScheme(
                name = "userPassword",
                type = "userPassword",
              ),
            ),
        ),
      ),
    )
    assertThat(
      fragment.api.protocol,
      equalTo(
        GeneratedProtocol(
          servers =
            listOf(
              GeneratedServer(
                name = "production",
                url = "broker.example.com:9092",
                protocol = "kafka",
                protocolVersion = "3.6",
                auth =
                  GeneratedAuth(
                    schemes = listOf("userPassword"),
                    requirements = listOf(GeneratedSecurityRequirement(schemes = listOf("userPassword"))),
                    securitySchemes =
                      listOf(
                        GeneratedSecurityScheme(
                          name = "userPassword",
                          type = "userPassword",
                        ),
                      ),
                  ),
                bindings =
                  listOf(
                    GeneratedProtocolBinding(
                      kind = GeneratedProtocolBinding.Kind.SERVER,
                      protocol = "kafka",
                      values =
                        mapOf(
                          "schemaRegistryUrl" to "https://schema.example.com",
                          "schemaRegistryVendor" to "confluent",
                        ),
                    ),
                  ),
              ),
            ),
        ),
      ),
    )

    val service = fragment.api.services.single()
    assertThat(service.baseUri, equalTo("broker.example.com:9092"))

    val operation = service.operations.single()
    assertThat(
      operation.protocol,
      equalTo(
        GeneratedProtocol(
          bindings =
            listOf(
              GeneratedProtocolBinding(
                kind = GeneratedProtocolBinding.Kind.CHANNEL,
                protocol = "kafka",
                values =
                  mapOf(
                    "topic" to "projects",
                    "partitions" to 12,
                    "replicas" to 3,
                  ),
              ),
              GeneratedProtocolBinding(
                kind = GeneratedProtocolBinding.Kind.OPERATION,
                protocol = "kafka",
                values =
                  mapOf(
                    "groupId" to mapOf("type" to "string"),
                    "clientId" to mapOf("type" to "string"),
                  ),
              ),
            ),
        ),
      ),
    )
  }

  @Test
  fun `maps OpenAPI-style AsyncAPI discriminator objects to generated union mappings`(
    @ResourceUri("asyncapi/ir/openapi-style-discriminator.yaml") testUri: URI,
  ) {
    val fragment = AsyncApiToGeneratedApi().convertFragment(testUri)
    val models = fragment.api.models.associateBy { model -> model.name }

    assertThat(
      models["EventIdentity"],
      equalTo(
        GeneratedModel(
          name = "EventIdentity",
          kind = GeneratedModel.Kind.UNION,
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, fragment.api.source.location),
          aliases =
            listOf(
              GeneratedTypeRef.named("UserIdentity"),
              GeneratedTypeRef.named("ServiceIdentity"),
            ),
          discriminator = "kind",
          discriminatorMappings =
            mapOf(
              "user" to GeneratedTypeRef.named("UserIdentity"),
              "service" to GeneratedTypeRef.named("ServiceIdentity"),
            ),
        ),
      ),
    )
  }
}
