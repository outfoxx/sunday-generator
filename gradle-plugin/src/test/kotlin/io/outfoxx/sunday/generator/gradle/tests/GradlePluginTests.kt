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
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.appendText

class GradlePluginTests {

  val dualTestBuildFile =
    """
    import static io.outfoxx.sunday.generator.gradle.TargetFramework.*
    import static io.outfoxx.sunday.generator.GenerationMode.*

    plugins {
      id 'org.jetbrains.kotlin.jvm' version '2.3.10'
      id 'io.outfoxx.sunday-generator'
    }

    repositories {
      mavenCentral()
    }

    sundayGenerations {
      client {
        source.set(fileTree('src/main/sunday') { it.include('**/*.raml') })
        includes.set(fileTree('src/main/sunday-includes') { it.include('**/*.raml') })
        framework.set(Sunday)
        mode.set(Client)
        pkgName.set('io.outfoxx.test.client')
        modelPkgName.set('io.outfoxx.test.client.model')
        servicePkgName.set('io.outfoxx.test.client.api')
        generateModel.set(true)
        generateService.set(true)
        serviceSuffix.set('API')
        disableValidationConstraints.set(false)
        disableJacksonAnnotations.set(false)
        disableModelImplementations.set(false)
        coroutines.set(false)
        reactiveResponseType.set(null)
        explicitSecurityParameters.set(false)
        baseUriMode.set(null)
        defaultMediaTypes.set(["application/json"])
        generatedAnnotation.set('javax.annotation.processing.Generated')
        alwaysUseResponseReturn.set(false)
        useResultResponseReturn.set(false)
        useJakartaPackages.set(false)
      }
      server {
        framework.set(JAXRS)
        mode.set(Server)
        reactiveResponseType.set("${CompletableFuture::class.java.canonicalName}")
        pkgName.set('io.outfoxx.test.server')
      }
    }

    dependencies {
      implementation "io.outfoxx.sunday:sunday-core:1.0.0-beta.25"
      implementation "org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec:1.0.0.Final"
      implementation "javax.validation:validation-api:2.0.1.Final"
      implementation "com.fasterxml.jackson.core:jackson-databind:2.10.0"
    }

    java {
      sourceCompatibility = "21"
      targetCompatibility = "21"
    }

    compileKotlin {
      kotlinOptions {
        jvmTarget = "21"
        suppressWarnings = true
      }
    }

    if (hasProperty('buildScan')) {
        buildScan {
            termsOfServiceUrl = 'https://gradle.com/terms-of-service'
            termsOfServiceAgree = 'yes'
        }
    }

    """.trimIndent()

  private lateinit var buildFile: File

  @TempDir
  lateinit var testProjectDir: File

  @BeforeEach
  fun setup() {

    buildFile = testProjectDir.resolve("build.gradle")
    buildFile.createNewFile()

    copy("/test.raml", testProjectDir.resolve("src/main/sunday"))
  }

