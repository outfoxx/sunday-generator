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
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.inheritedConstraintsFixture
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayOptions
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.sunday.generator.typescript.tools.compileTypes
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] Validation Test")
@Tag("validation")
class TypeScriptSundayIrValidationTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `every compatible parent constraint remains effective`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      inheritedConstraintsFixture(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ParentCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {ChildSchema} from './child';
            import {ReversedSchema} from './reversed';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            for (const factory of [ChildSchema, ReversedSchema]) {
              const schema = runtime.resolveSchema(factory);
              const payload = {text: 'abc', count: 2, amount: 0.3};
              const decoded = schema.parse(payload);
              if (JSON.stringify(z.encode(schema, decoded)) !== JSON.stringify(payload)) throw new Error('round trip changed');
              if (schema.parse({}).count !== 2) throw new Error('default lost');
              for (const invalid of [{text: 'a'}, {text: 'abcd'}, {count: 3}, {amount: 0.31}]) {
                if (schema.safeParse(invalid).success) throw new Error('parent restriction lost');
              }
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ParentCheck", "!parent-check") to check),
        "parent-check",
      ),
    )
  }

  @Test
  fun `applies IR validation constraints to TypeScript Sunday schemas`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Validation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchUsers",
                    method = "GET",
                    path = "/users",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "q",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string"),
                          required = true,
                          validation = mapOf("minLength" to "2", "maxLength" to "80"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "CreateUserRequest",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "displayName",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    validation =
                      mapOf(
                        "minLength" to "2",
                        "maxLength" to "50",
                        "pattern" to "^[A-Za-z].*$",
                      ),
                  ),
                  GeneratedModelProperty(
                    "luckyNumber",
                    GeneratedTypeRef.scalar("integer"),
                    validation =
                      mapOf(
                        "minimum" to "1",
                        "maximum" to "100",
                      ),
                  ),
                  GeneratedModelProperty(
                    "tags",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "tags",
                      arguments = listOf(GeneratedTypeRef.scalar("string", format = "uuid")),
                    ),
                    validation =
                      mapOf(
                        "minItems" to "1",
                        "maxItems" to "5",
                      ),
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val requestSource =
      buildString {
        FileSpec
          .get(findTypeMod("CreateUserRequest@!create-user-request", builtTypes), "create-user-request")
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get(findTypeMod("UsersAPI@!users-api", builtTypes), "users-api")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(requestSource.contains("'email': z.string().email()"), requestSource)
    assertTrue(requestSource.contains("'displayName': z.string().min(2).max(50).regex(/^[A-Za-z].*$/)"), requestSource)
    assertTrue(requestSource.contains("'luckyNumber': z.number().gte(1).lte(100).optional()"), requestSource)
    assertTrue(requestSource.contains("'tags': z.array(z.string().uuid()).min(1).max(5).optional()"), requestSource)
    assertTrue(serviceSource.contains("const searchUsersQParameterType = z.string().min(2).max(80);"), serviceSource)
    assertTrue(serviceSource.contains("q: searchUsersQParameterType.parse(q)"), serviceSource)
  }

  @Test
  @Tag("models")
  @Tag("requests")
  fun `validates scalar alias parameters without requiring optional values`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Aliases",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              "Identifier",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              "IdentifierAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("Identifier")),
            ),
          ),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "searchUsers",
                    method = "GET",
                    path = "/users",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "id",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.named("Identifier"),
                          required = true,
                          validation =
                            mapOf(
                              "pattern" to "^[A-Z]+$",
                            ),
                        ),
                        GeneratedParameter(
                          "filter",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.named("IdentifierAlias"),
                          validation =
                            mapOf(
                              "pattern" to "^[A-Z]+$",
                            ),
                        ),
                      ),
                  ),
                ),
            ),
          ),
      )
    TypeScriptSundayIrGenerator(api, registry, TypeScriptSundayOptions("http://example.com/", emptyList(), "API"))
      .generateServiceTypes()
    val checked =
      registry.buildTypes() +
        (
          TypeName.namedImport("AliasParameterCheck", "!alias-parameter-check") to
            ModuleSpec
              .builder("AliasParameterCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |const api = %T({} as any);
                  |const omitted = api.searchUsers('ID', undefined) as any;
                  |if (omitted.spec.request.queryParameters.filter !== undefined) throw new Error('optional filter changed');
                  |const supplied = api.searchUsers('ID', 'FILTER') as any;
                  |if (supplied.spec.request.queryParameters.filter !== 'FILTER') throw new Error('filter changed');
                  |for (const [id, filter] of [[undefined, undefined], ['invalid!', undefined], ['ID', 'invalid!']]) {
                  |  let rejected = false;
                  |  try { api.searchUsers(id as string, filter); } catch { rejected = true; }
                  |  if (!rejected) throw new Error('invalid alias parameter accepted');
                  |}
                  """.trimMargin(),
                  TypeName.namedImport("createUsersAPI", "!users-api"),
                ),
              ).build()
        )
    assertTrue(compileAndRunTypes(compiler, checked, "alias-parameter-check"))
  }

  @Test
  fun `treats OpenAPI empty schemas as unknown in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val holderSource =
      buildString {
        FileSpec
          .get(findTypeMod("AnyHolder@!any-holder", builtTypes), "any-holder")
          .writeTo(this)
      }
    val serviceSource =
      builtTypes
        .map { (typeName, moduleSpec) ->
          val imported = typeName.base as SymbolSpec.Imported
          val modulePath = imported.source.removePrefix("!")
          buildString {
            FileSpec
              .get(moduleSpec, modulePath)
              .writeTo(this)
          }
        }.single { source -> source.contains("updateValue") }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(holderSource.contains("'value': z.unknown().optional()"), holderSource)
    assertTrue(holderSource.contains("'documented': z.unknown().optional()"), holderSource)
    assertTrue(holderSource.contains("'named': z.unknown().optional()"), holderSource)
    assertTrue(serviceSource.contains("body: unknown"), serviceSource)
    assertTrue(serviceSource.contains("Operation<unknown, unknown, Factory>"), serviceSource)
  }

  @Test
  @Tag("models")
  fun `rejects duplicate wire values only for tolerant TypeScript enums`(compiler: TypeScriptCompiler) {
    val strictTypeRegistry = TypeScriptTypeRegistry(setOf())
    val strictModel =
      GeneratedModel(
        name = "Status",
        kind = GeneratedModel.Kind.ENUM,
        values = listOf("active", "active", "unknown"),
        enumValueNames = listOf("Primary", "Alias", "Unknown"),
      )
    val strictApi =
      GeneratedApi(
        name = "Strict Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models = listOf(strictModel),
      )

    TypeScriptSundayIrGenerator(strictApi, strictTypeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, strictTypeRegistry.buildTypes()))

    val tolerantTypeRegistry = TypeScriptTypeRegistry(setOf())
    val tolerantApi =
      strictApi.copy(
        name = "Tolerant Enum API",
        models = listOf(strictModel.copy(unknownValue = "unknown")),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(tolerantApi, tolerantTypeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(
      error.message!!.contains(
        "TypeScript tolerant enum 'Status' wire value 'active' is used by multiple members 'Primary', 'Alias'",
      ),
      error.message,
    )
    assertTrue(error.message!!.contains("require unique wire values for raw-value interning"), error.message)
  }

  @Test
  @Tag("models")
  fun `generates tolerant enum codecs that preserve raw values`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/ir/tolerant-enum.raml") ramlUri: URI,
    @ResourceUri("openapi/ir/tolerant-enum-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/tolerant-enum.yaml") asyncApiUri: URI,
  ) {
    val composedApi =
      GeneratedApi(
        name = "Tolerant Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "TaskState",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("pending", "running", "unknown"),
              unknownValue = "unknown",
            ),
          ),
      )
    val apis =
      listOf(
        RamlToGeneratedApi().convert(TestAPIProcessing.process(ramlUri)),
        OpenApiToGeneratedApi().convert(openApiUri),
        AsyncApiToGeneratedApi().convertFragment(asyncApiUri).api,
        GeneratedApiYaml.readString(GeneratedApiYaml.writeString(composedApi)),
      )

    apis.forEach { api ->
      val typeRegistry = TypeScriptTypeRegistry(setOf())
      TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
        .generateServiceTypes()
      assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))
    }

    val typeRegistry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(composedApi, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val runtimeCheckedTypes =
      typeRegistry.buildTypes() +
        (
          TypeName.namedImport("TolerantEnumRuntimeCheck", "!tolerant-enum-runtime-check") to
            ModuleSpec
              .builder("TolerantEnumRuntimeCheck", ModuleSpec.Kind.MODULE)
              .addCode(
                CodeBlock.of(
                  """
                  |import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
                  |const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601,
                  |  numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
                  |const schema = runtime.resolveSchema(%T);
                  |const TaskStateType = %T;
                  |const firstFromValue = TaskStateType.fromValue('future');
                  |const secondFromValue = TaskStateType.fromValue('future');
                  |const firstUnknown = schema.parse('future');
                  |const secondUnknown = schema.parse('future');
                  |const otherUnknown = schema.parse('other');
                  |if (firstFromValue !== secondFromValue || firstUnknown !== firstFromValue ||
                  |    firstUnknown !== secondUnknown || firstUnknown !== TaskStateType.Unknown('future')) {
                  |  throw new Error('equal unknown values were not interned');
                  |}
                  |if (TaskStateType.Unknown('pending') === TaskStateType.Pending ||
                  |    TaskStateType.fromValue('pending') !== TaskStateType.Pending) {
                  |  throw new Error('explicit fallback identity was lost');
                  |}
                  |if (firstUnknown === otherUnknown || firstUnknown.kind !== 'Unknown' || firstUnknown.rawValue !== 'future') {
                  |  throw new Error('distinct unknown values did not retain their identity and data');
                  |}
                  |if (new Set([firstUnknown, secondUnknown, otherUnknown]).size !== 2) {
                  |  throw new Error('interned values did not deduplicate in Set');
                  |}
                  |const values = new Map([[firstUnknown, 'found']]);
                  |if (values.get(secondUnknown) !== 'found') {
                  |  throw new Error('interned values did not work as Map keys');
                  |}
                  |if (schema.parse(schema.encode(firstUnknown)) !== firstUnknown) {
                  |  throw new Error('codec round-trip did not preserve the interned value');
                  |}
                  """.trimMargin(),
                  TypeName.namedImport("TaskStateSchema", "!task-state"),
                  TypeName.namedImport("TaskState", "!task-state"),
                ),
              ).build()
        )
    assertTrue(
      compileAndRunTypes(
        compiler,
        runtimeCheckedTypes,
        "tolerant-enum-runtime-check",
      ),
    )

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "task-state.ts")
    assertTrue(source.contains("export class TaskState"), source)
    assertTrue(source.contains("private static instances: Map<string, TaskState>"), source)
    assertTrue(source.contains("static Unknown(rawValue: string): TaskState"), source)
    assertTrue(source.contains("return TaskState.intern('Unknown', rawValue)"), source)
    assertTrue(source.contains("case 'pending': return TaskState.Pending"), source)
    assertTrue(source.contains("const key = kind.length + ':' + kind + rawValue"), source)
    assertTrue(source.contains("encode: (value) => value.rawValue"), source)
    assertTrue(source.contains("readonly kind: 'Pending' | 'Running' | 'Unknown'"), source)
    assertTrue(source.contains("toString(): string"), source)
    assertTrue(source.contains("return this.rawValue"), source)
  }

  @Test
  @Tag("models")
  fun `generates tolerant discriminator hierarchy fallbacks`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Jobs API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "JobPhase",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("started", "paused", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "JobProgress",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("jobId", GeneratedTypeRef.scalar("string"), required = true),
                ),
              discriminator = "phase",
              discriminatorMappings = mapOf("started" to GeneratedTypeRef.named("JobStarted")),
            ),
            GeneratedModel(
              name = "JobStarted",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("JobProgress")),
              discriminatorValue = "started",
              properties =
                listOf(
                  GeneratedModelProperty("taskCount", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "JobPaused",
              kind = GeneratedModel.Kind.OBJECT,
              discriminatorValue = "paused",
              properties =
                listOf(
                  GeneratedModelProperty("phase", GeneratedTypeRef.named("JobPhase"), required = true),
                  GeneratedModelProperty("reason", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "JobEvent",
              kind = GeneratedModel.Kind.UNION,
              aliases = listOf(GeneratedTypeRef.named("JobStarted"), GeneratedTypeRef.named("JobPaused")),
              discriminator = "phase",
              discriminatorMappings =
                mapOf(
                  "started" to GeneratedTypeRef.named("JobStarted"),
                  "paused" to GeneratedTypeRef.named("JobPaused"),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val hierarchySource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-progress.ts")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-progress-unknown.ts")
    val unionSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "job-event.ts")
    assertTrue(hierarchySource.contains("JobProgressUnknownSchema"), hierarchySource)
    assertTrue(fallbackSource.contains("export interface JobProgressUnknown"), fallbackSource)
    assertTrue(fallbackSource.contains("readonly rawBody: Readonly<Record<string, unknown>>"), fallbackSource)
    assertTrue(fallbackSource.contains("!['started'].includes(value)"), fallbackSource)
    assertTrue(unionSource.contains("JobEventUnknownSchema"), unionSource)
  }

  @Test
  @Tag("models")
  fun `generates tolerant external discriminator fallbacks`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Events API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.ASYNCAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EventType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("created", "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "EventData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              properties =
                listOf(
                  GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
                ),
              discriminatorMappings = mapOf("created" to GeneratedTypeRef.named("CreatedData")),
            ),
            GeneratedModel(
              name = "CreatedData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EventData")),
              discriminatorValue = "created",
              properties = listOf(GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "EventEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EventType"), required = true),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("EventData"),
                    required = true,
                    externalDiscriminator = "type",
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val envelopeSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "event-envelope.ts")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "event-data-unknown.ts")
    assertTrue(envelopeSource.contains("!['created'].includes(value)"), envelopeSource)
    assertTrue(envelopeSource.contains("EventDataUnknownSchema"), envelopeSource)
    assertTrue(fallbackSource.contains("version: number"), fallbackSource)
    assertTrue(fallbackSource.contains("readonly rawBody: Readonly<Record<string, unknown>>"), fallbackSource)
  }

  @Test
  @Tag("models")
  fun `escapes tolerant discriminator mapping values in fallback guards`(compiler: TypeScriptCompiler) {
    val trailingBackslash = "trailing\\"
    val injectionShaped = "known\\'; globalThis.compromised = true; //"
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Escaped Discriminators API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "InternalKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf(trailingBackslash, injectionShaped, "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "InternalEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("InternalKind"), required = true),
                ),
              discriminator = "kind",
              discriminatorMappings =
                mapOf(
                  trailingBackslash to GeneratedTypeRef.named("TrailingEvent"),
                  injectionShaped to GeneratedTypeRef.named("InjectionEvent"),
                ),
            ),
            GeneratedModel(
              name = "TrailingEvent",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("InternalEvent")),
              discriminatorValue = trailingBackslash,
            ),
            GeneratedModel(
              name = "InjectionEvent",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("InternalEvent")),
              discriminatorValue = injectionShaped,
            ),
            GeneratedModel(
              name = "ExternalKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf(trailingBackslash, injectionShaped, "unknown"),
              unknownValue = "unknown",
            ),
            GeneratedModel(
              name = "ExternalData",
              kind = GeneratedModel.Kind.OBJECT,
              externallyDiscriminated = true,
              discriminatorMappings =
                mapOf(
                  trailingBackslash to GeneratedTypeRef.named("TrailingData"),
                  injectionShaped to GeneratedTypeRef.named("InjectionData"),
                ),
            ),
            GeneratedModel(
              name = "TrailingData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("ExternalData")),
              discriminatorValue = trailingBackslash,
            ),
            GeneratedModel(
              name = "InjectionData",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("ExternalData")),
              discriminatorValue = injectionShaped,
            ),
            GeneratedModel(
              name = "ExternalEnvelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("ExternalKind"), required = true),
                  GeneratedModelProperty(
                    "data",
                    GeneratedTypeRef.named("ExternalData"),
                    required = true,
                    externalDiscriminator = "kind",
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val expectedValues =
      listOf(injectionShaped, trailingBackslash)
        .sorted()
        .map { value -> CodeBlock.of("%S", value) }
        .joinToString(", ")
    val internalFallbackSource =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "internal-event-unknown.ts")
    val externalEnvelopeSource =
      CompiledGeneratedSources.source(GeneratedCodeLanguage.TypeScript, "external-envelope.ts")
    assertTrue(internalFallbackSource.contains("![$expectedValues].includes(value)"), internalFallbackSource)
    assertTrue(externalEnvelopeSource.contains("![$expectedValues].includes(value)"), externalEnvelopeSource)
  }

  @Test
  @Tag("models")
  fun `rejects invalid explicit TypeScript enum member names`() {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("wire"),
              enumValueNames = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '123'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid member name '123'"), error.message)
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @Tag("models")
  fun `AsyncAPI enum refinements retain inherited storage and validate allowed values`(
    composed: Boolean,
    compiler: TypeScriptCompiler,
    @ResourceUri("asyncapi/ir/inherited-enum.yaml") asyncApiUri: URI,
    @ResourceUri("openapi/ir/composition-identity-3.1.yaml") openApiUri: URI,
  ) {
    val sources = if (composed) listOf(openApiUri, asyncApiUri) else listOf(asyncApiUri)
    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(GeneratedApiIrExporter().export(sources), registry, typeScriptSundayTestOptions)
      .generateServiceTypes()
    val check =
      ModuleSpec
        .builder("InheritedEnumCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {z} from 'zod';
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {EventType} from './event-type';
            import {BaseEvent, BaseEventSchema} from './base-event';
            import {AlphaEvent, AlphaEventSchema} from './alpha-event';
            import {BetaEvent, BetaEventSchema} from './beta-event';
            import {AlphaLeafEvent, AlphaLeafEventSchema} from './alpha-leaf-event';
            import {ReferencedAlphaEvent, ReferencedAlphaEventSchema} from './referenced-alpha-event';
            import {OptionalAlphaEventSchema} from './optional-alpha-event';
            import {MappedEventSchema} from './mapped-event';
            import {RecursiveAlphaEventSchema} from './recursive-alpha-event';

            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const events: BaseEvent[] = [
              {type: EventType.Alpha} satisfies AlphaEvent,
              {type: EventType.Beta} satisfies BetaEvent,
              {type: EventType.Alpha} satisfies AlphaLeafEvent,
              {type: EventType.Alpha} satisfies ReferencedAlphaEvent,
            ];
            const canonical: EventType[] = events.map(event => event.type);
            // @ts-expect-error inherited requiredness must survive the subtype refinement
            const missing: AlphaEvent = {};
            // @ts-expect-error subtype storage must use the named enum rather than a string
            const stringType: AlphaEvent = {type: 'alpha'};
            if (canonical[0] !== EventType.Alpha) throw new Error('enum storage changed');
            for (const [factory, value, invalidValue] of [
              [AlphaEventSchema, EventType.Alpha, EventType.Beta],
              [AlphaLeafEventSchema, EventType.Alpha, EventType.Beta],
              [ReferencedAlphaEventSchema, EventType.Alpha, EventType.Beta],
              [BetaEventSchema, EventType.Beta, EventType.Alpha],
            ] as const) {
              const schema = runtime.resolveSchema(factory);
              const wire = {type: value};
              const decoded = schema.parse(wire);
              const type: EventType = decoded.type;
              if (type !== value) throw new Error('wrong enum value');
              if (JSON.stringify(z.encode(schema, decoded)) !== JSON.stringify(wire)) throw new Error('round trip changed');
              for (const invalid of [{}, {type: null}, {type: invalidValue}]) {
                if (schema.safeParse(invalid).success) throw new Error('requiredness or narrowing lost');
              }
              if (z.safeEncode(schema, {type: invalidValue}).success) throw new Error('encoding bypassed narrowing');
            }
            for (const type of [EventType.Alpha, EventType.Beta]) {
              if (runtime.resolveSchema(BaseEventSchema).parse({type}).type !== type) throw new Error('base enum narrowed');
            }
            const optional = runtime.resolveSchema(OptionalAlphaEventSchema);
            if (optional.parse({})['event-type'] !== undefined) throw new Error('optional property became required');
            const wire = {'event-type': 'alpha'};
            const decoded = optional.parse(wire);
            const optionalType: EventType | null | undefined = decoded['event-type'];
            if (optionalType !== EventType.Alpha) throw new Error('optional enum storage changed');
            if (JSON.stringify(z.encode(optional, decoded)) !== JSON.stringify(wire)) throw new Error('wire name changed');
            if (optional.safeParse({'event-type': 'beta'}).success) throw new Error('optional restriction lost');
            const mapped = runtime.resolveSchema(MappedEventSchema);
            for (const type of ['alpha', 'beta']) {
              const decoded = mapped.parse({type});
              if (decoded.type !== type || JSON.stringify(z.encode(mapped, decoded)) !== JSON.stringify({type})) {
                throw new Error('mapped discriminator round trip changed');
              }
            }
            for (const invalid of [{}, {type: null}, {type: 'unknown'}]) {
              if (mapped.safeParse(invalid).success) throw new Error('invalid discriminator accepted');
            }
            const recursive = runtime.resolveSchema(RecursiveAlphaEventSchema);
            const nested = {type: 'alpha', next: {type: 'alpha'}};
            const roundTripped = recursive.parse(z.encode(recursive, recursive.parse(nested)));
            const nestedType: EventType | undefined = roundTripped.next?.type;
            if (roundTripped.type !== EventType.Alpha || nestedType !== EventType.Alpha) throw new Error('recursive round trip changed');
            if (recursive.safeParse({type: 'alpha', next: {type: 'beta'}}).success) throw new Error('recursive restriction lost');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("InheritedEnumCheck", "!inherited-enum-check") to check),
        "inherited-enum-check",
      ),
    )
  }
}
