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

  public static func baseURL(
    server: String? = "master",
    environment: Environment? = Environment.sbx,
    version: String? = "1"
  ) -> URI.Template {
    return URI.Template(
      format: "http://{server}.{environment}.example.com/api/{version}",
      parameters: [
        "server": server,
        "environment": environment,
        "version": version
      ]
    )
  }

  public func fetchTest() throws -> Sunday.Operation<Empty, String, TransportType> {
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
      )
    )
  }

}

/**
 * Resolves a server and invokes the application's transport factory exactly once. */
public func createAPI<TransportType : Transport>(
  config: TestAPIConfig,
  transportFactory: ClientTransportFactory<TransportType>,
  credentials: APICredentials = .init(),
  securityProfile: String? = nil,
  securitySelection: [String : APISecurityAlternative] = [:],
  defaultContentTypes: [MediaType] = [],
  defaultAcceptTypes: [MediaType] = [.json]
) throws -> API<TransportType> {
  let supplied: [String: any Credentials] = [:]
  let alternatives: [String: [[SecurityBinding]]]
  switch securityProfile {
  case nil:
    alternatives = [
      "fetchTest": [[

      ]],
    ]
  default: throw TokenProviderError()
  }
  guard securitySelection.keys.allSatisfy({ alternatives[$0] != nil }) else { throw TokenProviderError() }
  let selectedAlternatives = Dictionary(uniqueKeysWithValues: alternatives.map { entry in (entry.key, entry.value.filter { securitySelection[entry.key]?.matches($0) ?? true }) })
  let settings = try ClientSettings.resolve(baseURL: config.baseURL(), alternatives: selectedAlternatives, credentials: supplied)
  let transport = try transportFactory(settings)
  return API(transport: transport, defaultContentTypes: defaultContentTypes, defaultAcceptTypes: defaultAcceptTypes, clientSettings: settings)
}
