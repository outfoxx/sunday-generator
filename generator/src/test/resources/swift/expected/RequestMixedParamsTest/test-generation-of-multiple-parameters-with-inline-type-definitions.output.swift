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

  public func fetchTest(
    select: FetchTestSelectUriParam,
    page: FetchTestPageQueryParam,
    xType: FetchTestXTypeHeaderParam
  ) throws -> Sunday.Operation<Empty, [String : AnyValue], TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests/{select}",
        pathParameters: [
          "select": try ParameterValues.encode(select)
        ],
        queryParameters: [
          "page": try ParameterValues.encode(page)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: [
          "x-type": try ParameterValues.encode(xType)
        ]
      )
    )
  }

  public enum FetchTestSelectUriParam : String, CaseIterable, Codable, Sendable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestPageQueryParam : String, CaseIterable, Codable, Sendable {

    case all = "all"
    case limited = "limited"

  }

  public enum FetchTestXTypeHeaderParam : String, CaseIterable, Codable, Sendable {

    case all = "all"
    case limited = "limited"

  }

}
