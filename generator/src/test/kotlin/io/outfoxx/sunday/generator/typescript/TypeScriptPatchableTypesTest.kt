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

import io.outfoxx.sunday.generator.tools.patchableApi
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
@Tag("models")
@Tag("validation")
@Tag("requests")
class TypeScriptPatchableTypesTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference"])
  fun `PATCH schemas preserve presence without applying defaults`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(patchableApi(frontend, directory), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("PatchCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {SomeRequestPatch, SomeRequestPatchSchema} from './some-request-patch';
            import {SomeRequestSchema} from './some-request';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(SomeRequestPatchSchema);
            for (const fields of [{}, {title: 'new title'}, {description: null}, {title: null, 'display-name': null, 'optional-alias': null}, {'required-nullable': 'new', 'required-alias': 'new'}, {description: 'text', count: 2, state: 'active', 'display-name': 'Name'}]) {
              const patch = schema.parse(fields);
              const restored = JSON.parse(JSON.stringify(z.encode(schema, patch)));
              if (Object.keys(restored).length !== Object.keys(fields).length) throw new Error('omission changed');
              for (const [key, value] of Object.entries(fields)) {
                if (restored[key] !== value) throw new Error('value changed: ' + key);
              }
            }
            const empty: SomeRequestPatch = {title: undefined};
            if (JSON.stringify(z.encode(schema, empty)) !== '{}') throw new Error('undefined encoded');
            for (const invalid of [{'required-nullable': null}, {'required-alias': null}, {title: 'x'}, {count: 0}, {count: null}]) {
              if (schema.safeParse(invalid).success) throw new Error('invalid patch accepted');
            }
            const ordinary = runtime.resolveSchema(SomeRequestSchema);
            if (ordinary.safeParse({}).success) throw new Error('ordinary required field lost');
            if (ordinary.parse({count: 2, 'required-nullable': null, 'required-alias': null}).title !== ${if (frontend
                .startsWith(
                  "raml",
                )
            ) {
              "undefined"
            } else {
              "'initial'"
            }}) throw new Error('ordinary default lost');
            const request = runtime.forMode('request').resolveSchema(SomeRequestPatchSchema);
            const unknown = schema.parse({state: 'future'});
            if (request.safeEncode(unknown).success) throw new Error('unknown request enum accepted');
            const mutable = schema.parse({title: 'valid'});
            request.encode(mutable);
            mutable.title = null;
            request.encode(mutable);
            mutable.title = undefined;
            if (JSON.stringify(request.encode(mutable)) !== '{}') throw new Error('cancelled update encoded');
            mutable.title = 'x';
            if (request.safeEncode(mutable).success) throw new Error('mutation missed');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("PatchCheck", "!patch-check") to check),
        "patch-check",
      ),
    )
  }
}
