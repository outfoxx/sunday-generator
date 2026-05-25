public protocol Parent : Codable, CustomDebugStringConvertible, Sendable {

  var type: String { get }

}
