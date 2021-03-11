package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Swift] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import PotentCodables
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let map: [String : Any]
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(map, named: "map")
                .build()
          }

          public init(map: [String : Any]) {
            self.map = map
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.map = try container.decode([String : AnyValue].self, forKey: .map).mapValues { ${'$'}0.unwrapped }
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.map.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .map)
          }

          public func withMap(map: [String : Any]) -> Test {
            return Test(map: map)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case map = "map"

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
  fun `test generated nullability of property types`(
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let fromNilUnion: String?
          public let notRequired: String?
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(fromNilUnion, named: "fromNilUnion")
                .add(notRequired, named: "notRequired")
                .build()
          }

          public init(fromNilUnion: String?, notRequired: String?) {
            self.fromNilUnion = fromNilUnion
            self.notRequired = notRequired
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.fromNilUnion = try container.decodeIfPresent(String.self, forKey: .fromNilUnion)
            self.notRequired = try container.decodeIfPresent(String.self, forKey: .notRequired)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.fromNilUnion, forKey: .fromNilUnion)
            try container.encodeIfPresent(self.notRequired, forKey: .notRequired)
          }

          public func withFromNilUnion(fromNilUnion: String?) -> Test {
            return Test(fromNilUnion: fromNilUnion, notRequired: notRequired)
          }

          public func withNotRequired(notRequired: String?) -> Test {
            return Test(fromNilUnion: fromNilUnion, notRequired: notRequired)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case fromNilUnion = "fromNilUnion"
            case notRequired = "notRequired"

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
  fun `test naming of types defined inline in property`(
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.lastOrNull()?.name
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val api = findType("API", generateTypes(testUri, typeRegistry))

    assertEquals(
      "FetchTestResponsePayload",
      api.typeSpecs.lastOrNull()?.name
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    val testSpec = builtTypes[DeclaredTypeName.typeName(".Test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[DeclaredTypeName.typeName(".Test2")]
    test2Spec ?: fail("No Test2 class defined")

    val emptySpec = builtTypes[DeclaredTypeName.typeName(".Empty")]
    emptySpec ?: fail("No Empty class defined")

    val test3Spec = builtTypes[DeclaredTypeName.typeName(".Test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let value: String
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(value, named: "value")
                .build()
          }

          public init(value: String) {
            self.value = value
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value = try container.decode(String.self, forKey: .value)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.value, forKey: .value)
          }

          public func withValue(value: String) -> Test {
            return Test(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", testSpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import Sunday

        public class Test2 : Test {

          public let value2: String
          public override var debugDescription: String {
            return DescriptionBuilder(Test2.self)
                .add(value, named: "value")
                .add(value2, named: "value2")
                .build()
          }

          public init(value: String, value2: String) {
            self.value2 = value2
            super.init(value: value)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value2 = try container.decode(String.self, forKey: .value2)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.value2, forKey: .value2)
          }

          public override func withValue(value: String) -> Test2 {
            return Test2(value: value, value2: value2)
          }

          public func withValue2(value2: String) -> Test2 {
            return Test2(value: value, value2: value2)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value2 = "value2"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", test2Spec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import Sunday

        public class Empty : Test2 {

          public override var debugDescription: String {
            return DescriptionBuilder(Empty.self)
                .add(value, named: "value")
                .add(value2, named: "value2")
                .build()
          }

          public override init(value: String, value2: String) {
            super.init(value: value, value2: value2)
          }

          public required init(from decoder: Decoder) throws {
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
          }

          public override func withValue(value: String) -> Empty {
            return Empty(value: value, value2: value2)
          }

          public override func withValue2(value2: String) -> Empty {
            return Empty(value: value, value2: value2)
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", emptySpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import Sunday

        public class Test3 : Empty {

          public let value3: String
          public override var debugDescription: String {
            return DescriptionBuilder(Test3.self)
                .add(value, named: "value")
                .add(value2, named: "value2")
                .add(value3, named: "value3")
                .build()
          }

          public init(
            value: String,
            value2: String,
            value3: String
          ) {
            self.value3 = value3
            super.init(value: value, value2: value2)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value3 = try container.decode(String.self, forKey: .value3)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.value3, forKey: .value3)
          }

          public override func withValue(value: String) -> Test3 {
            return Test3(value: value, value2: value2, value3: value3)
          }

          public override func withValue2(value2: String) -> Test3 {
            return Test3(value: value, value2: value2, value3: value3)
          }

          public func withValue3(value3: String) -> Test3 {
            return Test3(value: value, value2: value2, value3: value3)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value3 = "value3"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", test3Spec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(GenerationMode.Client, setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let someValue: String
          public let anotherValue: String
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(someValue, named: "someValue")
                .add(anotherValue, named: "anotherValue")
                .build()
          }

          public init(someValue: String, anotherValue: String) {
            self.someValue = someValue
            self.anotherValue = anotherValue
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.someValue = try container.decode(String.self, forKey: .someValue)
            self.anotherValue = try container.decode(String.self, forKey: .anotherValue)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.someValue, forKey: .someValue)
            try container.encode(self.anotherValue, forKey: .anotherValue)
          }

          public func withSomeValue(someValue: String) -> Test {
            return Test(someValue: someValue, anotherValue: anotherValue)
          }

          public func withAnotherValue(anotherValue: String) -> Test {
            return Test(someValue: someValue, anotherValue: anotherValue)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case someValue = "some-value"
            case anotherValue = "another_value"

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
