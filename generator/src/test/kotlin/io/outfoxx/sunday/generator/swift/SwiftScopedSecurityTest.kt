/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.GeneratedTypeCategory
import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
@Tag("events")
@Tag("security")
class SwiftScopedSecurityTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `compiled clients authenticate operations and event connections`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operations =
      source.services.flatMap { service ->
        service.operations.map { it.copy(auth = source.effectiveAuth(service, it)) }
      }
    val api = source.copy(services = listOf(GeneratedService("SecurityService", operations = operations)))
    val registry = SwiftTypeRegistry(emptySet())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("https://api.example/problems/", listOf("application/json"), "API", profile = "external"),
    ).generateServiceTypes()
    registry.generateFiles(setOf(GeneratedTypeCategory.Model, GeneratedTypeCategory.Service), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    val calls =
      operations.joinToString("\n") { operation ->
        if (operation.streaming != null) {
          "for await _ in client.${operation.id}() {}"
        } else {
          "try await client.${operation.id}().execute()"
        }
      }
    Files.writeString(
      compiler.testsDir.resolve("SecurityTests.swift"),
      """
      import Foundation
      import Synchronization
      import XCTest
      import Sunday
      @testable import SundayGenTest

      final class SecurityTests: XCTestCase {
        func testSelectedProfileAndRuntimeProvider() async throws {
          let provider = Provider()
          let manager = try TokenManager(providers: ["application": provider])
          let config = URLSessionConfiguration.ephemeral
          config.protocolClasses = [CaptureProtocol.self]
          let session = URLSession(configuration: config)
          let transport = URLSessionTransport(baseURL: "https://api.example", session: session, eventSession: session,
                                              tokenManager: manager)
          defer { transport.close() }
          let client = SecurityAPI(transport: transport)
          $calls
          let requests = CaptureProtocol.requests.withLock { ${'$'}0 }
          XCTAssertEqual(requests.count, ${operations.size})
          for request in requests {
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization")?.lowercased(), "bearer external-token")
          }
          let acquired = provider.requests.withLock { ${'$'}0 }
          XCTAssertEqual(acquired.count, 1)
          let binding = try XCTUnwrap(acquired.first?.binding)
          XCTAssertEqual(binding.provider, "application")
          XCTAssertEqual(binding.profile, "external")
          XCTAssertEqual(binding.flow, .authorizationCode)
          XCTAssertEqual(binding.scopes, ["items:read"])
          XCTAssertEqual(binding.endpoints.tokenURL, "https://identity.example/token")
          XCTAssertEqual(binding.endpoints.authorizationURL, "https://identity.example/authorize")
          await manager.close()
        }
      }

      private final class Provider: TokenProvider {
        let identity = "application"
        let requests = Mutex<[TokenRequest]>([])
        func configure(_ binding: SecurityBinding) -> TokenConfiguration {
          TokenConfiguration(clientIdentity: "public-client", grantIdentity: "session")
        }
        func acquire(_ request: TokenRequest) async throws -> TokenSet {
          requests.withLock { ${'$'}0.append(request) }
          return TokenSet(accessToken: "external-token")
        }
      }

      private final class CaptureProtocol: URLProtocol, @unchecked Sendable {
        static let requests = Mutex<[URLRequest]>([])
        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
        override func startLoading() {
          Self.requests.withLock { ${'$'}0.append(request) }
          let response = HTTPURLResponse(url: request.url!, statusCode: 204, httpVersion: "HTTP/1.1", headerFields: [:])!
          client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
          client?.urlProtocolDidFinishLoading(self)
        }
        override func stopLoading() {}
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }
}
