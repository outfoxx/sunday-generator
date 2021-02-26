package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[TypeScript/Sunday] [RAML] Response Problems Test")
class ResponseProblemsTest {

  @Test
  fun `test problem types references in API`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)

    assertEquals(
      """
        import {InvalidIdProblem} from './invalid-id-problem';
        import {Test} from './test';
        import {TestNotFoundProblem} from './test-not-found-problem';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          static defaultContentTypes: Array<MediaType> = [];

          static defaultAcceptTypes: Array<MediaType> = [MediaType.JSON];

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: API.defaultAcceptTypes,
                  problemTypes: {
                    'invalid_id': InvalidIdProblem,
                    'test_not_found': TestNotFoundProblem
                  }
                },
                fetchTestReturnType
            );
          }

        }

        const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )

  }

  @Test
  fun `test problem type generation`(
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

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
        FileSpec.get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using base uri`(
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://api.example.com/api/invalid_id';

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
        FileSpec.get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://errors.example.com/docs/invalid_id';

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
        FileSpec.get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
        import {Problem} from '@outfoxx/sunday';


        export class InvalidIdProblem extends Problem {

          static TYPE: string = 'http://example.com/api/errors/invalid_id';

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
        FileSpec.get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry) { document ->
        TypeScriptSundayGenerator(
          document,
          typeRegistry,
          "http://example.com/",
          listOf("application/json")
        )
      }

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

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
        FileSpec.get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      }
    )
  }

}
