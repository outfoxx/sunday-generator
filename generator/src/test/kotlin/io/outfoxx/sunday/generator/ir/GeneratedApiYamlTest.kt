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

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class GeneratedApiYamlTest {

  @Test
  fun `serializes and deserializes the generated api contract`() {

    val api = craftProjectApi()

    val yaml = GeneratedApiYaml.writeString(api)
    val decoded = GeneratedApiYaml.readString(yaml)

    assertThat(decoded, equalTo(api))
    assertThat(yaml, containsString("irVersion: \"1\""))
    assertThat(yaml, containsString("name: \"Projects\""))
    assertThat(yaml, containsString("zanzibar:"))
    assertThat(yaml, containsString("zanzibarUserSource:"))
    assertThat(yaml, containsString("principalFallback: true"))
    assertThat(yaml, containsString("policy:"))
    assertThat(yaml, containsString("nullify:"))
    assertThat(yaml, containsString("streaming:"))
  }

  @Test
  fun `reads generated api contract from path`() {

    val api = craftProjectApi()
    val path = createTempFile("sunday-ir", ".yaml")
    path.writeText(GeneratedApiYaml.writeString(api))

    val decoded = GeneratedApiYaml.readPath(path)

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves documentation through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        tags =
          listOf(
            GeneratedTag(
              name = "projects",
              policy =
                GeneratedPolicy(
                  timeout = "PT5S",
                  retry = mapOf("maxRetries" to "3"),
                ),
              documentation = GeneratedDocumentation(description = "Project operations."),
            ),
          ),
        documentation =
          GeneratedDocumentation(
            summary = "Projects",
            description = "Projects API documentation.",
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded.documentation, equalTo(api.documentation))
    assertThat(decoded.tags, equalTo(api.tags))
  }

  @Test
  fun `preserves lifecycle metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "listProjects",
                    method = "GET",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "includeArchived",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.scalar("boolean"),
                          deprecated = true,
                        ),
                      ),
                    deprecated = true,
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Project",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "id",
                    type = GeneratedTypeRef.scalar("string"),
                    readOnly = true,
                  ),
                  GeneratedModelProperty(
                    name = "secret",
                    type = GeneratedTypeRef.scalar("string"),
                    writeOnly = true,
                    deprecated = true,
                  ),
                ),
              deprecated = true,
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves validation and serialization names through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "listProjects",
                    method = "GET",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "pageSize",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.scalar("integer"),
                          serializationName = "page-size",
                          validation = mapOf("minimum" to "1", "maximum" to "100"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "ProjectSearch",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "pageSize",
                    type = GeneratedTypeRef.scalar("integer"),
                    serializationName = "page-size",
                    validation = mapOf("minimum" to "1", "maximum" to "100"),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves parameter constant values through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "createProject",
                    method = "POST",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "clientVersion",
                          location = GeneratedParameter.Location.HEADER,
                          type = GeneratedTypeRef.scalar("string"),
                          required = true,
                          serializationName = "Client-Version",
                          constantValue = "2026-05",
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves structured parameter default values through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "listProjects",
                    method = "GET",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "includeArchived",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.scalar("boolean"),
                          defaultValue = false,
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
                          type = GeneratedTypeRef.scalar("object"),
                          defaultValue =
                            mapOf(
                              "archived" to false,
                              "labels" to listOf("api"),
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves inheritance and discriminator metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        models =
          listOf(
            GeneratedModel(
              name = "Animal",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                ),
              discriminator = "kind",
            ),
            GeneratedModel(
              name = "Cat",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("huntingSkill", GeneratedTypeRef.scalar("string"), required = true),
                ),
              inherits = listOf(GeneratedTypeRef.named("Animal")),
              discriminatorValue = "cat",
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves external discriminator metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        models =
          listOf(
            GeneratedModel(
              name = "Animal",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                ),
              externallyDiscriminated = true,
              discriminatorMappings =
                mapOf(
                  "cat" to GeneratedTypeRef.named("Cat"),
                  "dog" to GeneratedTypeRef.named("Dog"),
                ),
            ),
            GeneratedModel(
              name = "AnimalEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "animal",
                    GeneratedTypeRef.named("Animal"),
                    externalDiscriminator = "kind",
                  ),
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string")),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves response headers and request payload media through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "createProject",
                    method = "POST",
                    path = "/projects",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("ProjectCreate"),
                        mediaTypes = listOf("application/json"),
                        documentation = GeneratedDocumentation(description = "Project creation request."),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 201,
                          type = GeneratedTypeRef.named("Project"),
                          mediaTypes = listOf("application/json"),
                          headers =
                            listOf(
                              GeneratedParameter(
                                name = "location",
                                location = GeneratedParameter.Location.HEADER,
                                type = GeneratedTypeRef.scalar("string"),
                                required = true,
                                serializationName = "Location",
                              ),
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves operation transport metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              baseUri = "https://{region}.api.example.com/{version}",
              baseUriParameters =
                listOf(
                  GeneratedParameter(
                    name = "region",
                    location = GeneratedParameter.Location.PATH,
                    type = GeneratedTypeRef.scalar("string"),
                    defaultValue = "us",
                  ),
                  GeneratedParameter(
                    name = "version",
                    location = GeneratedParameter.Location.PATH,
                    type = GeneratedTypeRef.scalar("string"),
                    defaultValue = "v1",
                  ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    id = "createProject",
                    method = "POST",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "filter",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.scalar("string"),
                          encoding =
                            GeneratedParameterEncoding(
                              style = "form",
                              explode = true,
                              allowReserved = true,
                              allowEmptyValue = true,
                            ),
                        ),
                      ),
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("ProjectCreateJson"),
                        mediaTypes = listOf("application/json"),
                        payloads =
                          listOf(
                            GeneratedPayloadOption(
                              type = GeneratedTypeRef.named("ProjectCreateJson"),
                              mediaTypes = listOf("application/json"),
                            ),
                            GeneratedPayloadOption(
                              type = GeneratedTypeRef.named("ProjectCreatePatch"),
                              mediaTypes = listOf("application/merge-patch+json"),
                            ),
                          ),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("Project"),
                          mediaTypes = listOf("application/json"),
                          payloads =
                            listOf(
                              GeneratedPayloadOption(
                                type = GeneratedTypeRef.named("Project"),
                                mediaTypes = listOf("application/json"),
                              ),
                              GeneratedPayloadOption(
                                type = GeneratedTypeRef.named("ProjectSummary"),
                                mediaTypes = listOf("application/vnd.project-summary+json"),
                              ),
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves security and JAX-RS metadata through yaml round trip`() {

    val auth =
      GeneratedAuth(
        schemes = listOf("bearer"),
        requirements = listOf(GeneratedSecurityRequirement(schemes = listOf("bearer"))),
        securitySchemes =
          listOf(
            GeneratedSecurityScheme(
              name = "bearer",
              type = "http",
              scheme = "bearer",
              bearerFormat = "JWT",
              headers =
                listOf(
                  GeneratedParameter(
                    name = "authorization",
                    location = GeneratedParameter.Location.HEADER,
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    serializationName = "Authorization",
                  ),
                ),
              cookieParameters =
                listOf(
                  GeneratedParameter(
                    name = "sessionId",
                    location = GeneratedParameter.Location.COOKIE,
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    serializationName = "SESSION_ID",
                  ),
                ),
            ),
          ),
      )
    val api =
      craftProjectApi().copy(
        auth = auth,
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              auth = auth,
              operations =
                listOf(
                  GeneratedOperation(
                    id = "secureProject",
                    method = "POST",
                    path = "/projects",
                    auth = auth,
                    jaxrs =
                      GeneratedJaxrs(
                        asynchronous = true,
                        reactive = true,
                        sse = GeneratedModeFlag(server = true),
                        jsonBody = GeneratedModeFlag(client = true),
                        context = listOf("uriInfo", "securityContext"),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves examples through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        models =
          listOf(
            GeneratedModel(
              name = "Project",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "name",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    examples = listOf(GeneratedExample(value = "Build IR")),
                  ),
                ),
              examples =
                listOf(
                  GeneratedExample(
                    name = "sample",
                    value = mapOf("id" to "project-1", "name" to "Build IR"),
                  ),
                ),
            ),
          ),
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "createProject",
                    method = "POST",
                    path = "/projects",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "includeArchived",
                          location = GeneratedParameter.Location.QUERY,
                          type = GeneratedTypeRef.scalar("boolean"),
                          examples = listOf(GeneratedExample(value = false)),
                        ),
                      ),
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.named("ProjectCreate"),
                        mediaTypes = listOf("application/json"),
                        examples =
                          listOf(
                            GeneratedExample(
                              name = "create",
                              mediaType = "application/json",
                              value = mapOf("name" to "Build IR"),
                            ),
                          ),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 201,
                          type = GeneratedTypeRef.named("Project"),
                          mediaTypes = listOf("application/json"),
                          examples =
                            listOf(
                              GeneratedExample(
                                name = "created",
                                mediaType = "application/json",
                                value = mapOf("id" to "project-1", "name" to "Build IR"),
                              ),
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves problem payload metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        problems =
          listOf(
            GeneratedProblem(
              name = "InvalidIdProblem",
              sourceName = "invalid_id",
              source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "file:///api.raml"),
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
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves target override metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        targets =
          mapOf(
            "kotlin" to
              GeneratedTarget(
                packageName = "io.example.api",
                modelPackageName = "io.example.models",
              ),
            "swift" to
              GeneratedTarget(
                moduleName = "TargetAPI",
                modelModuleName = "TargetModels",
              ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "displayName",
                    type = GeneratedTypeRef.scalar("string"),
                    targets =
                      mapOf(
                        "kotlin" to
                          GeneratedTarget(
                            implementation =
                              GeneratedTargetImplementation(
                                code = "return %T::class.qualifiedName + %S",
                                parameters =
                                  listOf(
                                    GeneratedTargetImplementationParameter("Type", "kotlin.String"),
                                    GeneratedTargetImplementationParameter("String", "-display"),
                                  ),
                              ),
                          ),
                      ),
                  ),
                ),
              targets =
                mapOf(
                  "kotlin" to
                    GeneratedTarget(
                      modelPackageName = "io.example.custom.models",
                      typeName = "io.example.custom.UserValue",
                    ),
                ),
              nested =
                GeneratedNestedType(
                  enclosedIn = GeneratedTypeRef.named("Account"),
                  name = "User",
                ),
              patchable = true,
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves model source fidelity through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        models =
          listOf(
            GeneratedModel(
              name = "AuditEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "createdAt",
                    type = GeneratedTypeRef.scalar("datetime-only"),
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
                  ),
                ),
              closed = true,
              additionalProperties =
                GeneratedAdditionalProperties(
                  allowed = true,
                  type = GeneratedTypeRef.scalar("string"),
                  validation = mapOf("minLength" to "1"),
                ),
              patternProperties =
                listOf(
                  GeneratedPatternProperty(
                    pattern = "^[a-z][a-z0-9-]*$",
                    type = GeneratedTypeRef.scalar("integer", format = "int16"),
                  ),
                ),
            ),
            GeneratedModel(
              name = "TagSet",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
              collection = GeneratedCollectionKind.SET,
            ),
            GeneratedModel(
              name = "DynamicLabels",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
              patternProperties =
                listOf(
                  GeneratedPatternProperty(
                    pattern = "^[a-z][a-z0-9-]*$",
                    type = GeneratedTypeRef.scalar("string"),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves declaration source identity through yaml round trip`() {

    val librarySource =
      GeneratedSourceSpec(
        kind = GeneratedSourceSpec.Kind.RAML,
        location = "library.raml",
      )
    val api =
      craftProjectApi().copy(
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Test", source = librarySource)),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets =
                mapOf(
                  "kotlin" to GeneratedTarget(modelPackageName = "io.example.library"),
                  "swift" to GeneratedTarget(modelModuleName = "LibraryModels"),
                  "typescript" to GeneratedTarget(modelModuleName = "library-models"),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves operation local model scope through yaml round trip`() {

    val scope =
      GeneratedModelScope(
        service = "ProjectsService",
        operation = "fetchProject",
        usage = GeneratedModelScope.Usage.RESPONSE_BODY,
        status = 200,
      )
    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "fetchProject",
                    method = "GET",
                    path = "/projects/{projectId}",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("FetchProjectResponseBody", scope = scope),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "FetchProjectResponseBody",
              kind = GeneratedModel.Kind.OBJECT,
              scope = scope,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves protocol metadata through yaml round trip`() {

    val api =
      craftProjectApi().copy(
        protocol =
          GeneratedProtocol(
            servers =
              listOf(
                GeneratedServer(
                  name = "production",
                  url = "broker.example.com:9092",
                  protocol = "kafka",
                  protocolVersion = "3.6",
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
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              protocol =
                GeneratedProtocol(
                  bindings =
                    listOf(
                      GeneratedProtocolBinding(
                        kind = GeneratedProtocolBinding.Kind.CHANNEL,
                        protocol = "kafka",
                        values = mapOf("topic" to "projects"),
                      ),
                    ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    id = "projectChanged",
                    method = "SUBSCRIBE",
                    path = "project.changed",
                    protocol =
                      GeneratedProtocol(
                        bindings =
                          listOf(
                            GeneratedProtocolBinding(
                              kind = GeneratedProtocolBinding.Kind.OPERATION,
                              protocol = "kafka",
                              values = mapOf("groupId" to mapOf("type" to "string")),
                            ),
                          ),
                      ),
                  ),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `preserves query string metadata through yaml round trip`() {

    val operationScope =
      GeneratedModelScope(
        service = "ProjectsService",
        operation = "searchProjects",
        usage = GeneratedModelScope.Usage.QUERY_STRING,
      )
    val securityScope =
      GeneratedModelScope(
        securityScheme = "signed",
        usage = GeneratedModelScope.Usage.SECURITY_QUERY_STRING,
      )
    val api =
      craftProjectApi().copy(
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchProjects",
                    method = "GET",
                    path = "/projects",
                    queryString = GeneratedTypeRef.named("SearchProjectsQueryString", scope = operationScope),
                  ),
                ),
            ),
          ),
        auth =
          GeneratedAuth(
            schemes = listOf("signed"),
            securitySchemes =
              listOf(
                GeneratedSecurityScheme(
                  name = "signed",
                  type = "Pass Through",
                  queryString = GeneratedTypeRef.named("SignedQueryString", scope = securityScope),
                ),
              ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "SearchProjectsQueryString",
              kind = GeneratedModel.Kind.OBJECT,
              scope = operationScope,
              properties =
                listOf(
                  GeneratedModelProperty("q", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "SignedQueryString",
              kind = GeneratedModel.Kind.OBJECT,
              scope = securityScope,
              properties =
                listOf(
                  GeneratedModelProperty("signature", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    val decoded = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(api))

    assertThat(decoded, equalTo(api))
  }

  @Test
  fun `rejects unsupported ir versions`() {

    val exception =
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedApiYaml.readString(
          """
          irVersion: "0"
          name: "Projects"
          source:
            kind: "RAML"
            location: "projects.raml"
          """.trimIndent(),
        )
      }

    assertThat(exception.message, containsString("Unsupported Sunday IR version '0'"))
  }

  private fun craftProjectApi(): GeneratedApi =
    GeneratedApi(
      name = "Projects",
      source =
        GeneratedSourceSpec(
          kind = GeneratedSourceSpec.Kind.RAML,
          location = "craft:get-project.raml",
        ),
      auth =
        GeneratedAuth(
          schemes = listOf("oauth2"),
          zanzibar = mapOf("permission" to "project.read"),
          zanzibarUserSource =
            GeneratedZanzibarUserSource(
              jwt =
                GeneratedZanzibarJwtUserSource(
                  claims = listOf("azp", "sub"),
                  principalFallback = true,
                ),
            ),
        ),
      media =
        GeneratedMedia(
          request = listOf("application/json"),
          response = listOf("application/json"),
        ),
      services =
        listOf(
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
                  exchange = GeneratedExchange.RESPONSE,
                  nullify =
                    GeneratedNullify(
                      problems = listOf(GeneratedTypeRef.named("ProjectNotFoundProblem")),
                      statuses = listOf(404),
                    ),
                  policy =
                    GeneratedPolicy(
                      timeout = "PT5S",
                      retry = mapOf("maxRetries" to "3"),
                      source = "default",
                    ),
                  streaming =
                    GeneratedStreaming(
                      kind = GeneratedStreaming.Kind.EVENT_STREAM,
                      eventMode = GeneratedStreaming.EventMode.SIMPLE,
                    ),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "Project",
            kind = GeneratedModel.Kind.OBJECT,
            properties =
              listOf(
                GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
              ),
          ),
        ),
      problems =
        listOf(
          GeneratedProblem(
            name = "ProjectNotFoundProblem",
            typeUri = "project_not_found",
            status = 404,
            title = "Project Not Found",
            fields =
              listOf(
                GeneratedModelProperty("trace_id", GeneratedTypeRef.scalar("string")),
              ),
          ),
        ),
    )
}
