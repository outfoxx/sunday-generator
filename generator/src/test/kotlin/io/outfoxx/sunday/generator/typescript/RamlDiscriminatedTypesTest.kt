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
@DisplayName("[TypeScript] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)

    assertEquals(
      """
        import {Child1, Child2} from './index';
        import {JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';
        
        
        export interface ParentSpec {
        }
        
        @JsonTypeInfo({
          use: JsonTypeInfoId.NAME,
          include: JsonTypeInfoAs.PROPERTY,
          property: 'type',
        })
        @JsonSubTypes({
          types: [
            {class: () => Child1, name: 'Child1'},
            {class: () => Child2, name: 'child2'}
          ]
        })
        export abstract class Parent implements ParentSpec {
        
          toString(): string {
            return `Parent()`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(parentTypeModSpec)
          .writeTo(this)
      }
    )

    val child1TypeModSpec = findTypeMod("Child1@!child1", generatedTypes)

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
        import {JsonClassType, JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface Child1Spec extends ParentSpec {
        
          value?: string;
        
          value1: number;
        
        }
        
        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Child1 extends Parent implements Child1Spec {
        
          @JsonClassType({type: () => [String]})
          value: string | undefined;
        
          @JsonClassType({type: () => [Number]})
          value1: number;
        
          constructor(init: Child1Spec) {
            super();
            this.value = init.value;
            this.value1 = init.value1;
          }
        
          get type(): string {
            return 'Child1';
          }
        
          copy(changes: Partial<Child1Spec>): Child1 {
            return new Child1(Object.assign({}, this, changes));
          }
        
          toString(): string {
            return `Child1(value='${'$'}{this.value}', value1='${'$'}{this.value1}')`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(child1TypeModSpec)
          .writeTo(this)
      }
    )

    val child2TypeModSpec = findTypeMod("Child2@!child2", generatedTypes)

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
        import {JsonClassType, JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface Child2Spec extends ParentSpec {
        
          value?: string;
        
          value2: number;
        
        }
        
        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Child2 extends Parent implements Child2Spec {
        
          @JsonClassType({type: () => [String]})
          value: string | undefined;
        
          @JsonClassType({type: () => [Number]})
          value2: number;
        
          constructor(init: Child2Spec) {
            super();
            this.value = init.value;
            this.value2 = init.value2;
          }
        
          get type(): string {
            return 'child2';
          }
        
          copy(changes: Partial<Child2Spec>): Child2 {
            return new Child2(Object.assign({}, this, changes));
          }
        
          toString(): string {
            return `Child2(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(child2TypeModSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test polymorphism added to generated classes of enum discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))
    val generatedTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)

    assertEquals(
      """
        import {Child1, Child2} from './index';
        import {Type} from './type';
        import {JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';
        
        
        export interface ParentSpec {
        }
        
        @JsonTypeInfo({
          use: JsonTypeInfoId.NAME,
          include: JsonTypeInfoAs.PROPERTY,
          property: 'type',
        })
        @JsonSubTypes({
          types: [
            {class: () => Child1, name: Type.Child1},
            {class: () => Child2, name: Type.Child2}
          ]
        })
        export abstract class Parent implements ParentSpec {
        
          toString(): string {
            return `Parent()`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(parentTypeModSpec)
          .writeTo(this)
      }
    )

    val child1TypeModSpec = findTypeMod("Child1@!child1", generatedTypes)

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
        import {Type} from './type';
        import {JsonClassType, JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface Child1Spec extends ParentSpec {
        
          value?: string;
        
        }
        
        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Child1 extends Parent implements Child1Spec {

          @JsonClassType({type: () => [String]})
          value: string | undefined;

          constructor(init: Child1Spec) {
            super();
            this.value = init.value;
          }

          get type(): Type {
            return Type.Child1;
          }
        
          copy(changes: Partial<Child1Spec>): Child1 {
            return new Child1(Object.assign({}, this, changes));
          }
        
          toString(): string {
            return `Child1(value='${'$'}{this.value}')`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(child1TypeModSpec)
          .writeTo(this)
      }
    )

    val child2TypeModSpec = findTypeMod("Child2@!child2", generatedTypes)

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
        import {Type} from './type';
        import {JsonClassType, JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';

        
        export interface Child2Spec extends ParentSpec {
        
          value?: string;
        
        }
        
        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Child2 extends Parent implements Child2Spec {
        
          @JsonClassType({type: () => [String]})
          value: string | undefined;
        
          constructor(init: Child2Spec) {
            super();
            this.value = init.value;
          }
        
          get type(): Type {
            return Type.Child2;
          }
        
          copy(changes: Partial<Child2Spec>): Child2 {
            return new Child2(Object.assign({}, this, changes));
          }
        
          toString(): string {
            return `Child2(value='${'$'}{this.value}')`;
          }
        
        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(child2TypeModSpec)
          .writeTo(this)
      }
    )
  }
}
