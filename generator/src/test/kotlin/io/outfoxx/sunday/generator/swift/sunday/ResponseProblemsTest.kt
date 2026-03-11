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
import io.outfoxx.swiftpoet.DeclaredTypeName.Companion.typeName
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift/Sunday] [RAML] Response Problems Test")
class ResponseProblemsTest {

  @Test
  fun `test API problem registration`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
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
      "ResponseProblemsTest/test-api-problem-registration.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test API problem registration when no problems`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
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
      "ResponseProblemsTest/test-api-problem-registration-when-no-problems.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

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
  fun `test problem type generation using base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-base-uri.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-absolute-problem-base-uri.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-using-relative-problem-base-uri.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(typeName(".CreateFailedProblem")))
    assertTrue(builtTypes.containsKey(typeName(".TestNotFoundProblem")))

    val typeSpec = findType("InvalidIdProblem", builtTypes)

    assertSwiftSnapshot(
      "ResponseProblemsTest/test-problem-type-generation-locates-problems-in-libraries.output.swift",
      buildString {
        FileSpec
          .get("", typeSpec)
          .writeTo(this)
      },
    )
  }
}
