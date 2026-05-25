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
    obj: [String : Any],
    str: String,
    def: String,
    int: Int
  ) throws -> Sunday.Operation<Empty, [String : AnyValue], TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests/{obj}/{str}/{int}/{def}",
        pathParameters: [
          "obj": try ParameterValues.encode(obj),
          "str": try ParameterValues.encode(str),
          "def": try ParameterValues.encode(def),
          "int": try ParameterValues.encode(int)
        ],
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

}
