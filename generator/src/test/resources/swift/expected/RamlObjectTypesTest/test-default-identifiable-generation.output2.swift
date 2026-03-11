import Sunday

public class Test2 : Test {

  public override var debugDescription: String {
    return DescriptionBuilder(Test2.self)
        .add(id, named: "id")
        .build()
  }

  public override init(id: String) {
    super.init(id: id)
  }

  public required init(from decoder: Decoder) throws {
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
  }

  public override func withId(id: String) -> Test2 {
    return Test2(id: id)
  }

}
