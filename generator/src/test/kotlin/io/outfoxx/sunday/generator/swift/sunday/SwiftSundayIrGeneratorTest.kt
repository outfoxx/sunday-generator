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

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.AsyncApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedApiIrExporter
import io.outfoxx.sunday.generator.ir.GeneratedApiIrOptions
import io.outfoxx.sunday.generator.ir.GeneratedApiYaml
import io.outfoxx.sunday.generator.ir.GeneratedCollectionKind
import io.outfoxx.sunday.generator.ir.GeneratedDocumentation
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedNestedType
import io.outfoxx.sunday.generator.ir.GeneratedOperation
import io.outfoxx.sunday.generator.ir.GeneratedParameter
import io.outfoxx.sunday.generator.ir.GeneratedPayload
import io.outfoxx.sunday.generator.ir.GeneratedResponse
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTarget
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiReferenceOptions
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.AssociatedExtensions
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftSundayOptions
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileGeneratedFiles
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateSunday
import io.outfoxx.sunday.generator.swift.tools.reusableDiscriminatorMappingApi
import io.outfoxx.sunday.generator.swift.tools.reusableDiscriminatorMappingRuntimeTest
import io.outfoxx.sunday.generator.tools.CompiledGeneratedSources
import io.outfoxx.sunday.generator.tools.GeneratedCodeLanguage
import io.outfoxx.sunday.generator.tools.OpenApiHttpFixture
import io.outfoxx.sunday.generator.tools.OpenApiReferenceDocuments
import io.outfoxx.sunday.generator.tools.arrayMultiplesFixture
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.generator.tools.inheritedConstraintsFixture
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import io.outfoxx.swiftpoet.tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Generator Test")
class SwiftSundayIrGeneratorTest {

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
            let constructed = Wide(value: Double(literal)!)
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
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `integer defaults preserve exact values through aliases and inheritance`(compiler: SwiftCompiler) {
    val count = GeneratedModelProperty("count", GeneratedTypeRef.named("IntegerAlias"), defaultValue = "1.0")
    val defaults =
      GeneratedModel(
        "IntegerDefaults",
        GeneratedModel.Kind.OBJECT,
        properties =
          listOf(count) +
            listOf(
              "exponent" to "1e3",
              "negativeZero" to "-0.0",
              "maximum" to "9223372036854775807.0",
              "minimum" to "-9223372036854775808.0",
            ).map { (name, value) ->
              GeneratedModelProperty(name, GeneratedTypeRef.scalar("integer"), defaultValue = value)
            } +
            listOf(
              GeneratedModelProperty("fraction", GeneratedTypeRef.scalar("number"), defaultValue = "1.25"),
              GeneratedModelProperty(
                "nullable",
                GeneratedTypeRef.scalar("integer", nullable = true),
                defaultValue = "2.0",
              ),
            ),
      )
    val child =
      GeneratedModel(
        "RefinedIntegerDefaults",
        GeneratedModel.Kind.OBJECT,
        inherits = listOf(GeneratedTypeRef.named("IntegerDefaults")),
        properties =
          listOf(
            count.copy(defaultValue = "2.0", allowedValues = listOf(2), validation = mapOf("minimum" to "2")),
          ),
      )
    val api =
      GeneratedApi(
        name = "Integer defaults",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            child,
            child.copy(name = "RequiredIntegerDefaults", properties = listOf(count.copy(required = true))),
            GeneratedModel(
              "IntegerPatch",
              GeneratedModel.Kind.OBJECT,
              properties = listOf(count, defaults.properties.last()),
              patchable = true,
            ),
            defaults,
            GeneratedModel(
              "IntegerAlias",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("IntegerValue")),
            ),
            GeneratedModel(
              "IntegerValue",
              GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("integer")),
            ),
          ),
      )
    generateSwiftSundayFiles(compiler, api)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("IntegerDefaultsTests.swift"),
      """
      import Foundation
      import Sunday
      import XCTest
      @testable import SundayGenTest

      final class IntegerDefaultsTests: XCTestCase {
        func testExactDefaults() throws {
          let decoder = JSONDecoder()
          for value in [IntegerDefaults(), try decoder.decode(IntegerDefaults.self, from: Data("{}".utf8))] {
            XCTAssertEqual(value.count, 1)
            XCTAssertEqual(value.exponent, 1000)
            XCTAssertEqual(value.negativeZero, 0)
            XCTAssertEqual(value.maximum, Int.max)
            XCTAssertEqual(value.minimum, Int.min)
            XCTAssertEqual(value.fraction, 1.25)
            XCTAssertEqual(value.nullable, 2)
            let roundTrip = try decoder.decode(IntegerDefaults.self, from: JSONEncoder().encode(value))
            XCTAssertEqual(roundTrip.maximum, Int.max)
            XCTAssertEqual(roundTrip.minimum, Int.min)
          }
          for value in [RefinedIntegerDefaults(), try decoder.decode(RefinedIntegerDefaults.self, from: Data("{}".utf8))] {
            let parent = IntegerDefaults(count: value.count)
            XCTAssertEqual(parent.count, 2)
          }
          XCTAssertEqual(IntegerDefaults().count, 1)
          XCTAssertThrowsError(try decoder.decode(RefinedIntegerDefaults.self, from: Data(#"{"count":1}"#.utf8)))
          let null = try decoder.decode(IntegerDefaults.self, from: Data(#"{"count":null,"nullable":null}"#.utf8))
          XCTAssertNil(null.count)
          XCTAssertNil(null.nullable)
          XCTAssertNil(RequiredIntegerDefaults().count)
          XCTAssertThrowsError(try decoder.decode(RequiredIntegerDefaults.self, from: Data("{}".utf8)))
          for value in [IntegerPatch(), try decoder.decode(IntegerPatch.self, from: Data("{}".utf8))] {
            XCTAssertNil(value.count)
            XCTAssertNil(value.nullable)
            XCTAssertEqual(String(data: try JSONEncoder().encode(value), encoding: .utf8), "{}")
          }
          let deleted = try decoder.decode(IntegerPatch.self, from: Data(#"{"nullable":null}"#.utf8))
          guard case .delete? = deleted.nullable else { return XCTFail("null must remain a delete") }
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `invalid integer defaults report the wire property and literal`() {
    val integer =
      GeneratedModel(
        "IntegerValue",
        GeneratedModel.Kind.SCALAR_ALIAS,
        aliases = listOf(GeneratedTypeRef.scalar("integer")),
      )
    for (literal in listOf(
      "1.1",
      "1e-1",
      "NaN",
      "Infinity",
      "bad",
      "9223372036854775808",
      "-9223372036854775809.0",
      "1e1000",
    )) {
      val property =
        GeneratedModelProperty(
          "count",
          GeneratedTypeRef.named("IntegerValue"),
          serializationName = "wire-count",
          defaultValue = literal,
        )
      val api =
        GeneratedApi(
          name = "Invalid defaults",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          models = listOf(GeneratedModel("Counts", GeneratedModel.Kind.OBJECT, properties = listOf(property)), integer),
        )
      val error =
        assertThrows(GenerationException::class.java) {
          SwiftSundayIrGenerator(api, SwiftTypeRegistry(setOf()), swiftSundayTestOptions).generateServiceTypes()
        }
      assertTrue(error.message.orEmpty().contains("Counts.wire-count"), error.message)
      assertTrue(error.message.orEmpty().contains(literal), error.message)
      assertTrue(error.message.orEmpty().contains("integer"), error.message)
    }
  }

  @Test
  fun `exclusive bounds and validated defaults preserve inherited storage`(compiler: SwiftCompiler) {
    val property = GeneratedModelProperty("count", GeneratedTypeRef.scalar("integer"))
    val base = GeneratedModel("BoundBase", GeneratedModel.Kind.OBJECT, properties = listOf(property))

    fun child(
      name: String,
      validation: Map<String, String>,
      default: String? = "2",
    ) = GeneratedModel(
      name,
      GeneratedModel.Kind.OBJECT,
      inherits = listOf(GeneratedTypeRef.named("BoundBase")),
      properties = listOf(property.copy(validation = validation, defaultValue = default)),
    )
    val booleanBounds =
      child(
        "BooleanBounds",
        mapOf(
          "minimum" to "1",
          "exclusiveMinimum" to "true",
          "maximum" to "3",
          "exclusiveMaximum" to "true",
        ),
      )
    val numericBounds = child("NumericBounds", mapOf("exclusiveMinimum" to "1", "exclusiveMaximum" to "3"))
    val disabledBounds =
      child("DisabledBounds", mapOf("exclusiveMinimum" to "false", "exclusiveMaximum" to "false"), "0")
    val api =
      GeneratedApi(
        name = "Bounds",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models = listOf(base, booleanBounds, numericBounds, disabledBounds),
      )
    generateSwiftSundayFiles(compiler, api)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("BoundTests.swift"),
      """
      import Foundation
      import XCTest
      @testable import SundayGenTest
      final class BoundTests: XCTestCase {
        func testBounds() throws {
          let decoder = JSONDecoder()
          XCTAssertEqual(BooleanBounds().count, 2)
          XCTAssertEqual(NumericBounds().count, 2)
          XCTAssertEqual(try decoder.decode(BooleanBounds.self, from: Data("{}".utf8)).count, 2)
          XCTAssertEqual(try decoder.decode(NumericBounds.self, from: Data("{}".utf8)).count, 2)
          XCTAssertEqual(try decoder.decode(DisabledBounds.self, from: Data("{}".utf8)).count, 0)
          for value in [1, 3, 0, 4] {
            let data = try JSONSerialization.data(withJSONObject: ["count": value])
            XCTAssertThrowsError(try decoder.decode(BooleanBounds.self, from: data))
            XCTAssertThrowsError(try decoder.decode(NumericBounds.self, from: data))
            XCTAssertEqual(try decoder.decode(DisabledBounds.self, from: data).count, value)
          }
          let data = Data(#"{"count":2}"#.utf8)
          XCTAssertEqual(try decoder.decode(BooleanBounds.self, from: data).count, 2)
          XCTAssertEqual(try decoder.decode(NumericBounds.self, from: data).count, 2)
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `invalid effective defaults and unrepresentable bounds fail generation`() {
    val count = GeneratedModelProperty("count", GeneratedTypeRef.scalar("integer"))
    val parent = GeneratedModel("Parent", GeneratedModel.Kind.OBJECT, properties = listOf(count))
    val invalid =
      listOf(
        count.copy(defaultValue = "0", validation = mapOf("minimum" to "1")),
        count.copy(defaultValue = "1", validation = mapOf("minimum" to "1", "exclusiveMinimum" to "true")),
        count.copy(defaultValue = "3", validation = mapOf("maximum" to "3", "exclusiveMaximum" to "true")),
        count.copy(defaultValue = "1", allowedValues = listOf(2)),
        count.copy(
          type = GeneratedTypeRef.scalar("date"),
          defaultValue = "2026-01-01",
          allowedValues = listOf("2027-01-01"),
        ),
        count.copy(defaultValue = "0", allowedValues = listOf(false)),
        count.copy(defaultValue = "1", validation = mapOf("multipleOf" to "2")),
        count.copy(validation = mapOf("exclusiveMinimum" to "true")),
        count.copy(validation = mapOf("exclusiveMaximum" to "bad")),
        count.copy(validation = mapOf("minimum" to "1e1000")),
        count.copy(
          type = GeneratedTypeRef.scalar("string"),
          defaultValue = "bad",
          validation = mapOf("minLength" to "4"),
        ),
        count.copy(
          type = GeneratedTypeRef.scalar("string"),
          defaultValue = "bad",
          validation = mapOf("maxLength" to "2"),
        ),
        count.copy(
          type = GeneratedTypeRef.scalar("string"),
          defaultValue = "bad",
          validation =
            mapOf(
              "pattern" to "^good$",
            ),
        ),
      )
    for (property in invalid) {
      val child =
        GeneratedModel(
          "Child",
          GeneratedModel.Kind.OBJECT,
          inherits = listOf(GeneratedTypeRef.named("Parent")),
          properties = listOf(property),
        )
      val api =
        GeneratedApi(
          name = "Defaults",
          source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
          models = listOf(parent, child),
        )
      val error =
        assertThrows(GenerationException::class.java) {
          SwiftSundayIrGenerator(api, SwiftTypeRegistry(setOf()), swiftSundayTestOptions).generateServiceTypes()
        }
      assertTrue(error.message.orEmpty().contains("count"), error.message)
      if (property.defaultValue != null) assertTrue(error.message.orEmpty().contains("Child.count"), error.message)
    }
  }

  @Test
  fun `formatted defaults and patch constraints preserve wire operations`(compiler: SwiftCompiler) {
    val formats =
      listOf(
        Triple("uuid", "uuid", "00000000-0000-0000-0000-000000000000"),
        Triple("timestamp", "date-time", "2026-01-01T01:00:00.125+01:00"),
        Triple("local", "date-time-only", "2026-01-01T00:00:00"),
        Triple("date", "date", "2026-01-01"),
        Triple("time", "time", "01:30:00+01:00"),
        Triple("partial", "partial-time", "01:00:00.5"),
        Triple("url", "uri", "https://example.com/a%20b"),
        Triple("bytes", "byte", "aGVsbG8="),
      )
    val defaults =
      GeneratedModel(
        name = "FormattedDefaults",
        kind = GeneratedModel.Kind.OBJECT,
        properties =
          formats.map { (name, format, value) ->
            GeneratedModelProperty(
              name,
              if (name ==
                "uuid"
              ) {
                GeneratedTypeRef.named("UuidAlias")
              } else {
                GeneratedTypeRef.scalar("string", format = format)
              },
              defaultValue = value,
            )
          },
      )
    val patch =
      GeneratedModel(
        name = "ConstrainedPatch",
        kind = GeneratedModel.Kind.OBJECT,
        patchable = true,
        properties =
          listOf(
            GeneratedModelProperty(
              "value",
              GeneratedTypeRef.scalar("string"),
              required = true,
              defaultValue = "valid",
              allowedValues = listOf("valid"),
            ),
            GeneratedModelProperty(
              "nullable",
              GeneratedTypeRef.scalar("string", nullable = true),
              required = true,
              defaultValue = "valid",
              allowedValues = listOf("valid"),
            ),
          ),
      )
    val api =
      GeneratedApi(
        name = "Defaults",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            defaults,
            GeneratedModel(
              name = "RequiredDefaults",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("FormattedDefaults")),
              properties = listOf(defaults.properties.first().copy(required = true)),
            ),
            patch,
            patch.copy(name = "OrdinaryRequired", patchable = false),
            GeneratedModel(
              name = "UuidAlias",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.named("UuidValue")),
            ),
            GeneratedModel(
              name = "UuidValue",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string", format = "uuid")),
            ),
          ),
      )
    generateSwiftSundayFiles(compiler, api)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("DefaultsTests.swift"),
      """
      import Foundation
      import Sunday
      import XCTest
      @testable import SundayGenTest
      final class DefaultsTests: XCTestCase {
        func testDefaults() throws {
          let decoder = JSONDecoder()
          for value in [FormattedDefaults(), try decoder.decode(FormattedDefaults.self, from: Data("{}".utf8))] {
            XCTAssertEqual(value.uuid, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            XCTAssertEqual(value.timestamp?.timeIntervalSince1970, 1767225600.125)
            XCTAssertEqual(value.local?.timeIntervalSince1970, 1767225600)
            XCTAssertEqual(value.date?.timeIntervalSince1970, 1767225600)
            XCTAssertEqual(value.time?.timeIntervalSince1970, 1800)
            XCTAssertEqual(value.partial?.timeIntervalSince1970, 3600.5)
            XCTAssertEqual(value.url?.absoluteString, "https://example.com/a%20b")
            XCTAssertEqual(value.bytes, Data("hello".utf8))
          }
          let nullData = Data(#"{"uuid":null,"timestamp":null,"bytes":null}"#.utf8)
          let explicitNull = try decoder.decode(FormattedDefaults.self, from: nullData)
          XCTAssertNil(explicitNull.uuid)
          XCTAssertNil(explicitNull.timestamp)
          XCTAssertNil(explicitNull.bytes)
          XCTAssertNil(RequiredDefaults().uuid)
          XCTAssertThrowsError(try decoder.decode(RequiredDefaults.self, from: Data("{}".utf8)))
        }
        func testPatches() throws {
          let decoder = JSONDecoder()
          for json in ["{}", #"{"value":null}"#] {
            let patch = try decoder.decode(ConstrainedPatch.self, from: Data(json.utf8))
            XCTAssertNil(patch.value)
            XCTAssertNil(patch.nullable)
            XCTAssertEqual(String(data: try JSONEncoder().encode(patch), encoding: .utf8), "{}")
          }
          let deleted = try decoder.decode(ConstrainedPatch.self, from: Data(#"{"nullable":null}"#.utf8))
          guard case .delete? = deleted.nullable else { return XCTFail("null must remain a delete") }
          let supplied = try decoder.decode(ConstrainedPatch.self, from: Data(#"{"value":"valid","nullable":"valid"}"#.utf8))
          guard case .set("valid")? = supplied.value, case .set("valid")? = supplied.nullable else { return XCTFail("set lost") }
          for json in [#"{"value":"invalid"}"#, #"{"nullable":"invalid"}"#] {
            XCTAssertThrowsError(try decoder.decode(ConstrainedPatch.self, from: Data(json.utf8)))
          }
          XCTAssertThrowsError(try decoder.decode(OrdinaryRequired.self, from: Data("{}".utf8)))
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
    for ((format, literal) in listOf(
      "uuid" to "bad",
      "date-time" to "not-a-date",
      "date" to "2026-02-30",
      "uri" to "bad uri",
      "byte" to "???",
      "binary" to "abc",
    )) {
      val malformed =
        defaults.copy(
          properties =
            listOf(
              GeneratedModelProperty(
                "invalid",
                GeneratedTypeRef.scalar("string", format = format),
                defaultValue = literal,
              ),
            ),
        )
      val error =
        assertThrows(GenerationException::class.java) {
          SwiftSundayIrGenerator(
            api.copy(models = listOf(malformed)),
            SwiftTypeRegistry(setOf()),
            swiftSundayTestOptions,
          ).generateServiceTypes()
        }
      assertTrue(error.message.orEmpty().contains("FormattedDefaults.invalid"), error.message)
    }
  }

  @Test
  fun `superclass decoders use the most derived typed defaults`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val schemas =
        """
        DefaultCount: {type: integer}
        DefaultIdentifier: {type: string, format: uuid}
        DefaultParent:
          type: object
          properties:
            count: {${'$'}ref: '#/components/schemas/DefaultCount', default: 1}
            added: {type: integer}
            identifier: {${'$'}ref: '#/components/schemas/DefaultIdentifier', default: '00000000-0000-0000-0000-000000000000'}
            next: {${'$'}ref: '#/components/schemas/DefaultChild'}
            other: {${'$'}ref: '#/components/schemas/DefaultParent'}
            grandchild: {${'$'}ref: '#/components/schemas/DefaultGrandchild'}
            requiredChild: {${'$'}ref: '#/components/schemas/RequiredDefaultChild'}
        DefaultChild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultParent'}]
          properties:
            count: {minimum: 2, default: 2}
            added: {minimum: 4, default: 4.0}
            identifier: {default: '00000000-0000-0000-0000-000000000001'}
        DefaultGrandchild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultChild'}]
          properties: {count: {minimum: 3, default: 3}}
        RequiredDefaultChild:
          allOf: [{${'$'}ref: '#/components/schemas/DefaultChild'}]
          required: [count]
        """.trimIndent()
      fixture.respond("/defaults.yaml", OpenApiReferenceDocuments.document("Defaults", schemas))
      val source = directory.resolve("defaults.yaml")
      Files.writeString(
        source,
        OpenApiReferenceDocuments.document(
          "Inherited defaults",
          listOf("DefaultParent", "DefaultChild", "DefaultGrandchild", "RequiredDefaultChild").joinToString("\n") {
            "$it: {${'$'}ref: '${fixture.baseUri}defaults.yaml#/components/schemas/$it'}"
          },
        ),
      )
      val options =
        GeneratedApiIrOptions(
          openApiReferences = OpenApiReferenceOptions(directory.resolve("cache"), allowPrivateNetwork = true),
        )
      val api = OpenApiToGeneratedApi(options).convert(source.toUri())
      generateSwiftSundayFiles(compiler, api)
      Files.createDirectories(compiler.testsDir)
      Files.writeString(
        compiler.testsDir.resolve("InheritedDefaultsTests.swift"),
        """
        import Foundation
        import XCTest
        @testable import SundayGenTest

        final class InheritedDefaultsTests: XCTestCase {
          func testDynamicDefaults() throws {
            let decoder = JSONDecoder()
            let parent = try decoder.decode(DefaultParent.self, from: Data("{}".utf8))
            XCTAssertEqual(parent.count, 1)
            XCTAssertNil(parent.added)
            XCTAssertEqual(parent.identifier, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            for child in [DefaultChild(), try decoder.decode(DefaultChild.self, from: Data("{}".utf8))] {
              let assigned: DefaultParent = child
              XCTAssertEqual(assigned.count, 2)
              XCTAssertEqual(assigned.added, 4)
              XCTAssertEqual(assigned.identifier, UUID(uuidString: "00000000-0000-0000-0000-000000000001"))
            }
            for grandchild in [DefaultGrandchild(), try decoder.decode(DefaultGrandchild.self, from: Data("{}".utf8))] {
              XCTAssertEqual(grandchild.count, 3)
              XCTAssertEqual(grandchild.added, 4)
            }
            let nested = try decoder.decode(DefaultGrandchild.self, from: Data(#"{"next":{},"other":{}}"#.utf8))
            XCTAssertEqual(nested.count, 3)
            XCTAssertEqual(nested.next?.count, 2)
            XCTAssertEqual(nested.other?.count, 1)
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"count":1}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"count":null}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(DefaultChild.self, from: Data(#"{"added":3}"#.utf8)))
            XCTAssertThrowsError(try decoder.decode(RequiredDefaultChild.self, from: Data("{}".utf8)))
            XCTAssertEqual(try decoder.decode(RequiredDefaultChild.self, from: Data(#"{"count":5}"#.utf8)).count, 5)
            let supplied = try decoder.decode(DefaultChild.self, from: Data(#"{"count":6,"identifier":null}"#.utf8))
            XCTAssertEqual(supplied.count, 6)
            XCTAssertNil(supplied.identifier)
          }
        }
        """.trimIndent(),
      )
      assertTrue(compileAndTestGeneratedFiles(compiler))
      val parent = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DefaultParent.swift")
      val child = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DefaultChild.swift")
      assertTrue(parent.contains("class var _sundayDefaultCount"), parent)
      assertTrue(child.contains("class override var _sundayDefaultCount"), child)
      assertTrue(child.contains("try super.init(from: decoder)"), child)
      assertFalse(child.contains("public let count"), child)
    }
  }

  @Test
  fun `compiles remote schema resources`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    OpenApiHttpFixture().use { fixture ->
      val api = fixture.export(directory, OpenApiReferenceDocuments.sdkCompatibilityDecimalDefaults)
      generateSwiftSundayFiles(compiler, api)
      Files.createDirectories(compiler.testsDir)
      Files.writeString(
        compiler.testsDir.resolve("NullUnionTests.swift"),
        """
        import Foundation
        import XCTest
        @testable import SundayGenTest

        final class NullUnionTests: XCTestCase {
          func testSdkContracts() throws {
            let decoder = JSONDecoder()
            let mappedPayload = Data(#"{"kind":"cat","name":"Mittens"}"#.utf8)
            let mapped = try decoder.decode(SdkMappedPetRef.self, from: mappedPayload)
            let mappedParent: any SdkMappedCat = try XCTUnwrap(mapped.value as? SdkWrappedCat)
            XCTAssertEqual(mappedParent.name, "Mittens")
            let mappedData = try JSONEncoder().encode(mapped)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: mappedData) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: mappedPayload) as! NSDictionary)
            _ = try decoder.decode(SdkMappedPetRef.self, from: mappedData)
            let aliasPayload = Data(#"{"label":"base","count":2,"extra":"child"}"#.utf8)
            let aliasChild = try decoder.decode(SdkAliasChild.self, from: aliasPayload)
            XCTAssertEqual(aliasChild.label, "base")
            XCTAssertEqual(aliasChild.extra, "child")
            XCTAssertEqual(SdkAliasBase(label: aliasChild.label, count: aliasChild.count).count, 2)
            XCTAssertEqual(try JSONSerialization.jsonObject(with: JSONEncoder().encode(aliasChild)) as! NSDictionary,
                           try JSONSerialization.jsonObject(with: aliasPayload) as! NSDictionary)
            XCTAssertThrowsError(try decoder.decode(SdkAliasChild.self, from: Data(#"{"label":"base","count":0,"extra":"child"}"#.utf8)))
            let multiPayload = Data(#"{"a":"first","b":"second","count":2,"state":"b"}"#.utf8)
            let multi = try decoder.decode(SdkMultiChild.self, from: multiPayload)
            let reversed = try decoder.decode(SdkMultiReversed.self, from: multiPayload)
            XCTAssertEqual(multi.a, "first")
            XCTAssertEqual(multi.b, "second")
            XCTAssertEqual(reversed.a, "first")
            XCTAssertEqual(reversed.b, "second")
            for data in [try JSONEncoder().encode(multi), try JSONEncoder().encode(reversed)] {
              XCTAssertEqual(try JSONSerialization.jsonObject(with: data) as! NSDictionary,
                             try JSONSerialization.jsonObject(with: multiPayload) as! NSDictionary)
            }
            XCTAssertEqual(try decoder.decode(SdkMultiChild.self, from: Data(#"{"a":"first","b":"second"}"#.utf8)).count, 2)
            for invalid in [#"{"a":"first","b":"second","count":0}"#, #"{"a":"first","b":"second","state":"a"}"#] {
              XCTAssertThrowsError(try decoder.decode(SdkMultiChild.self, from: Data(invalid.utf8)))
              XCTAssertThrowsError(try decoder.decode(SdkMultiReversed.self, from: Data(invalid.utf8)))
            }
            let intersected = try decoder.decode(SdkConflictingChild.self, from: Data(#"{"status":"b"}"#.utf8))
            XCTAssertEqual(intersected.status, "b")
            XCTAssertThrowsError(try decoder.decode(SdkConflictingChild.self, from: Data(#"{"status":"a"}"#.utf8)))
            let envelopeDefaults = try decoder.decode(SdkEnvelope.self, from: Data("{}".utf8))
            XCTAssertEqual(envelopeDefaults.uuid, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            XCTAssertEqual(envelopeDefaults.timestamp?.timeIntervalSince1970, 1767225600.125)
            for value in [SdkIntegerChild(), try decoder.decode(SdkIntegerChild.self, from: Data("{}".utf8))] {
              XCTAssertEqual(value.count, 1)
              XCTAssertEqual(SdkIntegerBase(count: value.count).count, 1)
            }
            for (state, field) in [("rendered", "versionId"), ("refused", "refusalReason")] {
              let payload = ["currentAsset": ["state": state, field: "value"]]
              let data = try JSONSerialization.data(withJSONObject: payload)
              let entity = try decoder.decode(EntityDetails.self, from: data)
              let asset: (any CurrentAsset)? = entity.currentAsset?.value
              if state == "rendered" {
                XCTAssertEqual((asset as? RenderedAsset)?.versionId, "value")
              } else {
                XCTAssertEqual((asset as? RefusedAsset)?.refusalReason, "value")
              }
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(entity)) as! NSDictionary
              XCTAssertEqual(encoded, payload as NSDictionary)
            }
            let inline = try decoder.decode(SdkInlineChild.self, from: Data(#"{"detail":{"value":"value"},"selection":"text","tags":["b","a"]}"#.utf8))
            let inlineParent = SdkInlineBase(detail: inline.detail, selection: inline.selection, tags: inline.tags)
            XCTAssertEqual(inlineParent.detail?.value, "value")
            XCTAssertEqual(inline.tags, ["b", "a"])
            XCTAssertThrowsError(try decoder.decode(SdkInlineChild.self, from: Data(#"{"detail":{},"selection":"text","tags":["a","a"]}"#.utf8)))
            let event = try decoder.decode(CharacterChangeEvent.self, from: Data(#"{"type":"character","id":"one"}"#.utf8))
            let eventType: NarrativeChangeEventType = event.type
            XCTAssertEqual(eventType.rawValue, "character")
            XCTAssertEqual(event.count, 20)
            for json in [#"{"type":"prop","id":"one"}"#, #"{"type":"future","id":"one"}"#,
                         #"{"type":"character","id":"one","count":0}"#, #"{"type":"character","id":"one","count":21}"#] {
              XCTAssertThrowsError(try decoder.decode(CharacterChangeEvent.self, from: Data(json.utf8)))
            }
            let edit = try decoder.decode(AddFactOp.self, from: Data(#"{"op":"addFact","value":"fact"}"#.utf8))
            let parent: any FactEditOp = edit
            XCTAssertEqual(parent.op, "addFact")
            let problem = try decoder.decode(BadRequestProblem.self, from: Data(#"{"type":"about:blank","title":"Bad request","status":400}"#.utf8))
            XCTAssertEqual(problem.detail, "Invalid request")
            for status in ["401", "null", "false"] {
              XCTAssertThrowsError(try decoder.decode(BadRequestProblem.self, from: Data("{\"status\":\(status)}".utf8)))
            }
            let defaults = try decoder.decode(ScalarRestrictions.self, from: Data(#"{"value":"present"}"#.utf8))
            XCTAssertEqual(defaults.zero, 0)
            XCTAssertEqual(defaults.flag, false)
            XCTAssertEqual(defaults.mode?.rawValue, "character")
            for choice in ["null", "0", "false"] {
              _ = try decoder.decode(ScalarRestrictions.self, from: Data("{\"value\":\"present\",\"choice\":\(choice)}".utf8))
            }
            for json in [#"{}"#, #"{"value":null}"#, #"{"value":""}"#, #"{"value":"present","zero":1}"#,
                         #"{"value":"present","flag":true}"#, #"{"value":"present","choice":"0"}"#,
                         #"{"value":"present","choice":true}"#, #"{"value":"present","mode":"future"}"#] {
              XCTAssertThrowsError(try decoder.decode(ScalarRestrictions.self, from: Data(json.utf8)))
            }
          }

          func testDocumentaryInheritance() throws {
            let child = DocumentedRecord(id: "one", detail: "detail")
            XCTAssertEqual(child.id, BaseRecord(id: "one").id)
            let bytes = try JSONEncoder().encode(child)
            XCTAssertEqual(try JSONDecoder().decode(DocumentedRecord.self, from: bytes).id, "one")
            for payload in [nil, "value"] as [String?] {
              let documented = DocumentedRecord(id: "one", payload: payload)
              let parent = BaseRecord(id: "one", payload: payload)
              XCTAssertEqual(documented.payload, parent.payload)
              let encoded = try JSONEncoder().encode(documented)
              XCTAssertEqual(try JSONDecoder().decode(DocumentedRecord.self, from: encoded).payload, payload)
            }
            for next in [nil, RecordNode(id: "two", next: RecordNode(id: "three"))] as [RecordNode?] {
              let documented = DocumentedRecord(id: "one", next: next)
              let encoded = try JSONEncoder().encode(documented)
              let decoded = try JSONDecoder().decode(DocumentedRecord.self, from: encoded)
              XCTAssertEqual(decoded.next?.id, next?.id)
              XCTAssertEqual(decoded.next?.next?.id, next?.next?.id)
            }
            let recursive = DocumentedRecord(
              id: "one",
              direct: RecordNode(id: "two", direct: RecordNode(id: "three")),
              wrapped: RecordNode(id: "four", wrapped: RecordNode(id: "five"))
            )
            let recursiveBytes = try JSONEncoder().encode(recursive)
            let restored = try JSONDecoder().decode(DocumentedRecord.self, from: recursiveBytes)
            XCTAssertEqual(restored.direct?.direct?.id, "three")
            XCTAssertEqual(restored.wrapped?.wrapped?.id, "five")
            let invalid = Data(#"{"id":"one","next":42}"#.utf8)
            XCTAssertThrowsError(try JSONDecoder().decode(DocumentedRecord.self, from: invalid))
            let cat = try JSONDecoder().decode(Cat2.self, from: Data(#"{"kind":"Cat"}"#.utf8))
            let pet: any Pet = cat
            XCTAssertEqual(pet.kind, "Cat")
          }

          func testBooleanSchemas() throws {
            for value in [0, false, "value", ["nested": true]] as [Any] {
              let bytes = try JSONSerialization.data(withJSONObject: ["truth": value, "empty": value])
              let decoded = try JSONDecoder().decode(BooleanValues.self, from: bytes)
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary
              XCTAssertEqual(encoded["truth"] as? NSObject, encoded["empty"] as? NSObject)
            }
          }

          func testImplicitDiscriminatorValues() throws {
            for kind in ["Cat", "Dog"] {
              let bytes = try JSONSerialization.data(withJSONObject: ["animal": ["kind": kind]])
              let decoded = try JSONDecoder().decode(Pets.self, from: bytes)
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as? [String: Any]
              XCTAssertEqual((encoded?["animal"] as? [String: Any])?["kind"] as? String, kind)
            }
            let invalid = try JSONSerialization.data(withJSONObject: ["animal": ["kind": "Cat2"]])
            XCTAssertThrowsError(try JSONDecoder().decode(Pets.self, from: invalid))
          }

          func testRelativeDiscriminatorMappings() throws {
            for animal in [["kind": "kitty", "lives": 9], ["kind": "hound", "barks": true]] as [[String: Any]] {
              let bytes = try JSONSerialization.data(withJSONObject: ["animal": animal])
              let decoded = try JSONDecoder().decode(MappedPets.self, from: bytes)
              if animal["kind"] as? String == "kitty" {
                XCTAssertEqual((decoded.animal.value as? MappedCat)?.lives, 9)
              } else {
                XCTAssertEqual((decoded.animal.value as? MappedDog)?.barks, true)
              }
              let encoded = try JSONSerialization.jsonObject(with: JSONEncoder().encode(decoded)) as! NSDictionary
              XCTAssertEqual(encoded, ["animal": animal] as NSDictionary)
            }
            let invalid = Data(#"{"animal":{"kind":"MappedCat","lives":9}}"#.utf8)
            XCTAssertThrowsError(try JSONDecoder().decode(MappedPets.self, from: invalid))
          }

          func testNullabilityComposition() throws {
            let decoder = JSONDecoder()
            for values in [NSNull(), ["valid"]] as [Any] {
              let valid: [String: Any] = ["strictText": "valid", "values": values]
              let decoded = try decoder.decode(Nullability.self, from: JSONSerialization.data(withJSONObject: valid))
              XCTAssertEqual(decoded.values, values as? [String])
            }
            let invalid: [String: Any] = ["strictText": NSNull(), "values": NSNull()]
            let bytes = try JSONSerialization.data(withJSONObject: invalid)
            XCTAssertThrowsError(try decoder.decode(Nullability.self, from: bytes))
          }

          func testConstrainedNullUnions() throws {
            let valid: [String: Any] = ["address": ["street": "Main"], "text": "hello", "state": "active"]
            let decoder = JSONDecoder()
            let decoded = try decoder.decode(Restrictions.self, from: JSONSerialization.data(withJSONObject: valid))
            XCTAssertEqual(decoded.text, "hello")
            for field in valid.keys {
              for excluded in [42, NSNull()] as [Any] {
                var invalid = valid
                invalid[field] = excluded
                let bytes = try JSONSerialization.data(withJSONObject: invalid)
                XCTAssertThrowsError(try decoder.decode(Restrictions.self, from: bytes))
              }
            }
          }
        }
        """.trimIndent(),
      )
      assertTrue(compileAndTestGeneratedFiles(compiler))
      assertEquals(
        listOf(GeneratedTypeRef.named("BaseRecord")),
        api.models.single { it.name == "DocumentedRecord" }.inherits,
      )
      val record = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/DocumentedRecord.swift")
      assertEquals(1, "let id:".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertEquals(1, "let payload: String?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      assertEquals(1, "let next: RecordNode?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      for (field in listOf("direct", "wrapped")) {
        assertEquals(1, "let $field: RecordNode?".toRegex(RegexOption.LITERAL).findAll(record).count(), record)
      }
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Cat.swift").contains("unrelated"))
      assertTrue(CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Cat2.swift").contains("lives"))
      val user = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/User.swift")
      assertTrue(user.contains("Address"), user)
      assertTrue(user.contains("node: Node?"), user)
      assertTrue(user.contains("composedNode: Node?"), user)
      assertTrue(user.contains("copiedNode: Node?"), user)
      assertTrue(user.contains("maybeAddress: Address?"), user)
      assertTrue(user.contains("copiedAddress: Address?"), user)
      assertTrue(user.contains("composedAddress: Address?"), user)
      assertTrue(user.contains("UserProfile2"), user)
      assertFalse(user.contains("UserArbitrary"), user)
      assertFalse(user.contains("UserNullableArbitrary"), user)
      val profile = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserProfile.swift")
      assertTrue(profile.contains("remoteValue"), profile)
      val inlineProfile = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserProfile2.swift")
      assertTrue(inlineProfile.contains("localValue"), inlineProfile)
      val extended = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/UserExtendedAddress.swift")
      assertTrue(extended.contains("street"), extended)
      assertTrue(extended.contains("postalCode"), extended)
      val node = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Node.swift")
      assertTrue(node.contains("child: Node?"), node)
      val service = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "API.swift")
      assertTrue(service.contains("= 20"), service)
      val nullability = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/Nullability.swift")
      assertTrue(nullability.contains("strictText: String"), nullability)
      assertFalse(nullability.contains("strictText: String?"), nullability)
      assertTrue(nullability.contains("values: [String]?"), nullability)
    }
  }

  @Test
  fun `Swift Sunday CLI uses IR exporter directly`() {
    val source =
      Files.readString(
        Path.of(
          "..",
          "cli",
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "swift",
          "SwiftSundayGenerateCommand.kt",
        ),
      )

    assertTrue(source.contains("GeneratedApiIrExporter"))
    assertTrue(source.contains("SwiftSundayIrGenerator"))
    assertFalse(source.contains("SwiftSundayGenerator("), source)
  }

  @Test
  fun `Swift Sunday IR renderer does not read AMF service model types`() {
    val source =
      Files.readString(
        Path.of(
          "src",
          "main",
          "kotlin",
          "io",
          "outfoxx",
          "sunday",
          "generator",
          "swift",
          "SwiftSundayIrGenerator.kt",
        ),
      )

    assertFalse(source.contains("amf."), source)
    assertFalse(source.contains("processService"), source)
    assertFalse(source.contains("processResourceMethod"), source)
    assertFalse(source.contains("processReturnType("), source)
  }

  @Test
  fun `public generator generates service types from RAML through IR path`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val builtTypes = generateSunday(testUri, typeRegistry, compiler)
    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestMethodsTest/test-request-method-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `Swift Sunday generated files track Foundation imports for file payloads`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "TurnPost API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:turnpost.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "TeamsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "putTeamAvatar",
                    method = "PUT",
                    path = "/teams/{teamId}/avatar",
                    requestBody =
                      GeneratedPayload(
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("application/octet-stream"),
                      ),
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()
    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Service), compiler.srcDir)

    val source = Files.readString(compiler.srcDir.resolve("TeamsAPI.swift"))
    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("body: Data"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from RAML source`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/ir/any-shapes.raml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val source = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(source.contains("import PotentCodables"), source)
    assertTrue(source.contains("public let value: AnyValue?"), source)
    assertTrue(source.contains("try container.decodeIfPresent(AnyValue.self, forKey: .value)"), source)
  }

  @Test
  fun `Swift Sunday generated files compile from OpenAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/operation-surface-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday preserves OpenAPI inline object properties beside conditional allOf`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/inline-object-conditional-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))

    val dataSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/VisualizationRenderGraphTaskSettledData.swift",
      )
    val renderGraphSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/VisualizationRenderGraphTaskSettledDataRenderGraph.swift",
      )
    val allOfRequiredSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/AllOfRequiredData.swift",
      )
    assertTrue(
      dataSource.contains(
        "public let renderGraph: VisualizationRenderGraphTaskSettledDataRenderGraph",
      ),
      dataSource,
    )
    assertTrue(allOfRequiredSource.contains("public let value: String"), allOfRequiredSource)
    assertTrue(renderGraphSource.contains("public let graphJobId: String"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let taskId: String"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let state: RenderGraphTaskSettledState"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let completedCount: Int"), renderGraphSource)
    assertTrue(renderGraphSource.contains("public let taskCount: Int"), renderGraphSource)
  }

  @Test
  fun `Swift Sunday generated files use OpenAPI enum varnames`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/enum-varnames-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val notificationTypeSource = Files.readString(compiler.srcDir.resolve("Models").resolve("NotificationType.swift"))
    val fallbackTypeSource = Files.readString(compiler.srcDir.resolve("Models").resolve("FallbackType.swift"))
    val notificationSource = Files.readString(compiler.srcDir.resolve("Models").resolve("Notification.swift"))
    val notificationActivitySource =
      Files.readString(compiler.srcDir.resolve("Models").resolve("NotificationActivity.swift"))
    val reviewRequestedSource =
      Files.readString(compiler.srcDir.resolve("Models").resolve("PullRequestReviewRequestedNotification.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(
      notificationTypeSource.contains(
        "case pullRequestReviewRequested = \"notification.pull_request.review_requested\"",
      ),
      notificationTypeSource,
    )
    assertTrue(notificationTypeSource.contains("case pullRequestMerged = \"notification.pull_request.merged\""))
    assertTrue(notificationTypeSource.contains("case teamMemberAdded = \"notification.team.member_added\""))
    assertTrue(fallbackTypeSource.contains("case `open` = \"OPEN\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case lowerSnake = \"lower_snake\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case upperInterCaps = \"UpperInterCaps\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case lowerInterCaps = \"lowerInterCaps\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case dottedCase = \"dotted.case\""), fallbackTypeSource)
    assertTrue(fallbackTypeSource.contains("case mixedKebabCase = \"mixed-kebab.case\""), fallbackTypeSource)
    assertTrue(notificationSource.contains("public let type: NotificationType"), notificationSource)
    assertTrue(
      notificationActivitySource.contains("if discriminatorValue == \"notification.pull_request.review_requested\""),
      notificationActivitySource,
    )
    assertTrue(
      reviewRequestedSource.contains("return NotificationType.pullRequestReviewRequested"),
      reviewRequestedSource,
    )
  }

  @Test
  fun `Swift Sunday generated files use streaming operations for streaming request bodies`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/streaming-request-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    val source =
      Files
        .walk(compiler.srcDir)
        .use { files ->
          files
            .filter { path -> path.fileName.toString().endsWith(".swift") }
            .map { path -> Files.readString(path) }
            .filter { content -> "importArchive" in content }
            .findFirst()
            .orElseThrow()
        }

    assertTrue("body: StreamingBody" in source)
    assertTrue("Sunday.StreamingOperation<ImportAccepted, TransportType>" in source)
    assertTrue("Sunday.NilableOperation<StreamingBody, ImportAccepted, TransportType>" in source)
    assertTrue("spec: Sunday.OperationSpec.streaming(" in source)
    assertTrue("nilify: Sunday.NilifySpec(" in source)
    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files treat OpenAPI empty schemas as AnyValue`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/any-json-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(
      compiler,
      GeneratedApiIrExporter(GeneratedApiIrOptions(deriveServicesFromTags = true)).export(testUri),
    )

    val holderSource = Files.readString(compiler.srcDir.resolve("Models").resolve("AnyHolder.swift"))
    val entityStatePropertyValueSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyValue.swift"),
      )
    val entityStatePropertyRefSource =
      Files.readString(
        compiler.srcDir
          .resolve("Narrative")
          .resolve("Models")
          .resolve("EntityStatePropertyRef.swift"),
      )
    val serviceSource = Files.readString(compiler.srcDir.resolve("NarrativeAPI.swift"))

    assertTrue(compileGeneratedFiles(compiler))
    assertTrue(holderSource.contains("public let value: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let documented: AnyValue?"), holderSource)
    assertTrue(holderSource.contains("public let named: AnyValue?"), holderSource)
    assertTrue(
      entityStatePropertyValueSource.contains("public struct EntityStatePropertyValue : EntityStateProperty"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyValueSource.contains("public let value: AnyValue?"),
      entityStatePropertyValueSource,
    )
    assertTrue(
      entityStatePropertyRefSource.contains("case value(EntityStatePropertyValue)"),
      entityStatePropertyRefSource,
    )
    assertTrue(serviceSource.contains("body: AnyValue"), serviceSource)
    assertTrue(serviceSource.contains("Operation<AnyValue, AnyValue, TransportType>"), serviceSource)
  }

  @Test
  fun `Swift Sunday generated files compile from AsyncAPI source`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") testUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(testUri))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `Swift Sunday generated files compile from composed OpenAPI and AsyncAPI sources`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/typed-event-envelope-3.1.yaml") asyncApiUri: URI,
  ) {
    generateSwiftSundayFiles(compiler, GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri)))

    assertTrue(compileGeneratedFiles(compiler))
  }

  @Test
  fun `generates documentation comments from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Documentation API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory:docs.yaml"),
        services =
          listOf(
            GeneratedService(
              name = "ProjectsService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getProject",
                    method = "GET",
                    path = "/repos/{projectId}",
                    documentation =
                      GeneratedDocumentation(
                        summary = "Fetch a project.",
                        description = "Returns a project visible to the `/repos/**` caller.",
                      ),
                    parameters =
                      listOf(
                        GeneratedParameter(
                          name = "projectId",
                          location = GeneratedParameter.Location.PATH,
                          type = GeneratedTypeRef.scalar("string"),
                        ),
                      ),
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("Project"),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Project",
              kind = GeneratedModel.Kind.OBJECT,
              documentation =
                GeneratedDocumentation(
                  summary = "Project model.",
                  description = "A project in the workspace.",
                ),
              properties =
                listOf(
                  GeneratedModelProperty(
                    name = "projectId",
                    type = GeneratedTypeRef.scalar("string"),
                    required = true,
                    documentation = GeneratedDocumentation(description = "Stable project identifier."),
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("ProjectsAPI", builtTypes))
          .writeTo(this)
      }
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("Project", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("Fetch a project."), serviceSource)
    assertTrue(serviceSource.contains("Returns a project visible to the `/repos/ **` caller."), serviceSource)
    assertTrue(modelSource.contains("Project model."), modelSource)
    assertTrue(modelSource.contains("A project in the workspace."), modelSource)
    assertTrue(modelSource.contains("Stable project identifier."), modelSource)
  }

  @Test
  fun `uses content type header parameter as request media selection in Swift Sunday`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = avatarUploadApi()

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("UsersAPI", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("try .init(valid: contentType.rawValue)"), serviceSource)
    assertTrue(serviceSource.contains("acceptTypes: [try .init(valid: \"image/png\")"), serviceSource)
    assertFalse(serviceSource.contains("\"Content-Type\": contentType"), serviceSource)
  }

  @Test
  fun `generates composed OpenAPI and AsyncAPI HTTP event service from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/event-stream-payload.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    assertTrue(compileTypes(compiler, builtTypes))
    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "EventsAPI.swift")
    assertTrue(source.contains("public func streamProjectEvents("), source)
    assertTrue(source.contains("subscriberId: String"), source)
    assertTrue(source.contains("lastEventID: String? = nil"), source)
    assertTrue(source.contains("queryParameters: ["), source)
    assertTrue(source.contains("\"subscriberId\": try! ParameterValues.encode(subscriberId)"), source)
    assertTrue(source.contains("headers: ["), source)
    assertTrue(source.contains("\"Last-Event-ID\": try! ParameterValues.encode(lastEventID)"), source)
    assertTrue(source.contains("transport.eventStream"), source)
  }

  @Test
  fun `omits broker-only AsyncAPI channels from Swift Sunday output`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/event-stream-framing-3.1.yaml") openApiUri: URI,
    @ResourceUri("asyncapi/ir/http-and-broker-events.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(openApiUri, asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventsSource =
      buildString {
        FileSpec
          .get("", findType("EventsAPI", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(eventsSource.contains("public func streamProjectEvents("), eventsSource)
    assertTrue(eventsSource.contains("format: \"https://api.example.com\""), eventsSource)
    assertFalse(eventsSource.contains("broker.example.com"), eventsSource)
    assertFalse(eventsSource.contains("consumePlatformEvent"), eventsSource)
    assertFalse(eventsSource.contains("consumeBrokerPathEvent"), eventsSource)
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "PlatformAPI" })
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "BrokerAPI" })
  }

  @Test
  fun `generates typed AsyncAPI event payload models from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/typed-event-envelope.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventEnvelopeSource =
      buildString {
        FileSpec
          .get("", findType("EventEnvelope", builtTypes))
          .writeTo(this)
      }
    val projectCreatedSource =
      buildString {
        FileSpec
          .get("", findType("ProjectCreatedData", builtTypes))
          .writeTo(this)
      }
    val eventDataSource =
      buildString {
        FileSpec
          .get("", findType("EventData", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "EventData" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "ProjectDeletedData" })
    assertTrue(
      eventEnvelopeSource.contains("public enum EventEnvelope : Codable, CustomDebugStringConvertible, Sendable"),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("case projectCreated(ProjectCreatedEvent)"), eventEnvelopeSource)
    assertTrue(eventEnvelopeSource.contains("public var data: EventData"), eventEnvelopeSource)
    assertTrue(eventEnvelopeSource.contains("public struct ProjectCreatedEvent"), eventEnvelopeSource)
    assertTrue(
      eventEnvelopeSource.contains("let discriminatorValue = try container.decode(String.self, forKey: .type)"),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("if discriminatorValue == \"project.created\""), eventEnvelopeSource)
    assertTrue(eventEnvelopeSource.contains("return .projectCreatedData(value.data)"), eventEnvelopeSource)
    assertTrue(
      eventDataSource.contains("public enum EventData : Codable, CustomDebugStringConvertible, Sendable"),
      eventDataSource,
    )
    assertTrue(eventDataSource.contains("case projectCreatedData(ProjectCreatedData)"), eventDataSource)
    assertTrue(projectCreatedSource.contains("public struct ProjectCreatedData : Codable"), projectCreatedSource)
  }

  @Test
  fun `generates direct AsyncAPI discriminated event object unions from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/direct-discriminated-event-union.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val eventEnvelopeSource =
      buildString {
        FileSpec
          .get("", findType("EventEnvelope", builtTypes))
          .writeTo(this)
      }
    val accountsTeamCreatedEventSource =
      buildString {
        FileSpec
          .get("", findType("AccountsTeamCreatedEvent", builtTypes))
          .writeTo(this)
      }
    val notificationEventSource =
      buildString {
        FileSpec
          .get("", findType("NotificationsAnnouncementPublishedEvent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "AccountsTeamCreatedData" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "NotificationAnnouncementPublishedData" })
    assertTrue(
      eventEnvelopeSource.contains("public enum EventEnvelope : Codable, CustomDebugStringConvertible, Sendable"),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains("case accountsTeamCreatedEvent(AccountsTeamCreatedEvent)"),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains(
        "case notificationsAnnouncementPublishedEvent(NotificationsAnnouncementPublishedEvent)",
      ),
      eventEnvelopeSource,
    )
    assertTrue(
      eventEnvelopeSource.contains("let discriminatorValue = try container.decode(String.self, forKey: .type)"),
      eventEnvelopeSource,
    )
    assertTrue(eventEnvelopeSource.contains("if discriminatorValue == \"accounts.team.created\""), eventEnvelopeSource)
    assertTrue(
      eventEnvelopeSource.contains("self = .accountsTeamCreatedEvent(try AccountsTeamCreatedEvent(from: decoder))"),
      eventEnvelopeSource,
    )
    assertFalse(
      eventEnvelopeSource.contains("AnyValueDecoder.default.decode(AccountsTeamCreatedEvent.self, from: value)"),
      eventEnvelopeSource,
    )
    assertTrue(accountsTeamCreatedEventSource.contains("public let id: String"), accountsTeamCreatedEventSource)
    assertTrue(accountsTeamCreatedEventSource.contains("public let occurredAt: Date"), accountsTeamCreatedEventSource)
    assertTrue(
      accountsTeamCreatedEventSource.contains("public let data: AccountsTeamCreatedData"),
      accountsTeamCreatedEventSource,
    )
    assertTrue(notificationEventSource.contains("public let id: String"), notificationEventSource)
    assertTrue(notificationEventSource.contains("public let occurredAt: Date"), notificationEventSource)
    assertTrue(
      notificationEventSource.contains("public let data: NotificationAnnouncementPublishedData"),
      notificationEventSource,
    )
  }

  @Test
  fun `generates Sendable references for AsyncAPI discriminated base models`(
    compiler: SwiftCompiler,
    @ResourceUri("asyncapi/ir/discriminated-base-sendable-regression.yaml") asyncApiUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = GeneratedApiIrExporter().export(listOf(asyncApiUri))

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val narrativeChangeEventSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeChangeEvent", builtTypes))
          .writeTo(this)
      }
    val narrativeChangeEventRefSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeChangeEventRef", builtTypes))
          .writeTo(this)
      }
    val scriptChangeEventSource =
      buildString {
        FileSpec
          .get("", findType("ScriptChangeEvent", builtTypes))
          .writeTo(this)
      }
    val sceneUpdatedSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeIqSceneUpdatedData", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      narrativeChangeEventSource.contains(
        "public protocol NarrativeChangeEvent : Codable, CustomDebugStringConvertible, Sendable",
      ),
      narrativeChangeEventSource,
    )
    assertFalse(narrativeChangeEventSource.contains("public class NarrativeChangeEvent"), narrativeChangeEventSource)
    assertTrue(
      narrativeChangeEventRefSource.contains(
        "public enum NarrativeChangeEventRef : Codable, CustomDebugStringConvertible, Sendable",
      ),
      narrativeChangeEventRefSource,
    )
    assertTrue(narrativeChangeEventRefSource.contains("case script(ScriptChangeEvent)"))
    assertTrue(narrativeChangeEventRefSource.contains("case scene(SceneChangeEvent)"))
    assertTrue(
      scriptChangeEventSource.contains("public struct ScriptChangeEvent : NarrativeChangeEvent"),
      scriptChangeEventSource,
    )
    assertTrue(sceneUpdatedSource.contains("public let change: NarrativeChangeEventRef"), sceneUpdatedSource)
  }

  @Test
  fun `generates request methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation.output.swift",
    )
  }

  @Test
  fun `generates shared object models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
                  GeneratedModelProperty(
                    "metadata",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.scalar("object")),
                    ),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let id: String"), source)
    assertTrue(source.contains("public let displayName: String?"), source)
    assertTrue(source.contains("public let metadata: [[String : AnyValue]]"), source)
    assertTrue(source.contains("case displayName = \"displayName\""), source)
  }

  @Test
  fun `lowers IR date scalar properties to Swift Date`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Dates API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "AuditEvent",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("dateOnly", GeneratedTypeRef.scalar("date"), required = true),
                  GeneratedModelProperty("timeOnly", GeneratedTypeRef.scalar("time"), required = true),
                  GeneratedModelProperty("localDateTime", GeneratedTypeRef.scalar("datetime-only"), required = true),
                  GeneratedModelProperty("timestamp", GeneratedTypeRef.scalar("datetime"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("AuditEvent", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("import Foundation"), source)
    assertTrue(source.contains("public let dateOnly: Date"), source)
    assertTrue(source.contains("public let timeOnly: Date"), source)
    assertTrue(source.contains("public let localDateTime: Date"), source)
    assertTrue(source.contains("public let timestamp: Date"), source)
  }

  @Test
  fun `lowers supported IR scalar formats to Swift Foundation types`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Formatted API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "FormattedService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getFormatted",
                    method = "GET",
                    path = "/formatted/{resourceId}",
                    parameters =
                      listOf(
                        GeneratedParameter(
                          "resourceId",
                          GeneratedParameter.Location.PATH,
                          GeneratedTypeRef.scalar("string", format = "uuid"),
                          required = true,
                        ),
                        GeneratedParameter(
                          "callbackUrl",
                          GeneratedParameter.Location.QUERY,
                          GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                        ),
                        GeneratedParameter(
                          "location",
                          GeneratedParameter.Location.HEADER,
                          GeneratedTypeRef.scalar("string", format = "uri-reference"),
                          required = true,
                          serializationName = "Location",
                        ),
                      ),
                    responses = listOf(GeneratedResponse(status = 204)),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "FormattedModel",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "absoluteUrl",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "relativeUrl",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri-reference"),
                  ),
                  GeneratedModelProperty(
                    "id",
                    GeneratedTypeRef.scalar("string", format = "uuid"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "encoded",
                    GeneratedTypeRef.scalar("string", format = "byte"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "binary",
                    GeneratedTypeRef.scalar("string", format = "binary"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "createdAt",
                    GeneratedTypeRef.scalar("string", format = "date-time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "calendarDay",
                    GeneratedTypeRef.scalar("string", format = "date"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localTime",
                    GeneratedTypeRef.scalar("string", format = "time"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "localDateTime",
                    GeneratedTypeRef.scalar("string", format = "datetime-only"),
                    required = true,
                  ),
                  GeneratedModelProperty(
                    "email",
                    GeneratedTypeRef.scalar("string", format = "email"),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val modelSource =
      buildString {
        FileSpec
          .get("", findType("FormattedModel", builtTypes))
          .writeTo(this)
      }
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(modelSource.contains("import Foundation"), modelSource)
    assertTrue(modelSource.contains("public let absoluteUrl: URL"), modelSource)
    assertTrue(modelSource.contains("public let relativeUrl: URL?"), modelSource)
    assertTrue(modelSource.contains("public let id: UUID"), modelSource)
    assertTrue(modelSource.contains("public let encoded: Data"), modelSource)
    assertTrue(modelSource.contains("public let binary: Data"), modelSource)
    assertTrue(modelSource.contains("public let createdAt: Date"), modelSource)
    assertTrue(modelSource.contains("public let calendarDay: Date"), modelSource)
    assertTrue(modelSource.contains("public let localTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let localDateTime: Date"), modelSource)
    assertTrue(modelSource.contains("public let email: String"), modelSource)
    assertTrue(serviceSource.contains("resourceId: UUID"), serviceSource)
    assertTrue(serviceSource.contains("callbackUrl: URL? = nil"), serviceSource)
    assertTrue(serviceSource.contains("location: URL"), serviceSource)
  }

  @Test
  fun `adds Identifiable to IR models with id when default identifiable option is enabled`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "User",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("User", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      source.contains("public struct User : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      source,
    )
  }

  @Test
  fun `adds Identifiable to IR models with camel or acronym id suffixes`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))
    val api =
      GeneratedApi(
        name = "Identifiable API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "UserRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "TeamRef",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("teamID", GeneratedTypeRef.scalar("string"), required = true)),
            ),
            GeneratedModel(
              name = "NotIdentifiable",
              kind = GeneratedModel.Kind.OBJECT,
              properties = listOf(GeneratedModelProperty("userid", GeneratedTypeRef.scalar("string"), required = true)),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val userRefSource =
      buildString {
        FileSpec
          .get("", findType("UserRef", builtTypes))
          .writeTo(this)
      }
    val teamRefSource =
      buildString {
        FileSpec
          .get("", findType("TeamRef", builtTypes))
          .writeTo(this)
      }
    val notIdentifiableSource =
      buildString {
        FileSpec
          .get("", findType("NotIdentifiable", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      userRefSource.contains("public struct UserRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      userRefSource,
    )
    assertTrue(userRefSource.contains("public var id: String"), userRefSource)
    assertTrue(userRefSource.contains("return self.userId"), userRefSource)
    assertTrue(
      teamRefSource.contains("public struct TeamRef : Codable, CustomDebugStringConvertible, Sendable, Identifiable"),
      teamRefSource,
    )
    assertTrue(teamRefSource.contains("return self.teamID"), teamRefSource)
    assertFalse(
      notIdentifiableSource.contains(
        "public struct NotIdentifiable : Codable, CustomDebugStringConvertible, Sendable, Identifiable",
      ),
      notIdentifiableSource,
    )
  }

  @Test
  fun `filters inherited properties from Swift model subclasses`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problem API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "HttpProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = false),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                ),
            ),
            GeneratedModel(
              name = "BadRequest",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Conflict",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("HttpProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = false),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = false),
                ),
            ),
            GeneratedModel(
              name = "GraphsProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("detail", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "RepoNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("GraphsProblem")),
              properties =
                listOf(
                  GeneratedModelProperty("repoId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val rootSource =
      buildString {
        FileSpec
          .get("", findType("HttpProblem", builtTypes))
          .writeTo(this)
      }
    val source =
      buildString {
        FileSpec
          .get("", findType("BadRequest", builtTypes))
          .writeTo(this)
      }
    val graphsSource =
      buildString {
        FileSpec
          .get("", findType("GraphsProblem", builtTypes))
          .writeTo(this)
      }
    val repoNotFoundSource =
      buildString {
        FileSpec
          .get("", findType("RepoNotFoundProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(rootSource.contains("public protocol HttpProblem : Problem"), rootSource)
    assertFalse(rootSource.contains("public class HttpProblem"), rootSource)
    assertTrue(source.contains("public struct BadRequest : HttpProblem"), source)
    assertTrue(source.contains("public let type: URL"), source)
    assertTrue(source.contains("public let title: String"), source)
    assertTrue(source.contains("public let status: Int"), source)
    assertTrue(source.contains("public let code: String"), source)
    assertTrue(graphsSource.contains("public protocol GraphsProblem : Problem"), graphsSource)
    assertTrue(graphsSource.contains("var code: String { get }"), graphsSource)
    assertTrue(repoNotFoundSource.contains("public struct RepoNotFoundProblem : GraphsProblem"), repoNotFoundSource)
    assertFalse(repoNotFoundSource.contains("override"), repoNotFoundSource)
    assertTrue(repoNotFoundSource.contains("public let parameters: [String : AnyValue]?"), repoNotFoundSource)
  }

  @Test
  fun `generates sendable request body types for inherited and discriminated models`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "NarrativeService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "updateScript",
                    method = "PATCH",
                    path = "/scripts/{scriptId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("ScriptUpdate")),
                  ),
                  GeneratedOperation(
                    id = "updateEntity",
                    method = "PATCH",
                    path = "/entities/{entityId}",
                    requestBody = GeneratedPayload(type = GeneratedTypeRef.named("EntityUpdate")),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "Update",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("version", GeneratedTypeRef.scalar("integer"), required = true),
                ),
            ),
            GeneratedModel(
              name = "ScriptUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Update")),
              properties =
                listOf(
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = false),
                ),
            ),
            GeneratedModel(
              name = "EntityKind",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("scene"),
            ),
            GeneratedModel(
              name = "EntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.named("EntityKind"), required = true),
                ),
            ),
            GeneratedModel(
              name = "SceneEntityUpdate",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntityUpdate")),
              discriminatorValue = "scene",
              properties =
                listOf(
                  GeneratedModelProperty("sceneId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("API", builtTypes))
          .writeTo(this)
      }
    val scriptUpdateSource =
      buildString {
        FileSpec
          .get("", findType("ScriptUpdate", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntityUpdateRef", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      serviceSource.contains(
        "public func updateScript(body: ScriptUpdate) throws -> Sunday.Operation<ScriptUpdate, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      serviceSource.contains(
        "public func updateEntity(body: EntityUpdateRef) throws -> Sunday.Operation<EntityUpdateRef, Void, TransportType>",
      ),
      serviceSource,
    )
    assertTrue(
      scriptUpdateSource.contains("public struct ScriptUpdate : Codable, CustomDebugStringConvertible, Sendable"),
      scriptUpdateSource,
    )
    assertTrue(scriptUpdateSource.contains("public let version: Int"), scriptUpdateSource)
    assertTrue(scriptUpdateSource.contains("public let title: String?"), scriptUpdateSource)
    assertTrue(
      entityRefSource.contains("public enum EntityUpdateRef : Codable, CustomDebugStringConvertible, Sendable"),
    )
  }

  @Test
  fun `qualifies Sunday Problem when generated model has same name`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Narrative API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Problem",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "code",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string", format = "uri"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("status", GeneratedTypeRef.scalar("integer"), required = true),
                  GeneratedModelProperty(
                    "detail",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = false,
                  ),
                  GeneratedModelProperty(
                    "instance",
                    GeneratedTypeRef.scalar("string", nullable = true, format = "uri"),
                    required = false,
                  ),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "NarrativeProblem",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Problem")),
              properties =
                listOf(
                  GeneratedModelProperty("narrativeId", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val problemSource =
      buildString {
        FileSpec
          .get("", findType("Problem", builtTypes))
          .writeTo(this)
      }
    val narrativeProblemSource =
      buildString {
        FileSpec
          .get("", findType("NarrativeProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(problemSource.contains("public protocol Problem : Sunday.Problem"), problemSource)
    assertTrue(narrativeProblemSource.contains("public struct NarrativeProblem : Problem"), narrativeProblemSource)
  }

  @Test
  fun `uses reference models for recursive Swift object graphs`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Recursive API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "EntityType",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("location", "character"),
            ),
            GeneratedModel(
              name = "EntitySummary",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "type",
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.named("EntityType"), required = true),
                ),
            ),
            GeneratedModel(
              name = "LocationSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "location",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "parent",
                    GeneratedTypeRef.named("LocationSummary").copy(nullable = true),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "CharacterSummary",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("EntitySummary")),
              discriminatorValue = "character",
              properties =
                listOf(
                  GeneratedModelProperty("id", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "EntityList",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty(
                    "items",
                    GeneratedTypeRef(
                      kind = GeneratedTypeRef.Kind.ARRAY,
                      name = "array",
                      arguments = listOf(GeneratedTypeRef.named("EntitySummary")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "EntityMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.named("EntitySummary")),
            ),
            GeneratedModel(
              name = "EntityMapHolder",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("entries", GeneratedTypeRef.named("EntityMap"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val locationSource =
      buildString {
        FileSpec
          .get("", findType("LocationSummary", builtTypes))
          .writeTo(this)
      }
    val entityRefSource =
      buildString {
        FileSpec
          .get("", findType("EntitySummaryRef", builtTypes))
          .writeTo(this)
      }
    val listSource =
      buildString {
        FileSpec
          .get("", findType("EntityList", builtTypes))
          .writeTo(this)
      }
    val mapHolderSource =
      buildString {
        FileSpec
          .get("", findType("EntityMapHolder", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(locationSource.contains("public final class LocationSummary : EntitySummary"), locationSource)
    assertTrue(locationSource.contains("public let parent: LocationSummary?"), locationSource)
    assertTrue(locationSource.contains("parent: LocationSummary? = nil"), locationSource)
    assertTrue(
      entityRefSource.contains("let type = try container.decode(String.self, forKey: CodingKeys.type)"),
      entityRefSource,
    )
    assertTrue(entityRefSource.contains("case \"location\":"), entityRefSource)
    assertTrue(listSource.contains("public let items: [EntitySummaryRef]"), listSource)
    assertTrue(mapHolderSource.contains("public let entries: [String : EntitySummaryRef]"), mapHolderSource)
  }

  @Test
  fun `distinguishes value and reference models for OpenAPI allOf inheritance`(
    compiler: SwiftCompiler,
    @ResourceUri("openapi/ir/value-inheritance-3.1.yaml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = OpenApiToGeneratedApi().convert(testUri)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val summarySource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphSummary.swift")
    val detailsSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphDetails.swift")
    val listSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RenderGraphList.swift")
    val recursiveParentSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RecursiveParent.swift")
    val recursiveChildSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "RecursiveChild.swift")
    assertTrue(
      summarySource.contains(
        "public struct RenderGraphSummary : Codable, CustomDebugStringConvertible, Sendable",
      ),
      summarySource,
    )
    assertTrue(
      detailsSource.contains(
        "public struct RenderGraphDetails : Codable, CustomDebugStringConvertible, Sendable",
      ),
      detailsSource,
    )
    assertTrue(detailsSource.contains("public let graphJobId: String"), detailsSource)
    assertTrue(detailsSource.contains("public let tasks: [String]"), detailsSource)
    assertTrue(listSource.contains("public let items: [RenderGraphSummary]"), listSource)
    assertTrue(recursiveParentSource.contains("public class RecursiveParent"), recursiveParentSource)
    assertTrue(recursiveParentSource.contains("public let child: RecursiveChild?"), recursiveParentSource)
    assertTrue(
      recursiveChildSource.contains("public final class RecursiveChild : RecursiveParent"),
      recursiveChildSource,
    )
  }

  @Test
  fun `lowers shared enums and alias-like models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Alias API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        targets = mapOf("swift" to GeneratedTarget(modelModuleName = "AliasModels")),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("OPEN", "PULL_REQUEST_OPEN", "lower_snake_case", "mixed-kebab.case"),
            ),
            GeneratedModel(
              name = "TextAlias",
              kind = GeneratedModel.Kind.SCALAR_ALIAS,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextList",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.named("TextAlias")),
            ),
            GeneratedModel(
              name = "TextSet",
              kind = GeneratedModel.Kind.ARRAY,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
              collection = GeneratedCollectionKind.SET,
            ),
            GeneratedModel(
              name = "TextMap",
              kind = GeneratedModel.Kind.MAP,
              aliases = listOf(GeneratedTypeRef.scalar("string")),
            ),
            GeneratedModel(
              name = "TextUnion",
              kind = GeneratedModel.Kind.UNION,
              aliases = listOf(GeneratedTypeRef.scalar("string"), GeneratedTypeRef.scalar("integer")),
            ),
            GeneratedModel(
              name = "AliasContainer",
              kind = GeneratedModel.Kind.OBJECT,
              targets = mapOf("swift" to GeneratedTarget(typeName = "AliasModels.ContainerValue")),
              properties =
                listOf(
                  GeneratedModelProperty("status", GeneratedTypeRef.named("Status"), required = true),
                  GeneratedModelProperty("alias", GeneratedTypeRef.named("TextAlias"), required = true),
                  GeneratedModelProperty("list", GeneratedTypeRef.named("TextList"), required = true),
                  GeneratedModelProperty("set", GeneratedTypeRef.named("TextSet"), required = true),
                  GeneratedModelProperty("map", GeneratedTypeRef.named("TextMap"), required = true),
                  GeneratedModelProperty("union", GeneratedTypeRef.named("TextUnion"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val enumSource =
      buildString {
        FileSpec
          .get("AliasModels", findType("AliasModels.Status", builtTypes))
          .writeTo(this)
      }
    val containerSource =
      buildString {
        FileSpec
          .get("AliasModels", findType("AliasModels.ContainerValue", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      enumSource.contains("public enum Status : String, CaseIterable, Codable, CustomStringConvertible, Sendable"),
      enumSource,
    )
    assertTrue(enumSource.contains("case `open` = \"OPEN\""), enumSource)
    assertTrue(enumSource.contains("case pullRequestOpen = \"PULL_REQUEST_OPEN\""), enumSource)
    assertTrue(enumSource.contains("case lowerSnakeCase = \"lower_snake_case\""), enumSource)
    assertTrue(enumSource.contains("case mixedKebabCase = \"mixed-kebab.case\""), enumSource)
    assertTrue(
      enumSource.contains("public var description: String {\n    return rawValue\n  }"),
      enumSource,
    )
    assertTrue(containerSource.contains("public let status: Status"), containerSource)
    assertTrue(containerSource.contains("public let alias: String"), containerSource)
    assertTrue(containerSource.contains("public let list: [String]"), containerSource)
    assertTrue(containerSource.contains("public let set: Set<String>"), containerSource)
    assertTrue(containerSource.contains("public let map: [String : String]"), containerSource)
    assertTrue(containerSource.contains("public let union: AnyValue"), containerSource)
  }

  @Test
  fun `rejects duplicate explicit Swift enum case names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("one", "two"),
              enumValueNames = listOf("same", "same"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("case name 'same' is used for multiple values"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `generates tolerant enums with raw-value associated fallback cases`(
    compiler: SwiftCompiler,
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
      val typeRegistry = SwiftTypeRegistry(setOf())
      SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
        .generateServiceTypes()
      assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))
    }

    val typeRegistry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(composedApi, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()
    typeRegistry.generateFiles(setOf(GeneratedTypeCategory.Model), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("TolerantEnumTests.swift"),
      """
      import XCTest
      @testable import SundayGenTest

      final class TolerantEnumTests: XCTestCase {

        func testEqualityAndHashing() {
          XCTAssertEqual(TaskState.pending, .pending)
          XCTAssertNotEqual(TaskState.unknown("future"), .unknown("other"))

          let states: Set<TaskState> = [.pending, .unknown("future"), .unknown("future")]
          XCTAssertEqual(states.count, 2)

          let grouped = Dictionary(grouping: ["first", "second"]) { _ in TaskState.unknown("future") }
          XCTAssertEqual(grouped[.unknown("future")], ["first", "second"])
        }

        func testAllCasesContainsOnlyKnownValues() {
          XCTAssertEqual(TaskState.allCases, [.pending, .running])
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))

    val source = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "Models/TaskState.swift")
    assertTrue(
      source.contains("public enum TaskState : CaseIterable, Codable, CustomStringConvertible, Equatable, Hashable,"),
      source,
    )
    assertTrue(source.contains("Sendable {"), source)
    assertTrue(source.contains("return [.pending, .running]"), source)
    assertTrue(source.contains("case unknown(String)"), source)
    assertTrue(source.contains("default: self = .unknown(rawValue)"), source)
    assertTrue(source.contains("case .unknown(let rawValue): return rawValue"), source)
    assertTrue(
      source.contains("public var description: String {\n    return rawValue\n  }"),
      source,
    )
    assertTrue(source.contains("try container.encode(rawValue)"), source)
  }

  @Test
  fun `generates tolerant discriminator hierarchy fallbacks`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobProgressUnknown.swift")
    val referenceSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobProgressRef.swift")
    val unionSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "JobEvent.swift")
    assertTrue(fallbackSource.contains("public let rawBody: [String : AnyValue]"), fallbackSource)
    assertTrue(fallbackSource.contains("try container.encode(rawBody)"), fallbackSource)
    assertTrue(referenceSource.contains("case unknown(JobProgressUnknown)"), referenceSource)
    assertTrue(
      referenceSource.contains("default: self = .unknown(try JobProgressUnknown(from: decoder))"),
      referenceSource,
    )
    assertTrue(unionSource.contains("case unknown(JobEventUnknown)"), unionSource)
  }

  @Test
  fun `decodes reusable discriminator mappings through reference unions`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(reusableDiscriminatorMappingApi(), typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    typeRegistry.generateFiles(
      setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model),
      compiler.srcDir,
    )
    val serviceSourcePath =
      Files
        .walk(compiler.srcDir)
        .use { paths ->
          paths
            .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == "API.swift" }
            .findFirst()
            .orElseThrow()
        }
    val serviceSourceRelativePath = compiler.srcDir.relativize(serviceSourcePath).toString()
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ReusableDiscriminatorMappingTests.swift"),
      reusableDiscriminatorMappingRuntimeTest,
    )

    assertTrue(compileAndTestGeneratedFiles(compiler))

    val referenceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/NotificationEventEnvelopeRef.swift",
      )
    val canonicalSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/EventOne.swift",
      )
    val notificationSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/Notification.swift",
      )
    val inheritedReferenceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/InheritedNotificationEventEnvelopeRef.swift",
      )
    val inheritedNotificationSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        "Models/InheritedNotification.swift",
      )
    val serviceSource =
      CompiledGeneratedSources.source(
        GeneratedCodeLanguage.Swift,
        serviceSourceRelativePath,
      )

    assertTrue(referenceSource.contains("case eventOne(EventOne)"), referenceSource)
    assertTrue(
      referenceSource.contains("case unrecognized(NotificationEventEnvelopeUnrecognized)"),
      referenceSource,
    )
    assertFalse(referenceSource.contains("public var value:"), referenceSource)
    assertFalse(referenceSource.contains("public init(value:"), referenceSource)
    assertTrue(canonicalSource.contains("public struct EventOne : EventEnvelope"), canonicalSource)
    assertFalse(canonicalSource.contains("NotificationEventEnvelope"), canonicalSource)
    assertTrue(notificationSource.contains("public let event: NotificationEventEnvelopeRef"), notificationSource)
    assertTrue(inheritedReferenceSource.contains("case eventOne(EventOne)"), inheritedReferenceSource)
    assertTrue(
      inheritedReferenceSource.contains("case unrecognized(InheritedNotificationEventEnvelopeUnrecognized)"),
      inheritedReferenceSource,
    )
    assertTrue(
      inheritedNotificationSource.contains("public let event: InheritedNotificationEventEnvelopeRef"),
      inheritedNotificationSource,
    )
    assertTrue(
      serviceSource.contains("Operation<Empty, NotificationEventEnvelopeRef, TransportType>"),
      serviceSource,
    )
    assertTrue(serviceSource.contains("AsyncStream<NotificationEventEnvelopeRef>"), serviceSource)
    assertTrue(
      serviceSource.contains("Operation<Empty, InheritedNotificationEventEnvelopeRef, TransportType>"),
      serviceSource,
    )
    assertTrue(serviceSource.contains("AsyncStream<InheritedNotificationEventEnvelopeRef>"), serviceSource)
    assertTrue(
      serviceSource.contains("decoder.decode(NotificationEventEnvelopeRef.self, from: data) }"),
      serviceSource,
    )
    assertFalse(serviceSource.contains("NotificationEventEnvelopeRef.self, from: data).value"), serviceSource)
  }

  @Test
  fun `generates tolerant typed event envelope fallbacks`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    assertTrue(compileTypes(compiler, typeRegistry.buildTypes()))

    val envelopeSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "EventEnvelope.swift")
    val fallbackSource = CompiledGeneratedSources.source(GeneratedCodeLanguage.Swift, "EventDataUnknown.swift")
    assertTrue(envelopeSource.contains("case unknown(UnknownEvent)"), envelopeSource)
    assertTrue(envelopeSource.contains("public let data: EventDataUnknown"), envelopeSource)
    assertTrue(envelopeSource.contains("self = .unknown(try UnknownEvent(from: decoder))"), envelopeSource)
    assertTrue(fallbackSource.contains("public let version: Int"), fallbackSource)
    assertTrue(fallbackSource.contains("public let rawBody: [String : AnyValue]"), fallbackSource)
  }

  @Test
  fun `rejects invalid explicit Swift enum case names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("x-enum-varnames entry '123'"), error.message)
    assertTrue(error.message!!.contains("for value 'wire'"), error.message)
    assertTrue(error.message!!.contains("invalid case name '123'"), error.message)
  }

  @Test
  fun `rejects unmappable Swift enum values without explicit names`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Enum API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Status",
              kind = GeneratedModel.Kind.ENUM,
              values = listOf("123"),
            ),
          ),
      )

    val error =
      assertThrows(GenerationException::class.java) {
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("maps to invalid case name '123'"), error.message)
    assertTrue(error.message!!.contains("x-enum-varnames"), error.message)
  }

  @Test
  fun `rejects enum values that do not match enum entries`() {
    val typeRegistry = SwiftTypeRegistry(setOf())
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
        SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
          .generateServiceTypes()
      }

    assertTrue(error.message!!.contains("Swift enum 'Status' value 'missing'"), error.message)
    assertTrue(error.message!!.contains("does not match any enum value"), error.message)
  }

  @Test
  fun `generates object union enums directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Union API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        services =
          listOf(
            GeneratedService(
              name = "UsersService",
              operations =
                listOf(
                  GeneratedOperation(
                    id = "getUser",
                    method = "GET",
                    path = "/users/{userId}",
                    responses =
                      listOf(
                        GeneratedResponse(
                          status = 200,
                          type = GeneratedTypeRef.named("UserProfile"),
                          mediaTypes = listOf("application/json"),
                        ),
                      ),
                  ),
                ),
            ),
          ),
        models =
          listOf(
            GeneratedModel(
              name = "UserSelfResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("createdAt", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "teams",
                    GeneratedTypeRef(
                      GeneratedTypeRef.Kind.ARRAY,
                      "array",
                      arguments = listOf(GeneratedTypeRef.scalar("string")),
                    ),
                    required = true,
                  ),
                ),
            ),
            GeneratedModel(
              name = "UserSummaryResponse",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("userId", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("email", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("displayName", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "UserProfile",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("UserSelfResponse"),
                  GeneratedTypeRef.named("UserSummaryResponse"),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("UsersAPI", builtTypes))
          .writeTo(this)
      }
    val unionSource =
      buildString {
        FileSpec
          .get("", findType("UserProfile", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      serviceSource.contains("public func getUser() throws -> Sunday.Operation<Empty, UserProfile, TransportType>"),
      serviceSource,
    )
    assertTrue(
      unionSource.contains("public enum UserProfile : Codable, CustomDebugStringConvertible, Sendable"),
      unionSource,
    )
    assertTrue(unionSource.contains("case userSelfResponse(UserSelfResponse)"), unionSource)
    assertTrue(unionSource.contains("case userSummaryResponse(UserSummaryResponse)"), unionSource)
    assertTrue(unionSource.contains("let container = try decoder.container(keyedBy: CodingKeys.self)"), unionSource)
    assertTrue(unionSource.contains("let keys = container.allKeys"), unionSource)
    assertTrue(unionSource.contains("if keys.contains(.createdAt) || keys.contains(.teams)"), unionSource)
    assertTrue(unionSource.contains("self = .userSelfResponse(try UserSelfResponse(from: decoder))"), unionSource)
    assertTrue(
      unionSource.contains("if keys.contains(.userId) && keys.contains(.email) && keys.contains(.displayName)"),
      unionSource,
    )
    assertTrue(unionSource.contains("self = .userSummaryResponse(try UserSummaryResponse(from: decoder))"), unionSource)
    assertTrue(unionSource.contains("DecodingError.typeMismatch(Self.self"), unionSource)
    assertTrue(unionSource.contains("case .userSelfResponse(let value):"), unionSource)
  }

  @Test
  fun `generates nested shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Nested API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Container",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("child", GeneratedTypeRef.named("Child"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Child",
              kind = GeneratedModel.Kind.OBJECT,
              nested =
                GeneratedNestedType(
                  enclosedIn = GeneratedTypeRef.named("Container"),
                  name = "Child",
                ),
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Container", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let child: Child"), source)
    assertTrue(source.contains("public struct Child"), source)
    assertTrue(source.contains("public let value: String"), source)
  }

  @Test
  fun `resolves duplicate imported model names by source identity from IR`(compiler: SwiftCompiler) {
    val librarySource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "libraries/common.raml")
    val mainSource = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "api.raml")
    val typeRegistry = SwiftTypeRegistry(setOf())
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
              targets = mapOf("swift" to GeneratedTarget(typeName = "MainTest")),
              properties =
                listOf(
                  GeneratedModelProperty("mainValue", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Test",
              kind = GeneratedModel.Kind.OBJECT,
              source = librarySource,
              targets = mapOf("swift" to GeneratedTarget(typeName = "LibraryTest")),
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

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val source =
      buildString {
        FileSpec
          .get("", findType("Consumer", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public let main: MainTest"), source)
    assertTrue(source.contains("public let library: LibraryTest"), source)
  }

  @Test
  fun `generates patchable shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Patch API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "PatchModel",
              kind = GeneratedModel.Kind.OBJECT,
              patchable = true,
              properties =
                listOf(
                  GeneratedModelProperty("value", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "nullable",
                    GeneratedTypeRef.scalar("string", nullable = true),
                    required = true,
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("PatchModel", builtTypes)
    val source =
      buildString {
        FileSpec
          .builder("", typeSpec.name)
          .addType(typeSpec)
          .apply {
            typeSpec.tag<AssociatedExtensions>()?.forEach { addExtension(it) }
          }.build()
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(source.contains("public struct PatchModel"), source)
    assertTrue(source.contains("Sendable"), source)
    assertTrue(source.contains("public let value: UpdateOp<String>?"), source)
    assertTrue(source.contains("public let nullable: PatchOp<String>?"), source)
    assertTrue(source.contains("value: UpdateOp<String>? = .none"), source)
    assertTrue(source.contains("nullable: PatchOp<String>? = .none"), source)
    assertTrue(source.contains("self.value = try container.decodeIfExists(String.self, forKey: .value)"), source)
    assertTrue(source.contains("try container.encodeIfExists(self.value, forKey: .value)"), source)
    assertTrue(source.contains("extension AnyPatchOp where Value == PatchModel"), source)
  }

  @Test
  fun `generates discriminator mapped object union decoders from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "Problems API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "RepoNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "WorkingGraphNotFoundProblem",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("type", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("title", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty("code", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "CheckoutTargetUnknownProblem",
              kind = GeneratedModel.Kind.UNION,
              aliases =
                listOf(
                  GeneratedTypeRef.named("RepoNotFoundProblem"),
                  GeneratedTypeRef.named("WorkingGraphNotFoundProblem"),
                ),
              discriminator = "code",
              discriminatorMappings =
                mapOf(
                  "TPG-REPO-404" to GeneratedTypeRef.named("RepoNotFoundProblem"),
                  "TPG-WG-404" to GeneratedTypeRef.named("WorkingGraphNotFoundProblem"),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val unionSource =
      buildString {
        FileSpec
          .get("", findType("CheckoutTargetUnknownProblem", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(
      unionSource.contains(
        "public enum CheckoutTargetUnknownProblem : Codable, CustomDebugStringConvertible, Sendable",
      ),
      unionSource,
    )
    assertTrue(
      unionSource.contains("let discriminatorValue = try container.decode(String.self, forKey: .code)"),
      unionSource,
    )
    assertTrue(unionSource.contains("if discriminatorValue == \"TPG-REPO-404\""), unionSource)
    assertTrue(
      unionSource.contains("self = .repoNotFoundProblem(try RepoNotFoundProblem(from: decoder))"),
      unionSource,
    )
    assertTrue(unionSource.contains("if discriminatorValue == \"TPG-WG-404\""), unionSource)
    assertFalse(unionSource.contains("object[\"type\"] != nil"), unionSource)
  }

  @Test
  fun `generates externally discriminated shared models directly from IR`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api =
      GeneratedApi(
        name = "External Discriminator API",
        source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.RAML, "memory"),
        models =
          listOf(
            GeneratedModel(
              name = "Parent",
              kind = GeneratedModel.Kind.OBJECT,
              discriminator = "kind",
              externallyDiscriminated = true,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Cat",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "cat",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Dog",
              kind = GeneratedModel.Kind.OBJECT,
              inherits = listOf(GeneratedTypeRef.named("Parent")),
              discriminator = "kind",
              discriminatorValue = "dog",
              properties =
                listOf(
                  GeneratedModelProperty("name", GeneratedTypeRef.scalar("string"), required = true),
                ),
            ),
            GeneratedModel(
              name = "Envelope",
              kind = GeneratedModel.Kind.OBJECT,
              properties =
                listOf(
                  GeneratedModelProperty("kind", GeneratedTypeRef.scalar("string"), required = true),
                  GeneratedModelProperty(
                    "payload",
                    GeneratedTypeRef.named("Parent"),
                    required = true,
                    externalDiscriminator = "kind",
                  ),
                ),
            ),
          ),
      )

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val parentSource =
      buildString {
        FileSpec
          .get("", findType("Parent", builtTypes))
          .writeTo(this)
      }
    val envelopeSource =
      buildString {
        FileSpec
          .get("", findType("Envelope", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(parentSource.contains("public protocol Parent"), parentSource)
    assertFalse(parentSource.contains("enum AnyRef"), parentSource)
    assertTrue(envelopeSource.contains("public struct Envelope"), envelopeSource)
    assertTrue(envelopeSource.contains("public let payload: Parent"), envelopeSource)
    assertTrue(envelopeSource.contains("switch self.kind"), envelopeSource)
    assertTrue(envelopeSource.contains("case \"cat\":"), envelopeSource)
    assertTrue(
      envelopeSource.contains("self.payload = try container.decode(Cat.self, forKey: .payload)"),
      envelopeSource,
    )
    assertTrue(envelopeSource.contains("case \"dog\":"), envelopeSource)
    assertTrue(envelopeSource.contains("try container.encode(self.payload as! Cat, forKey: .payload)"), envelopeSource)
  }

  @Test
  fun `generates inherited discriminated model snapshots from IR with existing Swift output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()

    assertTrue(compileTypes(compiler, builtTypes))
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output.swift",
      buildString {
        FileSpec
          .get("", findType("Parent", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output2.swift",
      buildString {
        FileSpec
          .get("", findType("Child1", builtTypes))
          .writeTo(this)
      },
    )
    assertSwiftSnapshot(
      "RamlDiscriminatedTypesTest/test-polymorphism-added-to-generated-classes-of-string-discriminated-types.output3.swift",
      buildString {
        FileSpec
          .get("", findType("Child2", builtTypes))
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-basic-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates inherited path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-inherited-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional query parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestQueryParamsTest/test-optional-query-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates constant headers from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestHeaderParamsTest/test-constant-header-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates mixed inline parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMixedParamsTest/test-generation-of-multiple-parameters-with-inline-type-definitions.output.swift",
    )
  }

  @Test
  fun `generates explicit security parameters from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-explicit-security-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "SwiftSundayIrGeneratorTest/test-explicit-security-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-basic-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-optional-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates explicit request body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-generation-of-body-parameter-with-explicit-content-type.output.swift",
    )
  }

  @Test
  fun `generates explicit response body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates polymorphic response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates inline response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates no content response from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-client-mode.output.swift",
    )
  }

  @Test
  fun `generates nullify methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation-with-nullify.output.swift",
    )
  }

  @Test
  fun `registers referenced problems from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseProblemsTest/test-api-problem-registration.output.swift",
    )
  }

  @Test
  fun `generates referenced problem types directly from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertTrue(compileTypes(compiler, builtTypes))
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "CreateFailedProblem" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "TestNotFoundProblem" })
    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates event source methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-source-method.output.swift",
    )
  }

  @Test
  fun `generates event stream methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-stream-method-generation.output.swift",
    )
  }

  @Test
  fun `generates common-base event stream methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseEventsTest/test-event-stream-method-generation-for-common-base-events.output.swift",
    )
  }

  @Test
  fun `generates base URL companion from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BaseUriTest/test-baseurl-generation-in-api.output.swift",
    )
  }

  @Test
  fun `generates request builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-request-builder-method-generation.output.swift",
    )
  }

  @Test
  fun `generates response builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-response-builder-method-generation.output.swift",
    )
  }

  private fun assertIrServiceSnapshot(
    compiler: SwiftCompiler,
    testUri: URI,
    snapshotPath: String,
    options: SwiftSundayOptions = swiftSundayTestOptions,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("API", builtTypes)

    assertTrue(compileTypes(compiler, builtTypes))
    assertSwiftSnapshot(
      snapshotPath,
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  private fun generateSwiftSundayFiles(
    compiler: SwiftCompiler,
    api: GeneratedApi,
    options: SwiftSundayOptions = swiftSundayTestOptions,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())

    SwiftSundayIrGenerator(api, typeRegistry, options)
      .generateServiceTypes()

    typeRegistry.generateFiles(
      setOf(GeneratedTypeCategory.Service, GeneratedTypeCategory.Model),
      compiler.srcDir,
    )
  }

  private fun avatarUploadApi(): GeneratedApi =
    GeneratedApi(
      name = "Avatar API",
      source = GeneratedSourceSpec(GeneratedSourceSpec.Kind.OPENAPI, "memory"),
      services =
        listOf(
          GeneratedService(
            name = "UsersService",
            operations =
              listOf(
                GeneratedOperation(
                  id = "putUserAvatar",
                  method = "PUT",
                  path = "/users/{userId}/avatar",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                      GeneratedParameter(
                        name = "contentType",
                        location = GeneratedParameter.Location.HEADER,
                        type = GeneratedTypeRef.named("AvatarContentType"),
                        required = true,
                        serializationName = "Content-Type",
                      ),
                    ),
                  requestBody =
                    GeneratedPayload(
                      type = GeneratedTypeRef.scalar("file"),
                      mediaTypes = listOf("application/octet-stream"),
                    ),
                ),
                GeneratedOperation(
                  id = "getUserAvatar",
                  method = "GET",
                  path = "/users/{userId}/avatar",
                  parameters =
                    listOf(
                      GeneratedParameter(
                        name = "userId",
                        location = GeneratedParameter.Location.PATH,
                        type = GeneratedTypeRef.scalar("string"),
                        required = true,
                      ),
                    ),
                  responses =
                    listOf(
                      GeneratedResponse(
                        status = 200,
                        type = GeneratedTypeRef.scalar("file"),
                        mediaTypes = listOf("image/png", "image/jpeg", "image/webp"),
                      ),
                    ),
                ),
              ),
          ),
        ),
      models =
        listOf(
          GeneratedModel(
            name = "AvatarContentType",
            kind = GeneratedModel.Kind.ENUM,
            values = listOf("image/png", "image/jpeg", "image/webp"),
          ),
        ),
    )
}
