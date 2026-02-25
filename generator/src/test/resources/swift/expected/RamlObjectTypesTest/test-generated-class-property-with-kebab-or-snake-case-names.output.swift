import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var someValue: String
  public var anotherValue: String
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(someValue, named: "someValue")
        .add(anotherValue, named: "anotherValue")
        .build()
  }

  public init(someValue: String, anotherValue: String) {
    self.someValue = someValue
    self.anotherValue = anotherValue
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.someValue = try container.decode(String.self, forKey: .someValue)
    self.anotherValue = try container.decode(String.self, forKey: .anotherValue)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.someValue, forKey: .someValue)
    try container.encode(self.anotherValue, forKey: .anotherValue)
  }

  public func withSomeValue(someValue: String) -> Test {
    return Test(someValue: someValue, anotherValue: anotherValue)
  }

  public func withAnotherValue(anotherValue: String) -> Test {
    return Test(someValue: someValue, anotherValue: anotherValue)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case someValue = "some-value"
    case anotherValue = "another_value"

  }

}
