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
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
class GeneratedApiComposerTest {

  @Test
  fun `composes source fragments by explicit api and service identity`() {

    val projectModel = projectModel()
    val openApi =
      fragment(
        kind = GeneratedSourceSpec.Kind.OPENAPI,
        apiName = "Craft HTTP API",
        apiId = GeneratedIdentity.explicit("craft"),
        services =
          listOf(
            service(
              name = "ProjectsService",
              operations = listOf(operation("getProject", "GET", "/projects/{projectId}")),
            ),
          ),
        serviceIdentities = mapOf("ProjectsService" to GeneratedIdentity.explicit("projects")),
        models = listOf(projectModel),
      )
    val asyncApi =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        apiName = "Craft Events API",
        apiId = GeneratedIdentity.explicit("craft"),
        services =
          listOf(
            service(
              name = "ProjectEventsService",
              operations = listOf(operation("projectChanged", "SUBSCRIBE", "project.changed")),
            ),
          ),
        serviceIdentities = mapOf("ProjectEventsService" to GeneratedIdentity.explicit("projects")),
        models = listOf(projectModel),
      )

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.name, equalTo("Craft HTTP API"))
    assertThat(api.services.map { it.name }, equalTo(listOf("ProjectsService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { it.id },
      equalTo(listOf("getProject", "projectChanged")),
    )
    assertThat(api.models, equalTo(listOf(projectModel)))
  }

  @Test
  fun `composes api level jaxrs metadata from later fragments`() {

    val restClient =
      GeneratedJaxrsRestClient(
        configKey = "platform",
        oidcClient = "graphs",
        providers = listOf("io.test.client.GraphsClientFilter"),
      )
    val openApi = fragment(apiName = "Craft HTTP API")
    val asyncApi =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        apiName = "Craft Events API",
        apiJaxrs = GeneratedJaxrs(restClient = restClient),
      )

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.jaxrs, equalTo(GeneratedJaxrs(restClient = restClient)))
  }

  @Test
  fun `merges jaxrs metadata without dropping non rest client fields`() {

    val merged =
      GeneratedJaxrs(
        asynchronous = true,
        reactive = false,
        sse = GeneratedModeFlag(client = true),
        context = listOf("uriInfo"),
        restClient = GeneratedJaxrsRestClient(configKey = "api", providers = listOf("io.test.ApiFilter")),
      ).mergeWith(
        GeneratedJaxrs(
          reactive = true,
          jsonBody = GeneratedModeFlag(server = true),
          context = listOf("request"),
          restClient = GeneratedJaxrsRestClient(oidcClient = "graphs", providers = listOf("io.test.GraphsFilter")),
        ),
      )

    assertThat(
      merged,
      equalTo(
        GeneratedJaxrs(
          asynchronous = true,
          reactive = true,
          sse = GeneratedModeFlag(client = true),
          jsonBody = GeneratedModeFlag(server = true),
          context = listOf("uriInfo", "request"),
          restClient =
            GeneratedJaxrsRestClient(
              configKey = "api",
              oidcClient = "graphs",
              providers = listOf("io.test.ApiFilter", "io.test.GraphsFilter"),
            ),
        ),
      ),
    )
  }

  @Test
  fun `rejects api id mismatches with api id override guidance`() {

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer()
          .compose(
            listOf(
              fragment(apiId = GeneratedIdentity.explicit("craft")),
              fragment(apiId = GeneratedIdentity.explicit("billing")),
            ),
          )
      }

    assertThat(failure.message, containsString("x-sunday-apiId"))
  }

  @Test
  fun `composes OpenAPI service fragment with AsyncAPI event fragment`(
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") testUri: URI,
  ) {

    val openApi = OpenApiToGeneratedApi().convertFragment(testUri)
    val asyncApi =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        apiName = "Craft Events API",
        apiId = GeneratedIdentity.explicit("craft"),
        services =
          listOf(
            service(
              name = "ProjectEventsService",
              operations = listOf(operation("projectChanged", "SUBSCRIBE", "project.changed")),
            ),
          ),
        serviceIdentities = mapOf("ProjectEventsService" to GeneratedIdentity.explicit("projects")),
        operationIdentities =
          mapOf(
            GeneratedOperationIdentityKey("ProjectEventsService", "projectChanged") to
              GeneratedIdentity.native("projectChanged"),
          ),
      )

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.name, equalTo("Craft HTTP API"))
    assertThat(api.services.map { it.name }, equalTo(listOf("ProjectsService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { it.id },
      equalTo(listOf("getProject", "projectChanged")),
    )
  }

  @Test
  fun `composes tag derived services with explicit service labels case insensitively`(
    @ResourceUri("openapi/ir/tag-service-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/explicit-service-case.yaml") asyncApiUri: URI,
  ) {

    val options = GeneratedApiIrOptions(deriveServicesFromTags = true)
    val openApi = OpenApiToGeneratedApi(options).convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi(options).convertFragment(asyncApiUri)

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.services.map { service -> service.name }, equalTo(listOf("ScriptService")))
    assertThat(
      api
        .services
        .single()
        .operations
        .map { operation -> operation.id },
      equalTo(listOf("getScript", "streamEvents")),
    )
  }

  @Test
  fun `composes OpenAPI and AsyncAPI fragments with multiple services models and protocol metadata`(
    @ResourceUri("openapi/ir/composition-audit-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/composition-audit.yaml") asyncApiUri: URI,
  ) {

    val openApi = OpenApiToGeneratedApi().convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi().convertFragment(asyncApiUri)

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))

    assertThat(api.name, equalTo("Craft HTTP API"))
    assertThat(api.protocol, equalTo(asyncApi.api.protocol))
    assertThat(api.services.map { service -> service.name }, equalTo(listOf("ProjectsService", "UsersService")))
    assertThat(
      api.services.associate { service ->
        service.name to service.operations.map { operation -> operation.id }
      },
      equalTo(
        mapOf(
          "ProjectsService" to listOf("getProject", "projectChanged"),
          "UsersService" to listOf("getUser", "userChanged"),
        ),
      ),
    )
    assertThat(
      api.services.map { service -> service.baseUri },
      equalTo(listOf("broker.example.com:9092", "broker.example.com:9092")),
    )
    assertThat(
      api.models.map { model -> model.name },
      equalTo(listOf("Project", "User", "ProjectChanged", "UserChanged")),
    )
  }

  @Test
  fun `dedupes OpenAPI and AsyncAPI discriminated models with referenced validation metadata`(
    @ResourceUri("openapi/ir/composition-discriminated-dedupe-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/composition-discriminated-dedupe.yaml") asyncApiUri: URI,
  ) {

    val openApi = OpenApiToGeneratedApi().convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi().convertFragment(asyncApiUri)

    val api = GeneratedApiComposer().compose(listOf(openApi, asyncApi))
    val scopeSummaries = api.models.filter { model -> model.name == "NarrativeScopeSummary" }
    val idProperty = scopeSummaries.single().properties.single { property -> property.name == "id" }

    assertThat(scopeSummaries.size, equalTo(1))
    assertThat(idProperty.type, equalTo(GeneratedTypeRef.named("UniqueId")))
    assertThat(idProperty.validation, equalTo(mapOf("pattern" to "^[A-Z2-7]{26}$")))
  }

  @Test
  fun `rejects real OpenAPI and AsyncAPI operation collisions with override guidance`(
    @ResourceUri("openapi/ir/composition-operation-collision-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/project-events.yaml") asyncApiUri: URI,
  ) {

    val openApi = OpenApiToGeneratedApi().convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi().convertFragment(asyncApiUri)

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer().compose(listOf(openApi, asyncApi))
      }

    assertThat(failure.message, containsString("projectChanged"))
    assertThat(failure.message, containsString("ProjectsService"))
    assertThat(failure.message, containsString("x-sunday-operationId"))
  }

  @Test
  fun `rejects real OpenAPI and AsyncAPI model collisions with override guidance`(
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/composition-model-collision.yaml") asyncApiUri: URI,
  ) {

    val openApi = OpenApiToGeneratedApi().convertFragment(openApiUri)
    val asyncApi = AsyncApiToGeneratedApi().convertFragment(asyncApiUri)

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer().compose(listOf(openApi, asyncApi))
      }

    assertThat(failure.message, containsString("project"))
    assertThat(failure.message, containsString("Project"))
    assertThat(failure.message, containsString("x-sunday-modelName"))
  }

  @Test
  fun `dedupes model collisions with matching structural signatures`() {

    val openApiSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "openapi.yaml")
    val asyncApiSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "asyncapi.yaml")
    val first =
      fragment(
        models =
          listOf(
            projectModelWithSource(
              openApiSource,
              GeneratedTypeRef.named("UniqueId", source = openApiSource),
            ),
          ),
        modelIdentities = mapOf("Project" to GeneratedIdentity.generated("project")),
      )
    val second =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        models =
          listOf(
            projectModelWithSource(
              asyncApiSource,
              GeneratedTypeRef.named("UniqueId", source = asyncApiSource),
            ),
          ),
        modelIdentities = mapOf("Project" to GeneratedIdentity.generated("project")),
      )

    val api = GeneratedApiComposer().compose(listOf(first, second))

    assertThat(api.models.map { model -> model.name }, equalTo(listOf("Project")))
    assertThat(api.models.single(), equalTo(first.api.models.single()))
  }

  @Test
  fun `rejects model collisions with matching structural signatures but different generated names`() {

    val first =
      fragment(
        models = listOf(projectModel()),
        modelIdentities = mapOf("Project" to GeneratedIdentity.generated("project")),
      )
    val second =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        models = listOf(projectModel().copy(name = "ProjectView")),
        modelIdentities = mapOf("ProjectView" to GeneratedIdentity.generated("project")),
      )

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer().compose(listOf(first, second))
      }

    assertThat(failure.message, containsString("project"))
    assertThat(failure.message, containsString("ProjectView"))
    assertThat(failure.message, containsString("x-sunday-modelName"))
  }

  @Test
  fun `rejects default-derived operation collisions with operation id override guidance`() {

    val first =
      fragment(
        services =
          listOf(
            service(
              name = "ProjectsService",
              operations = listOf(operation("projectChanged", "SUBSCRIBE", "project.changed")),
            ),
          ),
        serviceIdentities = mapOf("ProjectsService" to GeneratedIdentity.explicit("projects")),
        operationIdentities =
          mapOf(
            GeneratedOperationIdentityKey("ProjectsService", "projectChanged") to
              GeneratedIdentity.generated("projectChanged"),
          ),
      )
    val second =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        services =
          listOf(
            service(
              name = "ProjectEventsService",
              operations = listOf(operation("projectChanged", "SUBSCRIBE", "project.updated")),
            ),
          ),
        serviceIdentities = mapOf("ProjectEventsService" to GeneratedIdentity.explicit("projects")),
        operationIdentities =
          mapOf(
            GeneratedOperationIdentityKey("ProjectEventsService", "projectChanged") to
              GeneratedIdentity.generated("projectChanged"),
          ),
      )

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer().compose(listOf(first, second))
      }

    assertThat(failure.message, containsString("x-sunday-operationId"))
  }

  @Test
  fun `rejects default-derived model collisions with model name override guidance`() {

    val first =
      fragment(
        models = listOf(projectModel()),
        modelIdentities = mapOf("Project" to GeneratedIdentity.generated("project")),
      )
    val second =
      fragment(
        kind = GeneratedSourceSpec.Kind.ASYNCAPI,
        models =
          listOf(
            projectModel(
              GeneratedModelProperty(
                name = "displayName",
                type = GeneratedTypeRef.scalar("string"),
              ),
            ),
          ),
        modelIdentities = mapOf("Project" to GeneratedIdentity.generated("project")),
      )

    val failure =
      assertThrows(GeneratedApiCompositionException::class.java) {
        GeneratedApiComposer().compose(listOf(first, second))
      }

    assertThat(failure.message, containsString("x-sunday-modelName"))
    assertThat(failure.message, containsString("openapi 'Craft API.yaml'"))
    assertThat(failure.message, containsString("asyncapi 'Craft API.yaml'"))
  }

  @Test
  fun `composes operation-local response models with same name and different statuses`() {

    val api =
      GeneratedApiComposer()
        .compose(
          listOf(
            fragment(
              models =
                listOf(
                  operationResponseModel(400, "validationCode"),
                  operationResponseModel(404, "missingResource"),
                ),
            ),
          ),
        )

    assertThat(
      api.models.map { model -> model.scope?.status },
      equalTo(listOf(400, 404)),
    )
  }

  private fun fragment(
    kind: GeneratedSourceSpec.Kind = GeneratedSourceSpec.Kind.OPENAPI,
    apiName: String = "Craft API",
    apiId: GeneratedIdentity = GeneratedIdentity.explicit("craft"),
    services: List<GeneratedService> = listOf(),
    serviceIdentities: Map<String, GeneratedIdentity> = mapOf(),
    operationIdentities: Map<GeneratedOperationIdentityKey, GeneratedIdentity> = mapOf(),
    models: List<GeneratedModel> = listOf(),
    modelIdentities: Map<String, GeneratedIdentity> = mapOf(),
    apiJaxrs: GeneratedJaxrs? = null,
  ): GeneratedApiFragment =
    GeneratedApiFragment(
      api =
        GeneratedApi(
          name = apiName,
          source = GeneratedSourceSpec(kind = kind, location = "$apiName.yaml"),
          services = services,
          models = models,
          jaxrs = apiJaxrs,
        ),
      apiId = apiId,
      serviceIdentities = serviceIdentities,
      operationIdentities = operationIdentities,
      modelIdentities = modelIdentities,
    )

  private fun service(
    name: String,
    operations: List<GeneratedOperation>,
  ): GeneratedService =
    GeneratedService(
      name = name,
      operations = operations,
    )

  private fun operation(
    id: String,
    method: String,
    path: String,
  ): GeneratedOperation =
    GeneratedOperation(
      id = id,
      method = method,
      path = path,
    )

  private fun projectModel(vararg extraProperties: GeneratedModelProperty): GeneratedModel =
    GeneratedModel(
      name = "Project",
      kind = GeneratedModel.Kind.OBJECT,
      properties =
        listOf(
          GeneratedModelProperty(
            name = "id",
            type = GeneratedTypeRef.scalar("string"),
            required = true,
          ),
        ) + extraProperties,
    )

  private fun projectModelWithSource(
    source: GeneratedSourceSpec,
    idType: GeneratedTypeRef,
  ): GeneratedModel =
    GeneratedModel(
      name = "Project",
      kind = GeneratedModel.Kind.OBJECT,
      source = source,
      properties =
        listOf(
          GeneratedModelProperty(
            name = "id",
            type = idType,
            required = true,
          ),
        ),
      documentation = GeneratedDocumentation(description = "Description from ${source.location}"),
    )

  private fun operationResponseModel(
    status: Int,
    propertyName: String,
  ): GeneratedModel =
    GeneratedModel(
      name = "UpdateWorkingGraphCheckoutTargetResponseBody",
      kind = GeneratedModel.Kind.OBJECT,
      scope =
        GeneratedModelScope(
          service = "ReposService",
          operation = "updateWorkingGraphCheckoutTarget",
          usage = GeneratedModelScope.Usage.RESPONSE_BODY,
          status = status,
        ),
      properties =
        listOf(
          GeneratedModelProperty(
            name = propertyName,
            type = GeneratedTypeRef.scalar("string"),
          ),
        ),
    )
}
