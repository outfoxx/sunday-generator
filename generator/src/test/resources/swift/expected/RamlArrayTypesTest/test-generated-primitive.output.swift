import Foundation
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var binary: Data
  public var nullableBinary: Data?
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(binary, named: "binary")
        .add(nullableBinary, named: "nullableBinary")
        .build()
  }

  public init(binary: Data, nullableBinary: Data?) {
    self.binary = binary
    self.nullableBinary = nullableBinary
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.binary = try container.decode(Data.self, forKey: .binary)
    self.nullableBinary = try container.decodeIfPresent(Data.self, forKey: .nullableBinary)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.binary, forKey: .binary)
    try container.encodeIfPresent(self.nullableBinary, forKey: .nullableBinary)
  }

  public func withBinary(binary: Data) -> Test {
    return Test(binary: binary, nullableBinary: nullableBinary)
  }

  public func withNullableBinary(nullableBinary: Data?) -> Test {
    return Test(binary: binary, nullableBinary: nullableBinary)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case binary = "binary"
    case nullableBinary = "nullableBinary"

  }

}
