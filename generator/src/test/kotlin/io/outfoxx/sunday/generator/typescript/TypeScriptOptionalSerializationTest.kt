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

import io.outfoxx.sunday.generator.tools.optionalSerializationApi
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TypeScriptTest
class TypeScriptOptionalSerializationTest {
  @Test
  fun `optional fields serialize according to presence and nullability`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      optionalSerializationApi(directory),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("OptionalCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {Request, RequestSchema} from './request';
            import {AliasRequestSchema} from './alias-request';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const aliasSchema = runtime.resolveSchema(AliasRequestSchema);
            const alias = {nullableAlias: null, anyValue: null};
            if (JSON.stringify(z.encode(aliasSchema, aliasSchema.parse(alias))) !== JSON.stringify(alias)) throw new Error('nullable alias changed');
            const schema = runtime.resolveSchema(RequestSchema);
            const value: Request = {name: 'test', requiredNullable: null};
            const expected = JSON.stringify(value);
            if (JSON.stringify(z.encode(schema, value)) !== expected) throw new Error('omission changed');
            for (const fields of [{text: '', number: 0, flag: false, items: []}, {text: 'main', number: 1, flag: true, items: ['item']}]) {
              const wire = {...value, optionalNullable: null, ...fields};
              const restored = JSON.parse(JSON.stringify(z.encode(schema, schema.parse(wire))));
              for (const key of Object.keys(wire)) {
                if (JSON.stringify(restored[key]) !== JSON.stringify((wire as any)[key])) throw new Error('value changed: ' + key);
              }
            }
            for (const invalid of [{}, {name: 'test'}, {...value, name: null}, {...value, text: null}]) {
              if (schema.safeParse(invalid).success) throw new Error('invalid value accepted: ' + JSON.stringify(invalid));
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("OptionalCheck", "!optional-check") to check),
        "optional-check",
      ),
    )
  }
}
