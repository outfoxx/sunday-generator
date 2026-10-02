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

import io.outfoxx.sunday.generator.tools.directionalToleranceApi
import io.outfoxx.sunday.generator.tools.objectUnionValidationApi
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
class TypeScriptDirectionalToleranceTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `union common constraints retain independent payload schemas`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      objectUnionValidationApi(frontend, directory, discriminated = true, commonMaximum = 5),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("CommonCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ChoiceSchema} from './choice.js';
            import {LargeSchema} from './large.js';
            import {HolderSchema} from './holder.js';
            const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
            const payload = runtime.resolveSchema(LargeSchema).parse({kind:'large',value:9});
            const union = runtime.resolveSchema(ChoiceSchema);
            union.parse({kind:'large',value:2});
            if (union.safeEncode(payload).success || union.safeParse({kind:'large',value:9}).success) {
              throw new Error('Union common constraint was lost: ' + JSON.stringify({encode:union.safeEncode(payload),parse:union.safeParse({kind:'large',value:9})}));
            }
            if (runtime.resolveSchema(HolderSchema).safeEncode({choice:payload}).success) {
              throw new Error('Nested union common constraint was lost');
            }
            """.trimIndent(),
          ),
        ).build()
    val passed =
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("CommonCheck", "!common-check") to check),
        "common-check",
        esm = true,
      )
    assertTrue(passed)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `string discriminator fallbacks retain unknown payloads and reject malformed known branches`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      directionalToleranceApi(frontend, directory),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ToleranceCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding, isUnknownVariant} from '@outfoxx/sunday';
            import {EventSchema} from './event.js';
            import {State, StateSchema} from './state.js';
            import {OpenStateSchema} from './open-state.js';
            import {ItemSchema} from './item.js';
            const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(EventSchema);
            const request = runtime.forMode('request');
            const disguisedState = State.Unknown('active');
            if (disguisedState === State.Active || request.resolveSchema(StateSchema).safeEncode(disguisedState).success) {
              throw new Error('explicit fallback lost its identity');
            }
            const state = runtime.resolveSchema(StateSchema).parse('future');
            if (request.resolveSchema(StateSchema).safeEncode(state).success) throw new Error('unknown request enum accepted');
            if (!runtime.resolveSchema(StateSchema).safeEncode(state).success) throw new Error('unknown response enum rejected');
            const openState = runtime.resolveSchema(OpenStateSchema).parse('future');
            request.resolveSchema(OpenStateSchema).encode(openState);
            const item = runtime.resolveSchema(ItemSchema).parse({state:'active',states:['active','future']});
            if (request.resolveSchema(ItemSchema).safeEncode(item).success) throw new Error('nested unknown accepted');
            item.states = [State.Active];
            request.resolveSchema(ItemSchema).encode(item);
            item.states.push(state);
            if (request.resolveSchema(ItemSchema).safeEncode(item).success) throw new Error('mutation missed');
            item.states = [State.Active];
            item.next = item;
            const cycle = request.resolveSchema(ItemSchema).safeEncode(item);
            if (cycle.success || cycle.error.issues[0].code !== 'custom' ||
                !cycle.error.issues[0].path.includes('next')) throw new Error('cycle did not produce native diagnostics');
            item.next = undefined;
            request.resolveSchema(ItemSchema).encode(item);
            const raw = {kind:'future',detail:{attempt:2}};
            const unknown = schema.parse(raw);
            if (!isUnknownVariant(unknown)) throw new Error('expected fallback');
            if (request.resolveSchema(EventSchema).safeEncode(unknown).success) throw new Error('unknown request union accepted');
            const disguised = {...unknown, kind:'created', count:1, rawBody:{kind:'created',count:1}};
            if (request.resolveSchema(EventSchema).safeEncode(disguised).success) throw new Error('fallback escaped through known branch');
            if (JSON.stringify(schema.encode(unknown)) !== JSON.stringify(raw)) throw new Error('lost payload');
            schema.parse({kind:'created',count:1});
            for (const invalid of [{kind:'x'},{kind:'FUTURE'},{kind:'created'},{kind:null},{}]) {
              if (schema.safeParse(invalid).success) throw new Error('invalid branch accepted');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ToleranceCheck", "!tolerance-check") to check),
        "tolerance-check",
        esm = true,
      ),
    )
  }
}
