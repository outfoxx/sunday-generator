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

package io.outfoxx.sunday.generator.gradle.tests

import com.sun.net.httpserver.HttpServer
import io.outfoxx.sunday.generator.ir.GeneratedTypeRef
import io.outfoxx.sunday.generator.ir.OpenApiDocumentSnapshot
import io.outfoxx.sunday.generator.ir.OpenApiToGeneratedApi
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class OpenApiRemoteReferencesTest {
  @Test
  fun `revalidates transitive resources before cached parallel generation`(
    @TempDir directory: File,
  ) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = AtomicInteger()
    val documents = ConcurrentHashMap<String, String>()
    Executors.newVirtualThreadPerTaskExecutor().use { executor ->
      server.executor = executor
      server.createContext("/") { exchange ->
        exchange.use {
          requests.incrementAndGet()
          val content = documents[exchange.requestURI.toString()]
          if (exchange.requestURI.path == "/entry") {
            exchange.responseHeaders.add("Location", "/schemas/user.yaml")
            exchange.sendResponseHeaders(302, -1)
          } else if (content == null) {
            exchange.sendResponseHeaders(404, -1)
          } else {
            val etag = "\"${content.hashCode()}\""
            exchange.responseHeaders.add("ETag", etag)
            if (exchange.requestHeaders.getFirst("If-None-Match") == etag) {
              exchange.sendResponseHeaders(304, -1)
            } else {
              val bytes = content.toByteArray()
              exchange.sendResponseHeaders(200, bytes.size.toLong())
              exchange.responseBody.write(bytes)
            }
          }
        }
      }
      server.start()
      try {
        val base = "http://127.0.0.1:${server.address.port}"
        documents["/schemas/user.yaml"] =
          """
          openapi: 3.1.0
          info: {title: Shared user, version: 1.0.0}
          paths: {}
          components:
            schemas:
              User:
                ${'$'}id: ./user-resource.yaml
                ${'$'}anchor: user
                type: [object, 'null']
                required: [id, parent, text, state]
                properties:
                  id: {type: string}
                  parent: {${'$'}ref: '#user'}
                  profile: {${'$'}ref: 'profile.yaml#profile'}
                  detail:
                    ${'$'}anchor: detail
                    description: Original detail
                    anyOf:
                      - {type: object, properties: {value: {type: string}}}
                      - {type: 'null'}
                  copiedDetail: {${'$'}ref: '#detail'}
                  strictDetail:
                    type: object
                    anyOf: [{type: object, properties: {value: {type: string}}}, {type: 'null'}]
                  text:
                    ${'$'}anchor: text
                    oneOf: [{type: [string, 'null']}, {type: 'null'}]
                  copiedText: {${'$'}ref: '#text'}
                  state:
                    ${'$'}anchor: state
                    type: [string, 'null']
                    enum: [active, inactive]
                  copiedState: {${'$'}ref: '#state'}
              AnnotatedDetail:
                ${'$'}ref: '#/components/schemas/User/properties/detail'
                description: Alias detail
                readOnly: true
                deprecated: true
              NullableValues:
                ${'$'}anchor: values
                type: [array, 'null']
                items: {type: string}
                then: {type: string}
              Anything: true
              Empty: {}
              Pet:
                type: object
                required: [kind]
                properties: {kind: {type: string}}
                discriminator: {propertyName: kind}
              MappedPet:
                ${'$'}id: ./mapped-cat?base=pet
                type: object
                required: [kind]
                properties: {kind: {type: string}}
                discriminator: {propertyName: kind, mapping: {kitty: '?schema=cat'}}
              Cat:
                allOf:
                  - ${'$'}ref: '#/components/schemas/Pet'
                  - type: object
                    properties:
                      kind: {type: string, description: The animal kind}
                      lives: {type: integer}
              RecordNode:
                allOf:
                  - type: object
                    required: [id]
                    properties:
                      id: {type: string}
                      next:
                        anyOf:
                          - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], title: Shared, description: Parent next}
                          - {type: 'null'}
                  - properties:
                      next:
                        anyOf:
                          - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], description: Updated next}
                          - {type: 'null'}
              BaseRecord:
                type: object
                required: [id]
                properties:
                  id: {type: string, description: Parent identifier}
                  payload:
                    anyOf: [{type: string, description: Parent payload}, {type: 'null'}]
                  next:
                    anyOf:
                      - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], title: Shared, description: Parent next}
                      - {type: 'null'}
              DocumentedRecord:
                allOf:
                  - ${'$'}ref: '#/components/schemas/BaseRecord'
                  - type: object
                    required: [id]
                    properties:
                      id: {type: string, description: Child identifier}
                      payload:
                        anyOf: [{type: string, description: Child payload}, {type: 'null'}]
                      next:
                        anyOf:
                          - {allOf: [{allOf: [{${'$'}ref: '#/components/schemas/RecordNode'}]}], description: Child next}
                          - {type: 'null'}
                      detail: {type: string}
              Dog: {allOf: [{${'$'}ref: '#/components/schemas/Pet'}, {type: object, properties: {barks: {type: boolean}}}]}
          """.trimIndent()
        documents["/schemas/profile.yaml"] = profileSchema(false)
        documents["/schemas/mapped-cat?schema=cat"] = mappedCatSchema(false)
        directory.resolve("settings.gradle").writeText(
          """
          rootProject.name = 'remote-references'
          include 'one', 'two'
          buildCache { local { directory = file('local-cache') } }
          """.trimIndent(),
        )
        directory.resolve("build.gradle").writeText(
          """
          import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
          import static io.outfoxx.sunday.generator.GenerationMode.*
          plugins {
            id 'org.jetbrains.kotlin.jvm' version '2.3.10' apply false
            id 'io.outfoxx.sunday-generator' apply false
          }
          subprojects {
            apply plugin: 'org.jetbrains.kotlin.jvm'
            apply plugin: 'io.outfoxx.sunday-generator'
            repositories { mavenCentral() }
            sundayGenerations {
              client {
                source.set(files('api.yaml'))
                framework.set(Sunday)
                mode.set(Client)
                pkgName.set('io.test.' + project.name)
                generateService.set(false)
                disableValidationConstraints.set(true)
                disableJacksonAnnotations.set(true)
                generatedAnnotation.set(null)
                openApiReferenceCacheDirectory.set(rootProject.layout.projectDirectory.dir('remote-cache'))
              }
            }
          }
          """.trimIndent(),
        )
        for (name in listOf("one", "two")) {
          directory.resolve(name).mkdirs()
          directory.resolve("$name/api.yaml").writeText(
            """
            openapi: 3.0.3
            info: {title: Remote, version: 1.0.0}
            paths: {}
            components:
              schemas:
                cat: {type: object, properties: {unrelated: {type: boolean}}}
                Anything: {${'$'}ref: '$base/entry#/components/schemas/Anything'}
                Empty: {${'$'}ref: '$base/entry#/components/schemas/Empty'}
                Unbounded: {type: integer, exclusiveMinimum: false, exclusiveMaximum: false}
                DocumentedRecord: {${'$'}ref: '$base/entry#/components/schemas/DocumentedRecord'}
                MappedPet: {${'$'}ref: '$base/entry#/components/schemas/MappedPet'}
                Pets:
                  type: object
                  properties:
                    animal: {${'$'}ref: '$base/entry#/components/schemas/Pet'}
                    cat: {${'$'}ref: '$base/entry#/components/schemas/Cat'}
                    dog: {${'$'}ref: '$base/entry#/components/schemas/Dog'}
                User: {${'$'}ref: '$base/entry#/components/schemas/User'}
                AnnotatedDetail: {${'$'}ref: '$base/entry#/components/schemas/AnnotatedDetail'}
                Nullability:
                  type: object
                  required: [strictText, values]
                  properties:
                    strictText: {type: string, allOf: [{type: string, nullable: true}]}
                    values: {${'$'}ref: '$base/entry#values'}
            """.trimIndent(),
          )
        }

        for (name in listOf("one", "two")) {
          val sources = directory.resolve("$name/src/main/kotlin").apply { mkdirs() }
          sources.resolve("InheritanceCheck.kt").writeText(
            "package io.test.$name\nfun asParent(child: DocumentedRecord): BaseRecord = child\n" +
              "fun asMappedParent(child: MappedCat): MappedPet = child\n",
          )
        }

        fun runner(vararg arguments: String) =
          GradleRunner
            .create()
            .withProjectDir(directory)
            .withPluginClasspath()
            .withArguments("build", "--stacktrace", "--build-cache", "--parallel", "--max-workers=4", *arguments)
            .withDebug(true)

        val first = runner().build()
        assertEquals(8, requests.get(), "Generation and source grouping must use captured documents")
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.SUCCESS, first.task(":$project:compileKotlin")?.outcome)
          val projectDirectory = directory.resolve(project)
          val manifest =
            projectDirectory.resolve("build/generated/sunday/documents").walkTopDown().single {
              it.name ==
                "manifest.yaml"
            }
          val captured = OpenApiDocumentSnapshot.loader(manifest.parentFile.toPath(), projectDirectory.toPath())
          val api = OpenApiToGeneratedApi().convert(projectDirectory.resolve("api.yaml").toURI(), captured)
          assertEquals(
            "unrelated",
            api.models
              .single { it.name == "cat" }
              .properties
              .single()
              .name,
          )
          assertEquals(listOf(GeneratedTypeRef.scalar("any")), api.models.single { it.name == "Anything" }.aliases)
          assertEquals(
            api.models.single { it.name == "Empty" }.aliases,
            api.models.single { it.name == "Anything" }.aliases,
          )
          assertEquals(listOf(GeneratedTypeRef.scalar("integer")), api.models.single { it.name == "Unbounded" }.aliases)
          assertEquals(
            mapOf("Cat" to GeneratedTypeRef.named("Cat2"), "Dog" to GeneratedTypeRef.named("Dog")),
            api.models.single { it.name == "Pet" }.discriminatorMappings,
          )
          assertEquals("Cat", api.models.single { it.name == "Cat2" }.discriminatorValue)
          assertEquals(
            mapOf("kitty" to GeneratedTypeRef.named("MappedCat")),
            api.models.single { it.name == "MappedPet" }.discriminatorMappings,
          )
          val mappedCat = api.models.single { it.name == "MappedCat" }
          assertEquals("kitty", mappedCat.discriminatorValue)
          assertEquals(listOf(GeneratedTypeRef.named("MappedPet")), mappedCat.inherits)
          assertEquals("lives", mappedCat.properties.single().name)
          assertTrue(mappedCat.properties.single().required)
          assertEquals(listOf(GeneratedTypeRef.named("Pet")), api.models.single { it.name == "Cat2" }.inherits)
          val record = api.models.single { it.name == "DocumentedRecord" }
          assertEquals(listOf(GeneratedTypeRef.named("BaseRecord")), record.inherits)
          assertEquals(listOf("detail"), record.properties.map { it.name })
          val identifier =
            api.models
              .single { it.name == "BaseRecord" }
              .properties
              .single { it.name == "id" }
          assertTrue(identifier.required)
          assertEquals("Parent identifier", identifier.documentation?.description)
          val payload =
            api.models
              .single { it.name == "BaseRecord" }
              .properties
              .single { it.name == "payload" }
          assertEquals(GeneratedTypeRef.scalar("string", nullable = true), payload.type)
          assertFalse(payload.required)
          val next =
            api.models
              .single { it.name == "BaseRecord" }
              .properties
              .single { it.name == "next" }
          assertEquals(GeneratedTypeRef.named("RecordNode", nullable = true), next.type)
          assertFalse(next.required)
          val profile = directory.resolve(project).walkTopDown().single { it.name == "Profile.kt" }
          assertFalse(profile.readText().contains("displayName"))
          val user = directory.resolve(project).walkTopDown().single { it.name == "User.kt" }
          assertTrue(user.readText().contains("parent: User?"))
          assertTrue(user.readText().contains("detail: Detail?"))
          assertTrue(user.readText().contains("copiedDetail: Detail?"))
          assertFalse(user.readText().contains("Alias detail"))
          assertTrue(user.readText().contains("text: String"))
          assertFalse(user.readText().contains("text: String?"))
          assertTrue(user.readText().contains("state: State"))
          assertFalse(user.readText().contains("state: State?"))
          val strict = directory.resolve(project).walkTopDown().single { it.name == "UserStrictDetail.kt" }
          assertTrue(Regex("`?value`?: String\\?").containsMatchIn(strict.readText()), strict.readText())
          val nullability =
            directory
              .resolve(project)
              .walkTopDown()
              .single { it.name == "Nullability.kt" }
              .readText()
          assertTrue(nullability.contains("strictText: String"), nullability)
          assertFalse(nullability.contains("strictText: String?"), nullability)
          assertTrue(nullability.contains("values: List<String>?"), nullability)
        }
        requests.set(0)
        val unchanged = runner().build()
        assertEquals(8, requests.get())
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":$project:sundayGenerate_client")?.outcome)
        }
        documents["/schemas/mapped-cat?schema=cat"] = mappedCatSchema(true)
        documents["/schemas/mapped-name"] = "type: string"
        val mappedChange = runner().build()
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.SUCCESS, mappedChange.task(":$project:sundayGenerate_client")?.outcome)
          assertEquals(TaskOutcome.SUCCESS, mappedChange.task(":$project:compileKotlin")?.outcome)
          val mappedCat = directory.resolve(project).walkTopDown().single { it.name == "MappedCat.kt" }
          assertTrue(mappedCat.readText().contains("nickname: String?"))
        }
        documents["/schemas/profile.yaml"] = profileSchema(true)
        documents["/schemas/name.yaml"] = "type: string"
        val changed = runner().build()
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.SUCCESS, changed.task(":$project:compileKotlin")?.outcome)
          val profile = directory.resolve(project).walkTopDown().single { it.name == "Profile.kt" }
          assertTrue(profile.readText().contains("displayName: String?"))
          directory.resolve("$project/build").deleteRecursively()
        }
        val restored = runner().build()
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.FROM_CACHE, restored.task(":$project:sundayGenerate_client")?.outcome)
        }
        requests.set(0)
        val offline = runner("--offline").build()
        assertEquals(0, requests.get())
        for (project in listOf("one", "two")) {
          assertEquals(TaskOutcome.UP_TO_DATE, offline.task(":$project:sundayGenerate_client")?.outcome)
        }
        for (name in listOf("one", "two")) {
          val source = directory.resolve("$name/api.yaml")
          source.writeText(source.readText().replace("/entry#", "/schemas/user.yaml#"))
        }
        runner("--offline").build()
        assertEquals(0, requests.get(), "Effective redirect URLs must remain available offline in parallel generations")
        documents.remove("/schemas/profile.yaml")
        val failed = runner().buildAndFail()
        assertTrue(failed.output.contains("404"), failed.output)
      } finally {
        server.stop(0)
      }
    }
  }

  private fun mappedCatSchema(includeName: Boolean): String =
    """
    allOf:
      - ${'$'}ref: 'user.yaml#/components/schemas/MappedPet'
      - type: object
        required: [lives]
        properties:
          lives: {type: integer}
    """.trimIndent() + if (includeName) "\n      nickname: {\$ref: 'mapped-name'}" else ""

  private fun profileSchema(includeName: Boolean): String =
    """
    ${'$'}id: ../identities/profile.yaml
    ${'$'}anchor: profile
    type: object
    properties:
      id: {type: string}
    """.trimIndent() + if (includeName) "\n  displayName: {\$ref: '../schemas/name.yaml'}" else ""
}
