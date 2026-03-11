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

  public func fetchTest(category: FetchTestCategoryUriParam, type: FetchTestTypeUriParam) async throws -> [String : AnyValue] {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/{category}/{type}",
      pathParameters: [
        "category": category,
        "type": type
      ],
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public enum FetchTestCategoryUriParam : String, CaseIterable, Codable {

    case politics = "politics"
    case science = "science"

  }

  public enum FetchTestTypeUriParam : String, CaseIterable, Codable {

    case all = "all"
    case limited = "limited"

  }

}
