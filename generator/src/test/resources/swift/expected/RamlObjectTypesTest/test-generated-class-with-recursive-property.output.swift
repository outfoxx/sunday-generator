import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var parent: Test?
  public var other: Test?
  public var children: [Test]
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(parent, named: "parent")
        .add(other, named: "other")
        .add(children, named: "children")
        .build()
  }

  public init(
    parent: Test?,
    other: Test? = nil,
    children: [Test]
  ) {
    self.parent = parent
    self.other = other
    self.children = children
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.parent = try container.decodeIfPresent(Test.self, forKey: .parent)
    self.other = try container.decodeIfPresent(Test.self, forKey: .other)
    self.children = try container.decode([Test].self, forKey: .children)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfPresent(self.parent, forKey: .parent)
    try container.encodeIfPresent(self.other, forKey: .other)
    try container.encode(self.children, forKey: .children)
  }

  public func withParent(parent: Test?) -> Test {
    return Test(parent: parent, other: other, children: children)
  }

  public func withOther(other: Test?) -> Test {
    return Test(parent: parent, other: other, children: children)
  }

  public func withChildren(children: [Test]) -> Test {
    return Test(parent: parent, other: other, children: children)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case parent = "parent"
    case other = "other"
    case children = "children"

  }

}
