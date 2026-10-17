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

import io.outfoxx.sunday.generator.tools.constantDiscriminatorsApi
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
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Path

@TypeScriptTest
@Tag("models")
class TypeScriptConstantDiscriminatorsTest {
  @ParameterizedTest
  @CsvSource(
    "openapi,kind",
    "asyncapi,kind",
    "composed,kind",
    "openapi,__proto__",
    "asyncapi,__proto__",
    "composed,__proto__",
  )
  fun `constant discriminators narrow and validate`(
    frontend: String,
    discriminator: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val extraField = if (discriminator == "__proto__") "extra" else "__proto__"
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      constantDiscriminatorsApi(directory, frontend, discriminator),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {Extent, ExtentSchema} from './extent';
            import {BeatExtentSchema} from './beat-extent';
            function narrow(value: Extent): number | undefined {
              switch (value.kind) {
                case 'beat': return value.beats;
                case 'time': return value.seconds;
              }
            }
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(ExtentSchema);
            if (narrow(schema.parse({['kind']: 'beat', beats: 2})) !== 2) throw new Error('wrong branch');
            if (schema.safeParse({['kind']: 'x'}).success) throw new Error('unknown tag accepted');
            const raw = JSON.parse('{"kind":"beat","beats":2,"$extraField":{"kept":true}}');
            const encoded = schema.encode(schema.parse(raw));
            if (encoded.kind !== 'beat' || !Object.hasOwn(encoded, '$extraField')) throw new Error('codec lost wire data');
            if (runtime.resolveSchema(BeatExtentSchema).safeParse({['kind']: 'x'}).success) throw new Error('invalid constant accepted');
            """.trimIndent().replace("kind", discriminator),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("Check", "!check") to consumer),
        "check",
      ),
    )
  }
}
