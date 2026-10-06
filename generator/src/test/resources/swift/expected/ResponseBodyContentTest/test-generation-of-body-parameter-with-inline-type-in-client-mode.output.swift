import PotentCodables
import Sunday

public final class API<TransportType : Transport> : Sendable {

  public static var problemTypes: [ProblemRegistration] {
    return []
  }
  public let transport: TransportType
  public let defaultContentTypes: [MediaType]
  public let defaultAcceptTypes: [MediaType]
  private let clientSettings: ClientSettings?

  public init(
    transport: TransportType,
    defaultContentTypes: [MediaType] = [],
    defaultAcceptTypes: [MediaType] = [.json],
    problemTypes: [ProblemRegistration] = API.problemTypes,
    clientSettings: ClientSettings? = nil
  ) {
    self.transport = transport
    self.defaultContentTypes = defaultContentTypes
    self.defaultAcceptTypes = defaultAcceptTypes
    problemTypes.forEach { $0.register(on: transport) }
    self.clientSettings = clientSettings
  }

  public func fetchTest() throws -> Sunday.Operation<Empty, FetchTestResponseBody, TransportType> {
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
        headers: nil,
        security: clientSettings?.bindings["fetchTest"] ?? []
      ),
      responseValidation: { value in
        let mode = ModelMode.response
        var context = ModelValidationContext(collectsDiagnostics: true)
        if !(!context.validatesNestedModels || value.isValid(mode, context: &context)) {
          throw context.validationError
        }
      }
    )
  }

  public struct FetchTestResponseBody : Codable, CustomDebugStringConvertible, Sendable,
      ModelValidatable {

    public let value: String
    public var debugDescription: String {
      return DescriptionBuilder(FetchTestResponseBody.self)
          .add(value, named: "value")
          .build()
    }
    /**
     * Schema-permitted dynamic fields, serialized at their original JSON level. */
    public let additionalProperties: [String : AnyValue]

    public init(value: String, additionalProperties: [String : AnyValue] = [:]) {
      self.additionalProperties = additionalProperties.filter { !["value"].contains($0.key) }
      self.value = value
    }

    public init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      self.value = try container.decode(String.self, forKey: .value)
      let extensionContainer = try decoder.container(keyedBy: UnknownPropertyCodingKey.self)
      let declaredFields: Set<String> = ["value"]
      self.additionalProperties = try [String : AnyValue](uniqueKeysWithValues: extensionContainer.allKeys.filter { !declaredFields.contains($0.stringValue) }.map { key in
        (key.stringValue, try extensionContainer.decode(AnyValue.self, forKey: key))
      })
      var validationContext = try ModelValidationContext.decoding(decoder, numericFields: [], dynamicFields: [], retainValues: true)
      if !FetchTestResponseBodyValidation.isValid(self, .response, context: &validationContext) {
        throw validationContext.decodingError
      }
    }

    public func encode(to encoder: Encoder) throws {
      try FetchTestResponseBodyValidation.validate(self, .response)
      var extensionContainer = encoder.container(keyedBy: UnknownPropertyCodingKey.self)
      for (key, value) in additionalProperties {
        try extensionContainer.encode(AdditionalPropertyValue(value: value), forKey: UnknownPropertyCodingKey(stringValue: key))
      }
      var container = encoder.container(keyedBy: CodingKeys.self)
      try container.encode(self.value, forKey: .value)
    }

    public func withValue(value: String) -> FetchTestResponseBody {
      return FetchTestResponseBody(value: value, additionalProperties: additionalProperties)
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public func isValid(_ mode: ModelMode, context: inout ModelValidationContext) -> Bool {
      return FetchTestResponseBodyValidation.isValid(self, mode, context: &context)
    }

    /**
     * Supplies current wire fields without serialization or model construction.
     */
    func _sundayValidationValue() -> ModelValidationValue {
      return ModelValidationValue.object(schemas: [FetchTestResponseBodyValidation.self], validate: { mode, context in self.isValid(mode, context: &context) }) {
        let fields: [String: ModelValidationValue] = ["value": ModelValidationValue.string(self.value)]
        return self.additionalProperties.mapValues { ModelValidationValue($0) }.merging(fields) { _, declared in declared }
      }
    }

    fileprivate enum CodingKeys : String, CodingKey {

      case value = "value"

    }

    fileprivate struct UnknownPropertyCodingKey : CodingKey {

      let stringValue: String
      let intValue: Int? = nil

      init(stringValue: String) {
        self.stringValue = stringValue
      }

      init(intValue: Int) {
        self.stringValue = String(intValue)
      }

    }

    private struct AdditionalPropertyValue : Encodable {

      let value: AnyValue

      func encode(to encoder: Encoder) throws {
        switch value {
        case .dictionary(let values):
          var container = encoder.container(keyedBy: UnknownPropertyCodingKey.self)
          for (key, item) in values {
            guard case .string(let name) = key else {
              throw EncodingError.invalidValue(key, .init(codingPath: encoder.codingPath, debugDescription: "JSON object keys must be strings"))
            }
            try container.encode(AdditionalPropertyValue(value: item), forKey: UnknownPropertyCodingKey(stringValue: name))
          }
        case .array(let values):
          var container = encoder.unkeyedContainer()
          for item in values { try container.encode(AdditionalPropertyValue(value: item)) }
        default:
          try value.encode(to: encoder)
        }
      }

    }

  }

  /**
   * Validates current values of the associated schema in request or response mode.
   */
  public enum FetchTestResponseBodyValidation : ModelValidator {

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      _ value: FetchTestResponseBody,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return isValid(normalized: view(value), mode, context: &context)
    }

    /**
     * Projects storage without constructing or encoding application models.
     */
    static func view(_ value: FetchTestResponseBody) -> ModelValidationValue {
      return value._sundayValidationValue()
    }

    /**
     * Checks the schema once using the caller's wire paths and traversal state.
     */
    public static func isValid(
      normalized value: ModelValidationValue,
      _ mode: ModelMode,
      context: inout ModelValidationContext
    ) -> Bool {
      return ModelObjectValidation(fields: [
        .init("value", required: true) { value, mode, context in
          if value.kind == .null {
            return context.reject(.nullValue)
          }
          return value.kind == .string || context.reject(.invalidValue)
        },
      ], patterns: [
      ], closed: false).isValid(value, mode, context: &context)
    }

  }

}
