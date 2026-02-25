import Sunday

public class Test : Codable, CustomDebugStringConvertible, Identifiable {

  public var id: String
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(id, named: "id")
        .build()
  }

  public init(id: String) {
    self.id = id
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.id = try container.decode(String.self, forKey: .id)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.id, forKey: .id)
  }

  public func withId(id: String) -> Test {
    return Test(id: id)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case id = "id"

  }

}
