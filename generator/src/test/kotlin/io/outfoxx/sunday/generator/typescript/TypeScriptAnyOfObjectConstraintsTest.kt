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

import io.outfoxx.sunday.generator.tools.anyOfObjectConstraintsApi
import io.outfoxx.sunday.generator.tools.anyOfObjectInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfObjectValidWires
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

/** Compile-backed runtime coverage for inclusive object constraints from issue #277. */
@TypeScriptTest
@Tag("models")
@Tag("validation")
class TypeScriptAnyOfObjectConstraintsTest {
  @ParameterizedTest
  @ValueSource(strings = ["shared", "inline", "named", "shared-composed", "nested"])
  fun `object anyOf preserves inclusive requirements and round trips`(
    shape: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {

    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      anyOfObjectConstraintsApi(shape, directory),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("AnyOfCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {CheckoutRequestSchema} from './checkout-request.js';
            const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(CheckoutRequestSchema);
            const inputs: any[] = $anyOfObjectValidWires;
            for (const raw of inputs) {
              const value = schema.parse(raw);
              const encoded = schema.encode(value) as any;
              if (Object.keys(encoded).length !== Object.keys(raw).length ||
                Object.keys(raw).some(key => JSON.stringify(encoded[key]) !== JSON.stringify(raw[key]))) {
                throw new Error('Lost wire fields: ' + JSON.stringify(encoded));
              }
            }
            for (const invalid of $anyOfObjectInvalidWires) {
              if (schema.safeParse(invalid).success) throw new Error('Invalid union accepted: ' + JSON.stringify(invalid));
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("AnyOfCheck", "!anyof-check") to check),
        "anyof-check",
        esm = true,
      ),
    )
  }
}
