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
@DisplayName("[TypeScript] [RAML] Scalar Types Test")
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Test {

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

        }

        export class Test implements Test {

          constructor(
              public bool: boolean,
              public string: string,
              public file: ArrayBuffer,
              public any: any,
              public nil: void
          ) {
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.bool ?? this.bool, src.string ?? this.string, src.file ?? this.file,
                src.any ?? this.any, src.nil ?? this.nil);
          }

          toString(): string {
            return `Test(bool='${'$'}{this.bool}', string='${'$'}{this.string}', file='${'$'}{this.file}', any='${'$'}{this.any}', nil='${'$'}{this.nil}')`;
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
  fun `test type names generated for integer scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Test {

          int8: number;

          int16: number;

          int32: number;

          int64: number;

          int: number;

          long: number;

          none: number;

        }

        export class Test implements Test {

          constructor(
              public int8: number,
              public int16: number,
              public int32: number,
              public int64: number,
              public int: number,
              public long: number,
              public none: number
          ) {
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.int8 ?? this.int8, src.int16 ?? this.int16, src.int32 ?? this.int32,
                src.int64 ?? this.int64, src.int ?? this.int, src.long ?? this.long, src.none ?? this.none);
          }

          toString(): string {
            return `Test(int8='${'$'}{this.int8}', int16='${'$'}{this.int16}', int32='${'$'}{this.int32}', int64='${'$'}{this.int64}', int='${'$'}{this.int}', long='${'$'}{this.long}', none='${'$'}{this.none}')`;
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
  fun `test type names generated for float scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        
        export interface Test {

          float: number;

          double: number;

          none: number;

        }

        export class Test implements Test {

          constructor(public float: number, public double: number, public none: number) {
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.float ?? this.float, src.double ?? this.double, src.none ?? this.none);
          }

          toString(): string {
            return `Test(float='${'$'}{this.float}', double='${'$'}{this.double}', none='${'$'}{this.none}')`;
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
  fun `test type names generated for date & time scalar types`(
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {LocalDate, LocalDateTime, LocalTime, OffsetDateTime} from '@outfoxx/sunday';


        export interface Test {

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

        }

        export class Test implements Test {

          constructor(public dateOnly: LocalDate, public timeOnly: LocalTime,
              public dateTimeOnly: LocalDateTime, public dateTime: OffsetDateTime) {
          }

          copy(src: Partial<Test>): Test {
            return new Test(src.dateOnly ?? this.dateOnly, src.timeOnly ?? this.timeOnly,
                src.dateTimeOnly ?? this.dateTimeOnly, src.dateTime ?? this.dateTime);
          }

          toString(): string {
            return `Test(dateOnly='${'$'}{this.dateOnly}', timeOnly='${'$'}{this.timeOnly}', dateTimeOnly='${'$'}{this.dateTimeOnly}', dateTime='${'$'}{this.dateTime}')`;
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
