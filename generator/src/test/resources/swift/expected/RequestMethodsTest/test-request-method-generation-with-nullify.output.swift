import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return [
      ProblemRegistration(type: TestNotFoundProblem.type, problemType: TestNotFoundProblem.self),
      ProblemRegistration(type: AnotherNotFoundProblem.type, problemType: AnotherNotFoundProblem.self)
    ]
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

  public func fetchTest1(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test1",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404, 405],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      )
    )
  }

  public func fetchTest2(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test2",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      )
    )
  }

  public func fetchTest3(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test3",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      ),
      nilify: Sunday.NilifySpec(
        statuses: [],
        problemTypes: [TestNotFoundProblem.self, AnotherNotFoundProblem.self]
      )
    )
  }

  public func fetchTest4(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test4",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404, 405],
        problemTypes: []
      )
    )
  }

  public func fetchTest5(limit: Int) throws -> Sunday.NilableOperation<Empty, Test, TransportType> {
    return Sunday.NilableOperation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/test5",
        pathParameters: nil,
        queryParameters: [
          "limit": try ParameterValues.encode(limit)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil
      ),
      nilify: Sunday.NilifySpec(
        statuses: [404],
        problemTypes: []
      )
    )
  }

}
