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

  public func fetchTest(bearerAuthorization: String, id: String) throws -> Sunday.Operation<Empty, String, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests/{id}",
        pathParameters: [
          "id": try ParameterValues.encode(id)
        ],
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: [
          "Authorization": try ParameterValues.encode(bearerAuthorization)
        ],
        security: clientSettings?.bindings["fetchTest"] ?? []
      )
    )
  }

}
