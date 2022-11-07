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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Scalar Types Test")
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Foundation
        import PotentCodables
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var bool: Bool
          public var string: String
          public var file: Data
          public var any: AnyValue
          public var `nil`: Void
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(bool, named: "bool")
                .add(string, named: "string")
                .add(file, named: "file")
                .add(any, named: "any")
                .add(`nil`, named: "nil")
                .build()
          }

          public init(
            bool: Bool,
            string: String,
            file: Data,
            any: AnyValue,
            `nil`: Void
          ) {
            self.bool = bool
            self.string = string
            self.file = file
            self.any = any
            self.`nil` = `nil`
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.bool = try container.decode(Bool.self, forKey: .bool)
            self.string = try container.decode(String.self, forKey: .string)
            self.file = try container.decode(Data.self, forKey: .file)
            self.any = try container.decode(AnyValue.self, forKey: .any)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.bool, forKey: .bool)
            try container.encode(self.string, forKey: .string)
            try container.encode(self.file, forKey: .file)
            try container.encode(self.any, forKey: .any)
          }

          public func withBool(bool: Bool) -> Test {
            return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
          }

          public func withString(string: String) -> Test {
            return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
          }

          public func withFile(file: Data) -> Test {
            return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
          }

          public func withAny(any: AnyValue) -> Test {
            return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
          }

          public func withNil(`nil`: Void) -> Test {
            return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case bool = "bool"
            case string = "string"
            case file = "file"
            case any = "any"
            case `nil` = "nil"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for integer scalar types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var int8: Int8
          public var int16: Int16
          public var int32: Int32
          public var int64: Int64
          public var int: Int
          public var long: Int64
          public var none: Int
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(int8, named: "int8")
                .add(int16, named: "int16")
                .add(int32, named: "int32")
                .add(int64, named: "int64")
                .add(int, named: "int")
                .add(long, named: "long")
                .add(none, named: "none")
                .build()
          }

          public init(
            int8: Int8,
            int16: Int16,
            int32: Int32,
            int64: Int64,
            int: Int,
            long: Int64,
            none: Int
          ) {
            self.int8 = int8
            self.int16 = int16
            self.int32 = int32
            self.int64 = int64
            self.int = int
            self.long = long
            self.none = none
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.int8 = try container.decode(Int8.self, forKey: .int8)
            self.int16 = try container.decode(Int16.self, forKey: .int16)
            self.int32 = try container.decode(Int32.self, forKey: .int32)
            self.int64 = try container.decode(Int64.self, forKey: .int64)
            self.int = try container.decode(Int.self, forKey: .int)
            self.long = try container.decode(Int64.self, forKey: .long)
            self.none = try container.decode(Int.self, forKey: .none)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.int8, forKey: .int8)
            try container.encode(self.int16, forKey: .int16)
            try container.encode(self.int32, forKey: .int32)
            try container.encode(self.int64, forKey: .int64)
            try container.encode(self.int, forKey: .int)
            try container.encode(self.long, forKey: .long)
            try container.encode(self.none, forKey: .none)
          }

          public func withInt8(int8: Int8) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withInt16(int16: Int16) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withInt32(int32: Int32) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withInt64(int64: Int64) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withInt(int: Int) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withLong(long: Int64) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          public func withNone(none: Int) -> Test {
            return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
                none: none)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case int8 = "int8"
            case int16 = "int16"
            case int32 = "int32"
            case int64 = "int64"
            case int = "int"
            case long = "long"
            case none = "none"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for float scalar types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var float: Float
          public var double: Double
          public var none: Double
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(float, named: "float")
                .add(double, named: "double")
                .add(none, named: "none")
                .build()
          }

          public init(
            float: Float,
            double: Double,
            none: Double
          ) {
            self.float = float
            self.double = double
            self.none = none
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.float = try container.decode(Float.self, forKey: .float)
            self.double = try container.decode(Double.self, forKey: .double)
            self.none = try container.decode(Double.self, forKey: .none)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.float, forKey: .float)
            try container.encode(self.double, forKey: .double)
            try container.encode(self.none, forKey: .none)
          }

          public func withFloat(float: Float) -> Test {
            return Test(float: float, double: double, none: none)
          }

          public func withDouble(double: Double) -> Test {
            return Test(float: float, double: double, none: none)
          }

          public func withNone(none: Double) -> Test {
            return Test(float: float, double: double, none: none)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case float = "float"
            case double = "double"
            case none = "none"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for date & time scalar types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Foundation
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var dateOnly: Date
          public var timeOnly: Date
          public var dateTimeOnly: Date
          public var dateTime: Date
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(dateOnly, named: "dateOnly")
                .add(timeOnly, named: "timeOnly")
                .add(dateTimeOnly, named: "dateTimeOnly")
                .add(dateTime, named: "dateTime")
                .build()
          }

          public init(
            dateOnly: Date,
            timeOnly: Date,
            dateTimeOnly: Date,
            dateTime: Date
          ) {
            self.dateOnly = dateOnly
            self.timeOnly = timeOnly
            self.dateTimeOnly = dateTimeOnly
            self.dateTime = dateTime
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.dateOnly = try container.decode(Date.self, forKey: .dateOnly)
            self.timeOnly = try container.decode(Date.self, forKey: .timeOnly)
            self.dateTimeOnly = try container.decode(Date.self, forKey: .dateTimeOnly)
            self.dateTime = try container.decode(Date.self, forKey: .dateTime)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.dateOnly, forKey: .dateOnly)
            try container.encode(self.timeOnly, forKey: .timeOnly)
            try container.encode(self.dateTimeOnly, forKey: .dateTimeOnly)
            try container.encode(self.dateTime, forKey: .dateTime)
          }

          public func withDateOnly(dateOnly: Date) -> Test {
            return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
                dateTime: dateTime)
          }

          public func withTimeOnly(timeOnly: Date) -> Test {
            return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
                dateTime: dateTime)
          }

          public func withDateTimeOnly(dateTimeOnly: Date) -> Test {
            return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
                dateTime: dateTime)
          }

          public func withDateTime(dateTime: Date) -> Test {
            return Test(dateOnly: dateOnly, timeOnly: timeOnly, dateTimeOnly: dateTimeOnly,
                dateTime: dateTime)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case dateOnly = "dateOnly"
            case timeOnly = "timeOnly"
            case dateTimeOnly = "dateTimeOnly"
            case dateTime = "dateTime"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
