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
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
class TypeScriptPrototypePropertyTest {
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
  fun `declared prototype properties validate and round trip independently of unknown fields`(
    frontend: String,
    preserve: Boolean,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      prototypePropertyApi(frontend, directory),
      registry,
      TypeScriptSundayOptions(
        "http://example.com/",
        listOf("application/json"),
        "API",
        preserveUnknownFields = preserve,
      ),
    ).generateServiceTypes()
    // AsyncAPI does not currently export patternProperties into the IR.
    val invalidPatternValues = if (frontend == "asyncapi") "'x', 123" else "'longer', 'x', 123"
    val check =
      ModuleSpec
        .builder("PrototypeCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {OptionalRecordSchema} from './optional-record';
            import {RequiredRecordSchema} from './required-record';
            import {ClosedRecordSchema} from './closed-record';
            import {NestedRecordSchema} from './nested-record';
            import {DefaultRecordSchema} from './default-record';
            import {DateRecordSchema} from './date-record';
            import {InheritedRecordSchema} from './inherited-record';
            import {PatternRecordSchema} from './pattern-record';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const optional = runtime.resolveSchema(OptionalRecordSchema);
            const required = runtime.resolveSchema(RequiredRecordSchema);
            const closed = runtime.resolveSchema(ClosedRecordSchema);
            const inherited = runtime.resolveSchema(InheritedRecordSchema);
            for (const schema of [optional, required, closed, inherited]) {
              for (const value of [123, null, 'x', {polluted: true}]) {
                const input = {['__proto__']: value};
                for (const result of [schema.safeParse(input), schema.safeEncode(input as never)]) {
                  if (result.success) throw new Error('accepted invalid declared prototype property');
                  if (!result.error.issues.some(issue => issue.path[0] === '__proto__')) throw new Error('missing property error path');
                }
              }
              const input = JSON.parse('{"__proto__":"valid"}');
              const decoded = schema.parse(input);
              for (const output of [decoded, schema.encode(decoded)]) {
                if (!Object.hasOwn(output, '__proto__') || output['__proto__'] !== 'valid') throw new Error('lost declared prototype property');
                if (Object.getPrototypeOf(output) !== Object.prototype) throw new Error('changed object prototype');
              }
            }
            for (const schema of [optional, closed, inherited]) {
              const output = schema.encode(schema.parse({}));
              if (Object.hasOwn(output, '__proto__')) throw new Error('invented absent optional property');
            }
            if (required.safeParse({}).success || required.safeEncode({} as never).success) throw new Error('missing required property accepted');
            if (closed.safeParse({['__proto__']: 'valid', extra: true}).success) throw new Error('closed model accepted unknown field');
            const nested = runtime.resolveSchema(NestedRecordSchema);
            const nestedInput = JSON.parse('{"__proto__":{"__proto__":"valid","future":true}}');
            const nestedValue = nested.parse(nestedInput);
            const nestedOutput = nested.encode(nestedValue) as Record<string, any>;
            if (nestedOutput['__proto__']['__proto__'] !== 'valid') throw new Error('lost nested property');
            if (Object.hasOwn(nestedOutput['__proto__'], 'future') !== $preserve) throw new Error('restored raw nested value');
            if (Object.getPrototypeOf(nestedValue) !== Object.prototype || Object.getPrototypeOf(nestedValue['__proto__']) !== Object.prototype) throw new Error('nested prototype changed');
            const defaults = runtime.resolveSchema(DefaultRecordSchema);
            const defaulted = defaults.parse({});
            ${if (frontend == "raml") "" else "if (defaulted['__proto__'] !== 'fallback' || defaults.encode(defaulted)['__proto__'] !== 'fallback') throw new Error('lost default');"}
            const date = runtime.resolveSchema(DateRecordSchema);
            const dateValue = date.parse(JSON.parse('{"__proto__":"2026-09-30T12:30:45Z"}'));
            if (typeof dateValue['__proto__'] !== 'object') throw new Error('lost decoded date');
            if (date.encode(dateValue)['__proto__'] !== '2026-09-30T12:30:45Z') throw new Error('lost encoded date');
            const pattern = runtime.resolveSchema(PatternRecordSchema);
            pattern.parse({['__proto__']: 'good'});
            for (const value of [$invalidPatternValues]) {
              const input = {['__proto__']: value};
              if (pattern.safeParse(input).success || pattern.safeEncode(input as never).success) throw new Error('ignored declared or pattern constraint');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("PrototypeCheck", "!prototype-check") to check),
        "prototype-check",
      ),
    )
  }

  private fun prototypePropertyApi(
    frontend: String,
    directory: Path,
  ): GeneratedApi {
    val raml =
      """
      #%RAML 1.0
      title: Prototype Properties
      types:
        Envelope:
          type: object
          properties:
            optional?: OptionalRecord
            required?: RequiredRecord
            closed?: ClosedRecord
            nested?: NestedRecord
            defaulted?: DefaultRecord
            date?: DateRecord
            inherited?: InheritedRecord
            pattern?: PatternRecord
        OptionalRecord:
          type: object
          properties:
            __proto__?: {type: string, minLength: 2}
        RequiredRecord:
          type: object
          properties:
            __proto__: {type: string, minLength: 2}
        ClosedRecord:
          type: object
          additionalProperties: false
          properties:
            __proto__?: {type: string, minLength: 2}
        NestedRecord:
          type: object
          properties:
            __proto__: OptionalRecord
        DefaultRecord:
          type: object
          properties:
            __proto__?: {type: string, default: fallback}
        DateRecord:
          type: object
          properties:
            __proto__: datetime
        InheritedRecord:
          type: OptionalRecord
          properties:
            kind?: string
        PatternRecord:
          type: object
          properties:
            __proto__?: {type: string, minLength: 2}
            /^__proto__$/: {type: string, maxLength: 4}
      /record:
        get:
          responses:
            200:
              body:
                application/json: Envelope
      """.trimIndent()
    val schemas =
      """
      components:
        schemas:
          Envelope:
            type: object
            properties:
              optional: {${'$'}ref: '#/components/schemas/OptionalRecord'}
              required: {${'$'}ref: '#/components/schemas/RequiredRecord'}
              closed: {${'$'}ref: '#/components/schemas/ClosedRecord'}
              nested: {${'$'}ref: '#/components/schemas/NestedRecord'}
              defaulted: {${'$'}ref: '#/components/schemas/DefaultRecord'}
              date: {${'$'}ref: '#/components/schemas/DateRecord'}
              inherited: {${'$'}ref: '#/components/schemas/InheritedRecord'}
              pattern: {${'$'}ref: '#/components/schemas/PatternRecord'}
          OptionalRecord:
            type: object
            properties:
              __proto__: {type: string, minLength: 2}
          RequiredRecord:
            type: object
            required: [__proto__]
            properties:
              __proto__: {type: string, minLength: 2}
          ClosedRecord:
            type: object
            additionalProperties: false
            properties:
              __proto__: {type: string, minLength: 2}
          NestedRecord:
            type: object
            required: [__proto__]
            properties:
              __proto__: {${'$'}ref: '#/components/schemas/OptionalRecord'}
          DefaultRecord:
            type: object
            properties:
              __proto__: {type: string, default: fallback}
          DateRecord:
            type: object
            required: [__proto__]
            properties:
              __proto__: {type: string, format: date-time}
          InheritedRecord:
            allOf: [{${'$'}ref: '#/components/schemas/OptionalRecord'}]
            type: object
            properties:
              kind: {type: string}
          PatternRecord:
            type: object
            properties:
              __proto__: {type: string, minLength: 2}
            patternProperties:
              '^__proto__$': {type: string, maxLength: 4}
      """.trimIndent()
    val prefix =
      if (frontend == "asyncapi") {
        """
        asyncapi: 3.0.0
        channels:
          records:
            address: /record
            messages:
              record: {payload: {${'$'}ref: '#/components/schemas/Envelope'}}
        operations:
          receiveRecord:
            action: receive
            channel: {${'$'}ref: '#/channels/records'}
            messages: [{${'$'}ref: '#/channels/records/messages/record'}]
        """.trimIndent()
      } else {
        """
        openapi: 3.1.0
        paths:
          /record:
            get:
              operationId: getRecord
              responses:
                '200':
                  description: Record
                  content:
                    application/json:
                      schema: {${'$'}ref: '#/components/schemas/Envelope'}
        """.trimIndent()
      }
    val source = directory.resolve(if (frontend == "raml") "models.raml" else "models.yaml")
    Files.writeString(
      source,
      if (frontend ==
        "raml"
      ) {
        raml
      } else {
        "$prefix\ninfo: {title: Prototype Properties, version: 1.0.0}\n$schemas"
      },
    )
    val sources = mutableListOf(source.toUri())
    if (frontend == "composed") {
      val events = directory.resolve("events.yaml")
      Files.writeString(events, "asyncapi: 3.0.0\ninfo: {title: Prototype Properties, version: 1.0.0}\nchannels: {}")
      sources += events.toUri()
    }
    return GeneratedApiIrExporter().export(sources)
  }
}
