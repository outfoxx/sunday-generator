import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return [
      ProblemRegistration(type: TestNotFoundProblem.type, problemType: TestNotFoundProblem.self),
      ProblemRegistration(type: AnotherNotFoundProblem.type, problemType: AnotherNotFoundProblem.self)
    ]
  }
  public let transport: TransportType
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]
  private let clientSettings: ClientSettings?

  public init(
    transport: TransportType,
    defaultContentTypes: [MediaType] = [],
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

  public func fetchTest1(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test1",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest1"] ?? []
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404, 405],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
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

  public func fetchTest2(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test2",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest2"] ?? []
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
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

  public func fetchTest3(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test3",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest3"] ?? []
      ),
      nilify: Sunday.NilifySpec(
        statuses: [],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
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

  public func fetchTest4(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test4",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest4"] ?? []
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404, 405],
        problemTypes: []
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

  public func fetchTest5(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test5",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest5"] ?? []
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404],
        problemTypes: []
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

}
