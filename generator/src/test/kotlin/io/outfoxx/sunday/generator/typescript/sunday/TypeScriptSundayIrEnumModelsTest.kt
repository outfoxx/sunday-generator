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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] EnumModels Test")
@Tag("models")
class TypeScriptSundayIrEnumModelsTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `rejects delimiter only TypeScript enum values with tailored error`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("---"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("contains no valid identifier characters"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `rejects enum discriminator values that do not match enum entries`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum Discriminator API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Kind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("known"),
            ),
            GeneratedModel(
              name = "Entity",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              properties =
                listOf(
                  GeneratedModelProperty(
                    "kind",
                    GeneratedTypeRef.named("Kind"),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "UnknownEntity",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Entity")),
              discriminatorValue = "unknown",
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("TypeScript enum 'Kind' discriminator value 'unknown'"), error.message)
    assertTrue(error.message!!.contains("does not match any enum value"), error.message)
  }

  @Test
  fun `generates named discriminated union models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Union API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "InvalidValueCode",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("invalid-value"),
            ),
            GeneratedModel(
              name = "MissingValueCode",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("missing-value"),
            ),
            GeneratedModel(
              name = "InvalidValueProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.named("InvalidValueCode"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "MissingValueProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("code", GeneratedTypeRef.named("MissingValueCode"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "GraphValidationProblem",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("InvalidValueProblem"),
                  GeneratedTypeRef.named("MissingValueProblem"),
                ),
              discriminator = "code",
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get(findTypeMod("GraphValidationProblem@!graph-validation-problem", builtTypes), "graph-validation-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      unionSource.contains("export type GraphValidationProblem = SchemaOutput<typeof GraphValidationProblemSchema>;"),
      unionSource,
    )
    assertTrue(unionSource.contains("z.union(["), unionSource)
    assertTrue(unionSource.contains("runtime.resolveSchema(InvalidValueProblemSchema)"), unionSource)
    assertTrue(unionSource.contains("runtime.resolveSchema(MissingValueProblemSchema)"), unionSource)
  }

  @Test
  fun `generates enum discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Parent@!parent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlDiscriminatedTypesTest/enum.parent.ts", parentOutput)
  }

  @Test
  fun `lowers shared enums and alias-like models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Alias API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active", "inactive"),
            ),
            GeneratedModel(
              name = "TextAlias",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextList",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextSet",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
              collection = GeneratedCollectionKind.SET,
            ),
            GeneratedModel(
              name = "TextMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextUnion",
              kind = GeneratedModel.Kind.UNION,
              aliases = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.scalar("number")),
            ),
            GeneratedModel(
              name = "AliasContainer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("status", GeneratedTypeRef.named("Status"), required = true),
                  GeneratedModelProperty("alias", GeneratedTypeRef.named("TextAlias"), required = true),
                  GeneratedModelProperty("list", GeneratedTypeRef.named("TextList"), required = true),
                  GeneratedModelProperty("set", GeneratedTypeRef.named("TextSet"), required = true),
                  GeneratedModelProperty("map", GeneratedTypeRef.named("TextMap"), required = true),
                  GeneratedModelProperty("union", GeneratedTypeRef.named("TextUnion"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val statusSource =
      buildString {
        FileSpec
          .get(findTypeMod("Status@!status", builtTypes), "status")
          .writeTo(this)
      }
    val containerSource =
      buildString {
        FileSpec
          .get(findTypeMod("AliasContainer@!alias-container", builtTypes), "alias-container")
          .writeTo(this)
      }
    val textUnionSource =
      buildString {
        FileSpec
          .get(findTypeMod("TextUnion@!text-union", builtTypes), "text-union")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(statusSource.contains("export enum Status"), statusSource)
    assertTrue(statusSource.contains("export const StatusSchema"), statusSource)
    assertTrue(
      containerSource.contains("export type AliasContainer = SchemaOutput<typeof AliasContainerSchema>;"),
      containerSource,
    )
    assertTrue(containerSource.contains("'status': runtime.resolveSchema(StatusSchema)"), containerSource)
    assertTrue(containerSource.contains("'alias': z.string()"), containerSource)
    assertTrue(containerSource.contains("'list': z.array(z.string())"), containerSource)
    assertTrue(containerSource.contains("'set': z.array(z.string())"), containerSource)
    assertTrue(
      containerSource.contains("'map': runtime.resolveSchema(z.record(z.string(), z.string()))"),
      containerSource,
    )
    assertTrue(
      containerSource.contains("'union': z.lazy(() => runtime.resolveSchema(TextUnionSchema))"),
      containerSource,
    )
    assertTrue(
      textUnionSource.contains("export type TextUnion = SchemaOutput<typeof TextUnionSchema>;"),
      textUnionSource,
    )
    assertTrue(textUnionSource.contains("export const TextUnionSchema"), textUnionSource)
  }

  @Test
  fun `uses OpenAPI enum varnames and wire values in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val notificationTypeSource =
      buildString {
        FileSpec
          .get(findTypeMod("NotificationType@!notification-type", builtTypes), "notification-type")
          .writeTo(this)
      }
    val fallbackTypeSource =
      buildString {
        FileSpec
          .get(findTypeMod("FallbackType@!fallback-type", builtTypes), "fallback-type")
          .writeTo(this)
      }
    val notificationSource =
      buildString {
        FileSpec
          .get(findTypeMod("Notification@!notification", builtTypes), "notification")
          .writeTo(this)
      }
    val activitySource =
      buildString {
        FileSpec
          .get(findTypeMod("NotificationActivity@!notification-activity", builtTypes), "notification-activity")
          .writeTo(this)
      }
    val reviewRequestedSource =
      buildString {
        FileSpec
          .get(
            findTypeMod(
              "PullRequestReviewRequestedNotification@!pull-request-review-requested-notification",
              builtTypes,
            ),
            "pull-request-review-requested-notification",
          ).writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      notificationTypeSource.contains(
        "pullRequestReviewRequested = 'notification.pull_request.review_requested'",
      ),
      notificationTypeSource,
    )
    assertTrue(notificationTypeSource.contains("pullRequestMerged = 'notification.pull_request.merged'"))
    assertTrue(notificationTypeSource.contains("teamMemberAdded = 'notification.team.member_added'"))
    assertTrue(fallbackTypeSource.contains("Open = 'OPEN'"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("LowerSnake = 'lower_snake'"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("UpperInterCaps = 'UpperInterCaps'"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("LowerInterCaps = 'lowerInterCaps'"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("DottedCase = 'dotted.case'"), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("MixedKebabCase = 'mixed-kebab.case'"), fallbackTypeSource)
    assertTrue(notificationSource.contains("'type': runtime.resolveSchema(NotificationTypeSchema)"), notificationSource)
    assertTrue(activitySource.contains("z.union(["), activitySource)
    assertTrue(
      reviewRequestedSource.contains("'kind': z.literal(NotificationType.pullRequestReviewRequested)"),
      reviewRequestedSource,
    )
  }

  @Test
  fun `rejects duplicate explicit TypeScript enum member names`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("one", "two"),
              enumValueNames = listOf("same", "same"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("member name 'same' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `generates discriminated unions for mappings that reuse canonical schemas`(
    preserve: Boolean,
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/reusable-discriminator-mapping.yaml") openApiUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri))

    TypeScriptSundayIrGenerator(
      api,
      typeRegistry,
      TypeScriptSundayOptions(
        "http://example.com/",
        listOf("application/json"),
        "API",
        preserveUnknownFields = preserve,
      ),
    ).generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeCheckedTypes =
      builtTypes +
        (
          TypeName.namedImport("ReusableDiscriminatorMappingTypeCheck", "!reusable-discriminator-mapping-type-check") to
            ModuleSpec
              .builder("ReusableDiscriminatorMappingTypeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |function eventDataId(notification: %T): string | undefined {
                  |  if ('data' in notification.event) {
                  |    const canonicalEvent: %T = notification.event;
                  |    return canonicalEvent.data.id;
                  |  }
                  |  const fallbackEvent: %T = notification.event;
                  |  return fallbackEvent.rawBody.id as string | undefined;
                  |}
                  |
                  |export {eventDataId};
                  |
                  """.trimMargin(),
                  TypeName.namedImport("Notification", "!notification"),
                  TypeName.namedImport("EventOne", "!event-one"),
                  TypeName.namedImport(
                    "NotificationEventEnvelopeUnrecognized",
                    "!notification-event-envelope-unrecognized",
                  ),
                ),
              ).build()
        )
    val runtimeCheckedTypes =
      typeCheckedTypes +
        (
          TypeName.namedImport(
            "ReusableDiscriminatorMappingRuntimeCheck",
            "!reusable-discriminator-mapping-runtime-check",
          ) to
            ModuleSpec
              .builder("ReusableDiscriminatorMappingRuntimeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
                  |const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
                  |  numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
                  |const eventEnvelopeSchema = runtime.resolveSchema(%T);
                  |const dataEvent = z.object({type: z.object({rawValue: z.string()}), data: z.object({id: z.string()})});
                  |const canonicalAlias = dataEvent.parse(eventEnvelopeSchema.parse({type: 'event.legacy', data: {id: 'canonical'}}));
                  |if (canonicalAlias.data.id !== 'canonical' || canonicalAlias.type.rawValue !== 'event.legacy') {
                  |  throw new Error('canonical mapping alias did not decode');
                  |}
                  |const notificationSchema = runtime.resolveSchema(%T);
                  |const recognized = notificationSchema.parse({event: {type: 'event.one', data: {id: 'known'}}});
                  |if (dataEvent.parse(recognized.event).data.id !== 'known' || recognized.event.type.rawValue !== 'event.one') {
                  |  throw new Error('recognized canonical event did not decode');
                  |}
                  |const aliased = notificationSchema.parse({event: {type: 'event.legacy', data: {id: 'legacy', future: null}, future: {nested: [null, true]}}});
                  |if (dataEvent.parse(aliased.event).data.id !== 'legacy' || aliased.event.type.rawValue !== 'event.legacy') {
                  |  throw new Error('aliased canonical event did not decode');
                  |}
                  |const encodedAlias = %T.object({event: z.object({type: z.string(), data: z.looseObject({id: z.string()})}).passthrough()})
                  |  .parse(z.encode(notificationSchema, aliased));
                  |if (encodedAlias.event.type !== 'event.legacy' || encodedAlias.event.data.id !== 'legacy') {
                  |  throw new Error('aliased canonical event did not encode');
                  |}
                  |if (('future' in encodedAlias.event) !== $preserve || ('future' in encodedAlias.event.data) !== $preserve) {
                  |  throw new Error('mapped discriminator lost field-preservation policy');
                  |}
                  |const unknown = notificationSchema.parse({event: {type: 'future.event', detail: 'preserved'}});
                  |if (unknown.event.type.rawValue !== 'future.event' || z.object({rawBody: z.object({detail: z.string()})}).parse(unknown.event).rawBody.detail !== 'preserved') {
                  |  throw new Error('unknown event did not preserve its discriminator and raw body');
                  |}
                  |function assertInvalid(input: unknown): void {
                  |  try {
                  |    notificationSchema.parse(input);
                  |  } catch {
                  |    return;
                  |  }
                  |  throw new Error('invalid discriminator was accepted');
                  |}
                  |assertInvalid({event: {}});
                  |assertInvalid({event: {type: 1}});
                  """.trimMargin(),
                  TypeName.namedImport("EventEnvelopeSchema", "!event-envelope"),
                  TypeName.namedImport("NotificationSchema", "!notification"),
                  TypeName.namedImport("z", "zod"),
                ),
              ).build()
        )

    assertTrue(
      compileAndRunTypes(
        compiler,
        runtimeCheckedTypes,
        "reusable-discriminator-mapping-runtime-check",
      ),
    )

    val envelopeSource =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "notification-event-envelope.ts")
    assertTrue(envelopeSource.contains("import {EventOneSchema} from './event-one';"), envelopeSource)
    assertTrue(
      envelopeSource.contains(
        "import {NotificationEventEnvelopeUnrecognizedSchema} from './notification-event-envelope-unrecognized';",
      ),
      envelopeSource,
    )
    assertTrue(envelopeSource.contains("const branchesSchema = z.union(["), envelopeSource)
  }

  @Test
  fun `rejects TypeScript contextual keyword enum member names`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("wire"),
              enumValueNames = listOf("type"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry 'type'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid member name 'type'"), error.message)
  }

  @Test
  fun `rejects unmappable TypeScript enum values without explicit names`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("maps to invalid member name '123'"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }
}
