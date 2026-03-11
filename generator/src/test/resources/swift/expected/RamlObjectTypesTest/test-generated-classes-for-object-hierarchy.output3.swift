import Sunday

public class Empty : Test2 {

  public override var debugDescription: String {
    return DescriptionBuilder(Empty.self)
        .add(value, named: "value")
        .add(value2, named: "value2")
        .build()
  }

  public override init(value: String, value2: String) {
    super.init(value: value, value2: value2)
  }

  public required init(from decoder: Decoder) throws {
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
  }

  public override func withValue(value: String) -> Empty {
    return Empty(value: value, value2: value2)
  }

  public override func withValue2(value2: String) -> Empty {
    return Empty(value: value, value2: value2)
  }

}
