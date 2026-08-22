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
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedDocumentation
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.python.tools.PythonCompiler
import io.outfoxx.sunday.generator.python.tools.compileModules
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.assertPythonSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class PythonModelRendererTest : PythonTest() {

  @Test
  fun `generates constrained aliases inheritance recursive references and open objects`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          name = "ShortName",
          kind = GeneratedModel.Kind.SCALAR_ALIAS,
          aliases = listOf(GeneratedTypeRef.scalar("string")),
          validation = mapOf("minLength" to "+002"),
        ),
        GeneratedModel(
          name = "Tags",
          kind = GeneratedModel.Kind.ARRAY,
          aliases = listOf(GeneratedTypeRef.scalar("string")),
          collection = GeneratedCollectionKind.SET,
          validation = mapOf("minItems" to "1", "uniqueItems" to "true"),
        ),
        GeneratedModel(
          name = "Scores",
          kind = GeneratedModel.Kind.MAP,
          aliases = listOf(GeneratedTypeRef.scalar("integer")),
          validation = mapOf("maxProperties" to "2"),
        ),
        GeneratedModel(
          name = "BaseRecord",
          kind = GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true)),
        ),
        GeneratedModel(
          name = "ChildRecord",
          kind = GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("BaseRecord")),
          properties =
            listOf(
              GeneratedModelProperty("name", GeneratedTypeRef.named("ShortName")),
              GeneratedModelProperty(
                "count",
                GeneratedTypeRef.scalar("integer"),
                defaultValue = "+003",
                validation = mapOf("minimum" to "+0001", "exclusiveMinimum" to "true"),
              ),
              GeneratedModelProperty(
                "ratio",
                GeneratedTypeRef.scalar("number"),
                defaultValue = "+01.50",
                validation = mapOf("maximum" to "2E+0", "multipleOf" to "+00.25"),
              ),
            ),
          closed = true,
        ),
        GeneratedModel(
          name = "PatternRecord",
          kind = GeneratedModel.Kind.OBJECT,
          patternProperties =
            listOf(
              GeneratedPatternProperty(
                pattern = "^x-",
                type = GeneratedTypeRef.scalar("string"),
                validation = mapOf("minLength" to "2"),
              ),
            ),
          additionalProperties = GeneratedAdditionalProperties(allowed = false),
        ),
        GeneratedModel(
          name = "Node",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
              GeneratedModelProperty(
                "children",
                GeneratedTypeRef(
                  kind = GeneratedTypeRef.Kind.ARRAY,
                  name = "children",
                  arguments = listOf(GeneratedTypeRef.named("Node")),
                ),
              ),
            ),
        ),
      )
    val modelsModule = PythonModelRenderer("turnpost_api").renderModels(models)
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from turnpost_api.models import ChildRecord, Node, PatternRecord, Scores, ShortName, Tags

          assert TypeAdapter(ShortName).validate_python("ok") == "ok"
          assert TypeAdapter(Tags).validate_python(["a", "a"]) == {"a"}
          assert TypeAdapter(Scores).validate_python({"a": 1, "b": 2}) == {"a": 1, "b": 2}
          child = ChildRecord.model_validate({"id": "1"})
          assert child.count == 3
          assert child.ratio == 1.5
          assert Node.model_validate({"value": "root", "children": [{"value": "leaf"}]}).children
          assert PatternRecord.model_validate({"x-name": "ok"}).model_dump()["x-name"] == "ok"

          invalid_values = (
              lambda: TypeAdapter(ShortName).validate_python("x"),
              lambda: TypeAdapter(Tags).validate_python([]),
              lambda: TypeAdapter(Scores).validate_python({"a": 1, "b": 2, "c": 3}),
              lambda: ChildRecord.model_validate({"id": "1", "name": None}),
              lambda: ChildRecord.model_validate({"id": "1", "count": 1}),
              lambda: PatternRecord.model_validate({"x-name": "x"}),
              lambda: PatternRecord.model_validate({"other": "ok"}),
          )
          for invalid_value in invalid_values:
              try:
                  invalid_value()
              except ValidationError:
                  pass
              else:
                  raise AssertionError("expected validation failure")
          """.trimIndent(),
      ),
      modelsModule.source,
    )
  }

  @Test
  fun `rejects executable OpenAPI numeric defaults and constraints`(
    @ResourceUri("openapi/ir/python-unsafe-numeric-default-3.1.yaml") defaultUri: URI,
    @ResourceUri("openapi/ir/python-unsafe-numeric-constraint-3.1.yaml") constraintUri: URI,
  ) {
    val cases =
      listOf(
        Triple(defaultUri, "Invalid integer default for property 'count'", "sunday-python-default"),
        Triple(constraintUri, "Invalid number constraint 'minimum'", "sunday-python-minimum"),
      )

    cases.forEach { (sourceUri, expectedContext, injectedMarker) ->
      val api = GeneratedApiIrExporter().export(sourceUri)
      val error =
        assertThrows(GenerationException::class.java) {
          PythonModelRenderer("turnpost_api").renderModels(api.models)
        }

      assertTrue(error.message!!.contains(expectedContext), error.message)
      assertTrue(error.message!!.contains(injectedMarker), error.message)
    }
  }

  @Test
  fun `escapes multiline schema descriptions as valid Python literals`(compiler: PythonCompiler) {
    val description = "First.\nSecond C:\\docs.\r\nTab:\tNul:\u0000 Emoji:\uD83D\uDC0D"
    val modelsModule =
      PythonModelRenderer("turnpost_api")
        .renderModels(
          listOf(
            GeneratedModel(
              name = "Example",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "value",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    documentation = GeneratedDocumentation(description = description),
                  ),
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
          from turnpost_api.models import Example

          expected = "First.\nSecond C:\\docs.\r\nTab:\tNul:\u0000 Emoji:\U0001F40D"
          assert Example.model_fields["value"].description == expected
          """.trimIndent(),
      ),
      modelsModule.source,
    )

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertTrue(
      source.contains(
        "description=\"First.\\nSecond C:\\\\docs.\\r\\nTab:\\tNul:\\u0000 Emoji:\uD83D\uDC0D\"",
      ),
      source,
    )
  }

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
  fun `generates direct AsyncAPI discriminated event object unions in Python models`(
    compiler: PythonCompiler,
    @ResourceUri("asyncapi/ir/direct-discriminated-event-union.yaml") sourceUri: URI,
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

          from turnpost_api.models import AccountsTeamCreatedData, EventEnvelope

          envelope = TypeAdapter(EventEnvelope).validate_python(
              {
                  "id": "event-1",
                  "type": "accounts.team.created",
                  "schemaVersion": "1",
                  "occurredAt": "2026-06-01T12:00:00Z",
                  "producer": {"kind": "service", "serviceId": "accounts"},
                  "scope": {"organizationId": "org-1", "teamId": "team-1"},
                  "correlationId": "corr-1",
                  "authorization": {"policy": "allowed"},
                  "data": {
                      "teamId": "team-1",
                      "ownerUserId": "user-1",
                      "slug": "core",
                      "name": "Core",
                  },
              },
          )

          assert envelope.id == "event-1"
          assert isinstance(envelope.data, AccountsTeamCreatedData)
          assert envelope.data.team_id == "team-1"
          """.trimIndent(),
      ),
    )
    assertTrue(modelsModule.source.contains("id: str"), modelsModule.source)
    assertTrue(modelsModule.source.contains("occurred_at: AwareDatetime"), modelsModule.source)
    assertTrue(modelsModule.source.contains("data: AccountsTeamCreatedData"), modelsModule.source)
    assertTrue(modelsModule.source.contains("data: NotificationAnnouncementPublishedData"), modelsModule.source)
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
  fun `generates tolerant string enums that retain and reserialize unknown values`(
    compiler: PythonCompiler,
    @ResourceUri("raml/ir/tolerant-enum.raml") ramlUri: URI,
    @ResourceUri("openapi/ir/tolerant-enum-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/tolerant-enum.yaml") asyncApiUri: URI,
  ) {
    val composedApi =
      GeneratedApi(
        name = "Tolerant Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "TaskState",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("pending", "running", "unknown"),
              unknownValue = "unknown",
            ),
          ),
      )
    val apis =
      listOf(
        GeneratedApiIrExporter().export(ramlUri),
        GeneratedApiIrExporter().export(openApiUri),
        GeneratedApiIrExporter().export(asyncApiUri),
        GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composedApi)),
      )

    apis.forEach { api ->
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

            from turnpost_api.models import TaskState

            adapter = TypeAdapter(TaskState)
            known = adapter.validate_python("pending")
            unknown = adapter.validate_python("refunded")

            assert known is TaskState.PENDING
            assert unknown.name == "UNKNOWN"
            assert unknown.value == "refunded"
            assert f"{known}" == "pending"
            assert f"{unknown}" == "refunded"
            assert adapter.dump_python(unknown, mode="json") == "refunded"
            """.trimIndent(),
        ),
      )
    }
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertTrue(source.contains("class TaskState(TolerantStrEnum):"), source)
    assertTrue(source.contains("__unknown_member_name__ = \"UNKNOWN\""), source)
    assertTrue(!source.contains("def _missing_("), source)
  }

  @Test
  fun `retains inherited OpenAPI allOf properties when discriminator base is a union alias`(
    compiler: PythonCompiler,
    @ResourceUri("openapi/ir/tolerant-discriminator-3.1.yaml") sourceUri: URI,
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

          from turnpost_api.models import JobProgress, JobStarted

          progress = TypeAdapter(JobProgress).validate_python(
              {"phase": "started", "jobId": "job-1", "taskCount": 3},
          )

          assert isinstance(progress, JobStarted)
          assert progress.phase == "started"
          assert progress.job_id == "job-1"
          assert progress.task_count == 3
          assert progress.model_dump(mode="json", by_alias=True) == {
              "phase": "started",
              "jobId": "job-1",
              "taskCount": 3,
          }
          """.trimIndent(),
      ),
      modelsModule.source,
    )
  }

  @Test
  fun `generates tolerant discriminator hierarchy fallbacks`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          name = "JobPhase",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("started", "paused", "unknown"),
          unknownValue = "unknown",
        ),
        GeneratedModel(
          name = "EventIdentity",
          kind = GeneratedModel.Kind.OBJECT,
          properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true)),
        ),
        GeneratedModel(
          name = "JobProgress",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty(
                "phase",
                GeneratedTypeRef.named("JobPhase"),
                required = true,
                serializationName = "jobPhase",
              ),
              GeneratedModelProperty(
                "jobId",
                GeneratedTypeRef.scalar("string"),
                required = true,
                serializationName = "job-id",
              ),
              GeneratedModelProperty("actor", GeneratedTypeRef.named("EventIdentity")),
            ),
          discriminator = "phase",
          discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
        ),
        GeneratedModel(
          name = "JobStarted",
          kind = GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("JobProgress")),
          discriminatorValue = "started",
          properties = listOf(GeneratedModelProperty("taskCount", GeneratedTypeRef.scalar("integer"), required = true)),
        ),
        GeneratedModel(
          name = "JobPaused",
          kind = GeneratedModel.Kind.OBJECT,
          discriminatorValue = "paused",
          properties =
            listOf(
              GeneratedModelProperty(
                "phase",
                GeneratedTypeRef.named("JobPhase"),
                required = true,
                serializationName = "jobPhase",
              ),
              GeneratedModelProperty("reason", GeneratedTypeRef.scalar("string"), required = true),
            ),
        ),
        GeneratedModel(
          name = "JobEvent",
          kind = GeneratedModel.Kind.UNION,
          aliases = listOf(GeneratedTypeRef.named("JobStarted"), GeneratedTypeRef.named("JobPaused")),
          discriminator = "phase",
          discriminatorMappings =
            mapOf(
              "started" to GeneratedTypeRef.named("JobStarted"),
              "paused" to GeneratedTypeRef.named("JobPaused"),
            ),
        ),
      )
    val modelsModule = PythonModelRenderer("turnpost_api").renderModels(models)
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import TypeAdapter, ValidationError
          from turnpost_api.models import JobEvent, JobEventUnknown, JobProgress, JobProgressUnknown, JobStarted

          adapter = TypeAdapter(JobProgress)
          known = adapter.validate_python({"jobPhase": "started", "job-id": "job-1", "taskCount": 3})
          assert isinstance(known, JobStarted)

          raw = {"jobPhase": "future", "job-id": "job-1", "detail": {"attempt": 2}}
          unknown = adapter.validate_python(raw)
          assert isinstance(unknown, JobProgressUnknown)
          assert unknown.phase.name == "UNKNOWN"
          assert unknown.phase.value == "future"
          assert unknown.job_id == "job-1"
          assert unknown.actor is None
          assert unknown.raw_body == raw
          assert adapter.dump_python(unknown, mode="json") == raw

          attributed = adapter.validate_python({"jobPhase": "future", "job-id": "job-1", "actor": {"name": "Ada"}})
          assert attributed.actor.name == "Ada"

          for invalid_actor in (None, {}, {"name": None}):
              try:
                  adapter.validate_python({"jobPhase": "future", "job-id": "job-1", "actor": invalid_actor})
                  raise AssertionError("unknown payload with invalid optional actor was accepted")
              except ValidationError:
                  pass

          paused = adapter.validate_python({"jobPhase": "paused", "job-id": "job-1"})
          assert isinstance(paused, JobProgressUnknown)
          assert paused.phase.value == "paused"

          try:
              adapter.validate_python({"jobPhase": "started", "job-id": "job-1"})
              raise AssertionError("known malformed payload was accepted")
          except ValidationError:
              pass

          try:
              adapter.validate_python({"jobPhase": "future"})
              raise AssertionError("unknown payload with invalid base fields was accepted")
          except ValidationError:
              pass

          sentinel = adapter.validate_python({"jobPhase": "unknown", "job-id": "job-1"})
          assert isinstance(sentinel, JobProgressUnknown)
          assert sentinel.phase.value == "unknown"

          for invalid in (
              {"job-id": "job-1"},
              {"jobPhase": None, "job-id": "job-1"},
              {"jobPhase": 7, "job-id": "job-1"},
          ):
              try:
                  adapter.validate_python(invalid)
                  raise AssertionError("invalid discriminator was accepted")
              except ValidationError:
                  pass

          event = TypeAdapter(JobEvent).validate_python({"jobPhase": "future", "detail": "raw"})
          assert isinstance(event, JobEventUnknown)
          assert event.phase.value == "future"
          assert TypeAdapter(JobEvent).dump_python(event, mode="json") == {"jobPhase": "future", "detail": "raw"}
          """.trimIndent(),
      ),
    )

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertTrue(source.contains("class JobProgressUnknown(RootModel[dict[str, Any]]):"), source)
    assertTrue(source.contains("def actor(self) -> EventIdentity | None:"), source)
    assertTrue(source.contains("Discriminator(_job_progress_discriminator)"), source)
  }

  @Test
  fun `generates tolerant external discriminator fallbacks`(compiler: PythonCompiler) {
    val models =
      listOf(
        GeneratedModel(
          name = "EventType",
          kind = GeneratedModel.Kind.ENUM,
          values = listOf("created", "unknown"),
          unknownValue = "unknown",
        ),
        GeneratedModel(
          name = "EventData",
          kind = GeneratedModel.Kind.OBJECT,
          externallyDiscriminated = true,
          properties =
            listOf(
              GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
            ),
          discriminatorMappings = mapOf("created" to GeneratedTypeRef.named("CreatedData")),
        ),
        GeneratedModel(
          name = "CreatedData",
          kind = GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("EventData")),
          discriminatorValue = "created",
          properties =
            listOf(
              GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
            ),
        ),
        GeneratedModel(
          name = "EventEnvelope",
          kind = GeneratedModel.Kind.OBJECT,
          properties =
            listOf(
              GeneratedModelProperty("type", GeneratedTypeRef.named("EventType"), required = true),
              GeneratedModelProperty(
                "data",
                GeneratedTypeRef.named("EventData"),
                required = true,
                externalDiscriminator = "type",
              ),
            ),
        ),
      )
    val modelsModule = PythonModelRenderer("turnpost_api").renderModels(models)
    val initModule = PythonModuleBuilder("turnpost_api/__init__.py").build()

    assertTrue(
      compileModules(
        compiler,
        listOf(initModule, modelsModule),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from turnpost_api.models import CreatedData, EventDataUnknown, EventEnvelope

          known = EventEnvelope.model_validate({"type": "created", "data": {"version": 1, "name": "ready"}})
          assert isinstance(known.data, CreatedData)

          raw_data = {"version": 2, "detail": {"attempt": 2}}
          unknown = EventEnvelope.model_validate({"type": "future", "data": raw_data})
          assert isinstance(unknown.data, EventDataUnknown)
          assert unknown.type.value == "future"
          assert unknown.data.version == 2
          assert unknown.data.raw_body == raw_data
          assert unknown.model_dump(mode="json", by_alias=True) == {"type": "future", "data": raw_data}

          try:
              EventEnvelope.model_validate({"type": "created", "data": {}})
              raise AssertionError("known malformed payload was accepted")
          except ValidationError:
              pass

          try:
              EventEnvelope.model_validate({"type": "future", "data": {"detail": "missing base"}})
              raise AssertionError("unknown payload with invalid base fields was accepted")
          except ValidationError:
              pass

          try:
              EventEnvelope.model_validate({"data": raw_data})
              raise AssertionError("missing discriminator was accepted")
          except ValidationError:
              pass
          """.trimIndent(),
      ),
      modelsModule.source,
    )
  }

  @Test
  fun `prefixes digit leading Python enum member names`() {
    assertEquals("_123", "123".pythonEnumMemberName)
    assertEquals("_123_ABC", "123ABC".pythonEnumMemberName)
  }

  @Test
  fun `generates wire faithful scalar formats and collections`(compiler: PythonCompiler) {
    val module =
      PythonModelRenderer("turnpost_api")
        .renderModels(
          listOf(
            GeneratedModel(
              name = "WireValues",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "fullDate",
                    GeneratedTypeRef.scalar("string", format = "full-date"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "partialTime",
                    GeneratedTypeRef.scalar("string", format = "partial-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "occurredAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localTime",
                    GeneratedTypeRef.scalar("string", format = "date-time-only"),
                    required = true,
                  ),
                  GeneratedModelProperty("uri", GeneratedTypeRef.scalar("string", format = "iri"), required = true),
                  GeneratedModelProperty(
                    "reference",
                    GeneratedTypeRef.scalar("string", format = "uri-reference"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "encoded",
                    GeneratedTypeRef.scalar("string", format = "byte"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "binary",
                    GeneratedTypeRef.scalar("string", format = "binary"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "Tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                      collection = GeneratedCollectionKind.SET,
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
        )

    assertTrue(
      compileModules(
        compiler,
        listOf(module),
        importModules = listOf("turnpost_api.models"),
        smokeCode =
          """
          from pydantic import ValidationError
          from turnpost_api.models import WireValues

          value = WireValues.model_validate({
              "fullDate": "2026-08-19",
              "partialTime": "12:30:00",
              "occurredAt": "2026-08-19T12:30:00Z",
              "localTime": "2026-08-19T12:30:00",
              "uri": "https://例え.テスト/path",
              "reference": "../relative",
              "encoded": "dmFsdWU=",
              "binary": "value",
              "tags": ["one", "two", "one"],
          })
          assert value.encoded == b"value"
          assert value.binary == b"value"
          assert value.tags == {"one", "two"}

          for field, invalid in (
              ("occurredAt", "2026-08-19T12:30:00"),
              ("localTime", "2026-08-19T12:30:00Z"),
          ):
              data = value.model_dump(mode="json", by_alias=True)
              data[field] = invalid
              try:
                  WireValues.model_validate(data)
              except ValidationError:
                  pass
              else:
                  raise AssertionError(f"{field} accepted an invalid timezone form")
          """.trimIndent(),
      ),
    )
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Python, "turnpost_api/models.py")
    assertTrue(source.contains("occurred_at: AwareDatetime"), source)
    assertTrue(source.contains("local_time: NaiveDatetime"), source)
    assertTrue(source.contains("encoded: Base64Bytes"), source)
    assertTrue(source.contains("reference: str"), source)
    assertTrue(source.contains("tags: set[str]"), source)
  }

  @Test
  fun `rejects blank explicit Python enum member names`() {
    val error =
      assertThrows(GenerationException::class.java) {
        PythonModelRenderer("turnpost_api")
          .renderModels(
            listOf(
              GeneratedModel(
                name = "Status",
                kind = GeneratedModel.Kind.ENUM,
                values = listOf("wire"),
                enumValueNames = listOf("---"),
              ),
            ),
          )
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '---'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("contains no valid identifier characters"), error.message)
  }

  @Test
  fun `generates digit leading Python enum values with prefixed member names`() {
    val modelsModule =
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

    assertTrue(modelsModule.source.contains("_123 = \"123\""), modelsModule.source)
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
