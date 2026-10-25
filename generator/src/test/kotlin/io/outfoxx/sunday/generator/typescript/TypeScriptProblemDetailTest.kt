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
      problemDetailApi(frontend, directory),
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
            import {WithoutDefaultProblem} from './without-default-problem.js';
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
              [new WithDefaultProblem({detail: 'Override'}).detail, 'Override'],
              [new WithoutDefaultProblem({detail: 'Override'}).detail, 'Override'],
              [new WithDefaultProblem({detail: ''}).detail, ''],
            ];
            for (const [actual, expected] of cases) {
              if (actual !== expected) throw new Error('Unexpected detail: ' + actual);
            }
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
