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
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URI

@SwiftTest
@DisplayName("[Swift] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
      import PotentCodables
      import Sunday

      public class Test : Codable, CustomDebugStringConvertible {

        public var map: [String : Any]
        public var array: [Any]
        public var debugDescription: String {
          return DescriptionBuilder(Test.self)
              .add(map, named: "map")
              .add(array, named: "array")
              .build()
        }

        public init(map: [String : Any], array: [Any]) {
          self.map = map
          self.array = array
        }

        public required init(from decoder: Decoder) throws {
          let container = try decoder.container(keyedBy: CodingKeys.self)
          self.map = try container.decode([String : AnyValue].self, forKey: .map).mapValues { ${'$'}0.unwrapped }
          self.array = try container.decode([AnyValue].self, forKey: .array).map { ${'$'}0.unwrapped }
        }

        public func encode(to encoder: Encoder) throws {
          var container = encoder.container(keyedBy: CodingKeys.self)
          try container.encode(self.map.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .map)
          try container.encode(self.array.map { try AnyValue.wrapped(${'$'}0) }, forKey: .array)
        }

        public func withMap(map: [String : Any]) -> Test {
          return Test(map: map, array: array)
        }

        public func withArray(array: [Any]) -> Test {
          return Test(map: map, array: array)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case map = "map"
          case array = "array"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of property types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val testTypeSpec = findType("Test", builtTypes)

    assertEquals(
      """
      import Sunday

      public class Test : Codable, CustomDebugStringConvertible {

        public var fromNilUnion: String?
        public var notRequired: String?
        public var debugDescription: String {
          return DescriptionBuilder(Test.self)
              .add(fromNilUnion, named: "fromNilUnion")
              .add(notRequired, named: "notRequired")
              .build()
        }

        public init(fromNilUnion: String?, notRequired: String? = nil) {
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
        FileSpec
          .get("", testTypeSpec)
          .writeTo(this)
      },
    )

    val test2TypeSpec = findType("Test2", builtTypes)

    assertEquals(
      """
      import PotentCodables
      import Sunday

      public class Test2 : Codable, CustomDebugStringConvertible {

        public var optionalObject: [String : Any]?
        public var nillableObject: [String : Any]?
        public var optionalHierarchy: Parent?
        public var nillableHierarchy: Parent?
        public var debugDescription: String {
          return DescriptionBuilder(Test2.self)
              .add(optionalObject, named: "optionalObject")
              .add(nillableObject, named: "nillableObject")
              .add(optionalHierarchy, named: "optionalHierarchy")
              .add(nillableHierarchy, named: "nillableHierarchy")
              .build()
        }

        public init(
          optionalObject: [String : Any]? = nil,
          nillableObject: [String : Any]?,
          optionalHierarchy: Parent? = nil,
          nillableHierarchy: Parent?
        ) {
          self.optionalObject = optionalObject
          self.nillableObject = nillableObject
          self.optionalHierarchy = optionalHierarchy
          self.nillableHierarchy = nillableHierarchy
        }

        public required init(from decoder: Decoder) throws {
          let container = try decoder.container(keyedBy: CodingKeys.self)
          self.optionalObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .optionalObject)?.mapValues { ${'$'}0.unwrapped }
          self.nillableObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .nillableObject)?.mapValues { ${'$'}0.unwrapped }
          self.optionalHierarchy = try container.decodeIfPresent(Parent.AnyRef.self, forKey: .optionalHierarchy)?.value
          self.nillableHierarchy = try container.decodeIfPresent(Parent.AnyRef.self, forKey: .nillableHierarchy)?.value
        }

        public func encode(to encoder: Encoder) throws {
          var container = encoder.container(keyedBy: CodingKeys.self)
          try container.encodeIfPresent(self.optionalObject?.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .optionalObject)
          try container.encodeIfPresent(self.nillableObject?.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .nillableObject)
          try container.encodeIfPresent(self.optionalHierarchy, forKey: .optionalHierarchy)
          try container.encodeIfPresent(self.nillableHierarchy, forKey: .nillableHierarchy)
        }

        public func withOptionalObject(optionalObject: [String : Any]?) -> Test2 {
          return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
              optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
        }

        public func withNillableObject(nillableObject: [String : Any]?) -> Test2 {
          return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
              optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
        }

        public func withOptionalHierarchy(optionalHierarchy: Parent?) -> Test2 {
          return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
              optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
        }

        public func withNillableHierarchy(nillableHierarchy: Parent?) -> Test2 {
          return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
              optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case optionalObject = "optionalObject"
          case nillableObject = "nillableObject"
          case optionalHierarchy = "optionalHierarchy"
          case nillableHierarchy = "nillableHierarchy"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", test2TypeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "Value",
      typeSpec.typeSpecs.lastOrNull()?.name,
    )
  }

  @Test
  fun `test naming of types defined inline in resource`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val api = findType("API", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "FetchTestResponse0Payload",
      api.typeSpecs.lastOrNull()?.name,
    )
  }

  @Test
  fun `test generated classes for object hierarchy`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

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

        public var value: String
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
        FileSpec
          .get("", testSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      import Sunday

      public class Test2 : Test {

        public var value2: String
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
        FileSpec
          .get("", test2Spec)
          .writeTo(this)
      },
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
        FileSpec
          .get("", emptySpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      import Sunday

      public class Test3 : Empty {

        public var value3: String
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
        FileSpec
          .get("", test3Spec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated classes for object hierarchy with empty root`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits-empty-root.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val rootSpec = builtTypes[DeclaredTypeName.typeName(".Root")]
    rootSpec ?: fail("No Root class defined")

    val branchSpec = builtTypes[DeclaredTypeName.typeName(".Branch")]
    branchSpec ?: fail("No Branch class defined")

    val leafSpec = builtTypes[DeclaredTypeName.typeName(".Leaf")]
    leafSpec ?: fail("No Leaf class defined")

    assertEquals(
      """
      import Sunday

      public class Root : Codable, CustomDebugStringConvertible {

        public var debugDescription: String {
          return DescriptionBuilder(Root.self)
              .build()
        }

        public init() {
        }

        public required init(from decoder: Decoder) throws {
          let _ = try decoder.container(keyedBy: CodingKeys.self)
        }

        public func encode(to encoder: Encoder) throws {
          let _ = encoder.container(keyedBy: CodingKeys.self)
        }

        fileprivate enum CodingKeys : CodingKey {
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", rootSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      import Sunday

      public class Branch : Root {

        public var value: String
        public override var debugDescription: String {
          return DescriptionBuilder(Branch.self)
              .add(value, named: "value")
              .build()
        }

        public init(value: String) {
          self.value = value
          super.init()
        }

        public required init(from decoder: Decoder) throws {
          let container = try decoder.container(keyedBy: CodingKeys.self)
          self.value = try container.decode(String.self, forKey: .value)
          try super.init(from: decoder)
        }

        public override func encode(to encoder: Encoder) throws {
          try super.encode(to: encoder)
          var container = encoder.container(keyedBy: CodingKeys.self)
          try container.encode(self.value, forKey: .value)
        }

        public func withValue(value: String) -> Branch {
          return Branch(value: value)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case value = "value"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", branchSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      import Sunday

      public class Leaf : Branch {

        public var value2: String
        public override var debugDescription: String {
          return DescriptionBuilder(Leaf.self)
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

        public override func withValue(value: String) -> Leaf {
          return Leaf(value: value, value2: value2)
        }

        public func withValue2(value2: String) -> Leaf {
          return Leaf(value: value, value2: value2)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case value2 = "value2"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", leafSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
      import Sunday

      public class Test : Codable, CustomDebugStringConvertible {

        public var someValue: String
        public var anotherValue: String
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
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursive property`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
      import Sunday

      public class Test : Codable, CustomDebugStringConvertible {

        public var parent: Test?
        public var other: Test?
        public var children: [Test]
        public var debugDescription: String {
          return DescriptionBuilder(Test.self)
              .add(parent, named: "parent")
              .add(other, named: "other")
              .add(children, named: "children")
              .build()
        }

        public init(
          parent: Test?,
          other: Test? = nil,
          children: [Test]
        ) {
          self.parent = parent
          self.other = other
          self.children = children
        }

        public required init(from decoder: Decoder) throws {
          let container = try decoder.container(keyedBy: CodingKeys.self)
          self.parent = try container.decodeIfPresent(Test.self, forKey: .parent)
          self.other = try container.decodeIfPresent(Test.self, forKey: .other)
          self.children = try container.decode([Test].self, forKey: .children)
        }

        public func encode(to encoder: Encoder) throws {
          var container = encoder.container(keyedBy: CodingKeys.self)
          try container.encodeIfPresent(self.parent, forKey: .parent)
          try container.encodeIfPresent(self.other, forKey: .other)
          try container.encode(self.children, forKey: .children)
        }

        public func withParent(parent: Test?) -> Test {
          return Test(parent: parent, other: other, children: children)
        }

        public func withOther(other: Test?) -> Test {
          return Test(parent: parent, other: other, children: children)
        }

        public func withChildren(children: [Test]) -> Test {
          return Test(parent: parent, other: other, children: children)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case parent = "parent"
          case other = "other"
          case children = "children"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursion down to a complex leaf`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive-complex-leaf.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpecs = generateTypes(testUri, typeRegistry, compiler)
    val typeSpec = findType("Node", typeSpecs)

    assertNotNull(typeSpec)
    assertNotNull(findType("NodeType", typeSpecs))
    assertNotNull(findType("NodeList", typeSpecs))
    assertNotNull(findType("NodeValue", typeSpecs))
    assertNotNull(findType("NodeMap", typeSpecs))

    assertEquals(
      """
      import Sunday

      public class Node : Codable, CustomDebugStringConvertible {

        public var type: NodeType {
          fatalError("abstract type method")
        }
        public var debugDescription: String {
          return DescriptionBuilder(Node.self)
              .build()
        }

        public init() {
        }

        public required init(from decoder: Decoder) throws {
          let _ = try decoder.container(keyedBy: Node.CodingKeys.self)
        }

        public func encode(to encoder: Encoder) throws {
          var container = encoder.container(keyedBy: Node.CodingKeys.self)
          try container.encode(self.type, forKey: .type)
        }

        public enum AnyRef : Codable, CustomDebugStringConvertible {

          case list(NodeList)
          case value(NodeValue)
          case map(NodeMap)

          public var value: Node {
            switch self {
            case .list(let value): return value
            case .value(let value): return value
            case .map(let value): return value
            }
          }
          public var debugDescription: String {
            switch self {
            case .list(let value): return value.debugDescription
            case .value(let value): return value.debugDescription
            case .map(let value): return value.debugDescription
            }
          }

          public init(value: Node) {
            switch value {
            case let value as NodeList: self = .list(value)
            case let value as NodeValue: self = .value(value)
            case let value as NodeMap: self = .map(value)
            default: fatalError("Invalid value type")
            }
          }

          public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: Node.CodingKeys.self)
            let type = try container.decode(NodeType.self, forKey: Node.CodingKeys.type)
            switch type {
            case .list: self = .list(try NodeList(from: decoder))
            case .value: self = .value(try NodeValue(from: decoder))
            case .map: self = .map(try NodeMap(from: decoder))
            }
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.singleValueContainer()
            switch self {
            case .list(let value): try container.encode(value)
            case .value(let value): try container.encode(value)
            case .map(let value): try container.encode(value)
            }
          }

        }

        fileprivate enum CodingKeys : String, CodingKey {

          case type = "type"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test default identifiable generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/obj-with-id.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf(SwiftTypeRegistry.Option.DefaultIdentifiableTypes))

    val types = generateTypes(testUri, typeRegistry, compiler)
    val typeSpec = findType("Test", types)
    val typeSpec2 = findType("Test2", types)

    assertEquals(
      """
      import Sunday

      public class Test : Codable, CustomDebugStringConvertible, Identifiable {

        public var id: String
        public var debugDescription: String {
          return DescriptionBuilder(Test.self)
              .add(id, named: "id")
              .build()
        }

        public init(id: String) {
          self.id = id
        }

        public required init(from decoder: Decoder) throws {
          let container = try decoder.container(keyedBy: CodingKeys.self)
          self.id = try container.decode(String.self, forKey: .id)
        }

        public func encode(to encoder: Encoder) throws {
          var container = encoder.container(keyedBy: CodingKeys.self)
          try container.encode(self.id, forKey: .id)
        }

        public func withId(id: String) -> Test {
          return Test(id: id)
        }

        fileprivate enum CodingKeys : String, CodingKey {

          case id = "id"

        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
      import Sunday

      public class Test2 : Test {

        public override var debugDescription: String {
          return DescriptionBuilder(Test2.self)
              .add(id, named: "id")
              .build()
        }

        public override init(id: String) {
          super.init(id: id)
        }

        public required init(from decoder: Decoder) throws {
          try super.init(from: decoder)
        }

        public override func encode(to encoder: Encoder) throws {
          try super.encode(to: encoder)
        }

        public override func withId(id: String) -> Test2 {
          return Test2(id: id)
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get("", typeSpec2)
          .writeTo(this)
      },
    )
  }
}
