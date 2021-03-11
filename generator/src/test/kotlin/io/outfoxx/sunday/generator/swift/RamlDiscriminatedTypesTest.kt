package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val parenTypeSpec = builtTypes[typeName(".Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        import Sunday

        public class Parent : Codable, CustomDebugStringConvertible {

          public var type: String {
            fatalError("abstract type method")
          }
          public var debugDescription: String {
            return DescriptionBuilder(Parent.self)
                .build()
          }

          public init() {
          }

          public required init(from decoder: Decoder) throws {
          }

          public func encode(to encoder: Encoder) throws {
          }

          public enum AnyRef : Codable, CustomDebugStringConvertible {

            case child1(Child1)
            case child2(Child2)

            public var value: Parent {
              switch self {
              case .child1(let value): return value
              case .child2(let value): return value
              }
            }
            public var debugDescription: String {
              switch self {
              case .child1(let value): return value.debugDescription
              case .child2(let value): return value.debugDescription
              }
            }

            public init(value: Parent) {
              switch value {
              case let value as Child1: self = .child1(value)
              case let value as Child2: self = .child2(value)
              default: fatalError("Invalid value type")
              }
            }

            public init(from decoder: Decoder) throws {
              let container = try decoder.container(keyedBy: CodingKeys.self)
              let type = try container.decode(String.self, forKey: CodingKeys.type)
              switch type {
              case "Child1": self = .child1(try Child1(from: decoder))
              case "child2": self = .child2(try Child2(from: decoder))
              default:
                  throw DecodingError.dataCorruptedError(
                    forKey: CodingKeys.type,
                    in: container,
                    debugDescription: "unsupported value for \"type\""
                  )
              }
            }

            public func encode(to encoder: Encoder) throws {
              var container = encoder.container(keyedBy: CodingKeys.self)
              switch self {
              case .child1(let value):
                  try container.encode("Child1", forKey: .type)
                  try value.encode(to: encoder)
              case .child2(let value):
                  try container.encode("child2", forKey: .type)
                  try value.encode(to: encoder)
              }
            }

          }

          fileprivate enum CodingKeys : String, CodingKey {

            case type = "type"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", parenTypeSpec)
          .writeTo(this)
      }
    )

    val child1TypeSpec = builtTypes[typeName(".Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        import Sunday

        public class Child1 : Parent {

          public override var type: String {
            return "Child1"
          }
          public let value: String?
          public let value1: Int
          public override var debugDescription: String {
            return DescriptionBuilder(Child1.self)
                .add(type, named: "type")
                .add(value, named: "value")
                .add(value1, named: "value1")
                .build()
          }

          public init(value: String?, value1: Int) {
            self.value = value
            self.value1 = value1
            super.init()
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value = try container.decodeIfPresent(String.self, forKey: .value)
            self.value1 = try container.decode(Int.self, forKey: .value1)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.value, forKey: .value)
            try container.encode(self.value1, forKey: .value1)
          }

          public func withValue(value: String?) -> Child1 {
            return Child1(value: value, value1: value1)
          }

          public func withValue1(value1: Int) -> Child1 {
            return Child1(value: value, value1: value1)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"
            case value1 = "value1"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", child1TypeSpec)
          .writeTo(this)
      }
    )

    val child2TypeSpec = builtTypes[typeName(".Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        import Sunday

        public class Child2 : Parent {

          public override var type: String {
            return "child2"
          }
          public let value: String?
          public let value2: Int
          public override var debugDescription: String {
            return DescriptionBuilder(Child2.self)
                .add(type, named: "type")
                .add(value, named: "value")
                .add(value2, named: "value2")
                .build()
          }

          public init(value: String?, value2: Int) {
            self.value = value
            self.value2 = value2
            super.init()
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value = try container.decodeIfPresent(String.self, forKey: .value)
            self.value2 = try container.decode(Int.self, forKey: .value2)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.value, forKey: .value)
            try container.encode(self.value2, forKey: .value2)
          }

          public func withValue(value: String?) -> Child2 {
            return Child2(value: value, value2: value2)
          }

          public func withValue2(value2: Int) -> Child2 {
            return Child2(value: value, value2: value2)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"
            case value2 = "value2"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", child2TypeSpec)
          .writeTo(this)
      }
    )

  }

  @Test
  fun `test polymorphism added to generated classes of enum discriminated types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val parenTypeSpec = builtTypes[typeName(".Parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        import Sunday

        public class Parent : Codable, CustomDebugStringConvertible {

          public var type: `Type` {
            fatalError("abstract type method")
          }
          public var debugDescription: String {
            return DescriptionBuilder(Parent.self)
                .build()
          }

          public init() {
          }

          public required init(from decoder: Decoder) throws {
          }

          public func encode(to encoder: Encoder) throws {
          }

          public enum AnyRef : Codable, CustomDebugStringConvertible {

            case child1(Child1)
            case child2(Child2)

            public var value: Parent {
              switch self {
              case .child1(let value): return value
              case .child2(let value): return value
              }
            }
            public var debugDescription: String {
              switch self {
              case .child1(let value): return value.debugDescription
              case .child2(let value): return value.debugDescription
              }
            }

            public init(value: Parent) {
              switch value {
              case let value as Child1: self = .child1(value)
              case let value as Child2: self = .child2(value)
              default: fatalError("Invalid value type")
              }
            }

            public init(from decoder: Decoder) throws {
              let container = try decoder.container(keyedBy: CodingKeys.self)
              let type = try container.decode(`Type`.self, forKey: CodingKeys.type)
              switch type {
              case .child1: self = .child1(try Child1(from: decoder))
              case .child2: self = .child2(try Child2(from: decoder))
              default:
                  throw DecodingError.dataCorruptedError(
                    forKey: CodingKeys.type,
                    in: container,
                    debugDescription: "unsupported value for \"type\""
                  )
              }
            }

            public func encode(to encoder: Encoder) throws {
              var container = encoder.container(keyedBy: CodingKeys.self)
              switch self {
              case .child1(let value):
                  try container.encode("Child1", forKey: .type)
                  try value.encode(to: encoder)
              case .child2(let value):
                  try container.encode("Child2", forKey: .type)
                  try value.encode(to: encoder)
              }
            }

          }

          fileprivate enum CodingKeys : String, CodingKey {

            case type = "type"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", parenTypeSpec)
          .writeTo(this)
      }
    )

    val child1TypeSpec = builtTypes[typeName(".Child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        import Sunday

        public class Child1 : Parent {

          public override var type: `Type` {
            return `Type`.child1
          }
          public let value: String?
          public override var debugDescription: String {
            return DescriptionBuilder(Child1.self)
                .add(type, named: "type")
                .add(value, named: "value")
                .build()
          }

          public init(value: String?) {
            self.value = value
            super.init()
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value = try container.decodeIfPresent(String.self, forKey: .value)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.value, forKey: .value)
          }

          public func withValue(value: String?) -> Child1 {
            return Child1(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", child1TypeSpec)
          .writeTo(this)
      }
    )

    val child2TypeSpec = builtTypes[typeName(".Child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        import Sunday

        public class Child2 : Parent {

          public override var type: `Type` {
            return `Type`.child2
          }
          public let value: String?
          public override var debugDescription: String {
            return DescriptionBuilder(Child2.self)
                .add(type, named: "type")
                .add(value, named: "value")
                .build()
          }

          public init(value: String?) {
            self.value = value
            super.init()
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.value = try container.decodeIfPresent(String.self, forKey: .value)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.value, forKey: .value)
          }

          public func withValue(value: String?) -> Child2 {
            return Child2(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", child2TypeSpec)
          .writeTo(this)
      }
    )

  }

}
