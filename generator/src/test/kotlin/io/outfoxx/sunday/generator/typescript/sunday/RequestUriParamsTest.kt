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
@DisplayName("[TypeScript/Sunday] [RAML] Request Uri Params Test")
class RequestUriParamsTest {

  @Test
  fun `test basic uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params.raml") testUri: URI
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

          fetchTest(def: string, obj: Test, strReq: string,
              int: number | undefined = undefined): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{obj}/{str-req}/{int}/{def}',
                  pathParameters: {
                    def,
                    obj,
                    'str-req': strReq,
                    int: int ?? 5
                  },
                  acceptTypes: this.defaultAcceptTypes
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
  fun `test inherited uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inherited.raml") testUri: URI
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

          fetchTest(obj: object, str: string, def: string, int: number): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{obj}/{str}/{int}/{def}',
                  pathParameters: {
                    obj,
                    str,
                    def,
                    int
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTestReturnType
            );
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

  @Test
  fun `test optional uri parameter generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-optional.raml") testUri: URI
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

          fetchTest(
              def2: number | null | undefined = undefined,
              obj: Test | undefined = undefined,
              str: string | undefined = undefined,
              def1: string | undefined = undefined,
              int: number | null = null,
              def: string
          ): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{obj}/{str}/{int}/{def}/{def1}/{def2}',
                  pathParameters: {
                    def2: def2 ?? 10,
                    obj,
                    str,
                    def1: def1 ?? 'test',
                    int,
                    def
                  },
                  acceptTypes: this.defaultAcceptTypes
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
  fun `test generation of multiple uri parameters with inline type definitions`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/req-uri-params-inline-types.raml") testUri: URI
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

          fetchTest(category: API.FetchTestCategoryUriParam,
              type: API.FetchTestTypeUriParam): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests/{category}/{type}',
                  pathParameters: {
                    category,
                    type
                  },
                  acceptTypes: this.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

        }

        export namespace API {

          export enum FetchTestCategoryUriParam {
            Politics = 'politics',
            Science = 'science'
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
