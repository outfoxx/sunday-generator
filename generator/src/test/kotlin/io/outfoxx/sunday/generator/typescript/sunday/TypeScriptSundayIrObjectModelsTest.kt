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

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.typescript.TypeScriptSundayIrGenerator
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
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [IR] ObjectModels Test")
@Tag("models")
class TypeScriptSundayIrObjectModelsTest : TypeScriptSundayIrTestSupport() {

  @Test
  fun `generates nested shared models directly from IR`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val groupOutput =
      buildString {
        FileSpec
          .get(findTypeMod("Group@!group", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(groupOutput.contains("export type Group = SchemaOutput<typeof GroupSchema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Group"), groupOutput)
    assertTrue(groupOutput.contains("export type Member1 = SchemaOutput<typeof Member1Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export type Member2 = SchemaOutput<typeof Member2Schema>;"), groupOutput)
    assertTrue(groupOutput.contains("export namespace Member1"), groupOutput)
    assertTrue(groupOutput.contains("export type Sub = SchemaOutput<typeof SubSchema>;"), groupOutput)
    assertFalse(groupOutput.contains("export class Group"), groupOutput)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: TypeScriptCompiler) {
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Imported API",
        source = mainSource,
        models =
          listOf(
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = mainSource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "main-models")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("typescript" to GeneratedTarget(modelModuleName = "library-models")),
              properties =
                listOf(
                  GeneratedModelProperty("libraryValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Consumer",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("main", GeneratedTypeRef.named("Test", source = mainSource), required = true),
                  GeneratedModelProperty(
                    "library",
                    GeneratedTypeRef.named("Test", source = librarySource),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get(findTypeMod("Consumer@!consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!main-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(
      builtTypes.containsKey(TypeName.standard("Test@!library-models/test")),
      "Available types: ${builtTypes.keys}",
    )
    assertTrue(source.contains("import {TestSchema} from './main-models/test';"), source)
    assertTrue(
      source.contains("import {TestSchema as TestSchema_} from './library-models/test';"),
      source,
    )
    assertTrue(source.contains("'main': z.lazy(() => runtime.resolveSchema(TestSchema))"), source)
    assertTrue(source.contains("'library': z.lazy(() => runtime.resolveSchema(TestSchema_))"), source)
  }

  @Test
  @Tag("responses")
  fun `generates inline response body models from IR with existing TypeScript Sunday output shape`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/res-body-param-inline-type.node-next.api.ts",
      TypeScriptTypeRegistry.ImportStyle.NodeNext,
    )
  }

  @Test
  @Tag("responses")
  fun `generates HTTP problem models as Sunday Problem errors`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "HTTP Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri")),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer")),
                  GeneratedModelProperty(
                    "detail",
                    GeneratedTypeRef.scalar("string"),
                    required = true,
                    validation =
                      mapOf(
                        "minLength" to "2",
                      ),
                  ),
                  GeneratedModelProperty("instance", GeneratedTypeRef.scalar("string", format = "uri")),
                ),
            ),
            GeneratedModel(
              name = "BadRequestProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string")),
                  GeneratedModelProperty(
                    "validation",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.MAP,
                      name = "map",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val httpProblemOutput =
      buildString {
        FileSpec
          .get(findTypeMod("HttpProblem@!http-problem", builtTypes), "http-problem")
          .writeTo(this)
      }
    val badRequestOutput =
      buildString {
        FileSpec
          .get(findTypeMod("BadRequestProblem@!bad-request-problem", builtTypes), "bad-request-problem")
          .writeTo(this)
      }

    val check =
      ModuleSpec
        .builder("ProblemFieldsCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
            import {BadRequestProblem, BadRequestProblemSchema} from './bad-request-problem';
            import {z} from 'zod';
            const runtime = createSchemaRuntime({format: 'json', dateEncoding: DateEncoding.ISO8601, numericDateDecoding: 0, arrayBufferEncoding: ArrayBufferEncoding.BASE64});
            const schema = runtime.resolveSchema(BadRequestProblemSchema);
            let rejected = false;
            try { new BadRequestProblem({detail: '', validation: {}}); }
            catch (error) { rejected = error instanceof z.ZodError; }
            if (!rejected) throw new Error('constructor bypassed native schema constraints');
            const dynamic = {nested: [null, true, {value: 'future'}]};
            const problem = schema.parse({detail: 'before', validation: {name: 'bad'}, future: dynamic, additionalProperties: {wire: true}});
            const copied = problem.copy({detail: 'after'});
            const output = schema.encode(copied) as Record<string, unknown>;
            if (output.detail !== 'after' || problem.detail !== 'before') throw new Error('copy changed declared fields');
            if (JSON.stringify(output.future) !== JSON.stringify(dynamic)) throw new Error('class copy lost dynamic fields');
            if (JSON.stringify(output.additionalProperties) !== '{"wire":true}') throw new Error('storage-name wire collision');
            const constructed = new BadRequestProblem({detail: 'declared', validation: {}, additionalProperties: {detail: 'spoofed', future: null}});
            const constructedOutput = schema.encode(constructed) as Record<string, unknown>;
            if (constructedOutput.detail !== 'declared' || constructedOutput.future !== null) throw new Error('extension overwrote declared field');
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        builtTypes + (TypeName.namedImport("ProblemFieldsCheck", "!problem-fields-check") to check),
        "problem-fields-check",
      ),
    )
    assertTrue(
      httpProblemOutput.contains("Problem") &&
        httpProblemOutput.contains("from '@outfoxx/sunday'"),
      httpProblemOutput,
    )
    assertTrue(
      httpProblemOutput.contains("export class HttpProblem extends Problem implements HttpProblemSpec"),
      httpProblemOutput,
    )
    assertTrue(httpProblemOutput.contains("type: init.type ?? Problem.BLANK_URL"), httpProblemOutput)
    assertTrue(httpProblemOutput.contains("detail: string;"), httpProblemOutput)
    assertFalse(httpProblemOutput.contains("type: URL | null | undefined;"), httpProblemOutput)
    assertTrue(
      badRequestOutput.contains("export class BadRequestProblem extends HttpProblem implements BadRequestProblemSpec"),
      badRequestOutput,
    )
    assertFalse(badRequestOutput.contains("title: string | null | undefined;"), badRequestOutput)
    assertFalse(badRequestOutput.contains("this.title = init.title;"), badRequestOutput)
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: TypeScriptCompiler) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Model API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
          ),
      )

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userTypeName = TypeName.namedImport("User", "!user")
    assertTrue(builtTypes.containsKey(userTypeName), "Available types: ${builtTypes.keys.joinToString()}")

    val source =
      buildString {
        FileSpec
          .get(findTypeMod("User@!user", builtTypes), "user")
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("export type User = SchemaOutput<typeof UserSchema>;"), source)
    assertTrue(source.contains("'displayName': z.string().optional()"), source)
    assertTrue(source.contains("export const UserSchema"), source)
    assertFalse(source.contains("export interface UserSpec"), source)
    assertFalse(source.contains("export class User"), source)
  }

  @Test
  fun `preserves OpenAPI inline object properties beside conditional allOf in TypeScript Sunday`(
    compiler: TypeScriptCompiler,
    @ResourceUri("openapi/ir/inline-object-conditional-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = TypeScriptTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(testUri)

    TypeScriptSundayIrGenerator(api, typeRegistry, typeScriptSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val dataSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "visualization-render-graph-task-settled-data.ts",
      )
    val renderGraphSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "visualization-render-graph-task-settled-data-render-graph.ts",
      )
    val allOfRequiredSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.TypeScript,
        "all-of-required-data.ts",
      )
    assertTrue(
      dataSource.contains(
        "'renderGraph': runtime.resolveSchema(VisualizationRenderGraphTaskSettledDataRenderGraphSchema)",
      ),
      dataSource,
    )
    assertTrue(allOfRequiredSource.contains("'value': z.string()"), allOfRequiredSource)
    assertTrue(renderGraphSource.contains("'graphJobId': z.string()"), renderGraphSource)
    assertTrue(renderGraphSource.contains("'taskId': z.string()"), renderGraphSource)
    assertTrue(
      renderGraphSource.contains("'state': runtime.resolveSchema(RenderGraphTaskSettledStateSchema)"),
      renderGraphSource,
    )
    assertTrue(renderGraphSource.contains("'completedCount': z.number()"), renderGraphSource)
    assertTrue(renderGraphSource.contains("'taskCount': z.number()"), renderGraphSource)
  }
}
