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
import io.outfoxx.swiftpoet.DeclaredTypeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
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
            self.map = try container.decode([String : AnyValue].self, forKey: .map).mapValues { ${'$'}0.unwrapped as Any }
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
        FileSpec.get("", testTypeSpec)
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
          public var debugDescription: String {
            return DescriptionBuilder(Test2.self)
                .add(optionalObject, named: "optionalObject")
                .add(nillableObject, named: "nillableObject")
                .build()
          }
        
          public init(optionalObject: [String : Any]?, nillableObject: [String : Any]?) {
            self.optionalObject = optionalObject
            self.nillableObject = nillableObject
          }
        
          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.optionalObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .optionalObject)?.mapValues { ${'$'}0.unwrapped as Any }
            self.nillableObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .nillableObject)?.mapValues { ${'$'}0.unwrapped as Any }
          }
        
          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encodeIfPresent(self.optionalObject?.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .optionalObject)
            try container.encodeIfPresent(self.nillableObject?.mapValues { try AnyValue.wrapped(${'$'}0) }, forKey: .nillableObject)
          }
        
          public func withOptionalObject(optionalObject: [String : Any]?) -> Test2 {
            return Test2(optionalObject: optionalObject, nillableObject: nillableObject)
          }
        
          public func withNillableObject(nillableObject: [String : Any]?) -> Test2 {
            return Test2(optionalObject: optionalObject, nillableObject: nillableObject)
          }
        
          fileprivate enum CodingKeys : String, CodingKey {
        
            case optionalObject = "optionalObject"
            case nillableObject = "nillableObject"
        
          }
        
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", test2TypeSpec)
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
        FileSpec.get("", testSpec)
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
        FileSpec.get("", test2Spec)
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
        FileSpec.get("", emptySpec)
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
        FileSpec.get("", test3Spec)
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
          }

          public func encode(to encoder: Encoder) throws {
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("", rootSpec)
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
        FileSpec.get("", branchSpec)
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
        FileSpec.get("", leafSpec)
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
        FileSpec.get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
