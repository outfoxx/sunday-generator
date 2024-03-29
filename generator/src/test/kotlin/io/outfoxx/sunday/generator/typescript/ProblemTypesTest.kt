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
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val invalidIdTypeModSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)
    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://example.com/invalid_id';

          offendingId: string;

          constructor(offendingId: string, instance: string | URL | undefined = undefined) {
            super({
              type: InvalidIdProblem.TYPE,
              title: 'Invalid Id',
              status: 400,
              detail: 'The id contains one or more invalid characters.',
              instance
            });
            this.offendingId = offendingId;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(invalidIdTypeModSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )

    val accountNotFoundTypeModSpec = findTypeMod("AccountNotFoundProblem@!account-not-found-problem", builtTypes)
    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class AccountNotFoundProblem extends Problem {

          static TYPE: string = 'http://example.com/account_not_found';

          constructor(instance: string | URL | undefined = undefined) {
            super({
              type: AccountNotFoundProblem.TYPE,
              title: 'Account Not Found',
              status: 404,
              detail: 'The requested account does not exist or you do not have permission to access it.',
              instance
            });
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(accountNotFoundTypeModSpec, "account-not-found-problem")
          .writeTo(this)
      },
    )

    val testResolverModSpec = findTypeMod("TestResolverProblem@!test-resolver-problem", builtTypes)
    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class TestResolverProblem extends Problem {

          static TYPE: string = 'http://example.com/test_resolver';

          optionalString: string | null;

          arrayOfStrings: Array<string>;

          optionalArrayOfStrings: Array<string> | null;

          constructor(optionalString: string | null, arrayOfStrings: Array<string>,
              optionalArrayOfStrings: Array<string> | null,
              instance: string | URL | undefined = undefined) {
            super({
              type: TestResolverProblem.TYPE,
              title: 'Test Resolve Type Reference',
              status: 500,
              detail: 'Tests the resolveTypeReference function implementation.',
              instance
            });
            this.optionalString = optionalString;
            this.arrayOfStrings = arrayOfStrings;
            this.optionalArrayOfStrings = optionalArrayOfStrings;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(testResolverModSpec, "test-resolver-problem")
          .writeTo(this)
      },
    )
  }

  @Test
  fun `generates problem types with jackson annotations`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val builtTypes = generateTypes(testUri, typeRegistry, compiler)

    val invalidIdTypeModSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)
    assertEquals(
      """
        import {JsonClassType, JsonProperty, JsonTypeName} from '@outfoxx/jackson-js';
        import {Problem} from '@outfoxx/sunday';


        @JsonTypeName({value: InvalidIdProblem.TYPE})
        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://example.com/invalid_id';

          @JsonProperty({value: 'offending_id'})
          @JsonClassType({type: () => [String]})
          offendingId: string;

          constructor(offendingId: string, instance: string | URL | undefined = undefined) {
            super({
              type: InvalidIdProblem.TYPE,
              title: 'Invalid Id',
              status: 400,
              detail: 'The id contains one or more invalid characters.',
              instance
            });
            this.offendingId = offendingId;
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(invalidIdTypeModSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )

    val accountNotFoundTypeModSpec = findTypeMod("AccountNotFoundProblem@!account-not-found-problem", builtTypes)
    assertEquals(
      """
        import {JsonTypeName} from '@outfoxx/jackson-js';
        import {Problem} from '@outfoxx/sunday';


        @JsonTypeName({value: AccountNotFoundProblem.TYPE})
        export class AccountNotFoundProblem extends Problem {

          static TYPE: string = 'http://example.com/account_not_found';

          constructor(instance: string | URL | undefined = undefined) {
            super({
              type: AccountNotFoundProblem.TYPE,
              title: 'Account Not Found',
              status: 404,
              detail: 'The requested account does not exist or you do not have permission to access it.',
              instance
            });
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(accountNotFoundTypeModSpec, "account-not-found-problem")
          .writeTo(this)
      },
    )
  }
}
