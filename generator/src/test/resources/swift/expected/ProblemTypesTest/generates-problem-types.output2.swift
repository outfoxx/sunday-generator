import Foundation
import Sunday

public class AccountNotFoundProblem : Problem {

  public static let type: URL = URL(string: "http://example.com/account_not_found")!
  public override var description: String {
    return DescriptionBuilder(Self.self)
        .add(type, named: "type")
        .add(title, named: "title")
        .add(status, named: "status")
        .add(detail, named: "detail")
        .add(instance, named: "instance")
        .build()
  }

  public init(instance: URL? = nil) {
    super.init(type: Self.type, title: "Account Not Found", status: 404,
        detail: "The requested account does not exist or you do not have permission to access it.",
        instance: instance, parameters: nil)
  }

  public required init(from decoder: Decoder) throws {
    try super.init(from: decoder)
  }

  public override func encode(to encoder: Encoder) throws {
    try super.encode(to: encoder)
  }

}
