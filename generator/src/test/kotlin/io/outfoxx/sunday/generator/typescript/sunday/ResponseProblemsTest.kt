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

package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTest
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [RAML] Response Problems Test")
class ResponseProblemsTest {

  @Test
  fun `test API problem registration`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
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

    val typeSpec = findTypeMod("API@!api", builtTypes)

    assertEquals(
      """
      import {InvalidIdProblem} from './invalid-id-problem';
      import {Test} from './test';
      import {TestNotFoundProblem} from './test-not-found-problem';
      import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
      import {Observable} from 'rxjs';


      export class API {

        defaultContentTypes: Array<MediaType>;

        defaultAcceptTypes: Array<MediaType>;

        constructor(public requestFactory: RequestFactory,
            options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
          this.defaultContentTypes =
              options?.defaultContentTypes ?? [];
          this.defaultAcceptTypes =
              options?.defaultAcceptTypes ?? [MediaType.JSON];
          requestFactory.registerProblem('http://example.com/invalid_id', InvalidIdProblem);
          requestFactory.registerProblem('http://example.com/test_not_found', TestNotFoundProblem);
        }

        fetchTest(): Observable<Test> {
          return this.requestFactory.result(
              {
                method: 'GET',
                pathTemplate: '/tests',
                acceptTypes: this.defaultAcceptTypes
              },
              fetchTestReturnType
          );
        }

      }

      const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test API problem registration when no problems`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-no-problems.raml") testUri: URI,
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

    val typeSpec = findTypeMod("API@!api", builtTypes)

    assertEquals(
      """
      import {Test} from './test';
      import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
      import {Observable} from 'rxjs';


      export class API {

        defaultContentTypes: Array<MediaType>;

        defaultAcceptTypes: Array<MediaType>;

        constructor(public requestFactory: RequestFactory,
            options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
          this.defaultContentTypes =
              options?.defaultContentTypes ?? [];
          this.defaultAcceptTypes =
              options?.defaultAcceptTypes ?? [MediaType.JSON];
        }

        fetchTest(): Observable<Test> {
          return this.requestFactory.result(
              {
                method: 'GET',
                pathTemplate: '/tests',
                acceptTypes: this.defaultAcceptTypes
              },
              fetchTestReturnType
          );
        }

      }

      const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec
          .get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

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
        FileSpec
          .get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using base uri`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
      import {Problem} from '@outfoxx/sunday';


      export class InvalidIdProblem extends Problem {

        static TYPE: string = 'http://api.example.com/api/invalid_id';

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
        FileSpec
          .get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using absolute problem base uri`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems-abs-problem-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
      import {Problem} from '@outfoxx/sunday';


      export class InvalidIdProblem extends Problem {

        static TYPE: string = 'http://errors.example.com/docs/invalid_id';

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
        FileSpec
          .get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation using relative problem base uri`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems-rel-problem-base-uri.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

    assertEquals(
      """
      import {Problem} from '@outfoxx/sunday';


      export class InvalidIdProblem extends Problem {

        static TYPE: string = 'http://example.com/api/errors/invalid_id';

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
        FileSpec
          .get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test problem type generation locates problems in libraries`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-problems-lib.raml") testUri: URI,
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

    assertFalse(builtTypes.containsKey(TypeName.standard("CreateFailedProblem@!create-failed-problem")))
    assertTrue(builtTypes.containsKey(TypeName.standard("TestNotFoundProblem@!test-not-found-problem")))

    val typeSpec = findTypeMod("InvalidIdProblem@!invalid-id-problem", builtTypes)

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
        FileSpec
          .get(typeSpec, "invalid-id-problem")
          .writeTo(this)
      },
    )
  }
}
