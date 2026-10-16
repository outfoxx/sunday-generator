import XCTest
import Sunday
import Synchronization
@testable import SundayGenTest

final class BinaryTests: XCTestCase {
  func testDownloads() async throws {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [DownloadProtocol.self]
    let session = URLSession(configuration: config)
    let transport = URLSessionTransport(baseURL: "https://example.com", session: session, eventSession: session)
    defer { transport.close() }
    let client = DownloadsAPI(transport: transport)
    let bytes = Data([0, 255, 34, 128])
    for media in ["application/json", "text/plain; charset=iso-8859-1", "image/png", "application/x-download"] {
      DownloadProtocol.payload.withLock { $0 = (media, bytes) }
      for operation in [try client.directDownload(), try client.aliasDownload(), try client.unionDownload(), try client.nestedDownload()] {
        let actual: Data = try await operation.execute()
        XCTAssertEqual(actual, bytes)
        let response = try await operation.response()
        XCTAssertEqual(response.result, bytes)
      }
    }
    DownloadProtocol.payload.withLock { $0 = ("application/json", Data("\"asset\"".utf8)) }
    let mixed = try await client.mixedDownload().execute()
    XCTAssertEqual(try JSONEncoder().encode(mixed), Data("\"asset\"".utf8))
    DownloadProtocol.payload.withLock { $0 = ("application/json", Data()) }
    do {
      _ = try await client.directDownload().execute()
      XCTFail("The Swift runtime must reject an empty typed response")
    } catch SundayError.responseDecodingFailed(reason: .noData) { }
  }
}

private final class DownloadProtocol: URLProtocol, @unchecked Sendable {
  static let payload = Mutex<(String, Data)>(("application/json", Data()))
  override class func canInit(with request: URLRequest) -> Bool { true }
  override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
  override func startLoading() {
    let (media, bytes) = Self.payload.withLock { $0 }
    let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": media])!
    client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
    client?.urlProtocol(self, didLoad: bytes)
    client?.urlProtocolDidFinishLoading(self)
  }
  override func stopLoading() {}
}
