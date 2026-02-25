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

import io.outfoxx.sunday.generator.swift.SwiftSundayGenerator
import io.outfoxx.sunday.generator.swift.SwiftTest
import io.outfoxx.sunday.generator.swift.SwiftTypeRegistry
import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generate
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift/Sunday] [RAML] Request Header Params Test")
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestHeaderParamsTest/test-basic-header-parameter-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test constant header parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestHeaderParamsTest/test-constant-header-parameter-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestHeaderParamsTest/test-optional-header-parameter-generation.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple header parameters with inline type definitions`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        SwiftSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          swiftSundayTestOptions,
        )
      }

    val typeSpec = findType("API", builtTypes)

    assertSwiftSnapshot(
      "RequestHeaderParamsTest/test-generation-of-multiple-header-parameters-with-inline-type-definitions.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
