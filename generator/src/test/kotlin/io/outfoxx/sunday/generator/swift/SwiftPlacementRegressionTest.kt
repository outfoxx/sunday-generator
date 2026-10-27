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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GeneratedOutputSelection
import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedPlacement
import io.outfoxx.sunday.generator.ir.GeneratedPlacementConfiguration
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.PlacementIdentityFixture
import io.outfoxx.sunday.generator.tools.PlacementNativeFixture
import io.outfoxx.sunday.generator.tools.PlacementRegressionFixture
import io.outfoxx.sunday.generator.tools.PlacementScopeFixture
import io.outfoxx.sunday.generator.utils.toUpperCamelCase
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftPlacementRegressionTest {
  @TempDir lateinit var directory: Path

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `declaration relocation keeps owned payload classes together`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = PlacementScopeFixture.inheritedRelocation(directory, kind)
    val parent =
      api.models
        .single { model -> model.properties.any { it.name == "child" } }
        .name
        .toUpperCamelCase()
    val child =
      api.models
        .single { model -> model.properties.any { it.name == "value" } }
        .name
        .toUpperCamelCase()
    compile(
      api,
      compiler,
      true,
      """
      let value = SharedModels.$parent(child: SharedModels.$child(value: "relocated"))
      let encoded = try JSONEncoder().encode(value)
      XCTAssertEqual(try JSONDecoder().decode(SharedModels.$parent.self, from: encoded).child.value, "relocated")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "composed"])
  fun `native problem relocations compile with direct placement`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementScopeFixture.problems(directory, kind),
      compiler,
      true,
      "XCTAssertEqual(SharedModels.FailureProblem().status, 400)",
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native relocation scopes compile with service defaults and exact model overrides`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = PlacementScopeFixture(directory, kind).api
    val child = api.models.single { model -> model.properties.any { it.name == "value" } }
    compile(
      api,
      compiler,
      true,
      """
      let value = SharedModels.${child.name.toUpperCamelCase()}(value: "relocated")
      let encoded = try JSONEncoder().encode(value)
      XCTAssertEqual(try JSONDecoder().decode(SharedModels.${child.name.toUpperCamelCase()}.self, from: encoded).value, "relocated")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `recomposed IR preserves contribution reference identities`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementIdentityFixture(directory, kind).recomposed(),
      compiler,
      true,
      """
      let shared = SharedModels.Foo(value: "original")
      let first = SharedModels.Wrapper(foo: shared)
      let second = SundayGenTest.Wrapper(foo: shared)
      XCTAssertEqual(first.foo.value, "original")
      XCTAssertEqual(second.foo.value, "original")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `composed nested declarations inherit distinct parent placement`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementIdentityFixture(directory, kind).nested(),
      compiler,
      true,
      """
      let first = SharedModels.Parent(child: SharedModels.Parent.Child(value: "first"))
      let second = SundayGenTest.Parent(child: SundayGenTest.Parent.Child(value: "second"))
      XCTAssertEqual(first.child.value, "first")
      XCTAssertEqual(second.child.value, "second")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `problem module dependencies include registration nullification and fields`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val fixture = PlacementIdentityFixture(directory, kind)
    compile(
      fixture.swiftProblems("SharedModels", "SharedModels"),
      compiler,
      true,
      """
      let problem = SharedModels.Missing(info: SharedModels.Detail(value: "context"))
      XCTAssertEqual(problem.info.value, "context")
      let encoded = try JSONEncoder().encode(problem)
      XCTAssertEqual(try JSONDecoder().decode(SharedModels.Missing.self, from: encoded).info.value, "context")
      """.trimIndent(),
    )
    for ((problemModule, fieldModule, message) in listOf(
      Triple(null, null, "unnamed output module"),
      Triple("SharedModels", null, "unnamed output module"),
      Triple("SharedModels", "SundayGenTest", "Cyclic Swift module placement"),
    )) {
      val api = fixture.swiftProblems(problemModule, fieldModule)
      for (nullifyOnly in listOf(false, true)) {
        val source =
          if (nullifyOnly) {
            api.copy(
              services =
                api.services.map { service ->
                  service.copy(operations = service.operations.map { it.copy(problems = emptyList()) })
                },
            )
          } else {
            api
          }
        val failure =
          assertThrows(IllegalArgumentException::class.java) {
            SwiftSundayIrGenerator(
              source,
              SwiftTypeRegistry(emptySet()),
              SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
            )
          }
        assertTrue(failure.message!!.contains(message), failure.message)
      }
    }
  }

  @Test
  fun `native imported nesting binds the library parent`(compiler: SwiftCompiler) {
    compile(
      PlacementNativeFixture(directory).importedNesting(),
      compiler,
      true,
      """
      let child = SharedModels.Parent.Child(value: 12)
      let value = Holder(local: SundayGenTest.Parent(value: "local"), parent: SharedModels.Parent(value: "library"), child: child)
      let encoded = try JSONEncoder().encode(value)
      XCTAssertEqual(try JSONDecoder().decode(Holder.self, from: encoded).child.value, 12)
      """.trimIndent(),
    )
  }

  @Test
  fun `native explicit nested names retain spelling in their identity`(compiler: SwiftCompiler) {
    compile(
      PlacementNativeFixture(directory).distinctEnclosures(sameParent = true),
      compiler,
      false,
      """
      let first = Parent.Nested_Child(value: "first")
      let second = Parent.NestedChild(value: "second")
      XCTAssertEqual(first.value, "first")
      XCTAssertEqual(second.value, "second")
      """.trimIndent(),
    )
  }

  @Test
  fun `native distinct enclosures share a module without deduplicating children`(compiler: SwiftCompiler) {
    compile(
      PlacementNativeFixture(directory).distinctEnclosures(),
      compiler,
      false,
      """
      let first = First(child: First.Child(value: "first"))
      let second = Second(child: Second.Child(value: "second"))
      XCTAssertEqual(first.child.value, "first")
      XCTAssertEqual(second.child.value, "second")
      """.trimIndent(),
    )
  }

  @Test
  fun `native hoisted hierarchy protocols retain their named module`(compiler: SwiftCompiler) {
    compile(
      PlacementNativeFixture(directory).hierarchy("raml", nested = true),
      compiler,
      true,
      """
      let value = SharedModels.Container.Variant(value: "nested")
      let base: any SharedModels.Base = value
      XCTAssertEqual(base.kind, "case")
      let data = try JSONEncoder().encode(value)
      XCTAssertEqual(try JSONDecoder().decode(SharedModels.Container.Variant.self, from: data).value, "nested")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native inferred hierarchy dependencies reject cycles and compile colocated cases`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val fixture = PlacementNativeFixture(directory)
    compile(
      fixture.hierarchy(kind),
      compiler,
      true,
      """
      let data = Data(#"{"kind":"${if (kind == "raml") "case" else "Case"}","value":"valid"}"#.utf8)
      let value = try JSONDecoder().decode(SharedModels.Case.self, from: data)
      XCTAssertEqual(value.value, "valid")
      let reference = try JSONDecoder().decode(SharedModels.BaseRef.self, from: data)
      XCTAssertEqual(reference.value.kind, value.kind)
      XCTAssertFalse(try JSONEncoder().encode(reference).isEmpty)
      """.trimIndent(),
    )
    val failure =
      assertThrows(IllegalArgumentException::class.java) {
        SwiftSundayIrGenerator(
          fixture.hierarchy(kind, cycle = true),
          SwiftTypeRegistry(emptySet()),
          SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
        )
      }
    assertTrue(failure.message!!.contains("Cyclic Swift module placement"), failure.message)
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `native duplicate operations preserve behavior through composition`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementNativeFixture(directory).duplicateOperations(kind, payloads = true),
      compiler,
      false,
      "XCTAssertTrue(true)",
    )
  }

  private fun compile(
    api: GeneratedApi,
    compiler: SwiftCompiler,
    sharedModule: Boolean,
    test: String,
    selection: GeneratedOutputSelection = GeneratedOutputSelection(),
    unnamedMain: Boolean = false,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("http://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(selection, compiler.srcDir)
    if (!unnamedMain && !Files.exists(compiler.srcDir.resolve("SundayGenTest"))) {
      Files.createDirectories(compiler.srcDir.resolve("SundayGenTest"))
      Files.writeString(compiler.srcDir.resolve("SundayGenTest/Consumer.swift"), "public struct PlacementConsumer {}")
    }
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("PlacementTests.swift"),
      """
      import Foundation
      import XCTest
      import Sunday
      import SundayGenTest
      ${if (sharedModule) "import SharedModels" else ""}
      final class PlacementTests: XCTestCase {
        func testPlacement() throws {
      $test
        }
      }
      """.trimIndent(),
    )
    val path = compiler.workDir.resolve("Package.swift")
    val original = Files.readString(path)
    var manifest =
      if (unnamedMain) {
        original.replace(
          "path: \"src\"",
          "path: \"src\", exclude: [\"SharedModels\"]",
        )
      } else {
        original.replace("path: \"src\"", "path: \"src/SundayGenTest\"")
      }
    if (sharedModule) {
      manifest =
        manifest
          .replace(
            "targets: [\n        .target(",
            """
            targets: [
              .target(name: "SharedModels",
                dependencies: [.product(name: "Sunday", package: "sunday-swift")], path: "src/SharedModels"),
              .target(
            """.trimIndent(),
          ).replace(
            "dependencies: [\n            .product",
            "dependencies: [\n            \"SharedModels\",\n            .product",
          )
    }
    try {
      Files.writeString(path, manifest)
      assertTrue(compileAndTestGeneratedFiles(compiler))
    } finally {
      Files.writeString(path, original)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `same named patch pairs retain source bindings`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementRegressionFixture(directory, kind).duplicates(),
      compiler,
      true,
      """
      let data = Data(#"{"value":"before","detail":{"label":"nested"}}"#.utf8)
      let first = try JSONDecoder().decode(SharedModels.Record.self, from: data)
      let second = try JSONDecoder().decode(SundayGenTest.Record.self, from: data)
      let firstPatch: SharedModels.RecordPatch = try first.patch()
      let secondPatch: SundayGenTest.RecordPatch = try second.patch()
      let firstMerged = try first.merge(firstPatch)
      let secondMerged = try second.merge(secondPatch)
      let _: SharedModels.Detail = firstMerged.detail
      let _: SundayGenTest.Detail = secondMerged.detail
      XCTAssertEqual(firstMerged.value, "before")
      XCTAssertEqual(secondMerged.value, "before")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `recursive inheritance across modules exposes overridable base members`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).inheritance()
    compile(
      api.copy(
        models = api.models.map { it.copy(additionalProperties = GeneratedAdditionalProperties(allowed = true)) },
      ),
      compiler,
      true,
      """
      let data = Data(#"{"next":{},"child":{"next":{}},"extra":"retained"}"#.utf8)
      let value = try JSONDecoder().decode(Child.self, from: data)
      let base: Base = value
      XCTAssertNotNil(base.next)
      XCTAssertNotNil(value.child?.next)
      XCTAssertNotNil(base.additionalProperties["extra"])
      let encoded = try JSONEncoder().encode(value)
      let copy = try JSONDecoder().decode(Child.self, from: encoded)
      XCTAssertNotNil(copy.child?.next)
      XCTAssertNotNil(copy.additionalProperties["extra"])
      _ = value.withNext(next: nil)
      _ = value.debugDescription
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `service only relocation emits configuration in service module`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).service()
    val config =
      GeneratedPlacementConfiguration(
        relocations =
          listOf(
            GeneratedPlacementConfiguration.Relocation(
              api.source.location,
              kind = "service",
              placement = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SundayGenTest")),
            ),
          ),
      )
    compile(
      api.copy(placementConfiguration = config),
      compiler,
      false,
      """
      let config = PlacementConfig()
      XCTAssertEqual(try config.baseURL().host, "example.com")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `local and imported declarations keep separate identities`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementRegressionFixture(directory, kind).localAndImported(),
      compiler,
      true,
      """
      let data = Data(#"{"local":{"value":"local"},"imported":{"value":"external"}}"#.utf8)
      let holder = try JSONDecoder().decode(Holder.self, from: data)
      let _: SundayGenTest.Record = holder.local
      let _: SharedModels.${if (kind == "raml") "Record" else "Record2"} = holder.imported
      XCTAssertEqual(holder.local.value, "local")
      XCTAssertEqual(holder.imported.value, "external")
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "composed"])
  fun `model selection includes top level anonymous payload descendants`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api =
      PlacementRegressionFixture(directory, kind).inline().copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            defaults = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SundayGenTest")),
          ),
      )
    val child = api.models.single { it.properties.any { property -> property.name == "value" } }
    compile(
      api,
      compiler,
      false,
      """
      let child = try JSONDecoder().decode(${child.name}.self, from: Data(#"{"value":"selected"}"#.utf8))
      XCTAssertEqual(child.value, "selected")
      """.trimIndent(),
      GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced composed declarations use invocation module defaults`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementRegressionFixture(directory, kind).distinctDefaults(),
      compiler,
      true,
      "XCTAssertEqual(SharedModels.First(value: \"first\").value, \"first\")\nXCTAssertEqual(SundayGenTest.Second(value: \"second\").value, \"second\")",
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `unplaced foreign declarations retain the unnamed output module`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    compile(
      PlacementRegressionFixture(
        directory,
        kind,
      ).distinctDefaults().copy(placementConfiguration = GeneratedPlacementConfiguration()),
      compiler,
      true,
      "XCTAssertEqual(SharedModels.First(value: \"first\").value, \"first\")\nXCTAssertEqual(SundayGenTest.Second(value: \"second\").value, \"second\")",
      unnamedMain = true,
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `external validation companions stay in their declaration module`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api =
      PlacementRegressionFixture(directory, kind).models(
        """
        Identifier:
          type: string
          (sunday.swiftType): Swift.String
          minLength: 2
          (sunday.placement): {swift: {module: SharedModels}}
        Holder:
          type: object
          (sunday.placement): {swift: {module: SharedModels}}
          properties: {identifier: Identifier}
        Consumer: {type: object, properties: {holder: Holder}}
        """.trimIndent(),
        """
        Identifier:
          type: string
          x-sunday-swiftType: Swift.String
          minLength: 2
          x-sunday-placement: {swift: {module: SharedModels}}
        Holder:
          type: object
          x-sunday-placement: {swift: {module: SharedModels}}
          required: [identifier]
          properties: {identifier: {${'$'}ref: '#/components/schemas/Identifier'}}
        Consumer:
          type: object
          required: [holder]
          properties: {holder: {${'$'}ref: '#/components/schemas/Holder'}}
        """.trimIndent(),
        "swift: {module: SundayGenTest}",
      )
    compile(
      api,
      compiler,
      true,
      """
      let data = Data(#"{"holder":{"identifier":"a2e32335-906e-41e0-90cc-bc5d98f28832"}}"#.utf8)
      let value = try JSONDecoder().decode(Consumer.self, from: data)
      XCTAssertEqual(value.holder.identifier, "a2e32335-906e-41e0-90cc-bc5d98f28832")
      _ = try JSONEncoder().encode(value)
      XCTAssertThrowsError(try JSONDecoder().decode(Consumer.self, from: Data(#"{"holder":{"identifier":"x"}}"#.utf8)))
      """.trimIndent(),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `named modules reject references to unnamed declarations`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api =
      PlacementRegressionFixture(directory, kind).models(
        """
        Amount: {type: object, properties: {value: string}}
        Money:
          type: object
          (sunday.placement): {swift: {module: SharedModels}}
          properties: {amount: Amount}
        """.trimIndent(),
        """
        Amount: {type: object, required: [value], properties: {value: {type: string}}}
        Money:
          type: object
          x-sunday-placement: {swift: {module: SharedModels}}
          required: [amount]
          properties: {amount: {${'$'}ref: '#/components/schemas/Amount'}}
        """.trimIndent(),
        "{}",
      )
    compile(
      api.copy(
        placementConfiguration =
          GeneratedPlacementConfiguration(
            defaults = GeneratedPlacement(swift = GeneratedPlacement.Scope(module = "SharedModels")),
          ),
      ),
      compiler,
      true,
      "XCTAssertEqual(Money(amount: Amount(value: \"ok\")).amount.value, \"ok\")",
    )
    val failure =
      assertThrows(IllegalArgumentException::class.java) {
        SwiftSundayIrGenerator(
          api,
          SwiftTypeRegistry(emptySet()),
          SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
        )
      }
    assertTrue(failure.message!!.contains("unnamed output module"))
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `nested validation uses applicable winning relocations`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    for (overridden in listOf(false, true)) {
      compile(
        PlacementRegressionFixture(directory, kind).filteredNested(overridden),
        compiler,
        true,
        "XCTAssertEqual(SharedModels.First.Child(value: \"nested\").value, \"nested\")",
        GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)),
      )
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi2", "asyncapi", "composed"])
  fun `excluding any container removes nested descendants and companions`(
    kind: String,
    compiler: SwiftCompiler,
  ) {
    val api = PlacementRegressionFixture(directory, kind).nestedSelection()
    for (excluded in listOf<String?>(null, "Root", "Middle")) {
      val owners =
        api.models
          .filter { it.name != excluded }
          .map { java.net.URI(it.source.location) }
          .toSet()
      compile(
        api,
        compiler,
        false,
        "XCTAssertEqual(try Survivor(value: \"ok\").value, \"ok\")" +
          if (excluded == null) "\nXCTAssertEqual(try Root.Middle.Leaf(value: \"leaf\").value, \"leaf\")" else "",
        GeneratedOutputSelection(ownerUris = owners),
      )
      if (excluded == "Middle") {
        val source =
          io.outfoxx.sunday.generator.tools.CompiledGeneratedSources.source(
            io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage.Swift,
            "SundayGenTest/Models/Root.swift",
          )
        assertTrue(!source.contains("Middle") && !source.contains("Leaf"))
      }
      if (excluded == "Root") {
        assertThrows(AssertionError::class.java) {
          io.outfoxx.sunday.generator.tools.CompiledGeneratedSources.source(
            io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage.Swift,
            "SundayGenTest/Models/Root.swift",
          )
        }
      }
    }
  }
}
