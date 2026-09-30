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
        headers: nil
      )
    )
  }

  public struct FetchTestResponseBody : Codable, CustomDebugStringConvertible, Sendable {

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
    }

    public func encode(to encoder: Encoder) throws {
      let extensionDecoder = AnyValueDecoder()
      extensionDecoder.userInfo = encoder.userInfo
      _ = try extensionDecoder.decode(AdditionalPropertiesValidator.self, from: AnyValue.dictionary(.init(uniqueKeysWithValues: additionalProperties.map { (.string($0.key), $0.value) })))
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

    private struct AdditionalPropertiesValidator : Decodable {

      init(from decoder: Decoder) throws {
      }

    }

  }

}
