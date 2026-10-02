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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.arrayMultiplesFixture
import io.outfoxx.sunday.generator.tools.inheritedConstraintsFixture
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Numeric Test")
@Tag("validation")
class SwiftSundayIrNumericTest : SwiftSundayIrTestSupport() {

  @Test
  fun `multiples preserve Double range without rounding Decimal supported values`(compiler: SwiftCompiler) {
    val number = GeneratedTypeRef.named("WideNumber")
    val array = GeneratedTypeRef(GeneratedTypeRef.Kind.ARRAY, "array", arguments = listOf(number.copy(nullable = true)))

    fun model(
      name: String,
      type: GeneratedTypeRef = number,
      validation: Map<String, String> = mapOf("multipleOf" to "1"),
    ) = GeneratedModel(
      name,
      GeneratedModel.Kind.OBJECT,
      properties = listOf(GeneratedModelProperty("value", type, validation = validation)),
    )
    val base = model("WideBase", validation = emptyMap())
    val api =
      inheritedConstraintsFixture().copy(
        models =
          listOf(
            GeneratedModel(
              "WideNumber",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("number")),
            ),
            GeneratedModel("WideNumbers", GeneratedModel.Kind.ARRAY, aliases = array.arguments),
            GeneratedModel(
              "WideSet",
              GeneratedModel.Kind.ARRAY,
              aliases = listOf(number),
              collection = io.outfoxx.sunday.generator.ir.GeneratedCollectionKind.SET,
            ),
            base,
            model(
              "Wide",
              validation = mapOf("multipleOf" to "1"),
            ).copy(inherits = listOf(GeneratedTypeRef.named("WideBase"))),
            model("WideThrees", validation = mapOf("multipleOf" to "3")),
            model("WideFraction", validation = mapOf("multipleOf" to "0.1")),
            model("WidePositive", validation = mapOf("multipleOf" to "1", "minimum" to "0")),
            model("WideUpper", validation = mapOf("multipleOf" to "1", "maximum" to "1e100")),
            model("WideLower", validation = mapOf("multipleOf" to "1", "minimum" to "-1e100")),
            model("WideArray", GeneratedTypeRef.named("WideNumbers"), mapOf("multipleOf" to "0.1")),
            model("WideUnique", array, mapOf("multipleOf" to "1", "uniqueItems" to "true")),
            model("WideSetHolder", GeneratedTypeRef.named("WideSet")),
            model("WidePatch", array.copy(nullable = true)).copy(patchable = true),
          ),
      )
    generateSwiftSundayFiles(compiler, api)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("WideNumberTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class WideNumberTests: XCTestCase {
        func testRange() throws {
          let decoder = JSONDecoder()
          func decode<T: Decodable>(_ type: T.Type, _ value: String) throws -> T {
            try decoder.decode(type, from: Data("{\"value\":\(value)}".utf8))
          }
          for literal in ["1e200", "-1e200", "1.7976931348623157e308", "-1.7976931348623157e308", "0", "-0.0"] {
            let decoded = try decode(Wide.self, literal)
            XCTAssertEqual(decoded.value, Double(literal))
            XCTAssertEqual(try decoder.decode(Wide.self, from: JSONEncoder().encode(decoded)).value, decoded.value)
            let constructed = try Wide(value: Double(literal)!)
            XCTAssertEqual(try decoder.decode(Wide.self, from: JSONEncoder().encode(constructed)).value, constructed.value)
          }
          _ = try decode(WideThrees.self, "3e200")
          _ = try decode(WideThrees.self, "-3e200")
          for literal in ["1e200", "-1e200", "1e-129", "5e-324"] {
            XCTAssertThrowsError(try decode(WideThrees.self, literal))
          }
          for literal in ["1e-129", "5e-324", "1e309"] { XCTAssertThrowsError(try decode(Wide.self, literal)) }
          _ = try decode(WidePositive.self, "1e200")
          _ = try decode(WidePositive.self, "0")
          XCTAssertThrowsError(try decode(WidePositive.self, "-1e200"))
          XCTAssertThrowsError(try decode(WideUpper.self, "1e200"))
          XCTAssertThrowsError(try decode(WideLower.self, "-1e200"))
          _ = try decode(WideUpper.self, "-1e200")
          _ = try decode(WideLower.self, "1e200")
          _ = try decode(WideFraction.self, "0.3")
          XCTAssertThrowsError(try decode(WideFraction.self, "0.30000000000000000000000000000000000001"))
          let mixed = try decode(WideArray.self, "[1e200,0.3,null,-1e200]")
          XCTAssertEqual(mixed.value, [1e200,0.3,nil,-1e200])
          XCTAssertEqual(try decoder.decode(WideArray.self, from: JSONEncoder().encode(mixed)).value, mixed.value)
          _ = try decode(WideArray.self, "[]")
          XCTAssertThrowsError(try decode(WideArray.self, "[1e200,0.30000000000000000000000000000000000001]"))
          _ = try decode(WideUnique.self, "[1e200,-1e200,1]")
          for array in ["[1e200,1e200]", "[1,1.0]", "[0,-0.0]", "[1e200,10e199]"] {
            XCTAssertThrowsError(try decode(WideUnique.self, array))
          }
          let set = try decode(WideSetHolder.self, "[1e200,-1e200]")
          XCTAssertEqual(try decoder.decode(WideSetHolder.self, from: JSONEncoder().encode(set)).value, set.value)
          for literal in ["null", "[]", "[1e200,null,-1e200]"] { _ = try decode(WidePatch.self, literal) }
          _ = try decoder.decode(WidePatch.self, from: Data("{}".utf8))
          _ = try decoder.decode(Wide.self, from: Data("{}".utf8))
          XCTAssertThrowsError(try decode(WidePatch.self, "[1e200,0.3]"))
          decoder.nonConformingFloatDecodingStrategy = .convertFromString(positiveInfinity: "inf", negativeInfinity: "-inf", nan: "nan")
          for literal in ["\"inf\"", "\"-inf\"", "\"nan\""] { XCTAssertThrowsError(try decode(Wide.self, literal)) }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `array multiples validate decimal elements and preserve collection decoding`(compiler: SwiftCompiler) {
    generateSwiftSundayFiles(compiler, arrayMultiplesFixture())
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ArrayMultipleTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class ArrayMultipleTests: XCTestCase {
        func testArrays() throws {
          let decoder = JSONDecoder()
          func check<T: Codable>(_ type: T.Type) throws {
            for array in ["[]", "[2,4]", "[0,-2]"] {
              let data = Data("{\"values\":\(array)}".utf8)
              let decoded = try decoder.decode(type, from: data)
              let encoded = try JSONEncoder().encode(decoded)
              XCTAssertEqual(try JSONSerialization.jsonObject(with: encoded) as? NSDictionary,
                             try JSONSerialization.jsonObject(with: data) as? NSDictionary)
            }
            XCTAssertThrowsError(try decoder.decode(type, from: Data(#"{"values":[2,3]}"#.utf8)))
          }
          try check(ArrayMultiples.self)
          try check(AliasedMultiples.self)
          try check(NullableMultiples.self)
          try check(ArrayChild.self)
          for json in [#"{"values":[]}"#, #"{"values":[2,4]}"#] {
            let decoded = try decoder.decode(SetMultiples.self, from: Data(json.utf8))
            XCTAssertEqual(try decoder.decode(SetMultiples.self, from: JSONEncoder().encode(decoded)).values, decoded.values)
          }
          XCTAssertThrowsError(try decoder.decode(SetMultiples.self, from: Data(#"{"values":[3]}"#.utf8)))
          _ = try decoder.decode(NullableMultiples.self, from: Data(#"{"values":[null,2]}"#.utf8))
          _ = try decoder.decode(AliasedMultiples.self, from: Data("{}".utf8))
          for number in [-4,6] {
            XCTAssertThrowsError(try decoder.decode(ArrayChild.self, from: Data("{\"values\":[\(number)]}".utf8)))
          }
          _ = try decoder.decode(FractionMultiples.self, from: Data(#"{"values":[0,-0.25,0.5]}"#.utf8))
          XCTAssertThrowsError(try decoder.decode(FractionMultiples.self, from: Data(#"{"values":[0.3]}"#.utf8)))
          _ = try decoder.decode(SizedMultiples.self, from: Data(#"{"values":[2,4]}"#.utf8))
          for array in ["[]", "[2,2]", "[2,4,6]"] {
            XCTAssertThrowsError(try decoder.decode(SizedMultiples.self, from: Data("{\"values\":\(array)}".utf8)))
          }
          for json in ["{}", #"{"values":null}"#, #"{"values":[]}"#, #"{"values":[2,4]}"#] {
            _ = try decoder.decode(ArrayPatch.self, from: Data(json.utf8))
          }
          XCTAssertThrowsError(try decoder.decode(ArrayPatch.self, from: Data(#"{"values":[3]}"#.utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `all parent constraints and decimal multiples survive decoding`(compiler: SwiftCompiler) {
    val fixture = inheritedConstraintsFixture()
    val tiny =
      GeneratedModel(
        "TinyMultiple",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(
            GeneratedModelProperty(
              "value",
              GeneratedTypeRef.scalar("number"),
              validation = mapOf("multipleOf" to "1e-128"),
            ),
          ),
      )
    generateSwiftSundayFiles(compiler, fixture.copy(models = fixture.models + tiny))
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("MultipleTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class MultipleTests: XCTestCase {
        func testConstraints() throws {
          let decoder = JSONDecoder()
          func check<T: Codable>(_ type: T.Type) throws {
            for number in [0, 2, -2, 6] {
              let data = Data("{\"text\":\"abc\",\"count\":\(number),\"amount\":0.3}".utf8)
              let decoded = try decoder.decode(type, from: data)
              let encoded = try JSONEncoder().encode(decoded)
              XCTAssertEqual(try JSONSerialization.jsonObject(with: encoded) as? NSDictionary,
                             try JSONSerialization.jsonObject(with: data) as? NSDictionary)
            }
            for json in [#"{"text":"a"}"#, #"{"text":"abcd"}"#, #"{"count":3}"#,
                         #"{"count":-3}"#, #"{"amount":0.31}"#, #"{"amount":0.30000000000000000000000000000000000001}"#] {
              XCTAssertThrowsError(try decoder.decode(type, from: Data(json.utf8)))
            }
            _ = try decoder.decode(type, from: Data("{}".utf8))
          }
          try check(Child.self)
          try check(Reversed.self)
          for json in [#"{"value":1e127}"#, #"{"value":1e-128}"#, #"{"value":-1e127}"#] {
            _ = try decoder.decode(TinyMultiple.self, from: Data(json.utf8))
          }
          for json in ["{}", #"{"count":null}"#, #"{"count":2}"#] {
            _ = try decoder.decode(MultiplePatch.self, from: Data(json.utf8))
          }
          XCTAssertThrowsError(try decoder.decode(MultiplePatch.self, from: Data(#"{"count":3}"#.utf8)))
          _ = try MultiplePatch()
          _ = try MultiplePatch(count: .delete)
          _ = try MultiplePatch(count: .set(2))
          XCTAssertThrowsError(try MultiplePatch(count: .set(3)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
