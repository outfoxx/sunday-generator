/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] ScalarModels Test")
@Tag("models")
class TypeScriptSundayIrScalarModelsTest : TypeScriptSundayIrTestSupport() {

  @Test
  @Tag("requests")
  fun `rejects enum parameter defaults that do not match enum entries`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum Default API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "SearchService",
              baseUri = "https://{status}.example.com",
              baseUriParameters =
                listOf(
                  GeneratedParameter(
                    "status",
                    GeneratedParameter.Location.PATH,
                    GeneratedTypeRef.named("Status"),
                    defaultValue = "missing",
                  ),
                ),
              operations =
                listOf(
                  GeneratedOperation(
                    id = "search",
                    method = "GET",
                    path = "/search",
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("active"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("TypeScript enum 'Status' default value 'missing'"), error.message)
    assertTrue(error.message!!.contains("does not match any enum value"), error.message)
  }

  @Test
  fun `wire refinements retain enum codecs formatted scalars and transport policies`(compiler: TypeScriptCompiler) {
    val plain = GeneratedTypeRef.named("PlainState")
    val tolerant = GeneratedTypeRef.named("StateAlias")
    val properties =
      listOf(
        GeneratedModelProperty(
          "plain",
          plain,
          validation =
            mapOf(
              "minLength" to "2",
              "maxLength" to "4",
              "pattern" to "^go",
            ),
        ),
        GeneratedModelProperty(
          "mode",
          tolerant,
          defaultValue = "good",
          allowedValues = listOf("good"),
          validation =
            mapOf(
              "minLength" to "2",
            ),
        ),
        GeneratedModelProperty(
          "url",
          GeneratedTypeRef.named("UrlAlias"),
          defaultValue = "https://example.test/",
          allowedValues = listOf("https://example.test/"),
        ),
        GeneratedModelProperty(
          "instant",
          GeneratedTypeRef.scalar("string", format = "date-time"),
          allowedValues = listOf("2026-01-01T00:00:01Z"),
        ),
        GeneratedModelProperty(
          "bytes",
          GeneratedTypeRef.scalar("string", format = "byte"),
          allowedValues = listOf("SGk"),
        ),
        GeneratedModelProperty(
          "zero",
          GeneratedTypeRef.scalar("integer"),
          defaultValue = "0",
          allowedValues = listOf(0),
        ),
        GeneratedModelProperty(
          "flag",
          GeneratedTypeRef.scalar("boolean"),
          defaultValue = "false",
          allowedValues = listOf(false),
        ),
      )
    val api =
      GeneratedApi(
        name = "Wire restrictions",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel("PlainState", GeneratedModel.Kind.ENUM, values = listOf("a", "good", "bad", "longer")),
            GeneratedModel(
              "State",
              GeneratedModel.Kind.ENUM,
              values = listOf("good", "bad", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              "StateAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("State")),
            ),
            GeneratedModel(
              "UrlAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string", format = "uri")),
            ),
            GeneratedModel(
              "Base",
              GeneratedModel.Kind.OBJECT,
              properties =
                properties.map {
                  it.copy(validation = emptyMap(), allowedValues = null, defaultValue = null)
                },
            ),
            GeneratedModel(
              "Child",
              GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Base")),
              properties = properties,
            ),
          ),
      )
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("WireCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ChildSchema} from './child';
            import {BaseSchema} from './base';
            import {PlainState} from './plain-state';
            const policy = {format: 'json' as const, dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64};
            const runtime = createSchemaRuntime(policy);
            const schema = runtime.resolveSchema(ChildSchema);
            const wire = {plain: 'good', mode: 'good', url: 'https://example.test/', instant: '2026-01-01T00:00:01Z', bytes: 'SGk', zero: 0, flag: false};
            const decoded = schema.parse(wire);
            const canonical: PlainState | null | undefined = decoded.plain;
            if (canonical !== PlainState.Good || !(decoded.url instanceof URL) || !(decoded.bytes instanceof ArrayBuffer)) throw new Error('canonical types lost');
            const encoded = z.encode(schema, decoded);
            if (JSON.stringify(encoded) !== JSON.stringify(wire)) throw new Error('wire round trip changed');
            const omitted = schema.parse({});
            if (omitted.mode?.toString() !== 'good' || omitted.url?.href !== wire.url || omitted.zero !== 0 || omitted.flag !== false) throw new Error('prefaults lost');
            for (const [field, value] of [['plain', 'a'], ['plain', 'longer'], ['plain', 'bad'], ['mode', 'future'], ['url', 'https://other.test/'], ['url', null], ['instant', '2027-01-01T00:00:01Z'], ['bytes', 'QQ'], ['zero', false], ['flag', 0]] as const) {
              if (schema.safeParse({...wire, [field]: value}).success) throw new Error('accepted invalid ' + field);
            }
            const base = runtime.resolveSchema(BaseSchema);
            const invalid = {...decoded, plain: PlainState.A};
            if (z.safeEncode(schema, invalid).success) throw new Error('encoding bypassed restriction');
            if (base.parse({mode: 'future'}).mode?.toString() !== 'future') throw new Error('tolerant base changed');
            const other = createSchemaRuntime({...policy, format: 'cbor', dateEncoding: DateEncoding.MILLISECONDS_SINCE_EPOCH, numericDateDecoding: 1, arrayBufferEncoding: ArrayBufferEncoding.RAW_BYTES});
            const otherSchema = other.resolveSchema(ChildSchema);
            const alternative = {...wire, instant: 1767225601000, bytes: decoded.bytes};
            const alternateDecoded = otherSchema.parse(alternative);
            const alternateEncoded = z.encode(otherSchema, alternateDecoded) as Record<string, unknown>;
            if (alternateEncoded.instant !== alternative.instant || !(alternateEncoded.bytes instanceof ArrayBuffer)) throw new Error('transport policy lost');
            if (otherSchema.safeParse({...alternative, instant: 1767225602000}).success) throw new Error('numeric timestamp bypassed restriction');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("WireCheck", "!wire-check") to check),
        "wire-check",
      ),
    )
  }

  @Test
  fun `emits OpenAPI scalar alias reference fields as scalar schemas`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/scalar-alias-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val updateSource =
      buildString {
        FileSpec
          .get(findTypeMod("NarrativeScopeUpdate@!narrative-scope-update", builtTypes), "narrative-scope-update")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(updateSource.contains("'location': z.string().regex(/^[A-Z2-7]{26}$/).optional()"), updateSource)
    assertFalse(updateSource.contains("z.record(z.string(), z.unknown()).regex"), updateSource)
  }
}
