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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@TypeScriptTest
@DisplayName("[TypeScript/Sunday] [RAML] Request Header Params Test")
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI,
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

          fetchTest(obj: Test, strReq: string, int: number | undefined = undefined): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    obj,
                    'str-req': strReq,
                    int: int ?? 5
                  }
                },
                fetchTestReturnType
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

        }

        const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test constant header parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-constant.raml") testUri: URI,
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

          putTest(xCustom: string): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'PUT',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    Expect: '100-continue',
                    'x-custom': xCustom
                  }
                },
                putTestReturnType
            );
          }

        }

        const putTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test optional header parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI,
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

          fetchTest(
              obj: Test | undefined = undefined,
              str: string | undefined = undefined,
              int: number | null = null,
              def1: string | undefined = undefined,
              def2: number | null | undefined = undefined
          ): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    obj,
                    str,
                    int,
                    def1: def1 ?? 'test',
                    def2: def2 ?? 10
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
      },
    )
  }

  @Test
  fun `test generation of multiple header parameters with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI,
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

          fetchTest(category: API.FetchTestCategoryHeaderParam,
              type: API.FetchTestTypeHeaderParam): Observable<Record<string, unknown>> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    category,
                    type
                  }
                },
                fetchTestReturnType
            );
          }

        }

        export namespace API {

          export enum FetchTestCategoryHeaderParam {
            Politics = 'politics',
            Science = 'science'
          }

          export enum FetchTestTypeHeaderParam {
            All = 'all',
            Limited = 'limited'
          }

        }

        const fetchTestReturnType: AnyType = [Object, [String, Object]];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }
}
