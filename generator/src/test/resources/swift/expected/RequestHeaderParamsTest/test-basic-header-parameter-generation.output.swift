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
    obj: Test,
    strReq: String,
    int: Int = 5
  ) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: [
        "obj": obj,
        "str-req": strReq,
        "int": int
      ]
    )
  }

  public func deleteTest() async throws {
    return try await self.requestFactory.result(
      method: .delete,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
