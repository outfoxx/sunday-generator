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
@DisplayName("[TypeScript] [RAML] Request Body Param Test")
class RequestBodyParamTest {

  @Test
  fun `test basic body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param.raml") testUri: URI
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

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(body: Test): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: fetchTestBodyType,
                  contentTypes: API.defaultContentTypes,
                  acceptTypes: API.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

        }

        const fetchTestBodyType: AnyType = [Test];
        const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test optional body parameter generation`(
    @ResourceUri("raml/resource-gen/req-body-param-optional.raml") testUri: URI
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

          constructor(public requestFactory: RequestFactory) {
          }

          fetchTest(body: Test | null): Observable<Test> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: fetchTestBodyType,
                  contentTypes: API.defaultContentTypes,
                  acceptTypes: API.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

        }

        const fetchTestBodyType: AnyType = [Test];
        const fetchTestReturnType: AnyType = [Test];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test generation of body parameter with explicit content type`(
    @ResourceUri("raml/resource-gen/req-body-param-explicit-content-type.raml") testUri: URI
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

          fetchTest(body: BodyInit): Observable<object> {
            return this.requestFactory.result(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  body: body,
                  bodyType: fetchTestBodyType,
                  contentTypes: [MediaType.OctetStream],
                  acceptTypes: API.defaultAcceptTypes
                },
                fetchTestReturnType
            );
          }

        }

        const fetchTestBodyType: AnyType = [ArrayBuffer];
        const fetchTestReturnType: AnyType = [Object];

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

}
