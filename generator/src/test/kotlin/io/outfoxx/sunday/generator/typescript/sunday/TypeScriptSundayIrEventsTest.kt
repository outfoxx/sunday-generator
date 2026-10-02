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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Events Test")
@Tag("events")
class TypeScriptSundayIrEventsTest : TypeScriptSundayIrTestSupport() {

  @Test
  @Tag("requests")
  fun `generates streaming operations for streaming request bodies`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertTrue(compileTypes(compiler, builtTypes))

    val serviceSource =
      builtTypes
        .map { (typeName, typeSpec) ->
          buildString {
            val imported = typeName.base as SymbolSpec.Imported
            FileSpec
              .get(typeSpec, imported.source.removePrefix("!"))
              .writeTo(this)
          }
        }.single { source -> "importArchive(" in source }

    assertTrue(serviceSource.contains("importArchive(body: StreamingBody)"), serviceSource)
    assertTrue(serviceSource.contains("importArchiveOrNil(body: StreamingBody)"), serviceSource)
    assertTrue(serviceSource.contains("StreamingOperation<ImportAccepted, Factory>"), serviceSource)
    assertTrue(serviceSource.contains("NullableOperation<StreamingBody, ImportAccepted, Factory>"), serviceSource)
    assertTrue(serviceSource.contains("createStreamingOperation(this.transport"), serviceSource)
    assertFalse(serviceSource.contains("importArchiveBodyType"), serviceSource)
    assertFalse(serviceSource.contains("importArchiveOrNilBodyType"), serviceSource)
  }

  @Test
  fun `generates event stream methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-events.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseEventsTest/res-events.api.ts")
  }

  @Test
  @Tag("requests")
  fun `composed event stream preserves HTTP query and header parameters`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertTrue(compileTypes(compiler, builtTypes))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "events-api.ts")

    assertTrue(source.contains("subscriberId: string"), source)
    assertTrue(source.contains("lastEventID: string | undefined"), source)
    assertTrue(source.contains("queryParameters: {"), source)
    assertTrue(source.contains("subscriberId"), source)
    assertTrue(source.contains("headers: {"), source)
    assertTrue(source.contains("'Last-Event-ID': lastEventID"), source)
  }

  @Test
  fun `generates event source methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseEventsTest/res-event-source.api.ts")
  }

  @Test
  fun `generates formatted date-time and typed external event data from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Events API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "EventsService",
              group = "Events",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "streamEvents",
                    method = "GET",
                    path = "/events",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("EventEnvelope"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "EventIdentity",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserIdentity"),
                  GeneratedTypeRef.named("ServiceIdentity"),
                ),
              discriminator = "kind",
            ),
            GeneratedModel(
              name = "UserIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ServiceIdentity",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("serviceId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              discriminatorMappings =
                mapOf(
                  "accounts.team.created" to GeneratedTypeRef.named("AccountsTeamCreatedData"),
                  "notification.created" to GeneratedTypeRef.named("NotificationCreatedData"),
                ),
            ),
            GeneratedModel(
              name = "AccountsTeamCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "accounts.team.created",
              properties =
                listOf(
                  GeneratedModelProperty("teamId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "NotificationCreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "notification.created",
              properties =
                listOf(
                  GeneratedModelProperty("notificationId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "occurredAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty("producer", GeneratedTypeRef.named("EventIdentity"), required = true),
                  GeneratedModelProperty("actor", GeneratedTypeRef.named("EventIdentity"), required = false),
                  GeneratedModelProperty("description", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val envelopeOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventEnvelope@!event-envelope", builtTypes), "event-envelope")
          .writeTo(this)
      }
    val dataOutput =
      buildString {
        FileSpec
          .get(findTypeMod("AccountsTeamCreatedData@!accounts-team-created-data", builtTypes))
          .writeTo(this)
      }
    val identityOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventIdentity@!event-identity", builtTypes), "event-identity")
          .writeTo(this)
      }
    val serviceOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventsAPI@!events-api", builtTypes), "events-api")
          .writeTo(this)
      }
    val typeCheckedTypes =
      builtTypes +
        (
          TypeName.namedImport("EventEnvelopeTypeCheck", "!event-envelope-type-check") to
            ModuleSpec
              .builder("EventEnvelopeTypeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |function readEventDataId(envelope: %T): string {
                  |  if (envelope.type === 'accounts.team.created') {
                  |    const data: { teamId: string } = envelope.data;
                  |    // @ts-expect-error notification payload shape must not match in this branch
                  |    const wrongData: { notificationId: string } = envelope.data;
                  |    return data.teamId;
                  |  }
                  |
                  |  const data: { notificationId: string } = envelope.data;
                  |  // @ts-expect-error team payload shape must not match in this branch
                  |  const wrongData: { teamId: string } = envelope.data;
                  |  return data.notificationId;
                  |}
                  |
                  |export {readEventDataId};
                  |
                  """.trimMargin(),
                  TypeName.namedImport("EventEnvelope", "!event-envelope"),
                ),
              ).build()
        )

    assertTrue(compileTypes(compiler, typeCheckedTypes))
    assertTrue(
      envelopeOutput.contains("export type EventEnvelope = SchemaOutput<typeof EventEnvelopeSchema>;"),
      envelopeOutput,
    )
    assertTrue(envelopeOutput.contains("'occurredAt': runtime.resolveSchema(OffsetDateTimeSchema)"), envelopeOutput)
    assertTrue(
      envelopeOutput.contains("import {EventIdentitySchema} from './event-identity';"),
      envelopeOutput,
    )
    assertFalse(envelopeOutput.contains("event-identity-schema"), envelopeOutput)
    assertTrue(
      envelopeOutput.contains("'actor': z.lazy(() => runtime.resolveSchema(EventIdentitySchema)).optional()"),
      envelopeOutput,
    )
    assertTrue(envelopeOutput.contains("'description': z.string().optional()"), envelopeOutput)
    assertTrue(envelopeOutput.contains("z.discriminatedUnion('type', ["), envelopeOutput)
    assertTrue(
      dataOutput.contains(
        "export type AccountsTeamCreatedData = SchemaOutput<typeof AccountsTeamCreatedDataSchema>;",
      ),
      dataOutput,
    )
    assertFalse(dataOutput.contains("extends EventData"), dataOutput)
    assertFalse(dataOutput.contains("AccountsTeamCreatedDataSpec"), dataOutput)
    assertTrue(
      identityOutput.contains("export type EventIdentity = SchemaOutput<typeof EventIdentitySchema>;"),
      identityOutput,
    )
    assertTrue(identityOutput.contains("z.union(["), identityOutput)
    assertFalse(identityOutput.contains("z.discriminatedUnion("), identityOutput)
    assertTrue(serviceOutput.contains("Operation<void, EventEnvelope, Factory>"), serviceOutput)
    assertTrue(serviceOutput.contains("SchemaLike<EventEnvelope>"), serviceOutput)
  }

  @Test
  @Tag("models")
  fun `generates direct AsyncAPI discriminated event object unions from IR`(
    compiler: TypeScriptCompiler,
    @ResourceUri("asyncapi/ir/direct-discriminated-event-union.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val envelopeOutput =
      buildString {
        FileSpec
          .get(findTypeMod("EventEnvelope@!event-envelope", builtTypes), "event-envelope")
          .writeTo(this)
      }
    val accountsTeamCreatedEventOutput =
      buildString {
        FileSpec
          .get(findTypeMod("AccountsTeamCreatedEvent@!accounts-team-created-event", builtTypes))
          .writeTo(this)
      }
    val notificationEventType =
      findTypeMod(
        "NotificationsAnnouncementPublishedEvent@!notifications-announcement-published-event",
        builtTypes,
      )
    val notificationEventOutput =
      buildString {
        FileSpec
          .get(notificationEventType)
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(envelopeOutput.contains("z.union(["), envelopeOutput)
    assertTrue(envelopeOutput.contains("AccountsTeamCreatedEventSchema"), envelopeOutput)
    assertTrue(envelopeOutput.contains("NotificationsAnnouncementPublishedEventSchema"), envelopeOutput)
    assertTrue(accountsTeamCreatedEventOutput.contains("'id': z.string()"), accountsTeamCreatedEventOutput)
    assertTrue(
      accountsTeamCreatedEventOutput.contains("'occurredAt': runtime.resolveSchema(OffsetDateTimeSchema)"),
      accountsTeamCreatedEventOutput,
    )
    assertTrue(
      accountsTeamCreatedEventOutput.contains("'data': runtime.resolveSchema(AccountsTeamCreatedDataSchema)"),
      accountsTeamCreatedEventOutput,
    )
    assertTrue(notificationEventOutput.contains("'id': z.string()"), notificationEventOutput)
    assertTrue(
      notificationEventOutput.contains("'occurredAt': runtime.resolveSchema(OffsetDateTimeSchema)"),
      notificationEventOutput,
    )
    assertTrue(
      notificationEventOutput.contains("'data': runtime.resolveSchema(NotificationAnnouncementPublishedDataSchema)"),
      notificationEventOutput,
    )
  }
}
