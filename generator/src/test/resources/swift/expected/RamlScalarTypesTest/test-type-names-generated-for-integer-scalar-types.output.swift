import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var int8: Int8
  public var int16: Int16
  public var int32: Int32
  public var int64: Int64
  public var int: Int
  public var long: Int64
  public var none: Int
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(int8, named: "int8")
        .add(int16, named: "int16")
        .add(int32, named: "int32")
        .add(int64, named: "int64")
        .add(int, named: "int")
        .add(long, named: "long")
        .add(none, named: "none")
        .build()
  }

  public init(
    int8: Int8,
    int16: Int16,
    int32: Int32,
    int64: Int64,
    int: Int,
    long: Int64,
    none: Int
  ) {
    self.int8 = int8
    self.int16 = int16
    self.int32 = int32
    self.int64 = int64
    self.int = int
    self.long = long
    self.none = none
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.int8 = try container.decode(Int8.self, forKey: .int8)
    self.int16 = try container.decode(Int16.self, forKey: .int16)
    self.int32 = try container.decode(Int32.self, forKey: .int32)
    self.int64 = try container.decode(Int64.self, forKey: .int64)
    self.int = try container.decode(Int.self, forKey: .int)
    self.long = try container.decode(Int64.self, forKey: .long)
    self.none = try container.decode(Int.self, forKey: .none)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.int8, forKey: .int8)
    try container.encode(self.int16, forKey: .int16)
    try container.encode(self.int32, forKey: .int32)
    try container.encode(self.int64, forKey: .int64)
    try container.encode(self.int, forKey: .int)
    try container.encode(self.long, forKey: .long)
    try container.encode(self.none, forKey: .none)
  }

  public func withInt8(int8: Int8) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withInt16(int16: Int16) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withInt32(int32: Int32) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withInt64(int64: Int64) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withInt(int: Int) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withLong(long: Int64) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  public func withNone(none: Int) -> Test {
    return Test(int8: int8, int16: int16, int32: int32, int64: int64, int: int, long: long,
        none: none)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case int8 = "int8"
    case int16 = "int16"
    case int32 = "int32"
    case int64 = "int64"
    case int = "int"
    case long = "long"
    case none = "none"

  }

}
