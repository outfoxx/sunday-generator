package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
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

@ExtendWith(ResourceExtension::class)
@DisplayName("[TypeScript] [RAML] Type Annotations Test")
class RamlTypeAnnotationsTest {

  @Test
  fun `test generated module for typeScriptModelModule annotation`(
    @ResourceUri("raml/type-gen/annotations/type-ts-model-module.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val generatedTypes = generateTypes(testUri, typeRegistry)

    assertThat(generatedTypes.keys, hasItem(TypeName.namedImport("Test", "!explicit/test")))
  }

  @Test
  fun `test generated module for typeScriptModule annotation`(
    @ResourceUri("raml/type-gen/annotations/type-ts-module.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val generatedTypes = generate(testUri, typeRegistry) {
      TypeScriptSundayGenerator(it, typeRegistry, "http://example.com", emptyList())
    }

    assertThat(generatedTypes.keys, hasItem(TypeName.namedImport("API", "!explicit/client/api")))
  }

  @Test
  fun `test class hierarchy generated for 'nested' annotation`(
    @ResourceUri("raml/type-gen/annotations/type-nested.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Group@!group", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Group {

          value: string;

        }

        export class Group implements Group {

          value: string;

          constructor(value: string) {
            this.value = value;
          }

          toString(): string {
            return `Group(value='${'$'}{this.value}')`;
          }

        }

        export namespace Group {

          export interface Member1 extends Group {

            memberValue1: string;

          }

          export class Member1 extends Group implements Member1 {

            memberValue1: string;

            constructor(value: string, memberValue1: string) {
              super(value);
              this.memberValue1 = memberValue1;
            }

            toString(): string {
              return `Group.Member1(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}')`;
            }

          }

          export namespace Member1 {

            export interface Sub extends Member1 {

              subMemberValue: string;

            }

            export class Sub extends Member1 implements Sub {

              subMemberValue: string;

              constructor(value: string, memberValue1: string, subMemberValue: string) {
                super(value, memberValue1);
                this.subMemberValue = subMemberValue;
              }
        
              copy(src: Partial<Sub>): Sub {
                return new Sub(src.value ?? this.value, src.memberValue1 ?? this.memberValue1,
                    src.subMemberValue ?? this.subMemberValue);
              }
        
              toString(): string {
                return `Group.Member1.Sub(value='${'$'}{this.value}', memberValue1='${'$'}{this.memberValue1}', subMemberValue='${'$'}{this.subMemberValue}')`;
              }

            }

          }

          export interface Member2 extends Group {

            memberValue2: string;

          }

          export class Member2 extends Group implements Member2 {

            memberValue2: string;

            constructor(value: string, memberValue2: string) {
              super(value);
              this.memberValue2 = memberValue2;
            }

            copy(src: Partial<Member2>): Member2 {
              return new Member2(src.value ?? this.value, src.memberValue2 ?? this.memberValue2);
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
    @ResourceUri("raml/type-gen/annotations/type-nested-lib.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Root@!root", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Root {

          value: string;

        }

        export class Root implements Root {

          value: string;

          constructor(value: string) {
            this.value = value;
          }

          copy(src: Partial<Root>): Root {
            return new Root(src.value ?? this.value);
          }

          toString(): string {
            return `Root(value='${'$'}{this.value}')`;
          }

        }

        export namespace Root {

          export interface Group {

            value: string;

          }

          export class Group implements Group {

            value: string;

            constructor(value: string) {
              this.value = value;
            }

            copy(src: Partial<Group>): Group {
              return new Group(src.value ?? this.value);
            }

            toString(): string {
              return `Root.Group(value='${'$'}{this.value}')`;
            }

          }
        
          export namespace Group {

            export interface Member {
  
              memberValue: string;
  
            }
  
            export class Member implements Member {
  
              memberValue: string;

              constructor(memberValue: string) {
                this.memberValue = memberValue;
              }
  
              copy(src: Partial<Member>): Member {
                return new Member(src.memberValue ?? this.memberValue);
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
    @ResourceUri("raml/type-gen/annotations/type-ts-impl.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {JsonIgnore} from '@outfoxx/jackson-js';
        import {OffsetDateTime} from '@outfoxx/sunday';


        export interface Test {
        }

        export class Test implements Test {

          @JsonIgnore()
          get className(): string {
            return OffsetDateTime.name + '-value-' + "-literal";
          }

          copy(src: Partial<Test>): Test {
            return new Test();
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
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val parenTypeSpec = builtTypes[TypeName.namedImport("Parent", "!parent")]
      ?: error("Parent type is not defined")

    assertEquals(
      """
        import {Child1} from './child1';
        import {Child2} from './child2';
        import {JsonSubTypes} from '@outfoxx/jackson-js';


        export interface Parent {

          type: string;

        }

        @JsonSubTypes({
          types: [
            {class: () => eval('Child1'), name: 'Child1'},
            {class: () => eval('Child2'), name: 'child2'}
          ]
        })
        export abstract class Parent implements Parent {

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
        import {Parent} from './parent';
        import {JsonClassType} from '@outfoxx/jackson-js';


        export interface Child1 extends Parent {

          value: string | undefined;

        }

        export class Child1 extends Parent implements Child1 {
        
          @JsonClassType({type: () => [String]})
          value: string | undefined;

          constructor(value: string | undefined) {
            super();
            this.value = value;
          }

          get type(): string {
            return 'Child1';
          }

          copy(src: Partial<Child1>): Child1 {
            return new Child1(src.value ?? this.value);
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
        import {Parent} from './parent';
        import {JsonClassType} from '@outfoxx/jackson-js';


        export interface Child2 extends Parent {

          value: string | undefined;

        }

        export class Child2 extends Parent implements Child2 {
        
          @JsonClassType({type: () => [String]})
          value: string | undefined;

          constructor(value: string | undefined) {
            super();
            this.value = value;
          }

          get type(): string {
            return 'child2';
          }

          copy(src: Partial<Child2>): Child2 {
            return new Child2(src.value ?? this.value);
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
        import {Parent} from './parent';
        import {JsonClassType, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';


        export interface Test {

          parent: Parent;

          parentType: string;

        }

        export class Test implements Test {
        
          @JsonTypeInfo({
            use: JsonTypeInfoId.NAME,
            include: JsonTypeInfoAs.EXTERNAL_PROPERTY,
            property: 'parentType',
          })
          @JsonClassType({type: () => [Parent]})
          parent: Parent;
        
          @JsonClassType({type: () => [String]})
          parentType: string;
        
          constructor(parent: Parent, parentType: string) {
            this.parent = parent;
            this.parentType = parentType;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.parent ?? this.parent, src.parentType ?? this.parentType);
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
    @ResourceUri("raml/type-gen/annotations/type-external-discriminator-invalid.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val exception =
      assertThrows<IllegalStateException> {
        generateTypes(testUri, typeRegistry)
      }

    assertTrue(exception.message?.contains("External discriminator") ?: false)
  }

  @Test
  fun `test patchable class generation`(
    @ResourceUri("raml/type-gen/annotations/type-patchable.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Test {

          string: string;

          int: number;

          bool: boolean;

        }

        export class Test implements Test {

          string: string;

          int: number;

          bool: boolean;

          constructor(string: string, int: number, bool: boolean) {
            this.string = string;
            this.int = int;
            this.bool = bool;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.string ?? this.string, src.int ?? this.int, src.bool ?? this.bool);
          }

          toString(): string {
            return `Test(string='${'$'}{this.string}', int='${'$'}{this.int}', bool='${'$'}{this.bool}')`;
          }

          patch(source: Partial<Test>): Test.Patch {
            return new Test.Patch(
              source['string'] !== undefined ? this.string : null,
              source['int'] !== undefined ? this.int : null,
              source['bool'] !== undefined ? this.bool : null
            );
          }

        }

        export namespace Test {

          export class Patch {

            constructor(public string: string | null | undefined, public int: number | null | undefined,
                public bool: boolean | null | undefined) {
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

}
