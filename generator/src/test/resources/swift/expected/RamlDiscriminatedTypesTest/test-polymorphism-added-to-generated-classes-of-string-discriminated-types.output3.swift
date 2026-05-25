import Sunday

public struct Child2 : Parent {

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

  public init(value: String? = nil, value2: Int) {
    self.value = value
    self.value2 = value2
  }

  public init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decodeIfPresent(String.self, forKey: .value)
    self.value2 = try container.decode(Int.self, forKey: .value2)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.type, forKey: .type)
    try container.encodeIfPresent(self.value, forKey: .value)
    try container.encode(self.value2, forKey: .value2)
  }

  public func withValue(value: String?) -> Child2 {
    return Child2(value: value, value2: value2)
  }

  public func withValue2(value2: Int) -> Child2 {
    return Child2(value: value, value2: value2)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case type = "type"
    case value = "value"
    case value2 = "value2"

  }

}
