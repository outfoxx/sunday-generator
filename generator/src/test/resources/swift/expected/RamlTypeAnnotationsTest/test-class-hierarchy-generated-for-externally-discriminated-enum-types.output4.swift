import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var parent: Parent
  public var parentType: `Type`
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(parent, named: "parent")
        .add(parentType, named: "parentType")
        .build()
  }

  public init(parent: Parent, parentType: `Type`) {
    self.parent = parent
    self.parentType = parentType
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.parentType = try container.decode(`Type`.self, forKey: .parentType)
    switch self.parentType {
    case .child2: self.parent = try container.decode(Child2.self, forKey: .parent)
    case .child1: self.parent = try container.decode(Child1.self, forKey: .parent)
    }
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.parentType, forKey: .parentType)
    switch self.parentType {
    case .child2: try container.encode(self.parent as! Child2, forKey: .parent)
    case .child1: try container.encode(self.parent as! Child1, forKey: .parent)
    }
  }

  public func withParent(parent: Parent) -> Test {
    return Test(parent: parent, parentType: parentType)
  }

  public func withParentType(parentType: `Type`) -> Test {
    return Test(parent: parent, parentType: parentType)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case parent = "parent"
    case parentType = "parentType"

  }

}
