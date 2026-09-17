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
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedProblem
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.inheritedConstraintsFixture
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateSunday
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Generator Test")
class TypeScriptSundayIrGeneratorTest {

  @Test
  fun `every compatible parent constraint remains effective`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      inheritedConstraintsFixture(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ParentCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ChildSchema} from './child';
            import {ReversedSchema} from './reversed';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            for (const factory of [ChildSchema, ReversedSchema]) {
              const schema = runtime.resolveSchema(factory);
              const payload = {text: 'abc', count: 2, amount: 0.3};
              const decoded = schema.parse(payload);
              if (JSON.stringify(z.encode(schema, decoded)) !== JSON.stringify(payload)) throw new Error('round trip changed');
              if (schema.parse({}).count !== 2) throw new Error('default lost');
              for (const invalid of [{text: 'a'}, {text: 'abcd'}, {count: 3}, {amount: 0.31}]) {
                if (schema.safeParse(invalid).success) throw new Error('parent restriction lost');
              }
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ParentCheck", "!parent-check") to check),
        "parent-check",
      ),
    )
  }

  @Test
  fun `wire refinements retain enum codecs formatted scalars and transport policies`(compiler: TypeScriptCompiler) {
    val plain = GeneratedTypeRef.named("PlainState")
    val tolerant = GeneratedTypeRef.named("StateAlias")
    val properties =
      listOf(
        GeneratedModelProperty(
          "plain",
          plain,
          validation =
            mapOf(
              "minLength" to "2",
              "maxLength" to "4",
              "pattern" to "^go",
            ),
        ),
        GeneratedModelProperty(
          "mode",
          tolerant,
          defaultValue = "good",
          allowedValues = listOf("good"),
          validation =
            mapOf(
              "minLength" to "2",
            ),
        ),
        GeneratedModelProperty(
          "url",
          GeneratedTypeRef.named("UrlAlias"),
          defaultValue = "https://example.test/",
          allowedValues = listOf("https://example.test/"),
        ),
        GeneratedModelProperty(
          "instant",
          GeneratedTypeRef.scalar("string", format = "date-time"),
          allowedValues = listOf("2026-01-01T00:00:01Z"),
        ),
        GeneratedModelProperty(
          "bytes",
          GeneratedTypeRef.scalar("string", format = "byte"),
          allowedValues = listOf("SGk"),
        ),
        GeneratedModelProperty(
          "zero",
          GeneratedTypeRef.scalar("integer"),
          defaultValue = "0",
          allowedValues = listOf(0),
        ),
        GeneratedModelProperty(
          "flag",
          GeneratedTypeRef.scalar("boolean"),
          defaultValue = "false",
          allowedValues = listOf(false),
        ),
      )
    val api =
      GeneratedApi(
        name = "Wire restrictions",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel("PlainState", GeneratedModel.Kind.ENUM, values = listOf("a", "good", "bad", "longer")),
            GeneratedModel(
              "State",
              GeneratedModel.Kind.ENUM,
              values = listOf("good", "bad", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              "StateAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("State")),
            ),
            GeneratedModel(
              "UrlAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string", format = "uri")),
            ),
            GeneratedModel(
              "Base",
              GeneratedModel.Kind.OBJECT,
              properties =
                properties.map {
                  it.copy(validation = emptyMap(), allowedValues = null, defaultValue = null)
                },
            ),
            GeneratedModel(
              "Child",
              GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Base")),
              properties = properties,
            ),
          ),
      )
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("WireCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ChildSchema} from './child';
            import {BaseSchema} from './base';
            import {PlainState} from './plain-state';
            const policy = {format: 'json' as const, dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64};
            const runtime = createSchemaRuntime(policy);
            const schema = runtime.resolveSchema(ChildSchema);
            const wire = {plain: 'good', mode: 'good', url: 'https://example.test/', instant: '2026-01-01T00:00:01Z', bytes: 'SGk', zero: 0, flag: false};
            const decoded = schema.parse(wire);
            const canonical: PlainState | null | undefined = decoded.plain;
            if (canonical !== PlainState.Good || !(decoded.url instanceof URL) || !(decoded.bytes instanceof ArrayBuffer)) throw new Error('canonical types lost');
            const encoded = z.encode(schema, decoded);
            if (JSON.stringify(encoded) !== JSON.stringify(wire)) throw new Error('wire round trip changed');
            const omitted = schema.parse({});
            if (omitted.mode?.toString() !== 'good' || omitted.url?.href !== wire.url || omitted.zero !== 0 || omitted.flag !== false) throw new Error('prefaults lost');
            for (const [field, value] of [['plain', 'a'], ['plain', 'longer'], ['plain', 'bad'], ['mode', 'future'], ['url', 'https://other.test/'], ['url', null], ['instant', '2027-01-01T00:00:01Z'], ['bytes', 'QQ'], ['zero', false], ['flag', 0]] as const) {
              if (schema.safeParse({...wire, [field]: value}).success) throw new Error('accepted invalid ' + field);
            }
            const base = runtime.resolveSchema(BaseSchema);
            const invalid = {...decoded, plain: PlainState.A};
            if (z.safeEncode(schema, invalid).success) throw new Error('encoding bypassed restriction');
            if (base.parse({mode: 'future'}).mode?.toString() !== 'future') throw new Error('tolerant base changed');
            const other = createSchemaRuntime({...policy, format: 'cbor', dateEncoding: DateEncoding.MILLISECONDS_SINCE_EPOCH, numericDateDecoding: 1, arrayBufferEncoding: ArrayBufferEncoding.RAW_BYTES});
            const otherSchema = other.resolveSchema(ChildSchema);
            const alternative = {...wire, instant: 1767225601000, bytes: decoded.bytes};
            const alternateDecoded = otherSchema.parse(alternative);
            const alternateEncoded = z.encode(otherSchema, alternateDecoded) as Record<string, unknown>;
            if (alternateEncoded.instant !== alternative.instant || !(alternateEncoded.bytes instanceof ArrayBuffer)) throw new Error('transport policy lost');
            if (otherSchema.safeParse({...alternative, instant: 1767225602000}).success) throw new Error('numeric timestamp bypassed restriction');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("WireCheck", "!wire-check") to check),
        "wire-check",
      ),
    )
  }

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
                    |const schemaCache = new Map<object, unknown>();
                    |const runtime = {
                    |  policy: {},
                    |  resolveSchema(ref: any): any {
                    |    if (ref && typeof ref.build === 'function') {
                    |      if (!schemaCache.has(ref)) {
                    |        schemaCache.set(ref, ref.build(runtime));
                    |      }
                    |      return schemaCache.get(ref);
                    |    }
                    |    return ref;
                    |  },
                    |};
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
  fun `generates request methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMethodsTest/req-methods.default.api.ts")
  }

  @Test
  fun `generates path parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestUriParamsTest/req-uri-params.api.ts")
  }

  @Test
  fun `generates optional query parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestQueryParamsTest/req-query-params-optional.api.ts")
  }

  @Test
  fun `generates constant headers from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestHeaderParamsTest/req-header-params-constant.api.ts")
  }

  @Test
  fun `generates same-name mixed inline parameters from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMixedParamsTest/req-mixed-params-inline-types-same-name.api.ts")
  }

  @Test
  fun `generates request bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestBodyParamTest/req-body-param.api.ts")
  }

  @Test
  fun `generates inline set request and response bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-set-inline.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestBodyParamTest/req-body-param-set-inline.api.ts")
  }

  @Test
  fun `generates response bodies from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseBodyContentTest/res-body-param.api.ts")
  }

  @Test
  fun `generates explicit response media from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseBodyContentTest/res-body-param-explicit-content-type.api.ts")
  }

  @Test
  fun `generates inline response body models from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/res-body-param-inline-type.node-next.api.ts",
      TypeScriptTypeRegistry.ImportStyle.NodeNext,
    )
  }

  @Test
  fun `generates problem registration from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseProblemsTest/res-problems.api.ts")
  }

  @Test
  fun `generates no-problem registration from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "ResponseProblemsTest/res-no-problems.api.ts")
  }

  @Test
  fun `generates referenced problem types directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "ProblemService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "fetchTest",
                    method = "GET",
                    path = "/tests/{id}",
                    responses = listOf(GeneratedResponse(status = 200, type = GeneratedTypeRef.scalar("string"))),
                    problems = listOf(GeneratedTypeRef.named("InvalidIdProblem")),
                  ),
                ),
            ),
          ),
        problems =
          listOf(
            GeneratedProblem(
              name = "InvalidIdProblem",
              sourceName = "invalid_id",
              typeUri = "http://example.com/invalid_id",
              status = 400,
              title = "Invalid Id",
              detail = "The id contains one or more invalid characters.",
              fields =
                listOf(
                  GeneratedModelProperty("offendingId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val problemOutput =
      buildString {
        FileSpec
          .get(findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes), "invalid-id-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("ProblemTypesTest/invalid-id-problem.ts", problemOutput)
  }

  @Test
  fun `generates HTTP problem models as Sunday Problem errors`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "HTTP Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri")),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer")),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string", format = "uri")),
                ),
            ),
            GeneratedModel(
              name = "BadRequestProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty(
                    "validation",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.MAP,
                      name = "map",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val httpProblemOutput =
      buildString {
        FileSpec
          .get(findTypeMod("HttpProblem@!http-problem", builtTypes), "http-problem")
          .writeTo(this)
      }
    val badRequestOutput =
      buildString {
        FileSpec
          .get(findTypeMod("BadRequestProblem@!bad-request-problem", builtTypes), "bad-request-problem")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      httpProblemOutput.contains("Problem") &&
        httpProblemOutput.contains("from '@outfoxx/sunday'"),
      httpProblemOutput,
    )
    assertTrue(
      httpProblemOutput.contains("export class HttpProblem extends Problem implements HttpProblemSpec"),
      httpProblemOutput,
    )
    assertTrue(httpProblemOutput.contains("type: init.type ?? Problem.BLANK_URL"), httpProblemOutput)
    assertTrue(httpProblemOutput.contains("detail: string;"), httpProblemOutput)
    assertFalse(httpProblemOutput.contains("type: URL | null | undefined;"), httpProblemOutput)
    assertTrue(
      badRequestOutput.contains("export class BadRequestProblem extends HttpProblem implements BadRequestProblemSpec"),
      badRequestOutput,
    )
    assertFalse(badRequestOutput.contains("title: string | null | undefined;"), badRequestOutput)
    assertFalse(badRequestOutput.contains("this.title = init.title;"), badRequestOutput)
  }

  @Test
  fun `generates nullify methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "RequestMethodsTest/req-methods-nullify.default.api.ts")
  }

  @Test
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
  fun `generates base URL companion from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BaseUriTest/base-uri.api.ts")
  }

  @Test
  fun `generates request builder methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BuilderMethodsTest/request-builder.api.ts")
  }

  @Test
  fun `generates response builder methods from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(compiler, testUri, "BuilderMethodsTest/response-builder.api.ts")
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Model API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userTypeName = TypeName.namedImport("User", "!user")
    assertTrue(builtTypes.containsKey(userTypeName), "Available types: ${builtTypes.keys.joinToString()}")

    val source =
      buildString {
        FileSpec
          .get(findTypeMod("User@!user", builtTypes), "user")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("export type User = SchemaOutput<typeof UserSchema>;"), source)
    assertTrue(source.contains("'displayName': z.string().nullish()"), source)
    assertTrue(source.contains("export const UserSchema"), source)
    assertFalse(source.contains("export interface UserSpec"), source)
    assertFalse(source.contains("export class User"), source)
  }

  @Test
  fun `applies IR validation constraints to TypeScript Sunday schemas`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Validation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchUsers",
                    method = "GET",
                    path = "/users",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "q",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string"),
                          required = true,
                          validation = mapOf("minLength" to "2", "maxLength" to "80"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "CreateUserRequest",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    validation =
                      mapOf(
                        "minLength" to "2",
                        "maxLength" to "50",
                        "pattern" to "^[A-Za-z].*$",
                      ),
                  ),
                  GeneratedModelProperty(
                    "luckyNumber",
                    GeneratedTypeRef.scalar("integer"),
                    validation =
                      mapOf(
                        "minimum" to "1",
                        "maximum" to "100",
                      ),
                  ),
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string", format = "uuid")),
                    ),
                    validation =
                      mapOf(
                        "minItems" to "1",
                        "maxItems" to "5",
                      ),
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource =
      buildString {
        FileSpec
          .get(findTypeMod("CreateUserRequest@!create-user-request", builtTypes), "create-user-request")
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get(findTypeMod("UsersAPI@!users-api", builtTypes), "users-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(requestSource.contains("'email': z.string().email()"), requestSource)
    assertTrue(requestSource.contains("'displayName': z.string().min(2).max(50).regex(/^[A-Za-z].*$/)"), requestSource)
    assertTrue(requestSource.contains("'luckyNumber': z.number().gte(1).lte(100).nullish()"), requestSource)
    assertTrue(requestSource.contains("'tags': z.array(z.string().uuid()).min(1).max(5).nullish()"), requestSource)
    assertTrue(serviceSource.contains("const searchUsersQParameterType = z.string().min(2).max(80);"), serviceSource)
    assertTrue(serviceSource.contains("q: searchUsersQParameterType.parse(q)"), serviceSource)
  }

  @Test
  fun `validates scalar alias parameters without requiring optional values`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Aliases",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              "Identifier",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              "IdentifierAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("Identifier")),
            ),
          ),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchUsers",
                    method = "GET",
                    path = "/users",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "id",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.named("Identifier"),
                          required = true,
                          validation =
                            mapOf(
                              "pattern" to "^[A-Z]+$",
                            ),
                        ),
                        GeneratedParameter(
                          "filter",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.named("IdentifierAlias"),
                          validation =
                            mapOf(
                              "pattern" to "^[A-Z]+$",
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )
    TypeScriptSundayIrGenerator(api, registry, TypeScriptSundayOptions("http://example.com/", emptyList(), "API"))
      .generateServiceTypes()
    val checked =
      registry.buildTypes() +
        (
          TypeName.namedImport("AliasParameterCheck", "!alias-parameter-check") to
            ModuleSpec
              .builder("AliasParameterCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |const api = %T({} as any);
                  |const omitted = api.searchUsers('ID', undefined) as any;
                  |if (omitted.request.queryParameters.filter !== undefined) throw new Error('optional filter changed');
                  |const supplied = api.searchUsers('ID', 'FILTER') as any;
                  |if (supplied.request.queryParameters.filter !== 'FILTER') throw new Error('filter changed');
                  |for (const [id, filter] of [[undefined, undefined], ['invalid!', undefined], ['ID', 'invalid!']]) {
                  |  let rejected = false;
                  |  try { api.searchUsers(id as string, filter); } catch { rejected = true; }
                  |  if (!rejected) throw new Error('invalid alias parameter accepted');
                  |}
                  """.trimMargin(),
                  TypeName.namedImport("createUsersAPI", "!users-api"),
                ),
              ).build()
        )
    assertTrue(compileAndRunTypes(compiler, checked, "alias-parameter-check"))
  }

  @Test
  fun `emits OpenAPI scalar alias reference fields as scalar schemas`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/scalar-alias-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val updateSource =
      buildString {
        FileSpec
          .get(findTypeMod("NarrativeScopeUpdate@!narrative-scope-update", builtTypes), "narrative-scope-update")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(updateSource.contains("'location': z.string().regex(/^[A-Z2-7]{26}$/).nullish()"), updateSource)
    assertFalse(updateSource.contains("z.record(z.string(), z.unknown()).regex"), updateSource)
  }

  @Test
  fun `treats OpenAPI empty schemas as unknown in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val holderSource =
      buildString {
        FileSpec
          .get(findTypeMod("AnyHolder@!any-holder", builtTypes), "any-holder")
          .writeTo(this)
      }
    val serviceSource =
      builtTypes
        .map { (typeName, moduleSpec) ->
          val imported = typeName.base as SymbolSpec.Imported
          val modulePath = imported.source.removePrefix("!")
          buildString {
            FileSpec
              .get(moduleSpec, modulePath)
              .writeTo(this)
          }
        }.single { source -> source.contains("updateValue") }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(holderSource.contains("'value': z.unknown().nullish()"), holderSource)
    assertTrue(holderSource.contains("'documented': z.unknown().nullish()"), holderSource)
    assertTrue(holderSource.contains("'named': z.unknown().nullish()"), holderSource)
    assertTrue(serviceSource.contains("body: unknown"), serviceSource)
    assertTrue(serviceSource.contains("Operation<unknown, unknown, Factory>"), serviceSource)
  }

  @Test
  fun `preserves OpenAPI inline object properties beside conditional allOf in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/inline-object-conditional-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val dataSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "visualization-render-graph-task-settled-data.ts",
      )
    val renderGraphSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "visualization-render-graph-task-settled-data-render-graph.ts",
      )
    val allOfRequiredSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "all-of-required-data.ts",
      )
    assertTrue(
      dataSource.contains(
        "'renderGraph': runtime.resolveSchema(VisualizationRenderGraphTaskSettledDataRenderGraphSchema)",
      ),
      dataSource,
    )
    assertTrue(allOfRequiredSource.contains("'value': z.string()"), allOfRequiredSource)
    assertTrue(renderGraphSource.contains("'graphJobId': z.string()"), renderGraphSource)
    assertTrue(renderGraphSource.contains("'taskId': z.string()"), renderGraphSource)
    assertTrue(
      renderGraphSource.contains("'state': runtime.resolveSchema(RenderGraphTaskSettledStateSchema)"),
      renderGraphSource,
    )
    assertTrue(renderGraphSource.contains("'completedCount': z.number()"), renderGraphSource)
    assertTrue(renderGraphSource.contains("'taskCount': z.number()"), renderGraphSource)
  }

  @Test
  fun `uses content type header parameter as request media selection in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Avatar API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "putUserAvatar",
                    method = "PUT",
                    path = "/users/{userId}/avatar",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "userId",
                          GeneratedParameter.Location.PATH,
                          GeneratedTypeRef.scalar("string"),
                          required = true,
                        ),
                        GeneratedParameter(
                          "contentType",
                          GeneratedParameter.Location.HEADER,
                          GeneratedTypeRef.named("AvatarContentType"),
                          required = true,
                          serializationName = "Content-Type",
                        ),
                      ),
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("image/png", "image/jpeg", "image/webp"),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "AvatarContentType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("image/png", "image/jpeg", "image/webp"),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get(findTypeMod("UsersAPI@!users-api", builtTypes), "users-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("contentTypes: [MediaType.from(contentType)]"), serviceSource)
    assertFalse(serviceSource.contains("'Content-Type': contentType"), serviceSource)
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

  @Test
  fun `rejects duplicate wire values only for tolerant TypeScript enums`(compiler: TypeScriptCompiler) {
    val strictTypeRegistry = TypeScriptTypeRegistry(setOf())
    val strictModel =
      GeneratedModel(
        name = "Status",
        kind = GeneratedModel.Kind.ENUM,
        values = listOf("active", "active", "unknown"),
        enumValueNames = listOf("Primary", "Alias", "Unknown"),
      )
    val strictApi =
      GeneratedApi(
        name = "Strict Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(strictModel),
      )

    TypeScriptSundayIrGenerator(strictApi, strictTypeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, strictTypeRegistry.buildTypes()))

    val tolerantTypeRegistry = TypeScriptTypeRegistry(setOf())
    val tolerantApi =
      strictApi.copy(
        name = "Tolerant Enum API",
        models = listOf(strictModel.copy(unknownValue = "unknown")),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(tolerantApi, tolerantTypeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(
      error.message!!.contains(
        "TypeScript tolerant enum 'Status' wire value 'active' is used by multiple members 'Primary', 'Alias'",
      ),
      error.message,
    )
    assertTrue(error.message!!.contains("require unique wire values for raw-value interning"), error.message)
  }

  @Test
  fun `generates tolerant enum codecs that preserve raw values`(
    compiler: TypeScriptCompiler,
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
        RamlToGeneratedApi().convert(TestAPIProcessing.process(ramlUri)),
        OpenApiToGeneratedApi().convert(openApiUri),
        AsyncApiToGeneratedApi().convertFragment(asyncApiUri).api,
        GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composedApi)),
      )

    apis.forEach { api ->
      val typeRegistry = TypeScriptTypeRegistry(setOf())
      TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
        .generateServiceTypes()
      assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))
    }

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(composedApi, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val runtimeCheckedTypes =
      typeRegistry.buildTypes() +
        (
          TypeName.namedImport("TolerantEnumRuntimeCheck", "!tolerant-enum-runtime-check") to
            ModuleSpec
              .builder("TolerantEnumRuntimeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |const schemaCache = new Map<object, unknown>();
                  |const runtime = {
                  |  policy: {},
                  |  resolveSchema(ref: any): any {
                  |    if (ref && typeof ref.build === 'function') {
                  |      if (!schemaCache.has(ref)) {
                  |        schemaCache.set(ref, ref.build(runtime));
                  |      }
                  |      return schemaCache.get(ref);
                  |    }
                  |    return ref;
                  |  },
                  |};
                  |const schema = runtime.resolveSchema(%T);
                  |const TaskStateType = %T;
                  |const firstFromValue = TaskStateType.fromValue('future');
                  |const secondFromValue = TaskStateType.fromValue('future');
                  |const firstUnknown = schema.parse('future');
                  |const secondUnknown = schema.parse('future');
                  |const otherUnknown = schema.parse('other');
                  |if (firstFromValue !== secondFromValue || firstUnknown !== firstFromValue ||
                  |    firstUnknown !== secondUnknown || firstUnknown !== TaskStateType.Unknown('future')) {
                  |  throw new Error('equal unknown values were not interned');
                  |}
                  |if (TaskStateType.Unknown('pending') !== TaskStateType.Pending ||
                  |    TaskStateType.fromValue('pending') !== TaskStateType.Pending) {
                  |  throw new Error('known values were not canonicalized');
                  |}
                  |if (firstUnknown === otherUnknown || firstUnknown.kind !== 'Unknown' || firstUnknown.rawValue !== 'future') {
                  |  throw new Error('distinct unknown values did not retain their identity and data');
                  |}
                  |if (new Set([firstUnknown, secondUnknown, otherUnknown]).size !== 2) {
                  |  throw new Error('interned values did not deduplicate in Set');
                  |}
                  |const values = new Map([[firstUnknown, 'found']]);
                  |if (values.get(secondUnknown) !== 'found') {
                  |  throw new Error('interned values did not work as Map keys');
                  |}
                  |if (schema.parse(schema.encode(firstUnknown)) !== firstUnknown) {
                  |  throw new Error('codec round-trip did not preserve the interned value');
                  |}
                  """.trimMargin(),
                  TypeName.namedImport("TaskStateSchema", "!task-state"),
                  TypeName.namedImport("TaskState", "!task-state"),
                ),
              ).build()
        )
    assertTrue(
      compileAndRunTypes(
        compiler,
        runtimeCheckedTypes,
        "tolerant-enum-runtime-check",
      ),
    )

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "task-state.ts")
    assertTrue(source.contains("export class TaskState"), source)
    assertTrue(source.contains("private static instances: Map<string, TaskState>"), source)
    assertTrue(source.contains("static Unknown(rawValue: string): TaskState"), source)
    assertTrue(source.contains("return TaskState.intern('Unknown', rawValue)"), source)
    assertTrue(source.contains("return TaskState.instances.get(rawValue) ?? TaskState.Unknown(rawValue)"), source)
    assertTrue(source.contains("encode: (value) => value.rawValue"), source)
    assertTrue(source.contains("readonly kind: 'Pending' | 'Running' | 'Unknown'"), source)
    assertTrue(source.contains("toString(): string"), source)
    assertTrue(source.contains("return this.rawValue"), source)
  }

  @Test
  fun `generates tolerant discriminator hierarchy fallbacks`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Jobs API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "JobPhase",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("started", "paused", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "JobProgress",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("jobId", GeneratedTypeRef.scalar("string"), required = true),
                ),
              discriminator = "phase",
              discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
            ),
            GeneratedModel(
              name = "JobStarted",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("JobProgress")),
              discriminatorValue = "started",
              properties =
                listOf(
                  GeneratedModelProperty("taskCount", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "JobPaused",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorValue = "paused",
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
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
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val hierarchySource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-progress.ts")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-progress-unknown.ts")
    val unionSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-event.ts")
    assertTrue(hierarchySource.contains("JobProgressUnknownSchema"), hierarchySource)
    assertTrue(fallbackSource.contains("export interface JobProgressUnknown"), fallbackSource)
    assertTrue(fallbackSource.contains("readonly rawBody: Readonly<Record<string, unknown>>"), fallbackSource)
    assertTrue(fallbackSource.contains("!['started'].includes(value)"), fallbackSource)
    assertTrue(unionSource.contains("JobEventUnknownSchema"), unionSource)
  }

  @Test
  fun `generates discriminated unions for mappings that reuse canonical schemas`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/reusable-discriminator-mapping.yaml") openApiUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri))

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

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
                  |const schemaCache = new Map<object, unknown>();
                  |const runtime = {
                  |  policy: {},
                  |  resolveSchema(ref: any): any {
                  |    if (ref && typeof ref.build === 'function') {
                  |      if (!schemaCache.has(ref)) {
                  |        schemaCache.set(ref, ref.build(runtime));
                  |      }
                  |      return schemaCache.get(ref);
                  |    }
                  |    return ref;
                  |  },
                  |};
                  |const eventEnvelopeSchema = runtime.resolveSchema(%T);
                  |const canonicalAlias = eventEnvelopeSchema.parse({type: 'event.legacy', data: {id: 'canonical'}});
                  |if (canonicalAlias.data.id !== 'canonical' || canonicalAlias.type.rawValue !== 'event.legacy') {
                  |  throw new Error('canonical mapping alias did not decode');
                  |}
                  |const notificationSchema = runtime.resolveSchema(%T);
                  |const recognized = notificationSchema.parse({event: {type: 'event.one', data: {id: 'known'}}});
                  |if (recognized.event.data.id !== 'known' || recognized.event.type.rawValue !== 'event.one') {
                  |  throw new Error('recognized canonical event did not decode');
                  |}
                  |const aliased = notificationSchema.parse({event: {type: 'event.legacy', data: {id: 'legacy'}}});
                  |if (aliased.event.data.id !== 'legacy' || aliased.event.type.rawValue !== 'event.legacy') {
                  |  throw new Error('aliased canonical event did not decode');
                  |}
                  |const encodedAlias = %T.encode(notificationSchema, aliased);
                  |if (encodedAlias.event.type !== 'event.legacy' || encodedAlias.event.data.id !== 'legacy') {
                  |  throw new Error('aliased canonical event did not encode');
                  |}
                  |const unknown = notificationSchema.parse({event: {type: 'future.event', detail: 'preserved'}});
                  |if (unknown.event.type.rawValue !== 'future.event' || unknown.event.rawBody.detail !== 'preserved') {
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
    assertTrue(envelopeSource.contains("const knownSchema = z.union(["), envelopeSource)
  }

  @Test
  fun `generates tolerant external discriminator fallbacks`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Events API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory"),
        models =
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
              properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true)),
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
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val envelopeSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "event-envelope.ts")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "event-data-unknown.ts")
    assertTrue(envelopeSource.contains("!['created'].includes(value)"), envelopeSource)
    assertTrue(envelopeSource.contains("EventDataUnknownSchema"), envelopeSource)
    assertTrue(fallbackSource.contains("version: number"), fallbackSource)
    assertTrue(fallbackSource.contains("readonly rawBody: Readonly<Record<string, unknown>>"), fallbackSource)
  }

  @Test
  fun `escapes tolerant discriminator mapping values in fallback guards`(compiler: TypeScriptCompiler) {
    val trailingBackslash = "trailing\\"
    val injectionShaped = "known\\'; globalThis.compromised = true; //"
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Escaped Discriminators API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "InternalKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf(trailingBackslash, injectionShaped, "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "InternalEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("InternalKind"), required = true),
                ),
              discriminator = "kind",
              discriminatorMappings =
                mapOf(
                  trailingBackslash to GeneratedTypeRef.named("TrailingEvent"),
                  injectionShaped to GeneratedTypeRef.named("InjectionEvent"),
                ),
            ),
            GeneratedModel(
              name = "TrailingEvent",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("InternalEvent")),
              discriminatorValue = trailingBackslash,
            ),
            GeneratedModel(
              name = "InjectionEvent",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("InternalEvent")),
              discriminatorValue = injectionShaped,
            ),
            GeneratedModel(
              name = "ExternalKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf(trailingBackslash, injectionShaped, "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "ExternalData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              discriminatorMappings =
                mapOf(
                  trailingBackslash to GeneratedTypeRef.named("TrailingData"),
                  injectionShaped to GeneratedTypeRef.named("InjectionData"),
                ),
            ),
            GeneratedModel(
              name = "TrailingData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("ExternalData")),
              discriminatorValue = trailingBackslash,
            ),
            GeneratedModel(
              name = "InjectionData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("ExternalData")),
              discriminatorValue = injectionShaped,
            ),
            GeneratedModel(
              name = "ExternalEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("ExternalKind"), required = true),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("ExternalData"),
                    required = true,
                    externalDiscriminator = "kind",
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val expectedValues =
      listOf(injectionShaped, trailingBackslash)
        .sorted()
        .map { value -> CodeBlock.of("%S", value) }
        .joinToString(", ")
    val internalFallbackSource =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "internal-event-unknown.ts")
    val externalEnvelopeSource =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "external-envelope.ts")
    assertTrue(internalFallbackSource.contains("![$expectedValues].includes(value)"), internalFallbackSource)
    assertTrue(externalEnvelopeSource.contains("![$expectedValues].includes(value)"), externalEnvelopeSource)
  }

  @Test
  fun `rejects invalid explicit TypeScript enum member names`() {
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
              enumValueNames = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '123'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid member name '123'"), error.message)
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
  fun `rejects enum parameter defaults that do not match enum entries`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum Default API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "SearchService",
              baseUri = "https://{status}.example.com",
              baseUriParameters =
                listOf(
                  GeneratedParameter(
                    "status",
                    GeneratedParameter.Location.PATH,
                    GeneratedTypeRef.named("Status"),
                    defaultValue = "missing",
                  ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    id = "search",
                    method = "GET",
                    path = "/search",
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("TypeScript enum 'Status' default value 'missing'"), error.message)
    assertTrue(error.message!!.contains("does not match any enum value"), error.message)
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
  fun `generates inherited discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
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
    val child1Output =
      buildString {
        FileSpec
          .get(findTypeMod("Child1@!child1", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlDiscriminatedTypesTest/simple.parent.ts", parentOutput)
    assertSnapshot("RamlDiscriminatedTypesTest/simple.sunday-ir.child1.ts", child1Output)
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
  fun `generates externally discriminated models directly from IR with existing TypeScript output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI,
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
    val testOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Test@!test", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator.parent.ts", parentOutput)
    assertSnapshot("RamlTypeAnnotationsTest/external-discriminator-sunday-ir.test.ts", testOutput)
  }

  @Test
  fun `public TypeScript Sunday generator owns inherited discriminated model generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val generatedTypes =
      generateSunday(testUri, typeRegistry, compiler, typeScriptSundayTestOptions)

    assertTrue(generatedTypes.containsKey(TypeName.standard("Parent@!parent")))
    assertTrue(generatedTypes.containsKey(TypeName.standard("Child1@!child1")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("ParentSchema@!parent-schema")))
    assertFalse(generatedTypes.containsKey(TypeName.standard("Child1Schema@!child1-schema")))
  }

  @Test
  fun `generates nested shared models directly from IR`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val groupOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Group@!group", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(groupOutput.contains("export type Group = SchemaOutput<typeof GroupSchema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Group"), groupOutput)
    assertTrue(groupOutput.contains("export type Member1 = SchemaOutput<typeof Member1Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export type Member2 = SchemaOutput<typeof Member2Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Member1"), groupOutput)
    assertTrue(groupOutput.contains("export type Sub = SchemaOutput<typeof SubSchema>;"), groupOutput)
    assertFalse(groupOutput.contains("export class Group"), groupOutput)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: TypeScriptCompiler) {
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Imported API",
        source = mainSource,
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = mainSource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "main-models")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "library-models")),
              properties =
                listOf(
                  GeneratedModelProperty("libraryValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Consumer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("main", GeneratedTypeRef.named("Test", source = mainSource), required = true),
                  GeneratedModelProperty(
                    "library",
                    GeneratedTypeRef.named("Test", source = librarySource),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get(findTypeMod("Consumer@!consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!main-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!library-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(source.contains("import {TestSchema} from './main-models/test';"), source)
    assertTrue(
      source.contains("import {TestSchema as TestSchema_} from './library-models/test';"),
      source,
    )
    assertTrue(source.contains("'main': z.lazy(() => runtime.resolveSchema(TestSchema))"), source)
    assertTrue(source.contains("'library': z.lazy(() => runtime.resolveSchema(TestSchema_))"), source)
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
      locationSource.contains("'parent': z.lazy(() => runtime.resolveSchema(LocationSummarySchema)).nullish()"),
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
      envelopeOutput.contains("'actor': z.lazy(() => runtime.resolveSchema(EventIdentitySchema)).nullish()"),
      envelopeOutput,
    )
    assertTrue(envelopeOutput.contains("'description': z.string().nullish()"), envelopeOutput)
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

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `AsyncAPI enum refinements retain inherited storage and validate allowed values`(
    composed: Boolean,
    compiler: TypeScriptCompiler,
    @ResourceUri("asyncapi/ir/inherited-enum.yaml") asyncApiUri: URI,
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") openApiUri: URI,
  ) {
    val sources = if (composed) listOf(openApiUri, asyncApiUri) else listOf(asyncApiUri)
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(sources), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("InheritedEnumCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {EventType} from './event-type';
            import {BaseEvent, BaseEventSchema} from './base-event';
            import {AlphaEvent, AlphaEventSchema} from './alpha-event';
            import {BetaEvent, BetaEventSchema} from './beta-event';
            import {AlphaLeafEvent, AlphaLeafEventSchema} from './alpha-leaf-event';
            import {ReferencedAlphaEvent, ReferencedAlphaEventSchema} from './referenced-alpha-event';
            import {OptionalAlphaEventSchema} from './optional-alpha-event';
            import {MappedEventSchema} from './mapped-event';
            import {RecursiveAlphaEventSchema} from './recursive-alpha-event';

            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const events: BaseEvent[] = [
              {type: EventType.Alpha} satisfies AlphaEvent,
              {type: EventType.Beta} satisfies BetaEvent,
              {type: EventType.Alpha} satisfies AlphaLeafEvent,
              {type: EventType.Alpha} satisfies ReferencedAlphaEvent,
            ];
            const canonical: EventType[] = events.map(event => event.type);
            // @ts-expect-error inherited requiredness must survive the subtype refinement
            const missing: AlphaEvent = {};
            // @ts-expect-error subtype storage must use the named enum rather than a string
            const stringType: AlphaEvent = {type: 'alpha'};
            if (canonical[0] !== EventType.Alpha) throw new Error('enum storage changed');
            for (const [factory, value, invalidValue] of [
              [AlphaEventSchema, EventType.Alpha, EventType.Beta],
              [AlphaLeafEventSchema, EventType.Alpha, EventType.Beta],
              [ReferencedAlphaEventSchema, EventType.Alpha, EventType.Beta],
              [BetaEventSchema, EventType.Beta, EventType.Alpha],
            ] as const) {
              const schema = runtime.resolveSchema(factory);
              const wire = {type: value};
              const decoded = schema.parse(wire);
              const type: EventType = decoded.type;
              if (type !== value) throw new Error('wrong enum value');
              if (JSON.stringify(z.encode(schema, decoded)) !== JSON.stringify(wire)) throw new Error('round trip changed');
              for (const invalid of [{}, {type: null}, {type: invalidValue}]) {
                if (schema.safeParse(invalid).success) throw new Error('requiredness or narrowing lost');
              }
              if (z.safeEncode(schema, {type: invalidValue}).success) throw new Error('encoding bypassed narrowing');
            }
            for (const type of [EventType.Alpha, EventType.Beta]) {
              if (runtime.resolveSchema(BaseEventSchema).parse({type}).type !== type) throw new Error('base enum narrowed');
            }
            const optional = runtime.resolveSchema(OptionalAlphaEventSchema);
            if (optional.parse({})['event-type'] !== undefined) throw new Error('optional property became required');
            const wire = {'event-type': 'alpha'};
            const decoded = optional.parse(wire);
            const optionalType: EventType | null | undefined = decoded['event-type'];
            if (optionalType !== EventType.Alpha) throw new Error('optional enum storage changed');
            if (JSON.stringify(z.encode(optional, decoded)) !== JSON.stringify(wire)) throw new Error('wire name changed');
            if (optional.safeParse({'event-type': 'beta'}).success) throw new Error('optional restriction lost');
            const mapped = runtime.resolveSchema(MappedEventSchema);
            for (const type of ['alpha', 'beta']) {
              const decoded = mapped.parse({type});
              if (decoded.type !== type || JSON.stringify(z.encode(mapped, decoded)) !== JSON.stringify({type})) {
                throw new Error('mapped discriminator round trip changed');
              }
            }
            for (const invalid of [{}, {type: null}, {type: 'unknown'}]) {
              if (mapped.safeParse(invalid).success) throw new Error('invalid discriminator accepted');
            }
            const recursive = runtime.resolveSchema(RecursiveAlphaEventSchema);
            const nested = {type: 'alpha', next: {type: 'alpha'}};
            const roundTripped = recursive.parse(z.encode(recursive, recursive.parse(nested)));
            const nestedType: EventType | undefined = roundTripped.next?.type;
            if (roundTripped.type !== EventType.Alpha || nestedType !== EventType.Alpha) throw new Error('recursive round trip changed');
            if (recursive.safeParse({type: 'alpha', next: {type: 'beta'}}).success) throw new Error('recursive restriction lost');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("InheritedEnumCheck", "!inherited-enum-check") to check),
        "inherited-enum-check",
      ),
    )
  }

  @Test
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

  private fun assertIrServiceSnapshot(
    compiler: TypeScriptCompiler,
    testUri: URI,
    snapshotPath: String,
    importStyle: TypeScriptTypeRegistry.ImportStyle = TypeScriptTypeRegistry.ImportStyle.ESM,
    options: TypeScriptSundayOptions = typeScriptSundayTestOptions,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf(), importStyle)
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findTypeMod("API@!api${importStyle.importExtension}", builtTypes)
    val output =
      buildString {
        FileSpec
          .get(typeSpec, "api${importStyle.importExtension}")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertSnapshot(snapshotPath, output)
  }

  private val aggregateServiceOptions =
    TypeScriptSundayOptions(
      "http://example.com/",
      listOf("application/json"),
      "API",
      true,
      "TurnPostAPI",
    )
}
