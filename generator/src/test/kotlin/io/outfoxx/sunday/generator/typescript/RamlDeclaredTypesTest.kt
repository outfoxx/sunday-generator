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

import io.outfoxx.sunday.generator.GenerationException
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findNestedType
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Declared Types Test")
class RamlDeclaredTypesTest {

  @Test
  fun `test multiple declarations with same name in separate files throws collision error`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/decl-dups-fail.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in server mode to test collision detection
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("Multiple classes") ?: false)
  }

  @Test
  fun `test multiple declarations with same name in separate files is fixed by package annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/decl-dups.raml") testUri: URI
  ) {

    // 'Client' mode assigns a specific package, generate in client mode to allow generation
    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {Test as Test_, TestSpec as TestSpec_} from './test/client/test';
        
        
        export interface TestSpec extends TestSpec_ {

          value2: string;

        }
        
        export class Test extends Test_ implements TestSpec {

          value2: string;

          constructor(init: TestSpec) {
            super(init);
            this.value2 = init.value2;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test resource fragments with anonymous types can be imported multiple times`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/res-frag-anon-decl-dup.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("API@!api", generateTypes(testUri, typeRegistry, compiler))

    findNestedType(typeModSpec, "API.Payload")
  }
}
