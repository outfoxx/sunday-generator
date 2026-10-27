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

import io.outfoxx.sunday.generator.tools.narrowedProblemDetailApi
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
class TypeScriptProblemEncodingTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "asyncapi", "composed"])
  fun `problem encoding omits absent detail and preserves empty strings`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      narrowedProblemDetailApi(frontend, directory),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {AccountFieldsInvalidProblem, AccountFieldsInvalidProblemSchema} from './account-fields-invalid-problem.js';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(AccountFieldsInvalidProblemSchema);
            for (const detail of [undefined, '', 'Text']) {
              const value = new AccountFieldsInvalidProblem({detail});
              const encoded = schema.encode(value) as Record<string, unknown>;
              const json = JSON.parse(JSON.stringify(encoded)) as Record<string, unknown>;
              if (Object.hasOwn(json, 'detail') !== (detail !== undefined)) throw new Error('Incorrect detail presence');
              if (json.detail !== detail) throw new Error('Incorrect detail');
              const roundtrip = schema.encode(schema.parse(json)) as Record<string, unknown>;
              if (JSON.stringify(roundtrip) !== JSON.stringify(encoded)) throw new Error('Roundtrip changed');
            }
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
