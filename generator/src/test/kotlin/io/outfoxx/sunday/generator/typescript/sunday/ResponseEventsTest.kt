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
@DisplayName("[TypeScript/Sunday] [RAML] Response Events Test")
class ResponseEventsTest {

  @Test
  fun `test event source method`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-event-source.raml") testUri: URI,
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
        import {MediaType, RequestFactory} from '@outfoxx/sunday';


        export class API {

          defaultContentTypes: Array<MediaType>;

          defaultAcceptTypes: Array<MediaType>;

          constructor(public requestFactory: RequestFactory,
              options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
            this.defaultContentTypes =
                options?.defaultContentTypes ?? [];
            this.defaultAcceptTypes =
                options?.defaultAcceptTypes ?? [];
          }

          fetchEvents(): EventSource {
            return this.requestFactory.eventSource(
                {
                  method: 'GET',
                  pathTemplate: '/tests',
                  acceptTypes: [MediaType.EventStream]
                }
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event stream method generation`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream.raml") testUri: URI,
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
        import {Test1} from './test1';
        import {Test2} from './test2';
        import {Test3} from './test3';
        import {MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          defaultContentTypes: Array<MediaType>;

          defaultAcceptTypes: Array<MediaType>;

          constructor(public requestFactory: RequestFactory,
              options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
            this.defaultContentTypes =
                options?.defaultContentTypes ?? [];
            this.defaultAcceptTypes =
                options?.defaultAcceptTypes ?? [];
          }

          fetchEventsSimple(): Observable<Test1> {
            return this.requestFactory.eventStream<Test1>(
                {
                  method: 'GET',
                  pathTemplate: '/test1',
                  acceptTypes: [MediaType.EventStream]
                },
                (decoder, event, id, data) => decoder.decodeText(data, [Test1])
            );
          }

          fetchEventsDiscriminated(): Observable<Test1 | Test2 | Test3> {
            return this.requestFactory.eventStream<Test1 | Test2 | Test3>(
                {
                  method: 'GET',
                  pathTemplate: '/test2',
                  acceptTypes: [MediaType.EventStream]
                },
                (decoder, event, id, data, logger) => {
                  switch (event) {
                  case 'Test1': return decoder.decodeText(data, [Test1]);
                  case 'test2': return decoder.decodeText(data, [Test2]);
                  case 't3': return decoder.decodeText(data, [Test3]);
                  default:
                    logger?.error?.(`Unknown event type, ignoring event: event=${'$'}{event}`);
                    return undefined;
                  }
                },
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }

  @Test
  fun `test event stream method generation for common base events`(
    compiler: TypeScriptCompiler,
    @ResourceUri("raml/resource-gen/res-event-stream-common.raml") testUri: URI,
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
        import {Base} from './base';
        import {Test1} from './test1';
        import {Test2} from './test2';
        import {MediaType, RequestFactory} from '@outfoxx/sunday';
        import {Observable} from 'rxjs';


        export class API {

          defaultContentTypes: Array<MediaType>;

          defaultAcceptTypes: Array<MediaType>;

          constructor(public requestFactory: RequestFactory,
              options: { defaultContentTypes?: Array<MediaType>, defaultAcceptTypes?: Array<MediaType> } | undefined = undefined) {
            this.defaultContentTypes =
                options?.defaultContentTypes ?? [];
            this.defaultAcceptTypes =
                options?.defaultAcceptTypes ?? [];
          }

          fetchEventsSimple(): Observable<Base> {
            return this.requestFactory.eventStream<Base>(
                {
                  method: 'GET',
                  pathTemplate: '/test1',
                  acceptTypes: [MediaType.EventStream]
                },
                (decoder, event, id, data) => decoder.decodeText(data, [Base])
            );
          }

          fetchEventsDiscriminated(): Observable<Base> {
            return this.requestFactory.eventStream<Base>(
                {
                  method: 'GET',
                  pathTemplate: '/test2',
                  acceptTypes: [MediaType.EventStream]
                },
                (decoder, event, id, data, logger) => {
                  switch (event) {
                  case 'Test1': return decoder.decodeText(data, [Test1]);
                  case 'Test2': return decoder.decodeText(data, [Test2]);
                  default:
                    logger?.error?.(`Unknown event type, ignoring event: event=${'$'}{event}`);
                    return undefined;
                  }
                },
            );
          }

        }

      """.trimIndent(),
      buildString {
        FileSpec.get(typeSpec)
          .writeTo(this)
      },
    )
  }
}
