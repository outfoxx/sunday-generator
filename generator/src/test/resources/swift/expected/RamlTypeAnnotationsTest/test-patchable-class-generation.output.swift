import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var string: UpdateOp<String>?
  public var int: UpdateOp<Int>?
  public var bool: UpdateOp<Bool>?
  public var nullable: PatchOp<String>?
  public var optional: UpdateOp<String>?
  public var nullableOptional: PatchOp<String>?
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(string, named: "string")
        .add(int, named: "int")
        .add(bool, named: "bool")
        .add(nullable, named: "nullable")
        .add(optional, named: "optional")
        .add(nullableOptional, named: "nullableOptional")
        .build()
  }

  public init(
    string: UpdateOp<String>? = .none,
    int: UpdateOp<Int>? = .none,
    bool: UpdateOp<Bool>? = .none,
    nullable: PatchOp<String>? = .none,
    optional: UpdateOp<String>? = .none,
    nullableOptional: PatchOp<String>? = .none
  ) {
    self.string = string
    self.int = int
    self.bool = bool
    self.nullable = nullable
    self.optional = optional
    self.nullableOptional = nullableOptional
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.string = try container.decodeIfExists(String.self, forKey: .string)
    self.int = try container.decodeIfExists(Int.self, forKey: .int)
    self.bool = try container.decodeIfExists(Bool.self, forKey: .bool)
    self.nullable = try container.decodeIfExists(String.self, forKey: .nullable)
    self.optional = try container.decodeIfExists(String.self, forKey: .optional)
    self.nullableOptional = try container.decodeIfExists(String.self, forKey: .nullableOptional)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfExists(self.string, forKey: .string)
    try container.encodeIfExists(self.int, forKey: .int)
    try container.encodeIfExists(self.bool, forKey: .bool)
    try container.encodeIfExists(self.nullable, forKey: .nullable)
    try container.encodeIfExists(self.optional, forKey: .optional)
    try container.encodeIfExists(self.nullableOptional, forKey: .nullableOptional)
  }

  public func withString(string: UpdateOp<String>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  public func withInt(int: UpdateOp<Int>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  public func withBool(bool: UpdateOp<Bool>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  public func withNullable(nullable: PatchOp<String>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  public func withOptional(optional: UpdateOp<String>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  public func withNullableOptional(nullableOptional: PatchOp<String>?) -> Test {
    return Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case string = "string"
    case int = "int"
    case bool = "bool"
    case nullable = "nullable"
    case optional = "optional"
    case nullableOptional = "nullableOptional"

  }

}

extension AnyPatchOp where Value == Test {

  public static func merge(
    string: UpdateOp<String>? = .none,
    int: UpdateOp<Int>? = .none,
    bool: UpdateOp<Bool>? = .none,
    nullable: PatchOp<String>? = .none,
    optional: UpdateOp<String>? = .none,
    nullableOptional: PatchOp<String>? = .none
  ) -> Self {
    Self.merge(Test(string: string, int: int, bool: bool, nullable: nullable, optional: optional,
        nullableOptional: nullableOptional))
  }

}
