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
@DisplayName("[TypeScript/Sunday] [RAML] Request Mixed Params Test")
class RequestMixedParamsTest {

  @Test
  fun `test generation of multiple parameters with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI,
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

          fetchTest(select: API.FetchTestSelectUriParam, page: API.FetchTestPageQueryParam,
              xType: API.FetchTestXTypeHeaderParam): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{select}',
                  pathParameters: {
                    select
                  },
                  queryParameters: {
                    page
                  },
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    'x-type': xType
                  }
                },
                fetchTestReturnType
            );
          }

        }

        export namespace API {

          export enum FetchTestSelectUriParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestPageQueryParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestXTypeHeaderParam {
            All = 'all',
            Limited = 'limited'
          }

        }

        const fetchTestReturnType: AnyType = [Object];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test generation of multiple parameters of same name with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI,
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

          fetchTest(type: API.FetchTestTypeUriParam, type_: API.FetchTestTypeQueryParam,
              type__: API.FetchTestTypeHeaderParam): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{type}',
                  pathParameters: {
                    type
                  },
                  queryParameters: {
                    type: type_
                  },
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    type: type__
                  }
                },
                fetchTestReturnType
            );
          }

        }

        export namespace API {

          export enum FetchTestTypeUriParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestTypeQueryParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestTypeHeaderParam {
            All = 'all',
            Limited = 'limited'
          }

        }

        const fetchTestReturnType: AnyType = [Object];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }
}
