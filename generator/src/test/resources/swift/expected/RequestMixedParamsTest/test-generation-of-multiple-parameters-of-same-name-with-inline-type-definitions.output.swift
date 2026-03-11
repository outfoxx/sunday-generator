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
    type: FetchTestTypeUriParam,
    type_: FetchTestTypeQueryParam,
    type__: FetchTestTypeHeaderParam
  ) async throws -> [String : AnyValue] {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/{type}",
      pathParameters: [
        "type": type
      ],
      queryParameters: [
        "type": type_
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: [
        "type": type__
      ]
    )
  }

  public enum FetchTestTypeUriParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestTypeQueryParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestTypeHeaderParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

}
