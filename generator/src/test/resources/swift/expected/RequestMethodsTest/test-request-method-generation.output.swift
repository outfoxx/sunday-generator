import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return []
  }
  public let transport: TransportType
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]

  public init(
    transport: TransportType,
    defaultContentTypes: [MediaType] = [.json],
    defaultAcceptTypes: [MediaType] = [.json],
    problemTypes: [ProblemRegistration] = API.problemTypes
  ) {
    self.transport = transport
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
    problemTypes.forEach { $0.register(on: transport) }
  }

  public func fetchTest() throws -> Sunday.Operation<Empty, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func putTest(body: Test) throws -> Sunday.Operation<Test, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .put,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: body,
        contentTypes: self.defaultContentTypes,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func postTest(body: Test) throws -> Sunday.Operation<Test, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .post,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: body,
        contentTypes: self.defaultContentTypes,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func patchTest(body: Test) throws -> Sunday.Operation<Test, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .patch,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: body,
        contentTypes: self.defaultContentTypes,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func deleteTest() throws -> Sunday.Operation<Empty, Void, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .delete,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func headTest() throws -> Sunday.Operation<Empty, Void, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .head,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func optionsTest() throws -> Sunday.Operation<Empty, Void, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .options,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func patchableTest(body: PatchableTest) throws -> Sunday.Operation<PatchableTest, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .patch,
        pathTemplate: "/tests2",
        pathParameters: nil,
        queryParameters: nil,
        body: body,
        contentTypes: self.defaultContentTypes,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public func requestTest() async throws -> TransportType.Request {
    return try await self.transport.transportRequest(
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

  public func responseTest() async throws -> TransportType.Response {
    return try await self.transport.transportResponse(
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
