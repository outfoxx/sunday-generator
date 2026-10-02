import Sunday

public protocol Parent : Codable, CustomDebugStringConvertible, Sendable, ModelValidatable {

  var type: String { get }

}
