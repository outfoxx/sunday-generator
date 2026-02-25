import Sunday

public class Parent : Codable, CustomDebugStringConvertible {

  public var type: String {
    fatalError("abstract type method")
  }
  public var debugDescription: String {
    return DescriptionBuilder(Parent.self)
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
