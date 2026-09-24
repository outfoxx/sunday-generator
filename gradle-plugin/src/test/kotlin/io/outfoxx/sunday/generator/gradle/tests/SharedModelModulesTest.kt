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

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File

class SharedModelModulesTest {

  @TempDir
  lateinit var directory: File

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `model-only generation compiles with dependent facades in ordinary and composite builds`(composite: Boolean) {
    val contracts = if (composite) directory.resolve("contracts").also { it.mkdirs() } else directory
    contracts.resolve("settings.gradle").writeText(
      "rootProject.name = 'contracts'\ninclude 'models', 'server', 'client', 'events'" +
        if (composite) "" else "\ninclude 'consumer'",
    )
    contracts.resolve("build.gradle").writeText(
      """
      plugins {
        id 'org.jetbrains.kotlin.jvm' version '2.3.20' apply false
        id 'io.outfoxx.sunday-generator' apply false
      }
      subprojects {
        apply plugin: 'org.jetbrains.kotlin.jvm'
        apply plugin: 'java-library'
        group = 'test'
        version = '1'
        repositories { mavenCentral() }
      }
      """.trimIndent(),
    )
    val schema =
      """
      components:
        schemas:
          Item:
            type: object
            required: [id]
            properties:
              id: {type: string}
      """.trimIndent()
    contracts.resolve("api.yaml").writeText(
      """
      openapi: 3.1.0
      info: {title: Items, version: 1.0.0}
      paths:
        /items:
          get:
            operationId: getItem
            responses:
              '200':
                description: Item
                content:
                  application/json:
                    schema:
                      ${'$'}ref: '#/components/schemas/Item'
      """.trimIndent() + "\n" + schema,
    )
    contracts.resolve("events.yaml").writeText(
      """
      asyncapi: 3.1.0
      info: {title: Items, version: 1.0.0}
      channels:
        items:
          address: items
          x-sunday-service: Events
          messages:
            item:
              payload:
                ${'$'}ref: '#/components/schemas/Item'
          bindings:
            amqp:
              exchange: {name: items}
      operations:
        sendItem:
          action: send
          channel:
            ${'$'}ref: '#/channels/items'
          messages:
            - ${'$'}ref: '#/channels/items/messages/item'
        consumeItems:
          action: receive
          channel:
            ${'$'}ref: '#/channels/items'
          messages:
            - ${'$'}ref: '#/channels/items/messages/item'
      """.trimIndent() + "\n" + schema,
    )
    for (module in listOf("models", "server", "client", "events")) {
      val moduleDirectory = contracts.resolve(module).also { it.mkdirs() }
      moduleDirectory.resolve("build.gradle").writeText(
        """
        apply plugin: 'io.outfoxx.sunday-generator'
        dependencies {
          ${if (module != "models") "api project(':models')" else ""}
          api 'jakarta.ws.rs:jakarta.ws.rs-api:3.1.0'
          ${if (module == "events") "api 'io.outfoxx.sunday:sunday-broker:2.0.0-beta.6'" else ""}
        }
        sundayGenerations {
          contract {
            source.set(files(rootProject.file('${if (module == "events") "events.yaml" else "api.yaml"}')))
            framework.set(io.outfoxx.sunday.generator.gradle.TargetFramework.${if (module == "events") "Sunday" else "JAXRS"})
            mode.set(io.outfoxx.sunday.generator.GenerationMode.${if (module == "server") "Server" else "Client"})
            generateModel.set(${module == "models"})
            generateService.set(${module != "models"})
            modelPkgName.set('test.model')
            servicePkgName.set('test.$module')
            useJakartaPackages.set(true)
            disableJacksonAnnotations.set(true)
            disableValidationConstraints.set(true)
          }
        }
        """.trimIndent(),
      )
    }
    val consumer = if (composite) directory else directory.resolve("consumer").also { it.mkdirs() }
    if (composite) {
      directory.resolve("settings.gradle").writeText("rootProject.name = 'consumer'\nincludeBuild('contracts')")
    }
    consumer.resolve("build.gradle").writeText(
      """
      ${if (composite) "plugins { id 'org.jetbrains.kotlin.jvm' version '2.3.20' }" else ""}
      repositories { mavenCentral() }
      dependencies {
        ${listOf("server", "client", "events").joinToString("\n") { module ->
        if (composite) "implementation 'test:$module:1'" else "implementation project(':$module')"
      }}
      }
      """.trimIndent(),
    )
    consumer.resolve("src/main/kotlin/Consumer.kt").also { source ->
      source.parentFile.mkdirs()
      source.writeText(
        """
        package test.consumer
        import test.model.Item
        import test.events.EventsBroker
        class Consumer(val broker: EventsBroker) {
          suspend fun send(item: Item) = broker.sendItem(item)
          fun consume() = broker.consumeItems()
        }
        """.trimIndent(),
      )
    }
    GradleRunner
      .create()
      .withProjectDir(directory)
      .withPluginClasspath()
      .withArguments(if (composite) "classes" else ":consumer:classes", "--parallel", "--max-workers=2", "--stacktrace")
      .build()
    // Inspect ownership only after the complete consumer graph has successfully compiled.
    for (module in listOf("models", "server", "client", "events")) {
      val classes =
        contracts
          .resolve("$module/build/classes")
          .walkTopDown()
          .count { it.isFile && it.name == "Item.class" }
      expectThat(classes).isEqualTo(if (module == "models") 1 else 0)
    }
  }
}
