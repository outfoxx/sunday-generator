import Sunday

public class Branch : Root {

  public var value: String
  public override var debugDescription: String {
    return DescriptionBuilder(Branch.self)
        .add(value, named: "value")
        .build()
  }

  public init(value: String) {
    self.value = value
    super.init()
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value = try container.decode(String.self, forKey: .value)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.value, forKey: .value)
  }

  public func withValue(value: String) -> Branch {
    return Branch(value: value)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value = "value"

  }

}
