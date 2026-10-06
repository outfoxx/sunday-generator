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

import io.outfoxx.sunday.generator.ir.GeneratedServer
import io.outfoxx.sunday.generator.tools.aggregateClientConfigurationApi
import io.outfoxx.sunday.generator.tools.aggregateFrontendConfigurationApi
import io.outfoxx.sunday.generator.tools.clientConfigurationApi
import io.outfoxx.sunday.generator.typescript.tools.TypeScriptCompiler
import io.outfoxx.sunday.generator.typescript.tools.compileAndRunTypes
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.ModuleSpec
import io.outfoxx.typescriptpoet.TypeName
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TypeScriptTest
class TypeScriptClientConfigurationTest {
  @ParameterizedTest
  @ValueSource(
    strings = [
      "raml", "openapi", "asyncapi", "composed", "security", "multi",
      "alternatives", "server-security", "server-profile", "prototype",
    ],
  )
  fun `configuration callback preserves transport type and is invoked once`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    fun prototypeVariable(server: GeneratedServer): GeneratedServer =
      server.copy(
        url = server.url.replace("{tenant}", "{__proto__}"),
        variables = server.variables.map { it.copy(serializationName = "__proto__") },
      )
    val sourceApi = clientConfigurationApi(frontend, directory)
    val api =
      if (frontend == "prototype") {
        sourceApi.copy(
          servers = sourceApi.servers.map(::prototypeVariable),
          services = sourceApi.services.map { it.copy(servers = it.servers.map(::prototypeVariable)) },
        )
      } else {
        sourceApi
      }
    val authenticated = frontend in setOf("security", "alternatives", "server-security", "server-profile")
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      api,
      registry,
      TypeScriptSundayOptions("https://example.com/", listOf("application/json"), "API"),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("ConfigCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport} from '@outfoxx/sunday';
            import {API, createAPI, APISecurityAlternative} from './api.js';
            if (false) {
              // @ts-expect-error A configuration cannot be used without a transport factory.
              createAPI({${if (frontend in
              setOf(
                "multi",
                "server-security",
              )
            ) {
              "serverId: 'production', "
            } else {
              ""
            }}tenant: 'secondary'});
            }
            let calls = 0;
            const client: API<FetchTransport> = createAPI({${if (frontend in
              setOf(
                "multi",
                "server-security",
              )
            ) {
              "serverId: 'production', "
            } else {
              ""
            }}tenant: 'secondary'}, settings => {
              calls++;
              if ((settings.tokenManager !== undefined) !== ${authenticated
            }) throw new Error('Manager was not prepared');
              if (settings.baseUrl !== 'https://secondary.example/v1') throw new Error('Endpoint mismatch');
              return FetchTransport.fromSettings(settings);
            }${if (frontend in setOf("alternatives", "server-profile")) {
              ", {credentials: {identity: {kind: 'bearer', token: 'secret'}, accessKey: {kind: 'apiKey', key: 'key'}}, securitySelection: {listItems: APISecurityAlternative.AccessKeyAndIdentity}}"
            } else if (frontend in setOf("security", "server-security", "server-profile")) {
              ", {credentials: {identity: {kind: 'bearer', token: 'secret'}}}"
            } else {
              ""
            }});
            if (calls !== 1) throw new Error('Factory count mismatch');
            ${if (frontend in
              setOf(
                "asyncapi",
                "server-security",
                "server-profile",
              )
            ) {
              ""
            } else {
              "const request: Promise<Request> = client.listItems().transportRequest(); await request;"
            }}
            const direct: API<FetchTransport> = createAPI(new FetchTransport('https://direct.example'));
            ${if (frontend in
              setOf(
                "asyncapi",
                "server-security",
                "server-profile",
              )
            ) {
              "void direct;"
            } else {
              "await direct.listItems().transportRequest();"
            }}
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("ConfigCheck", "!config-check") to check),
        "config-check",
        esm = true,
      ),
    )
  }

  @Test
  fun `generated factories preserve application token persistence`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      aggregateClientConfigurationApi(directory),
      registry,
      TypeScriptSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    val source =
      java.nio.file.Files
        .readString(Path.of("src/test/resources/client-config-persistence/typescript.ts"))
    val consumer =
      ModuleSpec
        .builder(
          "PersistenceCheck",
          ModuleSpec.Kind.MODULE,
        ).addCode(CodeBlock.of("%L", source))
        .build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("PersistenceCheck", "!persistence-check") to consumer),
        "persistence-check",
        esm = true,
      ),
    )
  }

  @Test
  fun `aggregate factory shares profile settings and token acquisition across children`(
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {
    val registry = TypeScriptTypeRegistry(setOf(), importStyle = TypeScriptTypeRegistry.ImportStyle.NodeNext)
    TypeScriptSundayIrGenerator(
      aggregateClientConfigurationApi(directory),
      registry,
      TypeScriptSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        profile = "external",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    val check =
      ModuleSpec
        .builder("AggregateCheck", ModuleSpec.Kind.MODULE)
        .addCode(
          CodeBlock.of(
            """
            import {FetchTransport, TokenProvider, TokenRequest} from '@outfoxx/sunday';
            import {createExampleAPI, ExampleAPICredentials, ExampleAPISecurityAlternative} from './example-api.js';
            const acquired: TokenRequest[] = [];
            const provider: TokenProvider = {
              identity: 'application',
              configure: () => ({clientIdentity: 'client', grantIdentity: 'session'}),
              acquire: async request => { acquired.push(request); return {accessToken: 'token'}; }
            };
            for (const override of [false, true]) {
              acquired.length = 0;
              let calls = 0;
              const expected = override ? 'external' : 'external-development';
              const client = createExampleAPI({serverId: 'development'}, settings => {
                calls++;
                if (settings.baseUrl !== 'https://api.dev.example/') throw new Error('Wrong URL');
                if (Object.keys(settings.bindings).length !== 3 || settings.bindings.register.length !== 0) throw new Error('Incomplete operation selection');
                for (const id of ['listUsers', 'listProjects']) {
                  const binding = settings.bindings[id][0];
                  if (binding.profile !== expected || binding.scopes.join(',') !== 'items:read') throw new Error('Wrong binding');
                }
                return FetchTransport.fromSettings(settings);
              }, {credentials: {identity: {kind: 'provider', provider}},
                  ...(override ? {securityProfile: 'external'} : {}),
                  securitySelection: {listProjects: ExampleAPISecurityAlternative.Identity}});
              if (calls !== 1 || acquired.length !== 0) throw new Error('Eager acquisition or duplicate transport');
              const publicRequest = await client.users.register().transportRequest();
              if (publicRequest.headers.has('Authorization') || acquired.length !== 0) throw new Error('Public operation authenticated');
              const users = await client.users.listUsers().transportRequest();
              const projects = await client.projects.listProjects().transportRequest();
              if (Number(acquired.length) !== 1 || acquired[0].profile !== expected) throw new Error('Settings or cache not shared');
              if (users.headers.get('Authorization')?.toLowerCase() !== 'bearer token' || projects.headers.get('Authorization')?.toLowerCase() !== 'bearer token') throw new Error('Missing credentials');
            }
            const invalidCredentials: ExampleAPICredentials[] = [
              {},
              {identity: {kind: 'provider', provider}, backupToken: {kind: 'bearer', token: 'key'}}
            ];
            for (const credentials of invalidCredentials) {
              let invalidCalls = 0;
              let rejected = false;
              try { createExampleAPI({serverId: 'development'}, settings => { invalidCalls++; return FetchTransport.fromSettings(settings); }, {credentials}); }
              catch { rejected = true; }
              if (!rejected || invalidCalls !== 0) throw new Error('Invalid credentials accepted');
            }
            """.trimIndent(),
          ),
        ).build()
    assertTrue(
      compileAndRunTypes(
        compiler,
        registry.buildTypes() + (TypeName.namedImport("AggregateCheck", "!aggregate-check") to check),
        "aggregate-check",
        esm = true,
      ),
    )
  }

  @ParameterizedTest
  @ValueSource(strings = ["raml", "openapi", "asyncapi", "composed"])
  fun `aggregate factories compile for every frontend`(
    frontend: String,
    compiler: TypeScriptCompiler,
    @TempDir directory: Path,
  ) {

    val registry = TypeScriptTypeRegistry(setOf())
    TypeScriptSundayIrGenerator(
      aggregateFrontendConfigurationApi(frontend, directory),
      registry,
      TypeScriptSundayOptions(
        "https://example.com/",
        listOf("application/json"),
        "API",
        aggregateServices = true,
        aggregateServiceName = "ExampleAPI",
      ),
    ).generateServiceTypes()
    assertTrue(
      io.outfoxx.sunday.generator.typescript.tools
        .compileTypes(compiler, registry.buildTypes()),
    )
  }
}
