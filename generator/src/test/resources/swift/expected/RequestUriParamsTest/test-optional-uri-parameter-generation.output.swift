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
    def2: Int? = 10,
    obj: Test? = nil,
    str: String? = nil,
    def1: String? = "test",
    int: Int? = nil,
    def: String
  ) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}",
      pathParameters: [
        "def2": def2 as Any?,
        "obj": obj as Any?,
        "str": str as Any?,
        "def1": def1 as Any?,
        "int": int as Any?,
        "def": def as Any?
      ].filter { $0.value != nil },
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
