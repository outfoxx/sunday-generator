import Foundation
import Sunday

public class TestResolverProblem : Problem {

  public static let type: URL = URL(string: "http://example.com/test_resolver")!
  public let optionalString: String?
  public let arrayOfStrings: [String]
  public let optionalArrayOfStrings: [String]?
  public override var description: String {
    return DescriptionBuilder(Self.self)
        .add(type, named: "type")
        .add(title, named: "title")
        .add(status, named: "status")
        .add(detail, named: "detail")
        .add(instance, named: "instance")
        .add(optionalString, named: "optionalString")
        .add(arrayOfStrings, named: "arrayOfStrings")
        .add(optionalArrayOfStrings, named: "optionalArrayOfStrings")
        .build()
  }

  public init(
    optionalString: String? = nil,
    arrayOfStrings: [String],
    optionalArrayOfStrings: [String]? = nil,
    instance: URL? = nil
  ) {
    self.optionalString = optionalString
    self.arrayOfStrings = arrayOfStrings
    self.optionalArrayOfStrings = optionalArrayOfStrings
    super.init(type: Self.type, title: "Test Resolve Type Reference", status: 500,
        detail: "Tests the resolveTypeReference function implementation.", instance: instance,
        parameters: nil)
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.optionalString = try container.decodeIfPresent(String.self, forKey: CodingKeys.optionalString)
    self.arrayOfStrings = try container.decode([String].self, forKey: CodingKeys.arrayOfStrings)
    self.optionalArrayOfStrings = try container.decodeIfPresent([String].self, forKey: CodingKeys.optionalArrayOfStrings)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.optionalString, forKey: CodingKeys.optionalString)
    try container.encode(self.arrayOfStrings, forKey: CodingKeys.arrayOfStrings)
    try container.encode(self.optionalArrayOfStrings, forKey: CodingKeys.optionalArrayOfStrings)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case optionalString = "optionalString"
    case arrayOfStrings = "arrayOfStrings"
    case optionalArrayOfStrings = "optionalArrayOfStrings"

  }

}
