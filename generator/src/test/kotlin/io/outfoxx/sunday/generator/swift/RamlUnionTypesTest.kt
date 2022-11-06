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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, SwiftCompilerExtension::class)
@DisplayName("[Swift] [RAML] Union Types Test")
class RamlUnionTypesTest {

  @Test
  fun `test generated types for general union types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/unions-general.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import PotentCodables
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var any: Any
          public var duplicate: String
          public var nullable: String?
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(any, named: "any")
                .add(duplicate, named: "duplicate")
                .add(nullable, named: "nullable")
                .build()
          }

          public init(
            any: Any,
            duplicate: String,
            nullable: String?
          ) {
            self.any = any
            self.duplicate = duplicate
            self.nullable = nullable
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.any = try container.decode(AnyValue.self, forKey: .any).unwrapped
            self.duplicate = try container.decode(String.self, forKey: .duplicate)
            self.nullable = try container.decodeIfPresent(String.self, forKey: .nullable)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(AnyValue.wrapped(any), forKey: .any)
            try container.encode(self.duplicate, forKey: .duplicate)
            try container.encodeIfPresent(self.nullable, forKey: .nullable)
          }

          public func withAny(any: Any) -> Test {
            return Test(any: any, duplicate: duplicate, nullable: nullable)
          }

          public func withDuplicate(duplicate: String) -> Test {
            return Test(any: any, duplicate: duplicate, nullable: nullable)
          }

          public func withNullable(nullable: String?) -> Test {
            return Test(any: any, duplicate: duplicate, nullable: nullable)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case any = "any"
            case duplicate = "duplicate"
            case nullable = "nullable"

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
  fun `test generated types for common object types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/unions-common-objects.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import Sunday

        public class Test : Codable, CustomDebugStringConvertible {

          public var value: Base
          public var debugDescription: String {
            return DescriptionBuilder(Test.self)
                .add(value, named: "value")
                .build()
          }

          public init(value: Base) {
            self.value = value
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: Test.CodingKeys.self)
            self.value = try container.decode(Base.self, forKey: .value)
          }

          public func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: Test.CodingKeys.self)
            try container.encode(self.value, forKey: .value)
          }

          public func withValue(value: Base) -> Test {
            return Test(value: value)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case value = "value"

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  @Disabled("Swift doesn't allow types with the same name")
  fun `test generated types for similarly named but uncommon object types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/types/unions-uncommon-objects.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val typeSpec = findType("Test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        package io.test
        
      """.trimIndent(),
      buildString {
        FileSpec.get("io.test", typeSpec)
          .writeTo(this)
      },
    )
  }
}
