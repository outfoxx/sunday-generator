import XCTest
import Sunday
import Synchronization
@testable import SundayGenTest

final class OptionalTests: XCTestCase {
  func testOmission() async throws {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [BodyProtocol.self]
    let session = URLSession(configuration: config)
    let transport = URLSessionTransport(baseURL: "https://example.com", session: session, eventSession: session)
    defer { transport.close() }
    let client = OptionalAPI(transport: transport)
    try await client.optionalPost().execute()
    XCTAssertTrue(BodyProtocol.body.withLock { $0.isEmpty })
    try await client.defaultPost().execute()
    XCTAssertTrue(BodyProtocol.body.withLock { $0.isEmpty })
    try await client.optionalPost(body: Input(target: "ok")).execute()
    XCTAssertTrue(BodyProtocol.body.withLock { String(decoding: $0, as: UTF8.self).contains("ok") })
  }
}

private final class BodyProtocol: URLProtocol, @unchecked Sendable {
  static let body = Mutex<Data>(Data())
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    var bytes = request.httpBody ?? Data()
    if let stream = request.httpBodyStream {
      stream.open()
      defer { stream.close() }
      var buffer = [UInt8](repeating: 0, count: 1024)
      while stream.hasBytesAvailable {
        let count = stream.read(&buffer, maxLength: buffer.count)
        if count <= 0 { break }
        bytes.append(contentsOf: buffer.prefix(count))
      }
    }
    Self.body.withLock { $0 = bytes }
    let response = HTTPURLResponse(url: request.url!, statusCode: 204, httpVersion: "HTTP/1.1", headerFields: [:])!
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocolDidFinishLoading(self)
  }
  override func stopLoading() {}
}
