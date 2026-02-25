import Sunday

public class Root : Codable, CustomDebugStringConvertible {

  public var debugDescription: String {
    return DescriptionBuilder(Root.self)
        .build()
  }

  public init() {
  }

  public required init(from decoder: Decoder) throws {
    let _ = try decoder.container(keyedBy: CodingKeys.self)
  }

  public func encode(to encoder: Encoder) throws {
    let _ = encoder.container(keyedBy: CodingKeys.self)
  }

  fileprivate enum CodingKeys : CodingKey {
  }

}
