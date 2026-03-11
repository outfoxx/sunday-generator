import PotentCodables
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var map: [String : Any]
  public var array: [Any]
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(map, named: "map")
        .add(array, named: "array")
        .build()
  }

  public init(map: [String : Any], array: [Any]) {
    self.map = map
    self.array = array
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.map = try container.decode([String : AnyValue].self, forKey: .map).mapValues { $0.unwrapped }
    self.array = try container.decode([AnyValue].self, forKey: .array).map { $0.unwrapped }
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.map.mapValues { try AnyValue.wrapped($0) }, forKey: .map)
    try container.encode(self.array.map { try AnyValue.wrapped($0) }, forKey: .array)
  }

  public func withMap(map: [String : Any]) -> Test {
    return Test(map: map, array: array)
  }

  public func withArray(array: [Any]) -> Test {
    return Test(map: map, array: array)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case map = "map"
    case array = "array"

  }

}
