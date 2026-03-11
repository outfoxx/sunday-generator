import Sunday

public class Parent : Codable, CustomDebugStringConvertible {

  public var type: `Type` {
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
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.type, forKey: .type)
  }

  public enum AnyRef : Codable, CustomDebugStringConvertible {

    case child1(Child1)
    case child2(Child2)

    public var value: Parent {
      switch self {
      case .child1(let value): return value
      case .child2(let value): return value
      }
    }
    public var debugDescription: String {
      switch self {
      case .child1(let value): return value.debugDescription
      case .child2(let value): return value.debugDescription
      }
    }

    public init(value: Parent) {
      switch value {
      case let value as Child1: self = .child1(value)
      case let value as Child2: self = .child2(value)
      default: fatalError("Invalid value type")
      }
    }

    public init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: CodingKeys.self)
      let type = try container.decode(`Type`.self, forKey: CodingKeys.type)
      switch type {
      case .child1: self = .child1(try Child1(from: decoder))
      case .child2: self = .child2(try Child2(from: decoder))
      }
    }

    public func encode(to encoder: Encoder) throws {
      var container = encoder.singleValueContainer()
      switch self {
      case .child1(let value): try container.encode(value)
      case .child2(let value): try container.encode(value)
      }
    }

  }

  fileprivate enum CodingKeys : String, CodingKey {

    case type = "type"

  }

}
