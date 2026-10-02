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

import io.outfoxx.sunday.generator.Tolerance
import io.outfoxx.sunday.generator.tools.parameterNameCollisionApi
import io.outfoxx.sunday.generator.tools.parameterToleranceApi
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
class TypeScriptParameterToleranceTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `validation helper names do not shadow operation parameters`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      parameterNameCollisionApi(frontend, directory, listOf("runtime", "runtime_", "values")),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("CollisionCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {createParametersAPI} from './parameters-api.js';
            import {State} from './state.js';
            const api = createParametersAPI(new FetchTransport('https://example.com'));
            await api.parameters(undefined, undefined, undefined).transportRequest();
            await api.parameters(State.Active, State.Active, [State.Active]).transportRequest();
            const unknown = State.fromValue('future');
            for (const operation of [
              api.parameters(unknown, State.Active, [State.Active]),
              api.parameters(State.Active, unknown, [State.Active]),
              api.parameters(State.Active, State.Active, [unknown]),
            ]) {
              try { await operation.transportRequest(); } catch { continue; }
              throw new Error('shadowed parameter bypassed validation');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("CollisionCheck", "!collision-check") to check),
        "collision-check",
        esm = true,
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed", "openapi-all"])
  fun `typed parameters revalidate at each request boundary`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val tolerance = if (frontend.endsWith("-all")) Tolerance.All else Tolerance.Response
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      parameterToleranceApi(frontend.removeSuffix("-all"), directory),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API", defaultTolerance = tolerance),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ParameterCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {createParametersAPI} from './parameters-api.js';
            import {State} from './state.js';
            import {OpenState} from './open-state.js';
            import {DefaultState} from './default-state.js';
            const api = createParametersAPI(new FetchTransport('https://example.com'));
            const values = [State.Active];
            const operation = api.parameters(State.Active, values, undefined, OpenState.fromValue('future'), undefined);
            await operation.transportRequest();
            values.push(State.fromValue('future'));
            async function rejects(operation: ReturnType<typeof api.parameters>) {
              try { await operation.transportRequest(); } catch { return; }
              throw new Error('unknown parameter accepted');
            }
            await rejects(operation);
            await rejects(api.parameters(State.fromValue('future'), undefined, undefined, undefined, undefined));
            await rejects(api.parameters(State.Active, undefined, State.fromValue('future'), undefined, undefined));
            const defaultOperation = api.parameters(State.Active, undefined, undefined, undefined, DefaultState.fromValue('future'));
            ${if (tolerance == Tolerance.All) "await defaultOperation.transportRequest();" else "await rejects(defaultOperation);"}
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ParameterCheck", "!parameter-check") to check),
        "parameter-check",
        esm = true,
      ),
    )
  }
}
