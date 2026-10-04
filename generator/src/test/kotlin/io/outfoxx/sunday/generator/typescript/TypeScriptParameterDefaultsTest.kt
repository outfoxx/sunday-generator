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

import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.tools.parameterDefaultsApi
import io.outfoxx.sunday.generator.tools.withOptionalParameterTemplates
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
@Tag("requests")
class TypeScriptParameterDefaultsTest {
  @Test
  fun `structured defaults preserve typed elements keys and fresh nested values`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val source = parameterDefaultsApi("openapi", directory, collections = true)
    val stateList =
      GeneratedModel("States", GeneratedModel.Kind.ARRAY, aliases = listOf(GeneratedTypeRef.named("State")))
    val dateList =
      GeneratedModel(
        "Dates",
        GeneratedModel.Kind.ARRAY,
        aliases = listOf(GeneratedTypeRef.scalar("string", format = "date")),
      )
    val nested = GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(GeneratedTypeRef.scalar("any")))
    val parameters =
      listOf(
        GeneratedParameter(
          "states",
          GeneratedParameter.Location.QUERY,
          GeneratedTypeRef.named("States"),
          defaultValue = listOf("active"),
        ),
        GeneratedParameter(
          "dates",
          GeneratedParameter.Location.QUERY,
          GeneratedTypeRef.named("Dates"),
          defaultValue = listOf("2026-10-03"),
        ),
        GeneratedParameter(
          "nested",
          GeneratedParameter.Location.QUERY,
          nested,
          defaultValue =
            mapOf(
              "__proto__" to listOf(null, false, 0, "", mapOf("wire-name" to "value")),
            ),
        ),
      )
    val api =
      source.copy(
        models = source.models + stateList + dateList,
        services =
          source.services.map { service ->
            service.copy(
              operations =
                service.operations.filter { it.id == "collections" }.map {
                  it.copy(
                    parameters = parameters,
                  )
                },
            )
          },
      )
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      api,
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("StructuredCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {LocalDate} from '@js-joda/core';
            import {ParametersAPI, createParametersAPI} from './parameters-api.js';
            import {State} from './state.js';
            const api = createParametersAPI(new FetchTransport(ParametersAPI.baseURL()));
            const first = api.collections().spec.request.queryParameters!;
            if ((first.states as State[])[0] !== State.Active) throw new Error('Enum default was not constructed');
            const date = (first.dates as LocalDate[])[0];
            if (!(date instanceof LocalDate) || date.toString() !== '2026-10-03') throw new Error('Formatted default was not constructed');
            const nested = first.nested as Record<string, unknown[]>;
            const expected = '{"__proto__":[null,false,0,"",{"wire-name":"value"}]}';
            if (!Object.hasOwn(nested, '__proto__') || JSON.stringify(nested) !== expected) throw new Error('Nested values or wire keys changed');
            nested.__proto__.push('mutated');
            if (JSON.stringify(api.collections().spec.request.queryParameters!.nested) !== expected) throw new Error('Nested default was shared');
            const explicit = {values: ['explicit']};
            if (api.collections(null, null, explicit).spec.request.queryParameters!.nested !== explicit) throw new Error('Explicit object was copied');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("StructuredCheck", "!structured-check") to check),
        "structured-check",
        esm = true,
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `client defaults and explicit omissions reach the wire`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      parameterDefaultsApi(frontend, directory, collections = true).withOptionalParameterTemplates(),
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
            const collections = await api.collections().transportRequest();
            if (!(collections instanceof Request)) throw new Error('Expected a Fetch request');
            const collectionURL = new URL(collections.url);
            if (JSON.stringify(collectionURL.searchParams.getAll('tags')) !== '["a","b"]' ||
                collectionURL.searchParams.has('empty') || collectionURL.searchParams.get('counts[a]') !== '0' ||
                collectionURL.searchParams.get('counts[b]') !== '2') {
              throw new Error('Collection defaults were not serialized as values: ' + collectionURL);
            }
            const absentCollections = await api.collections(null, null, null).transportRequest();
            if (!(absentCollections instanceof Request) || new URL(absentCollections.url).searchParams.size !== 0) {
              throw new Error('Explicit null collection arguments were defaulted');
            }
            const explicitCollections = await api.collections([''], {zero: 0}, ['x']).transportRequest();
            if (!(explicitCollections instanceof Request)) throw new Error('Expected a Fetch request');
            const explicitCollectionURL = new URL(explicitCollections.url);
            if (explicitCollectionURL.searchParams.get('tags') !== '' || explicitCollectionURL.searchParams.get('empty') !== 'x' ||
                explicitCollectionURL.searchParams.get('counts[zero]') !== '0') throw new Error('Explicit collections were replaced');
            const firstCollections = api.collections().spec.request.queryParameters!;
            (firstCollections.tags as string[]).push('mutated');
            (firstCollections.counts as Record<string, number>).a = 99;
            const nextCollections = api.collections().spec.request.queryParameters!;
            if (JSON.stringify(nextCollections.tags) !== '["a","b"]' || JSON.stringify(nextCollections.counts) !== '{"a":0,"b":2}') {
              throw new Error('Collection defaults were shared between invocations');
            }
            await api.scalarDefaults().transportRequest();
            const formatted = await api.formatted().transportRequest();
            if (!(formatted instanceof Request)) throw new Error("Expected a Fetch request");
            const formattedURL = new URL(formatted.url);
            for (const [name, value] of Object.entries({date: '[2026,10,3]', time: '[12,34,56,125000000]',
                localDateTime: '[2026,10,3,12,34,56,125000000]', dateTime: '1791023696.125',
                uuid: '123e4567-e89b-12d3-a456-426614174000'})) {
              if (formattedURL.searchParams.get(name) !== value) throw new Error('Incorrect formatted default: ' + name + ': ' + formattedURL);
            }
            const absentFormats = await api.formatted(null, null, null, null, null).transportRequest();
            if (!(absentFormats instanceof Request)) throw new Error("Expected a Fetch request");
            if (new URL(absentFormats.url).searchParams.size !== 0) throw new Error('null formatted parameters were defaulted');
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
