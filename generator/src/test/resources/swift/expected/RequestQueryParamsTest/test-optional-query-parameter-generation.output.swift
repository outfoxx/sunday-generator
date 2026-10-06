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

  public func fetchTest(
    obj: Test? = nil,
    str: String? = nil,
    int: Int? = nil,
    def1: String? = "test",
    def2: Int? = 10
  ) throws -> Sunday.Operation<Empty, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: [
          "obj": try ParameterValues.encode(obj),
          "str": try ParameterValues.encode(str),
          "int": try ParameterValues.encode(int),
          "def1": try ParameterValues.encode(def1),
          "def2": try ParameterValues.encode(def2)
        ].filter { $0.value != nil },
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        security: clientSettings?.bindings["fetchTest"] ?? [],
        parameterValidation: { [parameter = obj] in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          _ = parameter.map { value in !context.validatesNestedModels || value.isValid(mode, context: &context) } ?? true
          if !context.diagnostics.isEmpty { throw context.validationError }
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

}
