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
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.test.extensions.ResourceUri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("responses")
class SwiftMergePatchProblemDefaultsTest {
  @Test
  fun `merge patch preserves nonoptional problem defaults and optional field deletion`(
    @ResourceUri("openapi/ir/merge-patch-problem-defaults.yaml") source: URI,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      GeneratedApiIrExporter().export(source),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("MergePatchProblemDefaultsTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class MergePatchProblemDefaultsTests: XCTestCase {
        func testProblemDefaultsAndConstants() throws {
          for merging in [false, true] {
            let decoder = JSONDecoder()
            decoder.userInfo[CodingUserInfoKey(rawValue: "io.outfoxx.sunday.mergePatch")!] = merging
            let missingType = Data(#"{"title":"Not Found","status":404}"#.utf8)
            let notFound = try decoder.decode(RecordNotFoundProblem.self, from: missingType)
            XCTAssertEqual(notFound.type.absoluteString, "about:blank")
            XCTAssertEqual(notFound.title, "Not Found")
            XCTAssertEqual(notFound.status, 404)
            XCTAssertThrowsError(try decoder.decode(RecordNotFoundProblem.self, from: Data("{}".utf8)))
            let defaulted = try decoder.decode(DefaultedProblem.self, from: Data("{}".utf8))
            XCTAssertEqual(defaulted.type.absoluteString, "about:blank")
            XCTAssertEqual(defaulted.title, "Bad Request")
            XCTAssertEqual(defaulted.status, 400)
            XCTAssertEqual(defaulted.detail, "Details")
            XCTAssertEqual(notFound.detail, "Details")
            let unrelated = try decoder.decode(UnrelatedRecord.self, from: Data("{}".utf8))
            XCTAssertEqual(unrelated.label, "unrelated")
            XCTAssertEqual(unrelated.count, 7)
            let child = try decoder.decode(UnrelatedChild.self, from: Data("{}".utf8))
            XCTAssertEqual(child.localLabel, "unrelated")
            for (field, invalid) in [("type", "https://example.com/wrong" as Any),
                                      ("title", "Wrong"), ("status", 500)] {
              for value in [invalid, NSNull()] {
                var record: [String: Any] = ["title": "Not Found", "status": 404]
                record[field] = value
                let recordData = try JSONSerialization.data(withJSONObject: record)
                XCTAssertThrowsError(try decoder.decode(RecordNotFoundProblem.self, from: recordData))
                let defaultedData = try JSONSerialization.data(withJSONObject: [field: value])
                XCTAssertThrowsError(try decoder.decode(DefaultedProblem.self, from: defaultedData))
              }
            }
          }
        }
        func testReachableModelsWithoutPatchCompanions() throws {
          let decoder = JSONDecoder()
          let json = Data(#"{"items":[{"variant":{"kind":"named"}}]}"#.utf8)
          let ordinary = try decoder.decode(Settings.self, from: json)
          XCTAssertEqual(ordinary.items?.first?.label, "item")
          XCTAssertEqual(ordinary.items?.first?.baseLabel, "base")
          decoder.userInfo[CodingUserInfoKey(rawValue: "io.outfoxx.sunday.mergePatch")!] = true
          let merged = try decoder.decode(Settings.self, from: json)
          XCTAssertNotNil(merged.items?.first)
          XCTAssertNil(merged.items?.first?.label)
          XCTAssertNil(merged.items?.first?.baseLabel)
          let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(merged)) as! NSDictionary
          XCTAssertEqual(encoded["items"] as? NSArray, [["variant": ["kind": "named"]]] as NSArray)
        }
        func testMergePatchOmissionAndDeletion() throws {
          let decoder = JSONDecoder()
          let original = try decoder.decode(Settings.self, from: Data(#"{"nested":{}}"#.utf8))
          XCTAssertEqual(original.label, "initial")
          XCTAssertEqual(original.count, 3)
          XCTAssertEqual(original.nested?.label, "nested")
          let unchanged = try original.merge(decoder.decode(SettingsPatch.self, from: Data("{}".utf8)))
          XCTAssertEqual(unchanged.label, "initial")
          XCTAssertEqual(unchanged.count, 3)
          let patch = try decoder.decode(SettingsPatch.self,
            from: Data(#"{"label":null,"count":null,"nested":{"label":null}}"#.utf8))
          let deleted = try original.merge(patch)
          XCTAssertNil(deleted.label)
          XCTAssertNil(deleted.count)
          XCTAssertNotNil(deleted.nested)
          XCTAssertNil(deleted.nested?.label)
          XCTAssertEqual(original.label, "initial")
          XCTAssertEqual(original.nested?.label, "nested")
        }
      }
      """.trimIndent(),
    )
    val sourcePaths =
      Files.walk(compiler.srcDir).use { paths ->
        paths.filter(Files::isRegularFile).toList().associate { path ->
          path.fileName.toString() to compiler.srcDir.relativize(path).toString()
        }
      }
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val mergeContext = "io.outfoxx.sunday.mergePatch"
    listOf("RecordNotFoundProblem", "DefaultedProblem", "UnrelatedRecord", "UnrelatedChild").forEach { name ->
      val compiled = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, sourcePaths.getValue("$name.swift"))
      assertFalse(compiled.contains(mergeContext), compiled)
    }
    listOf("Settings", "NestedSettings", "Item", "NamedVariant").forEach { name ->
      val compiled = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, sourcePaths.getValue("$name.swift"))
      assertTrue(compiled.contains(mergeContext), compiled)
    }
    assertFalse(sourcePaths.containsKey("ItemPatch.swift"))
    assertFalse(sourcePaths.containsKey("NamedVariantPatch.swift"))
  }
}
