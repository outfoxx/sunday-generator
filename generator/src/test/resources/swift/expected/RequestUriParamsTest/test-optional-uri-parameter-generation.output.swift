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
    def2: Int? = 10,
    obj: Test? = nil,
    str: String? = nil,
    def1: String? = "test",
    int: Int? = nil,
    def: String
  ) throws -> Sunday.Operation<Empty, Test, TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}",
        pathParameters: [
          "def2": try ParameterValues.encode(def2),
          "obj": try ParameterValues.encode(obj),
          "str": try ParameterValues.encode(str),
          "def1": try ParameterValues.encode(def1),
          "int": try ParameterValues.encode(int),
          "def": try ParameterValues.encode(def)
        ].filter { $0.value != nil },
        queryParameters: nil,
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      )
    )
  }

}
