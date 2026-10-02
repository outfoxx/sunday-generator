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

import io.outfoxx.sunday.generator.ir.RamlToGeneratedApi
import io.outfoxx.sunday.generator.swift.SwiftSundayIrGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.compileTypes
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.generator.utils.TestAPIProcessing
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift/Sunday] [IR] Responses Test")
@Tag("responses")
class SwiftSundayIrResponsesTest : SwiftSundayIrTestSupport() {

  @Test
  fun `generates explicit response body content type from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-explicit-content-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-explicit-content-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates polymorphic response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-basic-body-parameter-generation-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates inline response body from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-body-param-inline-type.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-body-parameter-with-inline-type-in-client-mode.output.swift",
    )
  }

  @Test
  fun `generates no content response from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-no-content.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseBodyContentTest/test-generation-of-response-body-that-is-no-content-client-mode.output.swift",
    )
  }

  @Test
  fun `registers referenced problems from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "ResponseProblemsTest/test-api-problem-registration.output.swift",
    )
  }

  @Test
  fun `generates referenced problem types directly from IR`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
  ) {
    val typeRegistry = SwiftTypeRegistry(setOf())
    val result = TestAPIProcessing.process(testUri)
    val api = RamlToGeneratedApi().convert(result)

    SwiftSundayIrGenerator(api, typeRegistry, swiftSundayTestOptions)
      .generateServiceTypes()

    val builtTypes = typeRegistry.buildTypes()
    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertTrue(compileTypes(compiler, builtTypes))
    assertFalse(builtTypes.keys.any { typeName -> typeName.simpleName == "CreateFailedProblem" })
    assertTrue(builtTypes.keys.any { typeName -> typeName.simpleName == "TestNotFoundProblem" })
    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates response builder methods from IR with existing Swift Sunday output shape`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-builder.raml") testUri: URI,
  ) {
    assertIrServiceSnapshot(
      compiler,
      testUri,
      "BuilderMethodsTest/test-response-builder-method-generation.output.swift",
    )
  }
}
