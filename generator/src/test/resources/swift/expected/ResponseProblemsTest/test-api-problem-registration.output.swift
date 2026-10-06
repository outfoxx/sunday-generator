import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return [
      ProblemRegistration(type: InvalidIdProblem.type, problemType: InvalidIdProblem.self),
      ProblemRegistration(type: TestNotFoundProblem.type, problemType: TestNotFoundProblem.self)
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

}
