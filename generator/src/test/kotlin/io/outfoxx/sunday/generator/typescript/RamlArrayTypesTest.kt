package io.outfoxx.sunday.generator.typescript

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
@DisplayName("[TypeScript] [RAML] Array Types Test")
class RamlArrayTypesTest {

  @Test
  fun `test generated nullability of array types and elements in classes`(
    @ResourceUri("raml/type-gen/types/arrays-nullability.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Test {
      
          arrayOfStrings: Array<string>;
      
          arrayOfNullableStrings: Array<string | null>;
      
          nullableArrayOfStrings: Array<string> | null;
      
          nullableArrayOfNullableStrings: Array<string | null> | null;
      
          declaredArrayOfStrings: Array<string>;
      
          declaredArrayOfNullableStrings: Array<string | null>;
      
        }
      
        export class Test implements Test {

          arrayOfStrings: Array<string>;

          arrayOfNullableStrings: Array<string | null>;

          nullableArrayOfStrings: Array<string> | null;

          nullableArrayOfNullableStrings: Array<string | null> | null;

          declaredArrayOfStrings: Array<string>;

          declaredArrayOfNullableStrings: Array<string | null>;

          constructor(
              arrayOfStrings: Array<string>,
              arrayOfNullableStrings: Array<string | null>,
              nullableArrayOfStrings: Array<string> | null,
              nullableArrayOfNullableStrings: Array<string | null> | null,
              declaredArrayOfStrings: Array<string>,
              declaredArrayOfNullableStrings: Array<string | null>
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
    @ResourceUri("raml/type-gen/types/arrays-collection.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

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
    @ResourceUri("raml/type-gen/types/arrays-primitive.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeModSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
      
        export interface Test {
      
          binary: ArrayBuffer;
      
          nullableBinary: ArrayBuffer | null;
      
        }
      
        export class Test implements Test {

          binary: ArrayBuffer;

          nullableBinary: ArrayBuffer | null;

          constructor(binary: ArrayBuffer, nullableBinary: ArrayBuffer | null) {
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
