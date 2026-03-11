import Foundation
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var className: String {
    return String(describing: Data.self) + "-value-" + "-literal"
  }
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
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
