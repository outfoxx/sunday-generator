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

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.ir.GeneratedPlacementResolver
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.tools.PlacementIdentityFixture
import io.outfoxx.sunday.generator.tools.PlacementNativeFixture
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.PlacementScopeFixture
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunGeneratedFiles
import io.outfoxx.sunday.generator.typescript.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.utils.camelCaseToKebabCase
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@TypeScriptTest
class TypeScriptPlacementRegressionTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `native problem relocations compile with direct placement`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementScopeFixture.problems(directory, kind),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      "import {FailureProblem} from './problems/failure-problem.js';\nif(new FailureProblem().status !== 400) throw new Error('problem changed');",
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native relocation scopes compile with service defaults and exact model overrides`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementScopeFixture(directory, kind).api
    val child =
      api.models
        .single { model -> model.properties.any { it.name == "value" } }
        .name
        .toUpperCamelCase()
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {$child} from './exact/${child.camelCaseToKebabCase()}.js';
      const value: $child = {value: 'relocated'};
      if(value.value !== 'relocated') throw new Error('placement changed');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @Test
  fun `native imported nesting binds the library parent`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementNativeFixture(directory).importedNesting(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {Parent} from './first/parent.js';
      import {Holder} from './second/holder.js';
      const child: Parent.Child = {value: 12};
      const holder: Holder = {local: {value: 'local'}, parent: {value: 'library'}, child};
      if(holder.child.value !== 12) throw new Error('binding changed');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @Test
  fun `native distinct enclosures share a directory without deduplicating children`(compiler: TypeScriptCompiler) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementNativeFixture(directory).distinctEnclosures(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {First} from './second/first.js';
      import {Second} from './second/second.js';
      const first: First.Child = {value: 'first'};
      const second: Second.Child = {value: 'second'};
      if(first.value !== 'first' || second.value !== 'second') throw new Error('identity changed');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate operations preserve behavior through composition`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementNativeFixture(directory).duplicateOperations(kind, payloads = true),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    assertTrue(compileGeneratedFiles(compiler))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `recomposed IR preserves contribution reference identities`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementIdentityFixture(directory, kind).recomposed(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {Wrapper as First, WrapperSchema as FirstSchema} from './first/wrapper.js';
      import {Wrapper as Second, WrapperSchema as SecondSchema} from './second/wrapper.js';
      import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
      const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
      const first: First = {foo: {value: 'original'}};
      const second: Second = first;
      for(const schema of [FirstSchema, SecondSchema]) {
        if(runtime.resolveSchema(schema).parse(second).foo.value !== 'original') throw new Error('binding changed');
      }
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `composed nested declarations inherit distinct parent placement`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementIdentityFixture(directory, kind).nested(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {Parent as First} from './first/parent.js';
      import {Parent as Second} from './second/parent.js';
      const first: First.Child = {value: 'first'};
      const second: Second.Child = {value: 'second'};
      if(first.value !== 'first' || second.value !== 'second') throw new Error('nested identity');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `same named patch pairs retain source bindings`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).duplicates()
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
      import {Record as First, RecordSchema as FirstSchema} from './first/record.js';
      import {Record as Second, RecordSchema as SecondSchema} from './second/record.js';
      const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
      const input = {value:'before', detail:{label:'nested'}};
      const first = runtime.resolveSchema(FirstSchema).parse(input);
      const second = runtime.resolveSchema(SecondSchema).parse(input);
      const firstMerged = First.merge(first, First.patch(first));
      const secondMerged = Second.merge(second, Second.patch(second));
      if (JSON.stringify(runtime.resolveSchema(FirstSchema).encode(firstMerged)) !== JSON.stringify(input)) throw new Error('first round trip');
      if (JSON.stringify(runtime.resolveSchema(SecondSchema).encode(secondMerged)) !== JSON.stringify(input)) throw new Error('second round trip');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `same named problem schema companions have unambiguous barrel exports`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).duplicates(problems = true)
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import * as generated from './index.js';
      import {BaseProblemSchema as FirstSchema} from './first/base-problem-schema.js';
      import {BaseProblemSchema as SecondSchema} from './second/base-problem-schema.js';
      if (FirstSchema === SecondSchema) throw new Error('distinct declarations merged');
      if (Object.keys(generated).length < 4) throw new Error('missing barrel exports');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `local and imported declarations keep separate identities`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).localAndImported()
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {${if (kind == "raml") "RecordSchema" else "Record2Schema"} as Imported} from './first/${if (kind == "raml") "record" else "record2"}.js';
      import {RecordSchema as Local} from './second/record.js';
      import {HolderSchema} from './second/holder.js';
      import {createSchemaRuntime, DateEncoding, ArrayBufferEncoding} from '@outfoxx/sunday';
      const runtime = createSchemaRuntime({format:'json',dateEncoding:DateEncoding.ISO8601,numericDateDecoding:0,arrayBufferEncoding:ArrayBufferEncoding.BASE64});
      if (Imported === Local) throw new Error('declarations merged');
      const input = {local: {value: 'local'}, imported: {value: 'external'}};
      if (JSON.stringify(runtime.resolveSchema(HolderSchema).parse(input)) !== JSON.stringify(input)) throw new Error('round trip');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "composed"])
  fun `service selection includes anonymous payload descendants`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    val api = PlacementRegressionFixture(directory, kind).inline()
    TypeScriptSundayIrGenerator(
      api,
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Service)), compiler.srcDir)
    val child = api.models.single { it.properties.any { property -> property.name == "value" } }
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {${child.name}} from './${child.name.camelCaseToKebabCase()}.js';
      const child: ${child.name} = {value: 'selected'};
      if (child.value !== 'selected') throw new Error('missing child');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `ts suffix denotes a directory for both services and models`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api =
      PlacementRegressionFixture(directory, kind).inline().copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            defaults = GeneratedPlacement(typescript = GeneratedPlacement.Scope(module = "sdk.ts")),
          ),
      )
    for (style in TypeScriptTypeRegistry.ImportStyle.entries) {
      val registry = TypeScriptTypeRegistry(emptySet(), style)
      TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
      registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
      assertTrue(compileGeneratedFiles(compiler))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `nested types remain in their enclosing module after relocation`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val parsed =
      PlacementRegressionFixture(directory, kind).models(
        """
        Group: {type: object, properties: {value: string}}
        GroupMember:
          type: object
          (sunday.nested): {enclosedIn: Group, name: Member}
          properties: {member: string}
        """.trimIndent(),
        """
        Group: {type: object, required: [value], properties: {value: {type: string}}}
        GroupMember: {type: object, required: [member], properties: {member: {type: string}}}
        """.trimIndent(),
        "typescript: {module: models}",
      )
    // RAML supplies nesting natively; other frontends exercise the shared serialized-IR contract.
    val api =
      GeneratedApiYaml.readString(
        GeneratedApiYaml.writeString(
          parsed.copy(
            models =
              parsed.models.map {
                if (it.name == "GroupMember" &&
                  it.nested == null
                ) {
                  it.copy(nested = GeneratedNestedType(enclosedIn = GeneratedTypeRef.named("Group"), name = "Member"))
                } else {
                  it
                }
              },
          ),
        ),
      )
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      """
      import {Group} from './models/group.js';
      const member: Group.Member = {member: 'nested'};
      if (member.member !== 'nested') throw new Error('nesting');
      """.trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
    val child = api.models.single { it.name == "GroupMember" }
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              child.declarationUri!!,
              placement = GeneratedPlacement(typescript = GeneratedPlacement.Scope(module = "detached")),
            ),
          ),
      )
    val failure =
      assertThrows(IllegalArgumentException::class.java) {
        GeneratedPlacementResolver(config, generationLanguage = "typescript").resolve(api)
      }
    assertTrue(failure.message!!.contains("must remain with"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced composed declarations use invocation directory defaults`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(
      PlacementRegressionFixture(directory, kind).distinctDefaults(),
      registry,
      typeScriptSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
      "import {First} from './first/first.js';\nimport {Second} from './defaults/second.js';\nconst first: First = {value: 'first'}; const second: Second = {value: 'second'};\nif (first.value !== 'first' || second.value !== 'second') throw new Error('placement');",
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced foreign declarations use the output root`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api =
      PlacementRegressionFixture(
        directory,
        kind,
      ).distinctDefaults().copy(placementConfiguration = GeneratedPlacementConfiguration())
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.writeString(
      compiler.srcDir.resolve("smoke.ts"),
"""
import {First} from './first/first.js';
import {Second} from './second.js';
const a: First = {value: 'a'};
const b: Second = {value: 'b'};
if(a.value === b.value) throw new Error('identity');
""".trimIndent(),
    )
    assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `nested validation uses applicable winning relocations`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    for (overridden in listOf(false, true)) {
      val registry = TypeScriptTypeRegistry(emptySet())
      TypeScriptSundayIrGenerator(
        PlacementRegressionFixture(directory, kind).filteredNested(overridden),
        registry,
        typeScriptSundayTestOptions,
      ).generateServiceTypes()
      registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
      Files.writeString(
        compiler.srcDir.resolve("smoke.ts"),
"""
import {First} from './first/first.js';
const child: First.Child = {value: 'nested'};
if(child.value !== 'nested') throw new Error('identity');
""".trimIndent(),
      )
      assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `barrels escape quoted module directories`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    for (style in TypeScriptTypeRegistry.ImportStyle.entries) {
      for (duplicates in listOf(false, true)) {
        val fixture = PlacementRegressionFixture(directory, kind)
        val parsed =
          if (duplicates) {
            fixture.duplicates()
          } else {
            fixture.models(
              "Record: {type: object, properties: {value: string}}",
              "Record: {type: object, required: [value], properties: {value: {type: string}}}",
              "{}",
            )
          }
        val api =
          parsed.copy(
            placementConfiguration =
              GeneratedPlacementConfiguration(
                relocations =
                  parsed.models.map { it.source }.distinct().mapIndexed { index, source ->
                    GeneratedPlacementConfiguration.Relocation(
                      source.location,
                      placement =
                        GeneratedPlacement(
                          typescript = GeneratedPlacement.Scope(module = "owner's-models/$index"),
                        ),
                    )
                  },
              ),
          )
        val registry = TypeScriptTypeRegistry(emptySet(), style)
        TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
        registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
        assertTrue(compileGeneratedFiles(compiler))
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `excluding any container removes nested descendants and companions`(
    kind: String,
    compiler: TypeScriptCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).nestedSelection()
    val registry = TypeScriptTypeRegistry(emptySet())
    TypeScriptSundayIrGenerator(api, registry, typeScriptSundayTestOptions).generateServiceTypes()
    for (excluded in listOf<String?>(null, "Root", "Middle")) {
      val owners =
        api.models
          .filter { it.name != excluded }
          .map { java.net.URI(it.source.location) }
          .toSet()
      registry.generateFiles(GeneratedOutputSelection(ownerUris = owners), compiler.srcDir)
      val control =
        if (excluded ==
          null
        ) {
          """
          import {Root} from './models/root.js';
          const leaf: Root.Middle.Leaf = {value: 'leaf'};
          if(leaf.value !== 'leaf') throw new Error('leaf');
          """.trimIndent()
        } else {
          ""
        }
      Files.writeString(
        compiler.srcDir.resolve("smoke.ts"),
        """
        import {Survivor} from './models/survivor.js';
        const value: Survivor = {value: 'ok'};
        if(value.value !== 'ok') throw new Error('survivor');
        $control
        """.trimIndent(),
      )
      assertTrue(compileAndRunGeneratedFiles(compiler, "smoke"))
      if (excluded == "Middle") {
        val source =
          io.outfoxx.sunday.generator.tools.CompiledGeneratedSources.source(
            io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage.TypeScript,
            "models/root.ts",
          )
        assertTrue(!source.contains("Middle") && !source.contains("Leaf"))
      }
      if (excluded == "Root") {
        assertThrows(AssertionError::class.java) {
          io.outfoxx.sunday.generator.tools.CompiledGeneratedSources.source(
            io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage.TypeScript,
            "models/root.ts",
          )
        }
      }
    }
  }
}
