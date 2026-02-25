import Sunday

public class Leaf : Branch {

  public var value2: String
  public override var debugDescription: String {
    return DescriptionBuilder(Leaf.self)
        .add(value, named: "value")
        .add(value2, named: "value2")
        .build()
  }

  public init(value: String, value2: String) {
    self.value2 = value2
    super.init(value: value)
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value2 = try container.decode(String.self, forKey: .value2)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.value2, forKey: .value2)
  }

  public override func withValue(value: String) -> Leaf {
    return Leaf(value: value, value2: value2)
  }

  public func withValue2(value2: String) -> Leaf {
    return Leaf(value: value, value2: value2)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value2 = "value2"

  }

}
