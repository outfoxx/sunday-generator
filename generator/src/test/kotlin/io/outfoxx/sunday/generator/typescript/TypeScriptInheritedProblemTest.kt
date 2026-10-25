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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.tools.INHERITED_PROBLEM_JSON
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@Tag("models")
@Tag("responses")
class TypeScriptInheritedProblemTest {
  @Test
  fun `inherited problem fields survive construction decoding and encoding`(
    @ResourceUri("openapi/ir/inherited-problem.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(listOf(source)), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
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
            ok(value instanceof Problem);
            ok(value instanceof RebuildingProblem);
            deepStrictEqual(schema.encode(value), raw);
            deepStrictEqual(schema.encode(schema.parse(raw)), raw);
            for (const invalid of [{...raw, resourceKey: ''}, {...raw, retryAfterSeconds: 2},
              {...raw, queuePosition: 0}, {...raw, type: 'https://example.com/wrong'}, {...raw, nullableChild: undefined}]) {
              ok(!schema.safeParse(invalid).success, JSON.stringify(invalid));
            }
            throws(() => new QueuedProblem({...value, resourceKey: ''}));
            throws(() => new QueuedProblem({...value, queuePosition: 0}));
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
            void httpProblem();
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