  @Test
  fun `generate Sunday client & JAX-RS server for Kotlin`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "--stacktrace", "--debug")
        .withDebug(true)
        .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val genServerTask = result.task(":sundayGenerate_server")
    assertThat(genServerTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val kotlinTask = result.task(":compileKotlin")
    assertThat(kotlinTask?.outcome, equalTo(TaskOutcome.SUCCESS))
  }

  @Test
  fun `generate sources for kotlin are cached`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    listOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE).forEach { outcome ->
      println("Executing build expecting outcome: $outcome")

      if (outcome == TaskOutcome.FROM_CACHE) {
        // Delete build directory to test loading from cache
        testProjectDir.resolve("build").deleteRecursively()
      }

      val result =
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("build", "--stacktrace", "--debug", "--build-cache")
          .withDebug(true)
          .forwardOutput()
          .build()

      val genClientTask = result.task(":sundayGenerate_client")
      assertThat(genClientTask?.outcome, equalTo(outcome))

      val genServerTask = result.task(":sundayGenerate_server")
      assertThat(genServerTask?.outcome, equalTo(outcome))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(outcome))
    }
  }

  @Test
  fun `generate sources for kotlin are regenerated when sources changes`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    listOf(TaskOutcome.SUCCESS, TaskOutcome.SUCCESS).forEach { outcome ->
      println("Executing build expecting outcome: $outcome")

      // Delete build directory to test loading from cache
      testProjectDir.resolve("build").deleteRecursively()

      // Change contents of test.raml to force regeneration
      testProjectDir.resolve("src/main/sunday/test.raml").toPath().appendText("\n# Added comment\n")

      val result =
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("build", "--stacktrace", "--debug", "--build-cache")
          .withDebug(true)
          .forwardOutput()
          .build()

      val genClientTask = result.task(":sundayGenerate_client")
      assertThat(genClientTask?.outcome, equalTo(outcome))

      val genServerTask = result.task(":sundayGenerate_server")
      assertThat(genServerTask?.outcome, equalTo(outcome))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(outcome))
    }
  }

  @Test
  fun `generate sources for kotlin are regenerated when includes changes`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))

    val includes = testProjectDir.resolve("src/main/sunday-includes")
    includes.mkdirs()

    val include = includes.resolve("include.raml")
    include.writeText(
      """
      #%RAML 1.0
      title: Test
      version: 1.0.0
      baseUri: http://localhost:8080/api
      mediaType: application/json
      types:
        Test:
          type: object
          properties:
            id: string
      """.trimIndent(),
    )

    buildFile.writeText(dualTestBuildFile)

    // Generate settings.gradle.kts with temporary build cache dir
    testProjectDir.resolve("settings.gradle.kts").writeText(
      """
      buildCache {
        local {
          directory = file("${testProjectDir.resolve("local-cache")}")
        }
      }
      """.trimIndent(),
    )

    listOf(
      TaskOutcome.SUCCESS to TaskOutcome.SUCCESS,
      TaskOutcome.FROM_CACHE to TaskOutcome.FROM_CACHE,
      TaskOutcome.SUCCESS to TaskOutcome.FROM_CACHE,
    ).forEach { outcome ->
      val (clientOutcome, serverOutcome) = outcome
      println("Executing build expecting outcomes: client=$clientOutcome, server=$serverOutcome")

      // Delete build directory to test loading from cache
      testProjectDir.resolve("build").deleteRecursively()

      if (outcome == TaskOutcome.SUCCESS to TaskOutcome.FROM_CACHE) {
        // Change contents of include.raml to force regeneration
        include.writeText(
          """
          #%RAML 1.0
          title: Test
          version: 1.0.0
          baseUri: http://localhost:8080/api
          mediaType: application/json
          types:
            Test:
              type: object
              properties:
                id: string
                name: string
          """.trimIndent(),
        )
      }

      val result =
        GradleRunner
          .create()
          .withProjectDir(testProjectDir)
          .withPluginClasspath()
          .withArguments("build", "--stacktrace", "--debug", "--build-cache")
          .withDebug(true)
          .forwardOutput()
          .build()

      val genClientTask = result.task(":sundayGenerate_client")
      assertThat(genClientTask?.outcome, equalTo(clientOutcome))

      val genServerTask = result.task(":sundayGenerate_server")
      assertThat(genServerTask?.outcome, equalTo(serverOutcome))

      val kotlinTask = result.task(":compileKotlin")
      assertThat(kotlinTask?.outcome, equalTo(clientOutcome))
    }
  }

  @Test
  fun `disable container element validation applies use-site constraints`() {

    copy("/dualtest.kt", testProjectDir.resolve("src/main/kotlin"))
    copy("/validation-constraints.raml", testProjectDir.resolve("src/main/sunday"))

    buildFile.writeText(
      dualTestBuildFile +
        "\n" +
        """
        sundayGenerations {
          client {
            disableContainerElementValid.set(true)
          }
        }
        """.trimIndent(),
    )

    val result =
      GradleRunner
        .create()
        .withProjectDir(testProjectDir)
        .withPluginClasspath()
        .withArguments("build", "--stacktrace", "--debug")
        .withDebug(true)
        .build()

    val genClientTask = result.task(":sundayGenerate_client")
    assertThat(genClientTask?.outcome, equalTo(TaskOutcome.SUCCESS))

    val outputDir = testProjectDir.resolve("build/generated/sources/sunday/sundayGenerate_client")
    val modelPath =
      Files.walk(outputDir.toPath()).use { paths ->
        paths
          .filter { path -> path.fileName.toString() == "ValidationTest.kt" }
          .findFirst()
          .orElseThrow { FileNotFoundException("ValidationTest.kt not found under $outputDir") }
      }

    val source = modelPath.toFile().readText()
    assertThat(source, containsString("@field:Size("))
    assertThat(source, containsString("@field:Pattern("))
    assertThat(source, containsString("public val codes: List<String>"))
    assertThat(source, not(containsString("List<@Size")))
  }

  private fun copy(
    src: String,
    dstDir: File,
  ) {
    dstDir.mkdirs()

    val srcPath = Paths.get(GradlePluginTests::class.java.getResource(src)?.toURI() ?: error("Resource not found"))
    val dstPath = dstDir.toPath()
    Files.copy(srcPath, dstPath.resolve(srcPath.fileName))
  }
}
