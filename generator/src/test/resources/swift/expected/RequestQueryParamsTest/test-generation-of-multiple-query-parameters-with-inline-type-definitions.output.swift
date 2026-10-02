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

  public func fetchTest(category: FetchTestCategoryQueryParam, type: FetchTestTypeQueryParam) throws -> Sunday.Operation<Empty, [String : AnyValue], TransportType> {
    return Sunday.Operation(
      transport: self.transport,
      spec: Sunday.OperationSpec(
        method: .get,
        pathTemplate: "/tests",
        pathParameters: nil,
        queryParameters: [
          "category": try ParameterValues.encode(category),
          "type": try ParameterValues.encode(type)
        ],
        body: Empty.none,
        contentTypes: nil,
        acceptTypes: self.defaultAcceptTypes,
        headers: nil,
        parameterValidation: { [parameter = category, parameter_ = type] in
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          _ = !context.validatesNestedModels || parameter.isValid(mode, context: &context)
          _ = !context.validatesNestedModels || parameter_.isValid(mode, context: &context)
          if !context.diagnostics.isEmpty { throw context.validationError }
        }
      )
    )
  }

  public enum FetchTestCategoryQueryParam : String, CaseIterable, Codable, CustomStringConvertible,
      Sendable, ModelValidatable {

    case politics = "politics"
    case science = "science"

    public var description: String {
      return rawValue
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public func isValid(_ mode: ModelMode, context: inout ModelValidationContext) -> Bool {
      return FetchTestCategoryQueryParamValidation.isValid(self, mode, context: &context)
    }

  }

  public enum FetchTestTypeQueryParam : String, CaseIterable, Codable, CustomStringConvertible,
      Sendable, ModelValidatable {

    case all = "all"
    case limited = "limited"

    public var description: String {
      return rawValue
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public func isValid(_ mode: ModelMode, context: inout ModelValidationContext) -> Bool {
      return FetchTestTypeQueryParamValidation.isValid(self, mode, context: &context)
    }

  }

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestCategoryQueryParamValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestCategoryQueryParam,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestCategoryQueryParam) -> ModelValidationValue {
      return ModelValidationValue.string(value.rawValue)
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      normalized value: ModelValidationValue,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      guard let rawValue = value.string else { return context.reject(.invalidValue) }
      return ["politics", "science"].contains(rawValue) || context.reject(.allowedValue)
    }

  }

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestTypeQueryParamValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestTypeQueryParam,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestTypeQueryParam) -> ModelValidationValue {
      return ModelValidationValue.string(value.rawValue)
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      normalized value: ModelValidationValue,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      guard let rawValue = value.string else { return context.reject(.invalidValue) }
      return ["all", "limited"].contains(rawValue) || context.reject(.allowedValue)
    }

  }

}
