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
@DisplayName("[TypeScript] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in classes`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface Test {
      
          arrayOfStrings: Array<string>;
      
          arrayOfNullableStrings: Array<string | undefined>;
      
          nullableArrayOfStrings: Array<string> | undefined;
      
          nullableArrayOfNullableStrings: Array<string | undefined> | undefined;
      
          declaredArrayOfStrings: Array<string>;
      
          declaredArrayOfNullableStrings: Array<string | undefined>;
      
        }
      
        export class Test implements Test {

          arrayOfStrings: Array<string>;

          arrayOfNullableStrings: Array<string | undefined>;

          nullableArrayOfStrings: Array<string> | undefined;

          nullableArrayOfNullableStrings: Array<string | undefined> | undefined;

          declaredArrayOfStrings: Array<string>;

          declaredArrayOfNullableStrings: Array<string | undefined>;

          constructor(
              arrayOfStrings: Array<string>,
              arrayOfNullableStrings: Array<string | undefined>,
              nullableArrayOfStrings: Array<string> | undefined,
              nullableArrayOfNullableStrings: Array<string | undefined> | undefined,
              declaredArrayOfStrings: Array<string>,
              declaredArrayOfNullableStrings: Array<string | undefined>
          ) {
            this.arrayOfStrings = arrayOfStrings;
            this.arrayOfNullableStrings = arrayOfNullableStrings;
            this.nullableArrayOfStrings = nullableArrayOfStrings;
            this.nullableArrayOfNullableStrings = nullableArrayOfNullableStrings;
            this.declaredArrayOfStrings = declaredArrayOfStrings;
            this.declaredArrayOfNullableStrings = declaredArrayOfNullableStrings;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.arrayOfStrings ?? this.arrayOfStrings,
                src.arrayOfNullableStrings ?? this.arrayOfNullableStrings,
                src.nullableArrayOfStrings ?? this.nullableArrayOfStrings,
                src.nullableArrayOfNullableStrings ?? this.nullableArrayOfNullableStrings,
                src.declaredArrayOfStrings ?? this.declaredArrayOfStrings,
                src.declaredArrayOfNullableStrings ?? this.declaredArrayOfNullableStrings);
          }
      
          toString(): string {
            return `Test(arrayOfStrings='${'$'}{this.arrayOfStrings}', arrayOfNullableStrings='${'$'}{this.arrayOfNullableStrings}', nullableArrayOfStrings='${'$'}{this.nullableArrayOfStrings}', nullableArrayOfNullableStrings='${'$'}{this.nullableArrayOfNullableStrings}', declaredArrayOfStrings='${'$'}{this.declaredArrayOfStrings}', declaredArrayOfNullableStrings='${'$'}{this.declaredArrayOfNullableStrings}')`;
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
  fun `test generated collection class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface Test {
      
          implicit: Array<string>;
      
          unspecified: Array<string>;
      
          nonUnique: Array<string>;
      
          unique: Set<string>;
      
        }
      
        export class Test implements Test {

          implicit: Array<string>;

          unspecified: Array<string>;

          nonUnique: Array<string>;

          unique: Set<string>;

          constructor(implicit: Array<string>, unspecified: Array<string>, nonUnique: Array<string>,
              unique: Set<string>) {
            this.implicit = implicit;
            this.unspecified = unspecified;
            this.nonUnique = nonUnique;
            this.unique = unique;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.implicit ?? this.implicit, src.unspecified ?? this.unspecified,
                src.nonUnique ?? this.nonUnique, src.unique ?? this.unique);
          }
      
          toString(): string {
            return `Test(implicit='${'$'}{this.implicit}', unspecified='${'$'}{this.unspecified}', nonUnique='${'$'}{this.nonUnique}', unique='${'$'}{this.unique}')`;
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
  fun `test generated primitive class`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
      
        export interface Test {
      
          binary: ArrayBuffer;
      
          nullableBinary: ArrayBuffer | undefined;
      
        }
      
        export class Test implements Test {

          binary: ArrayBuffer;

          nullableBinary: ArrayBuffer | undefined;

          constructor(binary: ArrayBuffer, nullableBinary: ArrayBuffer | undefined) {
            this.binary = binary;
            this.nullableBinary = nullableBinary;
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.binary ?? this.binary, src.nullableBinary ?? this.nullableBinary);
          }
      
          toString(): string {
            return `Test(binary='${'$'}{this.binary}', nullableBinary='${'$'}{this.nullableBinary}')`;
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
