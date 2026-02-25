import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var fromNilUnion: String?
  public var notRequired: String?
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(fromNilUnion, named: "fromNilUnion")
        .add(notRequired, named: "notRequired")
        .build()
  }

  public init(fromNilUnion: String?, notRequired: String? = nil) {
    self.fromNilUnion = fromNilUnion
    self.notRequired = notRequired
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.fromNilUnion = try container.decodeIfPresent(String.self, forKey: .fromNilUnion)
    self.notRequired = try container.decodeIfPresent(String.self, forKey: .notRequired)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfPresent(self.fromNilUnion, forKey: .fromNilUnion)
    try container.encodeIfPresent(self.notRequired, forKey: .notRequired)
  }

  public func withFromNilUnion(fromNilUnion: String?) -> Test {
    return Test(fromNilUnion: fromNilUnion, notRequired: notRequired)
  }

  public func withNotRequired(notRequired: String?) -> Test {
    return Test(fromNilUnion: fromNilUnion, notRequired: notRequired)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case fromNilUnion = "fromNilUnion"
    case notRequired = "notRequired"

  }

}
