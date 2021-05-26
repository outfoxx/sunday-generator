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
@DisplayName("[TypeScript] [RAML] Union Types Test")
class RamlUnionTypesTest {

  @Test
  fun `test generated types for general union types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-general.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface Test {

          any: number | string;

          duplicate: string;

          nullable: string | null;

        }

        export class Test implements Test {

          any: number | string;

          duplicate: string;

          nullable: string | null;

          constructor(any: number | string, duplicate: string, nullable: string | null) {
            this.any = any;
            this.duplicate = duplicate;
            this.nullable = nullable;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.any ?? this.any, src.duplicate ?? this.duplicate,
                src.nullable ?? this.nullable);
          }

          toString(): string {
            return `Test(any='${'$'}{this.any}', duplicate='${'$'}{this.duplicate}', nullable='${'$'}{this.nullable}')`;
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
  fun `test generated types for common object types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-common-objects.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {Base} from './base';


        export interface Test {

          value: Base;

        }

        export class Test implements Test {

          value: Base;

          constructor(value: Base) {
            this.value = value;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.value ?? this.value);
          }

          toString(): string {
            return `Test(value='${'$'}{this.value}')`;
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
  fun `test generated types for similarly named but uncommon object types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-uncommon-objects.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtType = generateTypes(testUri, typeRegistry, compiler)

    val child1TypeModSpec = findTypeMod("Child1@!test/lib/child1", builtType)

    assertEquals(
      """
        import {Base} from './base';


        export interface Child1 extends Base {

          childValue: string;

        }

        export class Child1 extends Base implements Child1 {

          childValue: string;

          constructor(value: string, childValue: string) {
            super(value);
            this.childValue = childValue;
          }

          copy(src: Partial<Child1>): Child1 {
            return new Child1(src.value ?? this.value, src.childValue ?? this.childValue);
          }

          toString(): string {
            return `Child1(value='${'$'}{this.value}', childValue='${'$'}{this.childValue}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(child1TypeModSpec, "test/lib/child1")
          .writeTo(this)
      }
    )

    val child2TypeModSpec = findTypeMod("Child2@!test/lib/child2", builtType)

    assertEquals(
      """
        import {Base} from '../../base';


        export interface Child2 extends Base {

          childValue: string;

        }

        export class Child2 extends Base implements Child2 {

          childValue: string;

          constructor(value: string, childValue: string) {
            super(value);
            this.childValue = childValue;
          }
        
          copy(src: Partial<Child2>): Child2 {
            return new Child2(src.value ?? this.value, src.childValue ?? this.childValue);
          }

          toString(): string {
            return `Child2(value='${'$'}{this.value}', childValue='${'$'}{this.childValue}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(child2TypeModSpec, "test/lib/child2")
          .writeTo(this)
      }
    )

    val testTypeModSpec = findTypeMod("Test@!test/lib/test", builtType)

    assertEquals(
      """
        import {Child1} from './test/lib/child1';
        import {Child2} from './test/lib/child2';
        import {Test as Test_} from './test/lib/test';


        export interface Test {

          value: Child1 | Child2;

        }

        export class Test implements Test_ {

          value: Child1 | Child2;

          constructor(value: Child1 | Child2) {
            this.value = value;
          }

          copy(src: Partial<Test_>): Test_ {
            return new Test_(src.value ?? this.value);
          }

          toString(): string {
            return `Test(value='${'$'}{this.value}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(testTypeModSpec)
          .writeTo(this)
      }
    )
  }
}
