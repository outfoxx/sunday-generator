/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.python

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class PythonModelRendererTest : PythonTest() {

  @Test
  fun `generates pydantic object and enum models from IR`(compiler: PythonCompiler) {
    val modelsModule =
      PythonModelRenderer("turnpost_api")
        .renderModels(
          listOf(
            GeneratedModel(
              name = "ProjectStatus",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active", "archived", "pending-review"),
            ),
            GeneratedModel(
              name = "UniqueId",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "ProjectView",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("uniqueId", GeneratedTypeRef.named("UniqueId"), required = true),
                  GeneratedModelProperty(
                    "resourceId",
                    GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "createdAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty("releaseDate", GeneratedTypeRef.scalar("string", format = "date")),
                  GeneratedModelProperty("homePage", GeneratedTypeRef.scalar("string", format = "url")),
                  GeneratedModelProperty("avatar", GeneratedTypeRef.scalar("file")),
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    serializationName = "display-name",
                  ),
                  GeneratedModelProperty("status", GeneratedTypeRef.named("ProjectStatus"), required = true),
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                  ),
                ),
            ),
            GeneratedModel(
              name = "UserSummaryResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UserSelfResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "createdAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "UserResponse",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserSelfResponse"),
                  GeneratedTypeRef.named("UserSummaryResponse"),
                ),
            ),
            GeneratedModel(
              name = "UserIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorValue = "user",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ServiceIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorValue = "service",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("serviceId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Identity",
              kind = GeneratedModel.Kind.UNION,
              discriminator = "kind",
              discriminatorMappings =
                mapOf(
                  "user" to GeneratedTypeRef.named("UserIdentity"),
                  "service" to GeneratedTypeRef.named("ServiceIdentity"),
                ),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorMappings =
                mapOf(
                  "project.created" to GeneratedTypeRef.named("ProjectCreatedData"),
                  "project.deleted" to GeneratedTypeRef.named("ProjectDeletedData"),
                ),
            ),
            GeneratedModel(
              name = "ProjectCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "project.created",
              properties =
                listOf(
                  GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ProjectDeletedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "project.deleted",
              properties =
                listOf(
                  GeneratedModelProperty("projectId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("reason", GeneratedTypeRef.scalar("string")),
                ),
            ),
          ),
        )
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter
          from turnpost_api.models import (
              EventEnvelope,
              ProjectCreatedData,
              Identity,
              ProjectStatus,
              ProjectView,
              ServiceIdentity,
              UniqueId,
              UserResponse,
              UserSelfResponse,
          )

          project = ProjectView.model_validate(
              {
                  "projectId": "project-1",
                  "uniqueId": "01JWN9M5W6E9T2K3P4Q5R6S7T8",
                  "resourceId": "4f76662f-dc50-41b8-bb15-0a097ace8515",
                  "createdAt": "2026-05-24T13:45:00Z",
                  "releaseDate": "2026-05-24",
                  "homePage": "https://turnpost.example/projects/project-1",
                  "avatar": b"python",
                  "display-name": "Turnpost",
                  "status": "active",
                  "tags": ["graph", "api"],
              },
          )

          assert project.project_id == "project-1"
          assert project.unique_id == "01JWN9M5W6E9T2K3P4Q5R6S7T8"
          assert UniqueId.__name__ == "UniqueId"
          assert str(project.resource_id) == "4f76662f-dc50-41b8-bb15-0a097ace8515"
          assert project.created_at.year == 2026
          assert project.release_date is not None
          assert project.release_date.isoformat() == "2026-05-24"
          assert project.home_page is not None
          assert str(project.home_page) == "https://turnpost.example/projects/project-1"
          assert project.avatar == b"python"
          assert project.display_name == "Turnpost"
          assert project.status == ProjectStatus.ACTIVE
          assert project.model_dump(by_alias=True)["display-name"] == "Turnpost"

          user_response = TypeAdapter(UserResponse).validate_python(
              {
                  "userId": "user-1",
                  "email": "user@example.com",
                  "createdAt": "2026-05-24T13:45:00Z",
              },
          )
          assert isinstance(user_response, UserSelfResponse)

          identity = TypeAdapter(Identity).validate_python({"kind": "service", "serviceId": "svc-1"})
          assert isinstance(identity, ServiceIdentity)
          assert identity.kind == "service"

          envelope = EventEnvelope.model_validate(
              {
                  "type": "project.created",
                  "data": {"projectId": "project-1"},
              },
          )
          assert isinstance(envelope.data, ProjectCreatedData)
          assert envelope.data.project_id == "project-1"
          """.trimIndent(),
      ),
    )

    assertPythonSnapshot(
      "PythonModelRendererTest/models.py",
      CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py"),
    )
  }

  @Test
  fun `uses OpenAPI enum varnames and wire values in Python models`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") sourceUri: URI,
  ) {
    val api = GeneratedApiIrExporter().export(sourceUri)
    val modelsModule = PythonModelRenderer("turnpost_api").renderModels(api.models)
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter

          from turnpost_api.models import FallbackType, Notification, NotificationActivity, NotificationType, PullRequestReviewRequestedNotification

          notification = Notification.model_validate(
              {
                  "type": "notification.pull_request.review_requested",
                  "fallback": "mixed-kebab.case",
              },
          )

          assert notification.type == NotificationType.PULL_REQUEST_REVIEW_REQUESTED
          assert notification.type.value == "notification.pull_request.review_requested"
          assert notification.fallback == FallbackType.MIXED_KEBAB_CASE

          activity = TypeAdapter(NotificationActivity).validate_python(
              {
                  "kind": "notification.pull_request.review_requested",
                  "id": "notification-1",
                  "reviewerId": "user-1",
              },
          )
          assert isinstance(activity, PullRequestReviewRequestedNotification)
          assert activity.kind == NotificationType.PULL_REQUEST_REVIEW_REQUESTED
          """.trimIndent(),
      ),
    )

    val modelSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertTrue(
      modelSource.contains("PULL_REQUEST_REVIEW_REQUESTED = \"notification.pull_request.review_requested\""),
      modelSource,
    )
    assertTrue(modelSource.contains("PULL_REQUEST_MERGED = \"notification.pull_request.merged\""), modelSource)
    assertTrue(modelSource.contains("TEAM_MEMBER_ADDED = \"notification.team.member_added\""), modelSource)
    assertTrue(modelSource.contains("OPEN = \"OPEN\""), modelSource)
    assertTrue(modelSource.contains("LOWER_SNAKE = \"lower_snake\""), modelSource)
    assertTrue(modelSource.contains("UPPER_INTER_CAPS = \"UpperInterCaps\""), modelSource)
    assertTrue(modelSource.contains("LOWER_INTER_CAPS = \"lowerInterCaps\""), modelSource)
    assertTrue(modelSource.contains("DOTTED_CASE = \"dotted.case\""), modelSource)
    assertTrue(modelSource.contains("MIXED_KEBAB_CASE = \"mixed-kebab.case\""), modelSource)
    assertTrue(
      modelSource.contains("kind: Literal[\"notification.pull_request.review_requested\"]"),
      modelSource,
    )
  }

  @Test
  fun `rejects duplicate explicit Python enum member names`() {
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("turnpost_api")
          .renderModels(
            listOf(
              GeneratedModel(
                name = "Status",
                kind = GeneratedModel.Kind.ENUM,
                values = listOf("one", "two"),
                enumValueNames = listOf("same", "same"),
              ),
            ),
          )
      }

    assertTrue(error.message!!.contains("member name 'SAME' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `rejects invalid explicit Python enum member names`() {
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("turnpost_api")
          .renderModels(
            listOf(
              GeneratedModel(
                name = "Status",
                kind = GeneratedModel.Kind.ENUM,
                values = listOf("wire"),
                enumValueNames = listOf("123"),
              ),
            ),
          )
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '123'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid member name '123'"), error.message)
  }

  @Test
  fun `rejects unmappable Python enum values without explicit names`() {
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("turnpost_api")
          .renderModels(
            listOf(
              GeneratedModel(
                name = "Status",
                kind = GeneratedModel.Kind.ENUM,
                values = listOf("123"),
              ),
            ),
          )
      }

    assertTrue(error.message!!.contains("maps to invalid member name '123'"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `rejects delimiter only Python enum values with tailored error`() {
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("turnpost_api")
          .renderModels(
            listOf(
              GeneratedModel(
                name = "Status",
                kind = GeneratedModel.Kind.ENUM,
                values = listOf("---"),
              ),
            ),
          )
      }

    assertTrue(error.message!!.contains("contains no valid identifier characters"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }
}
