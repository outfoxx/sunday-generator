import Sunday

public class Child1 : Parent {

  public override var type: String {
    return "Child1"
  }
  public var value: String?
  public var value1: Int
  public override var debugDescription: String {
    return DescriptionBuilder(Child1.self)
        .add(type, named: "type")
        .add(value, named: "value")
        .add(value1, named: "value1")
        .build()
  }

  public init(value: String? = nil, value1: Int) {
    self.value = value
    self.value1 = value1
    super.init()
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decodeIfPresent(String.self, forKey: .value)
    self.value1 = try container.decode(Int.self, forKey: .value1)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfPresent(self.value, forKey: .value)
    try container.encode(self.value1, forKey: .value1)
  }

  public func withValue(value: String?) -> Child1 {
    return Child1(value: value, value1: value1)
  }

  public func withValue1(value1: Int) -> Child1 {
    return Child1(value: value, value1: value1)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value = "value"
    case value1 = "value1"

  }

}
