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
@DisplayName("[TypeScript/Sunday] [RAML] Response Events Test")
class ResponseEventsTest {

  @Test
  fun `test event source method`(
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI
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
        import {MediaType, RequestFactory} from '@outfoxx/sunday';


        export class API {

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
          }

          fetchEvents(): EventSource {
            return this.requestFactory.events(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes
                }
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test event stream method generation`(
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI
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
        import {Test1} from './test1';
        import {Test2} from './test2';
        import {EventTypes, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
          }

          fetchEvents(): Observable<Test1 | Test2> {
            const eventTypes: EventTypes<Test1 | Test2> = {
              'Test1' : [Test1], 
              'Test2' : [Test2]
            };
            return this.requestFactory.events<Test1 | Test2>(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes
                },
                eventTypes
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

  @Test
  fun `test event stream method generation for common base events`(
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI
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
        import {Base} from './base';
        import {Test1} from './test1';
        import {Test2} from './test2';
        import {EventTypes, MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          constructor(public requestFactory: RequestFactory,
              public defaultContentTypes: Array<MediaType> = [],
              public defaultAcceptTypes: Array<MediaType> = [MediaType.JSON]) {
          }

          fetchEvents(): Observable<Base> {
            const eventTypes: EventTypes<Base> = {
              'Test1' : [Test1], 
              'Test2' : [Test2]
            };
            return this.requestFactory.events<Base>(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: this.defaultAcceptTypes
                },
                eventTypes
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      }
    )
  }

}
