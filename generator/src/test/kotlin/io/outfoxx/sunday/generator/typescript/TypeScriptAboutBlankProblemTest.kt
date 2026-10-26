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
class TypeScriptAboutBlankProblemTest {
  @Test
  fun `standard status models retain constraints while HTTP errors use generic problems`(
    @ResourceUri("openapi/ir/about-blank-problems.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(source), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport, Problem, createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {createAPI} from './api';
            import {BadRequestProblem, BadRequestProblemSchema} from './bad-request-problem';
            import {NotFoundProblem, NotFoundProblemSchema} from './not-found-problem';
            function ok(value: boolean): void { if (!value) throw new Error('Assertion failed'); }
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            for (const [value, definition, status, title] of [
              [new BadRequestProblem({detail: 'Details', traceId: 'trace'}), BadRequestProblemSchema, 400, 'Bad Request'],
              [new NotFoundProblem({detail: 'Details', traceId: 'trace'}), NotFoundProblemSchema, 404, 'Not Found']
            ] as const) {
              ok(value.type.href === 'about:blank' && value.status === status && value.title === title);
              const schema = runtime.resolveSchema(definition);
              const raw = {type: 'about:blank', status, title, detail: 'Details', traceId: 'trace'};
              ok(schema.safeParse(raw).success);
              for (const [field, invalid] of [['status', 500], ['title', 'Wrong'], ['type', 'https://example.com/wrong']]) {
                ok(!schema.safeParse({...raw, [field]: invalid}).success);
              }
            }
            async function check(): Promise<void> {
              for (const [status, title] of [[400, 'Bad Request'], [404, 'Not Found'], [503, 'Service Unavailable']] as const) {
                for (const explicit of [false, true]) {
                  const raw = {status, title, detail: 'Details', traceId: 'trace', extra: {nested: [1, 2]},
                    ...(explicit ? {type: 'about:blank'} : {})};
                  globalThis.fetch = async () => new Response(JSON.stringify(raw), {
                    status, headers: {'Content-Type': 'application/problem+json'}
                  });
                  const client = createAPI(new FetchTransport('https://example.test'));
                  try { await client.getExample().execute(); } catch (error) {
                    if (!(error instanceof Problem)) throw error;
                    ok(!(error instanceof BadRequestProblem) && !(error instanceof NotFoundProblem));
                    ok(error.type.href === 'about:blank' && error.status === status && error.title === title);
                    ok(error.detail === 'Details' && error.parameters?.traceId === 'trace');
                    ok(JSON.stringify(error.parameters?.extra) === JSON.stringify({nested: [1, 2]}));
                    continue;
                  }
                  throw new Error('Expected generic HTTP problem');
                }
              }
            }
            void check();
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
