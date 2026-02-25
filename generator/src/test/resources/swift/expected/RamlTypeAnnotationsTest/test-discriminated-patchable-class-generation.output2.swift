import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var type: TestType {
    fatalError("abstract type method")
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
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.type, forKey: .type)
  }

  public enum AnyRef : Codable, CustomDebugStringConvertible {

    case child(Child)

    public var value: Test {
      switch self {
      case .child(let value): return value
      }
    }
    public var debugDescription: String {
      switch self {
      case .child(let value): return value.debugDescription
      }
    }

    public init(value: Test) {
      switch value {
      case let value as Child: self = .child(value)
      default: fatalError("Invalid value type")
      }
    }

    public init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      let type = try container.decode(TestType.self, forKey: CodingKeys.type)
      switch type {
      case .child: self = .child(try Child(from: decoder))
      }
    }

    public func encode(to encoder: Encoder) throws {
      var container = encoder.singleValueContainer()
      switch self {
      case .child(let value): try container.encode(value)
      }
    }

  }

  fileprivate enum CodingKeys : String, CodingKey {

    case type = "type"

  }

}

extension AnyPatchOp where Value == Test {

  public static func merge() -> Self {
    Self.merge(Test())
  }

}
