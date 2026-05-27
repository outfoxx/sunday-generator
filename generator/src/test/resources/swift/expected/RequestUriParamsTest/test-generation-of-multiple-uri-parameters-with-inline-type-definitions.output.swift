import PotentCodables
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

  public func fetchTest(category: FetchTestCategoryUriParam, type: FetchTestTypeUriParam) throws -> Sunday.Operation<Empty, [String : AnyValue], TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests/{category}/{type}",
        pathParameters: [
          "category": try ParameterValues.encode(category),
          "type": try ParameterValues.encode(type)
        ],
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

  public enum FetchTestCategoryUriParam : String, CaseIterable, Codable, Sendable {

    case politics = "politics"
    case science = "science"

  }

  public enum FetchTestTypeUriParam : String, CaseIterable, Codable, Sendable {

    case all = "all"
    case limited = "limited"

  }

}
