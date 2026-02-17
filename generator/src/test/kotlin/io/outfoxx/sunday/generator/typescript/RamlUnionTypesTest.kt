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
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
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

    assertEquals(
      """

      export interface TestSpec {

        any: number | string;

        duplicate: string;

        nullable: string | null;

      }

      export class Test implements TestSpec {

        any: number | string;

        duplicate: string;

        nullable: string | null;

        constructor(init: TestSpec) {
          this.any = init.any;
          this.duplicate = init.duplicate;
          this.nullable = init.nullable;
        }

        copy(changes: Partial<TestSpec>): Test {
          return new Test(Object.assign({}, this, changes));
        }

        toString(): string {
          return `Test(any='${'$'}{this.any}', duplicate='${'$'}{this.duplicate}', nullable='${'$'}{this.nullable}')`;
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated types for common object types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-common-objects.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
      import {Base} from './base';


      export interface TestSpec {

        value: Base;

      }

      export class Test implements TestSpec {

        value: Base;

        constructor(init: TestSpec) {
          this.value = init.value;
        }

        copy(changes: Partial<TestSpec>): Test {
          return new Test(Object.assign({}, this, changes));
        }

        toString(): string {
          return `Test(value='${'$'}{this.value}')`;
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated types for similarly named but uncommon object types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/unions-uncommon-objects.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtType = generateTypes(testUri, typeRegistry, compiler)

    val child1TypeModSpec = findTypeMod("Child1@!test/lib/child1", builtType)

    assertEquals(
      """
      import {Base, BaseSpec} from './base';


      export interface Child1Spec extends BaseSpec {

        childValue: string;

      }

      export class Child1 extends Base implements Child1Spec {

        childValue: string;

        constructor(init: Child1Spec) {
          super(init);
          this.childValue = init.childValue;
        }

        copy(changes: Partial<Child1Spec>): Child1 {
          return new Child1(Object.assign({}, this, changes));
        }

        toString(): string {
          return `Child1(value='${'$'}{this.value}', childValue='${'$'}{this.childValue}')`;
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get(child1TypeModSpec, "test/lib/child1")
          .writeTo(this)
      },
    )

    val child2TypeModSpec = findTypeMod("Child2@!test/lib/child2", builtType)

    assertEquals(
      """
      import {Base, BaseSpec} from '../../base';


      export interface Child2Spec extends BaseSpec {

        childValue: string;

      }

      export class Child2 extends Base implements Child2Spec {

        childValue: string;

        constructor(init: Child2Spec) {
          super(init);
          this.childValue = init.childValue;
        }

        copy(changes: Partial<Child2Spec>): Child2 {
          return new Child2(Object.assign({}, this, changes));
        }

        toString(): string {
          return `Child2(value='${'$'}{this.value}', childValue='${'$'}{this.childValue}')`;
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get(child2TypeModSpec, "test/lib/child2")
          .writeTo(this)
      },
    )

    val testTypeModSpec = findTypeMod("Test@!test/lib/test", builtType)

    assertEquals(
      """
      import {Child1} from './test/lib/child1';
      import {Child2} from './test/lib/child2';
      import {Test as Test_, TestSpec as TestSpec_} from './test/lib/test';


      export interface TestSpec {

        value: Child1 | Child2;

      }

      export class Test implements TestSpec_ {

        value: Child1 | Child2;

        constructor(init: TestSpec_) {
          this.value = init.value;
        }

        copy(changes: Partial<TestSpec_>): Test_ {
          return new Test_(Object.assign({}, this, changes));
        }

        toString(): string {
          return `Test(value='${'$'}{this.value}')`;
        }

      }

      """.trimIndent(),
      buildString {
        FileSpec
          .get(testTypeModSpec)
          .writeTo(this)
      },
    )
  }
}
