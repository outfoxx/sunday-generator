package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Swift] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let arrayOfStrings: [String]
          public let arrayOfNullableStrings: [String?]
          public let nullableArrayOfStrings: [String]?
          public let nullableArrayOfNullableStrings: [String?]?
          public let declaredArrayOfStrings: [String]
          public let declaredArrayOfNullableStrings: [String?]
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(arrayOfStrings, named: "arrayOfStrings")
                .add(arrayOfNullableStrings, named: "arrayOfNullableStrings")
                .add(nullableArrayOfStrings, named: "nullableArrayOfStrings")
                .add(nullableArrayOfNullableStrings, named: "nullableArrayOfNullableStrings")
                .add(declaredArrayOfStrings, named: "declaredArrayOfStrings")
                .add(declaredArrayOfNullableStrings, named: "declaredArrayOfNullableStrings")
                .build()
          }

          public init(
            arrayOfStrings: [String],
            arrayOfNullableStrings: [String?],
            nullableArrayOfStrings: [String]?,
            nullableArrayOfNullableStrings: [String?]?,
            declaredArrayOfStrings: [String],
            declaredArrayOfNullableStrings: [String?]
          ) {
            self.arrayOfStrings = arrayOfStrings
            self.arrayOfNullableStrings = arrayOfNullableStrings
            self.nullableArrayOfStrings = nullableArrayOfStrings
            self.nullableArrayOfNullableStrings = nullableArrayOfNullableStrings
            self.declaredArrayOfStrings = declaredArrayOfStrings
            self.declaredArrayOfNullableStrings = declaredArrayOfNullableStrings
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.arrayOfStrings = try container.decode([String].self, forKey: .arrayOfStrings)
            self.arrayOfNullableStrings = try container.decode([String?].self, forKey: .arrayOfNullableStrings)
            self.nullableArrayOfStrings = try container.decodeIfPresent([String].self, forKey: .nullableArrayOfStrings)
            self.nullableArrayOfNullableStrings = try container.decodeIfPresent([String?].self, forKey: .nullableArrayOfNullableStrings)
            self.declaredArrayOfStrings = try container.decode([String].self, forKey: .declaredArrayOfStrings)
            self.declaredArrayOfNullableStrings = try container.decode([String?].self, forKey: .declaredArrayOfNullableStrings)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.arrayOfStrings, forKey: .arrayOfStrings)
            try container.encode(self.arrayOfNullableStrings, forKey: .arrayOfNullableStrings)
            try container.encodeIfPresent(self.nullableArrayOfStrings, forKey: .nullableArrayOfStrings)
            try container.encodeIfPresent(self.nullableArrayOfNullableStrings, forKey: .nullableArrayOfNullableStrings)
            try container.encode(self.declaredArrayOfStrings, forKey: .declaredArrayOfStrings)
            try container.encode(self.declaredArrayOfNullableStrings, forKey: .declaredArrayOfNullableStrings)
          }

          public func withArrayOfStrings(arrayOfStrings: [String]) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          public func withArrayOfNullableStrings(arrayOfNullableStrings: [String?]) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          public func withNullableArrayOfStrings(nullableArrayOfStrings: [String]?) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          public func withNullableArrayOfNullableStrings(nullableArrayOfNullableStrings: [String?]?) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          public func withDeclaredArrayOfStrings(declaredArrayOfStrings: [String]) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          public func withDeclaredArrayOfNullableStrings(declaredArrayOfNullableStrings: [String?]) -> Test {
            return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
                nullableArrayOfStrings: nullableArrayOfStrings,
                nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
                declaredArrayOfStrings: declaredArrayOfStrings,
                declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case arrayOfStrings = "arrayOfStrings"
            case arrayOfNullableStrings = "arrayOfNullableStrings"
            case nullableArrayOfStrings = "nullableArrayOfStrings"
            case nullableArrayOfNullableStrings = "nullableArrayOfNullableStrings"
            case declaredArrayOfStrings = "declaredArrayOfStrings"
            case declaredArrayOfNullableStrings = "declaredArrayOfNullableStrings"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated collections`(
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let implicit: [String]
          public let unspecified: [String]
          public let nonUnique: [String]
          public let unique: Set<String>
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(implicit, named: "implicit")
                .add(unspecified, named: "unspecified")
                .add(nonUnique, named: "nonUnique")
                .add(unique, named: "unique")
                .build()
          }

          public init(
            implicit: [String],
            unspecified: [String],
            nonUnique: [String],
            unique: Set<String>
          ) {
            self.implicit = implicit
            self.unspecified = unspecified
            self.nonUnique = nonUnique
            self.unique = unique
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.implicit = try container.decode([String].self, forKey: .implicit)
            self.unspecified = try container.decode([String].self, forKey: .unspecified)
            self.nonUnique = try container.decode([String].self, forKey: .nonUnique)
            self.unique = try container.decode(Set<String>.self, forKey: .unique)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.implicit, forKey: .implicit)
            try container.encode(self.unspecified, forKey: .unspecified)
            try container.encode(self.nonUnique, forKey: .nonUnique)
            try container.encode(self.unique, forKey: .unique)
          }

          public func withImplicit(implicit: [String]) -> Test {
            return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
          }

          public func withUnspecified(unspecified: [String]) -> Test {
            return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
          }

          public func withNonUnique(nonUnique: [String]) -> Test {
            return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
          }

          public func withUnique(unique: Set<String>) -> Test {
            return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case implicit = "implicit"
            case unspecified = "unspecified"
            case nonUnique = "nonUnique"
            case unique = "unique"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated primitive`(
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import Foundation
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let binary: Data
          public let nullableBinary: Data?
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(binary, named: "binary")
                .add(nullableBinary, named: "nullableBinary")
                .build()
          }

          public init(binary: Data, nullableBinary: Data?) {
            self.binary = binary
            self.nullableBinary = nullableBinary
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.binary = try container.decode(Data.self, forKey: .binary)
            self.nullableBinary = try container.decodeIfPresent(Data.self, forKey: .nullableBinary)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.binary, forKey: .binary)
            try container.encodeIfPresent(self.nullableBinary, forKey: .nullableBinary)
          }

          public func withBinary(binary: Data) -> Test {
            return Test(binary: binary, nullableBinary: nullableBinary)
          }

          public func withNullableBinary(nullableBinary: Data?) -> Test {
            return Test(binary: binary, nullableBinary: nullableBinary)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case binary = "binary"
            case nullableBinary = "nullableBinary"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

}
