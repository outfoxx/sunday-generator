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

  public func putTest(xCustom: String) throws -> Sunday.Operation<Empty, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .put,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: [
          "Expect": try ParameterValues.encode("100-continue"),
          "x-custom": try ParameterValues.encode(xCustom)
        ],
        security: clientSettings?.bindings["putTest"] ?? []
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
