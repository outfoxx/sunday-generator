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

package io.outfoxx.sunday.generator.swift

import io.outfoxx.sunday.generator.swift.tools.SwiftCompiler
import io.outfoxx.sunday.generator.swift.tools.findType
import io.outfoxx.sunday.generator.swift.tools.generateTypes
import io.outfoxx.sunday.generator.tools.assertSwiftSnapshot
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.swiftpoet.FileSpec
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@SwiftTest
@DisplayName("[Swift] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    compiler: SwiftCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = SwiftTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val invalidIdType = findType("InvalidIdProblem", builtTypes)
    assertSwiftSnapshot(
      "ProblemTypesTest/generates-problem-types.output.swift",
      buildString {
        FileSpec
          .get("", invalidIdType)
          .writeTo(this)
      },
    )

    val accountNotFoundType = findType("AccountNotFoundProblem", builtTypes)
    assertSwiftSnapshot(
      "ProblemTypesTest/generates-problem-types.output2.swift",
      buildString {
        FileSpec
          .get("", accountNotFoundType)
          .writeTo(this)
      },
    )

    val testResolverType = findType("TestResolverProblem", builtTypes)
    assertSwiftSnapshot(
      "ProblemTypesTest/generates-problem-types.output3.swift",
      buildString {
        FileSpec
          .get("", testResolverType)
          .writeTo(this)
      },
    )
  }
}
