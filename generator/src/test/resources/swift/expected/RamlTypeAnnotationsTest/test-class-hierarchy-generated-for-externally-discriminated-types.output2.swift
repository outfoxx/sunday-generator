import Sunday

public class Child1 : Parent {

  public override var type: String {
    return "Child1"
  }
  public var value: String?
  public override var debugDescription: String {
    return DescriptionBuilder(Child1.self)
        .add(type, named: "type")
        .add(value, named: "value")
        .build()
  }

  public init(value: String? = nil) {
    self.value = value
    super.init()
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decodeIfPresent(String.self, forKey: .value)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfPresent(self.value, forKey: .value)
  }

  public func withValue(value: String?) -> Child1 {
    return Child1(value: value)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value = "value"

  }

}
