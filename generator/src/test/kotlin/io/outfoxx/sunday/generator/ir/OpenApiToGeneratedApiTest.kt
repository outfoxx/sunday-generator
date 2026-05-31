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

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(ResourceExtension::class)
class OpenApiToGeneratedApiTest {

  @Test
  fun `maps OpenAPI 3_1 document to generated API IR`(
    @ResourceUri("openapi/ir/project-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("project-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI defaults to normalized composition identities`(
    @ResourceUri("openapi/ir/project-3.1.yaml") testUri: URI,
  ) {
    val fragment = OpenApiToGeneratedApi().convertFragment(testUri)

    assertEquals(GeneratedIdentity.native("projectsApi"), fragment.apiId)
    assertEquals(
      mapOf("ProjectsService" to GeneratedIdentity.native("projects")),
      fragment.serviceIdentities,
    )
    assertEquals(
      mapOf(
        GeneratedOperationIdentityKey("ProjectsService", "getProject") to GeneratedIdentity.native("getProject"),
      ),
      fragment.operationIdentities,
    )
    assertEquals(mapOf("Project" to GeneratedIdentity.native("project")), fragment.modelIdentities)
  }

  @Test
  fun `maps OpenAPI Sunday composition identities`(
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") testUri: URI,
  ) {
    val fragment = OpenApiToGeneratedApi().convertFragment(testUri)

    assertEquals(GeneratedIdentity.explicit("craft"), fragment.apiId)
    assertEquals(
      mapOf("ProjectsService" to GeneratedIdentity.explicit("projects")),
      fragment.serviceIdentities,
    )
    assertEquals(
      mapOf(
        GeneratedOperationIdentityKey("ProjectsService", "getProject") to GeneratedIdentity.explicit("getProject"),
      ),
      fragment.operationIdentities,
    )
    assertEquals(
      "getProject",
      fragment
        .api
        .services
        .single()
        .operations
        .single()
        .id,
    )
  }

  @Test
  fun `maps OpenAPI 3_1 operation surface to generated API IR`(
    @ResourceUri("openapi/ir/operation-surface-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("operation-surface-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI streaming request body metadata`(
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    val requestBody =
      api
        .services
        .single()
        .operations
        .single { operation -> operation.id == "importArchive" }
        .requestBody

    assertEquals(GeneratedModeFlag(client = true), requestBody?.streaming)
  }

  @Test
  fun `maps OpenAPI 3_1 path item parameters to generated API IR`(
    @ResourceUri("openapi/ir/path-item-parameters-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("path-item-parameters-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI 3_1 component operation parameters with referenced request bodies`(
    @ResourceUri("openapi/ir/component-operation-parameters-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    val operation =
      api
        .services
        .single()
        .operations
        .single()
    assertEquals(
      listOf(
        GeneratedParameter(
          name = "projectId",
          location = GeneratedParameter.Location.PATH,
          type = GeneratedTypeRef.scalar("string"),
          required = true,
          encoding = GeneratedParameterEncoding(style = "simple", explode = false),
        ),
        GeneratedParameter(
          name = "contentType",
          location = GeneratedParameter.Location.HEADER,
          type = GeneratedTypeRef.named("AvatarContentType"),
          required = true,
          encoding = GeneratedParameterEncoding(style = "simple", explode = false),
        ),
      ),
      operation.parameters,
    )
    assertEquals(
      GeneratedPayload(
        type = GeneratedTypeRef.scalar("file", format = "binary"),
        mediaTypes = listOf("application/octet-stream"),
      ),
      operation.requestBody,
    )
  }

  @Test
  fun `preserves OpenAPI operation order and same schema response media`(
    @ResourceUri("openapi/ir/source-order-media-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)
    val operations = api.services.single().operations

    assertEquals(listOf("updateUser", "deleteUser", "getUserAvatar"), operations.map { operation -> operation.id })
    assertEquals(
      listOf("image/png", "image/jpeg", "image/webp"),
      operations
        .single { operation -> operation.id == "getUserAvatar" }
        .responses
        .single()
        .mediaTypes,
    )
  }

  @Test
  fun `maps OpenAPI 3_1 schema breadth to generated API IR`(
    @ResourceUri("openapi/ir/schema-breadth-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("schema-breadth-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI enum varnames to generated API IR`(
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    val notificationType = api.models.single { model -> model.name == "NotificationType" }
    assertEquals(
      listOf(
        "notification.pull_request.review_requested",
        "notification.pull_request.merged",
        "notification.team.member_added",
      ),
      notificationType.values,
    )
    assertEquals(
      listOf("pullRequestReviewRequested", "pullRequestMerged", "teamMemberAdded"),
      notificationType.enumValueNames,
    )
  }

  @Test
  fun `maps OpenAPI single allOf reference properties to referenced model types`(
    @ResourceUri("openapi/ir/single-allof-ref-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    val requestModel = api.models.single { model -> model.name == "UpdateBranchRequest" }
    assertEquals(
      GeneratedTypeRef.named("BranchVisibility"),
      requestModel.properties.single { property -> property.name == "visibility" }.type,
    )
  }

  @Test
  fun `maps OpenAPI scalar component schemas to scalar alias models`(
    @ResourceUri("openapi/ir/scalar-alias-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    val uniqueId = api.models.single { model -> model.name == "UniqueId" }
    val update = api.models.single { model -> model.name == "NarrativeScopeUpdate" }
    val location = update.properties.single { property -> property.name == "location" }

    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, uniqueId.kind)
    assertEquals(listOf(GeneratedTypeRef.scalar("string")), uniqueId.aliases)
    assertEquals(mapOf("pattern" to "^[A-Z2-7]{26}$"), uniqueId.validation)
    assertEquals(GeneratedTypeRef.named("UniqueId"), location.type)
    assertEquals(mapOf("pattern" to "^[A-Z2-7]{26}$"), location.validation)
  }

  @Test
  fun `maps OpenAPI JAX-RS REST client metadata from root and service tags`(
    @ResourceUri("openapi/ir/jaxrs-rest-client-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(
      GeneratedJaxrs(restClient = GeneratedJaxrsRestClient(configKey = "platform")),
      api.jaxrs,
    )
    assertEquals(
      GeneratedJaxrs(
        restClient =
          GeneratedJaxrsRestClient(
            configKey = "graphs",
            oidcClient = "graphs",
            providers = listOf("io.test.client.GraphsClientFilter"),
          ),
      ),
      api.services.single().jaxrs,
    )
    assertEquals(api.services.single().name, "GraphsService")
    assertEquals(
      api.services.single().jaxrs,
      api.tags.single().jaxrs,
    )
  }

  @Test
  fun `maps OpenAPI target-specific exclusions and JAX-RS context metadata for server targets`(
    @ResourceUri("openapi/ir/jaxrs-exclusions-3.1.yaml") testUri: URI,
  ) {
    val api =
      OpenApiToGeneratedApi(GeneratedApiIrOptions(generationMode = GenerationMode.Server))
        .convert(testUri)
    val service = api.services.single()
    val operation = service.operations.single()

    assertEquals("importArchive", operation.id)
    assertEquals(
      listOf("repoId", "reset", "xImportId"),
      operation.parameters.map { parameter -> parameter.name },
    )
    assertEquals(null, operation.requestBody)
    assertEquals(GeneratedJaxrs(context = listOf("routingContext")), operation.jaxrs)
    assertEquals(listOf("ImportsService"), api.services.map { service -> service.name })
  }

  @Test
  fun `maps OpenAPI target-specific exclusions and JAX-RS context metadata for client targets`(
    @ResourceUri("openapi/ir/jaxrs-exclusions-3.1.yaml") testUri: URI,
  ) {
    val api =
      OpenApiToGeneratedApi(GeneratedApiIrOptions(generationMode = GenerationMode.Client))
        .convert(testUri)
    val operations = api.services.flatMap { service -> service.operations }
    val operation = operations.single { current -> current.id == "importArchive" }

    assertEquals(
      listOf("repoId", "trace", "reset", "xImportId"),
      operation.parameters.map { parameter -> parameter.name },
    )
    assertEquals(GeneratedTypeRef.scalar("file", format = "binary"), operation.requestBody?.type)
    assertEquals(GeneratedJaxrs(context = listOf("headers")), operation.jaxrs)
    assertEquals(listOf("getInternal", "importArchive"), operations.map { current -> current.id }.sorted())
  }

  @Test
  fun `maps OpenAPI empty schemas to any JSON values`(
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)
    val operation =
      api
        .services
        .flatMap { service -> service.operations }
        .single { operation -> operation.id == "updateValue" }
    val anyJson = api.models.single { model -> model.name == "AnyJson" }
    val anyHolder = api.models.single { model -> model.name == "AnyHolder" }
    val entityStatePropertyValue = api.models.single { model -> model.name == "EntityStatePropertyValue" }

    assertEquals(GeneratedTypeRef.scalar("any"), operation.requestBody?.type)
    assertEquals(GeneratedTypeRef.named("AnyJson"), operation.responses.single().type)
    assertEquals(GeneratedModel.Kind.SCALAR_ALIAS, anyJson.kind)
    assertEquals(listOf(GeneratedTypeRef.scalar("any")), anyJson.aliases)
    assertEquals(
      GeneratedTypeRef.scalar("any"),
      anyHolder.properties.single { property -> property.name == "value" }.type,
    )
    assertEquals(
      GeneratedTypeRef.scalar("any"),
      anyHolder.properties.single { property -> property.name == "documented" }.type,
    )
    assertEquals(
      GeneratedTypeRef.named("AnyJson"),
      anyHolder.properties.single { property -> property.name == "named" }.type,
    )
    assertEquals(GeneratedModel.Kind.OBJECT, entityStatePropertyValue.kind)
    assertEquals(listOf(GeneratedTypeRef.named("EntityStateProperty")), entityStatePropertyValue.inherits)
    assertEquals("value", entityStatePropertyValue.discriminatorValue)
    assertEquals(
      GeneratedTypeRef.scalar("any"),
      entityStatePropertyValue.properties.single { property -> property.name == "value" }.type,
    )
  }

  @Test
  fun `maps OpenAPI 3_1 composition and discriminators to generated API IR`(
    @ResourceUri("openapi/ir/composition-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("composition-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI 3_1 additional properties to generated API IR`(
    @ResourceUri("openapi/ir/additional-properties-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("additional-properties-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI 3_1 lifecycle metadata to generated API IR`(
    @ResourceUri("openapi/ir/lifecycle-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("lifecycle-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI 3_1 security metadata to generated API IR`(
    @ResourceUri("openapi/ir/security-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("security-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps OpenAPI 3_1 examples documentation and tags to generated API IR`(
    @ResourceUri("openapi/ir/metadata-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("metadata-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `derives OpenAPI service names from operation tags when enabled`(
    @ResourceUri("openapi/ir/metadata-3.1.yaml") testUri: URI,
  ) {
    val fragment =
      OpenApiToGeneratedApi(GeneratedApiIrOptions(deriveServicesFromTags = true))
        .convertFragment(testUri)

    assertEquals(listOf("ProjectsService"), fragment.api.services.map { service -> service.name })
    assertEquals(
      mapOf("ProjectsService" to GeneratedIdentity.native("projects")),
      fragment.serviceIdentities,
    )
  }

  @Test
  fun `maps OpenAPI 3_1 Sunday extensions to generated API IR`(
    @ResourceUri("openapi/ir/extensions-3.1.yaml") testUri: URI,
  ) {
    val api = OpenApiToGeneratedApi().convert(testUri)

    assertEquals(expectedYaml("extensions-3.1.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `rejects OpenAPI operations with multiple service group tags`(
    @TempDir tempDir: Path,
  ) {
    val source = tempDir.resolve("sunday-openapi-service-tags.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      info:
        title: Tagged API
        version: 1.0.0
      tags:
        - name: users
          x-sunday-service-group: true
        - name: audit
          x-sunday-service-group: true
      paths:
        /users:
          get:
            tags: [users, audit]
            operationId: listUsers
            responses:
              "204":
                description: No content.
      """.trimIndent(),
    )

    val error =
      assertThrows(IllegalArgumentException::class.java) {
        OpenApiToGeneratedApi().convert(source.toUri())
      }

    assertEquals(
      "OpenAPI path '/users' operation has multiple service tags (users, audit). " +
        "Add x-sunday-service to select one generated service explicitly.",
      error.message,
    )
  }

  @Test
  fun `rejects OpenAPI enum varnames length mismatch`(
    @TempDir tempDir: Path,
  ) {
    val source = tempDir.resolve("sunday-openapi-enum-varnames.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      info:
        title: Enum API
        version: 1.0.0
      paths: {}
      components:
        schemas:
          NotificationType:
            type: string
            enum: [created, updated]
            x-enum-varnames: [created]
      """.trimIndent(),
    )

    val error =
      assertThrows(IllegalArgumentException::class.java) {
        OpenApiToGeneratedApi().convert(source.toUri())
      }

    assertEquals(
      "OpenAPI enum model 'NotificationType' x-enum-varnames has 1 entries for 2 enum values.",
      error.message,
    )
  }

  @Test
  fun `rejects blank OpenAPI enum varnames`(
    @TempDir tempDir: Path,
  ) {
    val source = tempDir.resolve("sunday-openapi-enum-varnames.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      info:
        title: Enum API
        version: 1.0.0
      paths: {}
      components:
        schemas:
          NotificationType:
            type: string
            enum: [created, updated]
            x-enum-varnames: [created, " "]
      """.trimIndent(),
    )

    val error =
      assertThrows(IllegalArgumentException::class.java) {
        OpenApiToGeneratedApi().convert(source.toUri())
      }

    assertEquals(
      "OpenAPI enum model 'NotificationType' x-enum-varnames entry 2 must be a non-blank string.",
      error.message,
    )
  }

  private fun normalizedYaml(api: GeneratedApi): String =
    GeneratedApiYaml
      .writeString(api)
      .let { yaml ->
        api.models
          .mapNotNull { model -> model.source?.location }
          .distinct()
          .filterNot { location -> location == api.source.location }
          .sorted()
          .fold(yaml.replace(api.source.location, "SOURCE_LOCATION")) { normalized, location ->
            normalized.replace(location, "SOURCE_LOCATION_REFERENCE")
          }
      }

  private fun expectedYaml(name: String): String =
    Files.readString(
      Path.of("src", "test", "resources", "ir", "expected", "OpenApiToGeneratedApiTest", name),
    )
}
