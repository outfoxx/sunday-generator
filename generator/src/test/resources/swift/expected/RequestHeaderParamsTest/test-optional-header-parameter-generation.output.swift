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
    obj: Test? = nil,
    str: String? = nil,
    int: Int? = nil,
    def1: String? = "test",
    def2: Int? = 10
  ) throws -> Sunday.Operation<Empty, Test, TransportType> {
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
        headers: [
          "obj": try ParameterValues.encode(obj),
          "str": try ParameterValues.encode(str),
          "int": try ParameterValues.encode(int),
          "def1": try ParameterValues.encode(def1),
          "def2": try ParameterValues.encode(def2)
        ].filter { $0.value != nil }
      )
    )
  }

}
