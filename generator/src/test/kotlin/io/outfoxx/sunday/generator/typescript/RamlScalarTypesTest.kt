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

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

          constructor(
              bool: boolean,
              string: string,
              file: ArrayBuffer,
              any: any,
              nil: void
          ) {
            this.bool = bool;
            this.string = string;
            this.file = file;
            this.any = any;
            this.nil = nil;
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
  fun `test type names generated for general scalar types with jackson decorators`(
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {JsonClassType} from '@outfoxx/jackson-js';

        
        export interface Test {

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

        }

        export class Test implements Test {

          @JsonClassType({type: () => [Boolean]})
          bool: boolean;

          @JsonClassType({type: () => [String]})
          string: string;

          @JsonClassType({type: () => [ArrayBuffer]})
          file: ArrayBuffer;

          @JsonClassType({type: () => [Object]})
          any: any;

          @JsonClassType({type: () => [Object]})
          nil: void;

          constructor(
              bool: boolean,
              string: string,
              file: ArrayBuffer,
              any: any,
              nil: void
          ) {
            this.bool = bool;
            this.string = string;
            this.file = file;
            this.any = any;
            this.nil = nil;
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

          int8: number;

          int16: number;

          int32: number;

          int64: number;

          int: number;

          long: number;

          none: number;

          constructor(
              int8: number,
              int16: number,
              int32: number,
              int64: number,
              int: number,
              long: number,
              none: number
          ) {
            this.int8 = int8;
            this.int16 = int16;
            this.int32 = int32;
            this.int64 = int64;
            this.int = int;
            this.long = long;
            this.none = none;
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
  fun `test type names generated for integer scalar types with jackson decorators`(
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {JsonClassType} from '@outfoxx/jackson-js';

        
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

          @JsonClassType({type: () => [Number]})
          int8: number;

          @JsonClassType({type: () => [Number]})
          int16: number;

          @JsonClassType({type: () => [Number]})
          int32: number;

          @JsonClassType({type: () => [Number]})
          int64: number;

          @JsonClassType({type: () => [Number]})
          int: number;

          @JsonClassType({type: () => [Number]})
          long: number;

          @JsonClassType({type: () => [Number]})
          none: number;

          constructor(
              int8: number,
              int16: number,
              int32: number,
              int64: number,
              int: number,
              long: number,
              none: number
          ) {
            this.int8 = int8;
            this.int16 = int16;
            this.int32 = int32;
            this.int64 = int64;
            this.int = int;
            this.long = long;
            this.none = none;
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

          float: number;

          double: number;

          none: number;

          constructor(float: number, double: number, none: number) {
            this.float = float;
            this.double = double;
            this.none = none;
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
  fun `test type names generated for float scalar types with jackson decorators`(
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {JsonClassType} from '@outfoxx/jackson-js';

        
        export interface Test {

          float: number;

          double: number;

          none: number;

        }

        export class Test implements Test {

          @JsonClassType({type: () => [Number]})
          float: number;

          @JsonClassType({type: () => [Number]})
          double: number;

          @JsonClassType({type: () => [Number]})
          none: number;

          constructor(float: number, double: number, none: number) {
            this.float = float;
            this.double = double;
            this.none = none;
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

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

          constructor(dateOnly: LocalDate, timeOnly: LocalTime, dateTimeOnly: LocalDateTime,
              dateTime: OffsetDateTime) {
            this.dateOnly = dateOnly;
            this.timeOnly = timeOnly;
            this.dateTimeOnly = dateTimeOnly;
            this.dateTime = dateTime;
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

  @Test
  fun `test type names generated for date & time scalar types with jackson decorators`(
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry))

    assertEquals(
      """
        import {JsonClassType} from '@outfoxx/jackson-js';
        import {LocalDate, LocalDateTime, LocalTime, OffsetDateTime} from '@outfoxx/sunday';


        export interface Test {

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

        }

        export class Test implements Test {

          @JsonClassType({type: () => [LocalDate]})
          dateOnly: LocalDate;

          @JsonClassType({type: () => [LocalTime]})
          timeOnly: LocalTime;

          @JsonClassType({type: () => [LocalDateTime]})
          dateTimeOnly: LocalDateTime;

          @JsonClassType({type: () => [OffsetDateTime]})
          dateTime: OffsetDateTime;

          constructor(dateOnly: LocalDate, timeOnly: LocalTime, dateTimeOnly: LocalDateTime,
              dateTime: OffsetDateTime) {
            this.dateOnly = dateOnly;
            this.timeOnly = timeOnly;
            this.dateTimeOnly = dateTimeOnly;
            this.dateTime = dateTime;
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
