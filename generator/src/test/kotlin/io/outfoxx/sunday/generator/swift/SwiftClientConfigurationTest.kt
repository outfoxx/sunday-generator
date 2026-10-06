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
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

@SwiftTest
class SwiftClientConfigurationTest {
  @ParameterizedTest
  @ValueSource(
    strings = ["raml", "openapi", "asyncapi", "composed", "security", "multi", "alternatives", "server-security"],
  )
  fun `configuration callback preserves transport type and is invoked once`(
    frontend: String,
    compiler: SwiftCompiler,
    @TempDir directory: Path,
  ) {
    val api = clientConfigurationApi(frontend, directory)
    val authenticated = frontend in setOf("security", "alternatives", "server-security")
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
          }${if (frontend == "alternatives") {
        ", credentials: APICredentials(identity: BearerCredentials(token: \"secret\"), accessKey: ApiKeyCredentials(key: \"key\")), securitySelection: [\"listItems\": .accessKeyAndIdentity]"
      } else if (frontend in setOf("security", "server-security")) {
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
}
