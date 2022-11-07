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
@DisplayName("[TypeScript] [RAML] Scalar Types Test")
class RamlScalarTypesTest {

  @Test
  fun `test type names generated for general scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface TestSpec {

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

        }

        export class Test implements TestSpec {

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

          constructor(init: TestSpec) {
            this.bool = init.bool;
            this.string = init.string;
            this.file = init.file;
            this.any = init.any;
            this.nil = init.nil;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(bool='${'$'}{this.bool}', string='${'$'}{this.string}', file='${'$'}{this.file}', any='${'$'}{this.any}', nil='${'$'}{this.nil}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for general scalar types with jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/misc.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';

        
        export interface TestSpec {

          bool: boolean;

          string: string;

          file: ArrayBuffer;

          any: any;

          nil: void;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Boolean]})
          bool: boolean;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [String]})
          string: string;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [ArrayBuffer]})
          file: ArrayBuffer;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Object]})
          any: any;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Object]})
          nil: void;

          constructor(init: TestSpec) {
            this.bool = init.bool;
            this.string = init.string;
            this.file = init.file;
            this.any = init.any;
            this.nil = init.nil;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(bool='${'$'}{this.bool}', string='${'$'}{this.string}', file='${'$'}{this.file}', any='${'$'}{this.any}', nil='${'$'}{this.nil}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for integer scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface TestSpec {

          int8: number;

          int16: number;

          int32: number;

          int64: number;

          int: number;

          long: number;

          none: number;

        }

        export class Test implements TestSpec {

          int8: number;

          int16: number;

          int32: number;

          int64: number;

          int: number;

          long: number;

          none: number;

          constructor(init: TestSpec) {
            this.int8 = init.int8;
            this.int16 = init.int16;
            this.int32 = init.int32;
            this.int64 = init.int64;
            this.int = init.int;
            this.long = init.long;
            this.none = init.none;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(int8='${'$'}{this.int8}', int16='${'$'}{this.int16}', int32='${'$'}{this.int32}', int64='${'$'}{this.int64}', int='${'$'}{this.int}', long='${'$'}{this.long}', none='${'$'}{this.none}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for integer scalar types with jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/ints.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';

        
        export interface TestSpec {

          int8: number;

          int16: number;

          int32: number;

          int64: number;

          int: number;

          long: number;

          none: number;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          int8: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          int16: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          int32: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          int64: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          int: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          long: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          none: number;

          constructor(init: TestSpec) {
            this.int8 = init.int8;
            this.int16 = init.int16;
            this.int32 = init.int32;
            this.int64 = init.int64;
            this.int = init.int;
            this.long = init.long;
            this.none = init.none;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(int8='${'$'}{this.int8}', int16='${'$'}{this.int16}', int32='${'$'}{this.int32}', int64='${'$'}{this.int64}', int='${'$'}{this.int}', long='${'$'}{this.long}', none='${'$'}{this.none}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for float scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        
        export interface TestSpec {

          float: number;

          double: number;

          none: number;

        }

        export class Test implements TestSpec {

          float: number;

          double: number;

          none: number;

          constructor(init: TestSpec) {
            this.float = init.float;
            this.double = init.double;
            this.none = init.none;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(float='${'$'}{this.float}', double='${'$'}{this.double}', none='${'$'}{this.none}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for float scalar types with jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/floats.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';

        
        export interface TestSpec {

          float: number;

          double: number;

          none: number;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          float: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          double: number;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [Number]})
          none: number;

          constructor(init: TestSpec) {
            this.float = init.float;
            this.double = init.double;
            this.none = init.none;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(float='${'$'}{this.float}', double='${'$'}{this.double}', none='${'$'}{this.none}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for date & time scalar types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {LocalDate, LocalDateTime, LocalTime, OffsetDateTime} from '@outfoxx/sunday';


        export interface TestSpec {

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

        }

        export class Test implements TestSpec {

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

          constructor(init: TestSpec) {
            this.dateOnly = init.dateOnly;
            this.timeOnly = init.timeOnly;
            this.dateTimeOnly = init.dateTimeOnly;
            this.dateTime = init.dateTime;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(dateOnly='${'$'}{this.dateOnly}', timeOnly='${'$'}{this.timeOnly}', dateTimeOnly='${'$'}{this.dateTimeOnly}', dateTime='${'$'}{this.dateTime}')`;
          }

        }
        
      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test type names generated for date & time scalar types with jackson decorators`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/types/scalar/dates.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val typeSpec = findTypeMod("Test@!test", generateTypes(testUri, typeRegistry, compiler))

    assertEquals(
      """
        import {JsonClassType, JsonCreator, JsonCreatorMode, JsonProperty} from '@outfoxx/jackson-js';
        import {LocalDate, LocalDateTime, LocalTime, OffsetDateTime} from '@outfoxx/sunday';


        export interface TestSpec {

          dateOnly: LocalDate;

          timeOnly: LocalTime;

          dateTimeOnly: LocalDateTime;

          dateTime: OffsetDateTime;

        }

        @JsonCreator({ mode: JsonCreatorMode.PROPERTIES_OBJECT })
        export class Test implements TestSpec {

          @JsonProperty({required: true})
          @JsonClassType({type: () => [LocalDate]})
          dateOnly: LocalDate;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [LocalTime]})
          timeOnly: LocalTime;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [LocalDateTime]})
          dateTimeOnly: LocalDateTime;

          @JsonProperty({required: true})
          @JsonClassType({type: () => [OffsetDateTime]})
          dateTime: OffsetDateTime;

          constructor(init: TestSpec) {
            this.dateOnly = init.dateOnly;
            this.timeOnly = init.timeOnly;
            this.dateTimeOnly = init.dateTimeOnly;
            this.dateTime = init.dateTime;
          }

          copy(changes: Partial<TestSpec>): Test {
            return new Test(Object.assign({}, this, changes));
          }

          toString(): string {
            return `Test(dateOnly='${'$'}{this.dateOnly}', timeOnly='${'$'}{this.timeOnly}', dateTimeOnly='${'$'}{this.dateTimeOnly}', dateTime='${'$'}{this.dateTime}')`;
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
