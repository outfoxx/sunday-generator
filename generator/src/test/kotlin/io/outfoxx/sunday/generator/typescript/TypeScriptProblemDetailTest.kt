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

import io.outfoxx.sunday.generator.tools.problemDetailApi
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
@Tag("responses")
class TypeScriptProblemDetailTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `problem details are optional defaults and can be overridden`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      problemDetailApi(frontend, directory).let { api ->
        api.copy(problems = api.problems.map { if (it.detail == null) it.copy(title = null, status = null) else it })
      },
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {WithDefaultProblem, WithDefaultProblemSchema} from './with-default-problem.js';
            import {WithoutDefaultProblem, WithoutDefaultProblemSchema} from './without-default-problem.js';
            const runtime = createSchemaRuntime({
              format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0,
              arrayBufferEncoding: ArrayBufferEncoding.BASE64,
            });
            const schema = runtime.resolveSchema(WithDefaultProblemSchema);
            const decoded = schema.parse({
              type: WithDefaultProblem.TYPE, title: 'With default', status: 400, detail: 'Wire detail',
            });
            const cases = [
              [decoded.detail, 'Wire detail'],
              [(schema.encode(decoded) as {detail?: string}).detail, 'Wire detail'],
              [new WithDefaultProblem().detail, 'Default detail'],
              [new WithoutDefaultProblem().detail, undefined],
              [new WithoutDefaultProblem().title, undefined],
              [new WithoutDefaultProblem().status, undefined],
              [new WithDefaultProblem({detail: 'Override'}).detail, 'Override'],
              [new WithoutDefaultProblem({detail: 'Override'}).detail, 'Override'],
              [new WithDefaultProblem({detail: ''}).detail, ''],
            ];
            for (const [actual, expected] of cases) {
              if (actual !== expected) throw new Error('Unexpected detail: ' + actual);
            }
            const withoutDefaults = runtime.resolveSchema(WithoutDefaultProblemSchema);
            const minimal = withoutDefaults.encode(withoutDefaults.parse({type: WithoutDefaultProblem.TYPE})) as Record<string, unknown>;
            if ('title' in minimal || 'status' in minimal || 'detail' in minimal)
              throw new Error('Absent standard members were invented');
            const valid: Record<string, unknown> = {type: WithDefaultProblem.TYPE, title: '', status: 409, detail: '', trace: null};
            const encoded = schema.encode(schema.parse(valid)) as Record<string, unknown>;
            if (encoded.title !== '' || encoded.status !== 409 || encoded.detail !== '' || encoded.trace !== null)
              throw new Error('Wire values or nullable extension changed');
            for (const member of ['type', 'title', 'status', 'detail', 'instance']) {
              const missing = {...valid}; delete missing[member];
              const wrong = member === 'status' ? [null, true, '400', [], {}] : [null, true, 42, [], {}];
              if (member === 'type') {
                for (const raw of [missing, ...wrong.map(value => ({...valid, [member]: value}))]) {
                  if (schema.safeParse(raw).success) throw new Error('Missing type became custom constant');
                }
              } else {
                const expected = JSON.stringify(schema.encode(schema.parse(missing)));
                for (const value of wrong) {
                  const actual = JSON.stringify(schema.encode(schema.parse({...valid, [member]: value})));
                  if (actual !== expected) throw new Error('Wrong category differs from missing: ' + member);
                }
              }
            }
            if (schema.safeParse({...valid, status: 409.5}).success) throw new Error('Fractional status accepted');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("Check", "!check") to check),
        "check",
        esm = true,
      ),
    )
  }
}
