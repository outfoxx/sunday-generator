import Sunday

public class Child : Test {

  public override var type: TestType {
    return TestType.child
  }
  public var child: UpdateOp<String>?
  public override var debugDescription: String {
    return DescriptionBuilder(Child.self)
        .add(type, named: "type")
        .add(child, named: "child")
        .build()
  }

  public init(child: UpdateOp<String>? = .none) {
    self.child = child
    super.init()
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.child = try container.decodeIfExists(String.self, forKey: .child)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfExists(self.child, forKey: .child)
  }

  public func withChild(child: UpdateOp<String>?) -> Child {
    return Child(child: child)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case child = "child"

  }

}

extension AnyPatchOp where Value == Child {

  public static func merge(child: UpdateOp<String>? = .none) -> Self {
    Self.merge(Child(child: child))
  }

}
