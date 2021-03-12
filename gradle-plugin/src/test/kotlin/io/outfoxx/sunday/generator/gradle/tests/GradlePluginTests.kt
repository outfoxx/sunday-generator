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

package io.outfoxx.sunday.generator.gradle.tests

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture

class GradlePluginTests {

  private lateinit var testProjectDir: File
  private lateinit var buildFile: File

  @BeforeEach
  fun setup() {
    testProjectDir = File("${System.getProperty("java.io.tmpdir")}/${UUID.randomUUID()}")
    testProjectDir.mkdirs()

    buildFile = testProjectDir.resolve("build.gradle")
    buildFile.createNewFile()

    copy("/Messaging.raml", testProjectDir.resolve("src/main/sunday"))
  }

  @Test
  fun testGeneratesSourceForKotlin() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    val buildFileContent = """
      import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
      import static io.outfoxx.sunday.generator.GenerationMode.*

      plugins {
        id 'org.jetbrains.kotlin.jvm' version '1.4.30'
        id 'io.outfoxx.sunday-generator'
      }

      repositories {
        mavenLocal()
        mavenCentral()
      }

      sundayGenerations {
        client { 
          framework.set(Sunday)
          mode.set(Client)
          modelPkgName.set('io.outfoxx.messaging.client.model')
          servicePkgName.set('io.outfoxx.messaging.client.api')
        }
        server {
          framework.set(JAXRS)
          mode.set(Server)
          reactiveResponseType.set("${CompletableFuture::class.java.canonicalName}")
          pkgName.set('io.outfoxx.messaging.server')
        }
      }

      dependencies {
        implementation "io.outfoxx:sunday:1.0.0-SNAPSHOT"
        implementation "org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec:1.0.0.Final"
        implementation "javax.validation:validation-api:1.1.0.Final"
        implementation "com.fasterxml.jackson.core:jackson-databind:2.10.0"
      }
      
      compileKotlin {
        kotlinOptions {
          jvmTarget = "11"
          suppressWarnings = true
        }
      }
    """.trimIndent()

    buildFile.writeText(buildFileContent)

    val result = GradleRunner.create()
      .withProjectDir(testProjectDir)
      .withPluginClasspath()
      .withArguments("build", "--stacktrace")
      .withDebug(true)
      .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val genServerTask = result.task(":sundayGenerate_server")
    assertThat(genServerTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val kotlinTask = result.task(":compileKotlin")
    assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.SUCCESS))
  }

  private fun copy(src: String, dstDir: File) {
    dstDir.mkdirs()

    val srcPath = Paths.get(GradlePluginTests::class.java.getResource(src).toURI())
    val dstPath = dstDir.toPath()
    Files.copy(srcPath, dstPath.resolve(srcPath.fileName))
  }
}
