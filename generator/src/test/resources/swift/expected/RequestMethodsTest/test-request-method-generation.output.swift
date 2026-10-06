import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return []
  }
  public let transport: TransportType
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]
  private let clientSettings: ClientSettings?

  public init(
    transport: TransportType,
    defaultContentTypes: [MediaType] = [.json],
    defaultAcceptTypes: [MediaType] = [.json],
    problemTypes: [ProblemRegistration] = API.problemTypes,
    clientSettings: ClientSettings? = nil
  ) {
    self.transport = transport
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
    problemTypes.forEach { $0.register(on: transport) }
    self.clientSettings = clientSettings
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
        headers: nil,
        security: clientSettings?.bindings["fetchTest"] ?? []
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
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
        headers: nil,
        security: clientSettings?.bindings["putTest"] ?? [],
        requestValidation: { value in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
            throw context.validationError
          }
        }
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
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
        headers: nil,
        security: clientSettings?.bindings["postTest"] ?? [],
        requestValidation: { value in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
            throw context.validationError
          }
        }
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
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
        headers: nil,
        security: clientSettings?.bindings["patchTest"] ?? [],
        requestValidation: { value in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
            throw context.validationError
          }
        }
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
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
        headers: nil,
        security: clientSettings?.bindings["deleteTest"] ?? []
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
        headers: nil,
        security: clientSettings?.bindings["headTest"] ?? []
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
        headers: nil,
        security: clientSettings?.bindings["optionsTest"] ?? []
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
        headers: nil,
        security: clientSettings?.bindings["patchableTest"] ?? [],
        requestValidation: { value in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
            throw context.validationError
          }
        }
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
    )
  }

  public func requestTest() async throws -> TransportType.Request {
    return try await self.transport.transportRequest(
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/request",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["requestTest"] ?? []
      )
    )
  }

  public func responseTest() async throws -> TransportType.Response {
    return try await self.transport.transportResponse(
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/response",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["responseTest"] ?? []
      )
    )
  }

}
