import Foundation
import FoundationNetworking
import PotentCodables

public struct Empty : Codable {
  public static let instance = Empty()
  public static let none: Empty? = nil
}

public struct DescriptionBuilder {

  public init(_ type: Any.Type) { fatalError() }

  public func add(_ value: Any?, named name: String) -> DescriptionBuilder {
    return self
  }

  public func build() -> String {
    return ""
  }

  public func debugBuild() -> String {
    return build()
  }

}

open class Problem: Error, Codable {

  public let type: URL

  public let title: String

  public let status: Int

  public let detail: String?

  public let instance: URL?

  public let parameters: [String: AnyValue]?

  public init(
    type: URL,
    title: String,
    status: Int,
    detail: String? = nil,
    instance: URL? = nil,
    parameters: [String: AnyValue]? = nil
  ) {
    self.type = type
    self.title = title
    self.status = status
    self.detail = detail
    self.instance = instance
    self.parameters = parameters
  }

}


public protocol RequestFactory {

  var baseURL: URI.Template { get }

  func registerProblem(type: URL, problemType: Problem.Type)
  func registerProblem(type: String, problemType: Problem.Type)

  func request<B: Encodable>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?
  ) async throws -> URLRequest

  func response<B: Encodable>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?
  ) async throws -> (Data?, HTTPURLResponse)

  func response(request: URLRequest) async throws -> (Data?, HTTPURLResponse)

  func result<B: Encodable, D: Decodable>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?
  ) async throws -> D

  func result<B: Encodable>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?
  ) async throws

  func eventSource<B: Encodable>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?
  ) -> EventSource

  func eventStream<B: Encodable, D>(
    method: HTTP.Method, pathTemplate: String,
    pathParameters: Parameters?, queryParameters: Parameters?, body: B?,
    contentTypes: [MediaType]?, acceptTypes: [MediaType]?,
    headers: Parameters?, eventTypes: [String: AnyTextMediaTypeDecodable]
  ) -> AsyncStream<D>

  func close(cancelOutstandingRequests: Bool)

}

public struct MediaType {
  public static let plain: MediaType = .init(valid: "")
  public static let html: MediaType = .init(valid: "")
  public static let json: MediaType = .init(valid: "")
  public static let yaml: MediaType = .init(valid: "")
  public static let cbor: MediaType = .init(valid: "")
  public static let octetStream: MediaType = .init(valid: "")
  public static let eventStream: MediaType = .init(valid: "")
  public static let wwwFormUrlEncoded: MediaType = .init(valid: "")
  public static let problem: MediaType = .init(valid: "")

  public init(valid: String) { fatalError() }
  public init?(_ string: String) { fatalError() }
}

public struct URI {
  public struct Template {
    public init(template: String, parameters: Parameters = [:]) {}
  }
}

public struct HTTP {
  public enum Method {
    case options
    case get
    case head
    case post
    case put
    case patch
    case delete
    case trace
    case connect
  }
  public typealias Headers = [String: [String]]
}

public typealias Parameters = [String: Any?]

public struct AnyPublisher<Result, Failure> {
}

public struct AnyTextMediaTypeDecodable {
  public static func erase<D : Decodable>(_ type: D.Type) -> AnyTextMediaTypeDecodable {
    return AnyTextMediaTypeDecodable()
  }
}

public struct EventSource {}
