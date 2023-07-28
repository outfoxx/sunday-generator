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
@DisplayName("[TypeScript] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in classes`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          arrayOfStrings: Array<string>;

          arrayOfNullableStrings: Array<string | null>;

          nullableArrayOfStrings: Array<string> | null;

          nullableArrayOfNullableStrings: Array<string | null> | null;

          declaredArrayOfStrings: Array<string>;

          declaredArrayOfNullableStrings: Array<string | null>;

        }

        export class Test implements TestSpec {

          arrayOfStrings: Array<string>;

          arrayOfNullableStrings: Array<string | null>;

          nullableArrayOfStrings: Array<string> | null;

          nullableArrayOfNullableStrings: Array<string | null> | null;

          declaredArrayOfStrings: Array<string>;

          declaredArrayOfNullableStrings: Array<string | null>;

          constructor(init: TestSpec) {
            this.arrayOfStrings = init.arrayOfStrings;
            this.arrayOfNullableStrings = init.arrayOfNullableStrings;
            this.nullableArrayOfStrings = init.nullableArrayOfStrings;
            this.nullableArrayOfNullableStrings = init.nullableArrayOfNullableStrings;
            this.declaredArrayOfStrings = init.declaredArrayOfStrings;
            this.declaredArrayOfNullableStrings = init.declaredArrayOfNullableStrings;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(arrayOfStrings='${'$'}{this.arrayOfStrings}', arrayOfNullableStrings='${'$'}{this.arrayOfNullableStrings}', nullableArrayOfStrings='${'$'}{this.nullableArrayOfStrings}', nullableArrayOfNullableStrings='${'$'}{this.nullableArrayOfNullableStrings}', declaredArrayOfStrings='${'$'}{this.declaredArrayOfStrings}', declaredArrayOfNullableStrings='${'$'}{this.declaredArrayOfNullableStrings}')`;
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
  fun `test generated collection class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          implicit: Array<string>;

          unspecified: Array<string>;

          nonUnique: Array<string>;

          unique: Set<string>;

        }

        export class Test implements TestSpec {

          implicit: Array<string>;

          unspecified: Array<string>;

          nonUnique: Array<string>;

          unique: Set<string>;

          constructor(init: TestSpec) {
            this.implicit = init.implicit;
            this.unspecified = init.unspecified;
            this.nonUnique = init.nonUnique;
            this.unique = init.unique;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(implicit='${'$'}{this.implicit}', unspecified='${'$'}{this.unspecified}', nonUnique='${'$'}{this.nonUnique}', unique='${'$'}{this.unique}')`;
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
  fun `test generated primitive class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """

        export interface TestSpec {

          binary: ArrayBuffer;

          nullableBinary: ArrayBuffer | null;

        }

        export class Test implements TestSpec {

          binary: ArrayBuffer;

          nullableBinary: ArrayBuffer | null;

          constructor(init: TestSpec) {
            this.binary = init.binary;
            this.nullableBinary = init.nullableBinary;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(binary='${'$'}{this.binary}', nullableBinary='${'$'}{this.nullableBinary}')`;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeModSpec)
          .writeTo(this)
      },
    )
  }
}
