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

import io.outfoxx.sunday.generator.tools.namedProblemReferenceApi
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
@Tag("responses")
class TypeScriptNamedProblemReferenceTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `Problem reference codecs retain every alias constraint in both directions`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(namedProblemReferenceApi(frontend, directory), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {HttpProblem, HttpProblemSchema} from './http-problem.js';
            import {OrdinaryRecordSchema} from './ordinary-record.js';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(HttpProblemSchema);
            const instance = 'urn:example-example';
            for (const value of [schema.parse({instance}), schema.decode({instance})]) {
              if (value.instance?.toString() !== instance) throw new Error('Reference changed');
              const output = schema.encode(value) as Record<string, unknown>;
              if (output.instance !== instance) throw new Error('Reference did not round trip');
            }
            for (const invalid of ['xxx:example-example', 'urn:otherexample-example',
              'urn:example-example-tail', 'urn:example', 'urn:example-long-long-long-example']) {
              if (schema.safeParse({instance: invalid}).success) throw new Error('Constraint lost: ' + invalid);
              let rejected = false;
              try { new HttpProblem({instance: invalid}); } catch { rejected = true; }
              if (!rejected) throw new Error('Constructor constraint lost: ' + invalid);
              const changed = Object.assign(schema.parse({instance}), {instance: invalid});
              if (schema.safeEncode(changed).success) throw new Error('Reverse constraint lost: ' + invalid);
            }
            for (const value of [undefined, null, 42, true, [], {}]) {
              const decoded = schema.parse({instance: value});
              if (decoded.instance !== undefined) throw new Error('Wrong category did not become absent');
              if ('instance' in JSON.parse(JSON.stringify(schema.encode(decoded))))
                throw new Error('Absent instance was encoded');
            }
            const ordinary = runtime.resolveSchema(OrdinaryRecordSchema).parse({uri: 'https://example.com'});
            if (!ordinary.uri.toString().startsWith('https://example.com'))
              throw new Error('Ordinary URI alias changed');
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
