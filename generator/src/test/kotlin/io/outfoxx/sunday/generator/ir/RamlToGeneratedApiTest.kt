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

import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(ResourceExtension::class)
class RamlToGeneratedApiTest {

  @Test
  fun `maps RAML document to generated API IR`(
    @ResourceUri("raml/ir/craft-project.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(api.name, equalTo("Projects API"))
    assertThat(api.source.kind, equalTo(GeneratedSourceSpec.Kind.RAML))
    assertThat(api.source.location, containsString(testUri.path))
    assertThat(
      api.media,
      equalTo(
        GeneratedMedia(
          request = listOf("application/json"),
          response = listOf("application/json"),
        ),
      ),
    )
    assertThat(api.auth, equalTo(oauth2Auth()))
    assertThat(api.models, equalTo(listOf(projectModel())))
    assertThat(api.problems, equalTo(listOf(projectNotFoundProblem(api.source.location))))

    assertThat(api.services, hasSize(1))
    assertThat(api.services.single(), equalTo(projectsService()))
    assertEquals(expectedYaml("craft-project.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML Sunday annotations to composition identities`(
    @ResourceUri("raml/ir/composition-identity.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val fragment = RamlToGeneratedApi().convertFragment(result)

    assertThat(fragment.apiId, equalTo(GeneratedIdentity.explicit("craft")))
    assertThat(
      fragment.serviceIdentities,
      equalTo(mapOf("ProjectsService" to GeneratedIdentity.native("projects"))),
    )
    assertThat(
      fragment.operationIdentities,
      equalTo(
        mapOf(
          GeneratedOperationIdentityKey("ProjectsService", "getProject") to GeneratedIdentity.explicit("getProject"),
        ),
      ),
    )
    assertThat(
      fragment
        .api
        .services
        .single()
        .operations
        .single()
        .id,
      equalTo("getProject"),
    )
  }

  @Test
  fun `derives RAML composition identity from title without service name suffix`(
    @ResourceUri("raml/ir/composition-service-name-script.raml") scriptUri: URI,
    @ResourceUri("raml/ir/composition-service-name-asset.raml") assetUri: URI,
  ) {

    val scriptFragment = RamlToGeneratedApi().convertFragment(TestAPIProcessing.process(scriptUri))
    val assetFragment = RamlToGeneratedApi().convertFragment(TestAPIProcessing.process(assetUri))
    val api = GeneratedApiComposer().compose(listOf(scriptFragment, assetFragment))

    assertThat(scriptFragment.apiId, equalTo(GeneratedIdentity.native("turnPostApi")))
    assertThat(assetFragment.apiId, equalTo(GeneratedIdentity.native("turnPostApi")))
    assertThat(
      scriptFragment.serviceIdentities,
      equalTo(mapOf("ScriptService" to GeneratedIdentity.native("script"))),
    )
    assertThat(
      assetFragment.serviceIdentities,
      equalTo(mapOf("AssetService" to GeneratedIdentity.native("asset"))),
    )
    assertThat(api.services.map { service -> service.name }, equalTo(listOf("ScriptService", "AssetService")))
    assertThat(api.problems, hasSize(3))
    val commonProblem = api.problems.single { problem -> problem.name == "InvalidTeamIdProblem" }
    assertThat(commonProblem.typeUri, equalTo("https://errors.example.com/common/problems/invalid_team_id"))
    assertThat(commonProblem.source?.location, containsString("composition-common-problems.raml"))
    assertThat(
      api.problems
        .filter { problem -> problem.name == "SharedLocalProblem" }
        .map { problem -> problem.typeUri },
      equalTo(
        listOf(
          "https://errors.example.com/script/problems/shared_local",
          "https://errors.example.com/asset/problems/shared_local",
        ),
      ),
    )
    assertThat(
      api.services
        .single { service -> service.name == "ScriptService" }
        .operations
        .single()
        .problems
        .single()
        .source
        ?.location,
      containsString("composition-service-name-script.raml"),
    )
    assertThat(
      api.services
        .single { service -> service.name == "AssetService" }
        .operations
        .single()
        .problems
        .single()
        .source
        ?.location,
      containsString("composition-service-name-asset.raml"),
    )
  }

  @Test
  fun `maps anonymous RAML any shapes to scalar any refs`(
    @ResourceUri("raml/ir/any-shapes.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val valueProperty =
      api.models
        .single { model -> model.name == "AnyHolder" }
        .properties
        .single { property -> property.name == "value" }

    assertThat(valueProperty.type, equalTo(GeneratedTypeRef.scalar("any", nullable = true)))
  }

  @Test
  fun `maps RAML request shapes to generated API IR`(
    @ResourceUri("raml/ir/request-shapes.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(api.name, equalTo("Request Shapes API"))
    assertThat(api.auth, equalTo(null))
    assertThat(api.models, equalTo(requestShapeModels()))
    assertThat(api.services, hasSize(1))
    assertThat(
      api.services
        .single()
        .operations
        .single(),
      equalTo(updateProjectOperation()),
    )
    assertEquals(expectedYaml("request-shapes.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML operation headers and request payload media to generated API IR`(
    @ResourceUri("raml/ir/operation-headers-media.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val operation =
      api.services
        .single()
        .operations
        .single()
    assertThat(
      operation.parameters.single { parameter -> parameter.name == "client" },
      equalTo(
        GeneratedParameter(
          name = "client",
          location = GeneratedParameter.Location.QUERY,
          type = GeneratedTypeRef.scalar("string"),
          required = true,
          constantValue = "sunday",
        ),
      ),
    )
    assertThat(
      operation.parameters.single { parameter -> parameter.name == "requestId" },
      equalTo(
        GeneratedParameter(
          name = "requestId",
          location = GeneratedParameter.Location.HEADER,
          type = GeneratedTypeRef.scalar("string"),
          required = true,
          serializationName = "Request-Id",
          validation = mapOf("minLength" to "8", "maxLength" to "64"),
        ),
      ),
    )
    assertThat(
      operation.parameters.single { parameter -> parameter.name == "clientVersion" },
      equalTo(
        GeneratedParameter(
          name = "clientVersion",
          location = GeneratedParameter.Location.HEADER,
          type = GeneratedTypeRef.scalar("string"),
          required = true,
          serializationName = "Client-Version",
          constantValue = "2026-05",
        ),
      ),
    )
    assertThat(
      operation.requestBody,
      equalTo(
        GeneratedPayload(
          type = GeneratedTypeRef.named("ProjectCreate"),
          mediaTypes = listOf("application/json"),
        ),
      ),
    )
    assertThat(
      operation.responses.single().headers,
      equalTo(
        listOf(
          GeneratedParameter(
            name = "location",
            location = GeneratedParameter.Location.HEADER,
            type = GeneratedTypeRef.scalar("string"),
            required = true,
            serializationName = "Location",
          ),
          GeneratedParameter(
            name = "retryAfter",
            location = GeneratedParameter.Location.HEADER,
            type = GeneratedTypeRef.scalar("integer"),
            serializationName = "Retry-After",
          ),
        ),
      ),
    )
    assertEquals(expectedYaml("operation-headers-media.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML examples to generated API IR`(
    @ResourceUri("raml/ir/examples.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val operation =
      api.services
        .single()
        .operations
        .single()
    assertThat(
      operation.parameters.single { parameter -> parameter.name == "includeArchived" }.examples,
      equalTo(
        listOf(
          GeneratedExample(
            name = "include",
            value = false,
            strict = true,
          ),
        ),
      ),
    )
    assertThat(
      operation.requestBody?.examples,
      equalTo(
        listOf(
          GeneratedExample(
            name = "create",
            mediaType = "application/json",
            value = mapOf("name" to "Build IR"),
            strict = true,
          ),
        ),
      ),
    )
    assertThat(
      operation.responses.single().examples,
      equalTo(
        listOf(
          GeneratedExample(
            name = "created",
            mediaType = "application/json",
            value = mapOf("id" to "project-1", "name" to "Build IR"),
            strict = true,
          ),
        ),
      ),
    )
    assertThat(
      api.models.single { model -> model.name == "Project" }.examples,
      equalTo(
        listOf(
          GeneratedExample(
            name = "sample",
            value = mapOf("id" to "project-1", "name" to "Build IR"),
            strict = true,
          ),
        ),
      ),
    )
    assertThat(
      api.models
        .single { model -> model.name == "Project" }
        .properties
        .single { property -> property.name == "name" }
        .examples,
      equalTo(
        listOf(
          GeneratedExample(
            value = "Build IR",
            strict = true,
          ),
        ),
      ),
    )
    assertEquals(expectedYaml("examples.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps Sunday RAML behavior annotations to generated API IR`(
    @ResourceUri("raml/ir/sunday-behavior.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val operations =
      api.services
        .single()
        .operations
        .associateBy { it.id }
    assertThat(operations["fetchProject"]?.nullify, equalTo(projectNullify()))
    assertThat(operations["createProjectRequest"]?.exchange, equalTo(GeneratedExchange.REQUEST))
    assertThat(operations["fetchProjectResponse"]?.exchange, equalTo(GeneratedExchange.RESPONSE))
    assertThat(
      operations["fetchProjectEvents"]?.streaming,
      equalTo(GeneratedStreaming(kind = GeneratedStreaming.Kind.EVENT_SOURCE)),
    )
    assertThat(
      operations["streamProjectEvents"]?.streaming,
      equalTo(
        GeneratedStreaming(
          kind = GeneratedStreaming.Kind.EVENT_STREAM,
          eventMode = GeneratedStreaming.EventMode.DISCRIMINATED,
        ),
      ),
    )
    assertThat(api.problems, equalTo(sundayBehaviorProblems(api.source.location)))
    assertEquals(expectedYaml("sunday-behavior.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML problem payload metadata to generated API IR`(
    @ResourceUri("raml/ir/problem-payloads.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(
      api.problems.single(),
      equalTo(
        GeneratedProblem(
          name = "InvalidIdProblem",
          sourceName = "invalid_id",
          source =
            GeneratedSourceSpec(
              kind = GeneratedSourceSpec.Kind.RAML,
              location = api.source.location,
            ),
          typeUri = "invalid_id",
          status = 400,
          title = "Invalid Id",
          detail = "The id contains one or more invalid characters.",
          statusBindings =
            listOf(
              GeneratedProblemStatusBinding(
                status = 400,
                typeUri = "invalid_id",
                title = "Invalid Id",
                detail = "The id contains one or more invalid characters.",
              ),
            ),
          payload =
            GeneratedProblemPayload(
              type = GeneratedTypeRef.named("InvalidIdProblem"),
              mediaTypes = listOf("application/problem+json"),
              fields =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty("offending_id", GeneratedTypeRef.scalar("string")),
                ),
            ),
          fields =
            listOf(
              GeneratedModelProperty("offending_id", GeneratedTypeRef.scalar("string")),
            ),
          documentation =
            GeneratedDocumentation(
              summary = "Invalid Id",
              description = "The id contains one or more invalid characters.",
            ),
        ),
      ),
    )
    assertEquals(expectedYaml("problem-payloads.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML file payload shapes to generated API IR`(
    @ResourceUri("raml/ir/file-payloads.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val operation =
      api.services
        .single()
        .operations
        .single()
    assertThat(operation.requestBody?.type, equalTo(GeneratedTypeRef.scalar("file")))
    assertThat(operation.responses.single().type, equalTo(GeneratedTypeRef.scalar("file")))
    assertThat(
      api.problems
        .single()
        .fields
        .single(),
      equalTo(GeneratedModelProperty("archive", GeneratedTypeRef.scalar("file"))),
    )
    assertEquals(expectedYaml("file-payloads.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML inline operation-local shapes to generated API IR`(
    @ResourceUri("raml/ir/inline-local-shapes.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("inline-local-shapes.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML query string shapes to generated API IR`(
    @ResourceUri("raml/ir/query-string-shapes.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("query-string-shapes.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML service annotations to generated API IR`(
    @ResourceUri("raml/ir/service-annotations.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(
      api.services.map { service -> service.name },
      equalTo(listOf("CraftService", "AdminService")),
    )
    assertThat(
      api.services.flatMap { service -> service.operations }.map { operation -> operation.id },
      equalTo(listOf("listProjects", "listAdminProjects")),
    )
    assertThat(
      api.services
        .single { service -> service.name == "CraftService" }
        .operations
        .single()
        .responses
        .single()
        .type,
      equalTo(
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.ARRAY,
          name = "array",
          arguments = listOf(GeneratedTypeRef.named("Project")),
        ),
      ),
    )
    assertEquals(expectedYaml("service-annotations.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML documentation to generated API IR`(
    @ResourceUri("raml/ir/documentation.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(
      api.documentation,
      equalTo(GeneratedDocumentation(description = "API documentation description.")),
    )
    assertThat(
      api.services.single().documentation,
      equalTo(GeneratedDocumentation(description = "Service documentation description.")),
    )
    assertThat(
      api.services
        .single()
        .operations
        .single()
        .documentation,
      equalTo(GeneratedDocumentation(description = "Operation documentation description.")),
    )
    assertThat(
      api.models.single { model -> model.name == "Project" }.documentation,
      equalTo(GeneratedDocumentation(description = "Project model documentation description.")),
    )
    assertEquals(expectedYaml("documentation.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML validation constraints and wire names to generated API IR`(
    @ResourceUri("raml/ir/validation-and-names.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val operation =
      api.services
        .single()
        .operations
        .single()
    assertThat(
      operation.parameters.single { parameter -> parameter.name == "pageSize" },
      equalTo(
        GeneratedParameter(
          name = "pageSize",
          location = GeneratedParameter.Location.QUERY,
          type = GeneratedTypeRef.scalar("integer"),
          serializationName = "page-size",
          defaultValue = 25,
          validation = mapOf("minimum" to "1", "maximum" to "100"),
        ),
      ),
    )
    assertThat(
      operation.parameters
        .single { parameter -> parameter.name == "xTraceId" }
        .serializationName,
      equalTo("X-Trace-Id"),
    )
    assertThat(
      api.models
        .single()
        .properties
        .single { property -> property.name == "pageSize" },
      equalTo(
        GeneratedModelProperty(
          name = "pageSize",
          type = GeneratedTypeRef.scalar("integer"),
          serializationName = "page-size",
          validation = mapOf("minimum" to "1", "maximum" to "100"),
        ),
      ),
    )
    assertThat(
      api.models
        .single()
        .properties
        .single { property -> property.name == "labels" }
        .type,
      equalTo(
        GeneratedTypeRef(
          kind = GeneratedTypeRef.Kind.ARRAY,
          name = "array",
          arguments = listOf(GeneratedTypeRef.scalar("string")),
          collection = GeneratedCollectionKind.SET,
        ),
      ),
    )
    assertEquals(expectedYaml("validation-and-names.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML model inheritance and discriminators to generated API IR`(
    @ResourceUri("raml/ir/inheritance-discriminator.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(
      api.models.single { model -> model.name == "Animal" },
      equalTo(
        GeneratedModel(
          name = "Animal",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
              GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
            ),
          discriminator = "kind",
        ),
      ),
    )
    assertThat(
      api.models.single { model -> model.name == "Cat" },
      equalTo(
        GeneratedModel(
          name = "Cat",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "huntingSkill",
                GeneratedTypeRef.scalar("string"),
                required = true,
                serializationName = "hunting-skill",
              ),
            ),
          inherits = listOf(GeneratedTypeRef.named("Animal")),
          discriminatorValue = "cat",
        ),
      ),
    )
    assertThat(
      api.models
        .single { model -> model.name == "Dog" }
        .properties
        .map { property -> property.name },
      equalTo(listOf("packSize")),
    )
    assertEquals(expectedYaml("inheritance-discriminator.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML external discriminators to generated API IR`(
    @ResourceUri("raml/ir/external-discriminator.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertThat(
      api.models.single { model -> model.name == "Animal" }.externallyDiscriminated,
      equalTo(true),
    )
    assertThat(
      api.models.single { model -> model.name == "Animal" }.discriminatorMappings,
      equalTo(
        mapOf(
          "cat" to GeneratedTypeRef.named("Cat"),
          "dog" to GeneratedTypeRef.named("Dog"),
        ),
      ),
    )
    assertThat(
      api.models
        .single { model -> model.name == "AnimalEnvelope" }
        .properties
        .single { property -> property.name == "animal" },
      equalTo(
        GeneratedModelProperty(
          name = "animal",
          type = GeneratedTypeRef.named("Animal"),
          required = true,
          externalDiscriminator = "kind",
        ),
      ),
    )
    assertEquals(expectedYaml("external-discriminator.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML target override metadata to generated API IR`(
    @ResourceUri("raml/ir/target-overrides.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("target-overrides.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML model source fidelity to generated API IR`(
    @ResourceUri("raml/ir/model-source-fidelity.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("model-source-fidelity.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML operation transport fidelity to generated API IR`(
    @ResourceUri("raml/ir/operation-transport-fidelity.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("operation-transport-fidelity.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML problem URI parity to generated API IR`(
    @ResourceUri("raml/ir/problem-uri-parity.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("problem-uri-parity.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps imported RAML declarations with durable source identity`(
    @ResourceUri("raml/ir/imported-declarations.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    val testModels = api.models.filter { model -> model.name == "Test" }
    assertThat(testModels, hasSize(2))
    val localModel = testModels.single { model -> model.source == null }
    val libraryModel = testModels.single { model -> model.source != null }

    assertThat(libraryModel.source?.location, containsString("imported-declaration-lib.raml"))
    assertThat(
      localModel.inherits
        .single()
        .source
        ?.location,
      equalTo(libraryModel.source?.location),
    )
    assertThat(
      libraryModel.targets["kotlin"]?.modelPackageName,
      equalTo("io.example.library"),
    )
    assertThat(
      libraryModel.targets["swift"]?.modelModuleName,
      equalTo("LibraryModels"),
    )
    assertThat(
      libraryModel.targets["typescript"]?.modelModuleName,
      equalTo("library-models"),
    )
    assertEquals(expectedYaml("imported-declarations.ir.yaml"), normalizedYaml(api))
  }

  @Test
  fun `maps RAML security and JAX-RS metadata to generated API IR`(
    @ResourceUri("raml/ir/security-jaxrs-metadata.raml") testUri: URI,
  ) {

    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    assertEquals(expectedYaml("security-jaxrs-metadata.ir.yaml"), normalizedYaml(api))
  }

  private fun normalizedYaml(api: GeneratedApi): String =
    GeneratedApiYaml
      .writeString(api)
      .let { yaml ->
        api.problems
          .mapNotNull { problem -> problem.source?.location }
          .plus(api.models.mapNotNull { model -> model.source?.location })
          .distinct()
          .filterNot { location -> location == api.source.location }
          .sorted()
          .fold(yaml.replace(api.source.location, "SOURCE_LOCATION")) { normalized, location ->
            normalized.replace(location, "SOURCE_LOCATION_REFERENCE")
          }
      }

  private fun expectedYaml(name: String): String =
    Files.readString(
      Path.of("src", "test", "resources", "ir", "expected", "RamlToGeneratedApiTest", name),
    )

  private fun projectModel(): GeneratedModel =
    GeneratedModel(
      name = "Project",
      kind = GeneratedModel.Kind.OBJECT,
      properties =
        listOf(
          GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
          GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
        ),
    )

  private fun projectNotFoundProblem(sourceLocation: String): GeneratedProblem =
    GeneratedProblem(
      name = "ProjectNotFoundProblem",
      sourceName = "project_not_found",
      source =
        GeneratedSourceSpec(
          kind = GeneratedSourceSpec.Kind.RAML,
          location = sourceLocation,
        ),
      typeUri = "project_not_found",
      status = 404,
      title = "Project Not Found",
      detail = "The requested project does not exist.",
      statusBindings =
        listOf(
          GeneratedProblemStatusBinding(
            status = 404,
            typeUri = "project_not_found",
            title = "Project Not Found",
            detail = "The requested project does not exist.",
          ),
        ),
      payload =
        GeneratedProblemPayload(
          type = GeneratedTypeRef.named("ProjectNotFoundProblem"),
          mediaTypes = listOf("application/problem+json"),
          fields = problemPayloadFields(),
        ),
      documentation =
        GeneratedDocumentation(
          summary = "Project Not Found",
          description = "The requested project does not exist.",
        ),
    )

  private fun oauth2Auth(): GeneratedAuth =
    GeneratedAuth(
      schemes = listOf("oauth2"),
      requirements = listOf(GeneratedSecurityRequirement(schemes = listOf("oauth2"))),
      securitySchemes =
        listOf(
          GeneratedSecurityScheme(
            name = "oauth2",
            type = "OAuth 2.0",
          ),
        ),
    )

  private fun projectsService(): GeneratedService =
    GeneratedService(
      name = "ProjectsService",
      operations =
        listOf(
          GeneratedOperation(
            id = "getProject",
            method = "GET",
            path = "/projects/{projectId}",
            parameters =
              listOf(
                GeneratedParameter(
                  name = "projectId",
                  location = GeneratedParameter.Location.PATH,
                  type = GeneratedTypeRef.scalar("string"),
                  required = true,
                ),
              ),
            responses =
              listOf(
                GeneratedResponse(
                  status = 200,
                  type = GeneratedTypeRef.named("Project"),
                  mediaTypes = listOf("application/json"),
                ),
              ),
            problems = listOf(GeneratedTypeRef.named("ProjectNotFoundProblem")),
            auth = oauth2Auth(),
            media =
              GeneratedMedia(
                request = listOf("application/json"),
                response = listOf("application/json"),
              ),
          ),
        ),
      auth = oauth2Auth(),
      media =
        GeneratedMedia(
          request = listOf("application/json"),
          response = listOf("application/json"),
        ),
    )

  private fun requestShapeModels(): List<GeneratedModel> =
    listOf(
      GeneratedModel(
        name = "ProjectPatch",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("name", GeneratedTypeRef.scalar("string")),
            GeneratedModelProperty("state", GeneratedTypeRef.named("ProjectState")),
            GeneratedModelProperty("tags", GeneratedTypeRef.named("ProjectTagList")),
            GeneratedModelProperty("metadata", GeneratedTypeRef.scalar("string", nullable = true)),
          ),
      ),
      GeneratedModel(
        name = "ProjectState",
        kind = GeneratedModel.Kind.ENUM,
        values = listOf("active", "archived"),
      ),
      GeneratedModel(
        name = "ProjectTagList",
        kind = GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.scalar("string")),
      ),
      GeneratedModel(
        name = "SearchOptions",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty("archived", GeneratedTypeRef.scalar("boolean"), required = true),
            GeneratedModelProperty(
              "labels",
              GeneratedTypeRef(
                kind = GeneratedTypeRef.Kind.ARRAY,
                name = "array",
                arguments = listOf(GeneratedTypeRef.scalar("string")),
              ),
              required = true,
            ),
          ),
      ),
      GeneratedModel(
        name = "SearchToken",
        kind = GeneratedModel.Kind.UNION,
        aliases = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.scalar("integer")),
      ),
    )

  private fun updateProjectOperation(): GeneratedOperation =
    GeneratedOperation(
      id = "updateProject",
      method = "PATCH",
      path = "/projects/{projectId}",
      parameters =
        listOf(
          GeneratedParameter(
            name = "projectId",
            location = GeneratedParameter.Location.PATH,
            type = GeneratedTypeRef.scalar("string"),
            required = true,
          ),
          GeneratedParameter(
            name = "includeArchived",
            location = GeneratedParameter.Location.QUERY,
            type = GeneratedTypeRef.scalar("boolean"),
            defaultValue = false,
          ),
          GeneratedParameter(
            name = "token",
            location = GeneratedParameter.Location.QUERY,
            type = GeneratedTypeRef.named("SearchToken"),
          ),
          GeneratedParameter(
            name = "state",
            location = GeneratedParameter.Location.QUERY,
            type = GeneratedTypeRef.named("ProjectState"),
            defaultValue = "active",
          ),
          GeneratedParameter(
            name = "labels",
            location = GeneratedParameter.Location.QUERY,
            type =
              GeneratedTypeRef(
                kind = GeneratedTypeRef.Kind.ARRAY,
                name = "array",
                arguments = listOf(GeneratedTypeRef.scalar("string")),
              ),
            defaultValue = listOf("api", "generator"),
          ),
          GeneratedParameter(
            name = "options",
            location = GeneratedParameter.Location.QUERY,
            type = GeneratedTypeRef.named("SearchOptions"),
            defaultValue =
              mapOf(
                "archived" to false,
                "labels" to listOf("api"),
              ),
          ),
          GeneratedParameter(
            name = "xTraceId",
            location = GeneratedParameter.Location.HEADER,
            type = GeneratedTypeRef.scalar("string"),
            serializationName = "X-Trace-Id",
            defaultValue = "generated",
          ),
        ),
      requestBody =
        GeneratedPayload(
          type = GeneratedTypeRef.named("ProjectPatch"),
          mediaTypes = listOf("application/json"),
        ),
      responses =
        listOf(
          GeneratedResponse(
            status = 200,
            type = GeneratedTypeRef.named("ProjectPatch"),
            mediaTypes = listOf("application/json"),
          ),
          GeneratedResponse(
            status = 202,
            type = GeneratedTypeRef.named("ProjectTagList"),
            mediaTypes = listOf("application/cbor"),
          ),
        ),
      media =
        GeneratedMedia(
          request = listOf("application/json"),
          response = listOf("application/json"),
        ),
    )

  private fun projectNullify(): GeneratedNullify =
    GeneratedNullify(
      problems = listOf(GeneratedTypeRef.named("InvalidIdProblem")),
      statuses = listOf(404),
    )

  private fun sundayBehaviorProblems(sourceLocation: String): List<GeneratedProblem> =
    listOf(
      GeneratedProblem(
        name = "InvalidIdProblem",
        sourceName = "invalid_id",
        source =
          GeneratedSourceSpec(
            kind = GeneratedSourceSpec.Kind.RAML,
            location = sourceLocation,
          ),
        typeUri = "invalid_id",
        status = 400,
        title = "Invalid Id",
        detail = "The id contains one or more invalid characters.",
        statusBindings =
          listOf(
            GeneratedProblemStatusBinding(
              status = 400,
              typeUri = "invalid_id",
              title = "Invalid Id",
              detail = "The id contains one or more invalid characters.",
            ),
          ),
        payload =
          GeneratedProblemPayload(
            type = GeneratedTypeRef.named("InvalidIdProblem"),
            mediaTypes = listOf("application/problem+json"),
            fields =
              problemPayloadFields(
                GeneratedModelProperty(
                  name = "offending_id",
                  type = GeneratedTypeRef.scalar("string"),
                ),
              ),
          ),
        fields =
          listOf(
            GeneratedModelProperty(
              name = "offending_id",
              type = GeneratedTypeRef.scalar("string"),
            ),
          ),
        documentation =
          GeneratedDocumentation(
            summary = "Invalid Id",
            description = "The id contains one or more invalid characters.",
          ),
      ),
      GeneratedProblem(
        name = "UnavailableProblem",
        sourceName = "unavailable",
        source =
          GeneratedSourceSpec(
            kind = GeneratedSourceSpec.Kind.RAML,
            location = sourceLocation,
          ),
        typeUri = "unavailable",
        status = 503,
        title = "Unavailable",
        detail = "The service is temporarily unavailable.",
        statusBindings =
          listOf(
            GeneratedProblemStatusBinding(
              status = 503,
              typeUri = "unavailable",
              title = "Unavailable",
              detail = "The service is temporarily unavailable.",
            ),
          ),
        payload =
          GeneratedProblemPayload(
            type = GeneratedTypeRef.named("UnavailableProblem"),
            mediaTypes = listOf("application/problem+json"),
            fields =
              problemPayloadFields(
                GeneratedModelProperty(
                  name = "retry_after",
                  type = GeneratedTypeRef.scalar("integer"),
                ),
              ),
          ),
        fields =
          listOf(
            GeneratedModelProperty(
              name = "retry_after",
              type = GeneratedTypeRef.scalar("integer"),
            ),
          ),
        documentation =
          GeneratedDocumentation(
            summary = "Unavailable",
            description = "The service is temporarily unavailable.",
          ),
      ),
    )

  private fun problemPayloadFields(vararg customFields: GeneratedModelProperty): List<GeneratedModelProperty> =
    listOf(
      GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
      GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
      GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string")),
    ) + customFields
}
