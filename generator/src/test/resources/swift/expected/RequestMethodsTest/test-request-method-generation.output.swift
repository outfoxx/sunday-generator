import Foundation
import Sunday

public class API {

  public let requestFactory: RequestFactory
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]

  public init(
    requestFactory: RequestFactory,
    defaultContentTypes: [MediaType] = [.json],
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

  public func putTest(body: Test) async throws -> Test {
    return try await self.requestFactory.result(
      method: .put,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: body,
      contentTypes: self.defaultContentTypes,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func postTest(body: Test) async throws -> Test {
    return try await self.requestFactory.result(
      method: .post,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: body,
      contentTypes: self.defaultContentTypes,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func patchTest(body: Test) async throws -> Test {
    return try await self.requestFactory.result(
      method: .patch,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: body,
      contentTypes: self.defaultContentTypes,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
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

  public func headTest() async throws {
    return try await self.requestFactory.result(
      method: .head,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func optionsTest() async throws {
    return try await self.requestFactory.result(
      method: .options,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func patchableTest(body: PatchableTest) async throws -> Test {
    return try await self.requestFactory.result(
      method: .patch,
      pathTemplate: "/tests2",
      pathParameters: nil,
      queryParameters: nil,
      body: body,
      contentTypes: self.defaultContentTypes,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func requestTest() async throws -> URLRequest {
    return try await self.requestFactory.request(
      method: .get,
      pathTemplate: "/request",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public func responseTest() async throws -> (Data?, HTTPURLResponse) {
    return try await self.requestFactory.response(
      method: .get,
      pathTemplate: "/response",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

}
