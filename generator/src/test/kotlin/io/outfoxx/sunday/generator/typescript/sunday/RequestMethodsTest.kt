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
@DisplayName("[TypeScript/Sunday] [RAML] Request Methods Test")
class RequestMethodsTest {

  @Test
  fun `test request method generation`(
    @ResourceUri("raml/resource-gen/req-methods.raml") testUri: URI
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

          static defaultContentTypes: Array<MediaType> = [MediaType.JSON];

          static defaultAcceptTypes: Array<MediaType> = [MediaType.JSON];

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = API.defaultContentTypes,
              public defaultAcceptTypes: Array<MediaType> = API.defaultAcceptTypes) {
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

          patchableTest(body: object): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'PATCH',
                  pathTemplate: '/tests2',
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
        const patchableTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }
}
