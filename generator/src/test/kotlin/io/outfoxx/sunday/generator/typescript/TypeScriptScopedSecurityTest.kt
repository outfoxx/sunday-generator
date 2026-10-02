/*
 * Copyright 2026 Outfox, Inc.
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

package io.outfoxx.sunday.generator.typescript

import io.outfoxx.sunday.generator.ir.GeneratedService
import io.outfoxx.sunday.generator.ir.emit.effectiveAuth
import io.outfoxx.sunday.generator.tools.scopedSecurityApi
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
@Tag("security")
class TypeScriptScopedSecurityTest {
  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "asyncapi3", "composed"])
  fun `compiled clients pass selected bindings to the shared runtime`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val source = scopedSecurityApi(frontend, directory)
    val operations =
      source.services.flatMap { service ->
        service.operations.map { operation ->
          operation.copy(auth = source.effectiveAuth(service, operation))
        }
      }
    val api = source.copy(services = listOf(GeneratedService("SecurityService", operations = operations)))
    val registry = TypeScriptTypeRegistry(emptySet(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      api,
      registry,
      TypeScriptSundayOptions(
        "https://api.example/problems/",
        listOf("application/json"),
        "API",
        profile = "external",
      ),
    ).generateServiceTypes()
    val calls =
      operations.joinToString("\n") {
        if (it.streaming != null) {
          "for await (const ignored of client.${it.id}()) { void ignored; }"
        } else {
          "await client.${it.id}().execute();"
        }
      }
    val check =
      ModuleSpec
        .builder("SecurityCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport, RequestSpec, TokenManager, TokenRequest} from '@outfoxx/sunday';
            import {createSecurityAPI} from './security-api.js';
            const acquired: TokenRequest[] = [];
            const requests: Request[] = [];
            const manager = new TokenManager({application: {
              identity: 'application',
              configure: () => ({clientIdentity: 'public-client', grantIdentity: 'fresh-session'}),
              acquire: async request => { acquired.push(request); return {accessToken: 'external-token'}; }
            }});
            class CaptureTransport extends FetchTransport {
              override async transportRequest(spec: RequestSpec<unknown>): Promise<Request> {
                const request = await super.transportRequest(spec);
                requests.push(request);
                return request;
              }
              override eventStream<E>(spec: RequestSpec<void>): AsyncIterable<E> {
                const transport = this;
                return {async *[Symbol.asyncIterator]() { await transport.transportRequest(spec); }};
              }
            }
            globalThis.fetch = async () => new Response(null, {status: 204});
            const client = createSecurityAPI(new CaptureTransport('https://api.example', {tokenManager: manager}));
            $calls
            if (requests.length !== ${operations.size}) throw new Error('Missing operation authentication');
            if (acquired.length !== 1) throw new Error('Cache not shared across operation scopes');
            const binding = acquired[0];
            if (binding.provider !== 'application' || binding.profile !== 'external' || binding.flow !== 'authorizationCode' ||
                binding.tokenUrl !== 'https://identity.example/token' || binding.authorizationUrl !== 'https://identity.example/authorize' ||
                binding.scopes.join(',') !== 'items:read') throw new Error('Wrong selected binding');
            for (const request of requests) {
              if (request.headers.get('Authorization')?.toLowerCase() !== 'bearer external-token') throw new Error('Missing bearer credential');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("SecurityCheck", "!security-check") to check),
        "security-check",
        esm = true,
      ),
    )
  }
}
