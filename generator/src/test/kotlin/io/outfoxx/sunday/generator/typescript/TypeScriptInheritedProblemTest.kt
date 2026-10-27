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

package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.tools.TEAM_NOT_FOUND_JSON
import io.outfoxx.sunday.generator.tools.USER_NOT_FOUND_JSON
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@TypeScriptTest
@Tag("models")
@Tag("responses")
class TypeScriptInheritedProblemTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `problem child can introduce explicit storage while closed siblings reject extras`(
    preserve: Boolean,
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val api = GeneratedApiIrExporter().export(listOf(source))
    val models =
      api.models.map { model ->
        model.copy(
          additionalProperties =
            when (model.name) {
              "QueuedProblem" -> GeneratedAdditionalProperties(allowed = true)
              "DefaultedProblem" -> GeneratedAdditionalProperties(allowed = false)
              else -> null
            },
        )
      }
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      api.copy(models = models),
      registry,
      TypeScriptSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        preserveUnknownFields = if (preserve) UnknownFieldPreservation.On else UnknownFieldPreservation.Schema,
      ),
    ).generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("StorageCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {QueuedProblem, QueuedProblemSchema} from './queued-problem';
            import {DefaultedProblem, DefaultedProblemSchema} from './defaulted-problem';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(QueuedProblemSchema);
            const raw = {...$INHERITED_PROBLEM_JSON, future: {nested: null}};
            const decoded = schema.parse(raw);
            const copied = decoded.copy({});
            const constructed = new QueuedProblem({nullableParent: null, nullableChild: null,
              metadata: {region: 'west'}, additionalProperties: {future: {nested: null}}});
            for (const value of [decoded, copied, constructed]) {
              const encoded = schema.encode(value) as Record<string, unknown>;
              if (JSON.stringify(encoded.future) !== JSON.stringify(raw.future)) throw new Error('explicit child storage lost');
            }
            const closed = runtime.resolveSchema(DefaultedProblemSchema);
            const closedValue = new DefaultedProblem();
            if (closed.safeParse({...closed.encode(closedValue), future: 1}).success) throw new Error('closed sibling accepted extra');
            if (('additionalProperties' in closedValue) !== $preserve) throw new Error('closed sibling storage should only come from its ancestor');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("StorageCheck", "!storage-check") to consumer),
        "storage-check",
      ),
    )
  }

  @Test
  fun `inherited problem fields survive construction decoding and encoding`(
    @ResourceUri("openapi/ir/inherited-problem-relative.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      GeneratedApiIrExporter().export(listOf(source)),
      registry,
      TypeScriptSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport, Problem, createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {createAPI} from './api';
            import {QueuedProblem, QueuedProblemSchema} from './queued-problem';
            import {RebuildingProblem} from './rebuilding-problem';
            import {DefaultedProblem} from './defaulted-problem';
            import {TeamNotFoundProblem} from './team-not-found-problem';
            import {UserNotFoundProblem} from './user-not-found-problem';
            import {NotFoundProblem} from './not-found-problem';
            import {NotFoundProblemsSchema} from './not-found-problems';
            function ok(value: boolean, message = 'assertion failed'): void { if (!value) throw new Error(message); }
            function deepStrictEqual(value: unknown, expected: unknown): void {
              const sorted = (v: unknown): unknown => v && typeof v === 'object' && !Array.isArray(v)
                ? Object.fromEntries(Object.entries(v).sort().map(([k, x]) => [k, sorted(x)])) : v;
              ok(JSON.stringify(sorted(value)) === JSON.stringify(sorted(expected)), JSON.stringify(value));
            }
            function throws(action: () => unknown): void {
              try { action(); } catch { return; }
              throw new Error('Expected failure');
            }
            const raw = $INHERITED_PROBLEM_JSON;
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(QueuedProblemSchema);
            const value = new QueuedProblem({detail: 'Resource A is rebuilding', traceId: 'trace-A',
              resourceKey: 'A', nullableParent: null, nullableChild: null, metadata: {region: 'west'}, queuePosition: 2});
            const baseDefaults = new DefaultedProblem();
            ok(baseDefaults.detail === 'Something bad happened');
            ok(baseDefaults.enabled === false && baseDefaults.count === 0 && baseDefaults.note === 'fallback');
            ok(baseDefaults.location?.toString() === 'https://example.com/default' && baseDefaults.revision === 1);
            ok(new DefaultedProblem({enabled: true, count: 3, note: null}).note === null);
            ok(!Reflect.set(baseDefaults, 'revision', 2));
            const defaulted = new QueuedProblem({nullableParent: null, nullableChild: null, metadata: {region: 'west'}});
            deepStrictEqual(schema.encode(defaulted), raw);
            const explicitUndefined = new QueuedProblem({detail: undefined, traceId: undefined, resourceKey: undefined,
              queuePosition: undefined, nullableParent: null, nullableChild: null, metadata: {region: 'west'}});
            deepStrictEqual(schema.encode(explicitUndefined), raw);
            const overridden = new QueuedProblem({detail: 'Override', traceId: 'trace-B', resourceKey: 'B',
              queuePosition: 3, nullableChild: null, metadata: {region: 'east'}});
            ok(overridden.detail === 'Override' && overridden.traceId === 'trace-B');
            ok(overridden.resourceKey === 'B' && overridden.queuePosition === 3);
            ok(!Reflect.set(value, 'retryAfterSeconds', 2));
            ok(!Reflect.set(value, 'queueName', 'wrong'));
            ok(!Reflect.set(value, 'type', new URL('https://example.com/wrong')));
            if (value.type instanceof URL) value.type.href = 'https://example.com/wrong';
            deepStrictEqual(schema.encode(value), raw);
            throws(() => new QueuedProblem({...value, retryAfterSeconds: 2}));
            ok(value instanceof Problem);
            ok(value instanceof RebuildingProblem);
            deepStrictEqual(schema.encode(value), raw);
            deepStrictEqual(schema.encode(schema.parse(raw)), raw);
            for (const invalid of [{...raw, resourceKey: ''}, {...raw, retryAfterSeconds: 2},
              {...raw, queuePosition: 0}, {...raw, type: 'https://example.com/wrong'}, {...raw, nullableChild: undefined},
              {...raw, resourceKey: undefined}, {...raw, queuePosition: undefined}]) {
              ok(!schema.safeParse(invalid).success, JSON.stringify(invalid));
            }
            throws(() => new QueuedProblem({...value, resourceKey: ''}));
            throws(() => new QueuedProblem({...value, queuePosition: 0}));
            for (const member of ['type', 'title', 'status', 'detail', 'instance']) {
              const missing: Record<string, unknown> = {...raw}; delete missing[member];
              const baseline = schema.safeParse(missing);
              for (const wrong of member === 'status' ? [null, true, '401', [], {}] : [null, true, 42, [], {}]) {
                const actual = schema.safeParse({...missing, [member]: wrong});
                ok(actual.success === baseline.success, JSON.stringify({member, wrong, actual, baseline}));
                if (actual.success && baseline.success) deepStrictEqual(schema.encode(actual.data), schema.encode(baseline.data));
              }
            }
            async function httpProblem(): Promise<void> {
              globalThis.fetch = async () => new Response(JSON.stringify(raw), {
                status: 409, headers: {'Content-Type': 'application/problem+json'}
              });
              const client = createAPI(new FetchTransport('https://example.test'));
              try { await client.readResource().execute(); } catch (error) {
                ok(error instanceof QueuedProblem, String(error));
                ok(error instanceof RebuildingProblem);
                deepStrictEqual(schema.encode(error as QueuedProblem), raw);
                return;
              }
              throw new Error('HTTP problem was not raised');
            }
            async function missingProblems(): Promise<void> {
              const union = runtime.resolveSchema(NotFoundProblemsSchema);
              for (const [raw, value] of [[$TEAM_NOT_FOUND_JSON, new TeamNotFoundProblem({nullableParent: null})],
                [$USER_NOT_FOUND_JSON, new UserNotFoundProblem({nullableParent: null})]] as const) {
                ok(value instanceof NotFoundProblem && value instanceof Problem);
                deepStrictEqual(union.encode(value), raw);
                deepStrictEqual(union.encode(union.parse(raw)), raw);
                ok(!Reflect.set(value, 'errorCode', 'changed'));
                const overridden = new TeamNotFoundProblem({detail: 'Override', resourceId: '123'});
                ok(overridden.detail === 'Override' && overridden.resourceId === '123');
                for (const field of ['type', 'error-code']) {
                  ok(!union.safeParse({...raw, [field]: 'wrong'}).success, field);
                }
                const {resourceId: _id, ...missingRequired} = raw;
                ok(!union.safeParse(missingRequired).success);
                globalThis.fetch = async () => new Response(JSON.stringify(raw), {
                  status: 404, headers: {'Content-Type': 'application/problem+json'}
                });
                const client = createAPI(new FetchTransport('https://example.test'));
                for (const request of [client.readMissing('test'), client.readMissingNamed('test')]) {
                  try { await request.execute(); } catch (error) {
                    ok(error instanceof value.constructor, String(error));
                    ok(error instanceof NotFoundProblem);
                    deepStrictEqual(union.encode(error as TeamNotFoundProblem | UserNotFoundProblem), raw);
                    continue;
                  }
                  throw new Error('404 problem was not raised');
                }
              }
            }
            void httpProblem().then(missingProblems);
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("Check", "!check") to consumer),
        "check",
      ),
    )
  }
}
