import Foundation
import PotentCodables
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var bool: Bool
  public var string: String
  public var file: Data
  public var any: AnyValue
  public var `nil`: Void
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(bool, named: "bool")
        .add(string, named: "string")
        .add(file, named: "file")
        .add(any, named: "any")
        .add(`nil`, named: "nil")
        .build()
  }

  public init(
    bool: Bool,
    string: String,
    file: Data,
    any: AnyValue,
    `nil`: Void
  ) {
    self.bool = bool
    self.string = string
    self.file = file
    self.any = any
    self.`nil` = `nil`
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.bool = try container.decode(Bool.self, forKey: .bool)
    self.string = try container.decode(String.self, forKey: .string)
    self.file = try container.decode(Data.self, forKey: .file)
    self.any = try container.decode(AnyValue.self, forKey: .any)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.bool, forKey: .bool)
    try container.encode(self.string, forKey: .string)
    try container.encode(self.file, forKey: .file)
    try container.encode(self.any, forKey: .any)
  }

  public func withBool(bool: Bool) -> Test {
    return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
  }

  public func withString(string: String) -> Test {
    return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
  }

  public func withFile(file: Data) -> Test {
    return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
  }

  public func withAny(any: AnyValue) -> Test {
    return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
  }

  public func withNil(`nil`: Void) -> Test {
    return Test(bool: bool, string: string, file: file, any: any, nil: `nil`)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case bool = "bool"
    case string = "string"
    case file = "file"
    case any = "any"
    case `nil` = "nil"

  }

}
