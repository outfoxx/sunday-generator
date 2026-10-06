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
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileAndTestGeneratedFiles
import io.outfoxx.sunday.generator.tools.aggregateClientConfigurationApi
import io.outfoxx.sunday.generator.tools.aggregateFrontendConfigurationApi
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftClientConfigurationTest {
  @Test
  fun `credential field collisions fail before emitting source`(
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi("credential-collision", directory)
    val error =
      assertThrows(IllegalArgumentException::class.java) {
        SwiftSundayIrGenerator(
          api,
          SwiftTypeRegistry(setOf()),
          SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
        ).generateServiceTypes()
      }
    assertTrue(error.message.orEmpty().contains("credential field name collision"))
  }

  @ParameterizedTest
  @ValueSource(
    strings = [
      "raml", "openapi", "asyncapi", "composed", "security", "multi",
      "alternatives", "server-security", "server-profile",
    ],
  )
  fun `configuration callback preserves transport type and is invoked once`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi(frontend, directory)
    val authenticated = frontend in setOf("security", "alternatives", "server-security", "server-profile")
    val configType =
      if (frontend in
        setOf("multi", "server-security")
      ) {
        "ExampleAPIProductionConfig"
      } else {
        "ExampleAPIConfig"
      }
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      api,
      registry,
      SwiftSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("ClientConfigTests.swift"),
      """
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class ClientConfigTests: XCTestCase {
        func testConstruction() throws {
          let config = $configType(tenant: "secondary")
          var calls = 0
          let client: API<URLSessionTransport> = try createAPI(config: config, transportFactory: { settings in
            calls += 1
            XCTAssertEqual(settings.tokenManager != nil, ${authenticated
      })
            XCTAssertEqual(settings.baseURL.absoluteString, "https://secondary.example/v1")
            return URLSessionTransport(settings: settings)
          }${if (frontend in setOf("alternatives", "server-profile")) {
        ", credentials: APICredentials(identity: BearerCredentials(token: \"secret\"), accessKey: ApiKeyCredentials(key: \"key\")), securitySelection: [\"listItems\": .accessKeyAndIdentity]"
      } else if (frontend in setOf("security", "server-security", "server-profile")) {
        ", credentials: APICredentials(identity: BearerCredentials(token: \"secret\"))"
      } else {
        ""
      }})
          defer { client.transport.close() }
          XCTAssertEqual(calls, 1)
          XCTAssertThrowsError(try $configType(tenant: "invalid").baseURL())
        }
      }
      """.trimIndent(),
    )
    assertTrue(compileAndTestGeneratedFiles(compiler))
  }

  @Test
  fun `aggregate factory shares settings and lazy token cache`(
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      aggregateClientConfigurationApi(directory),
      registry,
      SwiftSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        profile = "external",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    registry.generateFiles(GeneratedTypeCategory.entries.toSet(), compiler.srcDir)
    Files.createDirectories(compiler.testsDir)
    Files.writeString(
      compiler.testsDir.resolve("AggregateTests.swift"),
      """
      import Foundation
      import Synchronization
      import XCTest
      import Sunday
      @testable import SundayGenTest
      final class AggregateTests: XCTestCase {
        func testSharedSettings() async throws {
          for override in [false, true] {
            CaptureProtocol.requests.withLock { ${'$'}0.removeAll() }
            let provider = Provider()
            var calls = 0
            let expected = override ? "external" : "external-development"
            let factory: ClientTransportFactory<URLSessionTransport> = { settings in
              calls += 1
              XCTAssertEqual(settings.baseURL.absoluteString, "https://api.dev.example")
              XCTAssertEqual(Set(settings.bindings.keys), ["listUsers", "listProjects", "register"])
              XCTAssertEqual(settings.bindings["register"]?.count, 0)
              for id in ["listUsers", "listProjects"] {
                XCTAssertEqual(settings.bindings[id]?.first?.profile, expected)
                XCTAssertEqual(settings.bindings[id]?.first?.scopes, ["items:read"])
              }
              let config = URLSessionConfiguration.ephemeral
              config.protocolClasses = [CaptureProtocol.self]
              let session = URLSession(configuration: config)
              return URLSessionTransport(baseURL: URI.Template(stringLiteral: settings.baseURL.absoluteString), session: session, eventSession: session,
                                         tokenManager: settings.tokenManager)
            }
            let credentials = ExampleAPICredentials(identity: ProviderCredentials(provider: provider))
            let client = try override
              ? createExampleAPI(config: ExampleAPIDevelopmentConfig(), transportFactory: factory, credentials: credentials, securityProfile: "external")
              : createExampleAPI(config: ExampleAPIDevelopmentConfig(), transportFactory: factory, credentials: credentials,
                  securitySelection: ["listProjects": .identity])
            defer { client.transport.close() }
            XCTAssertEqual(calls, 1)
            XCTAssertTrue(provider.requests.withLock { ${'$'}0.isEmpty })
            XCTAssertTrue(client.users.transport === client.transport)
            XCTAssertTrue(client.projects.transport === client.transport)
            try await client.users.register().execute()
            XCTAssertTrue(provider.requests.withLock { ${'$'}0.isEmpty })
            XCTAssertNil(CaptureProtocol.requests.withLock { ${'$'}0.first?.value(forHTTPHeaderField: "Authorization") })
            try await client.users.listUsers().execute()
            try await client.projects.listProjects().execute()
            let acquired = provider.requests.withLock { ${'$'}0 }
            XCTAssertEqual(acquired.count, 1)
            XCTAssertEqual(acquired.first?.binding.profile, expected)
          }
          for credentials in [
            ExampleAPICredentials(),
            ExampleAPICredentials(identity: ProviderCredentials(provider: Provider()), backupToken: BearerCredentials(token: "key"))
          ] {
          var invalidCalls = 0
          XCTAssertThrowsError(try createExampleAPI(config: ExampleAPIDevelopmentConfig(), transportFactory: { settings in
            invalidCalls += 1
            return URLSessionTransport(settings: settings)
          }, credentials: credentials))
          XCTAssertEqual(invalidCalls, 0)
          }
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

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `aggregate factories compile for every frontend`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {

    val registry = SwiftTypeRegistry(setOf())
    SwiftSundayIrGenerator(
      aggregateFrontendConfigurationApi(frontend, directory),
      registry,
      SwiftSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    assertTrue(
      io.outfoxx.sunday.generator.swift.tools
        .compileTypes(compiler, registry.buildTypes()),
    )
  }
}
