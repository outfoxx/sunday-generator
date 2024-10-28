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
import io.outfoxx.sunday.generator.typescript.sunday.typeScriptSundayTestOptions
import io.outfoxx.sunday.generator.typescript.tools.*
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Object Types Test")
class RamlObjectTypesTest {

  @Test
  fun `test generated freeform object`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-freeform.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          map: Record<string, unknown>;

        }

        export class Test implements TestSpec {

          map: Record<string, unknown>;

          constructor(init: TestSpec) {
            this.map = init.map;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(map='${'$'}{this.map}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated nullability of property types in classes`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          fromNilUnion: string | null;

          notRequired?: string;

        }

        export class Test implements TestSpec {

          fromNilUnion: string | null;

          notRequired: string | undefined;

          constructor(init: TestSpec) {
            this.fromNilUnion = init.fromNilUnion;
            this.notRequired = init.notRequired;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(fromNilUnion='${'$'}{this.fromNilUnion}', notRequired='${'$'}{this.notRequired}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test naming of types defined inline in property`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    findNestedType(typeModSpec, "Test", "Value") ?: fail("Nested type 'Value' not defined")
  }

  @Test
  fun `test naming of types defined inline in resource`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-resource-inline-type.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          typeScriptSundayTestOptions,
        )
      }
    val typeModSpec = findTypeMod("API@!api", builtTypes)

    findNestedType(typeModSpec, "API", "FetchTestResponsePayload")
      ?: fail("Nested type 'FetchTestResponsePayload' not defined")
  }

  @Test
  fun `test generated classes for object hierarchy`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-inherits.raml") testUri: URI,
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

        export interface TestSpec {

          value: string;

        }

        export class Test implements TestSpec {

          value: string;

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
        FileSpec.get(testSpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
        import {Test, TestSpec} from './test';


        export interface Test2Spec extends TestSpec {

          value2: string;

        }

        export class Test2 extends Test implements Test2Spec {

          value2: string;

          constructor(init: Test2Spec) {
            super(init);
            this.value2 = init.value2;
          }

          copy(changes: Partial<Test2Spec>): Test2 {
            return new Test2(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test2(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(test2Spec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
        import {Test2, Test2Spec} from './test2';


        export interface EmptySpec extends Test2Spec {
        }

        export class Empty extends Test2 implements EmptySpec {

          constructor(init: EmptySpec) {
            super(init);
          }

          copy(changes: Partial<EmptySpec>): Empty {
            return new Empty(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Empty(value='${'$'}{this.value}', value2='${'$'}{this.value2}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(emptySpec)
          .writeTo(this)
      },
    )

    assertEquals(
      """
        import {Empty, EmptySpec} from './empty';


        export interface Test3Spec extends EmptySpec {

          value3: string;

        }

        export class Test3 extends Empty implements Test3Spec {

          value3: string;

          constructor(init: Test3Spec) {
            super(init);
            this.value3 = init.value3;
          }

          copy(changes: Partial<Test3Spec>): Test3 {
            return new Test3(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test3(value='${'$'}{this.value}', value2='${'$'}{this.value2}', value3='${'$'}{this.value3}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(test3Spec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          someValue: string;

          anotherValue: string;

        }

        export class Test implements TestSpec {

          someValue: string;

          anotherValue: string;

          constructor(init: TestSpec) {
            this.someValue = init.someValue;
            this.anotherValue = init.anotherValue;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(someValue='${'$'}{this.someValue}', anotherValue='${'$'}{this.anotherValue}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class property with kebab or snake case names and jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-property-renamed.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';


        export interface TestSpec {

          someValue: string;

          anotherValue: string;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({value: 'some-value', required: true})
          @JsonClassType({type: () => [String]})
          someValue: string;

          @JsonProperty({value: 'another_value', required: true})
          @JsonClassType({type: () => [String]})
          anotherValue: string;

          constructor(init: TestSpec) {
            this.someValue = init.someValue;
            this.anotherValue = init.anotherValue;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(someValue='${'$'}{this.someValue}', anotherValue='${'$'}{this.anotherValue}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursive property`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';


        export interface TestSpec {

          parent: Test | null;

          other?: Test;

          children: Array<Test>;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Test]})
          parent: Test | null;

          @JsonProperty()
          @JsonClassType({type: () => [Test]})
          other: Test | undefined;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Array, [Test]]})
          children: Array<Test>;

          constructor(init: TestSpec) {
            this.parent = init.parent;
            this.other = init.other;
            this.children = init.children;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(parent='${'$'}{this.parent}', other='${'$'}{this.other}', children='${'$'}{this.children}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generated class with recursion down to a complex leaf`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/obj-recursive-complex-leaf.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpecs = generateTypes(testUri, typeRegistry, compiler, includeIndex = true)
    val typeSpec = findTypeMod("Node@!node", typeSpecs)

    assertNotNull(typeSpec)
    assertNotNull(findTypeMod("NodeType@!node-type", typeSpecs))
    assertNotNull(findTypeMod("NodeValue@!node-value", typeSpecs))
    assertNotNull(findTypeMod("NodeList@!node-list", typeSpecs))
    assertNotNull(findTypeMod("NodeMap@!node-map", typeSpecs))

    assertEquals(
      """
        import {NodeList, NodeMap, NodeValue} from './index';
        import {JsonSubTypes, JsonTypeInfo, JsonTypeInfoAs, JsonTypeInfoId} from '@outfoxx/jackson-js';


        export interface NodeSpec {
        }

        @JsonTypeInfo({
          use: JsonTypeInfoId.NAME,
          include: JsonTypeInfoAs.PROPERTY,
          property: 'type',
        })
        @JsonSubTypes({
          types: [
            {class: () => NodeList, name: 'list' /* NodeType.List */},
            {class: () => NodeValue, name: 'value' /* NodeType.Value */},
            {class: () => NodeMap, name: 'map' /* NodeType.Map */}
          ]
        })
        export abstract class Node implements NodeSpec {

          toString(): string {
            return `Node()`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }
}
