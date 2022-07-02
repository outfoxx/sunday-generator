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
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.TypeName
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  @Test
  fun `test generated types for type annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-type.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val type = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      "FormData",
      (type.members.firstOrNull() as? InterfaceSpec)?.propertySpecs?.firstOrNull()?.type?.toString()
    )
  }

  @Test
  fun `test generated module for typeScriptModelModule annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-model-module.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val generatedTypes = generateTypes(testUri, typeRegistry, compiler)

    assertThat(generatedTypes.keys, hasItem(TypeName.namedImport("Test", "!explicit/test")))
  }

  @Test
  fun `test generated module for typeScriptModule annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-module.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val generatedTypes = generate(testUri, typeRegistry, compiler) {
      TypeScriptSundayGenerator(it, typeRegistry, typeScriptSundayTestOptions)
    }

    assertThat(generatedTypes.keys, hasItem(TypeName.namedImport("API", "!explicit/client/api")))
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Group@!group", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface GroupSpec {

          value: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Group implements GroupSpec {

          value: string;

          constructor(init: GroupSpec) {
            this.value = init.value;
          }

          copy(changes: Partial<GroupSpec>): Group {
            return new Group(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Group(value='${'$'}{this.value}')`;
          }

        }

        export namespace Group {

          export interface Member1Spec extends GroupSpec {

            memberValue1: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Member1 extends Group implements Member1Spec {

            memberValue1: string;

            constructor(init: Member1Spec) {
              super(init);
              this.memberValue1 = init.memberValue1;
            }

            copy(changes: Partial<Member1Spec>): Member1 {
              return new Member1(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Group.Member1(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}')`;
            }

          }

          export namespace Member1 {

            export interface SubSpec extends Member1Spec {

              subMemberValue: string;

            }

            @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
            export class Sub extends Member1 implements SubSpec {

              subMemberValue: string;

              constructor(init: SubSpec) {
                super(init);
                this.subMemberValue = init.subMemberValue;
              }
        
              copy(changes: Partial<SubSpec>): Sub {
                return new Sub(Object.assign({}, this, changes));
              }
        
              toString(): string {
                return `Group.Member1.Sub(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}', subMemberValue='${'$'}{this.subMemberValue}')`;
              }

            }

          }

          export interface Member2Spec extends GroupSpec {

            memberValue2: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Member2 extends Group implements Member2Spec {

            memberValue2: string;

            constructor(init: Member2Spec) {
              super(init);
              this.memberValue2 = init.memberValue2;
            }

            copy(changes: Partial<Member2Spec>): Member2 {
              return new Member2(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Group.Member2(value='${'$'}{this.value}', memberValue2='${'$'}{this.memberValue2}')`;
            }

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
  fun `test class hierarchy generated for 'nested' annotation (dashed scheme)`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-dashed.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Group@!group", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface GroupSpec {

          value: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Group implements GroupSpec {

          value: string;

          constructor(init: GroupSpec) {
            this.value = init.value;
          }

          copy(changes: Partial<GroupSpec>): Group {
            return new Group(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Group(value='${'$'}{this.value}')`;
          }

        }

        export namespace Group {

          export interface Member1Spec extends GroupSpec {

            memberValue1: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Member1 extends Group implements Member1Spec {

            memberValue1: string;

            constructor(init: Member1Spec) {
              super(init);
              this.memberValue1 = init.memberValue1;
            }

            copy(changes: Partial<Member1Spec>): Member1 {
              return new Member1(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Group.Member1(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}')`;
            }

          }

          export namespace Member1 {

            export interface SubSpec extends Member1Spec {

              subMemberValue: string;

            }

            @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
            export class Sub extends Member1 implements SubSpec {

              subMemberValue: string;

              constructor(init: SubSpec) {
                super(init);
                this.subMemberValue = init.subMemberValue;
              }
        
              copy(changes: Partial<SubSpec>): Sub {
                return new Sub(Object.assign({}, this, changes));
              }
        
              toString(): string {
                return `Group.Member1.Sub(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}', subMemberValue='${'$'}{this.subMemberValue}')`;
              }

            }

          }

          export interface Member2Spec extends GroupSpec {

            memberValue2: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Member2 extends Group implements Member2Spec {

            memberValue2: string;

            constructor(init: Member2Spec) {
              super(init);
              this.memberValue2 = init.memberValue2;
            }

            copy(changes: Partial<Member2Spec>): Member2 {
              return new Member2(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Group.Member2(value='${'$'}{this.value}', memberValue2='${'$'}{this.memberValue2}')`;
            }

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
  fun `test class hierarchy generated for 'nested' annotation using library types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Root@!root", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface RootSpec {

          value: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Root implements RootSpec {

          value: string;

          constructor(init: RootSpec) {
            this.value = init.value;
          }

          copy(changes: Partial<RootSpec>): Root {
            return new Root(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Root(value='${'$'}{this.value}')`;
          }

        }

        export namespace Root {

          export interface GroupSpec {

            value: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Group implements GroupSpec {

            value: string;

            constructor(init: GroupSpec) {
              this.value = init.value;
            }

            copy(changes: Partial<GroupSpec>): Group {
              return new Group(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Root.Group(value='${'$'}{this.value}')`;
            }

          }
        
          export namespace Group {

            export interface MemberSpec {
  
              memberValue: string;
  
            }
  
            @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
            export class Member implements MemberSpec {
  
              memberValue: string;

              constructor(init: MemberSpec) {
                this.memberValue = init.memberValue;
              }
  
              copy(changes: Partial<MemberSpec>): Member {
                return new Member(Object.assign({}, this, changes));
              }
  
              toString(): string {
                return `Root.Group.Member(memberValue='${'$'}{this.memberValue}')`;
              }

            }

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation using only library types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-nested-lib2.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Root@!root", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface RootSpec {

          value: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Root implements RootSpec {

          value: string;

          constructor(init: RootSpec) {
            this.value = init.value;
          }

          copy(changes: Partial<RootSpec>): Root {
            return new Root(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Root(value='${'$'}{this.value}')`;
          }

        }

        export namespace Root {

          export interface GroupSpec {

            value: string;

          }

          @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
          export class Group implements GroupSpec {

            value: string;

            constructor(init: GroupSpec) {
              this.value = init.value;
            }

            copy(changes: Partial<GroupSpec>): Group {
              return new Group(Object.assign({}, this, changes));
            }

            toString(): string {
              return `Root.Group(value='${'$'}{this.value}')`;
            }

          }
        
          export namespace Group {

            export interface MemberSpec {
  
              memberValue: string;
  
            }
  
            @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
            export class Member implements MemberSpec {
  
              memberValue: string;

              constructor(init: MemberSpec) {
                this.memberValue = init.memberValue;
              }
  
              copy(changes: Partial<MemberSpec>): Member {
                return new Member(Object.assign({}, this, changes));
              }
  
              toString(): string {
                return `Root.Group.Member(memberValue='${'$'}{this.memberValue}')`;
              }

            }

          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test class generated TypeScript implementations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-ts-impl.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonIgnore} from '@outfoxx/jackson-js';
        import {OffsetDateTime} from '@outfoxx/sunday';


        export interface TestSpec {
        }

        export class Test implements TestSpec {

          @JsonIgnore()
          get className(): string {
            return OffsetDateTime.name + '-value-' + "-literal";
          }

          toString(): string {
            return `Test()`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test class hierarchy generated for externally discriminated types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val builtTypes = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)

    val parenTypeSpec = builtTypes[TypeName.namedImport("Parent", "!parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        import {Child1, Child2} from './index';
        import {JsonSubTypes} from '@outfoxx/jackson-js';


        export interface ParentSpec {
        }

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
        FileSpec.get(parenTypeSpec)
          .writeTo(this)
      }
    )

    val child1TypeSpec = builtTypes[TypeName.namedImport("Child1", "!child1")]
      ?: error("Child1 type is not defined")

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
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

          get type(): string {
            return 'Child1';
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
        FileSpec.get(child1TypeSpec)
          .writeTo(this)
      }
    )

    val child2TypeSpec = builtTypes[TypeName.namedImport("Child2", "!child2")]
      ?: error("Child2 type is not defined")

    assertEquals(
      """
        import {Parent, ParentSpec} from './parent';
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

          get type(): string {
            return 'child2';
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
        FileSpec.get(child2TypeSpec)
          .writeTo(this)
      }
    )

    val testTypeSpec = builtTypes[TypeName.namedImport("Test", "!test")]
      ?: error("Test type is not defined")

    assertEquals(
      """
        import {Child1, Child2} from './index';
        import {Parent} from './parent';
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';


        export interface TestSpec {

          parent: Parent;

          parentType: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Test implements TestSpec {
        
          @JsonTypeInfo({
            use: JsonTypeInfoId.NAME,
            include: JsonTypeInfoAs.EXTERNAL_PROPERTY,
            property: 'parentType',
          })
          @JsonSubTypes({
            types: [
              {class: () => Child1, name: 'Child1'},
              {class: () => Child2, name: 'child2'}
            ]
          })
          @JsonClassType({type: () => [Parent]})
          parent: Parent;
        
          @JsonClassType({type: () => [String]})
          parentType: string;
        
          constructor(init: TestSpec) {
            this.parent = init.parent;
            this.parentType = init.parentType;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(parent='${'$'}{this.parent}', parentType='${'$'}{this.parentType}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(testTypeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test external discriminator must exist`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val exception =
      assertThrows<GenerationException> {
        generateTypes(testUri, typeRegistry, compiler)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonCreator, JsonCreatorMode} from '@outfoxx/jackson-js';
        
        
        export interface TestSpec {

          string: string;

          int: number;

          bool: boolean;

          nullable: string | null;
        
          optional?: string;

          nullableOptional?: string | null;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        export class Test implements TestSpec {

          string: string;

          int: number;

          bool: boolean;

          nullable: string | null;
        
          optional: string | undefined;
        
          nullableOptional: string | null | undefined;

          constructor(init: TestSpec) {
            this.string = init.string;
            this.int = init.int;
            this.bool = init.bool;
            this.nullable = init.nullable;
            this.optional = init.optional;
            this.nullableOptional = init.nullableOptional;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(string='${'$'}{this.string}', int='${'$'}{this.int}', bool='${'$'}{this.bool}', nullable='${'$'}{this.nullable}', optional='${'$'}{this.optional}', nullableOptional='${'$'}{this.nullableOptional}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test patchable class generation with Jackson`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonInclude, JsonIncludeType} from '@outfoxx/jackson-js';


        export interface TestSpec {

          string: string;

          int: number;

          bool: boolean;

          nullable: string | null;
        
          optional?: string;

          nullableOptional?: string | null;

        }

        @JsonCreator({ mode: JsonCreatorMode.DELEGATING })
        @JsonInclude({value: JsonIncludeType.ALWAYS})
        export class Test implements TestSpec {

          @JsonClassType({type: () => [String]})
          string: string;

          @JsonClassType({type: () => [Number]})
          int: number;

          @JsonClassType({type: () => [Boolean]})
          bool: boolean;

          @JsonClassType({type: () => [String]})
          nullable: string | null;
        
          @JsonClassType({type: () => [String]})
          optional: string | undefined;
        
          @JsonClassType({type: () => [String]})
          nullableOptional: string | null | undefined;

          constructor(init: TestSpec) {
            this.string = init.string;
            this.int = init.int;
            this.bool = init.bool;
            this.nullable = init.nullable;
            this.optional = init.optional;
            this.nullableOptional = init.nullableOptional;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(string='${'$'}{this.string}', int='${'$'}{this.int}', bool='${'$'}{this.bool}', nullable='${'$'}{this.nullable}', optional='${'$'}{this.optional}', nullableOptional='${'$'}{this.nullableOptional}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }
}
