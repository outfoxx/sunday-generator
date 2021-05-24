
public struct AnyValue : Codable {
  public static func wrapped(_ value: Any?) throws -> AnyValue { fatalError() }
  public var unwrapped: Any? { fatalError() }
}
