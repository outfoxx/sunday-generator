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

import io.outfoxx.sunday.generator.tools.closedModelsApi
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
class TypeScriptClosedModelsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `closed objects reject unknown fields`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(closedModelsApi(frontend, directory), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ClosedCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ClosedRecordSchema} from './closed-record';
            import {ClosedChildSchema} from './closed-child';
            import {EmptyClosedSchema} from './empty-closed';
            import {OpenRecordSchema} from './open-record';
            ${if (frontend == "composed") "import {EventRecordSchema} from './event-record';" else ""}
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const closed = runtime.resolveSchema(ClosedRecordSchema);
            closed.parse({'display-name': 'valid', choice: {kind: 'cat', name: 'cat'}});
            if (closed.safeParse({'display-name': 'valid', choice: {kind: 'cat', name: 'cat', extra: 1}}).success) {
              throw new Error('polymorphic closed model accepted unknown property');
            }
            for (const [schema, valid] of [
              [runtime.resolveSchema(ClosedRecordSchema), {'display-name': 'valid'}],
              [runtime.resolveSchema(ClosedChildSchema), {name: 'valid', count: 1}],
              [runtime.resolveSchema(EmptyClosedSchema), {}],
              ${if (frontend == "composed") "[runtime.resolveSchema(EventRecordSchema), {}]," else ""}
            ] as const) {
              schema.parse(valid);
              for (const extra of [1, null, {}, []]) {
                if (schema.safeParse({...valid, extra}).success) throw new Error('unknown property accepted');
              }
            }
            if (runtime.resolveSchema(ClosedRecordSchema).safeParse({'display-name': 'valid', empty: {extra: 1}}).success) {
              throw new Error('empty closed model accepted unknown property');
            }
            runtime.resolveSchema(OpenRecordSchema).parse({name: 'valid', extra: 1});
            if (runtime.resolveSchema(ClosedRecordSchema).safeParse({'display-name': 'valid', nested: {'display-name': 'nested', extra: 1}}).success) {
              throw new Error('nested unknown property accepted');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ClosedCheck", "!closed-check") to check),
        "closed-check",
      ),
    )
  }
}
