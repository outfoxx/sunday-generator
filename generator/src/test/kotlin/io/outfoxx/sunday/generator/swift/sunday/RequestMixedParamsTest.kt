package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.GenerationMode.Client
import io.outfoxx.sunday.generator.swift.SwiftSundayGenerator
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[Swift/Sunday] [RAML] Request Mixed Params Test")
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
        import PotentCodables
        import Sunday

        public class API {

          public let requestFactory: RequestFactory
          public let defaultContentTypes: [MediaType]
          public let defaultAcceptTypes: [MediaType]

          public init(
            requestFactory: RequestFactory,
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          func fetchTest(
            select: FetchTestSelectUriParam,
            page: FetchTestPageQueryParam,
            xType: FetchTestXTypeHeaderParam
          ) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{select}",
              pathParameters: [
                "select": select
              ],
              queryParameters: [
                "page": page
              ],
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: [
                "x-type": xType
              ]
            )
          }

          public enum FetchTestPageQueryParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

          }

          public enum FetchTestSelectUriParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

          }

          public enum FetchTestXTypeHeaderParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get("", typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test @Disabled("Blocking issue: https://github.com/aml-org/amf/issues/830")
  fun `test generation of multiple parameters of same name with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI
  ) {

    val typeRegistry = SwiftTypeRegistry(Client, setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        SwiftSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertEquals(
      """
        import PotentCodables
        import Sunday

        public class API {

          public let requestFactory: RequestFactory
          public let defaultContentTypes: [MediaType]
          public let defaultAcceptTypes: [MediaType]

          public init(
            requestFactory: RequestFactory,
            defaultContentTypes: [MediaType] = [],
            defaultAcceptTypes: [MediaType] = [.json]
          ) {
            self.requestFactory = requestFactory
            self.defaultContentTypes = defaultContentTypes
            self.defaultAcceptTypes = defaultAcceptTypes
          }

          func fetchTest(
            type: FetchTestTypeUriParam,
            type_: FetchTestTypeQueryParam,
            type__: FetchTestTypeHeaderParam
          ) -> RequestResultPublisher<[String : AnyValue]> {
            return self.requestFactory.result(
              method: .get,
              pathTemplate: "/tests/{type}",
              pathParameters: [
                "type": type
              ],
              queryParameters: [
                "type": type_
              ],
              body: nil as Empty?,
              contentTypes: nil,
              acceptTypes: self.defaultAcceptTypes,
              headers: [
                "type": type__
              ]
            )
          }

          public enum FetchTestTypeQueryParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

          }

          public enum FetchTestTypeUriParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

          }

          public enum FetchTestTypeHeaderParam : String, CaseIterable, Codable {

            case all = "all"
            case limited = "limited"

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
