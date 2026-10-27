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
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.discriminatedPatchApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("models")
@Tag("validation")
@Tag("requests")
class SwiftDiscriminatedPatchTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "explicit", "raml", "asyncapi", "composed", "renamed", "tolerant"])
  fun `discriminated patches retain required tags`(
    frontend: String,
    @TempDir directory: Path,
    compiler: SwiftCompiler,
  ) {
    val api = discriminatedPatchApi(frontend, directory)
    val tag = if (frontend == "renamed") "animal-kind" else "kind"
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(api, registry, swiftSundayTestOptions).generateServiceTypes()
    registry.generateFiles(
      GeneratedOutputSelection(GeneratedTypeCategory.entries.toSet()),
      compiler.srcDir,
    )
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("PatchTests.swift"),
      """
      import Foundation
      import Sunday
      import Testing
      import SundayGenTest
      @Test func patchTags() throws {
        let decoder = JSONDecoder()
        let encoder = JSONEncoder()
        for json in [#"{"$tag":"cat"}"#, #"{"$tag":"cat","nickname":null}"#, #"{"$tag":"cat","name":"Misty"}"#, #"{"$tag":"cat","custom":"extension"}"#] {
          let data = Data(json.utf8)
          let wrapped = try decoder.decode(AnimalPatchRef.self, from: data)
          guard case .cat(let patch) = wrapped else { Issue.record("incorrect subtype"); return }
          #expect(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as? NSDictionary == JSONSerialization.jsonObject(with: data) as? NSDictionary)
        }
        let base = try decoder.decode(Cat.self, from: Data(#"{"$tag":"cat","name":"Misty","detail":null,"nickname":"Kit","indoor":true}"#.utf8))
        #expect(throws: (any Error).self) { try base.patch() }
        let complete = try decoder.decode(Cat.self, from: Data(#"{"$tag":"cat","name":"Misty","detail":"info"}"#.utf8))
        let snapshot = try CatPatch(complete)
        #expect(try JSONSerialization.jsonObject(with: encoder.encode(snapshot)) as? NSDictionary == JSONSerialization.jsonObject(with: encoder.encode(complete)) as? NSDictionary)
        let unchanged = try base.patch(from: base)
        #expect(try JSONSerialization.jsonObject(with: encoder.encode(unchanged)) as? NSDictionary == ["$tag": "cat"] as NSDictionary)
        let merged = try base.merge(decoder.decode(CatPatch.self, from: Data(#"{"$tag":"cat","nickname":null}"#.utf8)))
        #expect(merged.name == "Misty")
        #expect(merged.nickname == nil)
        #expect(merged.indoor == true)
        let typedMerge: (CatPatch) throws -> Cat = base.merge
        _ = try typedMerge(unchanged)
        for json in [#"{"$tag":"cat","trained":true}"#, #"{"$tag":"cat","trained":null}"#] {
            let patch = try decoder.decode(CatPatch.self, from: Data(json.utf8))
            #expect(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as? NSDictionary == JSONSerialization.jsonObject(with: Data(json.utf8)) as? NSDictionary)
        }
        for json in ["{}", #"{"$tag":null}"#, #"{"$tag":"lizard"}"#, #"{"$tag":"cat","name":null}"#, #"{"$tag":"cat","detail":null}"#] {
          #expect(throws: (any Error).self) { try decoder.decode(CatPatch.self, from: Data(json.utf8)) }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
