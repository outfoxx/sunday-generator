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

import io.outfoxx.sunday.generator.tools.inheritedAdditionalPropertiesModels
import io.outfoxx.sunday.generator.tools.openModelFieldsApi
import io.outfoxx.sunday.generator.tools.openModelWire
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
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
@Tag("models")
class TypeScriptOpenModelFieldsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  @Tag("validation")
  fun `inherited additional constraints survive encoding and mutation`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val source = openModelFieldsApi(frontend, directory)
    val registry = TypeScriptTypeRegistry(emptySet(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      source.copy(models = source.models + inheritedAdditionalPropertiesModels()),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("InheritedCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
                  import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
                  import {DynamicChildSchema} from './dynamic-child.js';
            import {DynamicLeafSchema} from './dynamic-leaf.js';
            import {DynamicHolderSchema} from './dynamic-holder.js';
                  const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
                  for (const schema of [runtime.resolveSchema(DynamicChildSchema), runtime.resolveSchema(DynamicLeafSchema)]) {
                    const value = schema.parse({id: 'one', extra: [1,2]});
                    schema.encode(value);
                    for (const invalid of [[1], [1,2,3,4]]) {
                if (schema.safeParse({id: 'one', extra: invalid}).success) throw new Error('Inherited constraint ignored');
                if (runtime.resolveSchema(DynamicHolderSchema).safeParse({payload: {id: 'one', extra: invalid}}).success) throw new Error('Inherited nested model treated as free-form');
                      const prototype = Object.fromEntries([['id', 'one'], ['__proto__', invalid]]);
                      if (schema.safeParse(prototype).success) throw new Error('Inherited prototype property constraint ignored');
                (value as any).extra = invalid;
                      if (schema.safeEncode(value).success) throw new Error('Mutated inherited extension accepted');
                    }
                  }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() +
          (TypeName.namedImport("InheritedCheck", "!inherited-check") to check),
        "inherited-check",
      ),
    )
  }

  @ParameterizedTest
  @CsvSource(
    "raml,true",
    "openapi,true",
    "asyncapi,true",
    "composed,true",
    "raml,false",
    "openapi,false",
    "asyncapi,false",
    "composed,false",
  )
  fun `open model fields round trip by default and can be discarded`(
    frontend: String,
    preserve: Boolean,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    val options =
      if (preserve) {
        typeScriptSundayTestOptions
      } else {
        TypeScriptSundayOptions(
          "http://example.com/",
          listOf("application/json"),
          "API",
          preserveUnknownFields = false,
        )
      }
    TypeScriptSundayIrGenerator(openModelFieldsApi(frontend, directory), registry, options).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("OpenFieldsCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {NoticeSchema} from './notice.js';
            import {ExtendedRecordSchema} from './extended-record.js';
            import {ClosedChildSchema} from './closed-child.js';
            import {TypedRecordSchema} from './typed-record.js';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const input = $openModelWire;
            const schema = runtime.resolveSchema(NoticeSchema);
            const output = schema.encode(schema.parse(input)) as Record<string, any>;
            if (output.data.additionalProperties !== 'declared') throw new Error('declared storage-name collision');
            if ($preserve) {
              if (JSON.stringify(output) !== JSON.stringify(input)) throw new Error('lost fields: ' + JSON.stringify(output));
            } else if ('future' in output || 'future' in output.data || 'nested' in output.data) throw new Error('retained disabled fields');
            const inheritedSchema = runtime.resolveSchema(ExtendedRecordSchema);
            const inherited = inheritedSchema.encode(inheritedSchema.parse({id: 'one', kind: 'extended', future: null}));
            if (('future' in inherited) !== $preserve) throw new Error('inherited preservation');
            if (runtime.resolveSchema(ClosedChildSchema).safeParse({id: 'one', name: 'name', future: 1}).success) throw new Error('closed child accepted extra');
            if (runtime.resolveSchema(TypedRecordSchema).safeParse({id: 'one', future: 'wrong'}).success) throw new Error('typed extra was not validated');
            runtime.resolveSchema(TypedRecordSchema).parse({id: 'one', future: 2});
            if (${frontend != "raml"} && runtime.resolveSchema(TypedRecordSchema).safeParse({id: 'one', future: 3}).success) throw new Error('additional-property const was ignored');
            // Prototype-like keys are data, and must survive without becoming object prototypes.
            const prototype = JSON.parse('{"id":"one","data":{"name":"name","additionalProperties":"declared"},"__proto__":{"polluted":true}}');
            const prototypeOutput = schema.encode(schema.parse(prototype));
            if (Object.hasOwn(prototypeOutput, '__proto__') !== $preserve) throw new Error('prototype-like key lost');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("OpenFieldsCheck", "!open-fields-check") to check),
        "open-fields-check",
      ),
    )
  }
}
