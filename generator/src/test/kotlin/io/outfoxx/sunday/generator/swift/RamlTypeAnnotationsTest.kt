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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.SwiftCompilerExtension
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  @Test
  fun `test type annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-type.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val type = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "Foundation.URL",
      type.propertySpecs.firstOrNull()?.type?.toString()
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Group", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Group : Codable, CustomDebugStringConvertible {

          public let value: String
          public var debugDescription: String {
            return DescriptionBuilder(Group.self)
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

          public func withValue(value: String) -> Group {
            return Group(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

          public class Member1 : Group {

            public let memberValue1: String
            public override var debugDescription: String {
              return DescriptionBuilder(Member1.self)
                  .add(value, named: "value")
                  .add(memberValue1, named: "memberValue1")
                  .build()
            }

            public init(value: String, memberValue1: String) {
              self.memberValue1 = memberValue1
              super.init(value: value)
            }

            public required init(from decoder: Decoder) throws {
              let container = try decoder.container(keyedBy: CodingKeys.self)
              self.memberValue1 = try container.decode(String.self, forKey: .memberValue1)
              try super.init(from: decoder)
            }

            public override func encode(to encoder: Encoder) throws {
              try super.encode(to: encoder)
              var container = encoder.container(keyedBy: CodingKeys.self)
              try container.encode(self.memberValue1, forKey: .memberValue1)
            }

            public override func withValue(value: String) -> Member1 {
              return Member1(value: value, memberValue1: memberValue1)
            }

            public func withMemberValue1(memberValue1: String) -> Member1 {
              return Member1(value: value, memberValue1: memberValue1)
            }

            fileprivate enum CodingKeys : String, CodingKey {

              case memberValue1 = "memberValue1"

            }

            public class Sub : Member1 {

              public let subMemberValue: String
              public override var debugDescription: String {
                return DescriptionBuilder(Sub.self)
                    .add(value, named: "value")
                    .add(memberValue1, named: "memberValue1")
                    .add(subMemberValue, named: "subMemberValue")
                    .build()
              }

              public init(
                value: String,
                memberValue1: String,
                subMemberValue: String
              ) {
                self.subMemberValue = subMemberValue
                super.init(value: value, memberValue1: memberValue1)
              }

              public required init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                self.subMemberValue = try container.decode(String.self, forKey: .subMemberValue)
                try super.init(from: decoder)
              }

              public override func encode(to encoder: Encoder) throws {
                try super.encode(to: encoder)
                var container = encoder.container(keyedBy: CodingKeys.self)
                try container.encode(self.subMemberValue, forKey: .subMemberValue)
              }

              public override func withValue(value: String) -> Sub {
                return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
              }

              public override func withMemberValue1(memberValue1: String) -> Sub {
                return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
              }

              public func withSubMemberValue(subMemberValue: String) -> Sub {
                return Sub(value: value, memberValue1: memberValue1, subMemberValue: subMemberValue)
              }

              fileprivate enum CodingKeys : String, CodingKey {

                case subMemberValue = "subMemberValue"

              }

            }

          }

          public class Member2 : Group {

            public let memberValue2: String
            public override var debugDescription: String {
              return DescriptionBuilder(Member2.self)
                  .add(value, named: "value")
                  .add(memberValue2, named: "memberValue2")
                  .build()
            }

            public init(value: String, memberValue2: String) {
              self.memberValue2 = memberValue2
              super.init(value: value)
            }

            public required init(from decoder: Decoder) throws {
              let container = try decoder.container(keyedBy: CodingKeys.self)
              self.memberValue2 = try container.decode(String.self, forKey: .memberValue2)
              try super.init(from: decoder)
            }

            public override func encode(to encoder: Encoder) throws {
              try super.encode(to: encoder)
              var container = encoder.container(keyedBy: CodingKeys.self)
              try container.encode(self.memberValue2, forKey: .memberValue2)
            }

            public override func withValue(value: String) -> Member2 {
              return Member2(value: value, memberValue2: memberValue2)
            }

            public func withMemberValue2(memberValue2: String) -> Member2 {
              return Member2(value: value, memberValue2: memberValue2)
            }

            fileprivate enum CodingKeys : String, CodingKey {

              case memberValue2 = "memberValue2"

            }

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
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Root", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Root : Codable, CustomDebugStringConvertible {

          public let value: String
          public var debugDescription: String {
            return DescriptionBuilder(Root.self)
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

          public func withValue(value: String) -> Root {
            return Root(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

          public class Group : Codable, CustomDebugStringConvertible {

            public let value: String
            public var debugDescription: String {
              return DescriptionBuilder(Group.self)
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

            public func withValue(value: String) -> Group {
              return Group(value: value)
            }

            fileprivate enum CodingKeys : String, CodingKey {

              case value = "value"

            }

            public class Member : Codable, CustomDebugStringConvertible {

              public let memberValue: String
              public var debugDescription: String {
                return DescriptionBuilder(Member.self)
                    .add(memberValue, named: "memberValue")
                    .build()
              }

              public init(memberValue: String) {
                self.memberValue = memberValue
              }

              public required init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                self.memberValue = try container.decode(String.self, forKey: .memberValue)
              }

              public func encode(to encoder: Encoder) throws {
                var container = encoder.container(keyedBy: CodingKeys.self)
                try container.encode(self.memberValue, forKey: .memberValue)
              }

              public func withMemberValue(memberValue: String) -> Member {
                return Member(memberValue: memberValue)
              }

              fileprivate enum CodingKeys : String, CodingKey {

                case memberValue = "memberValue"

              }

            }

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
  fun `test class hierarchy generated for 'nested' annotation using only library types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib2.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Root", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Root : Codable, CustomDebugStringConvertible {

          public let value: String
          public var debugDescription: String {
            return DescriptionBuilder(Root.self)
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

          public func withValue(value: String) -> Root {
            return Root(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

          public class Group : Codable, CustomDebugStringConvertible {

            public let value: String
            public var debugDescription: String {
              return DescriptionBuilder(Group.self)
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

            public func withValue(value: String) -> Group {
              return Group(value: value)
            }

            fileprivate enum CodingKeys : String, CodingKey {

              case value = "value"

            }

            public class Member : Codable, CustomDebugStringConvertible {

              public let memberValue: String
              public var debugDescription: String {
                return DescriptionBuilder(Member.self)
                    .add(memberValue, named: "memberValue")
                    .build()
              }

              public init(memberValue: String) {
                self.memberValue = memberValue
              }

              public required init(from decoder: Decoder) throws {
                let container = try decoder.container(keyedBy: CodingKeys.self)
                self.memberValue = try container.decode(String.self, forKey: .memberValue)
              }

              public func encode(to encoder: Encoder) throws {
                var container = encoder.container(keyedBy: CodingKeys.self)
                try container.encode(self.memberValue, forKey: .memberValue)
              }

              public func withMemberValue(memberValue: String) -> Member {
                return Member(memberValue: memberValue)
              }

              fileprivate enum CodingKeys : String, CodingKey {

                case memberValue = "memberValue"

              }

            }

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
  fun `test class generated swift implementations`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-swift-impl.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Foundation
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var className: String {
            return String(describing: Data.self) + "-value-" + "-literal"
          }
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .build()
          }

          public init() {
          }

          public required init(from decoder: Decoder) throws {
          }

          public func encode(to encoder: Encoder) throws {
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
  fun `test class hierarchy generated for externally discriminated types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

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

          public override var type: String {
            return "child2"
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

    val testTypeSpec = builtTypes[typeName(".Test")]
      ?: error("Test type is not defined")

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let parent: Parent
          public let parentType: String
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(parent, named: "parent")
                .add(parentType, named: "parentType")
                .build()
          }

          public init(parent: Parent, parentType: String) {
            self.parent = parent
            self.parentType = parentType
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.parentType = try container.decode(String.self, forKey: .parentType)
            switch self.parentType {
            case "Child1": self.parent = try container.decode(Child1.self, forKey: .parent)
            case "child2": self.parent = try container.decode(Child2.self, forKey: .parent)
            default:
                throw DecodingError.dataCorruptedError(
                  forKey: CodingKeys.parentType,
                  in: container,
                  debugDescription: "unsupported value for \"parentType\""
                )
            }
          }
        
          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.parentType, forKey: .parentType)
            switch self.parentType {
            case "Child1": try container.encode(self.parent as! Child1, forKey: .parent)
            case "child2": try container.encode(self.parent as! Child2, forKey: .parent)
            default:
                throw EncodingError.invalidValue(
                  self.parentType,
                  EncodingError.Context(
                    codingPath: encoder.codingPath + [CodingKeys.parentType],
                    debugDescription: "unsupported value for \"parentType\""
                  )
                )
            }
          }

          public func withParent(parent: Parent) -> Test {
            return Test(parent: parent, parentType: parentType)
          }

          public func withParentType(parentType: String) -> Test {
            return Test(parent: parent, parentType: parentType)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case parent = "parent"
            case parentType = "parentType"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", testTypeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test external discriminator must exist`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("externalDiscriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public let string: String
          public let int: Int
          public let bool: Bool
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(string, named: "string")
                .add(int, named: "int")
                .add(bool, named: "bool")
                .build()
          }

          public init(
            string: String,
            int: Int,
            bool: Bool
          ) {
            self.string = string
            self.int = int
            self.bool = bool
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.string = try container.decode(String.self, forKey: .string)
            self.int = try container.decode(Int.self, forKey: .int)
            self.bool = try container.decode(Bool.self, forKey: .bool)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.string, forKey: .string)
            try container.encode(self.int, forKey: .int)
            try container.encode(self.bool, forKey: .bool)
          }

          public func withString(string: String) -> Test {
            return Test(string: string, int: int, bool: bool)
          }

          public func withInt(int: Int) -> Test {
            return Test(string: string, int: int, bool: bool)
          }

          public func withBool(bool: Bool) -> Test {
            return Test(string: string, int: int, bool: bool)
          }

          func patch(source: [String : Any]) -> Patch {
            return Patch(string: source.keys.contains("string") ? Optional.some(string) : nil,
                int: source.keys.contains("int") ? Optional.some(int) : nil,
                bool: source.keys.contains("bool") ? Optional.some(bool) : nil)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case string = "string"
            case int = "int"
            case bool = "bool"

          }

          public struct Patch : Codable {

            let string: String?
            let int: Int?
            let bool: Bool?

            init(
              string: String?,
              int: Int?,
              bool: Bool?
            ) {
              self.string = string
              self.int = int
              self.bool = bool
            }

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
