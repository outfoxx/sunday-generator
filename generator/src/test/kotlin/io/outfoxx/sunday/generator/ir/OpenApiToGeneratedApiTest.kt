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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
