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
    obj: [String : Any],
    str: String,
    def: String,
    int: Int
  ) async throws -> [String : AnyValue] {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/{obj}/{str}/{int}/{def}",
      pathParameters: [
        "obj": obj,
        "str": str,
        "def": def,
        "int": int
      ],
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
