import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var implicit: [String]
  public var unspecified: [String]
  public var nonUnique: [String]
  public var unique: Set<String>
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(implicit, named: "implicit")
        .add(unspecified, named: "unspecified")
        .add(nonUnique, named: "nonUnique")
        .add(unique, named: "unique")
        .build()
  }

  public init(
    implicit: [String],
    unspecified: [String],
    nonUnique: [String],
    unique: Set<String>
  ) {
    self.implicit = implicit
    self.unspecified = unspecified
    self.nonUnique = nonUnique
    self.unique = unique
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.implicit = try container.decode([String].self, forKey: .implicit)
    self.unspecified = try container.decode([String].self, forKey: .unspecified)
    self.nonUnique = try container.decode([String].self, forKey: .nonUnique)
    self.unique = try container.decode(Set<String>.self, forKey: .unique)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.implicit, forKey: .implicit)
    try container.encode(self.unspecified, forKey: .unspecified)
    try container.encode(self.nonUnique, forKey: .nonUnique)
    try container.encode(self.unique, forKey: .unique)
  }

  public func withImplicit(implicit: [String]) -> Test {
    return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
  }

  public func withUnspecified(unspecified: [String]) -> Test {
    return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
  }

  public func withNonUnique(nonUnique: [String]) -> Test {
    return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
  }

  public func withUnique(unique: Set<String>) -> Test {
    return Test(implicit: implicit, unspecified: unspecified, nonUnique: nonUnique, unique: unique)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case implicit = "implicit"
    case unspecified = "unspecified"
    case nonUnique = "nonUnique"
    case unique = "unique"

  }

}
