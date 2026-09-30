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

import io.outfoxx.sunday.generator.tools.patternModelInvalid
import io.outfoxx.sunday.generator.tools.patternModelRegressions
import io.outfoxx.sunday.generator.tools.patternModelValid
import io.outfoxx.sunday.generator.tools.patternModelsApi
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
class TypeScriptPatternModelsTest {
  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `OpenAPI patterns validate keys and values`(
    composed: Boolean,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      patternModelsApi(directory, composed),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("PatternCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
              import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
              import {PatternRecordSchema} from './pattern-record';
              import {PatternInheritedSchema} from './pattern-inherited';
              import {PatternOnlySchema} from './pattern-only';
              import {PatternObjectSchema} from './pattern-object';
              import {PatternClosedInheritedSchema} from './pattern-closed-inherited';
              import {PatternFieldInheritedSchema} from './pattern-field-inherited';
              import {NestedAdditionalPatternSchema} from './nested-additional-pattern';
              import {OpenPatternSchema} from './open-pattern';
              const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
              const schema = runtime.resolveSchema(PatternRecordSchema);
              for (const valid of [${patternModelValid.joinToString()}]) schema.parse(valid);
              for (const invalid of [${patternModelInvalid.joinToString()}]) {
                if (schema.safeParse(invalid).success) throw new Error('invalid pattern value accepted: ' + JSON.stringify(invalid));
              }
              ${patternModelRegressions.entries.joinToString("\n") { (name, values) ->
              """
              for (const valid of [${values.first.joinToString()}]) runtime.resolveSchema(${name}Schema).parse(valid);
              for (const invalid of [${values.second.joinToString()}]) {
                if (runtime.resolveSchema(${name}Schema).safeParse(invalid).success) throw new Error('$name accepted: ' + JSON.stringify(invalid));
              }
              """.trimIndent()
            }}
              runtime.resolveSchema(PatternInheritedSchema).parse({'x-valid':'ok'});
              for (const invalid of [{'x-invalid':'a'}, {extra:1}]) {
                if (runtime.resolveSchema(PatternInheritedSchema).safeParse(invalid).success) throw new Error('inherited constraint ignored');
              }
              runtime.resolveSchema(OpenPatternSchema).parse({'extra':1,'x-valid':'ok'});
              if (runtime.resolveSchema(OpenPatternSchema).safeParse({'extra':'wrong'}).success) throw new Error('invalid fallback accepted');
              runtime.resolveSchema(PatternOnlySchema).parse({'x-valid':'ok'});
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("PatternCheck", "!pattern-check") to check),
        "pattern-check",
      ),
    )
  }
}
