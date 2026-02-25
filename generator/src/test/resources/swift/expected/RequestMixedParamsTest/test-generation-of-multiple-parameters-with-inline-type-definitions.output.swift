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

  public func fetchTest(
    select: FetchTestSelectUriParam,
    page: FetchTestPageQueryParam,
    xType: FetchTestXTypeHeaderParam
  ) async throws -> [String : AnyValue] {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/{select}",
      pathParameters: [
        "select": select
      ],
      queryParameters: [
        "page": page
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: [
        "x-type": xType
      ]
    )
  }

  public enum FetchTestSelectUriParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestPageQueryParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestXTypeHeaderParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

}
