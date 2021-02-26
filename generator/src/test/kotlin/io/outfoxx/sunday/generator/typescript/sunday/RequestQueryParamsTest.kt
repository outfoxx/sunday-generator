package io.outfoxx.sunday.generator.typescript.sunday

import io.outfoxx.sunday.generator.typescript.TypeScriptSundayGenerator
import io.outfoxx.sunday.generator.typescript.TypeScriptTypeRegistry
import io.outfoxx.sunday.generator.typescript.tools.findTypeMod
import io.outfoxx.sunday.generator.typescript.tools.generate
import io.outfoxx.sunday.test.extensions.ResourceExtension
import io.outfoxx.sunday.test.extensions.ResourceUri
import io.outfoxx.typescriptpoet.FileSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI

@ExtendWith(ResourceExtension::class)
@DisplayName("[TypeScript/Sunday] [RAML] Request Query Params Test")
class RequestQueryParamsTest {

  @Test
  fun `test basic query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params.raml") testUri: URI
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
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          static defaultContentTypes: Array<MediaType> = [];

          static defaultAcceptTypes: Array<MediaType> = [MediaType.JSON];

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(obj: Test, strReq: string, int: number | undefined = undefined): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  queryParameters: {
                    obj,
                    'str-req': strReq,
                    int: int ?? 5
                  },
                  acceptTypes: API.defaultAcceptTypes
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
  fun `test optional query parameter generation`(
    @ResourceUri("raml/resource-gen/req-query-params-optional.raml") testUri: URI
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
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          static defaultContentTypes: Array<MediaType> = [];

          static defaultAcceptTypes: Array<MediaType> = [MediaType.JSON];

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(obj: Test | undefined = undefined, str: string | undefined = undefined,
              int: number | null = null): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  queryParameters: {
                    obj,
                    str,
                    int
                  },
                  acceptTypes: API.defaultAcceptTypes
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
  fun `test generation of multiple query parameters with inline type definitions`(
    @ResourceUri("raml/resource-gen/req-query-params-inline-types.raml") testUri: URI
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
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          static defaultContentTypes: Array<MediaType> = [];

          static defaultAcceptTypes: Array<MediaType> = [MediaType.JSON];

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(category: API.FetchTestCategoryQueryParam,
              type: API.FetchTestTypeQueryParam): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  queryParameters: {
                    category,
                    type
                  },
                  acceptTypes: API.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

        }

        export namespace API {

          export enum FetchTestCategoryQueryParam {
            Politics = 'politics',
            Science = 'science'
          }

          export enum FetchTestTypeQueryParam {
            All = 'all',
            Limited = 'limited'
          }

        }

        const fetchTestReturnType: AnyType = [Object];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

}
