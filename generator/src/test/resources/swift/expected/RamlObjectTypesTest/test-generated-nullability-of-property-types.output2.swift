import PotentCodables
import Sunday

public class Test2 : Codable, CustomDebugStringConvertible {

  public var optionalObject: [String : Any]?
  public var nillableObject: [String : Any]?
  public var optionalHierarchy: Parent?
  public var nillableHierarchy: Parent?
  public var debugDescription: String {
    return DescriptionBuilder(Test2.self)
        .add(optionalObject, named: "optionalObject")
        .add(nillableObject, named: "nillableObject")
        .add(optionalHierarchy, named: "optionalHierarchy")
        .add(nillableHierarchy, named: "nillableHierarchy")
        .build()
  }

  public init(
    optionalObject: [String : Any]? = nil,
    nillableObject: [String : Any]?,
    optionalHierarchy: Parent? = nil,
    nillableHierarchy: Parent?
  ) {
    self.optionalObject = optionalObject
    self.nillableObject = nillableObject
    self.optionalHierarchy = optionalHierarchy
    self.nillableHierarchy = nillableHierarchy
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.optionalObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .optionalObject)?.mapValues { $0.unwrapped }
    self.nillableObject = try container.decodeIfPresent([String : AnyValue].self, forKey: .nillableObject)?.mapValues { $0.unwrapped }
    self.optionalHierarchy = try container.decodeIfPresent(Parent.AnyRef.self, forKey: .optionalHierarchy)?.value
    self.nillableHierarchy = try container.decodeIfPresent(Parent.AnyRef.self, forKey: .nillableHierarchy)?.value
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encodeIfPresent(self.optionalObject?.mapValues { try AnyValue.wrapped($0) }, forKey: .optionalObject)
    try container.encodeIfPresent(self.nillableObject?.mapValues { try AnyValue.wrapped($0) }, forKey: .nillableObject)
    try container.encodeIfPresent(self.optionalHierarchy, forKey: .optionalHierarchy)
    try container.encodeIfPresent(self.nillableHierarchy, forKey: .nillableHierarchy)
  }

  public func withOptionalObject(optionalObject: [String : Any]?) -> Test2 {
    return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
        optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
  }

  public func withNillableObject(nillableObject: [String : Any]?) -> Test2 {
    return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
        optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
  }

  public func withOptionalHierarchy(optionalHierarchy: Parent?) -> Test2 {
    return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
        optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
  }

  public func withNillableHierarchy(nillableHierarchy: Parent?) -> Test2 {
    return Test2(optionalObject: optionalObject, nillableObject: nillableObject,
        optionalHierarchy: optionalHierarchy, nillableHierarchy: nillableHierarchy)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case optionalObject = "optionalObject"
    case nillableObject = "nillableObject"
    case optionalHierarchy = "optionalHierarchy"
    case nillableHierarchy = "nillableHierarchy"

  }

}
