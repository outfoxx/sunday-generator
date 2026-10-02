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
        ],
        parameterValidation: {
          let mode = ModelMode.request
          var context = ModelValidationContext(collectsDiagnostics: true)
          _ = !context.validatesNestedModels || select.isValid(mode, context: &context)
          _ = !context.validatesNestedModels || page.isValid(mode, context: &context)
          _ = !context.validatesNestedModels || xType.isValid(mode, context: &context)
          if !context.diagnostics.isEmpty { throw context.validationError }
        }
      )
    )
  }

  public enum FetchTestSelectUriParam : String, CaseIterable, Codable, CustomStringConvertible,
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
      return FetchTestSelectUriParamValidation.isValid(self, mode, context: &context)
    }

  }

  public enum FetchTestPageQueryParam : String, CaseIterable, Codable, CustomStringConvertible,
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
      return FetchTestPageQueryParamValidation.isValid(self, mode, context: &context)
    }

  }

  public enum FetchTestXTypeHeaderParam : String, CaseIterable, Codable, CustomStringConvertible,
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
      return FetchTestXTypeHeaderParamValidation.isValid(self, mode, context: &context)
    }

  }

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestSelectUriParamValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestSelectUriParam,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestSelectUriParam) -> ModelValidationValue {
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

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestPageQueryParamValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestPageQueryParam,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestPageQueryParam) -> ModelValidationValue {
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

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestXTypeHeaderParamValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestXTypeHeaderParam,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestXTypeHeaderParam) -> ModelValidationValue {
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
