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
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Frontend Test")
class TypeScriptSundayIrGeneratorTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `compiles remote schema resources`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val api = fixture.export(directory)
      val registry = TypeScriptTypeRegistry(setOf())
      TypeScriptSundayIrGenerator(
        api,
        registry,
        typeScriptSundayTestOptions,
      ).generateServiceTypes()
      val checkedTypes =
        registry.buildTypes() +
          (
            TypeName.namedImport("NullableReferenceCheck", "!nullable-reference-check") to
              ModuleSpec
                .builder("NullableReferenceCheck", ModuleSpec.Kind.MODULE)
                .addCode(
                  CodeBlock.of(
                    """
                    |const nullableFields: Pick<%T, 'node' | 'composedNode' | 'copiedNode' | 'maybeAddress'> =
                    |  {node: null, composedNode: null, copiedNode: null, maybeAddress: null};
                    |const child: %T = {child: null};
                    |const documented: %T = {id: 'one', detail: 'detail', payload: null};
                    |const parent: %T = documented;
                    |if (parent.id !== 'one') throw new Error('inherited field changed');
                    |import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
                    |const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
                    |  numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
                    |const recordSchema = runtime.resolveSchema(%T);
                    |recordSchema.parse(documented);
                    |for (const payload of [null, 'value']) {
                    |  const decoded = recordSchema.parse({...documented, payload});
                    |  if (decoded.payload !== payload) throw new Error('nullable inherited field changed');
                    |}
                    |if (recordSchema.safeParse({...documented, payload: 42}).success) {
                    |  throw new Error('inherited string accepted a number');
                    |}
                    |for (const next of [null, {id: 'two', next: {id: 'three'}}]) {
                    |  const decoded = recordSchema.parse({...documented, next});
                    |  if (JSON.stringify(decoded.next) !== JSON.stringify(next)) {
                    |    throw new Error('wrapped recursive field changed');
                    |  }
                    |}
                    |if (recordSchema.safeParse({...documented, next: 42}).success) {
                    |  throw new Error('recursive reference accepted a number');
                    |}
                    |for (const field of ['direct', 'wrapped']) {
                    |  const nested = {id: 'two', [field]: {id: 'three'}};
                    |  const decoded = recordSchema.parse({...documented, [field]: nested});
                    |  if (JSON.stringify(decoded[field]) !== JSON.stringify(nested)) {
                    |    throw new Error('recursive property intersection changed its payload');
                    |  }
                    |  if (recordSchema.safeParse({...documented, [field]: 42}).success) {
                    |    throw new Error('recursive property intersection accepted a number');
                    |  }
                    |}
                    |if (recordSchema.safeParse({detail: 'detail'}).success) {
                    |  throw new Error('inherited id must remain required');
                    |}
                    |const userSchema = runtime.resolveSchema(%T);
                    |const nodeSchema = runtime.resolveSchema(%T);
                    |const petsSchema = runtime.resolveSchema(%T);
                    |const mappedPetsSchema = runtime.resolveSchema(%T);
                    |for (const animal of [{kind: 'kitty', lives: 9}, {kind: 'hound', barks: true}]) {
                    |  const decoded = mappedPetsSchema.parse({animal});
                    |  const restored = JSON.parse(JSON.stringify(decoded));
                    |  if (JSON.stringify(restored) !== JSON.stringify({animal})) {
                    |    throw new Error('relative discriminator mapping lost the subtype or wire value');
                    |  }
                    |  mappedPetsSchema.parse(restored);
                    |}
                    |if (mappedPetsSchema.safeParse({animal: {kind: 'kitty', lives: 'nine'}}).success ||
                    |    mappedPetsSchema.safeParse({animal: {kind: 'MappedCat', lives: 9}}).success) {
                    |  throw new Error('relative discriminator mapping did not validate its subtype');
                    |}
                    |for (const kind of ['Cat', 'Dog']) {
                    |  const decoded = petsSchema.parse({animal: {kind}});
                    |  if (JSON.parse(JSON.stringify(decoded)).animal.kind !== kind) {
                    |    throw new Error('discriminator wire value changed');
                    |  }
                    |}
                    |if (petsSchema.safeParse({animal: {kind: 'Cat2'}}).success) {
                    |  throw new Error('generated model name became a wire value');
                    |}
                    |userSchema.parse({id: 'one', address: {street: 'Main'}, ...nullableFields});
                    |nodeSchema.parse(child);
                    |userSchema.parse({id: 'one', address: {street: 'Main'}, ...nullableFields, maybeAddress: {street: 'Main'}});
                    |if (userSchema.safeParse({id: 'one', address: {street: 'Main'}, ...nullableFields, maybeAddress: 42}).success) {
                    |  throw new Error('nullable address accepted a number');
                    |}
                    |if (userSchema.safeParse({id: 'one', address: {street: 'Main'}}).success ||
                    |    nodeSchema.safeParse({}).success) {
                    |  throw new Error('required nullable fields must still be present');
                    |}
                    |const restrictions = runtime.resolveSchema(%T);
                    |const nullability = runtime.resolveSchema(%T);
                    |const booleans = runtime.resolveSchema(%T);
                    |for (const value of [0, false, 'value', {nested: true}]) {
                    |  const decoded = booleans.parse({truth: value, empty: value});
                    |  if (JSON.stringify(decoded.truth) !== JSON.stringify(decoded.empty)) {
                    |    throw new Error('true and empty schemas disagree');
                    |  }
                    |}
                    |nullability.parse({strictText: 'valid', values: null});
                    |nullability.parse({strictText: 'valid', values: ['valid']});
                    |if (nullability.safeParse({strictText: null, values: null}).success) {
                    |  throw new Error('constrained string accepted null');
                    |}
                    |const valid = {address: {street: 'Main'}, text: 'hello', state: 'active'};
                    |restrictions.parse(valid);
                    |for (const field of Object.keys(valid)) {
                    |  for (const excluded of [42, null]) {
                    |    if (restrictions.safeParse({...valid, [field]: excluded}).success) {
                    |      throw new Error(field + ' accepted ' + excluded);
                    |    }
                    |  }
                    |}
                    """.trimMargin(),
                    TypeName.namedImport("User", "!user"),
                    TypeName.namedImport("Node", "!node"),
                    TypeName.namedImport("DocumentedRecord", "!documented-record"),
                    TypeName.namedImport("BaseRecord", "!base-record"),
                    TypeName.namedImport("DocumentedRecordSchema", "!documented-record"),
                    TypeName.namedImport("UserSchema", "!user"),
                    TypeName.namedImport("NodeSchema", "!node"),
                    TypeName.namedImport("PetsSchema", "!pets"),
                    TypeName.namedImport("MappedPetsSchema", "!mapped-pets"),
                    TypeName.namedImport("RestrictionsSchema", "!restrictions"),
                    TypeName.namedImport("NullabilitySchema", "!nullability"),
                    TypeName.namedImport("BooleanValuesSchema", "!boolean-values"),
                  ),
                ).addCode(
                  CodeBlock.of(
                    """
                    |const mappedAliasSchema = runtime.resolveSchema(%T);
                    |const mappedAliasPayload = {kind: 'cat', name: 'Mittens'};
                    |const mappedAlias = mappedAliasSchema.parse(mappedAliasPayload);
                    |const mappedEncoded: any = %T.encode(mappedAliasSchema, mappedAlias);
                    |if (mappedEncoded.kind !== 'cat' || mappedEncoded.name !== 'Mittens') throw new Error('mapped alias lost');
                    |mappedAliasSchema.parse(mappedEncoded);
                    |const aliasSchema = runtime.resolveSchema(%T);
                    |const aliasPayload = {label: 'base', count: 2, extra: 'child'};
                    |const aliasChild = aliasSchema.parse(aliasPayload);
                    |const aliasParent: %T = aliasChild;
                    |if (aliasParent.label !== 'base' || aliasChild.extra !== 'child') throw new Error('alias inheritance lost');
                    |if (JSON.stringify(%T.encode(aliasSchema, aliasChild)) !== JSON.stringify(aliasPayload)) throw new Error('alias fields lost');
                    |if (aliasSchema.safeParse({...aliasPayload, count: 0}).success) throw new Error('alias restriction lost');
                    |for (const reference of [%T, %T]) {
                    |  const schema = runtime.resolveSchema(reference);
                    |  const payload = {a: 'first', b: 'second', count: 2, state: 'b'};
                    |  const value = schema.parse(payload);
                    |  const encoded: any = %T.encode(schema, value);
                    |  for (const key of Object.keys(payload) as (keyof typeof payload)[]) {
                    |    if (encoded[key] !== payload[key]) throw new Error('multi-parent field lost: ' + key);
                    |  }
                    |  if (schema.parse({a: 'first', b: 'second'}).count !== 2) throw new Error('multi-parent default lost');
                    |  for (const invalid of [{...payload, count: 0}, {...payload, count: 3}, {...payload, state: 'a'}]) {
                    |    if (schema.safeParse(invalid).success) throw new Error('multi-parent intersection lost');
                    |  }
                    |}
                    |const inlineSchema = runtime.resolveSchema(%T);
                    |const inline: %T = inlineSchema.parse({detail: {value: 'value'}, selection: 'text', tags: ['b', 'a']});
                    |const inlineParent: %T = inline;
                    |if (inlineParent.detail?.value !== 'value' || inline.tags?.join(',') !== 'b,a') throw new Error('inline declaration changed');
                    |const entitySchema = runtime.resolveSchema(%T);
                    |for (const [state, field] of [['rendered', 'versionId'], ['refused', 'refusalReason']]) {
                    |  const payload = {currentAsset: {state, [field]: 'value'}};
                    |  const entity: %T = entitySchema.parse(payload);
                    |  const asset: %T | null | undefined = entity.currentAsset;
                    |  const restored: any = %T.encode(entitySchema, entity);
                    |  if (restored.currentAsset.state !== state || restored.currentAsset[field] !== 'value') throw new Error('asset subtype lost');
                    |  entitySchema.parse(restored);
                    |  if (String(asset?.state) !== state) throw new Error('canonical discriminator lost');
                    |}
                    |const eventSchema = runtime.resolveSchema(%T);
                    |const event: %T = eventSchema.parse({type: 'character', id: 'one'});
                    |const baseEvent: %T = event;
                    |const eventType: %T = baseEvent.type;
                    |if (event.count !== 20 || String(eventType) !== 'character') throw new Error('inherited default or enum changed');
                    |for (const invalid of [{type: 'prop', id: 'one'}, {type: 'future', id: 'one'},
                    |                       {type: 'character', id: 'one', count: 0}, {type: 'character', id: 'one', count: 21}]) {
                    |  if (eventSchema.safeParse(invalid).success) throw new Error('inherited refinement was not enforced');
                    |}
                    |const edit: %T = runtime.resolveSchema(%T).parse({op: 'addFact', value: 'fact'});
                    |if (String(edit.op) !== 'addFact') throw new Error('inherited discriminator enum changed');
                    |const problemSchema = runtime.resolveSchema(%T);
                    |const problem: %T = problemSchema.parse({});
                    |if (problem.detail !== 'Invalid request') throw new Error('inherited default lost');
                    |for (const status of [401, null, false]) {
                    |  if (problemSchema.safeParse({status}).success) throw new Error('invalid inherited constant accepted');
                    |}
                    |const scalarSchema = runtime.resolveSchema(%T);
                    |const defaults = scalarSchema.parse({value: 'present'});
                    |if (defaults.zero !== 0 || defaults.flag !== false || String(defaults.mode) !== 'character') throw new Error('scalar defaults changed');
                    |for (const choice of [null, 0, false]) scalarSchema.parse({value: 'present', choice});
                    |for (const invalid of [{}, {value: null}, {value: ''}, {value: 'present', zero: 1},
                    |                       {value: 'present', flag: true}, {value: 'present', choice: '0'},
                    |                       {value: 'present', choice: true}, {value: 'present', mode: 'future'}]) {
                    |  if (scalarSchema.safeParse(invalid).success) throw new Error('invalid scalar restriction accepted');
                    |}
                    """.trimMargin(),
                    TypeName.namedImport("SdkMappedPetSchema", "!sdk-mapped-pet"),
                    TypeName.namedImport("z", "zod"),
                    TypeName.namedImport("SdkAliasChildSchema", "!sdk-alias-child"),
                    TypeName.namedImport("SdkAliasBase", "!sdk-alias-base"),
                    TypeName.namedImport("z", "zod"),
                    TypeName.namedImport("SdkMultiChildSchema", "!sdk-multi-child"),
                    TypeName.namedImport("SdkMultiReversedSchema", "!sdk-multi-reversed"),
                    TypeName.namedImport("z", "zod"),
                    TypeName.namedImport("SdkInlineChildSchema", "!sdk-inline-child"),
                    TypeName.namedImport("SdkInlineChild", "!sdk-inline-child"),
                    TypeName.namedImport("SdkInlineBase", "!sdk-inline-base"),
                    TypeName.namedImport("EntityDetailsSchema", "!entity-details"),
                    TypeName.namedImport("EntityDetails", "!entity-details"),
                    TypeName.namedImport("CurrentAsset", "!current-asset"),
                    TypeName.namedImport("z", "zod"),
                    TypeName.namedImport("CharacterChangeEventSchema", "!character-change-event"),
                    TypeName.namedImport("CharacterChangeEvent", "!character-change-event"),
                    TypeName.namedImport("BaseNarrativeChangeEvent", "!base-narrative-change-event"),
                    TypeName.namedImport("NarrativeChangeEventType", "!narrative-change-event-type"),
                    TypeName.namedImport("FactEditOp", "!fact-edit-op"),
                    TypeName.namedImport("AddFactOpSchema", "!add-fact-op"),
                    TypeName.namedImport("BadRequestProblemSchema", "!bad-request-problem"),
                    TypeName.namedImport("HttpProblem", "!http-problem"),
                    TypeName.namedImport("ScalarRestrictionsSchema", "!scalar-restrictions"),
                  ),
                ).build()
          )
      assertTrue(compileAndRunTypes(compiler, checkedTypes, "nullable-reference-check"))
      assertEquals(
        listOf(GeneratedTypeRef.named("BaseRecord")),
        api.models.single { it.name == "DocumentedRecord" }.inherits,
      )
      val record = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "documented-record.ts")
      assertEquals(1, "'id': z.string()".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertEquals(
        1,
        "'payload': z.string().nullish()".toRegex(RegexOption.LITERAL).findAll(record).count(),
        record,
      )
      assertEquals(1, "'next':".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertTrue(record.contains("runtime.resolveSchema(RecordNodeSchema)"), record)
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "cat.ts").contains("unrelated"))
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "cat2.ts").contains("lives"))
      val user = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "user.ts")
      assertTrue(user.contains("Address"), user)
      assertTrue(user.contains("'node': runtime.resolveSchema(NodeSchema).nullable()"), user)
      assertTrue(user.contains("'composedNode': runtime.resolveSchema(NodeSchema).nullable()"), user)
      assertTrue(user.contains("'copiedNode': runtime.resolveSchema(NodeSchema).nullable().nullish()"), user)
      assertTrue(user.contains("UserProfile2"), user)
      assertFalse(user.contains("UserArbitrary"), user)
      assertFalse(user.contains("UserNullableArbitrary"), user)
      val profile = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "user-profile.ts")
      assertTrue(profile.contains("remoteValue"), profile)
      val inlineProfile = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "user-profile2.ts")
      assertTrue(inlineProfile.contains("localValue"), inlineProfile)
      val extended = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "user-extended-address.ts")
      assertTrue(extended.contains("street"), extended)
      assertTrue(extended.contains("postalCode"), extended)
      val node = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "node.ts")
      assertTrue(node.contains("'child': z.lazy(() => runtime.resolveSchema(NodeSchema)).nullable()"), node)
      val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "api.ts")
      assertTrue(service.contains("limit ?? 20"), service)
      val nullability = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "nullability.ts")
      assertTrue(nullability.contains("'strictText': z.string(),"), nullability)
      assertTrue(nullability.contains("'values': z.array(z.string()).nullable()"), nullability)
    }
  }

  @Test
  fun `TypeScript Sunday CLI uses the IR exporter directly`() {
    val source =
      Files.readString(
        Path.of(
          "..",
          "cli",
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "typescript",
          "TypeScriptSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("TypeScriptSundayIrGenerator"))
    assertFalse(source.contains("TypeScriptSundayGenerator("), source)
  }

  @Test
  fun `generates nullify methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMethodsTest/req-methods-nullify.default.api.ts")
  }

  @Test
  fun `emits explicit types for recursive schemas`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Recursive API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EntityType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("character", "location"),
            ),
            GeneratedModel(
              name = "EntitySummary",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityType"), required = true),
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "CharacterSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "character",
            ),
            GeneratedModel(
              name = "LocationSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "location",
              properties =
                listOf(
                  GeneratedModelProperty("parent", GeneratedTypeRef.named("LocationSummary"), required = false),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("list", "value"),
            ),
            GeneratedModel(
              name = "EntityStateProperty",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityStatePropertyType"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyList",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityStateProperty")),
              discriminatorValue = "list",
              properties =
                listOf(
                  GeneratedModelProperty(
                    "items",
                    GeneratedTypeRef(
                      GeneratedTypeRef.Kind.ARRAY,
                      "array",
                      arguments = listOf(GeneratedTypeRef.named("EntityStateProperty")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "EntityStatePropertyValue",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityStateProperty")),
              discriminatorValue = "value",
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val locationSource =
      buildString {
        FileSpec
          .get(findTypeMod("LocationSummary@!location-summary", builtTypes), "location-summary")
          .writeTo(this)
      }
    val unionSource =
      buildString {
        FileSpec
          .get(findTypeMod("EntityStateProperty@!entity-state-property", builtTypes), "entity-state-property")
          .writeTo(this)
      }
    val entitySummarySource =
      buildString {
        FileSpec
          .get(findTypeMod("EntitySummary@!entity-summary", builtTypes), "entity-summary")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(locationSource.contains("export interface LocationSummary"), locationSource)
    assertTrue(
      locationSource.contains("export const LocationSummarySchema: SchemaLike<LocationSummary>"),
      locationSource,
    )
    assertTrue(
      locationSource.contains("'parent': z.lazy(() => runtime.resolveSchema(LocationSummarySchema)).optional()"),
      locationSource,
    )
    assertTrue(
      unionSource.contains("export type EntityStateProperty = EntityStatePropertyList | EntityStatePropertyValue;"),
      unionSource,
    )
    assertTrue(
      unionSource.contains("export const EntityStatePropertySchema: SchemaLike<EntityStateProperty>"),
      unionSource,
    )
    assertTrue(
      entitySummarySource.contains("export type EntitySummary = CharacterSummary | LocationSummary;"),
      entitySummarySource,
    )
    assertTrue(
      entitySummarySource.contains("export const EntitySummarySchema: SchemaLike<EntitySummary>"),
      entitySummarySource,
    )
  }

  @Test
  fun `generates aggregate service from split IR services`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Aggregate API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              group = "Users",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getCurrentUser",
                    method = "GET",
                    path = "/users/me",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
            GeneratedService(
              name = "ProjectsService",
              group = "Projects",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/projects/{projectId}",
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, aggregateServiceOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val aggregateOutput =
      buildString {
        FileSpec
          .get(findTypeMod("TurnPostAPI@!turn-post-api", builtTypes), "turn-post-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      aggregateOutput.contains("export interface TurnPostAPI<Factory extends SundayTransport>"),
      aggregateOutput,
    )
    assertTrue(aggregateOutput.contains("users: UsersAPI<Factory>;"), aggregateOutput)
    assertTrue(aggregateOutput.contains("projects: ProjectsAPI<Factory>;"), aggregateOutput)
    assertTrue(aggregateOutput.contains("options?.defaultContentTypes"), aggregateOutput)
    assertTrue(aggregateOutput.contains("createUsersAPI(transport"), aggregateOutput)
    assertTrue(aggregateOutput.contains("defaultAcceptTypes: this.defaultAcceptTypes"), aggregateOutput)
  }
}
