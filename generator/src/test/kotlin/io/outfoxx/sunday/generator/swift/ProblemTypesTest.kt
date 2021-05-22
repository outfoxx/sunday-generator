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
@DisplayName("[Swift] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val invalidIdType = findType("InvalidIdProblem", builtTypes)
    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/invalid_id")!
          public let offendingId: String
          public override var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId")
                .build()
          }

          init(offendingId: String, instance: URL? = nil) {
            self.offendingId = offendingId
            super.init(type: Self.type, title: "Invalid Id", status: 400,
                detail: "The id contains one or more invalid characters.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case offendingId = "offending_id"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", invalidIdType)
          .writeTo(this)
      }
    )

    val accountNotFoundType = findType("AccountNotFoundProblem", builtTypes)
    assertEquals(
      """
        import Foundation
        import Sunday

        public class AccountNotFoundProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/account_not_found")!
          public override var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .build()
          }

          init(instance: URL? = nil) {
            super.init(type: Self.type, title: "Account Not Found", status: 404,
                detail: "The requested account does not exist or you do not have permission to access it.",
                instance: instance, parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", accountNotFoundType)
          .writeTo(this)
      }
    )

    val testResolverType = findType("TestResolverProblem", builtTypes)
    assertEquals(
      """
        import Foundation
        import Sunday

        public class TestResolverProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/test_resolver")!
          public let optionalString: String?
          public let arrayOfStrings: [String]
          public let optionalArrayOfStrings: [String]?
          public override var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(optionalString, named: "optionalString")
                .add(arrayOfStrings, named: "arrayOfStrings")
                .add(optionalArrayOfStrings, named: "optionalArrayOfStrings")
                .build()
          }

          init(
            optionalString: String?,
            arrayOfStrings: [String],
            optionalArrayOfStrings: [String]?,
            instance: URL? = nil
          ) {
            self.optionalString = optionalString
            self.arrayOfStrings = arrayOfStrings
            self.optionalArrayOfStrings = optionalArrayOfStrings
            super.init(type: Self.type, title: "Test Resolve Type Reference", status: 500,
                detail: "Tests the resolveTypeReference function implementation.", instance: instance,
                parameters: nil)
          }

          public required init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.optionalString = try container.decodeIfPresent(String.self, forKey: CodingKeys.optionalString)
            self.arrayOfStrings = try container.decode([String].self, forKey: CodingKeys.arrayOfStrings)
            self.optionalArrayOfStrings = try container.decodeIfPresent([String].self, forKey: CodingKeys.optionalArrayOfStrings)
            try super.init(from: decoder)
          }

          public override func encode(to encoder: Encoder) throws {
            try super.encode(to: encoder)
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(self.optionalString, forKey: CodingKeys.optionalString)
            try container.encode(self.arrayOfStrings, forKey: CodingKeys.arrayOfStrings)
            try container.encode(self.optionalArrayOfStrings, forKey: CodingKeys.optionalArrayOfStrings)
          }

          fileprivate enum CodingKeys : String, CodingKey {

            case optionalString = "optionalString"
            case arrayOfStrings = "arrayOfStrings"
            case optionalArrayOfStrings = "optionalArrayOfStrings"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", testResolverType)
          .writeTo(this)
      }
    )
  }
}
