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
    defaultContentTypes: [MediaType] = [],
    defaultAcceptTypes: [MediaType] = [],
    problemTypes: [ProblemRegistration] = API.problemTypes
  ) {
    self.transport = transport
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
    problemTypes.forEach { $0.register(on: transport) }
  }

  public func fetchEvents() -> EventSource {
    return self.transport.eventSource(
      method: .get,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: [.eventStream],
      headers: nil
    )}

}
