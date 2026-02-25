import PotentCodables
import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var any: Any
  public var duplicate: String
  public var nullable: String?
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(any, named: "any")
        .add(duplicate, named: "duplicate")
        .add(nullable, named: "nullable")
        .build()
  }

  public init(
    any: Any,
    duplicate: String,
    nullable: String?
  ) {
    self.any = any
    self.duplicate = duplicate
    self.nullable = nullable
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.any = try container.decode(AnyValue.self, forKey: .any).unwrapped
    self.duplicate = try container.decode(String.self, forKey: .duplicate)
    self.nullable = try container.decodeIfPresent(String.self, forKey: .nullable)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(AnyValue.wrapped(any), forKey: .any)
    try container.encode(self.duplicate, forKey: .duplicate)
    try container.encodeIfPresent(self.nullable, forKey: .nullable)
  }

  public func withAny(any: Any) -> Test {
    return Test(any: any, duplicate: duplicate, nullable: nullable)
  }

  public func withDuplicate(duplicate: String) -> Test {
    return Test(any: any, duplicate: duplicate, nullable: nullable)
  }

  public func withNullable(nullable: String?) -> Test {
    return Test(any: any, duplicate: duplicate, nullable: nullable)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case any = "any"
    case duplicate = "duplicate"
    case nullable = "nullable"

  }

}
