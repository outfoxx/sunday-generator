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

  public func fetchEventsSimple() -> AsyncStream<any Base> {
    return self.transport.eventStream(
      method: .get,
      pathTemplate: "/test1",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: [.eventStream],
      headers: nil,
      decoder: { decoder, _, _, data, _ in try decoder.decode(BaseRef.self, from: data).value }
    )
  }

  public func fetchEventsDiscriminated() -> AsyncStream<any Base> {
    return self.transport.eventStream(
      method: .get,
      pathTemplate: "/test2",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: [.eventStream],
      headers: nil,
      decoder: { decoder, event, _, data, log in
        switch event {
        case "Test1": return try decoder.decode(Test1.self, from: data)
        case "Test2": return try decoder.decode(Test2.self, from: data)
        default:
          log.error("Unknown event type, ignoring event: event=\(event ?? "<none>", privacy: .public)")
          return nil
        }
      }
    )
  }

}
