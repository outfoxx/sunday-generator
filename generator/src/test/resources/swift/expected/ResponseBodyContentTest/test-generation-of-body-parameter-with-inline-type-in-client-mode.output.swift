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
    defaultAcceptTypes: [MediaType] = [.json],
    problemTypes: [ProblemRegistration] = API.problemTypes
  ) {
    self.transport = transport
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
    problemTypes.forEach { $0.register(on: transport) }
  }

  public func fetchTest() throws -> Sunday.Operation<Empty, FetchTestResponseBody, TransportType> {
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
        headers: nil
      )
    )
  }

  public struct FetchTestResponseBody : Codable, CustomDebugStringConvertible, Sendable {

    public let value: String
    public var debugDescription: String {
      return DescriptionBuilder(FetchTestResponseBody.self)
          .add(value, named: "value")
          .build()
    }

    public init(value: String) {
      self.value = value
    }

    public init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      self.value = try container.decode(String.self, forKey: .value)
    }

    public func encode(to encoder: Encoder) throws {
      var container = encoder.container(keyedBy: CodingKeys.self)
      try container.encode(self.value, forKey: .value)
    }

    public func withValue(value: String) -> FetchTestResponseBody {
      return FetchTestResponseBody(value: value)
    }

    fileprivate enum CodingKeys : String, CodingKey {

      case value = "value"

    }

  }

}
