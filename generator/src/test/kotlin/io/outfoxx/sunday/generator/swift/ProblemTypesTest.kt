package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GenerationMode.Client
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
@DisplayName("[Swift] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdType = findType("InvalidIdProblem", builtTypes)
    assertEquals(
      """
        import Foundation
        import Sunday

        public class InvalidIdProblem : Problem {

          public static let type: URL = URL(string: "http://example.com/invalid_id")!
          public let offendingId: String
          var description: String {
            return DescriptionBuilder(Self.self)
                .add(type, named: "type")
                .add(title, named: "title")
                .add(status, named: "status")
                .add(detail, named: "detail")
                .add(instance, named: "instance")
                .add(offendingId, named: "offendingId").build()
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
          var description: String {
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
  }

}
