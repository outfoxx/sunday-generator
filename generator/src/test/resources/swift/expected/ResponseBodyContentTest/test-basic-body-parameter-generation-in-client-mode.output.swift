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

  public func fetchTest() async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func fetchDerivedTest() async throws -> Base {
    return try await (self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/derived",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    ) as Base.AnyRef).value
  }

}
