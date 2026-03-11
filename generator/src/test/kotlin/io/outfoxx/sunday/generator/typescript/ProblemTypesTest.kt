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

package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.assertSnapshot
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val invalidIdTypeModSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)
    val invalidOutput =
      buildString {
        FileSpec
          .get(invalidIdTypeModSpec, "invalid-id-problem")
          .writeTo(this)
      }

    assertSnapshot("ProblemTypesTest/invalid-id-problem.ts", invalidOutput)
  }

  @Test
  fun `generates problem types with temporal aliases`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types-all.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val createFailedTypeModSpec = findTypeMod("CreateFailedProblem@!create-failed-problem", builtTypes)
    val createFailedOutput =
      buildString {
        FileSpec
          .get(createFailedTypeModSpec, "create-failed-problem")
          .writeTo(this)
      }

    assertSnapshot("ProblemTypesTest/create-failed-problem.ts", createFailedOutput)
  }
}
