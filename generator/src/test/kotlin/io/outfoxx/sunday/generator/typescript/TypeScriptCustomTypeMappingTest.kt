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

import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TypeScriptTest
class TypeScriptCustomTypeMappingTest {
  @Test
  fun `custom TypeScript scalars use imported runtime codecs`(compiler: TypeScriptCompiler) {
    val api =
      OpenApiToGeneratedApi().convert(
        Path.of("src/test/resources/openapi/ir/custom-types/mapped-api.yaml").toUri(),
      )
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      api,
      registry,
      TypeScriptSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val domain =
      ModuleSpec
        .builder("domain", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            "%L",
            """
            import {z} from 'zod';
            export class DomainIdentifier { constructor(readonly value: string) {} }
            export const DomainIdentifierSchema = z.codec(z.string(), z.instanceof(DomainIdentifier), {
              decode: (value) => new DomainIdentifier(value), encode: (value) => value.value,
            });
            """.trimIndent(),
          ),
        ).build()
    val check =
      ModuleSpec
        .builder("mapping-check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            "%L",
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {FetchTransport} from '@outfoxx/sunday';
            import {createAPI} from './api.js';
            import {RecordSchema} from './record.js';
            import {DomainIdentifier} from './domain.js';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(RecordSchema);
            const value = schema.parse({id: 'id:abc'});
            const id: DomainIdentifier = value.id;
            if (!(id instanceof DomainIdentifier)) throw new Error('Custom type was lost');
            const wire = z.encode(schema, value) as {id: string};
            if (wire.id !== 'id:abc') throw new Error('Custom encoding was lost');
            if (schema.safeParse({id: 'invalid'}).success) throw new Error('Wire constraints ignored');
            const api = createAPI(new FetchTransport('https://example.com/'));
            const request = api.identify(new DomainIdentifier('id:abc'), new DomainIdentifier('id:def')).spec.request;
            if (request.queryParameters?.other !== 'id:def') throw new Error('Custom query encoding was lost');
            let rejected = false;
            try { api.identify(new DomainIdentifier('invalid'), new DomainIdentifier('id:def')); }
            catch { rejected = true; }
            if (!rejected) throw new Error('Custom parameter constraints ignored');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() +
          mapOf(
            TypeName.namedImport("DomainIdentifier", "!domain") to domain,
            TypeName.namedImport("MappingCheck", "!mapping-check") to check,
          ),
        "mapping-check",
        esm = true,
      ),
    )
  }

  @Test
  fun `Kotlin mapping metadata leaves TypeScript output unchanged`(compiler: TypeScriptCompiler) {
    val api = OpenApiToGeneratedApi().convert(Path.of("src/test/resources/openapi/ir/custom-types/api.yaml").toUri())
    val sources =
      listOf(api.models, api.models.map { it.copy(targets = emptyMap()) }).map { models ->
        val registry = TypeScriptTypeRegistry(setOf())
        TypeScriptSundayIrGenerator(
          api.copy(models = models),
          registry,
          TypeScriptSundayOptions("https://example.com/", listOf("application/json"), "API"),
        ).generateServiceTypes()
        assertTrue(compileTypes(compiler, registry.buildTypes()))
        CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "record.ts")
      }
    assertEquals(sources[0], sources[1])
  }
}
