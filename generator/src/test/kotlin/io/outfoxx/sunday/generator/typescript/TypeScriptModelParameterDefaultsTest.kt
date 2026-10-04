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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
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
import kotlin.io.path.writeText

@TypeScriptTest
@Tag("requests")
class TypeScriptModelParameterDefaultsTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `model defaults decode nested wire values without changing explicit arguments`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      modelDefaultsApi(frontend, directory),
      registry,
      TypeScriptSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ModelDefaultsCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {LocalDate} from '@js-joda/core';
            import {z} from 'zod';
            import {createParametersAPI} from './parameters-api.js';
            import {Filter} from './filter.js';
            import {State} from './state.js';
            import {isIdentifier} from './identifier.js';
            const api = createParametersAPI(new FetchTransport('https://example.com/'));
            function check(value: Filter): void {
              if (!(value.child.date instanceof LocalDate) || value.child.date.toString() !== '2026-10-04') {
                throw new Error('Nested date default was not decoded');
              }
              if (value.state !== State.Active || !isIdentifier(value.id)) throw new Error('Typed scalar default was lost');
              if (value.uri.toString() !== 'https://example.com/default') throw new Error('URI default changed');
              ${if (frontend == "raml") "" else "if (!(value.uri instanceof URL)) throw new Error('URI default was not decoded');"}
              if (JSON.stringify(value['wire-labels']) !== '["initial"]' ||
                  value.nullable !== ${if (frontend == "raml") "'present'" else "null"} || value.absent !== undefined) {
                throw new Error('Wire names, null, or absence changed');
              }
            }
            const first = api.defaults().spec.request.queryParameters!;
            const filter = first.filter as Filter;
            check(filter);
            check((first.filters as Filter[])[0]);
            check((first.byKey as Record<string, Filter>).first);
            check(first.choice as Filter);
            filter['wire-labels'].push('mutated');
            filter.child.date = LocalDate.parse('2000-01-01');
            check(api.defaults().spec.request.queryParameters!.filter as Filter);
            check((first.filters as Filter[])[0]);
            const explicit = {...filter, child: {date: LocalDate.parse('2027-01-01')}};
            const operation = api.defaults(explicit, null, null, null);
            if (operation.spec.request.queryParameters!.filter !== explicit) throw new Error('Explicit model was decoded or copied');
            await operation.transportRequest();
            const omitted = await api.defaults(null, null, null, null).transportRequest();
            if (!(omitted instanceof Request) || new URL(omitted.url).searchParams.size !== 0) {
              throw new Error('Explicit null was replaced with a default');
            }
            let rejected = false;
            try { api.invalidDefault(); } catch (error) {
              rejected = error instanceof z.ZodError && error.issues.some(issue => issue.path.join('.') === 'child.date');
            }
            if (!rejected) throw new Error('Invalid model default bypassed its schema');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ModelDefaultsCheck", "!model-defaults-check") to check),
        "model-defaults-check",
        esm = true,
      ),
    )
  }

  private fun modelDefaultsApi(
    frontend: String,
    directory: Path,
  ): GeneratedApi {
    val default =
      "{child: {date: '2026-10-04'}, state: active, id: id-1, uri: 'https://example.com/default', " +
        "wire-labels: [initial], nullable: null}"
    val schemas =
      """
      State: {type: string, enum: [active, inactive]}
      Identifier: {type: string, minLength: 1, x-sunday-wrapper-type: true}
      Child:
        type: object
        required: [date]
        properties:
          date: {type: string, format: date}
      Filter:
        type: object
        required: [child, state, id, uri, wire-labels, nullable]
        properties:
          child: {${'$'}ref: '#/components/schemas/Child'}
          state: {${'$'}ref: '#/components/schemas/State'}
          id: {${'$'}ref: '#/components/schemas/Identifier'}
          uri: {type: string, format: uri}
          wire-labels: {type: array, items: {type: string}}
          nullable: {type: [string, 'null']}
          absent: {type: string}
      Choice:
        oneOf: [{${'$'}ref: '#/components/schemas/Filter'}, {type: string}]
      """.trimIndent()
    val openapi =
      """
      openapi: 3.1.0
      info: {title: Model defaults, version: 1.0.0}
      paths:
        /defaults:
          get:
            operationId: defaults
            parameters:
              - {name: filter, in: query, schema: {${'$'}ref: '#/components/schemas/Filter', default: $default}}
            responses:
              '204': {description: No content}
      components:
        schemas:
      """.trimIndent() + "\n" + schemas.prependIndent("    ")
    val asyncapi =
      """
      asyncapi: 3.0.0
      info: {title: Model defaults, version: 1.0.0}
      channels:
        defaults:
          address: /defaults/{filter}
          parameters:
            filter: {schema: {${'$'}ref: '#/components/schemas/Filter', default: $default}}
            choice: {schema: {${'$'}ref: '#/components/schemas/Choice'}}
          messages:
            filter: {payload: {${'$'}ref: '#/components/schemas/Filter'}}
      operations:
        defaults:
          action: receive
          channel: {${'$'}ref: '#/channels/defaults'}
          messages: [{${'$'}ref: '#/channels/defaults/messages/filter'}]
      components:
        schemas:
      """.trimIndent() + "\n" + schemas.prependIndent("    ")
    val raml =
      """
      #%RAML 1.0
      title: Model defaults
      annotationTypes:
        sunday.wrapperType: {type: boolean, allowedTargets: [TypeDeclaration]}
      types:
        State: {type: string, enum: [active, inactive]}
        Identifier: {type: string, minLength: 1, (sunday.wrapperType): true}
        Child:
          type: object
          properties:
            date: date-only
        Filter:
          type: object
          properties:
            child: Child
            state: State
            id: Identifier
            uri: string
            wire-labels: string[]
            nullable: string | nil
            absent?: string
        Choice: Filter | string
      /defaults:
        get:
          displayName: defaults
          queryParameters:
            filter: {type: Filter, default: ${default.replace("nullable: null", "nullable: present")}}
            choice?: Choice
          responses:
            204:
      """.trimIndent()
    val source = directory.resolve(if (frontend == "raml") "models.raml" else "models.yaml")
    source.writeText(
      when (frontend) {
        "raml" -> raml
        "asyncapi" -> asyncapi
        else -> openapi
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      events.writeText("asyncapi: 3.0.0\ninfo: {title: Model defaults, version: 1.0.0}\nchannels: {}\noperations: {}\n")
      sources += events.toUri()
    }
    val exported = GeneratedApiIrExporter().export(sources)
    val api = GeneratedApiYaml.readString(GeneratedApiYaml.writeString(exported))
    val service = api.services.single()
    val operation = service.operations.single()
    val parameter =
      operation.parameters
        .single { it.name == "filter" }
        .copy(location = GeneratedParameter.Location.QUERY, required = false)
    val defaults =
      operation.copy(
        id = "defaults",
        method = "GET",
        path = "/defaults",
        requestBody = null,
        streaming = null,
        exchange = null,
        protocol = null,
        parameters =
          listOf(
            parameter,
            parameter.copy(
              name = "filters",
              type = GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(parameter.type)),
              defaultValue = listOf(parameter.defaultValue),
            ),
            parameter.copy(
              name = "byKey",
              type = GeneratedTypeRef(GeneratedTypeRef.Kind.MAP, "map", arguments = listOf(parameter.type)),
              defaultValue = mapOf("first" to parameter.defaultValue),
            ),
            parameter.copy(name = "choice", type = GeneratedTypeRef.named("Choice")),
          ),
      )
    val invalid =
      defaults.copy(
        id = "invalidDefault",
        parameters =
          listOf(
            parameter.copy(
              defaultValue = (parameter.defaultValue as Map<*, *>) + ("child" to emptyMap<String, Any>()),
            ),
          ),
      )
    // Exercise frontend-produced schemas/defaults at the shared HTTP parameter boundary, including AsyncAPI schemas.
    return api.copy(
      services =
        listOf(
          service.copy(name = "Parameters", group = null, protocol = null, operations = listOf(defaults, invalid)),
        ),
    )
  }
}
