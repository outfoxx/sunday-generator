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

  public static func baseURL(
    server: String = "master",
    environment: Environment = Environment.sbx,
    version: String = "1"
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

  public func fetchTest() async throws -> String {
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

}
