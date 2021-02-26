package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[TypeScript] [RAML] Discriminated Types Test")
class RamlDiscriminatedTypesTest {

  @Test
  fun `test polymorphism added to generated classes of string discriminated types`(
    @ResourceUri("raml/type-gen/discriminated/simple.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))
    val generatedTypes = generateTypes(testUri, typeRegistry)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)

    assertEquals(
      """
        import {JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';
        
        
        export interface Parent {
        
          type: string;
        
        }
        
        @JsonTypeInfo({
          use: JsonTypeInfoId.NAME,
          include: JsonTypeInfoAs.PROPERTY,
          property: 'type',
        })
        @JsonSubTypes({
          types: [
            {class: () => eval('Child1'), name: 'Child1'},
            {class: () => eval('Child2'), name: 'child2'}
          ]
        })
        export class Parent implements Parent {
        
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
        import {Parent} from './parent';
        
        
        export interface Child1 extends Parent {
        
          value: string | undefined;
        
          value1: number;
        
        }
        
        export class Child1 extends Parent implements Child1 {
        
          constructor(public value: string | undefined, public value1: number) {
            super();
          }
        
          get type(): string {
            return 'Child1';
          }
        
          copy(src: Partial<Child1>): Child1 {
            return new Child1(src.value ?? this.value, src.value1 ?? this.value1);
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
        import {Parent} from './parent';
        
        
        export interface Child2 extends Parent {
        
          value: string | undefined;
        
          value2: number;
        
        }
        
        export class Child2 extends Parent implements Child2 {
        
          constructor(public value: string | undefined, public value2: number) {
            super();
          }
        
          get type(): string {
            return 'child2';
          }
        
          copy(src: Partial<Child2>): Child2 {
            return new Child2(src.value ?? this.value, src.value2 ?? this.value2);
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
    @ResourceUri("raml/type-gen/discriminated/enum.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))
    val generatedTypes = generateTypes(testUri, typeRegistry)

    val parentTypeModSpec = findTypeMod("Parent@!parent", generatedTypes)

    assertEquals(
      """
        import {Type} from './type';
        import {JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';
        
        
        export interface Parent {
        
          type: Type;
        
        }
        
        @JsonTypeInfo({
          use: JsonTypeInfoId.NAME,
          include: JsonTypeInfoAs.PROPERTY,
          property: 'type',
        })
        @JsonSubTypes({
          types: [
            {class: () => eval('Child1'), name: 'Child1'},
            {class: () => eval('Child2'), name: 'Child2'}
          ]
        })
        export class Parent implements Parent {
        
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
        import {Parent} from './parent';
        import {Type} from './type';
        
        
        export interface Child1 extends Parent {
        
          value: string | undefined;
        
        }
        
        export class Child1 extends Parent implements Child1 {
        
          constructor(public value: string | undefined) {
            super();
          }
        
          get type(): Type {
            return Type.Child1;
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
        FileSpec.get(child1TypeModSpec)
          .writeTo(this)
      }
    )

    val child2TypeModSpec = findTypeMod("Child2@!child2", generatedTypes)

    assertEquals(
      """
        import {Parent} from './parent';
        import {Type} from './type';
        
        
        export interface Child2 extends Parent {
        
          value: string | undefined;
        
        }
        
        export class Child2 extends Parent implements Child2 {
        
          constructor(public value: string | undefined) {
            super();
          }
        
          get type(): Type {
            return Type.Child2;
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
        FileSpec.get(child2TypeModSpec)
          .writeTo(this)
      }
    )

  }

}
