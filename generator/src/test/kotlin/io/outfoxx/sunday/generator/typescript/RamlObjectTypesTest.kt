package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry.Option.JacksonDecorators
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findNestedType
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.generator.typescript.tools.generateTypes
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.sunday.test.extensions.TypeScriptCompilerExtension
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.net.URI

@ExtendWith(ResourceExtension::class, TypeScriptCompilerExtension::class)
@DisplayName("[TypeScript] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface Test {

          map: object;

        }
        
        export class Test implements Test {

          map: object;

          constructor(map: object) {
            this.map = map;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.map ?? this.map);
          }

          toString(): string {
            return `Test(map='${'$'}{this.map}')`;
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
  fun `test generated nullability of property types in classes`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface Test {

          fromNilUnion: string | null;

          notRequired: string | undefined;

        }

        export class Test implements Test {

          fromNilUnion: string | null;

          notRequired: string | undefined;

          constructor(fromNilUnion: string | null, notRequired: string | undefined) {
            this.fromNilUnion = fromNilUnion;
            this.notRequired = notRequired;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.fromNilUnion ?? this.fromNilUnion, src.notRequired ?? this.notRequired);
          }

          toString(): string {
            return `Test(fromNilUnion='${'$'}{this.fromNilUnion}', notRequired='${'$'}{this.notRequired}')`;
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
  fun `test naming of types defined inline in property`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    findNestedType(typeModSpec, "Test", "Value") ?: fail("Nested type 'Value' not defined")
  }

  @Test
  fun `test naming of types defined inline in resource`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { TypeScriptSundayGenerator(it, typeRegistry, "http://example.com", emptyList()) }
    val typeModSpec = findTypeMod("API@!api", builtTypes)

    findNestedType(typeModSpec, "API", "FetchTestResponsePayload")
      ?: fail("Nested type 'FetchTestResponsePayload' not defined")
  }

  @Test
  fun `test generated classes for object hierarchy`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val testSpec = builtTypes[TypeName.namedImport("Test", "!test")]
    testSpec ?: fail("No Test class defined")

    val test2Spec = builtTypes[TypeName.namedImport("Test2", "!test2")]
    test2Spec ?: fail("No Test2 class defined")

    val emptySpec = builtTypes[TypeName.namedImport("Empty", "!empty")]
    emptySpec ?: fail("No Empty class defined")

    val test3Spec = builtTypes[TypeName.namedImport("Test3", "!test3")]
    test3Spec ?: fail("No Test3 class defined")

    assertEquals(
      """
        
        export interface Test {

          value: string;

        }

        export class Test implements Test {

          value: string;

          constructor(value: string) {
            this.value = value;
          }

          toString(): string {
            return `Test(value='${'$'}{this.value}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(testSpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import {Test} from './test';


        export interface Test2 extends Test {

          value2: string;

        }

        export class Test2 extends Test implements Test2 {

          value2: string;

          constructor(value: string, value2: string) {
            super(value);
            this.value2 = value2;
          }

          toString(): string {
            return `Test2(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(test2Spec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import {Test2} from './test2';


        export interface Empty extends Test2 {
        }

        export class Empty extends Test2 implements Empty {

          constructor(value: string, value2: string) {
            super(value, value2);
          }

          toString(): string {
            return `Empty(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(emptySpec)
          .writeTo(this)
      }
    )

    assertEquals(
      """
        import {Empty} from './empty';


        export interface Test3 extends Empty {

          value3: string;

        }

        export class Test3 extends Empty implements Test3 {

          value3: string;

          constructor(value: string, value2: string, value3: string) {
            super(value, value2);
            this.value3 = value3;
          }

          copy(src: Partial<Test3>): Test3 {
            return new Test3(src.value ?? this.value, src.value2 ?? this.value2, src.value3 ?? this.value3);
          }

          toString(): string {
            return `Test3(value='${'$'}{this.value}', value2='${'$'}{this.value2}', value3='${'$'}{this.value3}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(test3Spec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface Test {

          someValue: string;

          anotherValue: string;

        }

        export class Test implements Test {

          someValue: string;

          anotherValue: string;

          constructor(someValue: string, anotherValue: string) {
            this.someValue = someValue;
            this.anotherValue = anotherValue;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.someValue ?? this.someValue, src.anotherValue ?? this.anotherValue);
          }

          toString(): string {
            return `Test(someValue='${'$'}{this.someValue}', anotherValue='${'$'}{this.anotherValue}')`;
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
  fun `test generated class property with kebab or snake case names and jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonProperty} from '@outfoxx/jackson-js';


        export interface Test {

          someValue: string;

          anotherValue: string;

        }

        export class Test implements Test {

          @JsonProperty({value: 'some-value'})
          @JsonClassType({type: () => [String]})
          someValue: string;

          @JsonProperty({value: 'another_value'})
          @JsonClassType({type: () => [String]})
          anotherValue: string;

          constructor(someValue: string, anotherValue: string) {
            this.someValue = someValue;
            this.anotherValue = anotherValue;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.someValue ?? this.someValue, src.anotherValue ?? this.anotherValue);
          }

          toString(): string {
            return `Test(someValue='${'$'}{this.someValue}', anotherValue='${'$'}{this.anotherValue}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      }
    )
  }

}
