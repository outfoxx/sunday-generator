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
@DisplayName("[TypeScript] [RAML] Problem Types Test")
class ProblemTypesTest {

  @Test
  fun `generates problem types`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdTypeModSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)
    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://example.com/invalid_id';

          constructor(offendingId: string, instance: string | null = null) {
            super(
              InvalidIdProblem.TYPE,
              'Invalid Id',
              400,
              'The id contains one or more invalid characters.',
              instance
            );
          }

        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(invalidIdTypeModSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )

    val accountNotFoundTypeModSpec = findTypeMod("AccountNotFoundProblem@!account-not-found-problem", builtTypes)
    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class AccountNotFoundProblem extends Problem {

          static TYPE: string = 'http://example.com/account_not_found';

          constructor(instance: string | null = null) {
            super(
              AccountNotFoundProblem.TYPE,
              'Account Not Found',
              404,
              'The requested account does not exist or you do not have permission to access it.',
              instance
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(accountNotFoundTypeModSpec, "account-not-found-problem")
          .writeTo(this)
      }
    )
  }

  @Test
  fun `generates problem types with jackson annotations`(
    @ResourceUri("raml/type-gen/annotations/problem-types.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf(JacksonDecorators))

    val builtTypes = generateTypes(testUri, typeRegistry)

    val invalidIdTypeModSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)
    assertEquals(
      """
        import {JsonProperty, JsonTypeName} from '@outfoxx/jackson-js';
        import {Problem} from '@outfoxx/sunday';


        @JsonTypeName({value: InvalidIdProblem.TYPE})
        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://example.com/invalid_id';

          constructor(
              @JsonProperty({value: 'offending_id'}) offendingId: string,
              instance: string | null = null
          ) {
            super(
              InvalidIdProblem.TYPE,
              'Invalid Id',
              400,
              'The id contains one or more invalid characters.',
              instance
            );
          }

        }
      
      """.trimIndent(),
      buildString {
        FileSpec.get(invalidIdTypeModSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )

    val accountNotFoundTypeModSpec = findTypeMod("AccountNotFoundProblem@!account-not-found-problem", builtTypes)
    assertEquals(
      """
        import {JsonTypeName} from '@outfoxx/jackson-js';
        import {Problem} from '@outfoxx/sunday';


        @JsonTypeName({value: AccountNotFoundProblem.TYPE})
        export class AccountNotFoundProblem extends Problem {

          static TYPE: string = 'http://example.com/account_not_found';

          constructor(instance: string | null = null) {
            super(
              AccountNotFoundProblem.TYPE,
              'Account Not Found',
              404,
              'The requested account does not exist or you do not have permission to access it.',
              instance
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(accountNotFoundTypeModSpec, "account-not-found-problem")
          .writeTo(this)
      }
    )
  }

}
