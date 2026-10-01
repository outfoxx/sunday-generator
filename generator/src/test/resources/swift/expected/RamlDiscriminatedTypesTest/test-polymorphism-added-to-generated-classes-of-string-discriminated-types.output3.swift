import PotentCodables
import Sunday

public struct Child2 : Parent, ModelValidatable {

  public var type: String {
    return "child2"
  }
  public let value: String?
  public let value2: Int
  public var debugDescription: String {
    return DescriptionBuilder(Child2.self)
        .add(type, named: "type")
        .add(value, named: "value")
        .add(value2, named: "value2")
        .build()
  }
  /**
   * Schema-permitted dynamic fields, serialized at their original JSON level. */
  public let additionalProperties: [String : AnyValue]

  public init(
    value: String? = nil,
    value2: Int,
    additionalProperties: [String : AnyValue] = [:]
  ) {
    self.additionalProperties = additionalProperties.filter { !["type", "value", "value2"].contains($0.key) }
    self.value = value
    self.value2 = value2
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decodeIfPresent(String.self, forKey: .value)
    self.value2 = try container.decode(Int.self, forKey: .value2)
    let extensionContainer = try decoder.container(keyedBy: UnknownPropertyCodingKey.self)
    let declaredFields: Set<String> = ["type", "value", "value2"]
    self.additionalProperties = try [String : AnyValue](uniqueKeysWithValues: extensionContainer.allKeys.filter { !declaredFields.contains($0.stringValue) }.map { key in
      (key.stringValue, try extensionContainer.decode(AnyValue.self, forKey: key))
    })
    var validationContext = try ModelValidationContext.decoding(decoder, numericFields: [], dynamicFields: [], retainValues: true)
    if !Child2Validation.isValid(self, .response, context: &validationContext) {
      throw validationContext.decodingError
    }
  }

  public func encode(to encoder: Encoder) throws {
    try Child2Validation.validate(self, .response)
    var extensionContainer = encoder.container(keyedBy: UnknownPropertyCodingKey.self)
    for (key, value) in additionalProperties {
      try extensionContainer.encode(AdditionalPropertyValue(value: value), forKey: UnknownPropertyCodingKey(stringValue: key))
    }
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.type, forKey: .type)
    try container.encodeIfPresent(self.value, forKey: .value)
    try container.encode(self.value2, forKey: .value2)
  }

  public func withValue(value: String?) -> Child2 {
    return Child2(value: value, value2: value2, additionalProperties: additionalProperties)
  }

  public func withValue2(value2: Int) -> Child2 {
    return Child2(value: value, value2: value2, additionalProperties: additionalProperties)
  }

  /**
   * Checks the schema once using the caller's wire paths and traversal state.
   */
  public func isValid(_ mode: ModelMode, context: inout ModelValidationContext) -> Bool {
    return Child2Validation.isValid(self, mode, context: &context)
  }

  /**
   * Supplies current wire fields without serialization or model construction.
   */
  func _sundayValidationValue() -> ModelValidationValue {
    return ModelValidationValue.object(schemas: [Child2Validation.self, ParentValidation.self], validate: { mode, context in self.isValid(mode, context: &context) }) {
      let fields: [String: ModelValidationValue] = ["type": ModelValidationValue.string(self.type),
          "value": self.value.map { value in ModelValidationValue.string(value) } ?? .omitted,
          "value2": ModelValidationValue.number(String(describing: self.value2))]
      return self.additionalProperties.mapValues { ModelValidationValue($0) }.merging(fields) { _, declared in declared }
    }
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case type = "type"
    case value = "value"
    case value2 = "value2"

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
