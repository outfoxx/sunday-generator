import Sunday

public class Test : Codable, CustomDebugStringConvertible {

  public var arrayOfStrings: [String]
  public var arrayOfNullableStrings: [String?]
  public var nullableArrayOfStrings: [String]?
  public var nullableArrayOfNullableStrings: [String?]?
  public var declaredArrayOfStrings: [String]
  public var declaredArrayOfNullableStrings: [String?]
  public var debugDescription: String {
    return DescriptionBuilder(Test.self)
        .add(arrayOfStrings, named: "arrayOfStrings")
        .add(arrayOfNullableStrings, named: "arrayOfNullableStrings")
        .add(nullableArrayOfStrings, named: "nullableArrayOfStrings")
        .add(nullableArrayOfNullableStrings, named: "nullableArrayOfNullableStrings")
        .add(declaredArrayOfStrings, named: "declaredArrayOfStrings")
        .add(declaredArrayOfNullableStrings, named: "declaredArrayOfNullableStrings")
        .build()
  }

  public init(
    arrayOfStrings: [String],
    arrayOfNullableStrings: [String?],
    nullableArrayOfStrings: [String]?,
    nullableArrayOfNullableStrings: [String?]?,
    declaredArrayOfStrings: [String],
    declaredArrayOfNullableStrings: [String?]
  ) {
    self.arrayOfStrings = arrayOfStrings
    self.arrayOfNullableStrings = arrayOfNullableStrings
    self.nullableArrayOfStrings = nullableArrayOfStrings
    self.nullableArrayOfNullableStrings = nullableArrayOfNullableStrings
    self.declaredArrayOfStrings = declaredArrayOfStrings
    self.declaredArrayOfNullableStrings = declaredArrayOfNullableStrings
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.arrayOfStrings = try container.decode([String].self, forKey: .arrayOfStrings)
    self.arrayOfNullableStrings = try container.decode([String?].self, forKey: .arrayOfNullableStrings)
    self.nullableArrayOfStrings = try container.decodeIfPresent([String].self, forKey: .nullableArrayOfStrings)
    self.nullableArrayOfNullableStrings = try container.decodeIfPresent([String?].self, forKey: .nullableArrayOfNullableStrings)
    self.declaredArrayOfStrings = try container.decode([String].self, forKey: .declaredArrayOfStrings)
    self.declaredArrayOfNullableStrings = try container.decode([String?].self, forKey: .declaredArrayOfNullableStrings)
  }

  public func encode(to encoder: Encoder) throws {
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.arrayOfStrings, forKey: .arrayOfStrings)
    try container.encode(self.arrayOfNullableStrings, forKey: .arrayOfNullableStrings)
    try container.encodeIfPresent(self.nullableArrayOfStrings, forKey: .nullableArrayOfStrings)
    try container.encodeIfPresent(self.nullableArrayOfNullableStrings, forKey: .nullableArrayOfNullableStrings)
    try container.encode(self.declaredArrayOfStrings, forKey: .declaredArrayOfStrings)
    try container.encode(self.declaredArrayOfNullableStrings, forKey: .declaredArrayOfNullableStrings)
  }

  public func withArrayOfStrings(arrayOfStrings: [String]) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  public func withArrayOfNullableStrings(arrayOfNullableStrings: [String?]) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  public func withNullableArrayOfStrings(nullableArrayOfStrings: [String]?) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  public func withNullableArrayOfNullableStrings(nullableArrayOfNullableStrings: [String?]?) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  public func withDeclaredArrayOfStrings(declaredArrayOfStrings: [String]) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  public func withDeclaredArrayOfNullableStrings(declaredArrayOfNullableStrings: [String?]) -> Test {
    return Test(arrayOfStrings: arrayOfStrings, arrayOfNullableStrings: arrayOfNullableStrings,
        nullableArrayOfStrings: nullableArrayOfStrings,
        nullableArrayOfNullableStrings: nullableArrayOfNullableStrings,
        declaredArrayOfStrings: declaredArrayOfStrings,
        declaredArrayOfNullableStrings: declaredArrayOfNullableStrings)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case arrayOfStrings = "arrayOfStrings"
    case arrayOfNullableStrings = "arrayOfNullableStrings"
    case nullableArrayOfStrings = "nullableArrayOfStrings"
    case nullableArrayOfNullableStrings = "nullableArrayOfNullableStrings"
    case declaredArrayOfStrings = "declaredArrayOfStrings"
    case declaredArrayOfNullableStrings = "declaredArrayOfNullableStrings"

  }

}
