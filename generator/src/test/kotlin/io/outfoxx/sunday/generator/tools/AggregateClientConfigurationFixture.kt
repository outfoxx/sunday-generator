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

package io.outfoxx.sunday.generator.tools

import io.outfoxx.sunday.generator.ir.GeneratedApi
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import java.nio.file.Path
import kotlin.io.path.writeText

/** Multi-service factory contract with public overrides and server-selected scoped authentication. */
internal fun aggregateClientConfigurationApi(directory: Path): GeneratedApi {
  val source = directory.resolve("aggregate.yaml")
  source.writeText(
    """
    openapi: 3.2.0
    info: {title: Example API, version: '1'}
    servers:
      - name: local
        url: http://localhost:9080
        x-sunday-security-profile: external
      - name: development
        url: https://api.dev.example
        x-sunday-security-profile: external-development
    tags:
      - {name: Users, x-sunday-service-group: true}
      - {name: Projects, x-sunday-service-group: true}
    security: [{identity: [items:read]}, {backupToken: []}]
    paths:
      /users:
        get:
          tags: [Users]
          operationId: listUsers
          responses: {'204': {description: OK}}
        post:
          tags: [Users]
          operationId: register
          security: []
          responses: {'204': {description: OK}}
      /projects:
        get:
          tags: [Projects]
          operationId: listProjects
          responses: {'204': {description: OK}}
    components:
      securitySchemes:
        backupToken:
          type: http
          scheme: bearer
          x-sunday-security:
            client: {provider: backup, flow: static}
        identity:
          type: http
          scheme: bearer
          x-sunday-security:
            profiles:
              external:
                client: {provider: application, flow: authorizationCode}
              external-development:
                client:
                  provider: application
                  flow: authorizationCode
                  discoveryUrl: https://auth.dev.example/.well-known/openid-configuration
    """.trimIndent(),
  )
  return OpenApiToGeneratedApi().convert(source.toUri())
}

/** Splits each frontend's operations into two services to compile the aggregate factory surface. */
internal fun aggregateFrontendConfigurationApi(
  frontend: String,
  directory: Path,
): GeneratedApi {
  val api = clientConfigurationApi(frontend, directory)
  val service = api.services.single()
  return api.copy(
    services =
      listOf(
        service.copy(name = "UsersService", group = "Users"),
        service.copy(
          name = "ProjectsService",
          group = "Projects",
          operations =
            service.operations.map {
              it.copy(id = "other" + it.id.replaceFirstChar(Char::uppercaseChar), path = "/other" + it.path)
            },
        ),
      ),
  )
}
