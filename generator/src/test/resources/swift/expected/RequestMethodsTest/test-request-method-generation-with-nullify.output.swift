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
    requestFactory.registerProblem(type: "http://example.com/test_not_found", problemType: TestNotFoundProblem.self)
    requestFactory.registerProblem(type: "http://example.com/another_not_found", problemType: AnotherNotFoundProblem.self)
  }

  public func fetchTest1OrNil(limit: Int) async throws -> Test? {
    return try await nilifyResponse(
        statuses: [404, 405],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      ) {
        try await fetchTest1(limit: limit)
      }
  }

  public func fetchTest1(limit: Int) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/test1",
      pathParameters: nil,
      queryParameters: [
        "limit": limit
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func fetchTest2OrNil(limit: Int) async throws -> Test? {
    return try await nilifyResponse(
        statuses: [404],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      ) {
        try await fetchTest2(limit: limit)
      }
  }

  public func fetchTest2(limit: Int) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/test2",
      pathParameters: nil,
      queryParameters: [
        "limit": limit
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func fetchTest3OrNil(limit: Int) async throws -> Test? {
    return try await nilifyResponse(
        statuses: [],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      ) {
        try await fetchTest3(limit: limit)
      }
  }

  public func fetchTest3(limit: Int) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/test3",
      pathParameters: nil,
      queryParameters: [
        "limit": limit
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func fetchTest4OrNil(limit: Int) async throws -> Test? {
    return try await nilifyResponse(
        statuses: [404, 405],
        problemTypes: []
      ) {
        try await fetchTest4(limit: limit)
      }
  }

  public func fetchTest4(limit: Int) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/test4",
      pathParameters: nil,
      queryParameters: [
        "limit": limit
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func fetchTest5OrNil(limit: Int) async throws -> Test? {
    return try await nilifyResponse(
        statuses: [404],
        problemTypes: []
      ) {
        try await fetchTest5(limit: limit)
      }
  }

  public func fetchTest5(limit: Int) async throws -> Test {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/test5",
      pathParameters: nil,
      queryParameters: [
        "limit": limit
      ],
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
