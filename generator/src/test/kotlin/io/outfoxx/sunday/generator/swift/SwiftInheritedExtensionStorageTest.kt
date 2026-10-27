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
import io.outfoxx.sunday.generator.UnknownFieldPreservation
import io.outfoxx.sunday.generator.ir.GeneratedAdditionalProperties
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedPatternProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files

@SwiftTest
@Tag("models")
class SwiftInheritedExtensionStorageTest {
  @ParameterizedTest
  @EnumSource(UnknownFieldPreservation::class)
  fun `recursive class storage follows concrete child retention rules`(
    mode: UnknownFieldPreservation,
    compiler: SwiftCompiler,
  ) {
    val source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory")

    fun recursiveModel(
      name: String,
      parent: String? = null,
      pattern: String,
    ): GeneratedModel =
      GeneratedModel(
        name,
        GeneratedModel.Kind.OBJECT,
        source = source,
        properties = listOf(GeneratedModelProperty("next$name", GeneratedTypeRef.named(name), required = false)),
        inherits = listOfNotNull(parent?.let(GeneratedTypeRef::named)),
        patternProperties = listOf(GeneratedPatternProperty(pattern, GeneratedTypeRef.scalar("string"))),
      )
    val base = recursiveModel("RecursiveBase", pattern = "^x-")
    val child = recursiveModel("RecursiveChild", base.name, "^y-")
    val open =
      recursiveModel("RecursiveOpenChild", base.name, "^y-").copy(
        additionalProperties = GeneratedAdditionalProperties(allowed = true),
      )
    val leaf = recursiveModel("RecursiveLeaf", child.name, "^z-")
    val models = listOf(base, child, open, leaf)
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      GeneratedApi(name = "Recursive storage", source = source, models = models),
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API", preserveUnknownFields = mode),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(setOf(GeneratedTypeCategory.Model)), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("InheritedStorageTests.swift"),
      """
      import Foundation
      import XCTest
      import PotentCodables
      @testable import SundayGenTest

      final class InheritedStorageTests: XCTestCase {
        func testConcreteRetention() throws {
          ${models.joinToString("\n") { model ->
        val patterns =
          when (model) {
            base -> listOf("x-label")
            leaf -> listOf("x-label", "y-label", "z-label")
            else -> listOf("x-label", "y-label")
          }
        val retain = mode == UnknownFieldPreservation.On || (model == open && mode == UnknownFieldPreservation.Schema)
        val fields = patterns.joinToString { "\"$it\": \"ok\"" }
        """
        do {
          let raw = Data(#"{$fields, "other": 123}"#.utf8)
          let decoded = try JSONDecoder().decode(${model.name}.self, from: raw)
          let constructed = try ${model.name}(additionalProperties: [$fields, "other": 123])
          for value: RecursiveBase in [decoded, constructed] {
            ${patterns.joinToString("\n") { "XCTAssertEqual(value.additionalProperties[\"$it\"], .string(\"ok\"))" }}
            XCTAssertEqual(value.additionalProperties["other"] != nil, $retain)
            let output = try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) as! [String: Any]
            ${patterns.joinToString("\n") { "XCTAssertEqual(output[\"$it\"] as? String, \"ok\")" }}
            XCTAssertEqual(output["other"] != nil, $retain)
          }
        }
        """.trimIndent()
      }}
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    models.forEach { model ->
      val generated = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/${model.name}.swift")
      assertTrue(generated.contains("class ${model.name}"))
    }
  }
}
