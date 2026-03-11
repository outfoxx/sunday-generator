import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var float: Float
  public var double: Double
  public var none: Double
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(float, named: "float")
        .add(double, named: "double")
        .add(none, named: "none")
        .build()
  }

  public init(
    float: Float,
    double: Double,
    none: Double
  ) {
    self.float = float
    self.double = double
    self.none = none
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.float = try container.decode(Float.self, forKey: .float)
    self.double = try container.decode(Double.self, forKey: .double)
    self.none = try container.decode(Double.self, forKey: .none)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.float, forKey: .float)
    try container.encode(self.double, forKey: .double)
    try container.encode(self.none, forKey: .none)
  }

  public func withFloat(float: Float) -> Test {
    return Test(float: float, double: double, none: none)
  }

  public func withDouble(double: Double) -> Test {
    return Test(float: float, double: double, none: none)
  }

  public func withNone(none: Double) -> Test {
    return Test(float: float, double: double, none: none)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case float = "float"
    case double = "double"
    case none = "none"

  }

}
