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
@DisplayName("[TypeScript/Sunday] [RAML] Request Header Params Test")
class RequestHeaderParamsTest {

  @Test
  fun `test basic header parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params.raml") testUri: URI
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
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
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
  fun `test optional header parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-optional.raml") testUri: URI
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
        import {Test} from './test';
        import {AnyType, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
          }

          fetchTest(obj: Test | undefined = undefined, str: string | undefined = undefined,
              int: number | null = null): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes,
                  headers: {
                    obj,
                    str,
                    int
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
  fun `test generation of multiple header parameters with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-header-params-inline-types.raml") testUri: URI
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

          fetchTest(category: API.FetchTestCategoryHeaderParam,
              type: API.FetchTestTypeHeaderParam): Observable<object> {
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

        const fetchTestReturnType: AnyType = [Object];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

}
