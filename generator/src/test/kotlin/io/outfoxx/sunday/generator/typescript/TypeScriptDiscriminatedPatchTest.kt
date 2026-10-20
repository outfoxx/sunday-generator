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

import io.outfoxx.sunday.generator.tools.discriminatedPatchApi
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
class TypeScriptDiscriminatedPatchTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "explicit", "raml", "asyncapi", "composed", "renamed", "tolerant"])
  fun `discriminated patches retain required tags`(
    frontend: String,
    @TempDir directory: Path,
    compiler: TypeScriptCompiler,
  ) {
    val api = discriminatedPatchApi(frontend, directory)
    val tag = if (frontend == "renamed") "animal-kind" else "kind"
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("PatchCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {AnimalPatchSchema} from './animal-patch';
            import {Animal, AnimalSchema} from './animal';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(AnimalPatchSchema);
            for (const data of [{'$tag': 'cat'}, {'$tag': 'cat', nickname: null}, {'$tag': 'cat', name: 'Misty'}, {'$tag': 'cat', custom: 'extension'}]) {
              if (JSON.stringify(z.encode(schema, schema.parse(data))) !== JSON.stringify(data)) throw new Error('round trip');
            }
            const ordinary = runtime.resolveSchema(AnimalSchema);
            const base = ordinary.parse({'$tag': 'cat', name: 'Misty', detail: null, nickname: 'Kit', indoor: true});
            if (JSON.stringify(z.encode(schema, Animal.patch(base, base))) !== '{"$tag":"cat"}') throw new Error('no-op discriminator');
            const merged = Animal.merge(base, schema.parse({'$tag': 'cat', nickname: null}));
            const wire = z.encode(ordinary, merged) as Record<string, unknown>;
            if (wire.nickname !== undefined || wire.name !== 'Misty' || wire.indoor !== true) throw new Error('merge');
            const complete = ordinary.parse({'$tag': 'cat', name: 'Misty', detail: 'info'});
            const snapshot = z.encode(schema, Animal.patch(complete));
            if (JSON.stringify(snapshot) !== JSON.stringify(z.encode(ordinary, complete))) throw new Error('snapshot');
            let snapshotRejected = false;
            try { Animal.patch(base); } catch { snapshotRejected = true; }
            if (!snapshotRejected) throw new Error('required null snapshot');
            let rejected = false;
            try { Animal.merge(base, schema.parse({'$tag': 'dog'})); } catch { rejected = true; }
            if (!rejected) throw new Error('cross subtype merge');
            for (const value of [true, null, false]) {
              if (schema.safeParse({'$tag': 'cat', trained: value}).success) throw new Error('wrong variant field');
            }
            for (const data of [{}, {'$tag': null}, {'$tag': 'lizard'}, {'$tag': 'cat', name: null}, {'$tag': 'cat', detail: null}]) {
              if (schema.safeParse(data).success) throw new Error('invalid accepted');
            }
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
