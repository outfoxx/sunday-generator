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
import io.outfoxx.sunday.generator.tools.rfc9457ProblemOneOfCases
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
@Tag("responses")
@Tag("validation")
class TypeScriptProblemOneOfTest {
  @Test
  fun `problem oneOf accepts exactly one valid alternative`(
    @ResourceUri("openapi/ir/problem-oneof.yaml") source: URI,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      GeneratedApiIrExporter().export(source),
      registry,
      TypeScriptSundayOptions("https://example.org/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val consumer =
      ModuleSpec
        .builder("Check", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {z} from 'zod';
            import {ProblemsSchema} from './problems';
            import {ReversedProblemsSchema} from './reversed-problems';
            import {TaggedProblemsSchema} from './tagged-problems';
            import {AmbiguousProblemsSchema} from './ambiguous-problems';
            import {ConstrainedProblemsSchema} from './constrained-problems';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
              numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schemas: Record<string, z.ZodType> = {
              Problems: runtime.resolveSchema(ProblemsSchema),
              ReversedProblems: runtime.resolveSchema(ReversedProblemsSchema),
              TaggedProblems: runtime.resolveSchema(TaggedProblemsSchema),
              AmbiguousProblems: runtime.resolveSchema(AmbiguousProblemsSchema),
              ConstrainedProblems: runtime.resolveSchema(ConstrainedProblemsSchema),
            };
            const failures: string[] = [];
            function normalized(value: unknown): unknown {
              return value && typeof value === 'object' && !Array.isArray(value)
                ? Object.fromEntries(Object.entries(value).sort().map(([key, item]) => [key, normalized(item)])) : value;
            }
            for (const test of $rfc9457ProblemOneOfCases) {
              const schema = schemas[test.union];
              const result = schema.safeParse(test.wire);
              if (!result.success) {
                if (test.expected !== null) failures.push('Rejected valid case: ' + JSON.stringify(test));
              } else if (test.expected === null) {
                failures.push('Accepted invalid case: ' + JSON.stringify(test));
              } else {
                if ((result.data as object).constructor.name !== test.expected) throw new Error(JSON.stringify(test));
                for (const member of ['type', 'title', 'status', 'detail', 'instance']) {
                  const missing: Record<string, unknown> = {...test.wire}; delete missing[member];
                  const outcome = (raw: unknown) => {
                    const parsed = schema.safeParse(raw);
                    return parsed.success ? normalized(schema.encode(parsed.data)) : null;
                  };
                  for (const wrong of member === 'status' ? [null, true, '404', [], {}] : [null, true, 42, [], {}]) {
                    if (JSON.stringify(outcome({...missing, [member]: wrong})) !== JSON.stringify(outcome(missing))) {
                      throw new Error(JSON.stringify({member, wrong, test}));
                    }
                  }
                }

                if (JSON.stringify(normalized(schema.encode(result.data))) !== JSON.stringify(normalized(test.wire))) {
                  throw new Error('Round trip failed: ' + JSON.stringify(test));
                }
              }
            }
            if (failures.length) throw new Error(failures.join('\n'));
            """.trimIndent(),
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
