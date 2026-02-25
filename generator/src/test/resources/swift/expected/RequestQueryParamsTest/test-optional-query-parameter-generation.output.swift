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
    obj: Test? = nil,
    str: String? = nil,
    int: Int? = nil,
    def1: String? = "test",
    def2: Int? = 10
  ) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: [
        "obj": obj as Any?,
        "str": str as Any?,
        "int": int as Any?,
        "def1": def1 as Any?,
        "def2": def2 as Any?
      ].filter { $0.value != nil },
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
