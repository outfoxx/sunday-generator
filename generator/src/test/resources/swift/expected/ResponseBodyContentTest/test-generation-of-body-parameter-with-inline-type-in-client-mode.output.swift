import Sunday

public class API {

  public let requestFactory: RequestFactory
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]

  public init(
    requestFactory: RequestFactory,
    defaultContentTypes: [MediaType] = [],
    defaultAcceptTypes: [MediaType] = [.json]
  ) {
    self.requestFactory = requestFactory
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
  }

  public func fetchTest() async throws -> FetchTestResponseBody {
    return try await self.requestFactory.result(
      method: .get,
      pathTemplate: "/tests",
      pathParameters: nil,
      queryParameters: nil,
      body: Empty.none,
      contentTypes: nil,
      acceptTypes: self.defaultAcceptTypes,
      headers: nil
    )
  }

  public class FetchTestResponseBody : Codable, CustomDebugStringConvertible {

    public var value: String
    public var debugDescription: String {
      return DescriptionBuilder(FetchTestResponseBody.self)
          .add(value, named: "value")
          .build()
    }

    public init(value: String) {
      self.value = value
    }

    public required init(from decoder: Decoder) throws {
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
