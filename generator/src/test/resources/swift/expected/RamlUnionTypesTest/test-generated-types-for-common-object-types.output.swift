import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var value: Base
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(value, named: "value")
        .build()
  }

  public init(value: Base) {
    self.value = value
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: Test.CodingKeys.self)
    self.value = try container.decode(Base.self, forKey: .value)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: Test.CodingKeys.self)
    try container.encode(self.value, forKey: .value)
  }

  public func withValue(value: Base) -> Test {
    return Test(value: value)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case value = "value"

  }

}
