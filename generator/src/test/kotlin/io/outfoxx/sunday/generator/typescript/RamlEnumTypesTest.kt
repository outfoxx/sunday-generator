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

import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Enum Types Test")
class RamlEnumTypesTest {

  @Test
  fun `test names generated for enums types & values`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/enums.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val generatedTypes = generateTypes(testUri, typeRegistry, compiler)
    val enumTypeModSpec = findTypeMod("TestEnum@!test-enum", generatedTypes)
    val usageTypeModSpec = findTypeMod("Test@!test", generatedTypes)

    assertEquals(
      """
        
        export enum TestEnum {
          None = 'none',
          Some = 'some',
          All = 'all',
          SnakeCase = 'snake_case',
          KebabCase = 'kebab-case',
          InvalidChar = 'invalid:char'
        }

      """.trimIndent(),
      buildString {
        FileSpec.get(enumTypeModSpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """       
        import {TestEnum} from './test-enum';
        import {JsonClassType} from '@outfoxx/jackson-js';
        
        
        export interface TestSpec {
        
          enumVal: TestEnum;
        
          setVal: Set<TestEnum>;
        
          arrayVal: Array<TestEnum>;
        
        }
        
        export class Test implements TestSpec {
        
          @JsonClassType({type: () => [Object]})
          enumVal: TestEnum;
        
          @JsonClassType({type: () => [Set, [Object]]})
          setVal: Set<TestEnum>;
        
          @JsonClassType({type: () => [Array, [Object]]})
          arrayVal: Array<TestEnum>;
        
          constructor(init: TestSpec) {
            this.enumVal = init.enumVal;
            this.setVal = init.setVal;
            this.arrayVal = init.arrayVal;
          }
        
          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }
        
          toString(): string {
            return `Test(enumVal='${'$'}{this.enumVal}', setVal='${'$'}{this.setVal}', arrayVal='${'$'}{this.arrayVal}')`;
          }
        
        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(usageTypeModSpec)
          .writeTo(this)
      }
    )
  }
}
