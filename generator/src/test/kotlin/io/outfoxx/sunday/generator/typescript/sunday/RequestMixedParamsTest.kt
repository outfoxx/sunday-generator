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
import org.junit.jupiter.api.Disabled
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
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
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

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
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

          export enum FetchTestPageQueryParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestSelectUriParam {
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
      }
    )
  }

  @Test @Disabled("Blocking issue: https://github.com/aml-org/amf/issues/830")
  fun `test generation of multiple parameters of same name with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-mixed-params-inline-types-same-name.raml") testUri: URI
  ) {

    val typeRegistry = TypeScriptTypeRegistry(setOf())

    val builtTypes =
      generate(testUri, typeRegistry, compiler) { document ->
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

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
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

          export enum FetchTestTypeQueryParam {
            All = 'all',
            Limited = 'limited'
          }

          export enum FetchTestTypeUriParam {
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
