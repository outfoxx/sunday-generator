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
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
@Tag("models")
@Tag("responses")
class TypeScriptAboutBlankProblemTest {
  @Test
  fun `schema problems register unique wire types alongside annotated problems`(
    @ResourceUri("openapi/ir/schema-problem-registration.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(source), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    assertTrue(compileTypes(compiler, registry.buildTypes()))
    val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "api.ts")
    assertEquals(1, Regex("https://example.com/problems/missing").findAll(service).count(), service)
    assertEquals(1, Regex("https://example.com/problems/annotated").findAll(service).count(), service)
    assertTrue(!service.contains("about:blank"), service)
  }

  @Test
  fun `named URI enum constraints survive Problem codecs`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val source = directory.resolve("problems.yaml")
    Files.writeString(
      source,
      """
      openapi: 3.1.0
      info: {title: Problem enum, version: '1'}
      paths: {}
      components:
        schemas:
          ProblemType: {type: string, enum: ['about:blank', 'https://example.com']}
          HttpProblem:
            type: object
            properties:
              type: {${'$'}ref: '#/components/schemas/ProblemType'}
              title: {type: string}
              status: {type: integer}
      """.trimIndent(),
    )
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(source.toUri()), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {HttpProblemSchema} from './http-problem';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(HttpProblemSchema);
            for (const type of ['about:blank', 'https://example.com']) {
              const output = schema.encode(schema.parse({type})) as Record<string, unknown>;
              if (output.type !== type) throw new Error('Named URI enum changed on round trip');
            }
            if (schema.safeParse({type: 'https://example.org/wrong'}).success)
              throw new Error('Named URI enum constraint lost');
            if (schema.parse({type: null}).type.toString() !== 'about:blank')
              throw new Error('Wrong category did not use RFC default');
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
            import {FlexibleProblemSchema} from './flexible-problem';
            import {CustomProblemSchema} from './custom-problem';
            import {RequiredProblemSchema} from './required-problem';
            function ok(value: boolean): void { if (!value) throw new Error('Assertion failed'); }
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            for (const [value, definition, status, title] of [
              [new BadRequestProblem({detail: 'Details', traceId: 'trace'}), BadRequestProblemSchema, 400, 'Bad Request'],
              [new NotFoundProblem({detail: 'Details', traceId: 'trace'}), NotFoundProblemSchema, 404, 'Not Found']
            ] as const) {
              ok(value.type.toString() === 'about:blank' && value.status === status && value.title === title);
              const schema = runtime.resolveSchema(definition);
              const raw = {type: 'about:blank', status, title, detail: 'Details', traceId: 'trace'};
              ok(schema.safeParse(raw).success);
              const omitted = schema.parse({status, title, detail: 'Details', traceId: 'trace'});
              ok(omitted.type.toString() === 'about:blank');
              for (const [field, invalid] of [['status', 500], ['title', 'Wrong'], ['type', 'https://example.com/wrong']]) {
                ok(!schema.safeParse({...raw, [field]: invalid}).success);
              }
            }
            const generic = runtime.resolveSchema(FlexibleProblemSchema);
            const custom = runtime.resolveSchema(CustomProblemSchema);
            const required = runtime.resolveSchema(RequiredProblemSchema);
            const omitted = {title: 'Error', status: 400};
            const explicit = {...omitted, type: 'https://example.com/problems/custom'};
            ok(generic.parse(omitted).type.toString() === 'about:blank');
            ok(generic.parse(explicit).type.toString() === explicit.type);
            ok(custom.parse(explicit).type.toString() === explicit.type);
            ok(!custom.safeParse(omitted).success);
            ok(required.parse(omitted).type.toString() === 'about:blank');
            ok(required.parse(explicit).type.toString() === explicit.type);
            ok(generic.parse({...omitted, type: null}).type.toString() === 'about:blank');
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
                    ok(error.type.toString() === 'about:blank' && error.status === status && error.title === title);
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
