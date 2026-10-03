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
  @ValueSource(strings = ["raml", "raml-auto", "openapi", "asyncapi", "composed", "reference", "collisions"])
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
            import {SomeRequest, SomeRequestSchema} from './some-request';
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
            const empty: SomeRequestPatch = {title: undefined${if (frontend == "collisions") ", constructor: undefined" else ""}};
            if (JSON.stringify(z.encode(schema, empty)) !== '{}') throw new Error('undefined encoded');
            for (const invalid of [{'required-nullable': null}, {'required-alias': null}, {title: 'x'}, {count: 0}, {count: null}, {labels: {bad: 'x'}}]) {
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
            const base = ordinary.parse({count: 2, 'required-nullable': null, 'required-alias': null});
            let failed = false;
            try { SomeRequest.patch(base); } catch (error) {
              if (!(error instanceof z.ZodError) || !error.issues.some(issue => issue.path[0] === 'required-nullable')) throw error;
              failed = true;
            }
            if (!failed) throw new Error('required null snapshot accepted');
            if (Object.keys(schema.encode(SomeRequest.patch(base, base))).length) throw new Error('unchanged fields included');
            const changed = ordinary.parse({count: 3, 'required-nullable': 'valid', 'required-alias': null, title: 'Changed'});
            const changes = SomeRequest.patch(changed, base);
            const wireChanges = schema.encode(changes) as Record<string, unknown>;
            if (wireChanges['required-alias'] !== undefined || wireChanges.count !== 3 || wireChanges.title !== 'Changed') throw new Error('incorrect difference');
            const restored = SomeRequest.merge(base, changes);
            if (restored.count !== 3 || restored['required-nullable'] !== 'valid' || restored['required-alias'] !== null || base.count !== 2) throw new Error('incorrect merge');
            const deleted = SomeRequest.merge(restored, {title: null${if (frontend == "collisions") ", constructor: undefined" else ""}});
            if (deleted.title !== undefined) throw new Error('deleted default restored');
            const nonNull = ordinary.parse({count: 3, 'required-nullable': 'valid', 'required-alias': 'valid'});
            if (JSON.stringify(schema.encode(SomeRequest.patch(nonNull))) !== JSON.stringify(schema.encode(SomeRequestPatch.fromModel(nonNull)))) throw new Error('factory mismatch');
            failed = false;
            try { SomeRequest.patch(base, nonNull); } catch { failed = true; }
            if (!failed) throw new Error('required null assignment accepted');
            const nestedBase = ordinary.parse({"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Original","note":"Remove","child":{"name":"Child","note":"Keep"}},"numbers":[1,null,2],"labels":{"keep":"yes","remove":"old"}});
            const nestedUpdated = ordinary.parse({"count":2,"required-nullable":null,"required-alias":null,"details":{"name":"Changed","note":"Remove","child":{"name":"Updated child","note":"Keep"}},"numbers":[3,null],"labels":{"keep":"yes","remove":"old"}});
            const nestedDiff = SomeRequest.patch(nestedUpdated, nestedBase);
            if (JSON.stringify(schema.encode(nestedDiff)) !== JSON.stringify({"details":{"name":"Changed","child":{"name":"Updated child"}},"numbers":[3,null]})) throw new Error('incorrect nested difference');
            const nestedMerged = SomeRequest.merge(nestedBase, nestedDiff);
            if (nestedMerged.details?.name !== 'Changed' || nestedMerged.details?.child?.name !== 'Updated child' || nestedBase.details?.child?.name !== 'Child' || nestedMerged.numbers?.length !== 2 || nestedMerged.numbers[1] !== null) throw new Error('incorrect nested merge');
            const nestedDeleted = SomeRequest.merge(nestedBase, schema.parse({"details":{"note":null},"labels":{"remove":null}}));
            if (nestedDeleted.details?.note !== undefined || nestedDeleted.labels?.remove !== undefined || nestedBase.details?.note !== 'Remove') throw new Error('incorrect nested deletion');
            failed = false;
            try { SomeRequest.merge(base, schema.parse({"details":{"note":"new"}})); } catch { failed = true; }
            if (!failed) throw new Error('invalid merged child accepted');
            ${if (frontend == "collisions") {
              """
            const special = ordinary.parse({...base, ...JSON.parse('{"__proto__":"before","constructor":"before"}')});
            const specialPatch = schema.parse(JSON.parse('{"__proto__":"after","constructor":"after"}'));
            const specialMerged = ordinary.encode(SomeRequest.merge(special, specialPatch)) as Record<string, unknown>;
            if (!Object.hasOwn(specialMerged, '__proto__') || specialMerged['__proto__'] !== 'after' || Reflect.get(specialMerged, 'constructor') !== 'after') throw new Error('prototype member lost during merge');
            """
            } else {
              ""
            }}

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
