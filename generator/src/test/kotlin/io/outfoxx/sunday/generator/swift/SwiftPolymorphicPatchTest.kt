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
import io.outfoxx.sunday.generator.swift.sunday.swiftSundayTestOptions
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.polymorphicPatchApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

@SwiftTest
@Tag("models")
@Tag("requests")
class SwiftPolymorphicPatchTest {
  @ParameterizedTest
  @ValueSource(strings = ["openapi", "raml", "asyncapi", "composed"])
  fun `polymorphic patch values store protocols and use references only for coding`(
    frontend: String,
    @TempDir directory: Path,
    compiler: SwiftCompiler,
  ) {
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      polymorphicPatchApi(frontend, directory),
      registry,
      swiftSundayTestOptions,
    ).generateServiceTypes()
    registry.generateFiles(GeneratedOutputSelection(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    compiler.testsDir.resolve("PolymorphicPatchTests.swift").writeText(
      """
      import Foundation
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class PolymorphicPatchTests: XCTestCase {
        func testRoundTripAndMerge() throws {
          let decoder = JSONDecoder()
          let encoder = JSONEncoder()
          let cat: any Animal = try decoder.decode(Cat.self, from: Data(#"{"kind":"cat","name":"Misty"}"#.utf8))
          let dog: any Animal = try decoder.decode(Dog.self, from: Data(#"{"kind":"dog","name":"Rex"}"#.utf8))
          var patch = ZooPatch()
          patch.requiredAnimals = .set([cat])
          patch.favorite = .set(dog)
          patch.animals = .set([cat, dog])
          patch.aliasedAnimals = .set([dog])
          patch.byName = .set(["first": cat, "removed": nil])
          patch.matrix = .set([[cat], [dog]])
          let expected = Data(#"{"requiredAnimals":[{"kind":"cat","name":"Misty"}],"favorite":{"kind":"dog","name":"Rex"},"animals":[{"kind":"cat","name":"Misty"},{"kind":"dog","name":"Rex"}],"aliasedAnimals":[{"kind":"dog","name":"Rex"}],"byName":{"first":{"kind":"cat","name":"Misty"},"removed":null},"matrix":[[{"kind":"cat","name":"Misty"}],[{"kind":"dog","name":"Rex"}]]}"#.utf8)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: expected) as! NSDictionary)
          let decoded = try decoder.decode(ZooPatch.self, from: expected)
          guard case .set(let decodedAnimals) = decoded.animals,
                case .set(let decodedFavorite) = decoded.favorite,
                case .set(let decodedByName) = decoded.byName,
                case .set(let decodedMatrix) = decoded.matrix else {
            return XCTFail("Expected decoded protocol values")
          }
          XCTAssertTrue(decodedAnimals[0] is Cat)
          XCTAssertTrue(decodedAnimals[1] is Dog)
          XCTAssertTrue(decodedFavorite is Dog)
          XCTAssertTrue(decodedByName["first"]! is Cat)
          XCTAssertTrue(decodedMatrix[1][0] is Dog)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(decoded)) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: expected) as! NSDictionary)
          let updated = decoded.withAnimals(animals: .set([dog]))
          guard case .set(let animals) = updated.animals, let animal = animals[0] as? Dog else {
            return XCTFail("Expected a decoded dog")
          }
          XCTAssertEqual(animal.name, "Rex")
          let original = try decoder.decode(Zoo.self, from: Data(#"{"requiredAnimals":[],"byName":{"removed":{"kind":"dog","name":"Old"}}}"#.utf8))
          let merged = try original.merge(decoded)
          let wire = try JSONSerialization.jsonObject(with: encoder.encode(merged)) as! NSDictionary
          var expectedMerge = try JSONSerialization.jsonObject(with: expected) as! [String: Any]
          expectedMerge["byName"] = ["first": ["kind": "cat", "name": "Misty"]]
          expectedMerge["nullableAnimals"] = NSNull()
          XCTAssertEqual(wire, expectedMerge as NSDictionary)
          XCTAssertEqual(original.requiredAnimals.count, 0)
          XCTAssertNotNil(original.byName?["removed"])
        }
        func testAliasesPreserveProtocolValuesAndNestedNulls() throws {
          let decoder = JSONDecoder()
          let encoder = JSONEncoder()
          let cat: any Animal = try decoder.decode(Cat.self, from: Data(#"{"kind":"cat","name":"Misty"}"#.utf8))
          var patch = ZooPatch()
          patch.choice = .set(cat)
          patch.nullableAnimals = .set([cat])
          patch.nestedNullableAnimals = .set([[cat], nil, []])
          let expected = Data(#"{"choice":{"kind":"cat","name":"Misty"},"nullableAnimals":[{"kind":"cat","name":"Misty"}],"nestedNullableAnimals":[[{"kind":"cat","name":"Misty"}],null,[]]}"#.utf8)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: expected) as! NSDictionary)
          let decoded = try decoder.decode(ZooPatch.self, from: expected)
          guard case .set(let choice) = decoded.choice,
                case .set(let animals) = decoded.nullableAnimals,
                case .set(let nested) = decoded.nestedNullableAnimals else {
            return XCTFail("Expected decoded alias values")
          }
          XCTAssertTrue(choice is Cat)
          XCTAssertTrue(animals[0] is Cat)
          XCTAssertTrue(nested[0]?[0] is Cat)
          XCTAssertNil(nested[1])
          XCTAssertEqual(nested[2]?.count, 0)
          XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(decoded)) as! NSDictionary,
                         try JSONSerialization.jsonObject(with: expected) as! NSDictionary)
          for json in ["{}", #"{"choice":null,"nullableAnimals":null}"#, #"{"nullableAnimals":[]}"#] {
            let input = Data(json.utf8)
            let value = try decoder.decode(ZooPatch.self, from: input)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(value)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: input) as! NSDictionary)
          }
          let original = try decoder.decode(Zoo.self, from: Data(#"{"requiredAnimals":[]}"#.utf8))
          let merged = try original.merge(decoded)
          let mergedJSON = try JSONSerialization.jsonObject(with: encoder.encode(merged)) as! [String: Any]
          var expectedMerged = try JSONSerialization.jsonObject(with: expected) as! [String: Any]
          expectedMerged["requiredAnimals"] = []
          XCTAssertEqual(mergedJSON as NSDictionary, expectedMerged as NSDictionary)
          var deletion = ZooPatch()
          deletion.choice = .delete
          deletion.nullableAnimals = .delete
          let deleted = try merged.merge(deletion)
          XCTAssertNil(deleted.choice)
          XCTAssertNil(deleted.nullableAnimals)
          for json in [#"{"choice":{"kind":"unknown","name":"Invalid"}}"#,
                       #"{"nullableAnimals":[{"kind":"cat"}]}"#,
                       #"{"nestedNullableAnimals":[[{"kind":"unknown","name":"Invalid"}]]}"#] {
            XCTAssertThrowsError(try decoder.decode(ZooPatch.self, from: Data(json.utf8)))
          }
        }
        func testPresenceDeletionAndValidation() throws {
          let decoder = JSONDecoder()
          let encoder = JSONEncoder()
          for json in ["{}", #"{"animals":null,"favorite":null}"#, #"{"animals":[],"requiredAnimals":[]}"#] {
            let input = Data(json.utf8)
            let patch = try decoder.decode(ZooPatch.self, from: input)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: encoder.encode(patch)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: input) as! NSDictionary)
          }
          let original = try decoder.decode(Zoo.self, from: Data(#"{"requiredAnimals":[],"animals":[{"kind":"cat","name":"Misty"}]}"#.utf8))
          var deletion = ZooPatch()
          deletion.animals = .delete
          XCTAssertNil(try original.merge(deletion).animals)
          for json in [#"{"requiredAnimals":null}"#, #"{"animals":[{"kind":"unknown","name":"Invalid"}]}"#,
                       #"{"animals":[{"kind":"cat"}]}"#, #"{"favorite":{"kind":"dog"}}"#,
                       #"{"byName":{"bad":{"kind":"dog"}}}"#] {
            XCTAssertThrowsError(try decoder.decode(ZooPatch.self, from: Data(json.utf8)))
          }
        }
      }
      """.trimIndent(),
    )
    val patchPath =
      Files.walk(compiler.srcDir).use { paths ->
        val path = paths.filter { it.fileName.toString() == "ZooPatch.swift" }.findFirst().orElseThrow()
        compiler.srcDir.relativize(path).toString()
      }
    assertTrue(compileAndTestGeneratedFiles(compiler))
    val patch = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, patchPath)
    assertTrue(patch.contains("var animals: PatchOp<[any Animal]>"), patch)
    assertTrue(patch.contains("var requiredAnimals: UpdateOp<[any Animal]>"), patch)
    assertTrue(patch.contains("var favorite: PatchOp<any Animal>"), patch)
    assertTrue(patch.contains("var choice: PatchOp<any Animal>"), patch)
    assertTrue(patch.contains("var nullableAnimals: PatchOp<[any Animal]>"), patch)
    assertTrue(patch.contains("var byName: PatchOp<[String : (any Animal)?]>"), patch)
  }
}
