import Sunday

public class API {

  public let requestFactory: RequestFactory
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]

  public init(
    requestFactory: RequestFactory,
    defaultContentTypes: [MediaType] = [],
    defaultAcceptTypes: [MediaType] = []
  ) {
    self.requestFactory = requestFactory
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
  }

  public func fetchEventsSimple() -> AsyncStream<Base> {
    return self.requestFactory.eventStream(
      method: .get,
      pathTemplate: "/test1",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: [.eventStream],
      headers: nil,
      decoder: { decoder, _, _, data, _ in try decoder.decode(Base.AnyRef.self, from: data).value }
    )
  }

  public func fetchEventsDiscriminated() -> AsyncStream<Base> {
    return self.requestFactory.eventStream(
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
