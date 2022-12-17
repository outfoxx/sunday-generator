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
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
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
@DisplayName("[TypeScript/Sunday] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
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
        import {PatchableTest} from './patchable-test';
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          defaultContentTypes: Array<MediaType>;

          defaultAcceptTypes: Array<MediaType>;

          constructor(public requestFactory: RequestFactory,
              options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
            this.defaultContentTypes =
                options?.defaultContentTypes ?? [MediaType.JSON];
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

          putTest(body: Test): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'PUT',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: putTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                putTestReturnType
            );
          }

          postTest(body: Test): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'POST',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: postTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                postTestReturnType
            );
          }

          patchTest(body: Test): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'PATCH',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: patchTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                patchTestReturnType
            );
          }

          deleteTest(): Observable<void> {
            return this.requestFactory.result(
                {
                  method: 'DELETE',
                  pathTemplate: '/tests'
                }
            );
          }

          headTest(): Observable<void> {
            return this.requestFactory.result(
                {
                  method: 'HEAD',
                  pathTemplate: '/tests'
                }
            );
          }

          optionsTest(): Observable<void> {
            return this.requestFactory.result(
                {
                  method: 'OPTIONS',
                  pathTemplate: '/tests'
                }
            );
          }

          patchableTest(body: PatchableTest): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'PATCH',
                  pathTemplate: '/tests2',
                  body: body,
                  bodyType: patchableTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                patchableTestReturnType
            );
          }

        }

        const fetchTestReturnType: AnyType = [Test];
        const putTestBodyType: AnyType = [Test];
        const putTestReturnType: AnyType = [Test];
        const postTestBodyType: AnyType = [Test];
        const postTestReturnType: AnyType = [Test];
        const patchTestBodyType: AnyType = [Test];
        const patchTestReturnType: AnyType = [Test];
        const patchableTestBodyType: AnyType = [PatchableTest];
        const patchableTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation with result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)

    assertEquals(
      """
        import {PatchableTest} from './patchable-test';
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory, ResultResponse} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          defaultContentTypes: Array<MediaType>;

          defaultAcceptTypes: Array<MediaType>;

          constructor(public requestFactory: RequestFactory,
              options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
            this.defaultContentTypes =
                options?.defaultContentTypes ?? [MediaType.JSON];
            this.defaultAcceptTypes =
                options?.defaultAcceptTypes ?? [MediaType.JSON];
          }

          fetchTest(): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

          putTest(body: Test): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'PUT',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: putTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                putTestReturnType
            );
          }

          postTest(body: Test): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'POST',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: postTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                postTestReturnType
            );
          }

          patchTest(body: Test): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'PATCH',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: patchTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                patchTestReturnType
            );
          }

          deleteTest(): Observable<ResultResponse<void>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'DELETE',
                  pathTemplate: '/tests'
                }
            );
          }

          headTest(): Observable<ResultResponse<void>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'HEAD',
                  pathTemplate: '/tests'
                }
            );
          }

          optionsTest(): Observable<ResultResponse<void>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'OPTIONS',
                  pathTemplate: '/tests'
                }
            );
          }

          patchableTest(body: PatchableTest): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'PATCH',
                  pathTemplate: '/tests2',
                  body: body,
                  bodyType: patchableTestBodyType,
                  contentTypes: this.defaultContentTypes,
                  acceptTypes: this.defaultAcceptTypes
                },
                patchableTestReturnType
            );
          }

        }

        const fetchTestReturnType: AnyType = [Test];
        const putTestBodyType: AnyType = [Test];
        const putTestReturnType: AnyType = [Test];
        const postTestBodyType: AnyType = [Test];
        const postTestReturnType: AnyType = [Test];
        const patchTestBodyType: AnyType = [Test];
        const patchTestReturnType: AnyType = [Test];
        const patchableTestBodyType: AnyType = [PatchableTest];
        const patchableTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation with nullify`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
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
        import {AnotherNotFoundProblem} from './another-not-found-problem';
        import {Test} from './test';
        import {TestNotFoundProblem} from './test-not-found-problem';
        import {AnyType, MediaType, RequestFactory, nullifyResponse} from '@outfoxx/sunday';
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
            requestFactory.registerProblem('http://example.com/test_not_found', TestNotFoundProblem);
            requestFactory.registerProblem('http://example.com/another_not_found', AnotherNotFoundProblem);
          }

          fetchTest1OrNull(limit: number): Observable<Test | null> {
            return this.fetchTest1(limit)
              .pipe(nullifyResponse(
                [404, 405],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest1(limit: number): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/test1',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest1ReturnType
            );
          }

          fetchTest2OrNull(limit: number): Observable<Test | null> {
            return this.fetchTest2(limit)
              .pipe(nullifyResponse(
                [404],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest2(limit: number): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/test2',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest2ReturnType
            );
          }

          fetchTest3OrNull(limit: number): Observable<Test | null> {
            return this.fetchTest3(limit)
              .pipe(nullifyResponse(
                [],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest3(limit: number): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/test3',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest3ReturnType
            );
          }

          fetchTest4OrNull(limit: number): Observable<Test | null> {
            return this.fetchTest4(limit)
              .pipe(nullifyResponse(
                [404, 405],
                []
              ));
          }

          fetchTest4(limit: number): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/test4',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest4ReturnType
            );
          }

          fetchTest5OrNull(limit: number): Observable<Test | null> {
            return this.fetchTest5(limit)
              .pipe(nullifyResponse(
                [404],
                []
              ));
          }

          fetchTest5(limit: number): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/test5',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest5ReturnType
            );
          }

        }

        const fetchTest1ReturnType: AnyType = [Test];
        const fetchTest2ReturnType: AnyType = [Test];
        const fetchTest3ReturnType: AnyType = [Test];
        const fetchTest4ReturnType: AnyType = [Test];
        const fetchTest5ReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test request method generation with nullify and result response`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-methods-nullify.raml") testUri: URI,
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document, shapeIndex ->
        TypeScriptSundayGenerator(
          document,
          shapeIndex,
          typeRegistry,
          TypeScriptSundayGenerator.Options(
            true,
            "http://example.com/",
            listOf("application/json"),
            "API",
          ),
        )
      }

    val typeSpec = findTypeMod("API@!api", builtTypes)

    assertEquals(
      """
        import {AnotherNotFoundProblem} from './another-not-found-problem';
        import {Test} from './test';
        import {TestNotFoundProblem} from './test-not-found-problem';
        import {AnyType, MediaType, RequestFactory, ResultResponse, nullifyResponse} from '@outfoxx/sunday';
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
            requestFactory.registerProblem('http://example.com/test_not_found', TestNotFoundProblem);
            requestFactory.registerProblem('http://example.com/another_not_found', AnotherNotFoundProblem);
          }

          fetchTest1OrNull(limit: number): Observable<ResultResponse<Test> | null> {
            return this.fetchTest1(limit)
              .pipe(nullifyResponse(
                [404, 405],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest1(limit: number): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/test1',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest1ReturnType
            );
          }

          fetchTest2OrNull(limit: number): Observable<ResultResponse<Test> | null> {
            return this.fetchTest2(limit)
              .pipe(nullifyResponse(
                [404],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest2(limit: number): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/test2',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest2ReturnType
            );
          }

          fetchTest3OrNull(limit: number): Observable<ResultResponse<Test> | null> {
            return this.fetchTest3(limit)
              .pipe(nullifyResponse(
                [],
                [TestNotFoundProblem, AnotherNotFoundProblem]
              ));
          }

          fetchTest3(limit: number): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/test3',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest3ReturnType
            );
          }

          fetchTest4OrNull(limit: number): Observable<ResultResponse<Test> | null> {
            return this.fetchTest4(limit)
              .pipe(nullifyResponse(
                [404, 405],
                []
              ));
          }

          fetchTest4(limit: number): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/test4',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest4ReturnType
            );
          }

          fetchTest5OrNull(limit: number): Observable<ResultResponse<Test> | null> {
            return this.fetchTest5(limit)
              .pipe(nullifyResponse(
                [404],
                []
              ));
          }

          fetchTest5(limit: number): Observable<ResultResponse<Test>> {
            return this.requestFactory.resultResponse(
                {
                  method: 'GET',
                  pathTemplate: '/test5',
                  queryParameters: {
                    limit
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTest5ReturnType
            );
          }

        }

        const fetchTest1ReturnType: AnyType = [Test];
        const fetchTest2ReturnType: AnyType = [Test];
        const fetchTest3ReturnType: AnyType = [Test];
        const fetchTest4ReturnType: AnyType = [Test];
        const fetchTest5ReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }
}
