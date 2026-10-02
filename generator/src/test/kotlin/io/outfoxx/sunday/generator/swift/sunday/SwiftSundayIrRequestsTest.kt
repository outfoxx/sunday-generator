/*
 * Copyright 2020 Outfox, Inc.
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

package io.outfoxx.sunday.generator.swift.sunday

import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Requests Test")
@Tag("requests")
class SwiftSundayIrRequestsTest : SwiftSundayIrTestSupport() {

  @Test
  fun `uses content type header parameter as request media selection in Swift Sunday`(compiler: SwiftCompiler) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val api = avatarUploadApi()

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val serviceSource =
      buildString {
        FileSpec
          .get("", findType("UsersAPI", builtTypes))
          .writeTo(this)
      }

    assertTrue(compileTypes(compiler, builtTypes))
    assertTrue(serviceSource.contains("try .init(valid: contentType.rawValue)"), serviceSource)
    assertTrue(serviceSource.contains("acceptTypes: [try .init(valid: \"image/png\")"), serviceSource)
    assertFalse(serviceSource.contains("\"Content-Type\": contentType"), serviceSource)
  }

  @Test
  fun `generates request methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMethodsTest/test-request-method-generation.output.swift",
    )
  }

  @Test
  fun `generates path parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestUriParamsTest/test-basic-uri-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional query parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestQueryParamsTest/test-optional-query-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates constant headers from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestHeaderParamsTest/test-constant-header-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates mixed inline parameters from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestMixedParamsTest/test-generation-of-multiple-parameters-with-inline-type-definitions.output.swift",
    )
  }

  @Test
  fun `generates request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-basic-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates optional request body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-optional-body-parameter-generation.output.swift",
    )
  }

  @Test
  fun `generates explicit request body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "RequestBodyParamTest/test-generation-of-body-parameter-with-explicit-content-type.output.swift",
    )
  }

  @Test
  fun `generates base URL companion from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/base-uri.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BaseUriTest/test-baseurl-generation-in-api.output.swift",
    )
  }

  @Test
  fun `generates request builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-request-builder-method-generation.output.swift",
    )
  }
}
