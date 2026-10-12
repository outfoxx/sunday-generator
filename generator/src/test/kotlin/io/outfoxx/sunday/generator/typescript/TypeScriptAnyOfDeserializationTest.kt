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

import io.outfoxx.sunday.generator.tools.anyOfDeserializationApi
import io.outfoxx.sunday.generator.tools.anyOfInvalidWires
import io.outfoxx.sunday.generator.tools.anyOfValidWires
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
class TypeScriptAnyOfDeserializationTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `anyOf tries complete alternatives and preserves open fields`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {

    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      anyOfDeserializationApi(frontend, directory),
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
            import {UserResponseSchema} from './user-response.js';
            import {OffsetDateTime} from '@js-joda/core';
            const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(UserResponseSchema);
            const inputs: any[] = $anyOfValidWires;
            for (const [index, raw] of inputs.entries()) {
              const value = schema.parse(raw);
              if ((value.createdAt instanceof OffsetDateTime) !== (index === 1)) throw new Error('Wrong alternative: ' + JSON.stringify(raw));
              const encoded = schema.encode(value) as any;
              if (index === 1) encoded.createdAt = new Date(encoded.createdAt).toISOString().replace('.000Z', 'Z');
              if (Object.keys(encoded).length !== Object.keys(raw).length ||
                Object.keys(raw).some(key => JSON.stringify(encoded[key]) !== JSON.stringify(raw[key]))) {
                throw new Error('Lost wire fields: ' + JSON.stringify(encoded));
              }
            }
            for (const invalid of $anyOfInvalidWires) {
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
