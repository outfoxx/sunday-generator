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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.GeneratedModel
import io.outfoxx.sunday.generator.ir.GeneratedModelProperty
import io.outfoxx.sunday.generator.ir.GeneratedSourceSpec
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Defaults Test")
@Tag("models")
@Tag("validation")
class SwiftSundayIrDefaultsTest : SwiftSundayIrTestSupport() {

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
          XCTAssertNil(try BooleanBounds().count)
          XCTAssertNil(try NumericBounds().count)
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
  @Tag("requests")
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
            patch.copy(
              name = "NullablePatch",
              properties =
                patch.properties.map { property ->
                  if (property.name ==
                    "nullable"
                  ) {
                    property.copy(required = false, allowedValues = listOf("valid", null))
                  } else {
                    property
                  }
                },
            ),
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
          for value in [try decoder.decode(FormattedDefaults.self, from: Data("{}".utf8))] {
            XCTAssertEqual(value.uuid, UUID(uuidString: "00000000-0000-0000-0000-000000000000"))
            XCTAssertEqual(value.timestamp?.timeIntervalSince1970, 1767225600.125)
            XCTAssertEqual(value.local?.timeIntervalSince1970, 1767225600)
            XCTAssertEqual(value.date?.timeIntervalSince1970, 1767225600)
            XCTAssertEqual(value.time?.timeIntervalSince1970, 1800)
            XCTAssertEqual(value.partial?.timeIntervalSince1970, 3600.5)
            XCTAssertEqual(value.url?.absoluteString, "https://example.com/a%20b")
            XCTAssertEqual(value.bytes, Data("hello".utf8))
          }
          XCTAssertEqual(String(data: try JSONEncoder().encode(FormattedDefaults()), encoding: .utf8), "{}")
          let nullData = Data(#"{"uuid":null,"timestamp":null,"bytes":null}"#.utf8)
          XCTAssertThrowsError(try decoder.decode(FormattedDefaults.self, from: nullData))
          XCTAssertNil(RequiredDefaults().uuid)
          XCTAssertThrowsError(try decoder.decode(RequiredDefaults.self, from: Data("{}".utf8)))
        }
        func testPatches() throws {
          let decoder = JSONDecoder()
          for json in ["{}"] {
            let patch = try decoder.decode(ConstrainedPatch.self, from: Data(json.utf8))
            XCTAssertEqual(patch.value, .unchanged)
            XCTAssertEqual(patch.nullable, .unchanged)
            XCTAssertEqual(String(data: try JSONEncoder().encode(patch), encoding: .utf8), "{}")
          }
          XCTAssertThrowsError(try decoder.decode(ConstrainedPatch.self, from: Data(#"{"value":null}"#.utf8)))
          XCTAssertThrowsError(try decoder.decode(ConstrainedPatch.self, from: Data(#"{"nullable":null}"#.utf8)))
          let deleted = try decoder.decode(NullablePatch.self, from: Data(#"{"nullable":null}"#.utf8))
          guard case .delete = deleted.nullable else { return XCTFail("null must remain a delete") }
          let supplied = try decoder.decode(ConstrainedPatch.self, from: Data(#"{"value":"valid","nullable":"valid"}"#.utf8))
          guard case .set("valid") = supplied.value, case .set("valid") = supplied.nullable else { return XCTFail("set lost") }
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
}
