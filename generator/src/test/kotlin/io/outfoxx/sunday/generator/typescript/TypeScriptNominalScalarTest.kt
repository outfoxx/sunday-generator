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

import io.outfoxx.sunday.generator.tools.nominalScalarApi
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
class TypeScriptNominalScalarTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `nominal scalars validate and preserve union identity`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      nominalScalarApi(frontend, directory),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("NominalCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            ${if (frontend == "asyncapi") "" else "import {createAPI} from './api';"}
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {BaseFactSid, BaseFactSidSchema, isBaseFactSid} from './base-fact-sid';
            import {BaseLossSid, isBaseLossSid} from './base-loss-sid';
            import {AnySid, AnySidSchema} from './any-sid';
            import {AmbiguousSidSchema} from './ambiguous-sid';
            import {PositiveCount} from './positive-count';
            import {Ratio} from './ratio';
            import {RecordSchema} from './record';
            import {DefaultsSchema} from './defaults';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(AnySidSchema);
            const fact = BaseFactSid('sid:f:abc');
            // @ts-expect-error Plain strings do not have nominal identity.
            const plain: BaseFactSid = 'sid:f:abc';
            // @ts-expect-error Different wrappers are not interchangeable.
            const wrong: BaseFactSid = BaseLossSid('sid:l:abc');
            const union: AnySid = fact;
            ${if (frontend == "asyncapi") "" else "createAPI({} as never)." + (if (frontend == "raml") "getRecordsId" else "getRecord") + "(union, undefined);"}
            if (!isBaseFactSid(union) || isBaseLossSid(union)) throw new Error('branch identity lost');
            for (const value of ['sid:f:abc', 'sid:l:abc']) {
              const decoded = schema.parse(value);
              if (z.encode(schema, decoded) !== value) throw new Error('not a scalar roundtrip');
            }
            function rejects(create: () => unknown): void {
              try { create(); } catch { return; }
              throw new Error('invalid input accepted');
            }
            rejects(() => BaseFactSid('invalid'));
            rejects(() => BaseFactSidSchema.parse('invalid'));
            rejects(() => schema.parse('invalid'));
            rejects(() => PositiveCount(0));
            rejects(() => Ratio(2));
            if (runtime.resolveSchema(DefaultsSchema).parse({}).fact !== ${if (frontend == "raml") "undefined" else "'sid:f:default'"}) throw new Error('nominal default lost');
            const ambiguous = runtime.resolveSchema(AmbiguousSidSchema);
            ${if (frontend == "raml") "ambiguous.parse('sid:f:abc');" else "rejects(() => ambiguous.parse('sid:f:abc'));"}
            const wire = {fact: 'sid:f:abc', identifiers: ['sid:l:abc'], count: 2, ratio: 0.5, enabled: true};
            const recordSchema = runtime.resolveSchema(RecordSchema);
            const record = recordSchema.parse(wire);
            if (JSON.stringify(z.encode(recordSchema, record)) !== JSON.stringify(wire)) throw new Error('record roundtrip failed');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("NominalCheck", "!nominal-check") to check),
        "nominal-check",
      ),
    )
  }
}
