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
@DisplayName("[TypeScript] [RAML] Union Types Test")
class RamlUnionTypesTest {

  @Test
  fun `test generated types for general union types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-general.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlUnionTypesTest/unions-general.test.ts", output)
  }

  @Test
  fun `test generated types for common object types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-common-objects.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))
    val output =
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      }

    assertSnapshot("RamlUnionTypesTest/unions-common-objects.test.ts", output)
  }
}
