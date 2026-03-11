import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var parent: Parent
  public var parentType: String
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(parent, named: "parent")
        .add(parentType, named: "parentType")
        .build()
  }

  public init(parent: Parent, parentType: String) {
    self.parent = parent
    self.parentType = parentType
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.parentType = try container.decode(String.self, forKey: .parentType)
    switch self.parentType {
    case "Child1": self.parent = try container.decode(Child1.self, forKey: .parent)
    case "child2": self.parent = try container.decode(Child2.self, forKey: .parent)
    default:
        throw DecodingError.dataCorruptedError(
          forKey: CodingKeys.parentType,
          in: container,
          debugDescription: "unsupported value for \"parentType\""
        )
    }
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.parentType, forKey: .parentType)
    switch self.parentType {
    case "Child1": try container.encode(self.parent as! Child1, forKey: .parent)
    case "child2": try container.encode(self.parent as! Child2, forKey: .parent)
    default:
        throw EncodingError.invalidValue(
          self.parentType,
          EncodingError.Context(
            codingPath: encoder.codingPath + [CodingKeys.parentType],
            debugDescription: "unsupported value for \"parentType\""
          )
        )
    }
  }

  public func withParent(parent: Parent) -> Test {
    return Test(parent: parent, parentType: parentType)
  }

  public func withParentType(parentType: String) -> Test {
    return Test(parent: parent, parentType: parentType)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case parent = "parent"
    case parentType = "parentType"

  }

}
