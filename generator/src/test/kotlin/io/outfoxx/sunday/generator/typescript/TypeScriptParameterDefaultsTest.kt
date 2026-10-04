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

import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
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
@Tag("requests")
class TypeScriptParameterDefaultsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `client defaults and explicit omissions reach the wire`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      parameterDefaultsApi(frontend, directory).withOptionalParameterTemplates(),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("DefaultsCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {ParametersAPI, createParametersAPI} from './parameters-api.js';
            const api = createParametersAPI(new FetchTransport(ParametersAPI.baseURL(null)));
            await api.scalarDefaults().transportRequest();
            const omitted = await api.probe(null, null, null, undefined, null, null, null).transportRequest();
            if (!(omitted instanceof Request)) throw new Error("Expected a Fetch request");
            if (new URL(omitted.url).pathname !== '/probe' || new URL(omitted.url).searchParams.size !== 0 || omitted.headers.has('headerValue')) {
              throw new Error('explicit null did not omit optional wire metadata: ' + omitted.url);
            }
            const emptyPath = await api.probe('', null, null, undefined, null, null, null).transportRequest();
            if (!(emptyPath instanceof Request) || new URL(emptyPath.url).pathname !== '/probe/') {
              throw new Error('an empty path parameter must preserve its slash');
            }
            const defaults = await api.probe(undefined, undefined, null, undefined, undefined, undefined, undefined).transportRequest();
            if (!(defaults instanceof Request)) throw new Error("Expected a Fetch request");
            const noArguments = await api.probe().transportRequest();
            if (!(noArguments instanceof Request) || noArguments.url !== defaults.url) {
              throw new Error('omitted arguments did not use method defaults');
            }
            const url = new URL(defaults.url);
            if (url.pathname !== '/probe/fallback' || url.searchParams.get('queryValue') !== '5' ||
                url.searchParams.get('zeroValue') !== '0' || url.searchParams.get('falseValue') !== 'false' ||
                defaults.headers.get('headerValue') !== 'header') {
              throw new Error('declared defaults were lost: ' + url);
            }
            const explicit = await api.probe('explicit', 7, null, undefined, 0, false, 'custom').transportRequest();
            if (!(explicit instanceof Request)) throw new Error("Expected a Fetch request");
            if (new URL(explicit.url).pathname !== '/probe/explicit' || explicit.headers.get('headerValue') !== 'custom') {
              throw new Error('explicit parameter values were replaced');
            }
            const defaultAPI = createParametersAPI(new FetchTransport(ParametersAPI.baseURL()));
            const defaultBase = await defaultAPI.probe(null, null, null, undefined, null, null, null).transportRequest();
            if (!(defaultBase instanceof Request) || new URL(defaultBase.url).pathname !== '/example.com/probe') {
              throw new Error('omitted base URI argument did not use its method default');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("DefaultsCheck", "!defaults-check") to check),
        "defaults-check",
        esm = true,
      ),
    )
  }
}
