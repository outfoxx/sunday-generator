import Foundation
import Sunday

public class InvalidIdProblem : Problem {

  public static let type: URL = URL(string: "http://api.example.com/api/invalid_id")!
  public let offendingId: String
  public override var description: String {
    return DescriptionBuilder(Self.self)
        .add(type, named: "type")
        .add(title, named: "title")
        .add(status, named: "status")
        .add(detail, named: "detail")
        .add(instance, named: "instance")
        .add(offendingId, named: "offendingId")
        .build()
  }

  public init(offendingId: String, instance: URL? = nil) {
    self.offendingId = offendingId
    super.init(type: Self.type, title: "Invalid Id", status: 400,
        detail: "The id contains one or more invalid characters.", instance: instance,
        parameters: nil)
  }

  public required init(from decoder: Decoder) throws {
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case offendingId = "offending_id"

  }

}
