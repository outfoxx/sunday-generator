import Sunday

public class Node : Codable, CustomDebugStringConvertible {

  public var type: NodeType {
    fatalError("abstract type method")
  }
  public var debugDescription: String {
    return DescriptionBuilder(Node.self)
        .build()
  }

  public init() {
  }

  public required init(from decoder: Decoder) throws {
    let _ = try decoder.container(keyedBy: Node.CodingKeys.self)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: Node.CodingKeys.self)
    try container.encode(self.type, forKey: .type)
  }

  public enum AnyRef : Codable, CustomDebugStringConvertible {

    case list(NodeList)
    case value(NodeValue)
    case map(NodeMap)

    public var value: Node {
      switch self {
      case .list(let value): return value
      case .value(let value): return value
      case .map(let value): return value
      }
    }
    public var debugDescription: String {
      switch self {
      case .list(let value): return value.debugDescription
      case .value(let value): return value.debugDescription
      case .map(let value): return value.debugDescription
      }
    }

    public init(value: Node) {
      switch value {
      case let value as NodeList: self = .list(value)
      case let value as NodeValue: self = .value(value)
      case let value as NodeMap: self = .map(value)
      default: fatalError("Invalid value type")
      }
    }

    public init(from decoder: Decoder) throws {
      let container = try decoder.container(keyedBy: Node.CodingKeys.self)
      let type = try container.decode(NodeType.self, forKey: Node.CodingKeys.type)
      switch type {
      case .list: self = .list(try NodeList(from: decoder))
      case .value: self = .value(try NodeValue(from: decoder))
      case .map: self = .map(try NodeMap(from: decoder))
      }
    }

    public func encode(to encoder: Encoder) throws {
      var container = encoder.singleValueContainer()
      switch self {
      case .list(let value): try container.encode(value)
      case .value(let value): try container.encode(value)
      case .map(let value): try container.encode(value)
      }
    }

  }

  fileprivate enum CodingKeys : String, CodingKey {

    case type = "type"

  }

}
