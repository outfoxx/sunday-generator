import Sunday

public class Test3 : Empty {

  public var value3: String
  public override var debugDescription: String {
    return DescriptionBuilder(Test3.self)
        .add(value, named: "value")
        .add(value2, named: "value2")
        .add(value3, named: "value3")
        .build()
  }

  public init(
    value: String,
    value2: String,
    value3: String
  ) {
    self.value3 = value3
    super.init(value: value, value2: value2)
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.value3 = try container.decode(String.self, forKey: .value3)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.value3, forKey: .value3)
  }

  public override func withValue(value: String) -> Test3 {
    return Test3(value: value, value2: value2, value3: value3)
  }

  public override func withValue2(value2: String) -> Test3 {
    return Test3(value: value, value2: value2, value3: value3)
  }

  public func withValue3(value3: String) -> Test3 {
    return Test3(value: value, value2: value2, value3: value3)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value3 = "value3"

  }

}
