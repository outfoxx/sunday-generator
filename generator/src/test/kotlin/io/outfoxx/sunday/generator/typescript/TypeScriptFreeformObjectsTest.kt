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

import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@Tag("models")
class TypeScriptFreeformObjectsTest {
  @Test
  fun `inline and named freeform objects round trip`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/freeform-objects.yaml") uri: URI,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(uri), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("FreeformCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {DocumentSchema} from './document.js';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(DocumentSchema);
            const value = {name: 'example', enabled: true, count: 3, fraction: 1.5, nullValue: null, nested: {value: null}, items: [1, 'two', null]};
            for (const field of ['properties', 'named', 'items']) {
              const original = {[field]: field === 'items' ? [value, {}] : value};
              const decoded = schema.parse(original);
              const properties: Record<string, unknown> | undefined = decoded.properties;
              const named: Record<string, unknown> | undefined = decoded.named;
              const result = schema.encode(decoded);
              if (JSON.stringify(result) !== JSON.stringify(original)) throw new Error('Object changed');
              for (const invalid of ['{}', true, 3]) {
                if (schema.safeParse({[field]: field === 'items' ? [invalid] : invalid}).success) throw new Error('Non-object accepted');
              }
            }
            for (const original of [{}, {properties: {}, named: {}, items: []}]) {
              if (JSON.stringify(schema.encode(schema.parse(original))) !== JSON.stringify(original)) throw new Error('Presence changed');
            }
            for (const field of ['properties', 'named', 'items']) {
              if (schema.safeParse({[field]: null}).success) throw new Error('Non-nullable field accepted null');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("FreeformCheck", "!freeform-check") to check),
        "freeform-check",
      ),
    )
  }
}
