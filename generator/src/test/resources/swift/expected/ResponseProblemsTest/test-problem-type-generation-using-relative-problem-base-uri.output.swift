import Foundation
import PotentCodables
import Sunday

/**
 * Invalid Id
 *
 * The id contains one or more invalid characters.
 */
public struct InvalidIdProblem : Problem {

  public static let type: URL = URL(string: "http://example.com/api/errors/invalid_id")!
  public let type: URL
  public let title: String
  public let status: Int
  public let detail: String?
  public let instance: URL?
  public let parameters: [String : AnyValue]?
  public let offendingId: String
  public var description: String {
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
    self.type = Self.type
    self.title = "Invalid Id"
    self.status = 400
    self.detail = "The id contains one or more invalid characters."
    self.instance = instance
    self.parameters = nil
    self.offendingId = offendingId
  }

  public init(from decoder: Decoder) throws {
    let problem = try GenericProblem(from: decoder)
    let container = try decoder.container(keyedBy: CodingKeys.self)
    self.offendingId = try container.decode(String.self, forKey: CodingKeys.offendingId)
    self.type = problem.type
    self.title = problem.title
    self.status = problem.status
    self.detail = problem.detail
    self.instance = problem.instance
    self.parameters = nil
  }

  public func encode(to encoder: Encoder) throws {
    let problem = GenericProblem(type: type, title: title, status: status, detail: detail,
        instance: instance, parameters: parameters)
    try problem.encode(to: encoder)
    var container = encoder.container(keyedBy: CodingKeys.self)
    try container.encode(self.offendingId, forKey: CodingKeys.offendingId)
  }

  fileprivate enum CodingKeys : String, CodingKey {

    case offendingId = "offending_id"

  }

}
